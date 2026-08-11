package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.opinion.AffectiveState;
import com.noobk.spmscavenger.opinion.DiscretionaryDirectorState;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import com.noobk.spmscavenger.opinion.OpinionMemory;
import com.noobk.spmscavenger.opinion.OpinionMemoryService;
import com.noobk.spmscavenger.opinion.EntityOpinionMemory;
import com.noobk.spmscavenger.opinion.PlaceOpinionMemory;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * GAO-0c/GAO-1 — per-mob episode registry, REST claims, and short-term mood.
 */
public final class MobExperienceContext {

    private final UUID mobId;
    private final AffectiveState affectiveState = new AffectiveState();
    private final OpinionMemory opinionMemory = new OpinionMemory();
    private final PlaceOpinionMemory placeOpinionMemory = new PlaceOpinionMemory();
    private final EntityOpinionMemory entityOpinionMemory = new EntityOpinionMemory();
    private final DiscretionaryDirectorState discretionaryDirector = new DiscretionaryDirectorState();
    private final OpinionExperienceSinks sinks;
    private final EpisodeRoutingPipeline pipeline;
    /**
     * Gate RET-1 — <b>live</b> episodes only. A closed episode is compacted out of this map and
     * its id moves to {@link #closedEpisodeIds}.
     *
     * <p>Before this, nothing ever removed an episode: {@code openEpisode} minted a fresh
     * {@code UUID.randomUUID()} per activity, {@code closed = true} was a tombstone flag rather than
     * an end of life, and one immortal mob doing ordinary activities grew this map forever.
     */
    private final Map<UUID, ActivityEpisode> episodes = new HashMap<>();

    /**
     * Bounded tombstones for episodes that have already completed.
     *
     * <p>Deleting a closed episode outright would be a <em>new correctness defect</em>: the
     * {@code closed} flag is what makes a late or duplicate terminal event a no-op. Without a
     * record, {@code ensureEpisode} would happily rebuild the episode and relearn from the same
     * event. So the heavyweight object goes and the identity stays, capped by insertion order.
     */
    private final Set<UUID> closedEpisodeIds = Collections.newSetFromMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                    return size() > MAX_CLOSED_EPISODE_TOMBSTONES;
                }
            });

    /**
     * How many completed episode identities to remember.
     *
     * <p>Bounds late-event rejection, not learning — durable learning already lives in
     * {@link OpinionMemory}. Sized well past any plausible in-flight event delay; an event arriving
     * after this many further episodes have completed is indistinguishable from a new activity.
     */
    private static final int MAX_CLOSED_EPISODE_TOMBSTONES = 256;
    private final Map<ActivityKind, Integer> executionFailureTotals = new EnumMap<>(ActivityKind.class);
    private Optional<RestSessionClaim> restClaim = Optional.empty();
    private boolean frozen;

    public MobExperienceContext(UUID mobId, OpinionExperienceSinks delegate) {
        this.mobId = Objects.requireNonNull(mobId, "mobId");
        OpinionExperienceSinks external =
                delegate == null ? OpinionExperienceSinks.noOp() : delegate;
        this.sinks = new OpinionExperienceSinks() {
            @Override
            public void onAffectPulse(AffectPulse pulse) {
                if (OpinionFeatureGate.isEnabled() && !frozen) {
                    affectiveState.applyPulse(pulse);
                }
                external.onAffectPulse(pulse);
            }

            @Override
            public void onLearningEvidence(EpisodeLearningEvidence evidence) {
                OpinionMemoryService.apply(MobExperienceContext.this, evidence);
                external.onLearningEvidence(evidence);
            }
        };
        this.pipeline = new EpisodeRoutingPipeline(this, sinks);
    }

    public UUID mobId() {
        return mobId;
    }

    public AffectiveState affectiveState() {
        return affectiveState;
    }

    public OpinionMemory opinionMemory() {
        return opinionMemory;
    }

    public PlaceOpinionMemory placeOpinionMemory() {
        return placeOpinionMemory;
    }

    public EntityOpinionMemory entityOpinionMemory() {
        return entityOpinionMemory;
    }

    public DiscretionaryDirectorState discretionaryDirector() {
        return discretionaryDirector;
    }

    public long episodeDuration(UUID episodeId, long closeTime) {
        ActivityEpisode episode = episodes.get(episodeId);
        if (episode == null) {
            return 0L;
        }
        return Math.max(0L, closeTime - episode.openedAtGameTime());
    }

    public EpisodeRoutingPipeline pipeline() {
        return pipeline;
    }

    public OpinionExperienceSinks sinks() {
        return sinks;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void freeze() {
        frozen = true;
        affectiveState.freeze();
        discretionaryDirector.onFreeze();
        invalidateEphemeral();
    }

    public void resume() {
        frozen = false;
        affectiveState.resume();
    }

    public Optional<RestSessionClaim> restClaim() {
        return restClaim;
    }

    public boolean hasLiveRestClaim() {
        return restClaim.filter(RestSessionClaim::isLive).isPresent();
    }

    public void setRestClaim(Optional<RestSessionClaim> claim) {
        restClaim = Objects.requireNonNull(claim, "claim");
    }

    public ActivityEpisode openEpisode(Optional<ActivityKind> activity, long gameTime) {
        UUID episodeId = UUID.randomUUID();
        ActivityEpisode episode = new ActivityEpisode(episodeId, activity, gameTime);
        episodes.put(episodeId, episode);
        return episode;
    }

    /**
     * The live episode for this id, creating one only if it has never completed.
     *
     * <p>Gate RET-1: a tombstoned id returns a detached, already-closed episode. It swallows the
     * late event exactly as the retained object used to, without re-entering the map — so
     * compaction cannot resurrect learning that already happened.
     */
    public ActivityEpisode ensureEpisode(
            UUID episodeId, long openedAtGameTime, Optional<ActivityKind> activity) {
        if (closedEpisodeIds.contains(episodeId)) {
            return ActivityEpisode.alreadyClosed(episodeId, activity, openedAtGameTime);
        }
        return episodes.compute(episodeId, (id, existing) -> {
            if (existing != null) {
                return existing;
            }
            return new ActivityEpisode(id, activity, Math.max(0L, openedAtGameTime));
        });
    }

    /**
     * Drops episodes that have completed, keeping their identities as tombstones.
     *
     * <p>Deliberately <b>not</b> an LRU over the whole map. A suspended or long-running episode is
     * live state, and evicting one for being old would silently discard an activity the mob is still
     * performing — trading a memory bug for a behaviour bug.
     */
    /**
     * Bulk sweep. Defensive only: unload, tests, and recovery from a path that closed an episode
     * without going through the pipeline. Normal lifetime is owned by
     * {@link #compactEpisodeIfClosed(UUID)}.
     */
    public int compactClosedEpisodes() {
        int removed = 0;
        Iterator<Map.Entry<UUID, ActivityEpisode>> entries = episodes.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, ActivityEpisode> entry = entries.next();
            if (entry.getValue().isClosed()) {
                closedEpisodeIds.add(entry.getKey());
                entries.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * O(1) compaction at the moment ownership ends.
     *
     * <p>The terminal event already knows the episode is over, so the code that closes it also
     * releases it. Scanning the whole map on a timer would leave every completed episode resident
     * until the next sweep — and a mob that stays loaded for hours performs hundreds of activities
     * between unloads, which is exactly the retention this repairs.
     *
     * @return whether an episode was released
     */
    public boolean compactEpisodeIfClosed(UUID episodeId) {
        ActivityEpisode episode = episodes.get(episodeId);
        if (episode == null || !episode.isClosed()) {
            return false;
        }
        episodes.remove(episodeId);
        closedEpisodeIds.add(episodeId);
        return true;
    }

    /** Live episodes only — the number this context is actually retaining. */
    public int liveEpisodeCount() {
        return episodes.size();
    }

    public int closedEpisodeTombstoneCount() {
        return closedEpisodeIds.size();
    }

    public boolean hasCompletedEpisode(UUID episodeId) {
        return closedEpisodeIds.contains(episodeId);
    }

    public Optional<ActivityEpisode> findEpisode(UUID episodeId) {
        return Optional.ofNullable(episodes.get(episodeId));
    }

    /** @deprecated prefer {@link #ensureEpisode(UUID, long, Optional)} with a real start tick */
    @Deprecated
    public ActivityEpisode episodeFor(UUID episodeId) {
        if (closedEpisodeIds.contains(episodeId)) {
            return ActivityEpisode.alreadyClosed(episodeId, Optional.empty(), 0L);
        }
        return episodes.computeIfAbsent(
                episodeId, id -> new ActivityEpisode(id, Optional.empty(), 0L));
    }

    public int registerExecutionFailure(ActivityKind kind) {
        return executionFailureTotals.merge(kind, 1, Integer::sum);
    }

    public void invalidateEphemeral() {
        restClaim = Optional.empty();
        for (ActivityEpisode episode : episodes.values()) {
            if (!episode.isClosed()) {
                episode.suspend();
            }
        }
        // Suspension is not an ending, so this only reclaims episodes that had already finished.
        compactClosedEpisodes();
    }

    /**
     * RET-GAO-1 — discard ephemeral execution state before parking a snapshot on chunk unload.
     *
     * <p>Suspended episodes that will never resume must not keep a heavyweight context alive in the
     * frozen store. Learning already committed to {@link OpinionMemory} survives via snapshot.
     */
    void prepareForUnloadPark() {
        invalidateEphemeral();
        for (ActivityEpisode episode : episodes.values()) {
            if (!episode.isClosed()) {
                episode.abandonForUnload();
            }
        }
        compactClosedEpisodes();
        episodes.clear();
        closedEpisodeIds.clear();
        executionFailureTotals.clear();
        discretionaryDirector.clearForUnload();
    }

    MobExperienceSnapshot captureSnapshot(long parkedAtGameTime) {
        return new MobExperienceSnapshot(
                mobId,
                affectiveState.engagement(),
                affectiveState.boredom(),
                affectiveState.satisfaction(),
                affectiveState.stress(),
                affectiveState.novelty(),
                affectiveState.ticksSinceMeaningfulProgress(),
                opinionMemory.captureSnapshot(),
                placeOpinionMemory.captureSnapshot(),
                entityOpinionMemory.captureSnapshot(),
                parkedAtGameTime);
    }

    void restoreFromSnapshot(MobExperienceSnapshot snapshot) {
        affectiveState.restoreSnapshot(
                snapshot.engagement(),
                snapshot.boredom(),
                snapshot.satisfaction(),
                snapshot.stress(),
                snapshot.novelty(),
                snapshot.ticksSinceMeaningfulProgress());
        opinionMemory.restoreFromSnapshot(snapshot.activityOpinions());
        placeOpinionMemory.restoreFromSnapshot(snapshot.placePreferences());
        entityOpinionMemory.restoreFromSnapshot(snapshot.entityPreferences());
    }
}

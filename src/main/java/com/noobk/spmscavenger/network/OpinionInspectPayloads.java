package com.noobk.spmscavenger.network;

import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.opinion.PersonalityModel;
import com.noobk.spmscavenger.opinion.readout.ActivityAdmissionView;
import com.noobk.spmscavenger.opinion.readout.OpinionInspectRejectReason;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutDecisionView;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutSnapshot;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutStatus;
import com.noobk.spmscavenger.opinion.readout.OpinionShelterHoldView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** GAO-8B Task 42B — play networking payloads (D-GAO-040). */
public final class OpinionInspectPayloads {

    public static final ResourceLocation REQUEST_ID =
            ResourceLocation.fromNamespaceAndPath(SpmScavenger.MOD_ID, "opinion_inspect_request");
    public static final ResourceLocation RESPONSE_ID =
            ResourceLocation.fromNamespaceAndPath(SpmScavenger.MOD_ID, "opinion_inspect_response");

    private OpinionInspectPayloads() {
    }

    private static boolean payloadTypesRegistered;

    /** Registers play payload types once per JVM (common init runs on integrated client and dedicated server). */
    public static void registerPayloadTypes() {
        if (payloadTypesRegistered) {
            return;
        }
        payloadTypesRegistered = true;
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Response.TYPE, Response.CODEC);
    }

    public record Request(long requestId, int entityId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Request> TYPE = new CustomPacketPayload.Type<>(REQUEST_ID);
        public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeLong(payload.requestId());
                    buf.writeVarInt(payload.entityId());
                },
                buf -> new Request(buf.readLong(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Response(
            long requestId,
            int entityId,
            OpinionInspectRejectReason rejectReason,
            Optional<OpinionReadoutSnapshot> snapshot) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Response> TYPE =
                new CustomPacketPayload.Type<>(RESPONSE_ID);
        public static final StreamCodec<FriendlyByteBuf, Response> CODEC = StreamCodec.of(
                (buf, response) -> {
                    buf.writeLong(response.requestId());
                    buf.writeVarInt(response.entityId());
                    buf.writeVarInt(response.rejectReason().ordinal());
                    buf.writeBoolean(response.snapshot().isPresent());
                    response.snapshot().ifPresent(snapshot -> OpinionReadoutCodecs.encodeSnapshot(buf, snapshot));
                },
                buf -> {
                    long requestId = buf.readLong();
                    int entityId = buf.readVarInt();
                    OpinionInspectRejectReason reason = OpinionInspectRejectReason.values()[buf.readVarInt()];
                    Optional<OpinionReadoutSnapshot> snapshot = buf.readBoolean()
                            ? Optional.of(OpinionReadoutCodecs.decodeSnapshot(buf))
                            : Optional.empty();
                    return new Response(requestId, entityId, reason, snapshot);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static final class OpinionReadoutCodecs {

        private OpinionReadoutCodecs() {
        }

        static void encodeSnapshot(FriendlyByteBuf buf, OpinionReadoutSnapshot snapshot) {
            buf.writeLong(snapshot.requestId());
            buf.writeVarInt(snapshot.entityId());
            buf.writeUtf(snapshot.mobDisplayName());
            buf.writeVarInt(snapshot.status().ordinal());
            writeStringList(buf, snapshot.summaryLines(), OpinionReadoutSnapshot.MAX_SUMMARY_LINES);
            buf.writeFloat(snapshot.engagement());
            buf.writeFloat(snapshot.boredom());
            buf.writeFloat(snapshot.satisfaction());
            buf.writeFloat(snapshot.stress());
            buf.writeFloat(snapshot.novelty());
            buf.writeVarInt(snapshot.ticksSinceMeaningfulProgress());
            buf.writeBoolean(snapshot.frozen());
            PersonalityModel personality = snapshot.personality();
            buf.writeFloat(personality.curiosity());
            buf.writeFloat(personality.sociability());
            buf.writeFloat(personality.riskTolerance());
            buf.writeFloat(personality.persistence());
            buf.writeFloat(personality.materialism());
            buf.writeFloat(personality.adventurousness());
            writeStringFloatMap(buf, snapshot.activityPreferences());
            writeStringFloatMap(buf, snapshot.environmentPreferences());
            buf.writeVarInt(snapshot.placePreferenceCount());
            buf.writeVarInt(snapshot.entityPreferenceCount());
            buf.writeBoolean(snapshot.resting());
            if (snapshot.shelterHold().isPresent()) {
                buf.writeBoolean(true);
                OpinionShelterHoldView hold = snapshot.shelterHold().get();
                buf.writeUtf(hold.phase());
                buf.writeVarInt(hold.anchorX());
                buf.writeVarInt(hold.anchorY());
                buf.writeVarInt(hold.anchorZ());
                buf.writeUtf(hold.commitmentId());
            } else {
                buf.writeBoolean(false);
            }
            buf.writeUtf(snapshot.incumbentActivity());
            buf.writeUtf(snapshot.currentIntentActivity());
            buf.writeUtf(snapshot.currentIntentLifecycle());
            buf.writeUtf(snapshot.restAuthorityPhase());
            buf.writeUtf(snapshot.currentDisposition());
            writeAdmission(buf, snapshot.exploreAdmission());
            writeAdmission(buf, snapshot.restAdmission());
            writeDecisions(buf, snapshot.recentDecisions());
        }

        private static OpinionReadoutSnapshot decodeSnapshot(FriendlyByteBuf buf) {
            long requestId = buf.readLong();
            int entityId = buf.readVarInt();
            String mobDisplayName = buf.readUtf();
            OpinionReadoutStatus status = OpinionReadoutStatus.values()[buf.readVarInt()];
            List<String> summary = readStringList(buf);
            float engagement = buf.readFloat();
            float boredom = buf.readFloat();
            float satisfaction = buf.readFloat();
            float stress = buf.readFloat();
            float novelty = buf.readFloat();
            int ticksSinceProgress = buf.readVarInt();
            boolean frozen = buf.readBoolean();
            PersonalityModel personality = new PersonalityModel(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat());
            Map<String, Float> activityPrefs = readStringFloatMap(buf);
            Map<String, Float> environmentPrefs = readStringFloatMap(buf);
            int placeCount = buf.readVarInt();
            int entityCount = buf.readVarInt();
            boolean resting = buf.readBoolean();
            Optional<OpinionShelterHoldView> shelter = buf.readBoolean()
                    ? Optional.of(new OpinionShelterHoldView(
                            buf.readUtf(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readUtf()))
                    : Optional.empty();
            String incumbent = buf.readUtf();
            String intentActivity = buf.readUtf();
            String intentLifecycle = buf.readUtf();
            String restPhase = buf.readUtf();
            String disposition = buf.readUtf();
            ActivityAdmissionView exploreAdmission = readAdmission(buf);
            ActivityAdmissionView restAdmission = readAdmission(buf);
            List<OpinionReadoutDecisionView> decisions = readDecisions(buf);
            return new OpinionReadoutSnapshot(
                    requestId,
                    entityId,
                    mobDisplayName,
                    status,
                    summary,
                    engagement,
                    boredom,
                    satisfaction,
                    stress,
                    novelty,
                    ticksSinceProgress,
                    frozen,
                    personality,
                    activityPrefs,
                    environmentPrefs,
                    placeCount,
                    entityCount,
                    resting,
                    shelter,
                    incumbent,
                    intentActivity,
                    intentLifecycle,
                    restPhase,
                    disposition,
                    exploreAdmission,
                    restAdmission,
                    decisions);
        }

        private static void writeAdmission(FriendlyByteBuf buf, ActivityAdmissionView admission) {
            buf.writeBoolean(!admission.isEmpty());
            if (admission.isEmpty()) {
                return;
            }
            buf.writeBoolean(admission.executorPresent());
            buf.writeBoolean(admission.adoptionReady());
            buf.writeUtf(admission.blocker());
            buf.writeUtf(admission.detail());
        }

        private static ActivityAdmissionView readAdmission(FriendlyByteBuf buf) {
            if (!buf.readBoolean()) {
                return ActivityAdmissionView.empty();
            }
            return new ActivityAdmissionView(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readUtf());
        }

        private static void writeStringList(FriendlyByteBuf buf, List<String> lines, int max) {
            int count = Math.min(lines.size(), max);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                buf.writeUtf(lines.get(i));
            }
        }

        private static List<String> readStringList(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<String> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(buf.readUtf());
            }
            return List.copyOf(lines);
        }

        private static void writeStringFloatMap(FriendlyByteBuf buf, Map<String, Float> map) {
            buf.writeVarInt(map.size());
            for (Map.Entry<String, Float> entry : map.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeFloat(entry.getValue());
            }
        }

        private static Map<String, Float> readStringFloatMap(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            Map<String, Float> map = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                map.put(buf.readUtf(), buf.readFloat());
            }
            return Map.copyOf(map);
        }

        private static void writeDecisions(FriendlyByteBuf buf, List<OpinionReadoutDecisionView> decisions) {
            int count = Math.min(decisions.size(), OpinionReadoutSnapshot.MAX_DECISIONS);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                OpinionReadoutDecisionView decision = decisions.get(i);
                buf.writeLong(decision.decisionId());
                buf.writeLong(decision.evaluatedAtGameTime());
                buf.writeUtf(decision.disposition());
                buf.writeUtf(decision.dispositionCause());
                buf.writeUtf(decision.selectedActivity());
                buf.writeUtf(decision.explanation());
                writeStringList(buf, decision.candidateLines(), OpinionReadoutDecisionView.MAX_CANDIDATE_LINES);
                writeStringList(buf, decision.transitionLines(), OpinionReadoutDecisionView.MAX_TRANSITION_LINES);
                buf.writeBoolean(decision.counterfactualOnly());
            }
        }

        private static List<OpinionReadoutDecisionView> readDecisions(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<OpinionReadoutDecisionView> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                out.add(new OpinionReadoutDecisionView(
                        buf.readLong(),
                        buf.readLong(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        readStringList(buf),
                        readStringList(buf),
                        buf.readBoolean()));
            }
            return List.copyOf(out);
        }
    }
}

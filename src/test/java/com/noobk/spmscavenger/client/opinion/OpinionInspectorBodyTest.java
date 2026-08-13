package com.noobk.spmscavenger.client.opinion;

import com.noobk.spmscavenger.opinion.PersonalityFactory;
import com.noobk.spmscavenger.opinion.readout.ActivityAdmissionView;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutSnapshot;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 43 item 8 — the last layer.
 *
 * <p>The server recorded the yield transaction, the snapshot carried it and the codec shipped it,
 * and the screen rendered none of it. No server-side test could catch that, because every one of
 * them stopped at the snapshot. This asserts the presentation layer actually shows what it was sent.
 */
class OpinionInspectorBodyTest {

    private static OpinionReadoutSnapshot snapshotWith(List<String> yieldLines) {
        return new OpinionReadoutSnapshot(
                1L, 7, "PlayerMob", OpinionReadoutStatus.READY,
                List.of("summary"),
                0f, 0f, 0f, 0f, 0f, 0, false,
                PersonalityFactory.fromIdentity(UUID.nameUUIDFromBytes("body".getBytes()), 5, 5),
                Map.of(), Map.of(), 0, 0, false, Optional.empty(),
                "EXPLORE", "EXPLORE", "RUNNING", "NONE", "INTENT_ISSUED",
                ActivityAdmissionView.empty(), ActivityAdmissionView.empty(),
                List.of(), yieldLines);
    }

    @Test
    void mustHappen_yieldTransactionsAppearInTheInspectorBody() {
        List<String> lines = OpinionInspectorBody.compose(snapshotWith(List.of(
                "REQUESTED @900 REST → EXPLORE origin=#3 expires=1100",
                "ENDED @934 REST → EXPLORE outcome=ACKNOWLEDGED origin=#3 after=34t")));

        String body = String.join("\n", lines);

        assertTrue(body.contains("Recent yield transactions"),
                "the section must exist, or the transaction is invisible to the user: " + body);
        assertTrue(body.contains("REQUESTED @900 REST → EXPLORE origin=#3 expires=1100"), body);
        assertTrue(body.contains("outcome=ACKNOWLEDGED"), body);
        assertTrue(body.contains("after=34t"), body);
    }

    @Test
    void mustNotHappen_anEmptySectionIsRendered() {
        String body = String.join("\n", OpinionInspectorBody.compose(snapshotWith(List.of())));

        assertFalse(body.contains("Recent yield transactions"),
                "a mob that has never yielded should not be shown an empty heading");
    }

    /** The screen renders what it was sent; it must not recompute or reformat. */
    @Test
    void mustHappen_theScreenRendersTheProjectedStringsVerbatim() {
        String exact = "ENDED @1200 EXPLORE → REST outcome=MANDATORY_INVALIDATION origin=#9 after=7t";

        assertTrue(OpinionInspectorBody.compose(snapshotWith(List.of(exact))).contains(exact),
                "deriving anything here would let the client disagree with the server's causal "
                        + "record, which is the whole point of projecting on the server");
    }
}

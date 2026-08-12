package com.noobk.spmscavenger.opinion.readout;

import java.util.Objects;

/** Read-only shelter authority slice for GAO-8B (D-GAO-044). */
public record OpinionShelterHoldView(String phase, int anchorX, int anchorY, int anchorZ, String commitmentId) {

    public OpinionShelterHoldView {
        Objects.requireNonNull(phase, "phase");
        commitmentId = commitmentId == null ? "" : commitmentId;
    }
}

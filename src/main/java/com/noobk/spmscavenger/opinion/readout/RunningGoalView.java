package com.noobk.spmscavenger.opinion.readout;

/** One running Goal row from a read-only GoalSelector scan. */
public record RunningGoalView(String goalSimpleName, String activityClass) {

    public RunningGoalView {
        goalSimpleName = goalSimpleName == null ? "" : goalSimpleName;
        activityClass = activityClass == null ? "" : activityClass;
    }
}

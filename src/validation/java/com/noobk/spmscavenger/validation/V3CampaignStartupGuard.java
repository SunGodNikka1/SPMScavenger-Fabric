package com.noobk.spmscavenger.validation;

import java.util.function.Consumer;

/** Contains non-fatal failures at the temporary Task-59 startup boundary. */
final class V3CampaignStartupGuard {

    @FunctionalInterface
    interface StartupAction {
        void run() throws Throwable;
    }

    record Outcome(boolean succeeded, Throwable failure, String failureSummary) {
        Outcome {
            if (succeeded && failure != null) {
                throw new IllegalArgumentException("successful startup cannot carry a failure");
            }
        }

        static Outcome success() {
            return new Outcome(true, null, "NONE");
        }

        static Outcome failure(Throwable failure) {
            return new Outcome(false, failure, summarize(failure));
        }
    }

    private V3CampaignStartupGuard() {
    }

    @SuppressWarnings("removal")
    static Outcome execute(StartupAction action) {
        try {
            action.run();
            return Outcome.success();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (ThreadDeath fatal) {
            throw fatal;
        } catch (LinkageError fatal) {
            throw fatal;
        } catch (Throwable failure) {
            return Outcome.failure(failure);
        }
    }

    static Outcome execute(StartupAction action, Consumer<Outcome> failureHandler) {
        Outcome outcome = execute(action);
        if (!outcome.succeeded()) {
            failureHandler.accept(outcome);
        }
        return outcome;
    }

    private static String summarize(Throwable failure) {
        StringBuilder summary = new StringBuilder(typeAndMessage(failure));
        Throwable root = failure;
        int depth = 0;
        while (root.getCause() != null && root.getCause() != root && depth++ < 12) {
            root = root.getCause();
        }
        if (root != failure) {
            summary.append("; rootCause=").append(typeAndMessage(root));
        }
        return truncate(summary.toString(), 320);
    }

    private static String typeAndMessage(Throwable failure) {
        String message = failure.getMessage();
        String clean = message == null || message.isBlank()
                ? "<no message>"
                : message.replace('\n', ' ').replace('\r', ' ').trim();
        return failure.getClass().getName() + ": " + clean;
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 3) + "...";
    }
}

package com.noobk.spmscavenger.experience;

/**
 * GAO-0b — ingress contract for raw experience events.
 *
 * <p>GAO-0c supplies the processing implementation. GAO-0b defines vocabulary only; no production
 * emitter may publish into an unowned concrete pipeline.
 */
@FunctionalInterface
public interface ExperiencePipeline {

    void accept(ExperienceEvent event);
}

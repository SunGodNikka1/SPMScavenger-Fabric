package com.noobk.spmscavenger.client;

import java.lang.reflect.Method;

/** Optional Iris API bridge kept reflective so Iris never becomes a compile/runtime dependency. */
public final class IrisShaderState {

    private static final String API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
    private static final Access ACCESS = discover();

    private IrisShaderState() {
    }

    public static Snapshot snapshot() {
        return ACCESS.snapshot();
    }

    public record Snapshot(boolean shaderPackInUse, boolean shadowPass) {
        static final Snapshot INACTIVE = new Snapshot(false, false);
    }

    private interface Access {
        Snapshot snapshot();
    }

    private static Access discover() {
        try {
            Class<?> apiType = Class.forName(API_CLASS, false, IrisShaderState.class.getClassLoader());
            Method getInstance = apiType.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Method inUse = apiType.getMethod("isShaderPackInUse");
            Method shadowPass = apiType.getMethod("isRenderingShadowPass");
            return () -> invoke(api, inUse, shadowPass);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return () -> Snapshot.INACTIVE;
        }
    }

    private static Snapshot invoke(Object api, Method inUse, Method shadowPass) {
        try {
            boolean active = (boolean) inUse.invoke(api);
            return active
                    ? new Snapshot(true, (boolean) shadowPass.invoke(api))
                    : Snapshot.INACTIVE;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Snapshot.INACTIVE;
        }
    }
}

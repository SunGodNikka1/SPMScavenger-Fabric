package com.noobk.spmscavenger.village.storage;

import net.minecraft.core.GlobalPos;

import java.util.UUID;

/**
 * Read-only grant view for pure policy classification.
 */
public interface GrantSnapshot {

    boolean hasOwner(GlobalPos key, UUID mobId);

    boolean isSharedWith(GlobalPos key, UUID mobId);
}

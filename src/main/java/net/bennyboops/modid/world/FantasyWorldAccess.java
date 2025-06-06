package net.bennyboops.modid.world;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface FantasyWorldAccess {
    void fantasy$setTickWhenEmpty(boolean tickWhenEmpty);

    boolean fantasy$shouldTick();
}

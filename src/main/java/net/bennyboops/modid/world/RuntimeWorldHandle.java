package net.bennyboops.modid.world;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.bennyboops.modid.world.Fantasy;
import net.bennyboops.modid.world.FantasyWorldAccess;
import net.bennyboops.modid.world.RuntimeWorld;

public final class RuntimeWorldHandle {
    private final Fantasy fantasy;
    private final ServerWorld world;

    RuntimeWorldHandle(Fantasy fantasy, ServerWorld world) {
        this.fantasy = fantasy;
        this.world = world;
    }

    public void setTickWhenEmpty(boolean tickWhenEmpty) {
        ((FantasyWorldAccess) this.world).fantasy$setTickWhenEmpty(tickWhenEmpty);
    }

    /**
     * Deletes the world, including all stored files
     */
    public void delete() {
        this.fantasy.enqueueWorldDeletion(this.world);
    }

    /**
     * Unloads the world. It only deletes the files if world is temporary.
     */
    public void unload() {
        if (this.world instanceof RuntimeWorld runtimeWorld && runtimeWorld.style == RuntimeWorld.Style.TEMPORARY) {
            this.fantasy.enqueueWorldDeletion(this.world);
        } else {
            this.fantasy.enqueueWorldUnloading(this.world);
        }
    }

    public ServerWorld asWorld() {
        return this.world;
    }

    public RegistryKey<World> getRegistryKey() {
        return this.world.getRegistryKey();
    }
}

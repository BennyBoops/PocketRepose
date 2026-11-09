package net.bennyboops.modid.block;

import com.mojang.serialization.MapCodec;
import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.bennyboops.modid.data.PlayerEntryData;
import net.bennyboops.modid.data.SuitcaseLocationTracker;
import net.bennyboops.modid.item.KeystoneItem;
import net.bennyboops.modid.world.Fantasy;
import net.bennyboops.modid.world.PortalChunkGenerator;
import net.bennyboops.modid.world.RuntimeWorldConfig;
import net.bennyboops.modid.world.RuntimeWorldHandle;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.nbt.*;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class SuitcaseBlock extends BlockWithEntity {

    private static final String SUITCASE_BE_ID = "pocket-repose:suitcase";
    public static final BooleanProperty OPEN = BooleanProperty.of("open");
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.of("color", DyeColor.class);
    private static final VoxelShape SHAPE_N = Block.createCuboidShape(0, 0, 2, 16, 4, 14);
    private static final VoxelShape SHAPE_S = Block.createCuboidShape(0, 0, 2, 16, 4, 14);
    private static final VoxelShape SHAPE_E = Block.createCuboidShape(2, 0, 0, 14, 4, 16);
    private static final VoxelShape SHAPE_W = Block.createCuboidShape(2, 0, 0, 14, 4, 16);

    public SuitcaseBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(OPEN, false).with(FACING, Direction.NORTH).with(COLOR, DyeColor.BROWN));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(SuitcaseBlock::new);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world.isClient) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SuitcaseBlockEntity suitcase)) return;

        trackPlacedSuitcase(world, suitcase, stack, exitOnTop(state, world, pos));
    }

    /**
     * For someone coming back out on top of the suitcase with this id at exit: the yaw facing the suitcase's
     * front (the way it was placed facing), or null if that suitcase isn't there.
     */
    @Nullable
    public static Float yawFacingFront(World world, Vec3d exit, @Nullable UUID suitcaseId) {
        if (suitcaseId == null) return null;
        BlockPos at = BlockPos.ofFloored(exit);
        for (BlockPos pos : new BlockPos[]{at, at.down()}) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof SuitcaseBlock && world.getBlockEntity(pos) instanceof SuitcaseBlockEntity suitcase
                    && suitcaseId.equals(suitcase.getSuitcaseId())) {
                return state.get(FACING).asRotation();
            }
        }
        return null;
    }

    /** Where travelers come back out of a placed suitcase: standing right on top of it (not on top of its block space). */
    public static Vec3d exitOnTop(BlockState state, World world, BlockPos pos) {
        VoxelShape shape = state.getCollisionShape(world, pos);
        double top = shape.isEmpty() ? 1.0 : shape.getMax(Direction.Axis.Y);
        return new Vec3d(pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5);
    }

    /**
     * Loads a placed suitcase's data from its item and records where its travelers come back out
     * (on top of a suitcase, in front of a pocket door).
     */
    public static void trackPlacedSuitcase(World world, SuitcaseBlockEntity suitcase, ItemStack stack, Vec3d exit) {
        NbtComponent comp = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (comp != null) {
            NbtCompound tag = comp.copyNbt();
            suitcase.readNbt(tag, world.getRegistryManager());
        }

        SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(world.getServer());
        if (tracker == null) return;

        String dimStr = world.getRegistryKey().getValue().toString();
        tracker.updateSuitcaseLocation(suitcase.getSuitcaseId(), dimStr, exit.x, exit.y, exit.z,
                SuitcaseLocationTracker.LocationType.BLOCK);

        String key = suitcase.getBoundKeystoneName();
        if (key != null) {
            for (SuitcaseBlockEntity.EnteredPlayerData p : suitcase.getEnteredPlayers()) {
                tracker.updateLocation(key, p.uuid, dimStr, suitcase.getSuitcaseId(),
                        exit.x, exit.y, exit.z, p.yaw, 0f, SuitcaseLocationTracker.LocationType.BLOCK);
            }
        }
    }

    /** Pocket blocks: suitcases (including the secret barrel and chest) and pocket doors. */
    public static boolean isPocketBlock(Block block) {
        return block instanceof SuitcaseBlock || block instanceof PocketDoorBlock;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SuitcaseBlockEntity suitcase) {
                ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), droppedStack(this, suitcase));
            }
            world.removeBlockEntity(pos);
        }
    }

    /** The item a broken pocket block drops, carrying its binding, lock and travelers. */
    public static ItemStack droppedStack(ItemConvertible item, SuitcaseBlockEntity suitcase) {
        ItemStack stack = new ItemStack(item);
        String key = suitcase.getBoundKeystoneName();

        if (key != null) {
            NbtCompound beTag = new NbtCompound();

            beTag.putString("id", SUITCASE_BE_ID);
            beTag.putString("BoundKeystone", key);
            beTag.putBoolean("Locked", suitcase.isLocked());
            beTag.putBoolean("DimensionLocked", suitcase.isDimensionLocked());

            beTag.putUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID, suitcase.getSuitcaseId());

            NbtList entered = new NbtList();
            for (SuitcaseBlockEntity.EnteredPlayerData d : suitcase.getEnteredPlayers())
                entered.add(d.toNbt());
            beTag.put("EnteredPlayers", entered);

            stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(beTag));

            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Bound to: " + (suitcase.isLocked() ? "§k" : "") + key.replace("_", " "))
                    .formatted(Formatting.GRAY));
            lore.add(Text.literal(suitcase.isLocked() ? "§cLocked" : "§aUnlocked")
                    .formatted(Formatting.GRAY));
            stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        }
        return stack;
    }

    private static String sanitize(ItemStack stack) {
        String raw = Formatting.strip(stack.getName().getString());
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return onUseWithItem(player.getMainHandStack(), state, world, pos, player, Hand.MAIN_HAND, hit).toActionResult();
    }

    @Override
    public ItemActionResult onUseWithItem(ItemStack held,
                                          BlockState state,
                                          World world,
                                          BlockPos pos,
                                          PlayerEntity player,
                                          Hand hand,
                                          BlockHitResult hit) {
        if (world.isClient) return ItemActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SuitcaseBlockEntity suitcase)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return useSuitcase(suitcase, held, world, pos, player, state.get(OPEN), opening -> {
            world.setBlockState(pos, state.with(OPEN, opening));
            playOpenCloseSound(world, pos, opening);
        });
    }

    /**
     * Whether using a pocket block with this item will just open or close it (rather than use a key on it,
     * or be refused). The client uses this to open pocket doors right away, as vanilla doors do.
     */
    public static boolean willToggle(SuitcaseBlockEntity suitcase, ItemStack held, World world, PlayerEntity player) {
        return suitcase.canOpenInDimension(world) && !(held.getItem() instanceof KeystoneItem)
                && (!player.isSneaking() || held.isEmpty())
                && suitcase.getBoundKeystoneName() != null && !suitcase.isLocked();
    }

    /**
     * Using a pocket block: a key binds it or locks/unlocks it, otherwise it opens or closes (when bound
     * and unlocked). Shared by suitcases and pocket doors, which open in their own way through setOpen.
     */
    public static ItemActionResult useSuitcase(SuitcaseBlockEntity suitcase, ItemStack held, World world, BlockPos pos,
                                               PlayerEntity player, boolean open, Consumer<Boolean> setOpen) {
        // Inside its own pocket dimension a suitcase can't be opened or locked, only rebound to another name
        boolean inOwnDimension = !suitcase.canOpenInDimension(world);
        String key = suitcase.getBoundKeystoneName();

        if (held.getItem() instanceof KeystoneItem) {
            String name = sanitize(held);

            if (key != null && key.equals(name) && !inOwnDimension) {
                boolean locked = !suitcase.isLocked();
                suitcase.setLocked(locked);
                world.playSound(null, pos,
                        locked ? SoundEvents.BLOCK_IRON_DOOR_CLOSE : SoundEvents.BLOCK_IRON_DOOR_OPEN,
                        SoundCategory.BLOCKS, .3F, 2F);
                player.sendMessage(Text.literal(locked ? "☒" : "☐").formatted(Formatting.GRAY), true);
                return ItemActionResult.SUCCESS;
            }

            if (suitcase.isLocked()) {
                world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, .3F, 2F);
                player.sendMessage(Text.literal("☒").formatted(Formatting.RED), true);
                return ItemActionResult.FAIL;
            }

            if (!held.contains(DataComponentTypes.CUSTOM_NAME)) {
                player.sendMessage(Text.literal("Name the key to bind.").formatted(Formatting.RED), false);
                return ItemActionResult.FAIL;
            }

            if (!KeystoneItem.isValidKeystone(held)) return ItemActionResult.FAIL;

            // A suitcase can never lead to the pocket dimension it's in
            if (world.getRegistryKey().getValue().equals(Identifier.of("pocket-repose", "pocket_dimension_" + name))) {
                world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, .3F, 2F);
                player.sendMessage(Text.literal("☒").formatted(Formatting.RED), true);
                return ItemActionResult.FAIL;
            }

            suitcase.bindKeystone(name);
            world.playSound(null, pos, SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.BLOCKS, 2F, 0F);
            return ItemActionResult.SUCCESS;
        }

        if (inOwnDimension) {
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, .3F, 2F);
            player.sendMessage(Text.literal("☒").formatted(Formatting.RED), true);
            return ItemActionResult.SUCCESS;
        }

        if (!player.isSneaking() || held.isEmpty()) {
            if (key == null) {
                world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, .5F, 2F);
                return ItemActionResult.FAIL;
            }
            if (suitcase.isLocked()) {
                // Sneaking with an empty hand knocks instead, and the knock is heard inside too
                if (player.isSneaking() && held.isEmpty()) {
                    knock(world, player, key);
                    return ItemActionResult.SUCCESS;
                }
                world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, .3F, 2F);
                return ItemActionResult.FAIL;
            }

            setOpen.accept(!open);
            return ItemActionResult.SUCCESS;
        }

        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient) return;
        // An item thrown onto the open block goes in (at the end of the tick, like players)
        if (entity instanceof ItemEntity item) {
            if (state.get(OPEN) && canEnterFrom(state, world, pos, item)) {
                PocketTravelHandler.requestItemEntry(item, (ServerWorld) world, pos);
            }
            return;
        }
        if (!(entity instanceof ServerPlayerEntity player)) return;
        if (!state.get(OPEN) || !player.isSneaking()) return;
        if (!canEnterFrom(state, world, pos, player)) return;
        if (recursionBlocked(world, pos, player)) return;

        // Teleporting from inside the movement packet handler makes vanilla re-apply the old movement
        // afterwards (in survival that puts the player at their old coordinates, but in the pocket
        // dimension), so the actual entry happens at the end of the server tick
        PocketTravelHandler.requestEntry(player, (ServerWorld) world, pos);
    }

    /** Whether a player touching this block is in a position to enter it. Suitcases can be entered from any side. */
    protected boolean canEnterFrom(BlockState state, World world, BlockPos pos, Entity player) {
        return true;
    }

    /**
     * A knock on a locked pocket block: heard by the person knocking, and (at the knockVolume setting) at the
     * player entry point inside the pocket dimension it leads to.
     */
    private static void knock(World world, PlayerEntity player, String key) {
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.PLAYERS, 0.25F, 1.5F);

        ServerWorld pocket = world.getServer() == null ? null : world.getServer().getWorld(
                RegistryKey.of(RegistryKeys.WORLD, Identifier.of("pocket-repose", "pocket_dimension_" + key)));
        if (pocket != null) {
            Vec3d entry = PlayerEntryData.get(pocket).getEntryPos();
            pocket.playSound(null, entry.x, entry.y, entry.z,
                    SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.PLAYERS, PocketRepose.getKnockVolume(), 1.5F);
        }
    }

    /** Pocket blocks can't be entered from inside a pocket dimension while allowRecursion is off. */
    public static boolean recursionBlocked(World world, BlockPos pos, PlayerEntity player) {
        Identifier id = world.getRegistryKey().getValue();
        boolean inPocketDimension =
                "pocket-repose".equals(id.getNamespace()) &&
                        id.getPath().startsWith("pocket_dimension_");

        if (inPocketDimension && !PocketRepose.getAllowRecursion()) {
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, .3F, 2F);
            player.sendMessage(Text.literal("§cSuitcase recursion is disabled"), true);
            return true;
        }
        return false;
    }

    private static boolean isOpenEntrance(BlockState state) {
        return (state.getBlock() instanceof SuitcaseBlock && state.get(OPEN))
                || (state.getBlock() instanceof PocketDoorBlock && state.get(DoorBlock.OPEN));
    }

    /**
     * Sends an item into the pocket dimension of the open pocket block at pos (a pocket door's lower half), to the
     * player entry point, if a player could go in that way.
     */
    public static void sendItemIn(ItemEntity item, ServerWorld world, BlockPos pos) {
        if (!item.isAlive() || item.getWorld() != world || PocketTravelHandler.hasTraveled(item)) return;
        BlockState state = world.getBlockState(pos);
        if (!isOpenEntrance(state) || !(world.getBlockEntity(pos) instanceof SuitcaseBlockEntity suitcase)) return;

        String key = suitcase.getBoundKeystoneName();
        if (key == null || !suitcase.canOpenInDimension(world)) return;
        boolean dimensionAllowed = world.getRegistryKey() == World.NETHER ? PocketRepose.getAllowInNether()
                : world.getRegistryKey() != World.END || PocketRepose.getAllowInEnd();
        if (!dimensionAllowed) return;
        if (PocketPortalBlock.isPocketDimension(world) && !PocketRepose.getAllowRecursion()) return;

        ServerWorld target = ensureDimensionExists(world.getServer(), key, Identifier.of("pocket-repose", "pocket_dimension_" + key));
        if (target == null) return;

        world.playSound(null, item.getX(), item.getY(), item.getZ(), SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 1F, 1F);
        PocketTravelHandler.markTraveled(item);
        Vec3d dest = PlayerEntryData.get(target).getEntryPos();
        item.teleportTo(new TeleportTarget(target, dest, Vec3d.ZERO, item.getYaw(), item.getPitch(), TeleportTarget.NO_OP));
        target.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 1F, 1F);
    }

    /**
     * Sends a player into the pocket dimension of the suitcase (or pocket door's lower half) at pos, if they can
     * still enter it. from is where they stood before walking in: a pocket door lets them back out on that side.
     */
    public static void enterSuitcase(ServerPlayerEntity player, ServerWorld world, BlockPos pos, Vec3d from) {
        if (player.getServerWorld() != world || !player.isAlive() || player.isRemoved()) return;
        BlockState state = world.getBlockState(pos);
        if (!isOpenEntrance(state)) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SuitcaseBlockEntity suitcase)) return;

        String key = suitcase.getBoundKeystoneName();
        if (key == null) return;

        boolean dimensionAllowed = world.getRegistryKey() == World.NETHER ? PocketRepose.getAllowInNether()
                : world.getRegistryKey() != World.END || PocketRepose.getAllowInEnd();
        if (!suitcase.canOpenInDimension(world) || !dimensionAllowed) {
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, .3F, 2F);
            player.sendMessage(Text.literal("☒").formatted(Formatting.RED), true);
            return;
        }

        Identifier dimId = Identifier.of("pocket-repose", "pocket_dimension_" + key);
        ServerWorld target = ensureDimensionExists(world.getServer(), key, dimId);

        if (target == null) {
            player.sendMessage(Text.literal("§cFailed to create pocket dimension"), true);
            return;
        }

        suitcase.playerEntered(player);
        // The advancement system only grants the criterion once per player, so always trigger it
        PocketRepose.ENTER_POCKET_DIMENSION.trigger(player);

        SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(world.getServer());
        if (tracker != null) {
            UUID sid = suitcase.getSuitcaseId();
            String dimStr = world.getRegistryKey().getValue().toString();

            tracker.setLastSuitcase(key, player.getUuidAsString(), sid);

            // Pocket doors send players back out in front of the doorway, on the side they walked in from;
            // where they entered would put them straight back into the open doorway
            Vec3d doorExit = state.getBlock() instanceof PocketDoorBlock door ? door.exitPoint(state, pos, from) : null;
            Vec3d entry = doorExit != null ? doorExit : player.getPos();

            tracker.updateLocation(
                    key,
                    player.getUuidAsString(),
                    dimStr,
                    sid,
                    entry.x, entry.y, entry.z,
                    player.getYaw(), player.getPitch(),
                    SuitcaseLocationTracker.LocationType.ENTRY
            );

            if (doorExit != null) {
                tracker.updateSuitcaseLocation(sid, dimStr, doorExit.x, doorExit.y, doorExit.z,
                        SuitcaseLocationTracker.LocationType.BLOCK);
            } else {
                Vec3d top = exitOnTop(state, world, pos);
                tracker.updateSuitcaseLocation(sid, dimStr, top.x, top.y, top.z, SuitcaseLocationTracker.LocationType.BLOCK);
            }
        }

        player.stopRiding();
        player.velocityModified = true;
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0f;

        PlayerEntryData ped = PlayerEntryData.get(target);
        Vec3d dest = ped.getEntryPos();
        TeleportTarget tpTarget = new TeleportTarget(target, dest, Vec3d.ZERO, ped.getEntryYaw(), player.getPitch(), TeleportTarget.NO_OP);
        player.teleportTo(tpTarget);

        player.networkHandler.sendPacket(new StopSoundS2CPacket(null, null));
        world.playSound(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 2F, 1F);
        // The sound above plays where the player was; play it where they arrive too, so they hear it themselves
        playTravelSound(player);

        // Locked blocks follow closeOnLockedEntry, unlocked ones closeOnEntry
        if (suitcase.isLocked() ? PocketRepose.getCloseOnLockedEntry() : PocketRepose.getCloseOnEntry()) {
            closeBehind(world, pos);
        }
    }

    /** The suitcase travel sound, played where the player is now (they can't hear one left in the world they left). */
    public static void playTravelSound(ServerPlayerEntity player) {
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 2F, 1F);
    }

    /** Closes a pocket block behind someone who just went in (closeOnEntry / closeOnLockedEntry). */
    public static void closeBehind(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SuitcaseBlock suitcase && state.get(OPEN)) {
            world.setBlockState(pos, state.with(OPEN, false));
            suitcase.playOpenCloseSound(world, pos, false);
        } else if (state.getBlock() instanceof PocketDoorBlock door) {
            door.setOpen(null, world, state, pos, false);
        }
    }


    private static ServerWorld ensureDimensionExists(net.minecraft.server.MinecraftServer server, String key, Identifier dimId) {
        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
        ServerWorld existing = server.getWorld(dimKey);
        if (existing != null) return existing;

        // Same path as using the keystone: registers the dimension so it reloads on restart, and only
        // builds the starting island when the dimension is brand new (never over an existing one)
        return KeystoneItem.createOrLoadPersistentDimension(server, dimId.getPath());
    }

    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    protected void playOpenCloseSound(World world, BlockPos pos, boolean opening) {
        playSuitcaseSound(world, pos, opening);
    }

    /** The suitcase's own open and close sounds, which every pocket block and pocket/exit door plays. */
    public static void playSuitcaseSound(World world, BlockPos pos, boolean opening) {
        world.playSound(null, pos,
                opening ? SoundEvents.BLOCK_LADDER_STEP : SoundEvents.BLOCK_LADDER_BREAK,
                SoundCategory.BLOCKS, .3F, 0F);

        world.playSound(null, pos,
                opening ? SoundEvents.BLOCK_CHEST_LOCKED : SoundEvents.BLOCK_BAMBOO_WOOD_TRAPDOOR_CLOSE,
                SoundCategory.BLOCKS, .3F, opening ? 2F : 0F);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return switch (state.get(FACING)) {
            case SOUTH -> SHAPE_S;
            case EAST -> SHAPE_E;
            case WEST -> SHAPE_W;
            default -> SHAPE_N;
        };
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) { return getOutlineShape(state, world, pos, ctx); }

    @Override public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) { return 1F; }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
        b.add(OPEN, FACING, COLOR);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SuitcaseBlockEntity(pos, state);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        ItemStack stack = super.getPickStack(world, pos, state);
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof SuitcaseBlockEntity suitcase) {
            String key = suitcase.getBoundKeystoneName();
            if (key != null) {
                NbtCompound beTag = new NbtCompound();
                beTag.putString("id", SUITCASE_BE_ID);
                beTag.putString("BoundKeystone", key);
                beTag.putBoolean("Locked", suitcase.isLocked());

                // No SuitcaseId: a picked copy is a new suitcase. Sharing the id made the exit
                // tracker follow the copy around (e.g. into the pocket dimension itself).

                stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(beTag));

                List<Text> lore = new ArrayList<>();
                lore.add(Text.literal("Bound to: " + (suitcase.isLocked() ? "§k" : "") + key.replace("_", " "))
                        .formatted(Formatting.GRAY));
                lore.add(Text.literal(suitcase.isLocked() ? "§cLocked" : "§aUnlocked")
                        .formatted(Formatting.GRAY));
                stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
            }

            DyeColor color = state.get(COLOR);
            NbtCompound custom = new NbtCompound();
            custom.putString("Color", color.getName());
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(custom));
        }
        return stack;
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        return Collections.emptyList();
    }

    @Override
    public boolean onSyncedBlockEvent(BlockState state, World world, BlockPos pos, int type, int data) {
        super.onSyncedBlockEvent(state, world, pos, type, data);
        BlockEntity be = world.getBlockEntity(pos);
        return be != null && be.onSyncedBlockEvent(type, data);
    }
}

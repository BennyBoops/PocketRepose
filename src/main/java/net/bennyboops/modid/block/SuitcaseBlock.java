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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
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

        NbtComponent comp = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (comp != null) {
            NbtCompound tag = comp.copyNbt();
            suitcase.readNbt(tag, world.getRegistryManager());
        }

        SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(world.getServer());
        if (tracker != null) {
            tracker.updateSuitcaseLocationFromBlock(
                    suitcase.getSuitcaseId(),
                    world.getRegistryKey().getValue().toString(),
                    pos
            );
        }

        String key = suitcase.getBoundKeystoneName();
        if (key != null) {
            SuitcaseLocationTracker t = SuitcaseLocationTracker.get(world.getServer());
            if (t != null) {
                for (SuitcaseBlockEntity.EnteredPlayerData p : suitcase.getEnteredPlayers()) {
                    t.updateLocationFromBlock(key, p.uuid,
                            world.getRegistryKey().getValue().toString(),
                            suitcase.getSuitcaseId(),
                            pos, p.yaw, 0f);
                }
            }
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SuitcaseBlockEntity suitcase) {

                ItemStack stack = new ItemStack(this);
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

                ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
            world.removeBlockEntity(pos);
        }
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

        if (!suitcase.canOpenInDimension(world)) {
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, .3F, 2F);
            player.sendMessage(Text.literal("☒").formatted(Formatting.RED), true);
            return ItemActionResult.SUCCESS;
        }

        String key = suitcase.getBoundKeystoneName();

        if (held.getItem() instanceof KeystoneItem) {
            String name = sanitize(held);

            if (key != null && key.equals(name)) {
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

            if (name.equals("item.pocket-repose.keystone")) {
                player.sendMessage(Text.literal("Name the key to bind.").formatted(Formatting.RED), false);
                return ItemActionResult.FAIL;
            }

            if (!KeystoneItem.isValidKeystone(held)) return ItemActionResult.FAIL;

            suitcase.bindKeystone(name);
            world.playSound(null, pos, SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.BLOCKS, 2F, 0F);
            return ItemActionResult.SUCCESS;
        }

        if (!player.isSneaking() || held.isEmpty()) {
            if (key == null) {
                world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, .5F, 2F);
                return ItemActionResult.FAIL;
            }
            if (suitcase.isLocked()) {
                world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, .3F, 2F);
                return ItemActionResult.FAIL;
            }

            boolean open = state.get(OPEN);
            world.setBlockState(pos, state.with(OPEN, !open));

            world.playSound(null, pos,
                    open ? SoundEvents.BLOCK_LADDER_BREAK : SoundEvents.BLOCK_LADDER_STEP,
                    SoundCategory.BLOCKS, .3F, 0F);

            world.playSound(null, pos,
                    open ? SoundEvents.BLOCK_BAMBOO_WOOD_TRAPDOOR_CLOSE : SoundEvents.BLOCK_CHEST_LOCKED,
                    SoundCategory.BLOCKS, .3F, open ? 0F : 2F);

            return ItemActionResult.SUCCESS;
        }

        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient) return;
        if (!(entity instanceof ServerPlayerEntity player)) return;
        if (!state.get(OPEN) || !player.isSneaking()) return;

        Identifier id = world.getRegistryKey().getValue();
        boolean inPocketDimension =
                "pocket-repose".equals(id.getNamespace()) &&
                        id.getPath().startsWith("pocket_dimension_");

        if (inPocketDimension && !PocketRepose.getAllowRecursion()) {
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, .3F, 2F);
            player.sendMessage(Text.literal("§cSuitcase recursion is disabled"), true);
            return;
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SuitcaseBlockEntity suitcase)) return;

        String key = suitcase.getBoundKeystoneName();
        if (key == null) return;

        if (!suitcase.canOpenInDimension(world)) {
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

        boolean first = suitcase.isFirstTimeEntering(player);
        suitcase.playerEntered(player);
        if (first) PocketRepose.ENTER_POCKET_DIMENSION.trigger(player);

        SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(world.getServer());
        if (tracker != null) {
            UUID sid = suitcase.getSuitcaseId();
            String dimStr = world.getRegistryKey().getValue().toString();

            tracker.setLastSuitcase(key, player.getUuidAsString(), sid);

            tracker.updateLocation(
                    key,
                    player.getUuidAsString(),
                    dimStr,
                    sid,
                    player.getX(), player.getY(), player.getZ(),
                    player.getYaw(), player.getPitch(),
                    SuitcaseLocationTracker.LocationType.ENTRY
            );

            tracker.updateSuitcaseLocationFromBlock(sid, dimStr, pos);
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
    }


    private ServerWorld ensureDimensionExists(net.minecraft.server.MinecraftServer server, String key, Identifier dimId) {
        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
        ServerWorld existing = server.getWorld(dimKey);
        if (existing != null) return existing;

        Registry<Biome> biomeRegistry = server.getRegistryManager().get(RegistryKeys.BIOME);
        ChunkGenerator generator = new PortalChunkGenerator(biomeRegistry);
        long seed = server.getOverworld().getSeed();

        RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setDimensionType(PocketRepose.POCKET_DIMENSION_TYPE_KEY)
                .setGenerator(generator)
                .setSeed(seed)
                .setShouldTickTime(false);

        RuntimeWorldHandle handle = Fantasy.get(server).getOrOpenPersistentWorld(dimId, config);
        ServerWorld world = handle.asWorld();

        world.setChunkForced(0, 0, true);
        generateInitialStructure(world);
        return world;
    }

    private void generateInitialStructure(ServerWorld world) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -64; y <= -61; y++) {
                    BlockPos portalPos = new BlockPos(x, y, z);
                    world.setBlockState(portalPos, ModBlocks.PORTAL.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }

        BlockPos platformPos = new BlockPos(0, 96, 0);
        world.setBlockState(platformPos, Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);

        BlockPos exitPortal = new BlockPos(0, 100, 0);
        world.setBlockState(exitPortal, ModBlocks.PORTAL.getDefaultState(), Block.NOTIFY_ALL);

        PlayerEntryData.get(world).setEntry(new Vec3d(0.5, 97.0, 0.5), 0f, 0f);
    }

    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

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

    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        ItemStack stack = super.getPickStack((WorldView) world, pos, state);
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof SuitcaseBlockEntity suitcase) {
            String key = suitcase.getBoundKeystoneName();
            if (key != null) {
                NbtCompound beTag = new NbtCompound();
                beTag.putString("id", SUITCASE_BE_ID);
                beTag.putString("BoundKeystone", key);
                beTag.putBoolean("Locked", suitcase.isLocked());

                beTag.putUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID, suitcase.getSuitcaseId());

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

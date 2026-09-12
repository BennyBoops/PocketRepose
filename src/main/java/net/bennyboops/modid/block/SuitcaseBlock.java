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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SuitcaseBlock extends BaseEntityBlock {

    private static final String SUITCASE_BE_ID = "pocket-repose:suitcase";
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    private static final VoxelShape SHAPE_N = Block.box(0, 0, 2, 16, 4, 14);
    private static final VoxelShape SHAPE_S = Block.box(0, 0, 2, 16, 4, 14);
    private static final VoxelShape SHAPE_E = Block.box(2, 0, 0, 14, 4, 16);
    private static final VoxelShape SHAPE_W = Block.box(2, 0, 0, 14, 4, 16);

    public SuitcaseBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(OPEN, false).setValue(FACING, Direction.NORTH).setValue(COLOR, DyeColor.BROWN));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(SuitcaseBlock::new);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (world.isClientSide) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SuitcaseBlockEntity suitcase)) return;

        CustomData comp = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (comp != null) {
            CompoundTag tag = comp.copyTag();
            suitcase.loadCustomOnly(tag, world.registryAccess());
        }

        SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(world.getServer());
        if (tracker != null) {
            tracker.updateSuitcaseLocationFromBlock(
                    suitcase.getSuitcaseId(),
                    world.dimension().location().toString(),
                    pos
            );
        }

        String key = suitcase.getBoundKeystoneName();
        if (key != null) {
            SuitcaseLocationTracker t = SuitcaseLocationTracker.get(world.getServer());
            if (t != null) {
                for (SuitcaseBlockEntity.EnteredPlayerData p : suitcase.getEnteredPlayers()) {
                    t.updateLocationFromBlock(key, p.uuid,
                            world.dimension().location().toString(),
                            suitcase.getSuitcaseId(),
                            pos, p.yaw, 0f);
                }
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos,
                            BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SuitcaseBlockEntity suitcase) {

                ItemStack stack = new ItemStack(this);
                String key = suitcase.getBoundKeystoneName();

                if (key != null) {
                    CompoundTag beTag = new CompoundTag();

                    beTag.putString("id", SUITCASE_BE_ID);
                    beTag.putString("BoundKeystone", key);
                    beTag.putBoolean("Locked", suitcase.isLocked());
                    beTag.putBoolean("DimensionLocked", suitcase.isDimensionLocked());

                    beTag.putUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID, suitcase.getSuitcaseId());

                    ListTag entered = new ListTag();
                    for (SuitcaseBlockEntity.EnteredPlayerData d : suitcase.getEnteredPlayers())
                        entered.add(d.toNbt());
                    beTag.put("EnteredPlayers", entered);

                    stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(beTag));

                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.literal("Bound to: " + (suitcase.isLocked() ? "§k" : "") + key.replace("_", " "))
                            .withStyle(ChatFormatting.GRAY));
                    lore.add(Component.literal(suitcase.isLocked() ? "§cLocked" : "§aUnlocked")
                            .withStyle(ChatFormatting.GRAY));
                    stack.set(DataComponents.LORE, new ItemLore(lore));
                }

                Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
            world.removeBlockEntity(pos);
        }
    }

    private static String sanitize(ItemStack stack) {
        String raw = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        return useItemOn(player.getMainHandItem(), state, world, pos, player, InteractionHand.MAIN_HAND, hit).result();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held,
                                              BlockState state,
                                              Level world,
                                              BlockPos pos,
                                              Player player,
                                              InteractionHand hand,
                                              BlockHitResult hit) {
        if (world.isClientSide) return ItemInteractionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SuitcaseBlockEntity suitcase)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!suitcase.canOpenInDimension(world)) {
            world.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, .3F, 2F);
            player.displayClientMessage(Component.literal("☒").withStyle(ChatFormatting.RED), true);
            return ItemInteractionResult.SUCCESS;
        }

        String key = suitcase.getBoundKeystoneName();

        if (held.getItem() instanceof KeystoneItem) {
            String name = sanitize(held);

            if (key != null && key.equals(name)) {
                boolean locked = !suitcase.isLocked();
                suitcase.setLocked(locked);
                world.playSound(null, pos,
                        locked ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN,
                        SoundSource.BLOCKS, .3F, 2F);
                player.displayClientMessage(Component.literal(locked ? "☒" : "☐").withStyle(ChatFormatting.GRAY), true);
                return ItemInteractionResult.SUCCESS;
            }

            if (suitcase.isLocked()) {
                world.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, .3F, 2F);
                player.displayClientMessage(Component.literal("☒").withStyle(ChatFormatting.RED), true);
                return ItemInteractionResult.FAIL;
            }

            if (name.equals("item.pocket-repose.keystone")) {
                player.displayClientMessage(Component.literal("Name the key to bind.").withStyle(ChatFormatting.RED), false);
                return ItemInteractionResult.FAIL;
            }

            if (!KeystoneItem.isValidKeystone(held)) return ItemInteractionResult.FAIL;

            suitcase.bindKeystone(name);
            world.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.BLOCKS, 2F, 0F);
            return ItemInteractionResult.SUCCESS;
        }

        if (!player.isShiftKeyDown() || held.isEmpty()) {
            if (key == null) {
                world.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, .5F, 2F);
                return ItemInteractionResult.FAIL;
            }
            if (suitcase.isLocked()) {
                world.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, .3F, 2F);
                return ItemInteractionResult.FAIL;
            }

            boolean open = state.getValue(OPEN);
            world.setBlockAndUpdate(pos, state.setValue(OPEN, !open));

            world.playSound(null, pos,
                    open ? SoundEvents.LADDER_BREAK : SoundEvents.LADDER_STEP,
                    SoundSource.BLOCKS, .3F, 0F);

            world.playSound(null, pos,
                    open ? SoundEvents.BAMBOO_WOOD_TRAPDOOR_CLOSE : SoundEvents.CHEST_LOCKED,
                    SoundSource.BLOCKS, .3F, open ? 0F : 2F);

            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (world.isClientSide) return;
        if (!(entity instanceof ServerPlayer player)) return;
        if (!state.getValue(OPEN) || !player.isShiftKeyDown()) return;

        ResourceLocation id = world.dimension().location();
        boolean inPocketDimension =
                "pocket-repose".equals(id.getNamespace()) &&
                        id.getPath().startsWith("pocket_dimension_");

        if (inPocketDimension && !PocketRepose.getAllowRecursion()) {
            world.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, .3F, 2F);
            player.displayClientMessage(Component.literal("§cSuitcase recursion is disabled"), true);
            return;
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SuitcaseBlockEntity suitcase)) return;

        String key = suitcase.getBoundKeystoneName();
        if (key == null) return;

        if (!suitcase.canOpenInDimension(world)) {
            world.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, .3F, 2F);
            player.displayClientMessage(Component.literal("☒").withStyle(ChatFormatting.RED), true);
            return;
        }

        ResourceLocation dimId = ResourceLocation.fromNamespaceAndPath("pocket-repose", "pocket_dimension_" + key);
        ServerLevel target = ensureDimensionExists(world.getServer(), key, dimId);

        if (target == null) {
            player.displayClientMessage(Component.literal("§cFailed to create pocket dimension"), true);
            return;
        }

        boolean first = suitcase.isFirstTimeEntering(player);
        suitcase.playerEntered(player);
        if (first) PocketRepose.ENTER_POCKET_DIMENSION.get().trigger(player);

        SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(world.getServer());
        if (tracker != null) {
            UUID sid = suitcase.getSuitcaseId();
            String dimStr = world.dimension().location().toString();

            tracker.setLastSuitcase(key, player.getStringUUID(), sid);

            tracker.updateLocation(
                    key,
                    player.getStringUUID(),
                    dimStr,
                    sid,
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(),
                    SuitcaseLocationTracker.LocationType.ENTRY
            );

            tracker.updateSuitcaseLocationFromBlock(sid, dimStr, pos);
        }

        player.stopRiding();
        player.hasImpulse = true;
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0f;

        PlayerEntryData ped = PlayerEntryData.get(target);
        Vec3 dest = ped.getEntryPos();
        DimensionTransition tpTarget = new DimensionTransition(target, dest, Vec3.ZERO, ped.getEntryYaw(), player.getXRot(), DimensionTransition.DO_NOTHING);
        player.changeDimension(tpTarget);

        player.connection.send(new ClientboundStopSoundPacket(null, null));
        world.playSound(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 2F, 1F);
    }


    private ServerLevel ensureDimensionExists(MinecraftServer server, String key, ResourceLocation dimId) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        ServerLevel existing = server.getLevel(dimKey);
        if (existing != null) return existing;

        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        ChunkGenerator generator = new PortalChunkGenerator(biomeRegistry);
        long seed = server.overworld().getSeed();

        RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setDimensionType(PocketRepose.POCKET_DIMENSION_TYPE_KEY)
                .setGenerator(generator)
                .setSeed(seed)
                .setShouldTickTime(false);

        RuntimeWorldHandle handle = Fantasy.get(server).getOrOpenPersistentWorld(dimId, config);
        ServerLevel world = handle.asWorld();

        world.setChunkForced(0, 0, true);
        generateInitialStructure(world);
        return world;
    }

    private void generateInitialStructure(ServerLevel world) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -64; y <= -61; y++) {
                    BlockPos portalPos = new BlockPos(x, y, z);
                    world.setBlock(portalPos, ModBlocks.PORTAL.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }

        BlockPos platformPos = new BlockPos(0, 96, 0);
        world.setBlock(platformPos, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);

        BlockPos exitPortal = new BlockPos(0, 100, 0);
        world.setBlock(exitPortal, ModBlocks.PORTAL.get().defaultBlockState(), Block.UPDATE_ALL);

        PlayerEntryData.get(world).setEntry(new Vec3(0.5, 97.0, 0.5), 0f, 0f);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_S;
            case EAST -> SHAPE_E;
            case WEST -> SHAPE_W;
            default -> SHAPE_N;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        return getShape(state, world, pos, ctx);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter world, BlockPos pos) {
        return 1F;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(OPEN, FACING, COLOR);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SuitcaseBlockEntity(pos, state);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(world, pos, state);
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof SuitcaseBlockEntity suitcase) {
            String key = suitcase.getBoundKeystoneName();
            if (key != null) {
                CompoundTag beTag = new CompoundTag();
                beTag.putString("id", SUITCASE_BE_ID);
                beTag.putString("BoundKeystone", key);
                beTag.putBoolean("Locked", suitcase.isLocked());

                beTag.putUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID, suitcase.getSuitcaseId());

                stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(beTag));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.literal("Bound to: " + (suitcase.isLocked() ? "§k" : "") + key.replace("_", " "))
                        .withStyle(ChatFormatting.GRAY));
                lore.add(Component.literal(suitcase.isLocked() ? "§cLocked" : "§aUnlocked")
                        .withStyle(ChatFormatting.GRAY));
                stack.set(DataComponents.LORE, new ItemLore(lore));
            }

            DyeColor color = state.getValue(COLOR);
            CompoundTag custom = new CompoundTag();
            custom.putString("Color", color.getName());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        }
        return stack;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.emptyList();
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level world, BlockPos pos, int type, int data) {
        super.triggerEvent(state, world, pos, type, data);
        BlockEntity be = world.getBlockEntity(pos);
        return be != null && be.triggerEvent(type, data);
    }
}

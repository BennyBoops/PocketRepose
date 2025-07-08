package net.bennyboops.modid.block;

import com.mojang.serialization.MapCodec;
import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.bennyboops.modid.data.PlayerEntryData;
import net.bennyboops.modid.item.KeystoneItem;
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
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SuitcaseBlock extends BlockWithEntity {
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
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SuitcaseBlockEntity suitcase) {
                NbtComponent comp = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
                if (comp != null) {
                    NbtCompound tag = comp.copyNbt();
                    suitcase.readNbt(tag, world.getRegistryManager());
                    String key = suitcase.getBoundKeystoneName();
                    if (key != null) {
                        for (SuitcaseBlockEntity.EnteredPlayerData p : suitcase.getEnteredPlayers()) {
                            suitcase.updatePlayerSuitcasePosition(p.uuid, pos);
                            SuitcaseBlockEntity.SUITCASE_REGISTRY.computeIfAbsent(key, k -> new HashMap<>()).put(p.uuid, pos);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SuitcaseBlockEntity suitcase) {
                ItemStack stack = new ItemStack(this);
                String key = suitcase.getBoundKeystoneName();
                if (key != null) {
                    NbtCompound beTag = new NbtCompound();
                    beTag.putString("BoundKeystone", key);
                    beTag.putBoolean("Locked", suitcase.isLocked());
                    beTag.putBoolean("DimensionLocked", suitcase.isDimensionLocked());
                    NbtList list = new NbtList();
                    for (SuitcaseBlockEntity.EnteredPlayerData d : suitcase.getEnteredPlayers()) list.add(d.toNbt());
                    beTag.put("EnteredPlayers", list);
                    stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(beTag));

                    List<Text> lore = new ArrayList<>();
                    if (!suitcase.getEnteredPlayers().isEmpty())
                        lore.add(Text.literal("⚠ Contains " + suitcase.getEnteredPlayers().size() + " Traveler(s)!").formatted(Formatting.RED));
                    lore.add(Text.literal("Bound to: " + (suitcase.isLocked() ? "§k" : "") + key.replace("_", " ")).formatted(Formatting.GRAY));
                    lore.add(Text.literal(suitcase.isLocked() ? "§cLocked" : "§aUnlocked").formatted(Formatting.GRAY));
                    stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
                }
                ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
            world.removeBlockEntity(pos);
        }
    }

    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack held = player.getStackInHand(hand);
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SuitcaseBlockEntity suitcase)) return ActionResult.PASS;
        if (!suitcase.canOpenInDimension(world)) {
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, .3F, 2F);
            player.sendMessage(Text.literal("§c☒"), true);
            return ActionResult.SUCCESS;
        }
        String key = suitcase.getBoundKeystoneName();
        if (held.getItem() instanceof KeystoneItem) {
            String name = held.getName().getString().toLowerCase().replaceAll("[^a-z0-9_]", "");
            if (key != null && key.equals(name)) {
                boolean locked = !suitcase.isLocked();
                suitcase.setLocked(locked);
                world.playSound(null, pos, locked ? SoundEvents.BLOCK_IRON_DOOR_CLOSE : SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, .3F, 2F);
                player.sendMessage(Text.literal(locked ? "§7☒" : "§7☐"), true);
                return ActionResult.SUCCESS;
            }
            if (suitcase.isLocked()) {
                world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, .3F, 2F);
                player.sendMessage(Text.literal("§c☒"), true);
                return ActionResult.FAIL;
            }
            if (name.equals("item.pocket-repose.keystone")) {
                player.sendMessage(Text.literal("§cName the key to bind."), false);
                return ActionResult.FAIL;
            }
            if (!KeystoneItem.isValidKeystone(held)) return ActionResult.FAIL;
            suitcase.bindKeystone(name);
            world.playSound(null, pos, SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.BLOCKS, 2F, 0F);
            return ActionResult.SUCCESS;
        }
        if (!player.isSneaking() || held.isEmpty()) {
            if (key == null) {
                world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, .5F, 2F);
                return ActionResult.FAIL;
            }
            if (suitcase.isLocked()) {
                world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, .3F, 2F);
                return ActionResult.FAIL;
            }
            boolean open = state.get(OPEN);
            world.setBlockState(pos, state.with(OPEN, !open));
            world.playSound(null, pos, open ? SoundEvents.BLOCK_LADDER_BREAK : SoundEvents.BLOCK_LADDER_STEP, SoundCategory.BLOCKS, .3F, 0F);
            world.playSound(null, pos, open ? SoundEvents.BLOCK_BAMBOO_WOOD_TRAPDOOR_CLOSE : SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, .3F, open ? 0F : 2F);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient && entity instanceof ServerPlayerEntity player && state.get(OPEN) && player.isSneaking()) {
            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof SuitcaseBlockEntity suitcase)) return;
            String key = suitcase.getBoundKeystoneName();
            if (key == null) return;
            RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("pocket-repose", "pocket_dimension_" + key));
            ServerWorld target = world.getServer().getWorld(dimKey);
            if (target == null) return;
            boolean first = suitcase.isFirstTimeEntering(player);
            suitcase.playerEntered(player);
            if (first) PocketRepose.ENTER_POCKET_DIMENSION.trigger(player);
            player.stopRiding();
            player.velocityModified = true;
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0f;
            PlayerEntryData ped = PlayerEntryData.get(target);
            Vec3d dest = ped.getEntryPos();
            TeleportTarget tpTarget = new TeleportTarget(target, dest, Vec3d.ZERO, ped.getEntryYaw(), player.getPitch(), TeleportTarget.NO_OP);
            player.teleportTo(tpTarget);
            player.networkHandler.sendPacket(new StopSoundS2CPacket(null, null));
            world.playSound(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 2F, 1F);
        }
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
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

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return getOutlineShape(state, world, pos, ctx);
    }

    @Override
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1F;
    }

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
                beTag.putString("BoundKeystone", key);
                beTag.putBoolean("Locked", suitcase.isLocked());
                stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(beTag));
                List<Text> lore = new ArrayList<>();
                lore.add(Text.literal("Bound to: " + (suitcase.isLocked() ? "§k" : "") + key.replace("_", " ")).formatted(Formatting.GRAY));
                lore.add(Text.literal(suitcase.isLocked() ? "§cLocked" : "§aUnlocked").formatted(Formatting.GRAY));
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

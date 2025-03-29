package net.bennyboops.modid.block;

import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.bennyboops.modid.item.KeystoneItem;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
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
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SuitcaseBlock extends BlockWithEntity {

    public static final BooleanProperty OPEN = BooleanProperty.of("open");
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.of("color", DyeColor.class);
    private final static VoxelShape SHAPE_N = Block.createCuboidShape(0, 0, 2, 16, 4, 14);
    private final static VoxelShape SHAPE_S = Block.createCuboidShape(0, 0, 2, 16, 4, 14);
    private final static VoxelShape SHAPE_E = Block.createCuboidShape(2, 0, 0, 14, 4, 16);
    private final static VoxelShape SHAPE_W = Block.createCuboidShape(2, 0, 0, 14, 4, 16);

    public SuitcaseBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState()
                .with(OPEN, false)
                .with(FACING, Direction.NORTH)
                .with(COLOR, DyeColor.BROWN)); // Default color
    }
    private static final Map<String, Map<String, BlockPos>> SUITCASE_REGISTRY = new HashMap<>();

    // Block placement and creation methods
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof SuitcaseBlockEntity suitcase) {
                // Transfer any existing NBT data from item to block entity
                NbtCompound blockEntityTag = itemStack.getSubNbt("BlockEntityTag");
                if (blockEntityTag != null) {
                    suitcase.readNbt(blockEntityTag);

                    // IMPORTANT: Register this new suitcase in the registry for any stored players
                    String keystoneName = suitcase.getBoundKeystoneName();
                    if (keystoneName != null) {
                        List<SuitcaseBlockEntity.EnteredPlayerData> players = suitcase.getEnteredPlayers();
                        for (SuitcaseBlockEntity.EnteredPlayerData player : players) {
                            Map<String, BlockPos> suitcases = SUITCASE_REGISTRY.computeIfAbsent(
                                    keystoneName, k -> new HashMap<>()
                            );
                            suitcases.put(player.uuid, pos);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof SuitcaseBlockEntity suitcase) {
                ItemStack itemStack = new ItemStack(this);
                String boundKeystone = suitcase.getBoundKeystoneName();
                if (boundKeystone != null) {
                    NbtCompound beNbt = new NbtCompound();
                    beNbt.putString("BoundKeystone", boundKeystone);
                    beNbt.putBoolean("Locked", suitcase.isLocked());
                    beNbt.putBoolean("DimensionLocked", suitcase.isDimensionLocked());

                    // Transfer entered players data to the item
                    NbtList playersList = new NbtList();
                    for (SuitcaseBlockEntity.EnteredPlayerData data : suitcase.getEnteredPlayers()) {
                        playersList.add(data.toNbt());
                    }
                    beNbt.put("EnteredPlayers", playersList);

                    // Add lore NBT for tooltip
                    NbtCompound display = new NbtCompound();
                    NbtList lore = new NbtList();
                    String displayName = boundKeystone.replace("_", " ");

                    // Create the lore text components
                    Text boundText;
                    if (suitcase.isLocked()) {
                        boundText = Text.literal("Bound to: §k" + displayName)
                                .formatted(Formatting.GRAY);
                    } else {
                        boundText = Text.literal("Bound to: " + displayName)
                                .formatted(Formatting.GRAY);
                    }

                    Text lockText = Text.literal(suitcase.isLocked() ? "§cLocked" : "§aUnlocked")
                            .formatted(Formatting.GRAY);

                    // Add warning if players are inside
                    if (!suitcase.getEnteredPlayers().isEmpty()) {
                        Text warningText = Text.literal("§c⚠ Contains " + suitcase.getEnteredPlayers().size() + " traveler(s)!")
                                .formatted(Formatting.RED);
                        lore.add(NbtString.of(Text.Serializer.toJson(warningText)));
                    }

                    lore.add(NbtString.of(Text.Serializer.toJson(boundText)));
                    lore.add(NbtString.of(Text.Serializer.toJson(lockText)));
                    display.put("Lore", lore);

                    itemStack.setSubNbt("display", display);
                    itemStack.setSubNbt("BlockEntityTag", beNbt);
                }

                ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), itemStack);
            }

            world.removeBlockEntity(pos);
        }
    }

    // Block interaction methods
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        ItemStack heldItem = player.getStackInHand(hand);
        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (!(blockEntity instanceof SuitcaseBlockEntity suitcase)) {
            return ActionResult.PASS;
        }

        if (!suitcase.canOpenInDimension(world)) {
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE,
                    SoundCategory.BLOCKS, 0.3F, 2.0F);
            player.sendMessage(Text.literal("§c☒"), true);
            return ActionResult.SUCCESS;
        }





        String boundKeystone = suitcase.getBoundKeystoneName();

        // Handle keystone interactions
        if (heldItem.getItem() instanceof KeystoneItem) {
            String keystoneName = heldItem.getName().getString().toLowerCase();

            // Handle locking with keystone
            if (boundKeystone != null && boundKeystone.equals(keystoneName)) {
                boolean newLockState = !suitcase.isLocked();
                suitcase.setLocked(newLockState);
                world.playSound(null, pos,
                        newLockState ? SoundEvents.BLOCK_IRON_DOOR_CLOSE : SoundEvents.BLOCK_IRON_DOOR_OPEN,
                        SoundCategory.BLOCKS, 0.3F, 2.0F);
                player.sendMessage(Text.literal(newLockState ? "§7☒" : "§7☐"), true);
                return ActionResult.SUCCESS;
            }

            // Prevent binding if suitcase is locked
            if (suitcase.isLocked()) {
                world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE,
                        SoundCategory.BLOCKS, 0.3F, 2.0F);
                player.sendMessage(Text.literal("§c☒"), true);
                return ActionResult.FAIL;
            }

            // Binding logic
            if (keystoneName.equals("item.pocket-repose.keystone")) {
                player.sendMessage(Text.literal("§cName the key to bind."), false);
                return ActionResult.FAIL;
            }

            if (!KeystoneItem.isValidKeystone(heldItem)) {
                return ActionResult.FAIL;
            }

            suitcase.bindKeystone(keystoneName);
            world.playSound(null, pos, SoundEvents.ITEM_LODESTONE_COMPASS_LOCK,
                    SoundCategory.BLOCKS, 2.0F, 0.0F);

            return ActionResult.SUCCESS;
        }

        // Handle opening/closing
        if (!player.isSneaking() || heldItem.isEmpty()) {
            if (boundKeystone == null) {
                world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE,
                        SoundCategory.BLOCKS, 0.5F, 2.0F);
                return ActionResult.FAIL;
            }

            if (suitcase.isLocked()) {
                world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE,
                        SoundCategory.BLOCKS, 0.3F, 2.0F);
                return ActionResult.FAIL;
            }

            boolean isOpen = state.get(OPEN);
            world.setBlockState(pos, state.with(OPEN, !isOpen));

            if (!isOpen) {
                world.playSound(null, pos, SoundEvents.BLOCK_LADDER_STEP,
                        SoundCategory.BLOCKS, 0.3F, 0.0F);
                world.playSound(null, pos, SoundEvents.BLOCK_CHEST_LOCKED,
                        SoundCategory.BLOCKS, 0.3F, 2.0F);
            } else {
                world.playSound(null, pos, SoundEvents.BLOCK_LADDER_BREAK,
                        SoundCategory.BLOCKS, 0.3F, 0.0F);
                world.playSound(null, pos, SoundEvents.BLOCK_BAMBOO_WOOD_TRAPDOOR_CLOSE,
                        SoundCategory.BLOCKS, 0.3F, 0.0F);
            }

            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient && entity instanceof ServerPlayerEntity player) {
            // Only teleport if suitcase is open and player is sneaking
            if (!state.get(OPEN) || !player.isSneaking()) {
                return;
            }

            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (!(blockEntity instanceof SuitcaseBlockEntity suitcase)) {
                return;
            }

            String keystoneName = suitcase.getBoundKeystoneName();
            if (keystoneName == null) {
                return;
            }

            // Create dimension ID for the pocket dimension
            String dimensionName = "pocket_dimension_" + keystoneName;
            Identifier dimensionId = new Identifier("pocket-repose", dimensionName);
            RegistryKey<World> dimensionKey = RegistryKey.of(RegistryKeys.WORLD, dimensionId);

            ServerWorld targetWorld = world.getServer().getWorld(dimensionKey);
            if (targetWorld != null) {
                // Store the player's entry point before teleporting
                suitcase.playerEntered(player);

                // Prepare player for teleport
                player.stopRiding();
                player.velocityModified = true;
                player.setVelocity(Vec3d.ZERO);
                player.fallDistance = 0f;

                // Request initial position update
                player.requestTeleport(2.5, 66, 5.5);

                // Execute teleport on next tick
                world.getServer().execute(() -> {
                    player.teleport(
                            targetWorld,
                            17.5,
                            97,
                            9.5,
                            0.0f,
                            player.getPitch()
                    );

                    world.playSound(null, pos,
                            SoundEvents.ITEM_BUNDLE_DROP_CONTENTS,
                            SoundCategory.PLAYERS, 2.0f, 1.0f);
                });
            }
        }
    }



    // Block state and shape methods
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case NORTH -> SHAPE_N;
            case SOUTH -> SHAPE_S;
            case EAST -> SHAPE_E;
            case WEST -> SHAPE_W;
            default -> SHAPE_N;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1.0f;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
        builder.add(FACING);
        builder.add(COLOR);
    }

    // Block entity methods
    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SuitcaseBlockEntity(pos, state);
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        ItemStack stack = super.getPickStack(world, pos, state);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof SuitcaseBlockEntity suitcase) {
            String boundKeystone = suitcase.getBoundKeystoneName();
            if (boundKeystone != null) {
                NbtCompound beNbt = new NbtCompound();
                beNbt.putString("BoundKeystone", boundKeystone);
                beNbt.putBoolean("Locked", suitcase.isLocked());

                // Add color NBT
                DyeColor color = state.get(COLOR);
                NbtCompound nbt = stack.getOrCreateNbt();
                nbt.putString("Color", color.getName());

                // Add display NBT
                NbtCompound display = new NbtCompound();
                NbtList lore = new NbtList();
                String displayName = boundKeystone.replace("_", " ");

                Text boundText;
                if (suitcase.isLocked()) {
                    boundText = Text.literal("Bound to: §k" + displayName)
                            .formatted(Formatting.GRAY);
                } else {
                    boundText = Text.literal("Bound to: " + displayName)
                            .formatted(Formatting.GRAY);
                }

                Text lockText = Text.literal(suitcase.isLocked() ? "§cLocked" : "§aUnlocked")
                        .formatted(Formatting.GRAY);

                lore.add(NbtString.of(Text.Serializer.toJson(boundText)));
                lore.add(NbtString.of(Text.Serializer.toJson(lockText)));
                display.put("Lore", lore);
                stack.setSubNbt("display", display);
                stack.setSubNbt("BlockEntityTag", beNbt);
            }
        }
        return stack;
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        super.onBreak(world, pos, state, player);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        return Collections.emptyList(); // Items are handled in onStateReplaced
    }

    @Override
    public boolean onSyncedBlockEvent(BlockState state, World world, BlockPos pos, int type, int data) {
        super.onSyncedBlockEvent(state, world, pos, type, data);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity != null && blockEntity.onSyncedBlockEvent(type, data);
    }

}
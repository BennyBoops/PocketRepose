package net.bennyboops.modid;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.bennyboops.modid.block.ModBlocks;
import net.bennyboops.modid.block.SuitcaseBlock;
import net.bennyboops.modid.block.entity.ModBlockEntities;
import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.bennyboops.modid.criterion.EnterPocketDimensionCriterion;
import net.bennyboops.modid.data.PlayerEntryData;
import net.bennyboops.modid.data.MobEntryData;
import net.bennyboops.modid.item.KeystoneItem;
import net.bennyboops.modid.item.ModItemGroups;
import net.bennyboops.modid.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PocketRepose implements ModInitializer {

	public static final Identifier POCKET_DIMENSION_TYPE_ID =
			new Identifier("pocket-repose", "pocket_dimension_type");
	public static final RegistryKey<DimensionType> POCKET_DIMENSION_TYPE_KEY =
			RegistryKey.of(RegistryKeys.DIMENSION_TYPE, POCKET_DIMENSION_TYPE_ID);
	public static final String MOD_ID = "pocket-repose";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final EnterPocketDimensionCriterion ENTER_POCKET_DIMENSION = new EnterPocketDimensionCriterion();


	@Override
	public void onInitialize() {

		Criteria.register(ENTER_POCKET_DIMENSION);

		registerPocketCommands();
		registerSuitcaseMobTeleport();
		registerMobEntrySetter();
		registerPlayerEntrySetter();
		registerKeyRescueMob();

		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
		ModItemGroups.registerItemGroups();
		ModBlockEntities.registerBlockEntities();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Path registryFile = server.getSavePath(WorldSavePath.ROOT)
					.resolve("data")
					.resolve("pocket-repose")
					.resolve("dimension_registry")
					.resolve("registry.txt");

			if (Files.exists(registryFile)) {
				try {
					List<String> dimensions = Files.readAllLines(registryFile);
					for (String dimName : dimensions) {
						Identifier worldId = new Identifier("pocket-repose", dimName);

						RuntimeWorldConfig cfg = new RuntimeWorldConfig()
								.setDimensionType(POCKET_DIMENSION_TYPE_KEY)
								.setGenerator(server.getOverworld().getChunkManager().getChunkGenerator())
								.setSeed(server.getOverworld().getSeed());

						Fantasy.get(server)
								.getOrOpenPersistentWorld(worldId, cfg);
					}
				} catch (IOException e) {
					PocketRepose.LOGGER.error("Failed to reload pocket dimensions", e);
				}
			}
		});
		LOGGER.info("Initializing " + MOD_ID);



		// Initialize player entry location
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					LiteralArgumentBuilder.<ServerCommandSource>literal("pocketRepose")
							.then(CommandManager.literal("setPlayerEntry")
									.executes(ctx -> {
										ServerCommandSource src = ctx.getSource();
										ServerWorld world = src.getWorld();
										Identifier id = world.getRegistryKey().getValue();
										if (!"pocket-repose".equals(id.getNamespace())
												|| !id.getPath().startsWith("pocket_dimension_")) {
											src.sendError(Text.literal("§cNot in a pocket dimension"));
											return 0;
										}
										Vec3d pos   = src.getPosition();
										float yaw   = src.getEntity().getYaw();
										float pitch = src.getEntity().getPitch();

										net.bennyboops.modid.data.PlayerEntryData.get(world)
												.setEntry(pos, yaw, pitch);

										src.sendFeedback(() -> Text.literal(
												String.format("§aPlayer entry set to %.2f, %.2f, %.2f",
														pos.x, pos.y, pos.z)
										), false);
										return 1;
									})));
		});
	}

	private void registerPocketCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					LiteralArgumentBuilder.<ServerCommandSource>literal("pocketRepose")
							//mob entry setter: /pocketRepose setmobentry
							.then(CommandManager.literal("setMobEntry")
									.executes(ctx -> {
										ServerCommandSource src = ctx.getSource();
										ServerWorld world = src.getWorld();
										Identifier id = world.getRegistryKey().getValue();
										if (!id.getNamespace().equals("pocket-repose")
												|| !id.getPath().startsWith("pocket_dimension_")) {
											src.sendError(Text.literal("§cNot in a pocket dimension"));
											return 0;
										}
										Vec3d pos   = src.getPosition();
										float yaw   = src.getEntity().getYaw();
										float pitch = src.getEntity().getPitch();

										MobEntryData.get(world).setEntry(pos, yaw, pitch);
										src.sendFeedback(() -> Text.literal(
												String.format("§aMob entry set to %.2f, %.2f, %.2f", pos.x, pos.y, pos.z)
										), false);
										return 1;
									})
							)
							//player entry setter: /pocketRepose setplayerentry
							.then(CommandManager.literal("setPlayerEntry")
									.executes(ctx -> {
										ServerCommandSource src = ctx.getSource();
										ServerWorld world = src.getWorld();
										Identifier id = world.getRegistryKey().getValue();
										if (!id.getNamespace().equals("pocket-repose")
												|| !id.getPath().startsWith("pocket_dimension_")) {
											src.sendError(Text.literal("§cNot in a pocket dimension"));
											return 0;
										}
										Vec3d pos   = src.getPosition();
										float yaw   = src.getEntity().getYaw();
										float pitch = src.getEntity().getPitch();

										PlayerEntryData.get(world).setEntry(pos, yaw, pitch);
										src.sendFeedback(() -> Text.literal(
												String.format("§aPlayer entry set to %.2f, %.2f, %.2f", pos.x, pos.y, pos.z)
										), false);
										return 1;
									})
							)

							//reset command
							.then(CommandManager.literal("resetPlayerEntry")
									.then(CommandManager.argument("dimension", StringArgumentType.word())
											.executes(ctx -> resetPocketDimension(ctx, StringArgumentType.getString(ctx, "dimension")))
									)
							)
			);
		});
	}


	private int resetPocketDimension(CommandContext<ServerCommandSource> ctx, String dimSuffix) {
		ServerCommandSource src = ctx.getSource();

		Identifier dimId = new Identifier("pocket-repose", "pocket_dimension_" + dimSuffix);
		RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
		ServerWorld targetWorld = src.getServer().getWorld(worldKey);

		if (targetWorld == null) {
			src.sendError(Text.literal("§cPocket dimension '"
					+ dimSuffix + "' not found"));
			return 0;
		}

		BlockPos plankPos = new BlockPos(17, 96, 9);
		targetWorld.setBlockState(plankPos, Blocks.OAK_PLANKS.getDefaultState());

		for (int y = 97; y <= 99; y++) {
			BlockPos airPos = new BlockPos(17, y, 9);
			targetWorld.setBlockState(airPos, Blocks.AIR.getDefaultState());
		}

		BlockPos portalPos = new BlockPos(17, 100, 9);
		targetWorld.setBlockState(portalPos, ModBlocks.PORTAL.getDefaultState());

		PlayerEntryData playerData = PlayerEntryData.get(targetWorld);
		playerData.setEntry(new Vec3d(17.5, 97.0, 9.5), 0f, 0f);

		src.sendFeedback(() -> Text.literal(
				"§aPocket dimension '" + dimSuffix + "' entry reset"
		), false);

		return 1;
	}





	private void registerPlayerEntrySetter() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack s = player.getStackInHand(hand);
			if (world.isClient || hand != Hand.MAIN_HAND || s.getItem() != Items.BONE)
				return TypedActionResult.pass(s);

			if (!(world instanceof ServerWorld sw)) return TypedActionResult.pass(s);
			Identifier id = sw.getRegistryKey().getValue();
			if (!"pocket-repose".equals(id.getNamespace())
					|| !id.getPath().startsWith("pocket_dimension_"))
				return TypedActionResult.pass(s);

			Vec3d pos   = player.getPos();
			float yaw   = player.getYaw();
			float pitch = player.getPitch();
			PlayerEntryData.get(sw).setEntry(pos, yaw, pitch);

			player.sendMessage(Text.literal(
					String.format("§aPlayer entry location set to %.1f, %.1f, %.1f",
							pos.x, pos.y, pos.z)
			), true);

			return TypedActionResult.success(s);
		});
	}

	private void registerMobEntrySetter() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (world.isClient || hand != Hand.MAIN_HAND) {
				return TypedActionResult.pass(stack);
			}
			if (stack.getItem() != Items.LEAD) {
				return TypedActionResult.pass(stack);
			}
			if (!(world instanceof ServerWorld sw)) {
				return TypedActionResult.pass(stack);
			}
			Identifier id = sw.getRegistryKey().getValue();
			if (!"pocket-repose".equals(id.getNamespace())
					|| !id.getPath().startsWith("pocket_dimension_")) {
				return TypedActionResult.pass(stack);
			}

			Vec3d pos   = player.getPos();
			float yaw   = player.getYaw();
			float pitch = player.getPitch();
			MobEntryData.get(sw).setEntry(pos, yaw, pitch);

			player.sendMessage(Text.literal(
					String.format("§aMob entry location set to %.1f, %.1f, %.1f",
							pos.x, pos.y, pos.z)
			), true);

			return TypedActionResult.success(stack);
		});
	}

	private void registerSuitcaseMobTeleport() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient) return ActionResult.PASS;

			ItemStack stack = player.getStackInHand(hand);
			if (!(stack.getItem() instanceof BlockItem bi)) {
				return ActionResult.PASS;
			}

			Block heldBlock = bi.getBlock();
			if (!(heldBlock instanceof SuitcaseBlock)) {
				return ActionResult.PASS;
			}

			NbtCompound beNbt = stack.getSubNbt("BlockEntityTag");
			if (beNbt == null || !beNbt.contains("BoundKeystone")) {
				player.sendMessage(Text.literal("§c☒"), true);
				world.playSound(
						null,
						player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
						SoundCategory.PLAYERS,
						0.3f, 1.5f
				);
				return ActionResult.FAIL;
			}
			if (beNbt.getBoolean("Locked")) {
				player.sendMessage(Text.literal("§c☒"), true);
				world.playSound(
						null,
						player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
						SoundCategory.PLAYERS,
						0.3f, 1.5f
				);
				return ActionResult.FAIL;
			}

			String keystone = beNbt.getString("BoundKeystone");

			Identifier dimId = new Identifier("pocket-repose", "pocket_dimension_" + keystone);
			RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
			ServerWorld targetWorld = world.getServer().getWorld(dimKey);
			if (targetWorld == null) {
				player.sendMessage(Text.literal("§cPocket dimension not found"), true);
				return ActionResult.FAIL;
			}

			if (!(entity instanceof LivingEntity mob)) {
				return ActionResult.PASS;
			}

			MobEntryData data = MobEntryData.get(targetWorld);
			Vec3d dest   = data.getEntryPos();
			float yaw    = data.getEntryYaw();
			float pitch  = data.getEntryPitch();

			TeleportTarget tpTarget = new TeleportTarget(
					dest, Vec3d.ZERO, yaw, pitch
			);
			FabricDimensions.teleport(mob, targetWorld, tpTarget);

			world.playSound(
					null,
					player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_BUNDLE_DROP_CONTENTS,
					SoundCategory.PLAYERS,
					2.0f, 1.0f
			);
			world.playSound(
					null,
					player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_ITEM_PICKUP,
					SoundCategory.PLAYERS,
					0.5f, 1.0f
			);
			return ActionResult.SUCCESS;
		});
	}

	private void registerKeyRescueMob() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient) {
				return ActionResult.PASS;
			}

			ItemStack held = player.getStackInHand(hand);
			if (!(held.getItem() instanceof KeystoneItem)) {
				return ActionResult.PASS;
			}

			Identifier dimId = world.getRegistryKey().getValue();
			String namespace = dimId.getNamespace();
			String path      = dimId.getPath();
			String prefix    = "pocket_dimension_";
			if (!namespace.equals("pocket-repose") || !path.startsWith(prefix)) {
				return ActionResult.PASS;
			}

			String keystoneName = path.substring(prefix.length());

			if (!(entity instanceof LivingEntity mob)) {
				return ActionResult.PASS;
			}

			String playerUuid = player.getUuidAsString();
			BlockPos suitcasePos = SuitcaseBlockEntity.findSuitcasePosition(keystoneName, playerUuid);
			if (suitcasePos == null) {
				player.sendMessage(Text.literal("§cNo suitcase found"), true);
				return ActionResult.FAIL;
			}

			ServerWorld overworld = world.getServer().getWorld(World.OVERWORLD);
			if (overworld == null) {
				player.sendMessage(Text.literal("§cOverworld is not loaded"), true);
				return ActionResult.FAIL;
			}

			Vec3d exitPos = new Vec3d(
					suitcasePos.getX() + 0.5,
					suitcasePos.getY() + 0.5,
					suitcasePos.getZ() + 0.5
			);
			float yaw   = mob.getYaw();
			float pitch = mob.getPitch();
			TeleportTarget tpTarget = new TeleportTarget(exitPos, Vec3d.ZERO, yaw, pitch);

			FabricDimensions.teleport(mob, overworld, tpTarget);

			overworld.playSound(
					null,
					exitPos.x, exitPos.y, exitPos.z,
					SoundEvents.ITEM_BUNDLE_DROP_CONTENTS,
					SoundCategory.PLAYERS,
					2.0f, 1.0f
			);

			world.playSound(
					null,
					player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_BUNDLE_DROP_CONTENTS,
					SoundCategory.PLAYERS,
					2.0f, 1.0f
			);
//			player.sendMessage(Text.literal("§aMob retrieved"), true);

			return ActionResult.SUCCESS;
		});
	}
}
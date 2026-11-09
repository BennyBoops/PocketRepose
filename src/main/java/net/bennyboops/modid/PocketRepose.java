package net.bennyboops.modid;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.bennyboops.modid.block.ModBlocks;
import net.bennyboops.modid.block.PocketTravelHandler;
import net.bennyboops.modid.block.PocketPortalBlock;
import net.bennyboops.modid.block.SuitcaseBlock;
import net.bennyboops.modid.block.entity.ModBlockEntities;
import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.bennyboops.modid.criterion.EnterPocketDimensionCriterion;
import net.bennyboops.modid.data.MobEntryData;
import net.bennyboops.modid.data.PlayerEntryData;
import net.bennyboops.modid.data.PocketReposeSettingsData;
import net.bennyboops.modid.data.SuitcaseLocationTracker;
import net.bennyboops.modid.data.SuitcaseRegistrySavedData;
import net.bennyboops.modid.item.KeystoneItem;
import net.bennyboops.modid.item.ModItemGroups;
import net.bennyboops.modid.item.ModItems;
import net.bennyboops.modid.util.VoidChunkGenerator;
import net.bennyboops.modid.world.Fantasy;
import net.bennyboops.modid.world.PortalChunkGenerator;
import net.bennyboops.modid.world.PortalChunkHandler;
import net.bennyboops.modid.world.RuntimeWorldConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.bennyboops.modid.util.StackData;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.network.packet.s2c.play.PlayerSpawnPositionS2CPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class PocketRepose implements ModInitializer {

	public static final Identifier POCKET_DIMENSION_TYPE_ID =
			Identifier.of("pocket-repose", "pocket_dimension_type");
	public static final RegistryKey<DimensionType> POCKET_DIMENSION_TYPE_KEY =
			RegistryKey.of(RegistryKeys.DIMENSION_TYPE, POCKET_DIMENSION_TYPE_ID);
	public static final String MOD_ID = "pocket-repose";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final EnterPocketDimensionCriterion ENTER_POCKET_DIMENSION =
			Registry.register(
					Registries.CRITERION,
					Identifier.of(PocketRepose.MOD_ID, "enter_pocket_dimension"),
					new EnterPocketDimensionCriterion()
			);

	@Override
	public void onInitialize() {

		LOGGER.info("Initializing " + MOD_ID);

		Registry.register(Registries.CHUNK_GENERATOR,
				Identifier.of("pocket-repose", "void_chunk_generator"),
				VoidChunkGenerator.CODEC);

		Registry.register(Registries.CHUNK_GENERATOR,
				Identifier.of("pocket-repose", "portal_chunk_generator"),
				PortalChunkGenerator.CODEC);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SuitcaseRegistrySavedData.onServerStart(server);
			PocketReposeSettingsData.onServerStart(server);
		});

		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
		ModItemGroups.registerItemGroups();
		ModBlockEntities.registerBlockEntities();

		PortalChunkHandler.initialize();
		PocketTravelHandler.initialize();

		registerPocketCommands();
		registerSuitcaseMobTeleport();
		registerMobEntrySetter();
		registerPlayerEntrySetter();
		registerKeyRescueMob();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Path registryFile = server.getSavePath(WorldSavePath.ROOT)
					.resolve("data")
					.resolve("pocket-repose")
					.resolve("dimension_registry")
					.resolve("registry.txt");

			if (!Files.exists(registryFile)) return;

			try {
				for (String dimName : Files.readAllLines(registryFile)) {
					if (dimName.isBlank()) continue;
					Identifier worldId = Identifier.of("pocket-repose", dimName.trim());
					RuntimeWorldConfig config = KeystoneItem.isWorldDimension(server, dimName.trim())
							? createWorldDimensionConfig(server)
							: createPocketWorldConfig(server);
					Fantasy.get(server).getOrOpenPersistentWorld(worldId, config);
				}
			} catch (IOException e) {
				LOGGER.error("Failed to reload pocket dimensions", e);
			}
		});

		ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register((originalEntity, newEntity, origin, destination) -> {
			if (!(newEntity instanceof ItemEntity itemEntity)) return;

			ItemStack stack = itemEntity.getStack();
			if (!(stack.getItem() instanceof BlockItem bi) || !SuitcaseBlock.isPocketBlock(bi.getBlock())) return;

			NbtComponent beData = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
			if (beData == null) return;

			NbtCompound nbt = beData.copyNbt();
			String keystone = nbt.getString("BoundKeystone");
			if (keystone.isEmpty()) return;

			UUID suitcaseId = null;
			if (nbt.containsUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID)) {
				suitcaseId = nbt.getUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID);
			}

			SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(destination.getServer());
			if (tracker == null) return;

			String dimStr = destination.getRegistryKey().getValue().toString();

			if (suitcaseId != null) {
				tracker.updateSuitcaseLocation(
						suitcaseId,
						dimStr,
						itemEntity.getX(),
						itemEntity.getY() + 1.0,
						itemEntity.getZ(),
						SuitcaseLocationTracker.LocationType.ITEM_ENTITY
				);
			}

			if (nbt.contains("EnteredPlayers", NbtElement.LIST_TYPE)) {
				NbtList players = nbt.getList("EnteredPlayers", NbtElement.COMPOUND_TYPE);
				for (int i = 0; i < players.size(); i++) {
					NbtCompound p = players.getCompound(i);
					String uuid = p.getString("UUID");
					tracker.updateLocation(
							keystone,
							uuid,
							dimStr,
							suitcaseId,
							itemEntity.getX(),
							itemEntity.getY() + 1.0,
							itemEntity.getZ(),
							0, 0,
							SuitcaseLocationTracker.LocationType.ITEM_ENTITY
					);
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(server);
			if (tracker == null) return;

			long ticks = server.getTicks();

			if (ticks % 20 == 0) {
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					String dimStr = player.getServerWorld().getRegistryKey().getValue().toString();
					for (int i = 0; i < player.getInventory().size(); i++) {
						ItemStack stack = player.getInventory().getStack(i);
						scanStackRecursivelyForSuitcases(
								tracker,
								stack,
								dimStr,
								player.getX(), player.getY() + 1.0, player.getZ(),
								player.getYaw(), player.getPitch(),
								MAX_NESTED_CONTAINER_DEPTH
						);

					}
				}
			}

			if (ticks % 40 == 0) {
				for (ServerWorld w : server.getWorlds()) {
					String dimStr = w.getRegistryKey().getValue().toString();

					Box searchBox = new Box(
							w.getWorldBorder().getCenterX() - w.getWorldBorder().getSize() / 2,
							w.getBottomY(),
							w.getWorldBorder().getCenterZ() - w.getWorldBorder().getSize() / 2,
							w.getWorldBorder().getCenterX() + w.getWorldBorder().getSize() / 2,
							w.getTopY(),
							w.getWorldBorder().getCenterZ() + w.getWorldBorder().getSize() / 2
					);

					List<ItemEntity> itemEntities = w.getEntitiesByClass(
							ItemEntity.class,
							searchBox,
							itemEntity -> {
								ItemStack st = itemEntity.getStack();
								if (st.isEmpty()) return false;

								if (st.getItem() instanceof BlockItem b && SuitcaseBlock.isPocketBlock(b.getBlock())) return true;

								return StackData.hasContainedStacks(st);
							}
					);

					for (ItemEntity item : itemEntities) {
						scanStackRecursivelyForSuitcases(
								tracker,
								item.getStack(),
								dimStr,
								item.getX(), item.getY() + 1.0, item.getZ(),
								0, 0,
								MAX_NESTED_CONTAINER_DEPTH
						);
					}

				}
			}

			if (ticks % 100 == 0) {
				for (ServerWorld w : server.getWorlds()) {
					if (!w.getRegistryKey().equals(World.OVERWORLD)
							&& !w.getRegistryKey().equals(World.NETHER)
							&& !w.getRegistryKey().equals(World.END)) {
						continue;
					}

					for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
						if (!player.getServerWorld().equals(w)) continue;

						ChunkPos playerChunk = player.getChunkPos();
						int radius = 8;

						for (int cx = playerChunk.x - radius; cx <= playerChunk.x + radius; cx++) {
							for (int cz = playerChunk.z - radius; cz <= playerChunk.z + radius; cz++) {
								if (!w.isChunkLoaded(cx, cz)) continue;

								WorldChunk chunk = w.getChunk(cx, cz);
								for (BlockEntity be : chunk.getBlockEntities().values()) {
									scanContainerForSuitcases(tracker, be, w);
								}
							}
						}
					}
				}
			}
		});
	}
	private static final int MAX_NESTED_CONTAINER_DEPTH = 4;
	private static void scanStackRecursivelyForSuitcases(SuitcaseLocationTracker tracker, ItemStack stack, String dimStr, double x, double y, double z, float yaw, float pitch, int depth) {
		if (stack.isEmpty() || depth <= 0) return;

		updateSuitcaseLocationFromItem(tracker, stack, dimStr, x, y, z, yaw, pitch);

		for (ItemStack inner : StackData.getContainedStacks(stack)) {
			scanStackRecursivelyForSuitcases(tracker, inner, dimStr, x, y, z, yaw, pitch, depth - 1);
		}
	}
	private static void scanContainerForSuitcases(SuitcaseLocationTracker tracker, BlockEntity blockEntity, ServerWorld world) {
		if (!(blockEntity instanceof Inventory inventory)) return;

		BlockPos containerPos = blockEntity.getPos();
		String dimStr = world.getRegistryKey().getValue().toString();

		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);

			scanStackRecursivelyForSuitcases(
					tracker,
					stack,
					dimStr,
					containerPos.getX() + 0.5,
					containerPos.getY() + 1.0,
					containerPos.getZ() + 0.5,
					0, 0,
					MAX_NESTED_CONTAINER_DEPTH
			);

			if (stack.isEmpty()) continue;

			if (!(stack.getItem() instanceof BlockItem bi) || !SuitcaseBlock.isPocketBlock(bi.getBlock())) continue;

			NbtComponent beData = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
			if (beData == null) continue;

			NbtCompound nbt = beData.copyNbt();
			String keystone = nbt.getString("BoundKeystone");
			if (keystone.isEmpty()) continue;

			UUID suitcaseId = null;
			if (nbt.containsUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID)) {
				suitcaseId = nbt.getUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID);
			}

			if (suitcaseId != null) {
				tracker.updateSuitcaseLocation(
						suitcaseId,
						dimStr,
						containerPos.getX() + 0.5,
						containerPos.getY() + 1.0,
						containerPos.getZ() + 0.5,
						SuitcaseLocationTracker.LocationType.ITEM_ENTITY
				);
			}

			if (!nbt.contains("EnteredPlayers", NbtElement.LIST_TYPE)) continue;

			NbtList players = nbt.getList("EnteredPlayers", NbtElement.COMPOUND_TYPE);
			for (int j = 0; j < players.size(); j++) {
				NbtCompound p = players.getCompound(j);
				String uuid = p.getString("UUID");

				tracker.updateLocation(
						keystone,
						uuid,
						dimStr,
						suitcaseId,
						containerPos.getX() + 0.5,
						containerPos.getY() + 1.0,
						containerPos.getZ() + 0.5,
						0, 0,
						SuitcaseLocationTracker.LocationType.ITEM_ENTITY
				);
			}

		}
	}
	private static void updateSuitcaseLocationFromItem(SuitcaseLocationTracker tracker, ItemStack stack, String dimStr, double x, double y, double z, float yaw, float pitch) {

		if (stack.isEmpty()) return;
		if (!(stack.getItem() instanceof BlockItem bi)) return;
		if (!SuitcaseBlock.isPocketBlock(bi.getBlock())) return;

		NbtComponent beData = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
		if (beData == null) return;

		NbtCompound nbt = beData.copyNbt();
		String keystone = nbt.getString("BoundKeystone");
		if (keystone.isEmpty()) return;

		UUID suitcaseId = null;
		if (nbt.containsUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID)) {
			suitcaseId = nbt.getUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID);
		}

		if (suitcaseId != null) {
			tracker.updateSuitcaseLocation(
					suitcaseId,
					dimStr,
					x, y, z,
					SuitcaseLocationTracker.LocationType.ITEM_ENTITY
			);
		}

		if (!nbt.contains("EnteredPlayers", NbtElement.LIST_TYPE)) return;

		NbtList players = nbt.getList("EnteredPlayers", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < players.size(); i++) {
			NbtCompound p = players.getCompound(i);
			String uuid = p.getString("UUID");

			tracker.updateLocation(
					keystone,
					uuid,
					dimStr,
					suitcaseId,
					x, y, z,
					yaw, pitch,
					SuitcaseLocationTracker.LocationType.ITEM_ENTITY
			);
		}
	}
	private static UUID ensureSuitcaseIdOnSuitcaseStack(ItemStack stack, NbtCompound beNbt) {
		if (beNbt.containsUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID)) {
			return beNbt.getUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID);
		}
		UUID id = UUID.randomUUID();
		beNbt.putUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID, id);
		return id;
	}
	private static void updateSuitcaseLocation(SuitcaseLocationTracker tracker, ItemStack stack, ServerWorld world, double x, double y, double z, float yaw, float pitch) {

		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)
				|| !SuitcaseBlock.isPocketBlock(bi.getBlock())) {
			return;
		}

		NbtComponent beData = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
		if (beData == null) return;

		NbtCompound nbt = beData.copyNbt();
		String keystone = nbt.getString("BoundKeystone");

		if (keystone.isEmpty() || !nbt.contains("EnteredPlayers", NbtElement.LIST_TYPE)) {
			return;
		}

		UUID sid = nbt.containsUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID)
				? nbt.getUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID)
				: null;

		if (sid != null) {
			tracker.updateSuitcaseLocation(
					sid,
					world.getRegistryKey().getValue().toString(),
					x, y, z,
					SuitcaseLocationTracker.LocationType.ITEM_ENTITY
			);
		}

		NbtList players = nbt.getList("EnteredPlayers", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < players.size(); i++) {
			NbtCompound playerData = players.getCompound(i);
			String uuid = playerData.getString("UUID");

			SuitcaseLocationTracker.LocationData current = tracker.getLocation(keystone, uuid);
			if (current == null || current.type != SuitcaseLocationTracker.LocationType.DESTROYED) {
				tracker.updateLocation(
						keystone, uuid,
						world.getRegistryKey().getValue().toString(),
						sid,
						x, y, z, yaw, pitch,
						SuitcaseLocationTracker.LocationType.ITEM_ENTITY
				);
			}
		}
	}
	/**
	 * Builds the config used for every pocket dimension. Game rules, difficulty, time and weather are
	 * mirrored from the overworld so that /gamerule and /difficulty apply inside pocket dimensions too,
	 * and a freshly created pocket dimension behaves the same as one reloaded after a restart.
	 */
	public static RuntimeWorldConfig createPocketWorldConfig(MinecraftServer server) {
		Registry<Biome> biomeRegistry = server.getRegistryManager().get(RegistryKeys.BIOME);
		return new RuntimeWorldConfig()
				.setDimensionType(POCKET_DIMENSION_TYPE_KEY)
				.setGenerator(new PortalChunkGenerator(biomeRegistry))
				.setSeed(server.getOverworld().getSeed())
				.setShouldTickTime(false)
				.setMirrorOverworldGameRules(true)
				.setMirrorOverworldDifficulty(true)
				.setMirrorOverworldTimeAndWeather(true);
	}

	/** A World Key dimension: normal overworld terrain and biomes (to be replaced by its own biomes later). */
	public static RuntimeWorldConfig createWorldDimensionConfig(MinecraftServer server) {
		var registries = server.getRegistryManager();
		var biomes = MultiNoiseBiomeSource.create(registries.get(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
				.entryOf(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
		var settings = registries.get(RegistryKeys.CHUNK_GENERATOR_SETTINGS).entryOf(ChunkGeneratorSettings.OVERWORLD);
		return new RuntimeWorldConfig()
				.setDimensionType(DimensionTypes.OVERWORLD)
				.setGenerator(new NoiseChunkGenerator(biomes, settings))
				.setSeed(server.getOverworld().getSeed())
				.setShouldTickTime(false)
				.setMirrorOverworldGameRules(true)
				.setMirrorOverworldDifficulty(true)
				.setMirrorOverworldTimeAndWeather(true);
	}

	/** Where compasses point: a pocket dimension's player entry point, otherwise the world spawn. */
	public static BlockPos compassTarget(ServerWorld world) {
		if (!MOD_ID.equals(world.getRegistryKey().getValue().getNamespace())) return world.getSpawnPos();
		return BlockPos.ofFloored(PlayerEntryData.get(world).getEntryPos());
	}

	/** Re-sends the compass target to the players in a pocket dimension after its entry point moves. */
	public static void updateCompasses(ServerWorld world) {
		PlayerSpawnPositionS2CPacket packet = new PlayerSpawnPositionS2CPacket(compassTarget(world), world.getSpawnAngle());
		for (ServerPlayerEntity player : world.getPlayers()) player.networkHandler.sendPacket(packet);
	}

	/**
	 * Each pocket dimension gets its own seed, derived from the world seed and the dimension's name,
	 * so World Key dimensions don't generate the same terrain as the overworld or as each other.
	 */
	public static long pocketSeed(long worldSeed, String dimensionPath) {
		return RandomSeed.mixStafford13(worldSeed ^ (long) dimensionPath.hashCode() * 0x9E3779B97F4A7C15L);
	}
	public static final RegistryKey<Biome> VOID_BIOME_KEY =
			RegistryKey.of(RegistryKeys.BIOME, Identifier.of("pocket-repose", "pocket_islands"));
	private void registerPocketCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					LiteralArgumentBuilder.<ServerCommandSource>literal("pocketRepose")

							// mob entry setter
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
										Vec3d pos = src.getPosition();
										float yaw = src.getRotation().y;
										float pitch = src.getRotation().x;

										MobEntryData.get(world).setEntry(pos, yaw, pitch);
										src.sendFeedback(() -> Text.literal(
												String.format("§aMob entry set to %.2f, %.2f, %.2f", pos.x, pos.y, pos.z)
										), false);
										return 1;
									})
							)

							// player entry setter
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
										Vec3d pos = src.getPosition();
										float yaw = src.getRotation().y;
										float pitch = src.getRotation().x;

										PlayerEntryData.get(world).setEntry(pos, yaw, pitch);
										updateCompasses(world);
										src.sendFeedback(() -> Text.literal(
												String.format("§aPlayer entry set to %.2f, %.2f, %.2f", pos.x, pos.y, pos.z)
										), false);
										return 1;
									})
							)

							// Resets a pocket dimension's player entry point (operators)
							.then(CommandManager.literal("resetPlayerEntry")
									.requires(src -> src.hasPermissionLevel(2))
									.then(CommandManager.argument("dimension", StringArgumentType.word())
											.executes(ctx -> resetPocketDimension(ctx, StringArgumentType.getString(ctx, "dimension")))
									)
							)

							// Whether pocket blocks close behind players who enter them (operators)
							.then(booleanSetting("closeOnEntry",
									PocketRepose::getCloseOnEntry, PocketRepose::setCloseOnEntry,
									"Unlocked pocket blocks closing behind players who enter")
									.requires(src -> src.hasPermissionLevel(2)))
							.then(booleanSetting("closeOnLockedEntry",
									PocketRepose::getCloseOnLockedEntry, PocketRepose::setCloseOnLockedEntry,
									"Locked pocket blocks closing behind players who enter")
									.requires(src -> src.hasPermissionLevel(2)))

							// Exit Keystone settings (operators)
							.then(CommandManager.literal("exitKeystone")
									.requires(src -> src.hasPermissionLevel(2))
									.then(CommandManager.literal("setCooldown")
											.then(CommandManager.argument("seconds", FloatArgumentType.floatArg(0, 600))
													.executes(ctx -> {
														float seconds = FloatArgumentType.getFloat(ctx, "seconds");
														setExitKeystoneCooldown(seconds);
														ServerCommandSource src = ctx.getSource();
														PocketReposeSettingsData.onSettingsChanged(src.getServer());
														src.sendFeedback(() -> Text.literal("Exit Keystone cooldown: §a" + seconds + " seconds"), false);
														return 1;
													}))
											.executes(ctx -> {
												float current = getExitKeystoneCooldown();
												ctx.getSource().sendFeedback(() -> Text.literal("§7Exit Keystone cooldown is currently: §a" + current + " seconds"), false);
												return 1;
											}))
									.then(booleanSetting("worksInOverworld",
											PocketRepose::getExitKeystoneInOverworld, PocketRepose::setExitKeystoneInOverworld,
											"Exit Keystone returning players to spawn from the Overworld"))
									.then(booleanSetting("worksInNether",
											PocketRepose::getExitKeystoneInNether, PocketRepose::setExitKeystoneInNether,
											"Exit Keystone returning players to spawn from the Nether"))
									.then(booleanSetting("worksInEnd",
											PocketRepose::getExitKeystoneInEnd, PocketRepose::setExitKeystoneInEnd,
											"Exit Keystone returning players to spawn from the End"))
							)

							// Pocket dimension settings and tools (operators)
							.then(CommandManager.literal("pocketDimensions")
									.requires(src -> src.hasPermissionLevel(2))

									.then(CommandManager.literal("list")
											.executes(ctx -> {
												ServerCommandSource src = ctx.getSource();
												src.sendFeedback(() -> Text.literal("§aPocket Dimensions Loaded:"), false);
												boolean foundAny = false;
												for (ServerWorld world : src.getServer().getWorlds()) {
													Identifier id = world.getRegistryKey().getValue();
													String namespace = id.getNamespace();
													String path = id.getPath();
													String prefix = "pocket_dimension_";

													if ("pocket-repose".equals(namespace) && path.startsWith(prefix)) {
														String suffix = path.substring(prefix.length());
														src.sendFeedback(() -> Text.literal(" " + suffix), false);
														foundAny = true;
													}
												}
												if (!foundAny) {
													src.sendFeedback(() -> Text.literal("§cNo pocket dimensions found."), false);
												}
												return 1;
											})
									)

									.then(booleanSetting("allowRecursion",
											PocketRepose::getAllowRecursion, PocketRepose::setAllowRecursion,
											"Entering pocket blocks from inside pocket dimensions"))
									.then(booleanSetting("allowSetSpawnPoint",
											PocketRepose::getAllowSpawnPoint, PocketRepose::setAllowSpawnPoint,
											"Setting spawn points with beds in pocket dimensions"))
									.then(booleanSetting("allowHostileMobCapture",
											PocketRepose::getCanCaptureHostile, PocketRepose::setCanCaptureHostile,
											"Capturing hostile mobs"))
									.then(booleanSetting("allowPassiveMobCapture",
											PocketRepose::getCanCapturePassive, PocketRepose::setCanCapturePassive,
											"Capturing passive mobs"))
									.then(booleanSetting("allowHostileMobSpawning",
											PocketRepose::getHostileMobSpawning, PocketRepose::setHostileMobSpawning,
											"Hostile mob spawning in pocket dimensions"))
									.then(booleanSetting("allowPassiveMobSpawning",
											PocketRepose::getPassiveMobSpawning, PocketRepose::setPassiveMobSpawning,
											"Passive mob spawning in pocket dimensions"))
									.then(booleanSetting("allowInNether",
											PocketRepose::getAllowInNether, PocketRepose::setAllowInNether,
											"Entering pocket blocks in the Nether"))
									.then(booleanSetting("allowInEnd",
											PocketRepose::getAllowInEnd, PocketRepose::setAllowInEnd,
											"Entering pocket blocks in the End"))
									.then(booleanSetting("spawnIsland",
											PocketRepose::getSpawnIsland, PocketRepose::setSpawnIsland,
											"Island structures in new pocket dimensions"))
									.then(CommandManager.literal("setKnockVolume")
											.then(CommandManager.argument("volume", FloatArgumentType.floatArg(0, 10))
													.executes(ctx -> {
														float volume = FloatArgumentType.getFloat(ctx, "volume");
														setKnockVolume(volume);
														ServerCommandSource src = ctx.getSource();
														PocketReposeSettingsData.onSettingsChanged(src.getServer());
														src.sendFeedback(() -> Text.literal("Knock volume inside pocket dimensions: §a" + volume), false);
														return 1;
													}))
											.executes(ctx -> {
												float current = getKnockVolume();
												ctx.getSource().sendFeedback(() -> Text.literal("§7Knock volume inside pocket dimensions is currently: §a" + current), false);
												return 1;
											}))

									.then(CommandManager.literal("captureBlacklist")
											.then(CommandManager.literal("add")
													.then(CommandManager.argument("entity", IdentifierArgumentType.identifier())
															.suggests((ctx, builder) -> CommandSource.suggestIdentifiers(Registries.ENTITY_TYPE.getIds(), builder))
															.executes(ctx -> {
																ServerCommandSource src = ctx.getSource();
																String entityString = IdentifierArgumentType.getIdentifier(ctx, "entity").toString();

																try {
																	Identifier entityId = Identifier.of(entityString);
																	Optional<EntityType<?>> found = Registries.ENTITY_TYPE.getOrEmpty(entityId);

																	if (found.isEmpty()) {
																		src.sendError(Text.literal("§cUnknown entity type: " + entityString));
																		return 0;
																	}
																	EntityType<?> entityType = found.get();

																	if (entityType == EntityType.PLAYER) {
																		src.sendError(Text.literal("§cCannot blacklist players"));
																		return 0;
																	}

																	addToBlacklist(entityType);
																	PocketReposeSettingsData.onSettingsChanged(src.getServer());
																	src.sendFeedback(() -> Text.literal(
																			"§aAdded " + entityId + " to blacklist"
																	), false);
																	return 1;
																} catch (Exception e) {
																	src.sendError(Text.literal("§cInvalid entity identifier: " + entityString));
																	return 0;
																}
															})
													)
											)

											.then(CommandManager.literal("remove")
													.then(CommandManager.argument("entity", IdentifierArgumentType.identifier())
															.suggests((ctx, builder) -> CommandSource.suggestIdentifiers(Registries.ENTITY_TYPE.getIds(), builder))
															.executes(ctx -> {
																ServerCommandSource src = ctx.getSource();
																String entityString = IdentifierArgumentType.getIdentifier(ctx, "entity").toString();

																try {
																	Identifier entityId = Identifier.of(entityString);
																	Optional<EntityType<?>> found = Registries.ENTITY_TYPE.getOrEmpty(entityId);

																	if (found.isEmpty()) {
																		src.sendError(Text.literal("§cUnknown entity type: " + entityString));
																		return 0;
																	}
																	EntityType<?> entityType = found.get();

																	boolean removed = removeFromBlacklist(entityType);
																	PocketReposeSettingsData.onSettingsChanged(src.getServer());
																	if (removed) {
																		src.sendFeedback(() -> Text.literal(
																				"§aRemoved " + entityId + " from blacklist"
																		), false);
																	} else {
																		src.sendFeedback(() -> Text.literal(
																				"§7" + entityId + " was not in blacklist"
																		), false);
																	}
																	return 1;
																} catch (Exception e) {
																	src.sendError(Text.literal("§cInvalid entity identifier: " + entityString));
																	return 0;
																}
															})
													)
											)

											.then(CommandManager.literal("list")
													.executes(ctx -> {
														ServerCommandSource src = ctx.getSource();
														Set<EntityType<?>> blacklist = getEntityBlacklist();

														if (blacklist.isEmpty()) {
															src.sendFeedback(() -> Text.literal("§7No entities are blacklisted"), false);
														} else {
															src.sendFeedback(() -> Text.literal("§aBlacklisted entities:"), false);
															blacklist.forEach(entityType -> {
																Identifier id = Registries.ENTITY_TYPE.getId(entityType);
																src.sendFeedback(() -> Text.literal(" " + id), false);
															});
														}
														return 1;
													})
											)

											.then(CommandManager.literal("clear")
													.executes(ctx -> {
														ServerCommandSource src = ctx.getSource();
														int count = getEntityBlacklist().size();
														clearBlacklist();
														PocketReposeSettingsData.onSettingsChanged(src.getServer());
														src.sendFeedback(() -> Text.literal(
																"§aCleared blacklist (removed " + count + " entities)"
														), false);
														return 1;
													})
											)
									)
							)
			);
		});
	}

	/** An on/off setting: "<name> true|false" sets it, "<name>" alone shows it. Saved with the world. */
	private static LiteralArgumentBuilder<ServerCommandSource> booleanSetting(String name, BooleanSupplier getter,
																			 Consumer<Boolean> setter, String label) {
		return CommandManager.literal(name)
				.then(CommandManager.argument("value", BoolArgumentType.bool())
						.executes(ctx -> {
							boolean value = BoolArgumentType.getBool(ctx, "value");
							setter.accept(value);
							ServerCommandSource src = ctx.getSource();
							PocketReposeSettingsData.onSettingsChanged(src.getServer());
							src.sendFeedback(() -> Text.literal(label + ": " + (value ? "§aEnabled" : "§cDisabled")), false);
							return 1;
						}))
				.executes(ctx -> {
					boolean current = getter.getAsBoolean();
					ctx.getSource().sendFeedback(() -> Text.literal("§7" + label + " is currently: "
							+ (current ? "§aEnabled" : "§cDisabled")), false);
					return 1;
				});
	}
	private int resetPocketDimension(CommandContext<ServerCommandSource> ctx, String dimSuffix) {
		ServerCommandSource src = ctx.getSource();

		Identifier dimId = Identifier.of("pocket-repose", "pocket_dimension_" + dimSuffix);
		RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
		ServerWorld targetWorld = src.getServer().getWorld(worldKey);

		if (targetWorld == null) {
			src.sendError(Text.literal("§cPocket dimension '" + dimSuffix + "' not found"));
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
		updateCompasses(targetWorld);

		src.sendFeedback(() -> Text.literal("§aPocket dimension '" + dimSuffix + "' entry reset"), false);
		return 1;
	}
	private void registerPlayerEntrySetter() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack s = player.getStackInHand(hand);
			if (world.isClient || hand != net.minecraft.util.Hand.MAIN_HAND || s.getItem() != Items.BONE)
				return TypedActionResult.pass(s);

			if (!(world instanceof ServerWorld sw)) return TypedActionResult.pass(s);
			Identifier id = sw.getRegistryKey().getValue();
			if (!"pocket-repose".equals(id.getNamespace())
					|| !id.getPath().startsWith("pocket_dimension_"))
				return TypedActionResult.pass(s);

			Vec3d pos = player.getPos();
			float yaw = player.getYaw();
			float pitch = player.getPitch();
			PlayerEntryData.get(sw).setEntry(pos, yaw, pitch);
			updateCompasses(sw);

			player.sendMessage(Text.literal(
					String.format("§aPlayer entry location set to %.1f, %.1f, %.1f", pos.x, pos.y, pos.z)
			), true);

			return TypedActionResult.success(s);
		});
	}
	private void registerMobEntrySetter() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (world.isClient || hand != net.minecraft.util.Hand.MAIN_HAND) {
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

			Vec3d pos = player.getPos();
			float yaw = player.getYaw();
			float pitch = player.getPitch();
			MobEntryData.get(sw).setEntry(pos, yaw, pitch);

			player.sendMessage(Text.literal(
					String.format("§aMob entry location set to %.1f, %.1f, %.1f", pos.x, pos.y, pos.z)
			), true);

			return TypedActionResult.success(stack);
		});
	}
	private static boolean canCaptureHostile = false;
	private static boolean spawnIsland = true;
	private static boolean allowRecursion = true;
	private static boolean hostileMobSpawning = false;
	private static boolean passiveMobSpawning = true;
	private static boolean allowSpawnPoint = false;
	private static boolean exitKeystoneInOverworld = false;
	// Seconds before the Exit Keystone can be used again
	private static float exitKeystoneCooldown = 1.0F;
	public static float getExitKeystoneCooldown() {
		return exitKeystoneCooldown;
	}
	public static void setExitKeystoneCooldown(float seconds) {
		exitKeystoneCooldown = seconds;
	}
	private static boolean exitKeystoneInNether = true;
	private static boolean exitKeystoneInEnd = true;
	private static boolean canCapturePassive = true;
	private static boolean allowInNether = true;
	private static boolean allowInEnd = true;
	private static boolean closeOnEntry = false;
	private static boolean closeOnLockedEntry = true;
	public static boolean getCloseOnLockedEntry() {
		return closeOnLockedEntry;
	}
	public static void setCloseOnLockedEntry(boolean value) {
		closeOnLockedEntry = value;
	}
	public static boolean getCloseOnEntry() {
		return closeOnEntry;
	}
	public static void setCloseOnEntry(boolean value) {
		closeOnEntry = value;
	}
	// How loud a knock on a locked pocket block is inside the pocket dimension
	private static float knockVolume = 0.5F;
	public static float getKnockVolume() {
		return knockVolume;
	}
	public static void setKnockVolume(float value) {
		knockVolume = value;
	}
	public static boolean getExitKeystoneInOverworld() {
		return exitKeystoneInOverworld;
	}
	public static void setExitKeystoneInOverworld(boolean value) {
		exitKeystoneInOverworld = value;
	}
	public static boolean getExitKeystoneInNether() {
		return exitKeystoneInNether;
	}
	public static void setExitKeystoneInNether(boolean value) {
		exitKeystoneInNether = value;
	}
	public static boolean getExitKeystoneInEnd() {
		return exitKeystoneInEnd;
	}
	public static void setExitKeystoneInEnd(boolean value) {
		exitKeystoneInEnd = value;
	}
	public static boolean getCanCapturePassive() {
		return canCapturePassive;
	}
	public static void setCanCapturePassive(boolean value) {
		canCapturePassive = value;
	}
	public static boolean getAllowInNether() {
		return allowInNether;
	}
	public static void setAllowInNether(boolean value) {
		allowInNether = value;
	}
	public static boolean getAllowInEnd() {
		return allowInEnd;
	}
	public static void setAllowInEnd(boolean value) {
		allowInEnd = value;
	}
	public static boolean getHostileMobSpawning() {
		return hostileMobSpawning;
	}
	public static void setHostileMobSpawning(boolean value) {
		hostileMobSpawning = value;
	}
	public static boolean getPassiveMobSpawning() {
		return passiveMobSpawning;
	}
	public static void setPassiveMobSpawning(boolean value) {
		passiveMobSpawning = value;
	}
	public static boolean getAllowSpawnPoint() {
		return allowSpawnPoint;
	}
	public static void setAllowSpawnPoint(boolean value) {
		allowSpawnPoint = value;
	}
	public static boolean getAllowRecursion() {
		return allowRecursion;
	}
	public static void setAllowRecursion(boolean value) {
		allowRecursion = value;
	}
	private static Set<EntityType<?>> entityBlacklist = new HashSet<>();
	public static boolean getCanCaptureHostile() {
		return canCaptureHostile;
	}
	public static void setCanCaptureHostile(boolean value) {
		canCaptureHostile = value;
	}
	public static boolean getSpawnIsland() {
		return spawnIsland;
	}
	public static void setSpawnIsland(boolean value) {
		spawnIsland = value;
	}
	public static Set<EntityType<?>> getEntityBlacklist() {
		return new HashSet<>(entityBlacklist);
	}
	public static void addToBlacklist(EntityType<?> entityType) {
		entityBlacklist.add(entityType);
	}
	public static boolean removeFromBlacklist(EntityType<?> entityType) {
		return entityBlacklist.remove(entityType);
	}
	public static boolean isBlacklisted(EntityType<?> entityType) {
		return entityBlacklist.contains(entityType);
	}
	public static void clearBlacklist() {
		entityBlacklist.clear();
	}
	public static void resetSettings() {
		canCaptureHostile = false;
		spawnIsland = true;
		allowRecursion = true;
		hostileMobSpawning = false;
		passiveMobSpawning = true;
		allowSpawnPoint = false;
		exitKeystoneInOverworld = false;
		exitKeystoneCooldown = 1.0F;
		exitKeystoneInNether = true;
		exitKeystoneInEnd = true;
		canCapturePassive = true;
		allowInNether = true;
		allowInEnd = true;
		knockVolume = 0.5F;
		closeOnEntry = false;
		closeOnLockedEntry = true;
		entityBlacklist.clear();
	}
	private boolean isHostileMob(LivingEntity mob) {
		return mob instanceof HostileEntity ||
				mob instanceof SpiderEntity ||
				mob instanceof EndermanEntity ||
				mob instanceof PiglinEntity ||
				mob instanceof ZombifiedPiglinEntity ||
				(mob instanceof WolfEntity wolf && wolf.hasAngerTime());
	}
	private void registerSuitcaseMobTeleport() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient) return ActionResult.PASS;

			if (hand != net.minecraft.util.Hand.MAIN_HAND) return ActionResult.PASS;

			ItemStack stack = player.getStackInHand(hand);
			if (!(stack.getItem() instanceof BlockItem bi)) return ActionResult.PASS;

			Block heldBlock = bi.getBlock();
			if (!SuitcaseBlock.isPocketBlock(heldBlock)) return ActionResult.PASS;

			if (entity instanceof PlayerEntity) {
				player.sendMessage(Text.literal("☒"), true);
				world.playSound(null,
						player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
						SoundCategory.PLAYERS,
						0.3f, 1.5f
				);
				return ActionResult.FAIL;
			}

			NbtComponent beData = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
			if (beData == null) {
				player.sendMessage(Text.literal("☒"), true);
				world.playSound(null,
						player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
						SoundCategory.PLAYERS,
						0.3f, 1.5f
				);
				return ActionResult.FAIL;
			}

			NbtCompound beNbt = beData.copyNbt();
			if (!beNbt.contains("BoundKeystone")) {
				player.sendMessage(Text.literal("☒"), true);
				world.playSound(null,
						player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
						SoundCategory.PLAYERS,
						0.3f, 1.5f
				);
				return ActionResult.FAIL;
			}

			if (beNbt.getBoolean("Locked")) {
				player.sendMessage(Text.literal("☒"), true);
				world.playSound(null,
						player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
						SoundCategory.PLAYERS,
						0.3f, 1.5f
				);
				return ActionResult.FAIL;
			}

			String keystone = beNbt.getString("BoundKeystone");
			Identifier dimId = Identifier.of("pocket-repose", "pocket_dimension_" + keystone);
			RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
			ServerWorld targetWorld = world.getServer().getWorld(dimKey);

			if (targetWorld == null) {
				player.sendMessage(Text.literal("§cPocket dimension not found"), true);
				return ActionResult.FAIL;
			}

			if (!(entity instanceof LivingEntity mob)) return ActionResult.PASS;

			if (isBlacklisted(mob.getType())) {
				player.sendMessage(Text.literal("☒"), true);
				world.playSound(null,
						player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
						SoundCategory.PLAYERS,
						0.3f, 1.5f
				);
				return ActionResult.FAIL;
			}

			if (isHostileMob(mob) ? !canCaptureHostile : !canCapturePassive) {
				player.sendMessage(Text.literal("☒"), true);
				world.playSound(null,
						player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
						SoundCategory.PLAYERS,
						0.3f, 1.5f
				);
				return ActionResult.FAIL;
			}

			UUID suitcaseId = ensureSuitcaseIdOnSuitcaseStack(stack, beNbt);
			stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(beNbt));

			SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(world.getServer());
			if (tracker != null && beNbt.contains("EnteredPlayers", NbtElement.LIST_TYPE)) {
				String dimStr = ((ServerPlayerEntity) player).getServerWorld().getRegistryKey().getValue().toString();

				NbtList players = beNbt.getList("EnteredPlayers", NbtElement.COMPOUND_TYPE);
				for (int i = 0; i < players.size(); i++) {
					NbtCompound playerData = players.getCompound(i);
					String uuid = playerData.getString("UUID");

					UUID expected = tracker.getLastSuitcase(keystone, uuid);
					if (expected != null && expected.equals(suitcaseId)) {
						tracker.updateLocation(
								keystone, uuid,
								dimStr, suitcaseId,
								player.getX(), player.getY() + 1.0, player.getZ(),
								player.getYaw(), player.getPitch(),
								SuitcaseLocationTracker.LocationType.ITEM_ENTITY
						);
					}
				}
			}

			MobEntryData data = MobEntryData.get(targetWorld);
			Vec3d dest = data.getEntryPos();
			float yaw = data.getEntryYaw();
			float pitch = data.getEntryPitch();

			TeleportTarget target = new TeleportTarget(
					targetWorld,
					dest,
					Vec3d.ZERO,
					yaw,
					pitch,
					TeleportTarget.NO_OP
			);

			mob.teleportTo(target);

			world.playSound(null,
					player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_BUNDLE_DROP_CONTENTS,
					SoundCategory.PLAYERS,
					2.0f, 1.0f
			);

			world.playSound(null,
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
			if (world.isClient) return ActionResult.PASS;
			if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

			ItemStack held = sp.getStackInHand(hand);
			if (!(held.getItem() instanceof KeystoneItem)) return ActionResult.PASS;

			Identifier dimId = sp.getWorld().getRegistryKey().getValue();
			String namespace = dimId.getNamespace();
			String path = dimId.getPath();
			String prefix = "pocket_dimension_";

			if (!namespace.equals("pocket-repose") || !path.startsWith(prefix)) return ActionResult.PASS;

			String keystoneName = path.substring(prefix.length());

			if (!(entity instanceof LivingEntity mob)) return ActionResult.PASS;

			MinecraftServer server = sp.getServer();
			if (server == null) return ActionResult.FAIL;

			SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(server);
			String playerUuid = sp.getUuidAsString();

			TeleportResult res = sendMobOut(server, sp.getServerWorld(), mob, keystoneName, playerUuid);
			boolean teleported = res.success;
			ServerWorld targetWorldUsed = res.world;
			Vec3d targetPosUsed = res.pos;

			if (!teleported) {
				sp.sendMessage(Text.literal("§cNo suitcase exit found for mob"), true);
				return ActionResult.FAIL;
			}

			sp.getWorld().playSound(
					null,
					sp.getX(), sp.getY(), sp.getZ(),
					SoundEvents.ITEM_BUNDLE_DROP_CONTENTS,
					SoundCategory.PLAYERS,
					2.0f, 1.0f
			);

			if (targetWorldUsed != null && targetPosUsed != null) {
				targetWorldUsed.playSound(
						null,
						targetPosUsed.x, targetPosUsed.y, targetPosUsed.z,
						SoundEvents.ITEM_BUNDLE_DROP_CONTENTS,
						SoundCategory.PLAYERS,
						2.0f, 1.0f
				);
			}

			return ActionResult.SUCCESS;
		});
	}
	/**
	 * Sends a mob out of a pocket dimension to where the given player would come back out, checked in the
	 * same order as for players, and never anywhere inside the pocket dimension being left (a suitcase bound
	 * to it can be carried or placed in it).
	 */
	private static TeleportResult sendMobOut(MinecraftServer server, ServerWorld pocketWorld, Entity mob,
											 String keystoneName, String playerUuid) {
		SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(server);
		UUID expectedSuitcaseId = (tracker == null) ? null : tracker.getLastSuitcase(keystoneName, playerUuid);

		boolean teleported = false;
		ServerWorld targetWorldUsed = null;
		Vec3d targetPosUsed = null;

		ServerWorld destWorld = null;
		Vec3d dest = null;
		if (tracker != null && expectedSuitcaseId != null) {
			SuitcaseLocationTracker.SuitcaseInstanceLocation sLoc = tracker.getSuitcaseLocation(expectedSuitcaseId);
			if (sLoc != null && sLoc.type != SuitcaseLocationTracker.LocationType.DESTROYED) {
				ServerWorld w = worldFromId(server, sLoc.dimensionId);
				if (w != pocketWorld) {
					destWorld = w;
					dest = new Vec3d(sLoc.x, sLoc.y, sLoc.z);
				}
			}
		}
		if (dest == null && tracker != null) {
			SuitcaseLocationTracker.LocationData loc = tracker.getLocation(keystoneName, playerUuid);
			if (loc != null && loc.type != SuitcaseLocationTracker.LocationType.DESTROYED
					&& (expectedSuitcaseId == null || expectedSuitcaseId.equals(loc.suitcaseId))) {
				ServerWorld w = worldFromId(server, loc.dimensionId);
				if (w != pocketWorld) {
					destWorld = w;
					dest = new Vec3d(loc.x, loc.y, loc.z);
				}
			}
		}
		if (dest != null && teleportMobTo(mob, destWorld, dest)) {
			teleported = true;
			targetWorldUsed = destWorld;
			targetPosUsed = dest;
		}

		if (!teleported && expectedSuitcaseId != null) {
			TeleportResult res = findSuitcaseAndTeleportMob(server, pocketWorld, mob, keystoneName, expectedSuitcaseId);
			if (res.success) {
				teleported = true;
				targetWorldUsed = res.world;
				targetPosUsed = res.pos;
			}
		}

		// Last resort: where the player went in
		if (!teleported && tracker != null) {
			SuitcaseLocationTracker.LocationData entry = tracker.getEntryLocation(keystoneName, playerUuid);
			if (entry != null) {
				ServerWorld w = worldFromId(server, entry.dimensionId);
				Vec3d pos = new Vec3d(entry.x, entry.y, entry.z);
				if (w != pocketWorld && teleportMobTo(mob, w, pos)) {
					teleported = true;
					targetWorldUsed = w;
					targetPosUsed = pos;
				}
			}
		}

		return teleported ? new TeleportResult(true, targetWorldUsed, targetPosUsed) : TeleportResult.fail();
	}

	/**
	 * A mob that falls off the bottom of a pocket dimension leaves it as if released with a key by the player
	 * who most recently went in. With no one to follow, it goes to the world spawn.
	 */
	public static void releaseFallenMob(ServerWorld pocketWorld, LivingEntity mob) {
		sendOut(pocketWorld, mob, null);
	}

	/**
	 * An item thrown into a portal block or an exit portal door (or falling off the bottom) leaves a pocket
	 * dimension like a released mob: to where the player who threw it would come back out, or else the player who
	 * most recently went in, or else the world spawn.
	 */
	public static void releaseItem(ServerWorld pocketWorld, ItemEntity item) {
		String playerUuid = item.getOwner() instanceof PlayerEntity thrower ? thrower.getUuidAsString() : null;
		sendOut(pocketWorld, item, playerUuid);
	}

	/** Sends an entity out of a pocket dimension, following the given player (or the latest one in, if null). */
	private static void sendOut(ServerWorld pocketWorld, Entity entity, @Nullable String playerUuid) {
		MinecraftServer server = pocketWorld.getServer();
		String keystoneName = pocketWorld.getRegistryKey().getValue().getPath().substring("pocket_dimension_".length());
		SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(server);
		// Only someone who has come in through a pocket block of this dimension has a way out to follow
		if (playerUuid != null && (tracker == null || tracker.getLastSuitcase(keystoneName, playerUuid) == null)) {
			playerUuid = null;
		}
		if (playerUuid == null && tracker != null) {
			playerUuid = tracker.getLatestEntrant(keystoneName);
		}

		// Falling this far would hurt on arrival (the entity is copied into the other dimension, fall distance and all)
		entity.fallDistance = 0f;
		TeleportResult res = playerUuid == null ? TeleportResult.fail() : sendMobOut(server, pocketWorld, entity, keystoneName, playerUuid);
		if (!res.success) {
			ServerWorld overworld = server.getOverworld();
			Vec3d safe = PocketPortalBlock.findSafeSpotNear(overworld, overworld.getSpawnPos());
			if (safe == null) safe = Vec3d.ofBottomCenter(overworld.getSpawnPos());
			teleportMobTo(entity, overworld, safe);
			res = new TeleportResult(true, overworld, safe);
		}
		res.world.playSound(null, res.pos.x, res.pos.y, res.pos.z,
				SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 2.0f, 1.0f);
	}

	private static class TeleportResult {
		final boolean success;
		final ServerWorld world;
		final Vec3d pos;

		TeleportResult(boolean success, ServerWorld world, Vec3d pos) {
			this.success = success;
			this.world = world;
			this.pos = pos;
		}

		static TeleportResult fail() {
			return new TeleportResult(false, null, null);
		}
	}
	private static boolean teleportMobTo(Entity mob, ServerWorld targetWorld, Vec3d pos) {
		TeleportTarget target = new TeleportTarget(
				targetWorld,
				pos,
				Vec3d.ZERO,
				mob.getYaw(),
				mob.getPitch(),
				TeleportTarget.NO_OP
		);
		mob.teleportTo(target);
		return true;
	}
	private static ServerWorld worldFromId(MinecraftServer server, String dimStr) {
		if (dimStr == null || dimStr.isEmpty()) return server.getWorld(World.OVERWORLD);

		Identifier id = Identifier.tryParse(dimStr);
		if (id == null) return server.getWorld(World.OVERWORLD);

		RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
		ServerWorld w = server.getWorld(key);
		return (w != null) ? w : server.getWorld(World.OVERWORLD);
	}
	private static boolean isSuitcaseWithKeystoneAndId(ItemStack stack, String keystoneName, UUID expectedSuitcaseId) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)
				|| !SuitcaseBlock.isPocketBlock(bi.getBlock())) {
			return false;
		}

		NbtComponent beTag = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
		if (beTag == null) return false;

		NbtCompound nbt = beTag.copyNbt();
		if (!keystoneName.equals(nbt.getString("BoundKeystone"))) return false;

		if (!nbt.containsUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID)) return false;
		UUID id = nbt.getUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID);

		return expectedSuitcaseId.equals(id);
	}
	private static TeleportResult findSuitcaseAndTeleportMob(MinecraftServer server, ServerWorld pocketWorld, Entity mob, String keystoneName, UUID expectedSuitcaseId) {

		for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
			if (online.getServerWorld() == pocketWorld) continue;
			for (int i = 0; i < online.getInventory().size(); i++) {
				ItemStack stack = online.getInventory().getStack(i);
				if (isSuitcaseWithKeystoneAndId(stack, keystoneName, expectedSuitcaseId)) {
					ServerWorld w = online.getServerWorld();
					Vec3d dest = new Vec3d(online.getX(), online.getY() + 1.0, online.getZ());
					teleportMobTo(mob, w, dest);
					return new TeleportResult(true, w, dest);
				}
			}
		}

		for (ServerWorld w : server.getWorlds()) {
			if (w == pocketWorld) continue;
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				if (p.getServerWorld() != w) continue;

				Box box = p.getBoundingBox().expand(256);
				List<ItemEntity> items = w.getEntitiesByClass(ItemEntity.class, box,
						item -> isSuitcaseWithKeystoneAndId(item.getStack(), keystoneName, expectedSuitcaseId));

				if (!items.isEmpty()) {
					ItemEntity suitcaseItem = items.get(0);
					Vec3d dest = new Vec3d(suitcaseItem.getX(), suitcaseItem.getY() + 1.0, suitcaseItem.getZ());
					teleportMobTo(mob, w, dest);
					return new TeleportResult(true, w, dest);
				}
			}
		}

		for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
			ServerWorld w = online.getServerWorld();
			if (w == pocketWorld) continue;
			ChunkPos center = online.getChunkPos();
			int radius = 8;

			for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
				for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
					if (!w.isChunkLoaded(cx, cz)) continue;

					WorldChunk chunk = w.getChunk(cx, cz);
					for (BlockEntity be : chunk.getBlockEntities().values()) {
						if (!(be instanceof Inventory inv)) continue;

						for (int i = 0; i < inv.size(); i++) {
							ItemStack stack = inv.getStack(i);
							if (isSuitcaseWithKeystoneAndId(stack, keystoneName, expectedSuitcaseId)) {
								BlockPos cpos = be.getPos();
								Vec3d dest = new Vec3d(cpos.getX() + 0.5, cpos.getY() + 1.0, cpos.getZ() + 0.5);
								teleportMobTo(mob, w, dest);
								return new TeleportResult(true, w, dest);
							}
						}
					}
				}
			}
		}

		return TeleportResult.fail();
	}
}

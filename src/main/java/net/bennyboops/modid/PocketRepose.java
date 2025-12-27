package net.bennyboops.modid;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.bennyboops.modid.block.ModBlocks;
import net.bennyboops.modid.block.SuitcaseBlock;
import net.bennyboops.modid.block.entity.ModBlockEntities;
import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.bennyboops.modid.criterion.EnterPocketDimensionCriterion;
import net.bennyboops.modid.data.MobEntryData;
import net.bennyboops.modid.data.PlayerEntryData;
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
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
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
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
		});

		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
		ModItemGroups.registerItemGroups();
		ModBlockEntities.registerBlockEntities();

		PortalChunkHandler.initialize();

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

			Registry<Biome> biomeRegistry = server.getRegistryManager().get(RegistryKeys.BIOME);
			long seed = server.getOverworld().getSeed();

			try {
				for (String dimName : Files.readAllLines(registryFile)) {
					Identifier worldId = Identifier.of("pocket-repose", dimName);
					ChunkGenerator gen = new PortalChunkGenerator(biomeRegistry);

					RuntimeWorldConfig cfg = new RuntimeWorldConfig()
							.setDimensionType(POCKET_DIMENSION_TYPE_KEY)
							.setGenerator(gen)
							.setSeed(seed);

					Fantasy.get(server).getOrOpenPersistentWorld(worldId, cfg);
				}
			} catch (IOException e) {
				LOGGER.error("Failed to reload pocket dimensions", e);
			}
		});

		ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register((originalEntity, newEntity, origin, destination) -> {
			if (!(newEntity instanceof ItemEntity itemEntity)) return;

			ItemStack stack = itemEntity.getStack();
			if (!(stack.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof SuitcaseBlock)) return;

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

								if (st.getItem() instanceof BlockItem b && b.getBlock() instanceof SuitcaseBlock) return true;

								return st.get(DataComponentTypes.CONTAINER) != null;
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

		ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
		if (container == null) return;

		for (ItemStack inner : container.iterateNonEmpty()) {
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

			if (!(stack.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof SuitcaseBlock)) continue;

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
		if (!(bi.getBlock() instanceof SuitcaseBlock)) return;

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
				|| !(bi.getBlock() instanceof SuitcaseBlock)) {
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
										float yaw = src.getEntity().getYaw();
										float pitch = src.getEntity().getPitch();

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
										float yaw = src.getEntity().getYaw();
										float pitch = src.getEntity().getPitch();

										PlayerEntryData.get(world).setEntry(pos, yaw, pitch);
										src.sendFeedback(() -> Text.literal(
												String.format("§aPlayer entry set to %.2f, %.2f, %.2f", pos.x, pos.y, pos.z)
										), false);
										return 1;
									})
							)

							// reset entry helper (kept from your original)
							.then(CommandManager.literal("resetPlayerEntry")
									.then(CommandManager.argument("dimension", StringArgumentType.word())
											.executes(ctx -> resetPocketDimension(ctx, StringArgumentType.getString(ctx, "dimension")))
									)
							)

							// list dimensions
							.then(CommandManager.literal("listDimensions")
									.requires(src -> src.hasPermissionLevel(2))
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

							// canCaptureHostile true/false
							.then(CommandManager.literal("canCaptureHostile")
									.requires(src -> src.hasPermissionLevel(2))
									.then(CommandManager.argument("value", BoolArgumentType.bool())
											.executes(ctx -> {
												boolean value = BoolArgumentType.getBool(ctx, "value");
												setCanCaptureHostile(value);
												ServerCommandSource src = ctx.getSource();
												src.sendFeedback(() -> Text.literal(
														value ? "§aHostile mob capture enabled" : "§cHostile mob capture disabled"
												), false);
												return 1;
											})
									)
									.executes(ctx -> {
										ServerCommandSource src = ctx.getSource();
										boolean current = getCanCaptureHostile();
										src.sendFeedback(() -> Text.literal(
												"§7Hostile mob capture is currently: " + (current ? "§aEnabled" : "§cDisabled")
										), false);
										return 1;
									})
							)

							// spawnIsland true/false
							.then(CommandManager.literal("spawnIsland")
									.requires(src -> src.hasPermissionLevel(2))
									.then(CommandManager.argument("value", BoolArgumentType.bool())
											.executes(ctx -> {
												boolean value = BoolArgumentType.getBool(ctx, "value");
												setSpawnIsland(value);
												ServerCommandSource src = ctx.getSource();
												src.sendFeedback(() -> Text.literal(
														value ? "§aIsland structure spawning enabled" : "§cIsland structure spawning disabled (grass cube will spawn)"
												), false);
												return 1;
											})
									)
									.executes(ctx -> {
										ServerCommandSource src = ctx.getSource();
										boolean current = getSpawnIsland();
										src.sendFeedback(() -> Text.literal(
												"§7Island structure spawning is currently: " + (current ? "§aEnabled" : "§cDisabled")
										), false);
										return 1;
									})
							)

							// mob blacklist group
							.then(CommandManager.literal("mobBlacklist")
									.requires(src -> src.hasPermissionLevel(2))

									.then(CommandManager.literal("add")
											.then(CommandManager.argument("entity", StringArgumentType.string())
													.executes(ctx -> {
														ServerCommandSource src = ctx.getSource();
														String entityString = StringArgumentType.getString(ctx, "entity");

														try {
															Identifier entityId = Identifier.of(entityString);
															EntityType<?> entityType = Registries.ENTITY_TYPE.get(entityId);

															if (entityType == EntityType.PIG && !entityString.equals("minecraft:pig")) {
																src.sendError(Text.literal("§cUnknown entity type: " + entityString));
																return 0;
															}

															if (entityType == EntityType.PLAYER) {
																src.sendError(Text.literal("§cCannot blacklist players"));
																return 0;
															}

															addToBlacklist(entityType);
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
											.then(CommandManager.argument("entity", StringArgumentType.string())
													.executes(ctx -> {
														ServerCommandSource src = ctx.getSource();
														String entityString = StringArgumentType.getString(ctx, "entity");

														try {
															Identifier entityId = Identifier.of(entityString);
															EntityType<?> entityType = Registries.ENTITY_TYPE.get(entityId);

															if (entityType == EntityType.PIG && !entityString.equals("minecraft:pig")) {
																src.sendError(Text.literal("§cUnknown entity type: " + entityString));
																return 0;
															}

															boolean removed = removeFromBlacklist(entityType);
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
												src.sendFeedback(() -> Text.literal(
														"§aCleared blacklist (removed " + count + " entities)"
												), false);
												return 1;
											})
									)
							)

							// allowRecursion true/false
							.then(CommandManager.literal("allowRecursion")
									.requires(src -> src.hasPermissionLevel(2))
									.then(CommandManager.argument("value", BoolArgumentType.bool())
											.executes(ctx -> {
												boolean value = BoolArgumentType.getBool(ctx, "value");
												setAllowRecursion(value);

												ServerCommandSource src = ctx.getSource();
												src.sendFeedback(() -> Text.literal(
														value ? "§aSuitcase recursion enabled" : "§cSuitcase recursion disabled"
												), false);
												return 1;
											})
									)
									.executes(ctx -> {
										ServerCommandSource src = ctx.getSource();
										boolean current = getAllowRecursion();
										src.sendFeedback(() -> Text.literal(
												"§7Suitcase recursion is currently: " + (current ? "§aEnabled" : "§cDisabled")
										), false);
										return 1;
									})
							)

			);
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
			if (!(heldBlock instanceof SuitcaseBlock)) return ActionResult.PASS;

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

			if (!canCaptureHostile && isHostileMob(mob)) {
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

			UUID expectedSuitcaseId = (tracker == null) ? null : tracker.getLastSuitcase(keystoneName, playerUuid);

			boolean teleported = false;
			ServerWorld targetWorldUsed = null;
			Vec3d targetPosUsed = null;

			if (tracker != null) {
				SuitcaseLocationTracker.LocationData loc = tracker.getLocation(keystoneName, playerUuid);
				if (loc != null && loc.type != SuitcaseLocationTracker.LocationType.DESTROYED) {

					if (expectedSuitcaseId == null || (loc.suitcaseId != null && expectedSuitcaseId.equals(loc.suitcaseId))) {
						ServerWorld target = worldFromId(server, loc.dimensionId);
						if (target != null) {
							Vec3d dest = new Vec3d(loc.x, loc.y, loc.z);
							if (teleportMobTo(mob, target, dest)) {
								teleported = true;
								targetWorldUsed = target;
								targetPosUsed = dest;
							}
						}
					}
				}
			}

			if (!teleported && expectedSuitcaseId != null) {
				TeleportResult res = findSuitcaseAndTeleportMob(server, mob, keystoneName, expectedSuitcaseId);
				if (res.success) {
					teleported = true;
					targetWorldUsed = res.world;
					targetPosUsed = res.pos;
				}
			}

			if (!teleported && tracker != null) {
				SuitcaseLocationTracker.LocationData loc = tracker.getLocation(keystoneName, playerUuid);
				if (loc != null) {
					ServerWorld target = worldFromId(server, loc.dimensionId);
					if (target != null) {
						Vec3d dest = new Vec3d(loc.x, loc.y, loc.z);
						sp.sendMessage(Text.literal("§6No suitcase found — returning mob to last known point"), true);
						if (teleportMobTo(mob, target, dest)) {
							teleported = true;
							targetWorldUsed = target;
							targetPosUsed = dest;
						}
					}
				}
			}

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
	private static boolean teleportMobTo(LivingEntity mob, ServerWorld targetWorld, Vec3d pos) {
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
				|| !(bi.getBlock() instanceof SuitcaseBlock)) {
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
	private static TeleportResult findSuitcaseAndTeleportMob(MinecraftServer server, LivingEntity mob, String keystoneName, UUID expectedSuitcaseId) {

		for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
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

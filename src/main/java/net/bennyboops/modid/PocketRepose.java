package net.bennyboops.modid;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Mod(PocketRepose.MODID)
public class PocketRepose {

	/** NeoForge mod id — hyphens are not allowed there, unlike in resource namespaces. */
	public static final String MODID = "pocket_repose";
	/** Resource namespace, kept as-is so existing worlds and assets stay compatible. */
	public static final String MOD_ID = "pocket-repose";

	public static final ResourceLocation POCKET_DIMENSION_TYPE_ID =
			ResourceLocation.fromNamespaceAndPath(MOD_ID, "pocket_dimension_type");
	public static final ResourceKey<DimensionType> POCKET_DIMENSION_TYPE_KEY =
			ResourceKey.create(Registries.DIMENSION_TYPE, POCKET_DIMENSION_TYPE_ID);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<Biome> VOID_BIOME_KEY =
			ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(MOD_ID, "pocket_islands"));

	public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
			DeferredRegister.create(Registries.TRIGGER_TYPE, MOD_ID);

	public static final DeferredHolder<CriterionTrigger<?>, EnterPocketDimensionCriterion> ENTER_POCKET_DIMENSION =
			TRIGGERS.register("enter_pocket_dimension", EnterPocketDimensionCriterion::new);

	public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
			DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID);

	public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<VoidChunkGenerator>> VOID_CHUNK_GENERATOR =
			CHUNK_GENERATORS.register("void_chunk_generator", () -> VoidChunkGenerator.CODEC);

	public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<PortalChunkGenerator>> PORTAL_CHUNK_GENERATOR =
			CHUNK_GENERATORS.register("portal_chunk_generator", () -> PortalChunkGenerator.CODEC);

	public PocketRepose(IEventBus modEventBus, ModContainer modContainer) {

		LOGGER.info("Initializing " + MOD_ID);

		ModItems.registerModItems(modEventBus);
		ModBlocks.registerModBlocks(modEventBus);
		ModItemGroups.registerItemGroups(modEventBus);
		ModBlockEntities.registerBlockEntities(modEventBus);

		TRIGGERS.register(modEventBus);
		CHUNK_GENERATORS.register(modEventBus);

		IEventBus gameEventBus = NeoForge.EVENT_BUS;

		gameEventBus.addListener(PocketRepose::onServerStarted);
		gameEventBus.addListener(PocketRepose::onServerStopping);
		gameEventBus.addListener(PocketRepose::onServerTickPre);
		gameEventBus.addListener(PocketRepose::onServerTickPost);
		gameEventBus.addListener(PocketRepose::onRegisterCommands);
		gameEventBus.addListener(PortalChunkHandler::onChunkLoad);
		gameEventBus.addListener(PocketRepose::onEntityJoinLevel);
		gameEventBus.addListener(PocketRepose::onRightClickItem);
		gameEventBus.addListener(PocketRepose::onEntityInteract);
	}

	private static void onServerStarted(ServerStartedEvent event) {
		MinecraftServer server = event.getServer();

		SuitcaseRegistrySavedData.onServerStart(server);

		Path registryFile = server.getWorldPath(LevelResource.ROOT)
				.resolve("data")
				.resolve("pocket-repose")
				.resolve("dimension_registry")
				.resolve("registry.txt");

		if (!Files.exists(registryFile)) return;

		Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
		long seed = server.overworld().getSeed();

		try {
			for (String dimName : Files.readAllLines(registryFile)) {
				ResourceLocation worldId = ResourceLocation.fromNamespaceAndPath(MOD_ID, dimName);
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
	}

	private static void onServerStopping(ServerStoppingEvent event) {
		Fantasy.onServerStopping(event.getServer());
	}

	private static void onServerTickPre(ServerTickEvent.Pre event) {
		Fantasy.onServerTick(event.getServer());
	}

	private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide()) return;
		if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
		if (!(event.getLevel() instanceof ServerLevel destination)) return;

		ItemStack stack = itemEntity.getItem();
		if (!(stack.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof SuitcaseBlock)) return;

		CustomData beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (beData == null) return;

		CompoundTag nbt = beData.copyTag();
		String keystone = nbt.getString("BoundKeystone");
		if (keystone.isEmpty()) return;

		UUID suitcaseId = null;
		if (nbt.hasUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID)) {
			suitcaseId = nbt.getUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID);
		}

		SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(destination.getServer());
		if (tracker == null) return;

		String dimStr = destination.dimension().location().toString();

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

		if (nbt.contains("EnteredPlayers", Tag.TAG_LIST)) {
			ListTag players = nbt.getList("EnteredPlayers", Tag.TAG_COMPOUND);
			for (int i = 0; i < players.size(); i++) {
				CompoundTag p = players.getCompound(i);
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
	}

	private static void onServerTickPost(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();

		SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(server);
		if (tracker == null) return;

		long ticks = server.getTickCount();

		if (ticks % 20 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				String dimStr = player.serverLevel().dimension().location().toString();
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					ItemStack stack = player.getInventory().getItem(i);
					scanStackRecursivelyForSuitcases(
							tracker,
							stack,
							dimStr,
							player.getX(), player.getY() + 1.0, player.getZ(),
							player.getYRot(), player.getXRot(),
							MAX_NESTED_CONTAINER_DEPTH
					);

				}
			}
		}

		if (ticks % 40 == 0) {
			for (ServerLevel w : server.getAllLevels()) {
				String dimStr = w.dimension().location().toString();

				AABB searchBox = new AABB(
						w.getWorldBorder().getCenterX() - w.getWorldBorder().getSize() / 2,
						w.getMinBuildHeight(),
						w.getWorldBorder().getCenterZ() - w.getWorldBorder().getSize() / 2,
						w.getWorldBorder().getCenterX() + w.getWorldBorder().getSize() / 2,
						w.getMaxBuildHeight(),
						w.getWorldBorder().getCenterZ() + w.getWorldBorder().getSize() / 2
				);

				List<ItemEntity> itemEntities = w.getEntitiesOfClass(
						ItemEntity.class,
						searchBox,
						itemEntity -> {
							ItemStack st = itemEntity.getItem();
							if (st.isEmpty()) return false;

							if (st.getItem() instanceof BlockItem b && b.getBlock() instanceof SuitcaseBlock) return true;

							return st.get(DataComponents.CONTAINER) != null;
						}
				);

				for (ItemEntity item : itemEntities) {
					scanStackRecursivelyForSuitcases(
							tracker,
							item.getItem(),
							dimStr,
							item.getX(), item.getY() + 1.0, item.getZ(),
							0, 0,
							MAX_NESTED_CONTAINER_DEPTH
					);
				}

			}
		}

		if (ticks % 100 == 0) {
			for (ServerLevel w : server.getAllLevels()) {
				if (!w.dimension().equals(Level.OVERWORLD)
						&& !w.dimension().equals(Level.NETHER)
						&& !w.dimension().equals(Level.END)) {
					continue;
				}

				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (!player.serverLevel().equals(w)) continue;

					ChunkPos playerChunk = player.chunkPosition();
					int radius = 8;

					for (int cx = playerChunk.x - radius; cx <= playerChunk.x + radius; cx++) {
						for (int cz = playerChunk.z - radius; cz <= playerChunk.z + radius; cz++) {
							if (!w.hasChunk(cx, cz)) continue;

							LevelChunk chunk = w.getChunk(cx, cz);
							for (BlockEntity be : chunk.getBlockEntities().values()) {
								scanContainerForSuitcases(tracker, be, w);
							}
						}
					}
				}
			}
		}
	}

	private static final int MAX_NESTED_CONTAINER_DEPTH = 4;

	private static void scanStackRecursivelyForSuitcases(SuitcaseLocationTracker tracker, ItemStack stack, String dimStr, double x, double y, double z, float yaw, float pitch, int depth) {
		if (stack.isEmpty() || depth <= 0) return;

		updateSuitcaseLocationFromItem(tracker, stack, dimStr, x, y, z, yaw, pitch);

		ItemContainerContents container = stack.get(DataComponents.CONTAINER);
		if (container == null) return;

		for (ItemStack inner : container.nonEmptyItems()) {
			scanStackRecursivelyForSuitcases(tracker, inner, dimStr, x, y, z, yaw, pitch, depth - 1);
		}
	}

	private static void scanContainerForSuitcases(SuitcaseLocationTracker tracker, BlockEntity blockEntity, ServerLevel world) {
		if (!(blockEntity instanceof Container inventory)) return;

		BlockPos containerPos = blockEntity.getBlockPos();
		String dimStr = world.dimension().location().toString();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);

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

			CustomData beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
			if (beData == null) continue;

			CompoundTag nbt = beData.copyTag();
			String keystone = nbt.getString("BoundKeystone");
			if (keystone.isEmpty()) continue;

			UUID suitcaseId = null;
			if (nbt.hasUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID)) {
				suitcaseId = nbt.getUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID);
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

			if (!nbt.contains("EnteredPlayers", Tag.TAG_LIST)) continue;

			ListTag players = nbt.getList("EnteredPlayers", Tag.TAG_COMPOUND);
			for (int j = 0; j < players.size(); j++) {
				CompoundTag p = players.getCompound(j);
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

		CustomData beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (beData == null) return;

		CompoundTag nbt = beData.copyTag();
		String keystone = nbt.getString("BoundKeystone");
		if (keystone.isEmpty()) return;

		UUID suitcaseId = null;
		if (nbt.hasUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID)) {
			suitcaseId = nbt.getUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID);
		}

		if (suitcaseId != null) {
			tracker.updateSuitcaseLocation(
					suitcaseId,
					dimStr,
					x, y, z,
					SuitcaseLocationTracker.LocationType.ITEM_ENTITY
			);
		}

		if (!nbt.contains("EnteredPlayers", Tag.TAG_LIST)) return;

		ListTag players = nbt.getList("EnteredPlayers", Tag.TAG_COMPOUND);
		for (int i = 0; i < players.size(); i++) {
			CompoundTag p = players.getCompound(i);
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

	private static UUID ensureSuitcaseIdOnSuitcaseStack(ItemStack stack, CompoundTag beNbt) {
		if (beNbt.hasUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID)) {
			return beNbt.getUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID);
		}
		UUID id = UUID.randomUUID();
		beNbt.putUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID, id);
		return id;
	}

	private static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(
				LiteralArgumentBuilder.<CommandSourceStack>literal("pocketRepose")

						// mob entry setter
						.then(Commands.literal("setMobEntry")
								.executes(ctx -> {
									CommandSourceStack src = ctx.getSource();
									ServerLevel world = src.getLevel();
									ResourceLocation id = world.dimension().location();
									if (!id.getNamespace().equals("pocket-repose")
											|| !id.getPath().startsWith("pocket_dimension_")) {
										src.sendFailure(Component.literal("§cNot in a pocket dimension"));
										return 0;
									}
									Vec3 pos = src.getPosition();
									float yaw = src.getEntity().getYRot();
									float pitch = src.getEntity().getXRot();

									MobEntryData.get(world).setEntry(pos, yaw, pitch);
									src.sendSuccess(() -> Component.literal(
											String.format("§aMob entry set to %.2f, %.2f, %.2f", pos.x, pos.y, pos.z)
									), false);
									return 1;
								})
						)

						// player entry setter
						.then(Commands.literal("setPlayerEntry")
								.executes(ctx -> {
									CommandSourceStack src = ctx.getSource();
									ServerLevel world = src.getLevel();
									ResourceLocation id = world.dimension().location();
									if (!id.getNamespace().equals("pocket-repose")
											|| !id.getPath().startsWith("pocket_dimension_")) {
										src.sendFailure(Component.literal("§cNot in a pocket dimension"));
										return 0;
									}
									Vec3 pos = src.getPosition();
									float yaw = src.getEntity().getYRot();
									float pitch = src.getEntity().getXRot();

									PlayerEntryData.get(world).setEntry(pos, yaw, pitch);
									src.sendSuccess(() -> Component.literal(
											String.format("§aPlayer entry set to %.2f, %.2f, %.2f", pos.x, pos.y, pos.z)
									), false);
									return 1;
								})
						)

						// reset entry helper (kept from your original)
						.then(Commands.literal("resetPlayerEntry")
								.then(Commands.argument("dimension", StringArgumentType.word())
										.executes(ctx -> resetPocketDimension(ctx, StringArgumentType.getString(ctx, "dimension")))
								)
						)

						// list dimensions
						.then(Commands.literal("listDimensions")
								.requires(src -> src.hasPermission(2))
								.executes(ctx -> {
									CommandSourceStack src = ctx.getSource();
									src.sendSuccess(() -> Component.literal("§aPocket Dimensions Loaded:"), false);
									boolean foundAny = false;
									for (ServerLevel world : src.getServer().getAllLevels()) {
										ResourceLocation id = world.dimension().location();
										String namespace = id.getNamespace();
										String path = id.getPath();
										String prefix = "pocket_dimension_";

										if ("pocket-repose".equals(namespace) && path.startsWith(prefix)) {
											String suffix = path.substring(prefix.length());
											src.sendSuccess(() -> Component.literal(" " + suffix), false);
											foundAny = true;
										}
									}
									if (!foundAny) {
										src.sendSuccess(() -> Component.literal("§cNo pocket dimensions found."), false);
									}
									return 1;
								})
						)

						// canCaptureHostile true/false
						.then(Commands.literal("canCaptureHostile")
								.requires(src -> src.hasPermission(2))
								.then(Commands.argument("value", BoolArgumentType.bool())
										.executes(ctx -> {
											boolean value = BoolArgumentType.getBool(ctx, "value");
											setCanCaptureHostile(value);
											CommandSourceStack src = ctx.getSource();
											src.sendSuccess(() -> Component.literal(
													value ? "§aHostile mob capture enabled" : "§cHostile mob capture disabled"
											), false);
											return 1;
										})
								)
								.executes(ctx -> {
									CommandSourceStack src = ctx.getSource();
									boolean current = getCanCaptureHostile();
									src.sendSuccess(() -> Component.literal(
											"§7Hostile mob capture is currently: " + (current ? "§aEnabled" : "§cDisabled")
									), false);
									return 1;
								})
						)

						// spawnIsland true/false
						.then(Commands.literal("spawnIsland")
								.requires(src -> src.hasPermission(2))
								.then(Commands.argument("value", BoolArgumentType.bool())
										.executes(ctx -> {
											boolean value = BoolArgumentType.getBool(ctx, "value");
											setSpawnIsland(value);
											CommandSourceStack src = ctx.getSource();
											src.sendSuccess(() -> Component.literal(
													value ? "§aIsland structure spawning enabled" : "§cIsland structure spawning disabled (grass cube will spawn)"
											), false);
											return 1;
										})
								)
								.executes(ctx -> {
									CommandSourceStack src = ctx.getSource();
									boolean current = getSpawnIsland();
									src.sendSuccess(() -> Component.literal(
											"§7Island structure spawning is currently: " + (current ? "§aEnabled" : "§cDisabled")
									), false);
									return 1;
								})
						)

						// mob blacklist group
						.then(Commands.literal("mobBlacklist")
								.requires(src -> src.hasPermission(2))

								.then(Commands.literal("add")
										.then(Commands.argument("entity", StringArgumentType.string())
												.executes(ctx -> {
													CommandSourceStack src = ctx.getSource();
													String entityString = StringArgumentType.getString(ctx, "entity");

													try {
														ResourceLocation entityId = ResourceLocation.parse(entityString);
														EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityId);

														if (entityType == EntityType.PIG && !entityString.equals("minecraft:pig")) {
															src.sendFailure(Component.literal("§cUnknown entity type: " + entityString));
															return 0;
														}

														if (entityType == EntityType.PLAYER) {
															src.sendFailure(Component.literal("§cCannot blacklist players"));
															return 0;
														}

														addToBlacklist(entityType);
														src.sendSuccess(() -> Component.literal(
																"§aAdded " + entityId + " to blacklist"
														), false);
														return 1;
													} catch (Exception e) {
														src.sendFailure(Component.literal("§cInvalid entity identifier: " + entityString));
														return 0;
													}
												})
										)
								)

								.then(Commands.literal("remove")
										.then(Commands.argument("entity", StringArgumentType.string())
												.executes(ctx -> {
													CommandSourceStack src = ctx.getSource();
													String entityString = StringArgumentType.getString(ctx, "entity");

													try {
														ResourceLocation entityId = ResourceLocation.parse(entityString);
														EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityId);

														if (entityType == EntityType.PIG && !entityString.equals("minecraft:pig")) {
															src.sendFailure(Component.literal("§cUnknown entity type: " + entityString));
															return 0;
														}

														boolean removed = removeFromBlacklist(entityType);
														if (removed) {
															src.sendSuccess(() -> Component.literal(
																	"§aRemoved " + entityId + " from blacklist"
															), false);
														} else {
															src.sendSuccess(() -> Component.literal(
																	"§7" + entityId + " was not in blacklist"
															), false);
														}
														return 1;
													} catch (Exception e) {
														src.sendFailure(Component.literal("§cInvalid entity identifier: " + entityString));
														return 0;
													}
												})
										)
								)

								.then(Commands.literal("list")
										.executes(ctx -> {
											CommandSourceStack src = ctx.getSource();
											Set<EntityType<?>> blacklist = getEntityBlacklist();

											if (blacklist.isEmpty()) {
												src.sendSuccess(() -> Component.literal("§7No entities are blacklisted"), false);
											} else {
												src.sendSuccess(() -> Component.literal("§aBlacklisted entities:"), false);
												blacklist.forEach(entityType -> {
													ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
													src.sendSuccess(() -> Component.literal(" " + id), false);
												});
											}
											return 1;
										})
								)

								.then(Commands.literal("clear")
										.executes(ctx -> {
											CommandSourceStack src = ctx.getSource();
											int count = getEntityBlacklist().size();
											clearBlacklist();
											src.sendSuccess(() -> Component.literal(
													"§aCleared blacklist (removed " + count + " entities)"
											), false);
											return 1;
										})
								)
						)

						// allowRecursion true/false
						.then(Commands.literal("allowRecursion")
								.requires(src -> src.hasPermission(2))
								.then(Commands.argument("value", BoolArgumentType.bool())
										.executes(ctx -> {
											boolean value = BoolArgumentType.getBool(ctx, "value");
											setAllowRecursion(value);

											CommandSourceStack src = ctx.getSource();
											src.sendSuccess(() -> Component.literal(
													value ? "§aSuitcase recursion enabled" : "§cSuitcase recursion disabled"
											), false);
											return 1;
										})
								)
								.executes(ctx -> {
									CommandSourceStack src = ctx.getSource();
									boolean current = getAllowRecursion();
									src.sendSuccess(() -> Component.literal(
											"§7Suitcase recursion is currently: " + (current ? "§aEnabled" : "§cDisabled")
									), false);
									return 1;
								})
						)

		);
	}

	private static int resetPocketDimension(CommandContext<CommandSourceStack> ctx, String dimSuffix) {
		CommandSourceStack src = ctx.getSource();

		ResourceLocation dimId = ResourceLocation.fromNamespaceAndPath(MOD_ID, "pocket_dimension_" + dimSuffix);
		ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, dimId);
		ServerLevel targetWorld = src.getServer().getLevel(worldKey);

		if (targetWorld == null) {
			src.sendFailure(Component.literal("§cPocket dimension '" + dimSuffix + "' not found"));
			return 0;
		}

		BlockPos plankPos = new BlockPos(17, 96, 9);
		targetWorld.setBlockAndUpdate(plankPos, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());

		for (int y = 97; y <= 99; y++) {
			BlockPos airPos = new BlockPos(17, y, 9);
			targetWorld.setBlockAndUpdate(airPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		}

		BlockPos portalPos = new BlockPos(17, 100, 9);
		targetWorld.setBlockAndUpdate(portalPos, ModBlocks.PORTAL.get().defaultBlockState());

		PlayerEntryData playerData = PlayerEntryData.get(targetWorld);
		playerData.setEntry(new Vec3(17.5, 97.0, 9.5), 0f, 0f);

		src.sendSuccess(() -> Component.literal("§aPocket dimension '" + dimSuffix + "' entry reset"), false);
		return 1;
	}

	private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
		Player player = event.getEntity();
		Level world = event.getLevel();
		InteractionHand hand = event.getHand();
		ItemStack stack = event.getItemStack();

		if (world.isClientSide || hand != InteractionHand.MAIN_HAND) return;
		if (!(world instanceof ServerLevel sw)) return;

		if (stack.getItem() != Items.BONE && stack.getItem() != Items.LEAD) return;

		ResourceLocation id = sw.dimension().location();
		if (!"pocket-repose".equals(id.getNamespace())
				|| !id.getPath().startsWith("pocket_dimension_")) {
			return;
		}

		Vec3 pos = player.position();
		float yaw = player.getYRot();
		float pitch = player.getXRot();

		if (stack.getItem() == Items.BONE) {
			PlayerEntryData.get(sw).setEntry(pos, yaw, pitch);
			player.displayClientMessage(Component.literal(
					String.format("§aPlayer entry location set to %.1f, %.1f, %.1f", pos.x, pos.y, pos.z)
			), true);
		} else {
			MobEntryData.get(sw).setEntry(pos, yaw, pitch);
			player.displayClientMessage(Component.literal(
					String.format("§aMob entry location set to %.1f, %.1f, %.1f", pos.x, pos.y, pos.z)
			), true);
		}

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
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

	private static boolean isHostileMob(LivingEntity mob) {
		return mob instanceof Monster ||
				mob instanceof Spider ||
				mob instanceof EnderMan ||
				mob instanceof Piglin ||
				mob instanceof ZombifiedPiglin ||
				(mob instanceof Wolf wolf && wolf.isAngry());
	}

	private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		InteractionResult result = suitcaseMobTeleport(event);
		if (result == InteractionResult.PASS) {
			result = keyRescueMob(event);
		}

		if (result != InteractionResult.PASS) {
			event.setCanceled(true);
			event.setCancellationResult(result);
		}
	}

	private static InteractionResult suitcaseMobTeleport(PlayerInteractEvent.EntityInteract event) {
		Player player = event.getEntity();
		Level world = event.getLevel();
		InteractionHand hand = event.getHand();
		Entity entity = event.getTarget();

		if (world.isClientSide) return InteractionResult.PASS;

		if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof BlockItem bi)) return InteractionResult.PASS;

		net.minecraft.world.level.block.Block heldBlock = bi.getBlock();
		if (!(heldBlock instanceof SuitcaseBlock)) return InteractionResult.PASS;

		if (entity instanceof Player) {
			player.displayClientMessage(Component.literal("☒"), true);
			world.playSound(null,
					player.getX(), player.getY(), player.getZ(),
					SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
					SoundSource.PLAYERS,
					0.3f, 1.5f
			);
			return InteractionResult.FAIL;
		}

		CustomData beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (beData == null) {
			player.displayClientMessage(Component.literal("☒"), true);
			world.playSound(null,
					player.getX(), player.getY(), player.getZ(),
					SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
					SoundSource.PLAYERS,
					0.3f, 1.5f
			);
			return InteractionResult.FAIL;
		}

		CompoundTag beNbt = beData.copyTag();
		if (!beNbt.contains("BoundKeystone")) {
			player.displayClientMessage(Component.literal("☒"), true);
			world.playSound(null,
					player.getX(), player.getY(), player.getZ(),
					SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
					SoundSource.PLAYERS,
					0.3f, 1.5f
			);
			return InteractionResult.FAIL;
		}

		if (beNbt.getBoolean("Locked")) {
			player.displayClientMessage(Component.literal("☒"), true);
			world.playSound(null,
					player.getX(), player.getY(), player.getZ(),
					SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
					SoundSource.PLAYERS,
					0.3f, 1.5f
			);
			return InteractionResult.FAIL;
		}

		String keystone = beNbt.getString("BoundKeystone");
		ResourceLocation dimId = ResourceLocation.fromNamespaceAndPath(MOD_ID, "pocket_dimension_" + keystone);
		ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
		ServerLevel targetWorld = world.getServer().getLevel(dimKey);

		if (targetWorld == null) {
			player.displayClientMessage(Component.literal("§cPocket dimension not found"), true);
			return InteractionResult.FAIL;
		}

		if (!(entity instanceof LivingEntity mob)) return InteractionResult.PASS;

		if (isBlacklisted(mob.getType())) {
			player.displayClientMessage(Component.literal("☒"), true);
			world.playSound(null,
					player.getX(), player.getY(), player.getZ(),
					SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
					SoundSource.PLAYERS,
					0.3f, 1.5f
			);
			return InteractionResult.FAIL;
		}

		if (!canCaptureHostile && isHostileMob(mob)) {
			player.displayClientMessage(Component.literal("☒"), true);
			world.playSound(null,
					player.getX(), player.getY(), player.getZ(),
					SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
					SoundSource.PLAYERS,
					0.3f, 1.5f
			);
			return InteractionResult.FAIL;
		}

		UUID suitcaseId = ensureSuitcaseIdOnSuitcaseStack(stack, beNbt);
		stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(beNbt));

		SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(world.getServer());
		if (tracker != null && beNbt.contains("EnteredPlayers", Tag.TAG_LIST)) {
			String dimStr = ((ServerPlayer) player).serverLevel().dimension().location().toString();

			ListTag players = beNbt.getList("EnteredPlayers", Tag.TAG_COMPOUND);
			for (int i = 0; i < players.size(); i++) {
				CompoundTag playerData = players.getCompound(i);
				String uuid = playerData.getString("UUID");

				UUID expected = tracker.getLastSuitcase(keystone, uuid);
				if (expected != null && expected.equals(suitcaseId)) {
					tracker.updateLocation(
							keystone, uuid,
							dimStr, suitcaseId,
							player.getX(), player.getY() + 1.0, player.getZ(),
							player.getYRot(), player.getXRot(),
							SuitcaseLocationTracker.LocationType.ITEM_ENTITY
					);
				}
			}
		}

		MobEntryData data = MobEntryData.get(targetWorld);
		Vec3 dest = data.getEntryPos();
		float yaw = data.getEntryYaw();
		float pitch = data.getEntryPitch();

		DimensionTransition target = new DimensionTransition(
				targetWorld,
				dest,
				Vec3.ZERO,
				yaw,
				pitch,
				DimensionTransition.DO_NOTHING
		);

		mob.changeDimension(target);

		world.playSound(null,
				player.getX(), player.getY(), player.getZ(),
				SoundEvents.BUNDLE_DROP_CONTENTS,
				SoundSource.PLAYERS,
				2.0f, 1.0f
		);

		world.playSound(null,
				player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_PICKUP,
				SoundSource.PLAYERS,
				0.5f, 1.0f
		);

		return InteractionResult.SUCCESS;
	}

	private static InteractionResult keyRescueMob(PlayerInteractEvent.EntityInteract event) {
		Player player = event.getEntity();
		Level world = event.getLevel();
		InteractionHand hand = event.getHand();
		Entity entity = event.getTarget();

		if (world.isClientSide) return InteractionResult.PASS;
		if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

		ItemStack held = sp.getItemInHand(hand);
		if (!(held.getItem() instanceof KeystoneItem)) return InteractionResult.PASS;

		ResourceLocation dimId = sp.level().dimension().location();
		String namespace = dimId.getNamespace();
		String path = dimId.getPath();
		String prefix = "pocket_dimension_";

		if (!namespace.equals("pocket-repose") || !path.startsWith(prefix)) return InteractionResult.PASS;

		String keystoneName = path.substring(prefix.length());

		if (!(entity instanceof LivingEntity mob)) return InteractionResult.PASS;

		MinecraftServer server = sp.getServer();
		if (server == null) return InteractionResult.FAIL;

		SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(server);
		String playerUuid = sp.getStringUUID();

		UUID expectedSuitcaseId = (tracker == null) ? null : tracker.getLastSuitcase(keystoneName, playerUuid);

		boolean teleported = false;
		ServerLevel targetWorldUsed = null;
		Vec3 targetPosUsed = null;

		if (tracker != null) {
			SuitcaseLocationTracker.LocationData loc = tracker.getLocation(keystoneName, playerUuid);
			if (loc != null && loc.type != SuitcaseLocationTracker.LocationType.DESTROYED) {

				if (expectedSuitcaseId == null || (loc.suitcaseId != null && expectedSuitcaseId.equals(loc.suitcaseId))) {
					ServerLevel target = worldFromId(server, loc.dimensionId);
					if (target != null) {
						Vec3 dest = new Vec3(loc.x, loc.y, loc.z);
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
				ServerLevel target = worldFromId(server, loc.dimensionId);
				if (target != null) {
					Vec3 dest = new Vec3(loc.x, loc.y, loc.z);
					sp.displayClientMessage(Component.literal("§6No suitcase found — returning mob to last known point"), true);
					if (teleportMobTo(mob, target, dest)) {
						teleported = true;
						targetWorldUsed = target;
						targetPosUsed = dest;
					}
				}
			}
		}

		if (!teleported) {
			sp.displayClientMessage(Component.literal("§cNo suitcase exit found for mob"), true);
			return InteractionResult.FAIL;
		}

		sp.level().playSound(
				null,
				sp.getX(), sp.getY(), sp.getZ(),
				SoundEvents.BUNDLE_DROP_CONTENTS,
				SoundSource.PLAYERS,
				2.0f, 1.0f
		);

		if (targetWorldUsed != null && targetPosUsed != null) {
			targetWorldUsed.playSound(
					null,
					targetPosUsed.x, targetPosUsed.y, targetPosUsed.z,
					SoundEvents.BUNDLE_DROP_CONTENTS,
					SoundSource.PLAYERS,
					2.0f, 1.0f
			);
		}

		return InteractionResult.SUCCESS;
	}

	private static class TeleportResult {
		final boolean success;
		final ServerLevel world;
		final Vec3 pos;

		TeleportResult(boolean success, ServerLevel world, Vec3 pos) {
			this.success = success;
			this.world = world;
			this.pos = pos;
		}

		static TeleportResult fail() {
			return new TeleportResult(false, null, null);
		}
	}

	private static boolean teleportMobTo(LivingEntity mob, ServerLevel targetWorld, Vec3 pos) {
		DimensionTransition target = new DimensionTransition(
				targetWorld,
				pos,
				Vec3.ZERO,
				mob.getYRot(),
				mob.getXRot(),
				DimensionTransition.DO_NOTHING
		);
		mob.changeDimension(target);
		return true;
	}

	private static ServerLevel worldFromId(MinecraftServer server, String dimStr) {
		if (dimStr == null || dimStr.isEmpty()) return server.getLevel(Level.OVERWORLD);

		ResourceLocation id = ResourceLocation.tryParse(dimStr);
		if (id == null) return server.getLevel(Level.OVERWORLD);

		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
		ServerLevel w = server.getLevel(key);
		return (w != null) ? w : server.getLevel(Level.OVERWORLD);
	}

	private static boolean isSuitcaseWithKeystoneAndId(ItemStack stack, String keystoneName, UUID expectedSuitcaseId) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)
				|| !(bi.getBlock() instanceof SuitcaseBlock)) {
			return false;
		}

		CustomData beTag = stack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (beTag == null) return false;

		CompoundTag nbt = beTag.copyTag();
		if (!keystoneName.equals(nbt.getString("BoundKeystone"))) return false;

		if (!nbt.hasUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID)) return false;
		UUID id = nbt.getUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID);

		return expectedSuitcaseId.equals(id);
	}

	private static TeleportResult findSuitcaseAndTeleportMob(MinecraftServer server, LivingEntity mob, String keystoneName, UUID expectedSuitcaseId) {

		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			for (int i = 0; i < online.getInventory().getContainerSize(); i++) {
				ItemStack stack = online.getInventory().getItem(i);
				if (isSuitcaseWithKeystoneAndId(stack, keystoneName, expectedSuitcaseId)) {
					ServerLevel w = online.serverLevel();
					Vec3 dest = new Vec3(online.getX(), online.getY() + 1.0, online.getZ());
					teleportMobTo(mob, w, dest);
					return new TeleportResult(true, w, dest);
				}
			}
		}

		for (ServerLevel w : server.getAllLevels()) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.serverLevel() != w) continue;

				AABB box = p.getBoundingBox().inflate(256);
				List<ItemEntity> items = w.getEntitiesOfClass(ItemEntity.class, box,
						item -> isSuitcaseWithKeystoneAndId(item.getItem(), keystoneName, expectedSuitcaseId));

				if (!items.isEmpty()) {
					ItemEntity suitcaseItem = items.get(0);
					Vec3 dest = new Vec3(suitcaseItem.getX(), suitcaseItem.getY() + 1.0, suitcaseItem.getZ());
					teleportMobTo(mob, w, dest);
					return new TeleportResult(true, w, dest);
				}
			}
		}

		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			ServerLevel w = online.serverLevel();
			ChunkPos center = online.chunkPosition();
			int radius = 8;

			for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
				for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
					if (!w.hasChunk(cx, cz)) continue;

					LevelChunk chunk = w.getChunk(cx, cz);
					for (BlockEntity be : chunk.getBlockEntities().values()) {
						if (!(be instanceof Container inv)) continue;

						for (int i = 0; i < inv.getContainerSize(); i++) {
							ItemStack stack = inv.getItem(i);
							if (isSuitcaseWithKeystoneAndId(stack, keystoneName, expectedSuitcaseId)) {
								BlockPos cpos = be.getBlockPos();
								Vec3 dest = new Vec3(cpos.getX() + 0.5, cpos.getY() + 1.0, cpos.getZ() + 0.5);
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

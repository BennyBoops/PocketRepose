package net.bennyboops.modid;

import net.bennyboops.modid.block.ModBlocks;
import net.bennyboops.modid.block.PocketPortalBlock;
import net.bennyboops.modid.block.entity.ModBlockEntities;
import net.bennyboops.modid.criterion.EnterPocketDimensionCriterion;
import net.bennyboops.modid.item.ModItemGroups;
import net.bennyboops.modid.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

						// build the same config you use in KeystoneItem:
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
	}
}
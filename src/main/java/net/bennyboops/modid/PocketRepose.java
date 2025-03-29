package net.bennyboops.modid;

import net.bennyboops.modid.block.ModBlocks;
import net.bennyboops.modid.block.entity.ModBlockEntities;
import net.bennyboops.modid.item.ModItemGroups;
import net.bennyboops.modid.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PocketRepose implements ModInitializer {
	public static final String MOD_ID = "pocket-repose";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {

		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
		ModItemGroups.registerItemGroups();
		ModBlockEntities.registerBlockEntities();

		SuitcaseRegistryState.registerEvents();

		ServerWorldEvents.LOAD.register((server, world) -> {
			// Check if this is one of our dimensions
			if (world.getRegistryKey().getValue().getNamespace().equals("pocket-repose")) {
				String dimensionName = world.getRegistryKey().getValue().getPath();

				// Check if we need to place a structure
				Path structureMarkerPath = server.getSavePath(WorldSavePath.ROOT)
						.resolve("data")
						.resolve("pocket-repose")
						.resolve("pending_structures")
						.resolve(dimensionName + ".txt");

				if (Files.exists(structureMarkerPath)) {
					try {
						// Place the structure
						StructureTemplate template = server.getStructureTemplateManager()
								.getTemplate(new Identifier("pocket-repose", "pocket_island_01"))
								.orElse(null);

						if (template != null) {
							BlockPos pos = new BlockPos(0, 64, 0);
							template.place(
									world,
									pos,
									pos,
									new StructurePlacementData(),
									world.getRandom(),
									Block.NOTIFY_LISTENERS
							);

							// Delete the marker file
							Files.delete(structureMarkerPath);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		});
		LOGGER.info("Initializing " + MOD_ID);
	}
}
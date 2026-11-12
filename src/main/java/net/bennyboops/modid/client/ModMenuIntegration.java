package net.bennyboops.modid.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** The config button in Mod Menu's mods list. Only used when Mod Menu is installed. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PocketReposeConfigScreen::new;
    }
}

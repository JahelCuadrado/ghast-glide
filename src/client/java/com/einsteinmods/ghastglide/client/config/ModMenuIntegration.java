package com.einsteinmods.ghastglide.client.config;

import com.einsteinmods.ghastglide.client.GhastGlideClient;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu "Configure" button; only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
  @Override
  public ConfigScreenFactory<?> getModConfigScreenFactory() {
    return parent ->
        new GlideSettingsScreen(parent, GhastGlideClient.state(), GhastGlideClient.keyMappings());
  }
}

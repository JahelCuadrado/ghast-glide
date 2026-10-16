package com.einsteinmods.ghastglide.client;

import com.einsteinmods.ghastglide.GhastGlide;
import com.einsteinmods.ghastglide.client.config.GlideSettingsScreen;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.glide.GlideRules;
import com.einsteinmods.ghastglide.network.CallGhastPayload;
import com.einsteinmods.ghastglide.network.DismountPayload;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Mod key mappings. Defaults avoid vanilla keys and popular mods (N is Simple Voice Chat, Z is
 * JourneyMap / Xaero's Minimap): dismount on Left Alt, noclip toggle and settings unbound. Since
 * 26.x vanilla fires every mapping bound to a key, so a shared key still works, but triggers both
 * actions.
 */
public final class GlideKeyMappings {
  private static final KeyMapping.Category CATEGORY =
      KeyMapping.Category.register(GhastGlide.id("main"));

  private final KeyMapping dismount =
      KeyMappingHelper.registerKeyMapping(
          new KeyMapping("key.ghastglide.dismount", InputConstants.KEY_LALT, CATEGORY));
  private final KeyMapping toggleNoclip =
      KeyMappingHelper.registerKeyMapping(
          new KeyMapping(
              "key.ghastglide.toggle_noclip", InputConstants.UNKNOWN.getValue(), CATEGORY));
  private final KeyMapping callGhast =
      KeyMappingHelper.registerKeyMapping(
          new KeyMapping("key.ghastglide.call_ghast", InputConstants.KEY_G, CATEGORY));
  private final KeyMapping openSettings =
      KeyMappingHelper.registerKeyMapping(
          new KeyMapping(
              "key.ghastglide.open_settings", InputConstants.UNKNOWN.getValue(), CATEGORY));

  private final ClientGlideState state;

  public GlideKeyMappings(ClientGlideState state) {
    this.state = state;
  }

  /** Mappings shown in the settings screen, in display order. */
  public List<KeyMapping> configurable() {
    return List.of(dismount, callGhast, toggleNoclip, openSettings);
  }

  /** The dismount mapping, named in the mount message on servers with the mod. */
  public KeyMapping dismount() {
    return dismount;
  }

  public void onClientTick(Minecraft minecraft) {
    while (dismount.consumeClick()) {
      // Servers without the mod have no handler; vanilla sneak dismounts there.
      if (minecraft.player != null
          && GlideRules.isPiloting(minecraft.player)
          && ClientPlayNetworking.canSend(DismountPayload.TYPE)) {
        ClientPlayNetworking.send(DismountPayload.INSTANCE);
      }
    }
    while (toggleNoclip.consumeClick()) {
      toggleNoclip(minecraft);
    }
    while (callGhast.consumeClick()) {
      if (minecraft.player != null && ClientPlayNetworking.canSend(CallGhastPayload.TYPE)) {
        ClientPlayNetworking.send(CallGhastPayload.INSTANCE);
      }
    }
    while (openSettings.consumeClick()) {
      // Clicks only count without a screen open; the check keeps a burst from stacking screens.
      if (minecraft.gui.screen() == null) {
        minecraft.gui.setScreen(new GlideSettingsScreen(null, state, this));
      }
    }
  }

  private void toggleNoclip(Minecraft minecraft) {
    GlideSettings toggled = state.requested().withNoclipEnabled(!state.requested().noclipEnabled());
    state.update(toggled);
    if (minecraft.player == null) {
      return;
    }
    boolean forbidden = state.serverPolicy() != null && !state.serverPolicy().allowNoclip();
    String key =
        forbidden
            ? "message.ghastglide.noclip_forbidden"
            : toggled.noclipEnabled()
                ? "message.ghastglide.noclip_on"
                : "message.ghastglide.noclip_off";
    minecraft.player.sendOverlayMessage(Component.translatable(key));
  }
}

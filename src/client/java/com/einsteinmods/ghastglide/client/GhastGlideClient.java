package com.einsteinmods.ghastglide.client;

import com.einsteinmods.ghastglide.client.config.ClientSettingsFile;
import com.einsteinmods.ghastglide.config.JsonConfigFile;
import com.einsteinmods.ghastglide.glide.GlideRules;
import com.einsteinmods.ghastglide.network.ServerPolicyPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/** Client-only entrypoint. */
public final class GhastGlideClient implements ClientModInitializer {
  private static ClientGlideState state;
  private static GlideKeyMappings keyMappings;

  /** Shared with the config screen (Mod Menu entrypoint has no constructor arguments). */
  public static ClientGlideState state() {
    return state;
  }

  /** Shared with the config screen, which rebinds these mappings. */
  public static GlideKeyMappings keyMappings() {
    return keyMappings;
  }

  @Override
  public void onInitializeClient() {
    state =
        new ClientGlideState(
            new JsonConfigFile<>(
                FabricLoader.getInstance().getConfigDir().resolve(ClientSettingsFile.FILE_NAME),
                ClientSettingsFile.class,
                ClientSettingsFile.DEFAULTS));
    GlideRules.installClientBridge(state);

    ClientPlayNetworking.registerGlobalReceiver(
        ServerPolicyPayload.TYPE, (payload, context) -> state.onServerPolicy(payload.policy()));
    ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> state.onJoin());
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> state.onDisconnect());

    keyMappings = new GlideKeyMappings(state);
    ClientTickEvents.END_CLIENT_TICK.register(keyMappings::onClientTick);
  }
}

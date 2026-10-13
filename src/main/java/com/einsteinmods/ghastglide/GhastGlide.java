package com.einsteinmods.ghastglide;

import com.einsteinmods.ghastglide.anchor.GhastAttachments;
import com.einsteinmods.ghastglide.config.JsonConfigFile;
import com.einsteinmods.ghastglide.config.ServerPolicyFile;
import com.einsteinmods.ghastglide.core.ServerPolicy;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import com.einsteinmods.ghastglide.network.GlideNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/** Common (client + server) entrypoint. */
public final class GhastGlide implements ModInitializer {
  public static final String MOD_ID = "ghastglide";

  @Override
  public void onInitialize() {
    GhastAttachments.register();
    GlideNetworking.register();
    ServerLifecycleEvents.SERVER_STARTING.register(
        server -> ServerGlideSessions.start(loadFilePolicy()));
    ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerGlideSessions.stop());
  }

  public static Identifier id(String path) {
    return Identifier.fromNamespaceAndPath(MOD_ID, path);
  }

  /**
   * Every server (dedicated or integrated) reads its policy file; the singleplayer/LAN host is
   * exempted per player in {@link ServerGlideSessions#join}.
   */
  private static ServerPolicy loadFilePolicy() {
    return new JsonConfigFile<>(
            FabricLoader.getInstance().getConfigDir().resolve(ServerPolicyFile.FILE_NAME),
            ServerPolicyFile.class,
            ServerPolicyFile.DEFAULTS)
        .load()
        .toPolicy();
  }
}

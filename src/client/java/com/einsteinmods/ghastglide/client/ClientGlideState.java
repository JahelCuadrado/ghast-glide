package com.einsteinmods.ghastglide.client;

import com.einsteinmods.ghastglide.client.config.ClientPreferences;
import com.einsteinmods.ghastglide.client.config.ClientSettingsFile;
import com.einsteinmods.ghastglide.config.JsonConfigFile;
import com.einsteinmods.ghastglide.core.FlightInput;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.core.ServerPolicy;
import com.einsteinmods.ghastglide.core.SettingsResolver;
import com.einsteinmods.ghastglide.glide.ClientGlideBridge;
import com.einsteinmods.ghastglide.network.GlideSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Client-side glide state: the player's saved settings plus the policy of the current server. Glide
 * is active once a server with the mod has sent its policy, or on joining a server without the mod
 * (client-only mode: no noclip, capped speed, pitch steering). Client thread only.
 */
public final class ClientGlideState implements ClientGlideBridge {
  private final JsonConfigFile<ClientSettingsFile> file;
  private GlideSettings requested;
  private ClientPreferences preferences;
  private @Nullable ServerPolicy serverPolicy;
  private @Nullable GlideSettings effective;
  private boolean clientOnlyMode;

  public ClientGlideState(JsonConfigFile<ClientSettingsFile> file) {
    this.file = file;
    ClientSettingsFile loaded = file.load();
    this.requested = loaded.toSettings();
    this.preferences = loaded.toPreferences();
  }

  public GlideSettings requested() {
    return requested;
  }

  public ClientPreferences preferences() {
    return preferences;
  }

  /** Persists new settings and, when connected to a modded server, sends them. */
  public void update(GlideSettings settings) {
    update(settings, preferences);
  }

  /** Persists both; only {@code settings} reach the server, preferences stay on this client. */
  public void update(GlideSettings settings, ClientPreferences newPreferences) {
    requested = settings.sanitized();
    preferences = newPreferences;
    file.save(ClientSettingsFile.from(requested, preferences));
    recomputeEffective();
    if (serverPolicy != null && ClientPlayNetworking.canSend(GlideSettingsPayload.TYPE)) {
      ClientPlayNetworking.send(new GlideSettingsPayload(requested));
    }
  }

  public void onServerPolicy(ServerPolicy policy) {
    clientOnlyMode = false;
    serverPolicy = policy;
    recomputeEffective();
    ClientPlayNetworking.send(new GlideSettingsPayload(requested));
  }

  /**
   * Called once play is ready; Fabric has already applied the channels the server announced during
   * configuration, so a server with the mod can receive our settings payload at this point.
   */
  public void onJoin() {
    if (ClientPlayNetworking.canSend(GlideSettingsPayload.TYPE)) {
      return;
    }
    clientOnlyMode = true;
    serverPolicy = ServerPolicy.WITHOUT_SERVER_MOD;
    recomputeEffective();
  }

  public void onDisconnect() {
    clientOnlyMode = false;
    serverPolicy = null;
    effective = null;
  }

  /**
   * Policy in force: the server's, {@link ServerPolicy#WITHOUT_SERVER_MOD} on servers without the
   * mod, or null before either is known.
   */
  public @Nullable ServerPolicy serverPolicy() {
    return serverPolicy;
  }

  /**
   * True once a server with the mod has sent its policy: glide is server-backed, so the mod's
   * dismount key (not sneak) dismounts. False before the policy arrives and in client-only mode.
   */
  public boolean hasModdedServer() {
    return serverPolicy != null && !clientOnlyMode;
  }

  private void recomputeEffective() {
    effective = serverPolicy == null ? null : SettingsResolver.effective(requested, serverPolicy);
  }

  @Override
  public @Nullable GlideSettings effectiveSettings(Player rider) {
    return rider instanceof LocalPlayer ? effective : null;
  }

  @Override
  public FlightInput input(Player rider) {
    if (!(rider instanceof LocalPlayer localPlayer)) {
      return FlightInput.NONE;
    }
    Input keys = localPlayer.input.keyPresses;
    return new FlightInput(
        localPlayer.xxa,
        localPlayer.zza,
        keys.jump(),
        keys.shift(),
        keys.sprint(),
        localPlayer.getYRot(),
        localPlayer.getXRot());
  }

  @Override
  public boolean pitchSteering(Player rider) {
    return clientOnlyMode && rider instanceof LocalPlayer;
  }
}

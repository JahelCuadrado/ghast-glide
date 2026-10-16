package com.einsteinmods.ghastglide.glide;

import com.einsteinmods.ghastglide.anchor.GhastAnchors;
import com.einsteinmods.ghastglide.anchor.GhastCalls;
import com.einsteinmods.ghastglide.core.ControlScheme;
import com.einsteinmods.ghastglide.core.FlightInput;
import com.einsteinmods.ghastglide.core.GlideSettings;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Single entry point that mixins use to decide whether glide applies, on either logical side. */
public final class GlideRules {
  private static volatile ClientGlideBridge clientBridge = ClientGlideBridge.NONE;

  private GlideRules() {}

  public static void installClientBridge(ClientGlideBridge bridge) {
    clientBridge = bridge;
  }

  /** Effective settings of the player flying {@code ghast}, or null if glide does not apply. */
  public static @Nullable GlideSettings activeSettings(HappyGhast ghast) {
    return ghast.getControllingPassenger() instanceof Player rider ? settingsFor(rider) : null;
  }

  public static @Nullable GlideSettings settingsFor(Player rider) {
    return rider.level().isClientSide()
        ? clientBridge.effectiveSettings(rider)
        : ServerGlideSessions.get(rider);
  }

  /**
   * Gliding with noclip, flying to a caller that has noclip, or getting unstuck after a dismount
   * (the last two server only).
   */
  public static boolean isNoclipActive(HappyGhast ghast) {
    GlideSettings settings = activeSettings(ghast);
    return (settings != null && settings.noclipEnabled())
        || GhastCalls.isNoclipCall(ghast)
        || GhastAnchors.isGettingUnstuck(ghast);
  }

  /**
   * True when {@code rider} is flying a ghast with glide and the mod's controls, so sneak means
   * "descend". With {@link ControlScheme#VANILLA} sneak dismounts as in vanilla.
   */
  public static boolean isGliding(Player rider) {
    GlideSettings settings = pilotSettings(rider);
    return settings != null && usesModControls(settings);
  }

  /** True when {@code rider} flies a ghast with glide, whatever the control scheme. */
  public static boolean isPiloting(Player rider) {
    return pilotSettings(rider) != null;
  }

  /**
   * True when {@code settings} replace vanilla flight with the mod's ({@code HappyGhast#travel}).
   */
  public static boolean usesModControls(GlideSettings settings) {
    return settings.controlScheme() == ControlScheme.MOD;
  }

  private static @Nullable GlideSettings pilotSettings(Player rider) {
    Entity vehicle = rider.getVehicle();
    return vehicle instanceof HappyGhast ghast && ghast.getControllingPassenger() == rider
        ? settingsFor(rider)
        : null;
  }

  public static FlightInput input(Player rider) {
    return clientBridge.input(rider);
  }

  public static boolean pitchSteering(Player rider) {
    return clientBridge.pitchSteering(rider);
  }
}

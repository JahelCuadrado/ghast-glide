package com.einsteinmods.ghastglide.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import org.jspecify.annotations.Nullable;

/**
 * Which Happy Ghast "Hide ghast in first person" hides this frame: the one the local player rides,
 * while the option is on and the camera is not detached. Stateless, so render hooks never see a
 * stale ghast; each call costs a few field reads and allocates nothing.
 */
public final class FirstPersonGhastHiding {
  private FirstPersonGhastHiding() {}

  /** The ridden ghast to hide, or null when nothing is hidden. Cheapest checks first. */
  public static @Nullable Entity hiddenGhast(Minecraft minecraft, Camera camera) {
    if (!GhastGlideClient.state().preferences().hideGhastInFirstPerson() || camera.isDetached()) {
      return null;
    }
    LocalPlayer player = minecraft.player;
    if (player == null) {
      return null;
    }
    Entity vehicle = player.getVehicle();
    return vehicle instanceof HappyGhast ? vehicle : null;
  }
}

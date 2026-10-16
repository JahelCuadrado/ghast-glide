package com.einsteinmods.ghastglide.client;

import com.einsteinmods.ghastglide.glide.BoatHanging;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lets a rider's right-click with a boat target the Happy Ghast they ride. Vanilla crosshair
 * picking never returns the player's own vehicle ({@code ProjectileUtil#getEntityHitResult} skips
 * entities sharing the player's root vehicle unless the eye is inside a box), so the click would
 * otherwise reach {@code BoatItem#use} and, with no block in reach, do nothing.
 *
 * <p>Runs only when the use key fires ({@code Minecraft#startUseItem}), never per frame or tick.
 */
public final class RiddenGhastTarget {
  private RiddenGhastTarget() {}

  /**
   * The ridden ghast as an entity hit when the view ray reaches its box before anything vanilla
   * picked and the held boat would really hang ({@link BoatHanging#canHang}); else {@code null}, so
   * vanilla uses its own hit unchanged.
   */
  public static @Nullable EntityHitResult boatTarget(
      LocalPlayer player, @Nullable HitResult vanillaHit) {
    if (!(player.getVehicle() instanceof HappyGhast ghast)) {
      return null;
    }
    Vec3 eye = player.getEyePosition();
    AABB box = ghast.getBoundingBox().inflate(ghast.getPickRadius());
    Vec3 hitLocation;
    if (box.contains(eye)) {
      hitLocation = eye;
    } else {
      Vec3 reach = eye.add(player.getViewVector(1.0F).scale(player.entityInteractionRange()));
      Optional<Vec3> clip = box.clip(eye, reach);
      if (clip.isEmpty()) {
        return null;
      }
      hitLocation = clip.get();
    }
    if (vanillaHit != null
        && vanillaHit.getType() != HitResult.Type.MISS
        && vanillaHit.getLocation().distanceToSqr(eye) < hitLocation.distanceToSqr(eye)) {
      return null;
    }
    if (!BoatHanging.canHang(ghast, player, InteractionHand.MAIN_HAND)) {
      return null;
    }
    return new EntityHitResult(ghast, hitLocation);
  }
}

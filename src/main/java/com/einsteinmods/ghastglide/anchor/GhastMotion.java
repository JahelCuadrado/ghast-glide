package com.einsteinmods.ghastglide.anchor;

import com.einsteinmods.ghastglide.core.ArrivalRamp;
import com.einsteinmods.ghastglide.core.CreativeFlightModel;
import com.einsteinmods.ghastglide.core.GlideSettings;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.phys.Vec3;

/**
 * Autonomous ghast flight shared by calls and surfacing: straight moves with {@link ArrivalRamp} at
 * the default glide speeds and acceleration, and smooth yaw turns. Moves use {@code Entity#move},
 * so blocks collide unless the noclip collision hook says otherwise.
 */
final class GhastMotion {
  /** Calls and surfacing always fly with default glide settings, whoever called. */
  static final GlideSettings MODEL_SETTINGS = GlideSettings.DEFAULTS;

  static final double HORIZONTAL_TOP_SPEED =
      CreativeFlightModel.horizontalTopSpeed(MODEL_SETTINGS, false);
  static final double VERTICAL_TOP_SPEED = CreativeFlightModel.verticalTopSpeed(MODEL_SETTINGS);

  /** Drag of {@link #MODEL_SETTINGS}, computed once instead of per moving ghast per tick. */
  static final double MODEL_DRAG = CreativeFlightModel.drag(MODEL_SETTINGS);

  /** Fraction of the remaining yaw turned per tick, as vanilla {@code HappyGhast#tickRidden}. */
  static final float TURN_RATE = 0.08F;

  static final double FULL_THRUST = 1.0;
  static final double AT_REST = 0.0;

  /**
   * Arrival check for moves driven by {@code ArrivalRamp}: the ramp lands exactly on the target, so
   * this only absorbs double rounding (under 1e-8 at the world border). The last braking step can
   * be any length up to one impulse; a larger tolerance would skip it and stop the ghast short with
   * a jump in speed.
   */
  static final double ARRIVAL_TOLERANCE = 1.0E-6;

  private GhastMotion() {}

  /**
   * Moves one tick towards {@code offset} (relative target) and returns the velocity left after
   * drag, measured from the real displacement so collisions kill speed.
   */
  static double moveToward(
      HappyGhast ghast, Vec3 offset, double speed, double thrust, double topSpeed) {
    double distance = offset.length();
    double step =
        ArrivalRamp.step(
            speed, distance, thrust, CreativeFlightModel.impulseForDrag(topSpeed, MODEL_DRAG));
    if (step <= 0.0) {
      return AT_REST;
    }
    Vec3 start = ghast.position();
    ghast.move(MoverType.SELF, offset.scale(step / distance));
    return ghast.position().distanceTo(start) * MODEL_DRAG;
  }

  /**
   * Turns yaw, body and head {@link #TURN_RATE} of the way to {@code targetYaw}, snapping once
   * within {@code snapDegrees}. Returns the absolute yaw error left, in degrees.
   */
  static float turnToward(HappyGhast ghast, float targetYaw, float snapDegrees) {
    float difference = Mth.wrapDegrees(targetYaw - ghast.getYRot());
    float yaw =
        Math.abs(difference) <= snapDegrees ? targetYaw : ghast.getYRot() + difference * TURN_RATE;
    ghast.setYRot(yaw);
    ghast.setYBodyRot(yaw);
    ghast.setYHeadRot(yaw);
    return Math.abs(Mth.wrapDegrees(targetYaw - yaw));
  }

  /** Vanilla yaw of the horizontal direction (dx, dz). */
  static float yawOf(double deltaX, double deltaZ) {
    return (float) -Math.toDegrees(Math.atan2(deltaX, deltaZ));
  }
}

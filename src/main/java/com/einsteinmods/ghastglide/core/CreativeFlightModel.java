package com.einsteinmods.ghastglide.core;

/**
 * Creative-style flight physics (see {@code LocalPlayer.aiStep}, {@code Player.travel}, {@code
 * Entity.moveRelative}, {@code LivingEntity.travelInAir}) with tunable acceleration.
 *
 * <p>Top speeds are the vanilla creative terminal speeds scaled by the multipliers. Drag {@code d}
 * is derived from {@link GlideSettings#accelerationSeconds()} so that 90% of top speed is reached
 * in that time: {@code d = 0.1^(1 / ticks)}. The per-tick impulse is {@code top * (1 - d) / d}, so
 * the terminal speed {@code impulse * d / (1 - d)} equals {@code top} whatever the acceleration.
 *
 * <p>Per tick: {@link #accelerate} before moving, then the entity moves, then {@link #applyDrag}.
 */
public final class CreativeFlightModel {

  /** Vanilla {@code Abilities.flyingSpeed} default. */
  public static final double BASE_FLYING_SPEED = 0.05;

  /** Vanilla vertical impulse is {@code flyingSpeed * 3} per tick. */
  public static final double VERTICAL_IMPULSE_FACTOR = 3.0;

  /** Vanilla air drag applied to horizontal motion. */
  public static final double HORIZONTAL_DRAG = 0.91;

  /** Vanilla creative-flight damping applied to vertical motion. */
  public static final double VERTICAL_DRAG = 0.6;

  public static final double TICKS_PER_SECOND = 20.0;

  /** Vanilla creative horizontal terminal speed: {@code 0.05 * 0.91 / 0.09 ≈ 0.5056}. */
  public static final double CREATIVE_HORIZONTAL_TOP_SPEED =
      BASE_FLYING_SPEED * HORIZONTAL_DRAG / (1.0 - HORIZONTAL_DRAG);

  public static final double CREATIVE_HORIZONTAL_BLOCKS_PER_SECOND =
      CREATIVE_HORIZONTAL_TOP_SPEED * TICKS_PER_SECOND;

  /** Vanilla creative vertical terminal speed: {@code 0.15 * 0.6 / 0.4 = 0.225}. */
  public static final double CREATIVE_VERTICAL_TOP_SPEED =
      BASE_FLYING_SPEED * VERTICAL_IMPULSE_FACTOR * VERTICAL_DRAG / (1.0 - VERTICAL_DRAG);

  /** Fraction of top speed still missing after {@code accelerationSeconds} (90% reached). */
  public static final double REMAINING_FRACTION_AFTER_ACCELERATION = 0.1;

  private static final double MIN_INPUT_LENGTH_SQUARED = 1.0E-7;

  /**
   * Highest per-tick speed in pitch-steered mode. Vanilla {@code
   * ServerGamePacketListenerImpl#handleMoveVehicle} rejects a vehicle move when {@code movedDist -
   * expectedDist > 100} (squared blocks, i.e. 10 blocks/tick); 9 leaves a 10% margin for the
   * server's stale expected motion and float rounding.
   */
  public static final double MAX_PITCH_STEERED_SPEED = 9.0;

  /**
   * Last {@link #drag} result: settings change rarely, but glide asks for drag twice per tick.
   * Immutable entry behind a volatile field, so any thread reads a consistent pair. The NaN seed is
   * also correct for its key: {@code pow(0.1, 1 / NaN)} is NaN.
   */
  private static volatile DragEntry lastDrag = new DragEntry(Double.NaN, Double.NaN);

  private CreativeFlightModel() {}

  /** Adds this tick's input impulse to the current velocity. */
  public static Motion accelerate(Motion current, FlightInput input, GlideSettings settings) {
    double drag = drag(settings);
    double impulsePerTopSpeed = (1.0 - drag) / drag;
    double horizontalSpeed = horizontalTopSpeed(settings, input.sprint()) * impulsePerTopSpeed;
    double verticalImpulse =
        input.verticalDirection() * verticalTopSpeed(settings) * impulsePerTopSpeed;

    double strafe = input.strafe();
    double forward = input.forward();
    double lengthSquared = strafe * strafe + forward * forward;
    double deltaX = 0.0;
    double deltaZ = 0.0;
    if (lengthSquared >= MIN_INPUT_LENGTH_SQUARED) {
      double scale =
          lengthSquared > 1.0 ? horizontalSpeed / Math.sqrt(lengthSquared) : horizontalSpeed;
      double scaledStrafe = strafe * scale;
      double scaledForward = forward * scale;
      double yawRadians = Math.toRadians(input.yawDegrees());
      double sin = Math.sin(yawRadians);
      double cos = Math.cos(yawRadians);
      deltaX = scaledStrafe * cos - scaledForward * sin;
      deltaZ = scaledForward * cos + scaledStrafe * sin;
    }

    return new Motion(current.x() + deltaX, current.y() + verticalImpulse, current.z() + deltaZ);
  }

  /**
   * Pitch-steered variant, used on servers without the mod: forward follows the camera pitch
   * (looking down + forward descends), jump ascends like creative and sneak is ignored because
   * vanilla dismounts on it. With a level look it equals {@link #accelerate} without descend. The
   * result is clamped to {@link #MAX_PITCH_STEERED_SPEED} because servers without the mod apply the
   * vanilla vehicle speed check to the colinear look and jump impulses.
   */
  public static Motion acceleratePitchSteered(
      Motion current, FlightInput input, GlideSettings settings) {
    // Same drag on every axis, so the terminal velocity follows the look direction.
    double drag = drag(settings);
    double impulsePerTopSpeed = (1.0 - drag) / drag;
    double lookSpeed = horizontalTopSpeed(settings, input.sprint()) * impulsePerTopSpeed;
    double ascendImpulse = input.ascend() ? verticalTopSpeed(settings) * impulsePerTopSpeed : 0.0;

    double strafe = input.strafe();
    double forward = input.forward();
    // Tilting forward by the pitch keeps the input length, so normalisation matches accelerate.
    double lengthSquared = strafe * strafe + forward * forward;
    double deltaX = 0.0;
    double deltaY = 0.0;
    double deltaZ = 0.0;
    if (lengthSquared >= MIN_INPUT_LENGTH_SQUARED) {
      double scale = lengthSquared > 1.0 ? lookSpeed / Math.sqrt(lengthSquared) : lookSpeed;
      double pitchRadians = Math.toRadians(input.pitchDegrees());
      double scaledStrafe = strafe * scale;
      double scaledLevelForward = forward * Math.cos(pitchRadians) * scale;
      deltaY = -forward * Math.sin(pitchRadians) * scale;
      double yawRadians = Math.toRadians(input.yawDegrees());
      double sin = Math.sin(yawRadians);
      double cos = Math.cos(yawRadians);
      deltaX = scaledStrafe * cos - scaledLevelForward * sin;
      deltaZ = scaledLevelForward * cos + scaledStrafe * sin;
    }

    return clampSpeed(
        new Motion(
            current.x() + deltaX, current.y() + deltaY + ascendImpulse, current.z() + deltaZ),
        MAX_PITCH_STEERED_SPEED);
  }

  /** Damps the velocity left after moving (already zeroed on collided axes by the caller). */
  public static Motion applyDrag(Motion afterMove, GlideSettings settings) {
    double drag = drag(settings);
    return new Motion(afterMove.x() * drag, afterMove.y() * drag, afterMove.z() * drag);
  }

  /**
   * Per-tick velocity factor: {@code 0.1^(1 / ticks)}, applied to every axis. Cached for the last
   * acceleration time (exact bit match), so a steady glide does not call {@code Math.pow}.
   */
  public static double drag(GlideSettings settings) {
    double accelerationSeconds = settings.accelerationSeconds();
    DragEntry cached = lastDrag;
    if (Double.compare(cached.accelerationSeconds(), accelerationSeconds) == 0) {
      return cached.drag();
    }
    double ticks = accelerationSeconds * TICKS_PER_SECOND;
    double drag = Math.pow(REMAINING_FRACTION_AFTER_ACCELERATION, 1.0 / ticks);
    lastDrag = new DragEntry(accelerationSeconds, drag);
    return drag;
  }

  /** Per-tick impulse that, with {@link #drag}, gives {@code topSpeed} as terminal speed. */
  public static double impulse(double topSpeed, GlideSettings settings) {
    return impulseForDrag(topSpeed, drag(settings));
  }

  /** As {@link #impulse}, for a drag the caller already has (e.g. a constant). */
  public static double impulseForDrag(double topSpeed, double drag) {
    return topSpeed * (1.0 - drag) / drag;
  }

  /** Horizontal top speed in blocks per tick. */
  public static double horizontalTopSpeed(GlideSettings settings, boolean sprint) {
    return CREATIVE_HORIZONTAL_TOP_SPEED
        * settings.horizontalMultiplier()
        * (sprint ? settings.sprintMultiplier() : 1.0);
  }

  /** Vertical top speed in blocks per tick. */
  public static double verticalTopSpeed(GlideSettings settings) {
    return CREATIVE_VERTICAL_TOP_SPEED * settings.verticalMultiplier();
  }

  private static Motion clampSpeed(Motion motion, double maxSpeed) {
    double speedSquared =
        motion.x() * motion.x() + motion.y() * motion.y() + motion.z() * motion.z();
    if (speedSquared <= maxSpeed * maxSpeed) {
      return motion;
    }
    double scale = maxSpeed / Math.sqrt(speedSquared);
    return new Motion(motion.x() * scale, motion.y() * scale, motion.z() * scale);
  }

  private record DragEntry(double accelerationSeconds, double drag) {}
}

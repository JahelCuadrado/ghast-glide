package com.einsteinmods.ghastglide.anchor;

import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.core.LeaveMode;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Calling the last-ridden ghast. The ghast is moved directly ({@link GhastMotion}) while vanilla AI
 * is held still by the anchor: vanilla {@code GhastMoveControl} only adds a small impulse every few
 * ticks and gives up on any target with blocks in the way, so it cannot fly through blocks nor
 * reach ~6 blocks/s. {@code Entity#move} collides normally, or ignores blocks through the noclip
 * collision hook when the caller has noclip enabled.
 *
 * <p>The ghast turns towards where it flies and only thrusts as much as it faces that way, speeds
 * up and brakes like default glide, and on arrival turns to face the caller before anchoring (a
 * caller whose leave mode is {@link LeaveMode#VANILLA} then gets it released to vanilla AI). A
 * ghast already near the caller when called does not move: it only turns to face them.
 */
public final class GhastCalls {
  public static final String OUT_OF_RANGE_MESSAGE = "message.ghastglide.ghast_out_of_range";

  static final double MAX_CALL_DISTANCE = 100.0;

  /**
   * 30 seconds: enough for {@link #MAX_CALL_DISTANCE} with detours; then it anchors where it is.
   */
  static final int CALL_TIMEOUT_TICKS = 600;

  /**
   * Gap between the ghast's side and the caller (adult ghast centre ends 6 blocks away): close
   * enough to board in a few steps without the ghast feeling glued to the player.
   */
  static final double SIDE_CLEARANCE = 4.0;

  /** Yaw error under which the ghast snaps to the wanted yaw (arrival facing is then exact). */
  static final float YAW_SNAP_DEGREES = 2.0F;

  private static final double MIN_HORIZONTAL_LENGTH_SQUARED = 1.0E-4;

  private GhastCalls() {}

  /** Payload entry point: starts a call and tells the player when no ghast can come. */
  public static CallResult requestCall(Player player, ServerLevel level) {
    CallResult result = call(player, level);
    if (result == CallResult.OUT_OF_RANGE) {
      player.sendOverlayMessage(Component.translatable(OUT_OF_RANGE_MESSAGE));
    }
    return result;
  }

  public static CallResult call(Player player, ServerLevel level) {
    UUID ghastId = player.getAttached(GhastAttachments.LAST_RIDDEN_GHAST);
    if (ghastId == null
        || !(level.getEntity(ghastId) instanceof HappyGhast ghast)
        || !ghast.isAlive()
        || ghast.distanceToSqr(player) > MAX_CALL_DISTANCE * MAX_CALL_DISTANCE) {
      return CallResult.OUT_OF_RANGE;
    }
    if (ghast.isVehicle() || ghast.isLeashed()) {
      return CallResult.BUSY;
    }
    GlideSettings settings = ServerGlideSessions.get(player);
    boolean noclip = settings != null && settings.noclipEnabled();
    LeaveMode leaveMode = GhastIdleDrift.leaveModeOf(player);
    GhastAnchors.anchor(ghast, leaveMode);
    ghast.setAttached(
        GhastAttachments.CALL,
        new GhastCall(
            player,
            noclip,
            leaveMode,
            isAlreadyNear(ghast, player),
            CALL_TIMEOUT_TICKS,
            GhastMotion.AT_REST));
    return CallResult.STARTED;
  }

  /** True while {@code ghast} flies to a caller that has noclip enabled. */
  public static boolean isNoclipCall(HappyGhast ghast) {
    GhastCall call = ghast.getAttached(GhastAttachments.CALL);
    return call != null && call.noclip();
  }

  /** One flight step of an ongoing call; the anchor keeps vanilla AI still meanwhile. */
  static void tick(HappyGhast ghast) {
    GhastCall call = ghast.getAttached(GhastAttachments.CALL);
    if (call == null) {
      return;
    }
    Player caller = call.caller();
    if (call.remainingTicks() <= 0
        || !caller.isAlive()
        || caller.isRemoved()
        || caller.level() != ghast.level()) {
      GhastAnchors.anchor(ghast, call.leaveMode());
      return;
    }
    Vec3 offset =
        call.alreadyNear() ? Vec3.ZERO : targetBeside(ghast, caller).subtract(ghast.position());
    if (offset.length() <= GhastMotion.ARRIVAL_TOLERANCE) {
      float callerYaw =
          GhastMotion.yawOf(caller.getX() - ghast.getX(), caller.getZ() - ghast.getZ());
      if (GhastMotion.turnToward(ghast, callerYaw, YAW_SNAP_DEGREES) == 0.0F) {
        GhastAnchors.anchor(ghast, call.leaveMode());
      } else {
        ghast.setAttached(GhastAttachments.CALL, call.advanced(GhastMotion.AT_REST));
      }
      return;
    }
    double thrust = GhastMotion.FULL_THRUST;
    if (offset.horizontalDistanceSqr() >= MIN_HORIZONTAL_LENGTH_SQUARED) {
      float yawError =
          GhastMotion.turnToward(ghast, GhastMotion.yawOf(offset.x, offset.z), YAW_SNAP_DEGREES);
      thrust = Math.max(0.0, Math.cos(Math.toRadians(yawError)));
    }
    double speed =
        GhastMotion.moveToward(
            ghast, offset, call.speed(), thrust, GhastMotion.HORIZONTAL_TOP_SPEED);
    ghast.setAttached(GhastAttachments.CALL, call.advanced(speed));
  }

  /** Beside the caller at their feet height, on the side the ghast comes from. */
  private static Vec3 targetBeside(HappyGhast ghast, Player caller) {
    Vec3 horizontal = new Vec3(ghast.getX() - caller.getX(), 0.0, ghast.getZ() - caller.getZ());
    if (horizontal.lengthSqr() < MIN_HORIZONTAL_LENGTH_SQUARED) {
      horizontal = Vec3.directionFromRotation(0.0F, caller.getYRot());
    }
    return caller.position().add(horizontal.normalize().scale(stopDistance(ghast)));
  }

  /**
   * Within the stop distance horizontally and vertically overlapping the caller's feet (feet
   * heights at most one ghast height apart): flying would only move the ghast away or onto them.
   */
  private static boolean isAlreadyNear(HappyGhast ghast, Player caller) {
    double stopDistance = stopDistance(ghast);
    double deltaX = ghast.getX() - caller.getX();
    double deltaZ = ghast.getZ() - caller.getZ();
    return deltaX * deltaX + deltaZ * deltaZ <= stopDistance * stopDistance
        && Math.abs(ghast.getY() - caller.getY()) <= ghast.getBbHeight();
  }

  /** Horizontal distance from the caller to the ghast's centre where a call stops. */
  private static double stopDistance(HappyGhast ghast) {
    return ghast.getBbWidth() / 2.0 + SIDE_CLEARANCE;
  }
}

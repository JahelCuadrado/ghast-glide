package com.einsteinmods.ghastglide.anchor;

import com.einsteinmods.ghastglide.core.LeaveMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Anchor lifecycle, called from mixins on the server. An anchored ghast is held in vanilla's own
 * "stay still" state (the one used while a player stands on it): {@code GhastMoveControl} then
 * waits and calls {@code stopInPlace} every tick, the float goal is disabled and clients get the
 * synced STAYS_STILL flag. While idle it may still drift gently around where it was left ({@link
 * GhastIdleDrift}), moved directly like calls.
 */
public final class GhastAnchors {
  /** Vanilla {@code HappyGhast.MAX_STILL_TIMEOUT}; renewed every tick while anchored. */
  static final int STILL_TIMEOUT_TICKS = 10;

  private GhastAnchors() {}

  public static boolean isAnchored(HappyGhast ghast) {
    return ghast.hasAttached(GhastAttachments.ANCHORED);
  }

  /** True while a dismounted ghast moves out of blocks or water (server only). */
  public static boolean isGettingUnstuck(HappyGhast ghast) {
    return DismountUnstuck.isMoving(ghast);
  }

  /** A player started riding: the anchor is released and the ghast remembered for calls. */
  public static void onPassengerAdded(HappyGhast ghast, Entity passenger) {
    if (ghast.level().isClientSide()) {
      return;
    }
    release(ghast);
    if (passenger instanceof Player player) {
      player.setAttached(GhastAttachments.LAST_RIDDEN_GHAST, ghast.getUUID());
    }
  }

  /**
   * The last passenger left (any cause): stop dead and anchor in place with that player's leave
   * mode, then get unstuck once no player stands on its head. With {@link LeaveMode#VANILLA} it is
   * only anchored while stuck; otherwise vanilla's own dismount handling is left alone.
   */
  public static void onPassengerRemoved(HappyGhast ghast, Entity passenger) {
    if (ghast.level().isClientSide()
        || !(passenger instanceof Player player)
        || ghast.isVehicle()) {
      return;
    }
    LeaveMode leaveMode = GhastIdleDrift.leaveModeOf(player);
    if (leaveMode == LeaveMode.VANILLA && !DismountUnstuck.isStuck(ghast)) {
      return;
    }
    anchor(ghast, leaveMode);
    DismountUnstuck.markIfStuck(ghast);
  }

  /**
   * True while a player still rides {@code ghast} as its pilot (first passenger), so vanilla's
   * still timeout must not start: {@code HappyGhast#removePassenger} sets it whenever any passenger
   * leaves and {@code HappyGhast#tick} renews it while {@code scanPlayerAboveGhast} finds a player
   * on its head (a dismounted passenger lands there). During that timeout {@code
   * getControllingPassenger()} is null, so the pilot would lose steering and sneak would dismount
   * it.
   */
  public static boolean keepsPilotControl(HappyGhast ghast) {
    return ghast.getFirstPassenger() instanceof Player;
  }

  /**
   * Called instead of vanilla {@code HappyGhastLookControl#tick}, which snaps a still ghast's yaw
   * to the nearest cardinal direction every tick. Returns true when the ghast is anchored: its yaw
   * is then kept (head follows it), so a called ghast can turn and keep facing its caller.
   */
  public static boolean holdFacing(HappyGhast ghast) {
    if (!isAnchored(ghast)) {
      return false;
    }
    ghast.setYHeadRot(ghast.getYRot());
    return true;
  }

  /**
   * Called after vanilla {@code BodyRotationControl#clientTick} on both sides. Ghast models turn by
   * body yaw, which is never synced: clients derive it from the head yaw, and vanilla only lets the
   * body catch up once the head has been stable for 10-20 ticks, so a slow idle turn would show as
   * a late snap. A still ghast (anchored, or vanilla's still state, whose yaw vanilla keeps fixed)
   * has no other source of body yaw, so the body simply follows the head.
   */
  public static void keepBodyOnHeadWhileStill(HappyGhast ghast) {
    if (ghast.isOnStillTimeout() && !ghast.isVehicle()) {
      ghast.yBodyRot = ghast.getYHeadRot();
    }
  }

  public static void tick(HappyGhast ghast) {
    if (!isAnchored(ghast)) {
      return;
    }
    if (ghast.isVehicle() || ghast.isLeashed()) {
      release(ghast);
      return;
    }
    ((StillGhast) ghast).ghastglide$setStillTimeout(STILL_TIMEOUT_TICKS);
    GhastCalls.tick(ghast);
    DismountUnstuck.tick(ghast);
    if (GhastIdleDrift.isIdle(ghast) && leaveModeOf(ghast) == LeaveMode.VANILLA) {
      // Unstuck or arrived: a vanilla-mode anchor ends here (also one reloaded from a save).
      release(ghast);
      return;
    }
    GhastIdleDrift.tick(ghast);
  }

  /** Leave mode of an anchored ghast; anchors saved before it existed get the default. */
  private static LeaveMode leaveModeOf(HappyGhast ghast) {
    AnchorRest rest = ghast.getAttached(GhastAttachments.ANCHOR_REST);
    return rest == null ? LeaveMode.DEFAULT : rest.leaveMode();
  }

  /** Anchors where the ghast is now; {@code leaveMode} is the player's setting that applies. */
  static void anchor(HappyGhast ghast, LeaveMode leaveMode) {
    ghast.removeAttached(GhastAttachments.CALL);
    ghast.removeAttached(GhastAttachments.UNSTUCK_PENDING);
    ghast.removeAttached(GhastAttachments.UNSTUCK);
    ghast.setDeltaMovement(Vec3.ZERO);
    ghast.removeAttached(GhastAttachments.IDLE_DRIFT);
    ghast.setAttached(GhastAttachments.ANCHORED, Boolean.TRUE);
    ghast.setAttached(GhastAttachments.ANCHOR_REST, GhastIdleDrift.restHere(ghast, leaveMode));
    ((StillGhast) ghast).ghastglide$setStillTimeout(STILL_TIMEOUT_TICKS);
  }

  /** Vanilla's still timeout then runs out on its own, so the ghast resumes wandering. */
  static void release(HappyGhast ghast) {
    ghast.removeAttached(GhastAttachments.ANCHORED);
    ghast.removeAttached(GhastAttachments.CALL);
    ghast.removeAttached(GhastAttachments.UNSTUCK_PENDING);
    ghast.removeAttached(GhastAttachments.UNSTUCK);
    ghast.removeAttached(GhastAttachments.ANCHOR_REST);
    ghast.removeAttached(GhastAttachments.IDLE_DRIFT);
  }
}

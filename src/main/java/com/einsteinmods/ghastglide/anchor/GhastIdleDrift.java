package com.einsteinmods.ghastglide.anchor;

import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.core.LeaveMode;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Natural idle motion of an anchored ghast left alone (no call, not getting unstuck): it floats
 * still for a random pause, then either glides slowly to a random spot within {@link #MAX_RADIUS}
 * blocks of its {@link AnchorRest#origin} at the origin's height, or turns {@link
 * #MIN_TURN_DEGREES}..{@link #MAX_TURN_DEGREES} left or right, staying within {@link
 * #MAX_REST_DEVIATION_DEGREES} of its rest yaw. It never rises above the origin nor sinks below it.
 *
 * <p>A glide target is only taken when the box swept along the whole straight path is free of
 * collisions (blocks, collidable entities, world border) and liquids, so the ghast never enters
 * blocks; moves still use {@code Entity#move} with normal collisions, and a blocked glide ends.
 * With no free spot it only turns. Planning runs only when a pause ends; a pause costs one game
 * time comparison per tick.
 *
 * <p>Vanilla dismounts riders onto the ghast's head ({@code getDismountLocationForPassenger}), and
 * like vanilla the ghast never slides away under a player standing there: it then only turns, which
 * moves nobody (its box is square and axis-aligned).
 */
final class GhastIdleDrift {
  static final int MIN_PAUSE_TICKS = 60;
  static final int MAX_PAUSE_TICKS = 200;

  /** Farthest a glide target lies from the origin, horizontally. */
  static final double MAX_RADIUS = 4.0;

  /** Glides shorter than this (from where the ghast is) are not worth starting. */
  static final double MIN_GLIDE_DISTANCE = 1.0;

  /** Random spots tried per planning before settling for a turn. */
  static final int MAX_SPOT_ATTEMPTS = 8;

  /** Chance that a planned motion is a turn instead of a glide. */
  static final float TURN_CHANCE = 0.5F;

  /** Range of one turn, left or right of the current yaw. */
  static final float MIN_TURN_DEGREES = 20.0F;

  static final float MAX_TURN_DEGREES = 60.0F;

  /** Turns never take the ghast farther than this from the yaw it was left with, either way. */
  static final float MAX_REST_DEVIATION_DEGREES = 90.0F;

  static final float TURN_SNAP_DEGREES = 0.5F;

  /** Drift top speed as a fraction of the default glide top speed (≈0.6 of ≈6 blocks/s). */
  static final double TOP_SPEED_FRACTION = 0.1;

  static final double TOP_SPEED = GhastMotion.HORIZONTAL_TOP_SPEED * TOP_SPEED_FRACTION;

  /** Generous bounds for a glide (≈8 blocks at most) and a turn; then the ghast pauses. */
  static final int GLIDE_TIMEOUT_TICKS = 600;

  static final int TURN_TIMEOUT_TICKS = 200;

  /**
   * A ghast found farther than this from its origin (teleported, pushed) is re-rooted where it is
   * instead of gliding back from afar.
   */
  static final double REROOT_HORIZONTAL_DISTANCE = MAX_RADIUS + 2.0;

  static final double REROOT_VERTICAL_DISTANCE = 1.0;

  private GhastIdleDrift() {}

  /** Leave mode of {@code player}; players without the mod get the default. */
  static LeaveMode leaveModeOf(Player player) {
    GlideSettings settings = ServerGlideSessions.get(player);
    return (settings == null ? GlideSettings.DEFAULTS : settings).leaveMode();
  }

  /** Called every tick for an anchored ghast, after calls and unstuck moves. */
  static void tick(HappyGhast ghast) {
    if (!isIdle(ghast)) {
      ghast.removeAttached(GhastAttachments.IDLE_DRIFT);
      return;
    }
    AnchorRest rest = ghast.getAttached(GhastAttachments.ANCHOR_REST);
    if (rest == null) {
      rest = restHere(ghast, LeaveMode.DEFAULT);
      ghast.setAttached(GhastAttachments.ANCHOR_REST, rest);
    }
    if (rest.leaveMode() != LeaveMode.NATURAL) {
      return;
    }
    long now = ghast.level().getGameTime();
    IdleDriftPhase phase = ghast.getAttached(GhastAttachments.IDLE_DRIFT);
    switch (phase) {
      case null -> pause(ghast, now);
      case IdleDriftPhase.Pause pause -> {
        if (now >= pause.endTick()) {
          plan(ghast, rest, now);
        }
      }
      case IdleDriftPhase.Glide glide -> glide(ghast, glide, now);
      case IdleDriftPhase.Turn turn -> turn(ghast, turn, now);
    }
  }

  /** Rest data for a ghast anchored where it stands now. */
  static AnchorRest restHere(HappyGhast ghast, LeaveMode leaveMode) {
    return new AnchorRest(ghast.position(), ghast.getYRot(), leaveMode);
  }

  /** After an unstuck move the ghast rests where it ended up; the leave mode is kept. */
  static void rerootHere(HappyGhast ghast) {
    AnchorRest rest = ghast.getAttached(GhastAttachments.ANCHOR_REST);
    if (rest != null) {
      ghast.setAttached(GhastAttachments.ANCHOR_REST, rest.withOrigin(ghast.position()));
    }
  }

  /** Anchored ghast neither called nor getting unstuck. */
  static boolean isIdle(HappyGhast ghast) {
    return !ghast.hasAttached(GhastAttachments.CALL)
        && !ghast.hasAttached(GhastAttachments.UNSTUCK_PENDING)
        && !ghast.hasAttached(GhastAttachments.UNSTUCK);
  }

  private static void pause(HappyGhast ghast, long now) {
    int ticks = Mth.randomBetweenInclusive(ghast.getRandom(), MIN_PAUSE_TICKS, MAX_PAUSE_TICKS);
    ghast.setAttached(GhastAttachments.IDLE_DRIFT, new IdleDriftPhase.Pause(now + ticks));
  }

  private static void plan(HappyGhast ghast, AnchorRest rest, long now) {
    Vec3 offOrigin = ghast.position().subtract(rest.origin());
    if (offOrigin.horizontalDistance() > REROOT_HORIZONTAL_DISTANCE
        || Math.abs(offOrigin.y) > REROOT_VERTICAL_DISTANCE) {
      rest = rest.withOrigin(ghast.position());
      ghast.setAttached(GhastAttachments.ANCHOR_REST, rest);
    }
    RandomSource random = ghast.getRandom();
    @Nullable Vec3 target =
        random.nextFloat() < TURN_CHANCE || DismountUnstuck.hasPlayerOnHead(ghast)
            ? null
            : freeSpot(ghast, rest.origin());
    IdleDriftPhase next =
        target == null
            ? new IdleDriftPhase.Turn(turnTarget(ghast, rest), now + TURN_TIMEOUT_TICKS)
            : new IdleDriftPhase.Glide(target, GhastMotion.AT_REST, now + GLIDE_TIMEOUT_TICKS);
    ghast.setAttached(GhastAttachments.IDLE_DRIFT, next);
  }

  /**
   * A random turn left or right of the current yaw; turned the other way when it would end beyond
   * {@link #MAX_REST_DEVIATION_DEGREES} from the rest yaw (the other way then stays within).
   */
  private static float turnTarget(HappyGhast ghast, AnchorRest rest) {
    RandomSource random = ghast.getRandom();
    float turn = Mth.randomBetween(random, MIN_TURN_DEGREES, MAX_TURN_DEGREES);
    if (random.nextBoolean()) {
      turn = -turn;
    }
    float yaw = ghast.getYRot();
    if (Math.abs(Mth.wrapDegrees(yaw + turn - rest.restYaw())) > MAX_REST_DEVIATION_DEGREES) {
      turn = -turn;
    }
    return yaw + turn;
  }

  /**
   * A random spot within {@link #MAX_RADIUS} of {@code origin} at its height whose straight path
   * from the ghast is free of collisions and liquids, or null when none of the attempts is.
   */
  private static @Nullable Vec3 freeSpot(HappyGhast ghast, Vec3 origin) {
    Level level = ghast.level();
    RandomSource random = ghast.getRandom();
    AABB box = ghast.getBoundingBox();
    for (int attempt = 0; attempt < MAX_SPOT_ATTEMPTS; attempt++) {
      double angle = random.nextDouble() * Mth.TWO_PI;
      double distance = random.nextDouble() * MAX_RADIUS;
      Vec3 target = origin.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
      Vec3 offset = target.subtract(ghast.position());
      if (offset.length() < MIN_GLIDE_DISTANCE) {
        continue;
      }
      AABB path = box.minmax(box.move(offset));
      if (level.noCollision(ghast, path) && !level.containsAnyLiquid(path)) {
        return target;
      }
    }
    return null;
  }

  private static void glide(HappyGhast ghast, IdleDriftPhase.Glide glide, long now) {
    Vec3 offset = glide.target().subtract(ghast.position());
    if (now >= glide.endTick()
        || offset.length() <= GhastMotion.ARRIVAL_TOLERANCE
        || DismountUnstuck.hasPlayerOnHead(ghast)) {
      pause(ghast, now);
      return;
    }
    double speed =
        GhastMotion.moveToward(ghast, offset, glide.speed(), GhastMotion.FULL_THRUST, TOP_SPEED);
    if (speed <= GhastMotion.AT_REST) {
      // Something appeared in the way since planning: give up this glide.
      pause(ghast, now);
      return;
    }
    ghast.setAttached(GhastAttachments.IDLE_DRIFT, glide.advanced(speed));
  }

  private static void turn(HappyGhast ghast, IdleDriftPhase.Turn turn, long now) {
    if (now >= turn.endTick()
        || GhastMotion.turnToward(ghast, turn.targetYaw(), TURN_SNAP_DEGREES) == 0.0F) {
      pause(ghast, now);
    }
  }
}

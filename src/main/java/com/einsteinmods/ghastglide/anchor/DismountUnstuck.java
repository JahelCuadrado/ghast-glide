package com.einsteinmods.ghastglide.anchor;

import com.einsteinmods.ghastglide.glide.PhasingGhast;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Freeing a ghast left inside blocks or water after a dismount. Vanilla dismounts riders whose eyes
 * are underwater, and a noclip ghast can be left sunk in terrain; the anchored ghast would stay
 * there. If its box overlaps collision blocks or water, it waits until no player stands on its head
 * (vanilla's own "player above" box, so a player who dismounted onto it is not thrown up; this
 * applies to water too, though an underwater rider is normally not left on top), then:
 *
 * <ol>
 *   <li>It rises straight up to the lowest spot where its box is free of both and its tentacles,
 *       which hang below the box, are free of blocks, when no block lying entirely above its box is
 *       in the way (it only passes through blocks it already overlaps). If only the tentacle
 *       clearance meets such a block, it rises just until its box is free.
 *   <li>Otherwise (a ceiling: cave, tunnel) it moves to the spot of its hollow closest to the
 *       midpoint between the walls, or to the least-overlapping one when none is free.
 * </ol>
 *
 * The move flies at default glide speeds ({@link GhastMotion}) through blocks (noclip collision
 * hook; the phasing ghast is immune to suffocation) while the anchor keeps vanilla AI still. The
 * search is bounded and runs only at dismount, when the wait ends and on arrival; while waiting,
 * only the small box above the head is scanned for players. The move is not saved: after a reload
 * the ghast stays anchored wherever it was.
 */
final class DismountUnstuck {
  /** 30 seconds: enough for {@link #MAX_RISE_LAYERS}; then it stays where it is. */
  static final int UNSTUCK_TIMEOUT_TICKS = 600;

  /** Deeper water is left in several rises: the target is recomputed on arrival. */
  static final int MAX_RISE_LAYERS = 64;

  /** Blocks scanned on each side of the ghast's centre to find the hollow's walls. */
  static final int HOLLOW_EXTENT_RADIUS = 8;

  /** Whole-block horizontal offsets from the hollow's midpoint tried as destinations. */
  static final int CANDIDATE_HORIZONTAL_RADIUS = 3;

  /** Whole-block vertical offsets from the ghast's height tried as destinations. */
  static final int CANDIDATE_VERTICAL_RADIUS = 2;

  /**
   * How far the rendered tentacles reach below the box, per block of box height. Adult {@code
   * HappyGhastModel} (scaled x4, box 4 high): the longest tentacle is 8 model pixels (2 blocks)
   * long, hangs from a pivot 0.254 blocks above the box bottom and swings 0.2..0.6 rad off vertical
   * ({@code GhastModel#animateTentacles}); its lowest tip, at 0.2 rad, is 1.755 blocks below the
   * box bottom (1.755 / 4 = 0.439, rounded up). Baby and scaled ghasts scale model and box alike.
   */
  private static final double TENTACLE_REACH_PER_BOX_HEIGHT = 0.44;

  /**
   * Vanilla {@code HappyGhast#scanPlayerAboveGhast} box: the ghast's box widened by this on each
   * horizontal side, from {@link #PLAYER_ABOVE_BOTTOM_EPSILON} below its top up to half its height
   * above it.
   */
  private static final double PLAYER_ABOVE_HORIZONTAL_MARGIN = 1.0;

  private static final double PLAYER_ABOVE_BOTTOM_EPSILON = 1.0E-5F;

  private static final double PLAYER_ABOVE_HEIGHT_PER_BOX_HEIGHT = 0.5;

  /** Below this squared horizontal offset a move is vertical and uses the vertical top speed. */
  private static final double MIN_HORIZONTAL_LENGTH_SQUARED = 1.0E-6;

  /** Blocks starting this close below the box top already overlap it, so they are no ceiling. */
  private static final double CEILING_EPSILON = 1.0E-7;

  /** Fewest overlapped blocks, then fewest water blocks, then closest to the hollow's midpoint. */
  private static final Comparator<HollowSpot> BEST_SPOT_FIRST =
      Comparator.comparingInt(HollowSpot::blockOverlap)
          .thenComparingInt(HollowSpot::waterOverlap)
          .thenComparingInt(HollowSpot::horizontalOffsetSquared)
          .thenComparingInt(HollowSpot::verticalOffset);

  private DismountUnstuck() {}

  /** Marks the (already anchored) ghast to move once free of players if it is stuck. */
  static void markIfStuck(HappyGhast ghast) {
    if (destination(ghast) != null) {
      // Immune while it waits, before the noclip move itself marks it as phasing.
      ((PhasingGhast) ghast).ghastglide$setPhasing(true);
      ghast.setAttached(GhastAttachments.UNSTUCK_PENDING, Boolean.TRUE);
    }
  }

  /** True when the ghast's box overlaps collision blocks or water it would be moved out of. */
  static boolean isStuck(HappyGhast ghast) {
    return destination(ghast) != null;
  }

  static boolean isMoving(HappyGhast ghast) {
    return ghast.hasAttached(GhastAttachments.UNSTUCK);
  }

  static void tick(HappyGhast ghast) {
    if (ghast.hasAttached(GhastAttachments.UNSTUCK_PENDING)) {
      if (hasPlayerOnHead(ghast)) {
        return;
      }
      ghast.removeAttached(GhastAttachments.UNSTUCK_PENDING);
      start(ghast);
    }
    UnstuckMove move = ghast.getAttached(GhastAttachments.UNSTUCK);
    if (move == null) {
      return;
    }
    if (move.remainingTicks() <= 0) {
      finish(ghast);
      return;
    }
    Vec3 target = move.target();
    if (target.distanceTo(ghast.position()) <= GhastMotion.ARRIVAL_TOLERANCE) {
      target = destination(ghast);
      if (target == null) {
        finish(ghast);
        return;
      }
    }
    Vec3 offset = target.subtract(ghast.position());
    double topSpeed =
        offset.horizontalDistanceSqr() < MIN_HORIZONTAL_LENGTH_SQUARED
            ? GhastMotion.VERTICAL_TOP_SPEED
            : GhastMotion.HORIZONTAL_TOP_SPEED;
    double speed =
        GhastMotion.moveToward(ghast, offset, move.speed(), GhastMotion.FULL_THRUST, topSpeed);
    ghast.setAttached(GhastAttachments.UNSTUCK, move.advanced(target, speed));
  }

  /** The ghast now rests where the move left it, so idle drift stays around there. */
  private static void finish(HappyGhast ghast) {
    ghast.removeAttached(GhastAttachments.UNSTUCK);
    GhastIdleDrift.rerootHere(ghast);
  }

  private static void start(HappyGhast ghast) {
    Vec3 target = destination(ghast);
    if (target != null) {
      ghast.setAttached(
          GhastAttachments.UNSTUCK,
          new UnstuckMove(target, UNSTUCK_TIMEOUT_TICKS, GhastMotion.AT_REST));
    }
  }

  /**
   * Same result as vanilla {@code HappyGhast#scanPlayerAboveGhast} (private): a non-spectator
   * player whose root vehicle is not a Happy Ghast and stands inside the box above the head.
   * Vanilla iterates {@code level.players()}; this searches entities in that box instead, which
   * finds the same players (they are level entities, and an entity's position lies inside its own
   * box) and also players that are not in the player list, such as GameTest mock players.
   *
   * <p>Runs every tick for a ghast waiting to get unstuck or gliding idly. The untyped search is
   * kept on purpose: {@code Level#hasEntities} with an {@code EntityTypeTest} measured more bytes
   * per call (240 vs 168) when nobody is there, the common case.
   */
  static boolean hasPlayerOnHead(HappyGhast ghast) {
    AABB box = ghast.getBoundingBox();
    AABB above =
        new AABB(
            box.minX - PLAYER_ABOVE_HORIZONTAL_MARGIN,
            box.maxY - PLAYER_ABOVE_BOTTOM_EPSILON,
            box.minZ - PLAYER_ABOVE_HORIZONTAL_MARGIN,
            box.maxX + PLAYER_ABOVE_HORIZONTAL_MARGIN,
            box.maxY + box.getYsize() * PLAYER_ABOVE_HEIGHT_PER_BOX_HEIGHT,
            box.maxZ + PLAYER_ABOVE_HORIZONTAL_MARGIN);
    return !ghast
        .level()
        .getEntities(ghast, above, root -> carriesPlayerAbove(root, above))
        .isEmpty();
  }

  /** {@code root} is the root vehicle of a non-spectator player (or that player) and is inside. */
  private static boolean carriesPlayerAbove(Entity root, AABB above) {
    return !root.isPassenger()
        && !(root instanceof HappyGhast)
        && above.contains(root.position())
        && isOrCarriesNonSpectatorPlayer(root);
  }

  /** As {@code getSelfAndPassengers().anyMatch(...)}, without building streams. */
  private static boolean isOrCarriesNonSpectatorPlayer(Entity entity) {
    if (entity instanceof Player player && !player.isSpectator()) {
      return true;
    }
    List<Entity> passengers = entity.getPassengers();
    for (int index = 0; index < passengers.size(); index++) {
      if (isOrCarriesNonSpectatorPlayer(passengers.get(index))) {
        return true;
      }
    }
    return false;
  }

  /** Where the ghast should go, or null when it is not stuck or already at the best spot. */
  private static @Nullable Vec3 destination(HappyGhast ghast) {
    Level level = ghast.level();
    AABB box = ghast.getBoundingBox();
    if (level.noBlockCollision(ghast, box) && waterTop(level, box) <= box.minY) {
      return null;
    }
    double tentacleReach = box.getYsize() * TENTACLE_REACH_PER_BOX_HEIGHT;
    OptionalDouble riseMinY = clearMinY(ghast, box, tentacleReach);
    if (riseMinY.isEmpty()) {
      riseMinY = clearMinY(ghast, box, 0.0);
    }
    Vec3 target =
        riseMinY.isPresent()
            ? ghast.position().add(0.0, riseMinY.getAsDouble() - box.minY, 0.0)
            : hollowSpot(ghast, box);
    return target.distanceTo(ghast.position()) > GhastMotion.ARRIVAL_TOLERANCE ? target : null;
  }

  /**
   * Lowest box bottom, at or above {@code box.minY}, where the box overlaps no collision block, no
   * collision block lies within {@code tentacleReach} below it and no water surface lies above its
   * bottom; empty when a block lying entirely above {@code box} (a ceiling) is in the way. Past
   * {@link #MAX_RISE_LAYERS} it returns the highest bottom checked.
   */
  private static OptionalDouble clearMinY(HappyGhast ghast, AABB box, double tentacleReach) {
    Level level = ghast.level();
    double ceilingFrom = box.maxY - CEILING_EPSILON;
    double limit = box.minY + MAX_RISE_LAYERS;
    AABB candidate = box;
    while (true) {
      double top = waterTop(level, candidate);
      AABB withTentacles = candidate.expandTowards(0.0, -tentacleReach, 0.0);
      for (VoxelShape shape : level.getBlockCollisions(ghast, withTentacles)) {
        if (shape.isEmpty()) {
          continue;
        }
        if (shape.min(Direction.Axis.Y) >= ceilingFrom) {
          return OptionalDouble.empty();
        }
        top = Math.max(top, shape.max(Direction.Axis.Y) + tentacleReach);
      }
      if (top <= candidate.minY || top >= limit) {
        return OptionalDouble.of(candidate.minY);
      }
      candidate = candidate.move(0.0, top - candidate.minY, 0.0);
    }
  }

  /**
   * Highest water surface inside {@code box}, or {@code box.minY} when none lies above its bottom
   * (water whose surface is at or below the bottom does not count).
   */
  private static double waterTop(Level level, AABB box) {
    double top = box.minY;
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    for (int y = Mth.floor(box.minY); y < box.maxY; y++) {
      for (int x = Mth.floor(box.minX); x < box.maxX; x++) {
        for (int z = Mth.floor(box.minZ); z < box.maxZ; z++) {
          FluidState fluid = level.getFluidState(pos.set(x, y, z));
          if (fluid.is(FluidTags.WATER)) {
            top = Math.max(top, y + fluid.getHeight(level, pos));
          }
        }
      }
    }
    return top;
  }

  /** Best of the whole-block offsets around the midpoint between the hollow's walls. */
  private static Vec3 hollowSpot(HappyGhast ghast, AABB box) {
    Vec3 centre = box.getCenter();
    int layer = Mth.floor(centre.y);
    double midX = hollowMidpoint(ghast, Direction.Axis.X, centre, layer, box.getXsize() / 2.0);
    double midZ = hollowMidpoint(ghast, Direction.Axis.Z, centre, layer, box.getZsize() / 2.0);
    @Nullable HollowSpot best = null;
    for (int dx = -CANDIDATE_HORIZONTAL_RADIUS; dx <= CANDIDATE_HORIZONTAL_RADIUS; dx++) {
      for (int dz = -CANDIDATE_HORIZONTAL_RADIUS; dz <= CANDIDATE_HORIZONTAL_RADIUS; dz++) {
        for (int dy = -CANDIDATE_VERTICAL_RADIUS; dy <= CANDIDATE_VERTICAL_RADIUS; dy++) {
          Vec3 position = new Vec3(midX + dx, ghast.getY() + dy, midZ + dz);
          AABB moved = box.move(position.subtract(ghast.position()));
          HollowSpot spot =
              new HollowSpot(
                  position,
                  blockOverlap(ghast, moved),
                  waterOverlap(ghast.level(), moved),
                  dx * dx + dz * dz,
                  Math.abs(dy));
          if (best == null || BEST_SPOT_FIRST.compare(spot, best) < 0) {
            best = spot;
          }
        }
      }
    }
    return best == null ? ghast.position() : best.position();
  }

  /**
   * Midpoint of the open run of blocks along {@code axis} through the ghast's centre at {@code
   * layer}, starting from the nearest open block when the centre is inside a wall. When a side has
   * no wall within {@link #HOLLOW_EXTENT_RADIUS}, the box is only shifted clear of the other wall.
   */
  private static double hollowMidpoint(
      HappyGhast ghast, Direction.Axis axis, Vec3 centre, int layer, double halfSize) {
    BlockPos origin = BlockPos.containing(centre.x, layer, centre.z);
    OptionalInt start = nearestOpen(ghast, origin, axis);
    double coordinate = centre.get(axis);
    if (start.isEmpty()) {
      return coordinate;
    }
    int low = start.getAsInt();
    while (low > -HOLLOW_EXTENT_RADIUS && isOpen(ghast, origin.relative(axis, low - 1))) {
      low--;
    }
    int high = start.getAsInt();
    while (high < HOLLOW_EXTENT_RADIUS && isOpen(ghast, origin.relative(axis, high + 1))) {
      high++;
    }
    boolean lowWall = low > -HOLLOW_EXTENT_RADIUS;
    boolean highWall = high < HOLLOW_EXTENT_RADIUS;
    double lowEdge = origin.get(axis) + low;
    double highEdge = origin.get(axis) + high + 1.0;
    if (lowWall && highWall) {
      return (lowEdge + highEdge) / 2.0;
    }
    if (lowWall) {
      coordinate = Math.max(coordinate, lowEdge + halfSize);
    }
    if (highWall) {
      coordinate = Math.min(coordinate, highEdge - halfSize);
    }
    return coordinate;
  }

  /** Offset of the open block closest to {@code origin} along {@code axis}, if any in range. */
  private static OptionalInt nearestOpen(HappyGhast ghast, BlockPos origin, Direction.Axis axis) {
    for (int distance = 0; distance <= HOLLOW_EXTENT_RADIUS; distance++) {
      if (isOpen(ghast, origin.relative(axis, -distance))) {
        return OptionalInt.of(-distance);
      }
      if (isOpen(ghast, origin.relative(axis, distance))) {
        return OptionalInt.of(distance);
      }
    }
    return OptionalInt.empty();
  }

  private static boolean isOpen(HappyGhast ghast, BlockPos pos) {
    Level level = ghast.level();
    return level
        .getBlockState(pos)
        .getCollisionShape(level, pos, CollisionContext.of(ghast))
        .isEmpty();
  }

  private static int blockOverlap(HappyGhast ghast, AABB box) {
    int count = 0;
    for (VoxelShape shape : ghast.level().getBlockCollisions(ghast, box)) {
      if (!shape.isEmpty()) {
        count++;
      }
    }
    return count;
  }

  /** Water blocks inside {@code box} whose surface lies above its bottom. */
  private static int waterOverlap(Level level, AABB box) {
    int count = 0;
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    for (int y = Mth.floor(box.minY); y < box.maxY; y++) {
      for (int x = Mth.floor(box.minX); x < box.maxX; x++) {
        for (int z = Mth.floor(box.minZ); z < box.maxZ; z++) {
          FluidState fluid = level.getFluidState(pos.set(x, y, z));
          if (fluid.is(FluidTags.WATER) && y + fluid.getHeight(level, pos) > box.minY) {
            count++;
          }
        }
      }
    }
    return count;
  }

  /** A candidate destination in the hollow and how well it fits. */
  private record HollowSpot(
      Vec3 position,
      int blockOverlap,
      int waterOverlap,
      int horizontalOffsetSquared,
      int verticalOffset) {}
}

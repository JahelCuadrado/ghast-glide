package com.einsteinmods.ghastglide.glide;

import com.einsteinmods.ghastglide.core.CreativeFlightModel;
import com.einsteinmods.ghastglide.core.FlightInput;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.core.Motion;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Movement adapters called from mixins; keep mixins thin. */
public final class GlideMovement {
  private GlideMovement() {}

  /** Replaces HappyGhast#travel with creative flight. Runs only on the controlling client. */
  public static void travel(HappyGhast ghast, Player rider, GlideSettings settings) {
    Vec3 current = ghast.getDeltaMovement();
    Motion currentMotion = new Motion(current.x, current.y, current.z);
    FlightInput input = GlideRules.input(rider);
    Motion accelerated =
        GlideRules.pitchSteering(rider)
            ? CreativeFlightModel.acceleratePitchSteered(currentMotion, input, settings)
            : CreativeFlightModel.accelerate(currentMotion, input, settings);
    ghast.setDeltaMovement(accelerated.x(), accelerated.y(), accelerated.z());
    ghast.move(MoverType.SELF, ghast.getDeltaMovement());
    // Entity#move zeroes blocked axes in delta movement; drag applies to what is left.
    Vec3 afterMove = ghast.getDeltaMovement();
    Motion next =
        CreativeFlightModel.applyDrag(new Motion(afterMove.x, afterMove.y, afterMove.z), settings);
    ghast.setDeltaMovement(next.x(), next.y(), next.z());
  }

  /**
   * Noclip collision: the ghast's own box ignores blocks, but every passenger's box still collides,
   * so riders never end up inside blocks. Passenger boxes are rebuilt at their expected riding
   * position (same as {@code Entity#positionRider}) so client and server replay give equal results.
   *
   * <p>Vanilla collision ({@code Entity#collideWithShapes}) ignores blocks a box already overlaps,
   * so a rider that is inside blocks would sink deeper with every push; it may only move along axes
   * that do not take it deeper.
   */
  public static Vec3 collideWithPassengersOnly(HappyGhast ghast, Vec3 movement) {
    Vec3 allowed = movement;
    for (Entity passenger : ghast.getPassengers()) {
      if (allowed.lengthSqr() == 0.0) {
        break;
      }
      AABB box = expectedRiderBox(ghast, passenger);
      allowed = Entity.collideBoundingBox(passenger, allowed, box, ghast.level(), List.of());
      allowed = withoutAxesGoingDeeper(passenger, box, allowed);
    }
    ((PhasingGhast) ghast).ghastglide$setPhasing(true);
    return allowed;
  }

  /**
   * Called after {@code HappyGhast#tickRidden} turned the ghast. The seat sits in front of the
   * ghast and rotates with its yaw without any collision check, so a turn that would sweep a rider
   * into blocks is undone. A rider that is already inside blocks may still turn, but only if that
   * does not take it deeper (so it can turn to get out, never further in).
   *
   * <p>Contact is not "inside": translation lets a box rest up to {@link Shapes#EPSILON} inside a
   * block face, while {@code Level#noCollision} counts any overlap of a full block. Treating that
   * contact as "already inside" let turns sweep riders into the blocks they were touching.
   *
   * <p>Runs every tick for the ridden ghast; a tick without a turn skips the block queries.
   */
  public static void keepRidersClearAfterTurn(HappyGhast ghast, float yawBeforeTurn) {
    // No turn, nothing to undo: tickRidden already wrote this yaw to every field applyYaw sets.
    if (ghast.getYRot() == yawBeforeTurn || !GlideRules.isNoclipActive(ghast)) {
      return;
    }
    double turnedOverlap = ridersOverlap(ghast);
    if (turnedOverlap == 0.0) {
      return;
    }
    float turnedYaw = ghast.getYRot();
    applyYaw(ghast, yawBeforeTurn);
    if (turnedOverlap <= ridersOverlap(ghast)) {
      applyYaw(ghast, turnedYaw);
    }
  }

  /** Keeps, in vanilla axis order, only the axis steps that do not increase the rider's overlap. */
  private static Vec3 withoutAxesGoingDeeper(Entity passenger, AABB box, Vec3 movement) {
    double overlap = overlapVolume(passenger, box);
    if (overlap == 0.0) {
      return movement;
    }
    Vec3 kept = Vec3.ZERO;
    for (Direction.Axis axis : Direction.axisStepOrder(movement)) {
      double step = movement.get(axis);
      if (step == 0.0) {
        continue;
      }
      Vec3 candidate = kept.with(axis, step);
      double candidateOverlap = overlapVolume(passenger, box.move(candidate));
      if (candidateOverlap <= overlap) {
        kept = candidate;
        overlap = candidateOverlap;
      }
    }
    return kept;
  }

  private static double ridersOverlap(HappyGhast ghast) {
    double overlap = 0.0;
    for (Entity passenger : ghast.getPassengers()) {
      overlap += overlapVolume(passenger, expectedRiderBox(ghast, passenger));
    }
    return overlap;
  }

  /**
   * Volume of {@code box} inside block collision shapes, ignoring the {@link Shapes#EPSILON}
   * contact tolerance of vanilla collision on every axis: zero when the box is clear or only
   * touching. Unlike a depth, it does not grow while a box slides from one solid block into the
   * next, so heading toward open space through solid blocks never counts as going deeper.
   */
  private static double overlapVolume(Entity passenger, AABB box) {
    double volume = 0.0;
    for (VoxelShape shape : passenger.level().getBlockCollisions(passenger, box)) {
      for (AABB part : shape.toAabbs()) {
        volume +=
            overlapBeyondContact(box.minX, box.maxX, part.minX, part.maxX)
                * overlapBeyondContact(box.minY, box.maxY, part.minY, part.maxY)
                * overlapBeyondContact(box.minZ, box.maxZ, part.minZ, part.maxZ);
      }
    }
    return volume;
  }

  private static double overlapBeyondContact(
      double min, double max, double otherMin, double otherMax) {
    return Math.max(0.0, Math.min(max, otherMax) - Math.max(min, otherMin) - Shapes.EPSILON);
  }

  /** Same fields {@code HappyGhast#tickRidden} writes. */
  private static void applyYaw(HappyGhast ghast, float yaw) {
    ghast.setYRot(yaw);
    ghast.yRotO = yaw;
    ghast.setYBodyRot(yaw);
    ghast.setYHeadRot(yaw);
  }

  private static AABB expectedRiderBox(HappyGhast ghast, Entity passenger) {
    Vec3 ridingPosition =
        ghast
            .getPassengerRidingPosition(passenger)
            .subtract(passenger.getVehicleAttachmentPoint(ghast));
    return passenger.getDimensions(passenger.getPose()).makeBoundingBox(ridingPosition);
  }
}

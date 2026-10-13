package com.einsteinmods.ghastglide.gametest;

import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import java.util.Locale;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The rider seat sits 1.7 blocks in front of the ghast centre and rotates with the ghast's yaw, so
 * a noclip ghast that turns must never sweep its rider into blocks. Ticks are driven with {@link
 * HappyGhast#aiStep()}, which runs {@code HappyGhast#tickRidden} (the yaw lerp toward the rider).
 */
public final class RiderClearanceGameTest {
  // Ghast (4x4x4) at x=4, z=2 spans x 2..6, z 0..4, y 1..5. Rider feet at y 4.4 (head 6.2).
  private static final Vec3 GHAST_POSITION = new Vec3(4.0, 1.0, 2.0);
  private static final float FACING_SOUTH = 0.0F;
  private static final float FACING_WEST = 90.0F;
  private static final float FACING_EAST = -90.0F;
  private static final float TURN_TOLERANCE_DEGREES = 5.0F;
  private static final int TURN_TICKS = 60;

  // Pillar west of the ghast, level with the rider: seat is clear facing south, blocked facing
  // west.
  private static final int PILLAR_X = 2;
  private static final int PILLAR_MIN_Y = 4;
  private static final int PILLAR_MAX_Y = 6;
  private static final int PILLAR_MIN_Z = 1;
  private static final int PILLAR_MAX_Z = 3;

  // Staircase rising toward east (+x): step k is a column at x = 5 + k with top at y = 4 + k.
  private static final int STAIR_FIRST_X = 5;
  private static final int STAIR_COUNT = 3;
  private static final int STAIR_FIRST_TOP_Y = 3;
  private static final int STAIR_MIN_Z = 1;
  private static final int STAIR_MAX_Z = 7;
  private static final Vec3 CLIMB_STEP = new Vec3(0.25, 0.25, 0.0);
  private static final int CLIMB_TICKS = 24;

  // Stepped channel for the strafe test: the ghast faces east toward a wall (x = 6); its rider box
  // (x 3.4..4.0, z 2.2..2.8) sits between two steps (z = 1 and z = 3) of different heights.
  private static final Vec3 STRAFE_GHAST_POSITION = new Vec3(2.0, 1.0, 2.5);
  private static final int STRAFE_WALL_X = 6;
  private static final int STEP_MIN_X = 2;
  private static final int LOW_STEP_Z = 1;
  private static final int LOW_STEP_TOP_Y = 4;
  private static final int HIGH_STEP_Z = 3;
  private static final int HIGH_STEP_TOP_Y = 5;
  private static final int STRUCTURE_TOP_Y = 7;
  private static final long STRAFE_SEED = 7L;
  private static final int STRAFE_CYCLES = 150;
  private static final int STRAFE_TICKS_PER_CYCLE = 8;
  private static final double YAW_JITTER_DEGREES = 25.0;
  private static final double STRAFE_SPEED = 0.3;
  private static final double FORWARD_PUSH = 0.15;
  private static final double VERTICAL_PUSH = 0.1;
  private static final double CONTACT_RESIDUE = Shapes.EPSILON / 2.0;

  // Facing south, the rider box spans x 3.7..4.3, y 4.4..6.2, z 3.4..4.0: this block overlaps its
  // east half; pushing east would take the rider deeper.
  private static final int EMBEDDING_BLOCK_X = 4;
  private static final int EMBEDDING_BLOCK_Y = 5;
  private static final int EMBEDDING_BLOCK_Z = 3;
  private static final Vec3 DEEPER_PUSH = new Vec3(0.1, 0.0, 0.0);
  private static final int PUSH_TICKS = 5;

  @GameTest(maxTicks = 20)
  public void turningNeverSweepsRiderIntoBlocks(GameTestHelper helper) {
    Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
    HappyGhast ghast = mountedGhast(helper, rider);
    for (int y = PILLAR_MIN_Y; y <= PILLAR_MAX_Y; y++) {
      for (int z = PILLAR_MIN_Z; z <= PILLAR_MAX_Z; z++) {
        helper.setBlock(PILLAR_X, y, z, Blocks.STONE);
      }
    }
    assertRiderClear(helper, ghast, rider, "before turning");

    rider.setYRot(FACING_WEST);
    for (int tick = 0; tick < TURN_TICKS; tick++) {
      ghast.aiStep();
      assertRiderClear(helper, ghast, rider, "turn tick " + tick + ", yaw " + ghast.getYRot());
    }
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void turningInOpenAirStillFollowsRider(GameTestHelper helper) {
    Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
    HappyGhast ghast = mountedGhast(helper, rider);

    rider.setYRot(FACING_WEST);
    for (int tick = 0; tick < TURN_TICKS; tick++) {
      ghast.aiStep();
    }

    helper.assertTrue(
        Math.abs(ghast.getYRot() - FACING_WEST) < TURN_TOLERANCE_DEGREES,
        "ghast should turn toward the rider, yaw " + ghast.getYRot());
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void climbingStairsWhileFacingThemKeepsRiderClear(GameTestHelper helper) {
    Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
    HappyGhast ghast = mountedGhast(helper, rider);
    for (int step = 0; step < STAIR_COUNT; step++) {
      for (int y = 1; y <= STAIR_FIRST_TOP_Y + step; y++) {
        for (int z = STAIR_MIN_Z; z <= STAIR_MAX_Z; z++) {
          helper.setBlock(STAIR_FIRST_X + step, y, z, Blocks.STONE);
        }
      }
    }
    assertRiderClear(helper, ghast, rider, "before climbing");

    rider.setYRot(FACING_EAST);
    for (int tick = 0; tick < CLIMB_TICKS; tick++) {
      ghast.aiStep();
      ghast.move(MoverType.PLAYER, CLIMB_STEP);
      assertRiderClear(helper, ghast, rider, "climb tick " + tick + ", yaw " + ghast.getYRot());
    }
    helper.succeed();
  }

  /**
   * Vanilla translation lets a box come to rest up to {@link Shapes#EPSILON} inside a block face
   * (rebuilding the rider box from the ghast position leaves such a residue). From that contact,
   * strafing left and right in a stepped area while the yaw wobbles must never push the rider in.
   */
  @GameTest(maxTicks = 20)
  public void strafingFromContactInSteppedAreaNeverPushesRiderIntoBlocks(GameTestHelper helper) {
    Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
    HappyGhast ghast = mountedGhast(helper, rider, STRAFE_GHAST_POSITION, FACING_EAST);
    for (int y = 1; y <= STRUCTURE_TOP_Y; y++) {
      for (int z = 0; z <= STRUCTURE_TOP_Y; z++) {
        helper.setBlock(STRAFE_WALL_X, y, z, Blocks.STONE);
      }
    }
    for (int x = STEP_MIN_X; x < STRAFE_WALL_X; x++) {
      for (int y = 1; y <= LOW_STEP_TOP_Y; y++) {
        helper.setBlock(x, y, LOW_STEP_Z, Blocks.STONE);
      }
      for (int y = 1; y <= HIGH_STEP_TOP_Y; y++) {
        helper.setBlock(x, y, HIGH_STEP_Z, Blocks.STONE);
      }
    }
    // Rest against the high step's face, as a blocked strafe leaves the rider.
    double faceZ = helper.absoluteVec(new Vec3(0.0, 0.0, HIGH_STEP_Z)).z;
    double shift = faceZ + CONTACT_RESIDUE - expectedRiderBox(ghast, rider).maxZ;
    ghast.setPos(ghast.getX(), ghast.getY(), ghast.getZ() + shift);
    ghast.positionRider(rider);
    double startDepth = riderOverlapDepth(helper, ghast, rider);
    helper.assertTrue(
        startDepth > 0.0 && startDepth <= Shapes.EPSILON,
        "rider should start touching the step, depth " + startDepth);

    Random random = new Random(STRAFE_SEED);
    StringBuilder depthPerCycle = new StringBuilder();
    for (int cycle = 0; cycle < STRAFE_CYCLES; cycle++) {
      double side = cycle % 2 == 0 ? STRAFE_SPEED : -STRAFE_SPEED;
      double cycleDepth = 0.0;
      boolean eyeInWall = false;
      for (int tick = 0; tick < STRAFE_TICKS_PER_CYCLE; tick++) {
        rider.setYRot(FACING_EAST + (float) (random.nextGaussian() * YAW_JITTER_DEGREES));
        ghast.aiStep();
        Vec3 push =
            new Vec3(
                random.nextDouble() * FORWARD_PUSH,
                (random.nextDouble() - 0.5) * VERTICAL_PUSH,
                side);
        ghast.move(MoverType.PLAYER, push);
        ghast.positionRider(rider);
        cycleDepth = Math.max(cycleDepth, riderOverlapDepth(helper, ghast, rider));
        eyeInWall |= rider.isInWall();
      }
      depthPerCycle.append(String.format(Locale.ROOT, " %.3f", cycleDepth));
      helper.assertTrue(
          cycleDepth <= Shapes.EPSILON && !eyeInWall,
          "rider pushed into blocks; deepest overlap per cycle:" + depthPerCycle);
    }
    helper.succeed();
  }

  /**
   * Vanilla collision ignores blocks a box already overlaps; a rider that is inside a block may
   * move out of it but never deeper into it.
   */
  @GameTest(maxTicks = 20)
  public void riderInsideBlockMovesOutButNeverDeeper(GameTestHelper helper) {
    Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
    HappyGhast ghast = mountedGhast(helper, rider);
    helper.setBlock(EMBEDDING_BLOCK_X, EMBEDDING_BLOCK_Y, EMBEDDING_BLOCK_Z, Blocks.STONE);
    double startDepth = riderOverlapDepth(helper, ghast, rider);
    helper.assertTrue(startDepth > Shapes.EPSILON, "rider should start inside the block");

    for (int tick = 0; tick < PUSH_TICKS; tick++) {
      ghast.move(MoverType.PLAYER, DEEPER_PUSH);
    }
    double pushedDepth = riderOverlapDepth(helper, ghast, rider);
    helper.assertTrue(
        pushedDepth <= startDepth, "rider went deeper: " + startDepth + " -> " + pushedDepth);

    for (int tick = 0; tick < PUSH_TICKS; tick++) {
      ghast.move(MoverType.PLAYER, DEEPER_PUSH.reverse());
    }
    double leftDepth = riderOverlapDepth(helper, ghast, rider);
    helper.assertTrue(leftDepth <= Shapes.EPSILON, "rider should get out, depth " + leftDepth);
    helper.succeed();
  }

  private static HappyGhast mountedGhast(GameTestHelper helper, Player rider) {
    return mountedGhast(helper, rider, GHAST_POSITION, FACING_SOUTH);
  }

  private static HappyGhast mountedGhast(
      GameTestHelper helper, Player rider, Vec3 position, float yaw) {
    HappyGhast ghast = helper.spawn(EntityTypes.HAPPY_GHAST, position);
    ghast.setNoAi(true);
    ghast.setYRot(yaw);
    ghast.setYBodyRot(yaw);
    ghast.setYHeadRot(yaw);
    ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.pick(DyeColor.WHITE)));
    rider.setYRot(yaw);
    rider.startRiding(ghast, true, false);
    ServerGlideSessions.update(rider.getUUID(), GlideSettings.DEFAULTS);
    helper.assertTrue(ghast.getControllingPassenger() == rider, "rider must control the ghast");
    return ghast;
  }

  /** Rider box at its expected riding position, built from vanilla APIs only. */
  private static AABB expectedRiderBox(HappyGhast ghast, Entity rider) {
    Vec3 feet =
        ghast.getPassengerRidingPosition(rider).subtract(rider.getVehicleAttachmentPoint(ghast));
    return rider.getDimensions(rider.getPose()).makeBoundingBox(feet);
  }

  private static void assertRiderClear(
      GameTestHelper helper, HappyGhast ghast, Entity rider, String when) {
    helper.assertTrue(
        helper.getLevel().noCollision(rider, expectedRiderBox(ghast, rider)),
        "rider box inside blocks at " + when);
  }

  /** How far the rider box reaches into the block it overlaps most, along its shallowest axis. */
  private static double riderOverlapDepth(GameTestHelper helper, HappyGhast ghast, Entity rider) {
    AABB box = expectedRiderBox(ghast, rider);
    double deepest = 0.0;
    for (VoxelShape shape : helper.getLevel().getBlockCollisions(rider, box)) {
      for (AABB part : shape.toAabbs()) {
        double x = Math.min(box.maxX - part.minX, part.maxX - box.minX);
        double y = Math.min(box.maxY - part.minY, part.maxY - box.minY);
        double z = Math.min(box.maxZ - part.minZ, part.maxZ - box.minZ);
        deepest = Math.max(deepest, Math.min(x, Math.min(y, z)));
      }
    }
    return deepest;
  }
}

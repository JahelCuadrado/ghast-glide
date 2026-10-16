package com.einsteinmods.ghastglide.gametest;

import com.einsteinmods.ghastglide.core.ControlScheme;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.core.ServerPolicy;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.phys.Vec3;

/**
 * Server-side noclip behaviour. Moves are replayed with {@link MoverType#PLAYER}, exactly like
 * {@code ServerGamePacketListenerImpl#handleMoveVehicle} does for client-controlled vehicles.
 */
public final class NoclipGameTest {
  private static final double STEP = 2.0;
  private static final double EPSILON = 1.0E-6;
  private static final GlideSettings NOCLIP_OFF = GlideSettings.DEFAULTS.withNoclipEnabled(false);

  // Ghast (4x4x4) at x=4, z=2 spans z 0..4 and y 1..5, eyes at y 3.6. Measured rider feet: y 4.4.
  private static final Vec3 GHAST_POSITION = new Vec3(4.0, 1.0, 2.0);
  private static final int WALL_Z = 5;
  private static final int GHAST_TOP_Y = 3;
  private static final int RIDER_TOP_Y = 7;

  @GameTest(maxTicks = 20)
  public void ghastPassesWallThatOnlyItsBodyTouches(GameTestHelper helper) {
    HappyGhast ghast = mountedGhast(helper, GlideSettings.DEFAULTS);
    buildWall(helper, GHAST_TOP_Y);

    double moved = moveForward(ghast);

    helper.assertTrue(
        Math.abs(moved - STEP) < EPSILON, "ghast should move full step, moved " + moved);
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void vanillaControlsStillPassWallThatOnlyTheBodyTouches(GameTestHelper helper) {
    HappyGhast ghast =
        mountedGhast(helper, GlideSettings.DEFAULTS.withControlScheme(ControlScheme.VANILLA));
    buildWall(helper, GHAST_TOP_Y);

    double moved = moveForward(ghast);

    helper.assertTrue(
        Math.abs(moved - STEP) < EPSILON, "ghast should move full step, moved " + moved);
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void riderBodyStillCollidesWithBlocks(GameTestHelper helper) {
    HappyGhast ghast = mountedGhast(helper, GlideSettings.DEFAULTS);
    buildWall(helper, RIDER_TOP_Y);

    double moved = moveForward(ghast);

    helper.assertTrue(moved < STEP - EPSILON, "rider box should stop the ghast, moved " + moved);
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void phasingGhastDoesNotSuffocateEvenAfterDismount(GameTestHelper helper) {
    HappyGhast ghast = mountedGhast(helper, GlideSettings.DEFAULTS);
    buildWall(helper, GHAST_TOP_Y);
    moveForward(ghast);
    moveForward(ghast);

    helper.assertFalse(
        helper.getLevel().noCollision(ghast.getBoundingBox()), "ghast must overlap the wall");
    helper.assertFalse(ghast.isInWall(), "ghast inside blocks must not be in wall while gliding");
    ghast.ejectPassengers();
    helper.assertFalse(ghast.isInWall(), "ghast left inside blocks must stay immune");
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void noclipDisabledKeepsVanillaCollision(GameTestHelper helper) {
    HappyGhast ghast = mountedGhast(helper, NOCLIP_OFF);
    buildWall(helper, GHAST_TOP_Y);

    double moved = moveForward(ghast);

    helper.assertTrue(moved < STEP - EPSILON, "wall should stop the ghast, moved " + moved);
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void serverPolicyCanForbidNoclip(GameTestHelper helper) {
    ServerPolicy previous = ServerGlideSessions.filePolicy();
    ServerGlideSessions.start(new ServerPolicy(false, ServerPolicy.MAX_COMBINED_MULTIPLIER));
    try {
      HappyGhast ghast = mountedGhast(helper, GlideSettings.DEFAULTS);
      buildWall(helper, GHAST_TOP_Y);

      double moved = moveForward(ghast);

      helper.assertTrue(moved < STEP - EPSILON, "forbidden noclip should collide, moved " + moved);
    } finally {
      ServerGlideSessions.start(previous);
    }
    helper.succeed();
  }

  private static HappyGhast mountedGhast(GameTestHelper helper, GlideSettings settings) {
    HappyGhast ghast = helper.spawn(EntityTypes.HAPPY_GHAST, GHAST_POSITION);
    ghast.setNoAi(true);
    ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.pick(DyeColor.WHITE)));
    Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
    rider.startRiding(ghast, true, false);
    ServerGlideSessions.update(rider.getUUID(), settings);
    helper.assertTrue(ghast.getControllingPassenger() == rider, "rider must control the ghast");
    return ghast;
  }

  private static void buildWall(GameTestHelper helper, int topY) {
    for (int x = 1; x <= 7; x++) {
      for (int y = 1; y <= topY; y++) {
        helper.setBlock(x, y, WALL_Z, Blocks.STONE);
      }
    }
  }

  private static double moveForward(HappyGhast ghast) {
    double before = ghast.getZ();
    ghast.move(MoverType.PLAYER, new Vec3(0.0, 0.0, STEP));
    return ghast.getZ() - before;
  }
}

package com.einsteinmods.ghastglide.gametest;

import com.einsteinmods.ghastglide.core.ControlScheme;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side effect of the control scheme. Sneak is checked as vanilla does: {@code
 * Player#rideTick} stops riding on the server when {@code Player#wantsToStopRiding} (sneak) is
 * true. Each test runs within its first tick, so sessions cleared by other tests cannot interfere.
 */
public final class ControlSchemeGameTest {
  private static final Vec3 GHAST_POSITION = new Vec3(4.0, 1.0, 2.0);
  private static final Vec3 FORWARD_INPUT = new Vec3(0.0, 0.0, 1.0);

  /** Vanilla {@code HappyGhast#travel} moves {@code 0.05 * 5 / 3 ≈ 0.083} blocks for this input. */
  private static final double MIN_VANILLA_STEP = 0.05;

  private static final double EPSILON = 1.0E-6;

  /** Long enough for vanilla to have crouched ({@code Player#updatePlayerPose} runs every tick). */
  private static final int SNEAK_TICKS = 10;

  @GameTest(maxTicks = 20)
  public void vanillaControlsSneakDismounts(GameTestHelper helper) {
    Mounted mounted = mount(helper, ControlScheme.VANILLA);

    sneak(mounted.rider());

    helper.assertFalse(mounted.rider().isPassenger(), "sneak must dismount with vanilla controls");
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void modControlsSneakKeepsRiding(GameTestHelper helper) {
    Mounted mounted = mount(helper, ControlScheme.MOD);

    sneak(mounted.rider());

    helper.assertTrue(
        mounted.rider().getVehicle() == mounted.ghast(), "sneak must descend, not dismount");
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void modControlsSneakKeepsStandingPose(GameTestHelper helper) {
    Player rider = mount(helper, ControlScheme.MOD).rider();
    double height = rider.getBoundingBox().getYsize();

    for (int tick = 0; tick < SNEAK_TICKS; tick++) {
      sneak(rider);
      helper.assertFalse(rider.isCrouching(), "sneak while gliding must not crouch, tick " + tick);
      double heightNow = rider.getBoundingBox().getYsize();
      helper.assertTrue(
          Math.abs(heightNow - height) < EPSILON,
          "rider box height must stay " + height + ", was " + heightNow + " at tick " + tick);
    }
    // Glide's descend input (FlightInput#descend) reads the same sneak state.
    helper.assertTrue(rider.isShiftKeyDown(), "sneak must stay held for descending");
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void sneakStillCrouchesWhenNotRiding(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setPos(helper.absoluteVec(GHAST_POSITION));

    player.setShiftKeyDown(true);
    player.tick();

    helper.assertTrue(player.hasPose(Pose.CROUCHING), "sneak must crouch when not riding");
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void vanillaControlsKeepVanillaTravel(GameTestHelper helper) {
    HappyGhast ghast = mount(helper, ControlScheme.VANILLA).ghast();

    double moved = travelForward(ghast);

    helper.assertTrue(
        moved > MIN_VANILLA_STEP, "vanilla travel must move the ghast, moved " + moved);
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void modControlsReplaceVanillaTravel(GameTestHelper helper) {
    HappyGhast ghast = mount(helper, ControlScheme.MOD).ghast();

    // Glide reads the rider's keys (none on the server), not vanilla's travel input.
    double moved = travelForward(ghast);

    helper.assertTrue(moved < EPSILON, "glide must ignore vanilla travel input, moved " + moved);
    helper.succeed();
  }

  private static Mounted mount(GameTestHelper helper, ControlScheme scheme) {
    HappyGhast ghast = helper.spawn(EntityTypes.HAPPY_GHAST, GHAST_POSITION);
    ghast.setNoAi(true);
    ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.pick(DyeColor.WHITE)));
    Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
    rider.startRiding(ghast, true, false);
    ServerGlideSessions.update(rider.getUUID(), GlideSettings.DEFAULTS.withControlScheme(scheme));
    helper.assertTrue(ghast.getControllingPassenger() == rider, "rider must control the ghast");
    return new Mounted(ghast, rider);
  }

  private static void sneak(Player rider) {
    rider.setShiftKeyDown(true);
    rider.rideTick();
  }

  private static double travelForward(HappyGhast ghast) {
    Vec3 before = ghast.position();
    ghast.setDeltaMovement(Vec3.ZERO);
    ghast.travel(FORWARD_INPUT);
    return ghast.position().subtract(before).horizontalDistance();
  }

  private record Mounted(HappyGhast ghast, Player rider) {}
}

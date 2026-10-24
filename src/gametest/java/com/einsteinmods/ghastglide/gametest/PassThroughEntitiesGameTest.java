package com.einsteinmods.ghastglide.gametest;

import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * "Pass through entities" with noclip off (noclip already ignores entities): the flown ghast moves
 * through a boat and does not push a mob it overlaps. Moves are replayed with {@link
 * MoverType#PLAYER}, as {@code ServerGamePacketListenerImpl#handleMoveVehicle} does.
 */
public final class PassThroughEntitiesGameTest {
  private static final double STEP = 2.0;
  private static final double EPSILON = 1.0E-6;

  // Ghast (4x4x4) at x=4, z=2 spans x 2..6, z 0..4 and y 1..5.
  private static final Vec3 GHAST_POSITION = new Vec3(4.0, 1.0, 2.0);

  // A boat (1.375 wide) just in front of the ghast, at its height: z 4.81..6.19.
  private static final Vec3 BOAT_POSITION = new Vec3(4.0, 2.0, 5.5);

  // A pig on the floor inside the ghast's box, off its centre so vanilla pushes it sideways. With
  // no AI it never travels (LivingEntity#aiStep), so pushes pile up in its delta movement.
  private static final Vec3 PIG_POSITION = new Vec3(5.5, 1.0, 2.0);
  private static final int PUSH_TICKS = 10;

  private static final GlideSettings PASSING = GlideSettings.DEFAULTS.withNoclipEnabled(false);
  private static final GlideSettings COLLIDING = PASSING.withPassThroughEntities(false);

  @GameTest(maxTicks = 20)
  public void ghastPassesThroughBoatWithSettingOn(GameTestHelper helper) {
    HappyGhast ghast = mountedGhast(helper, PASSING);
    helper.spawn(EntityTypes.OAK_BOAT, BOAT_POSITION);

    double moved = moveForward(ghast);

    helper.assertTrue(
        Math.abs(moved - STEP) < EPSILON, "ghast should pass the boat, moved " + moved);
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void boatStopsGhastWithSettingOff(GameTestHelper helper) {
    HappyGhast ghast = mountedGhast(helper, COLLIDING);
    helper.spawn(EntityTypes.OAK_BOAT, BOAT_POSITION);

    double moved = moveForward(ghast);

    helper.assertTrue(moved < STEP - EPSILON, "boat should stop the ghast, moved " + moved);
    helper.succeed();
  }

  @GameTest(maxTicks = PUSH_TICKS + 10)
  public void ghastDoesNotPushMobWithSettingOn(GameTestHelper helper) {
    mountedGhast(helper, PASSING);
    Pig pig = pig(helper);

    helper.runAfterDelay(
        PUSH_TICKS,
        () -> {
          double pushed = pig.getDeltaMovement().horizontalDistance();
          helper.assertTrue(pushed < EPSILON, "pig must not be pushed, pushed " + pushed);
          helper.succeed();
        });
  }

  @GameTest(maxTicks = PUSH_TICKS + 10)
  public void ghastPushesMobWithSettingOff(GameTestHelper helper) {
    mountedGhast(helper, COLLIDING);
    Pig pig = pig(helper);

    helper.runAfterDelay(
        PUSH_TICKS,
        () -> {
          double pushed = pig.getDeltaMovement().horizontalDistance();
          helper.assertTrue(pushed > EPSILON, "vanilla ghast must push the pig");
          helper.succeed();
        });
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

  private static Pig pig(GameTestHelper helper) {
    Pig pig = helper.spawn(EntityTypes.PIG, PIG_POSITION);
    pig.setNoAi(true);
    return pig;
  }

  private static double moveForward(HappyGhast ghast) {
    double before = ghast.getZ();
    ghast.move(MoverType.PLAYER, new Vec3(0.0, 0.0, STEP));
    return ghast.getZ() - before;
  }
}

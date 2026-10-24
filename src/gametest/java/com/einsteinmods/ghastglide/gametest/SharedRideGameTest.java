package com.einsteinmods.ghastglide.gametest;

import com.einsteinmods.ghastglide.core.ControlScheme;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The pilot (first passenger) keeps control while others share the ghast. Vanilla starts its still
 * timeout when any passenger leaves ({@code HappyGhast#removePassenger}) and renews it while a
 * player stands on the ghast's head ({@code HappyGhast#tick}); during it {@code
 * getControllingPassenger()} is null, so sneak would dismount the pilot ({@code Player#rideTick}).
 * Each test runs within its first tick, so sessions cleared by other tests cannot interfere.
 */
public final class SharedRideGameTest {
  private static final Vec3 GHAST_POSITION = new Vec3(4.0, 1.0, 2.0);

  @GameTest(maxTicks = 20)
  public void passengerLeavingKeepsPilotControl(GameTestHelper helper) {
    HappyGhast ghast = spawnHarnessedGhast(helper);
    Player pilot = mountGliding(helper, ghast);
    Player passenger = helper.makeMockPlayer(GameType.SURVIVAL);
    passenger.startRiding(ghast, true, false);

    passenger.stopRiding();

    assertPilotKeepsControl(helper, ghast, pilot);
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void playerOnHeadKeepsPilotControl(GameTestHelper helper) {
    HappyGhast ghast = spawnHarnessedGhast(helper);
    Player pilot = mountGliding(helper, ghast);
    // Vanilla scanPlayerAboveGhast only sees players in the level's player list.
    if (!(helper.makeMockServerPlayer(GameType.SURVIVAL) instanceof ServerPlayer bystander)) {
      throw helper.assertionException(Component.literal("mock server player expected"));
    }
    AABB box = ghast.getBoundingBox();
    bystander.setPos(box.getCenter().x, box.maxY, box.getCenter().z);
    List<ServerPlayer> players = helper.getLevel().players();
    players.add(bystander);
    try {
      ghast.tick();
    } finally {
      players.remove(bystander);
    }

    assertPilotKeepsControl(helper, ghast, pilot);
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void nonPilotSneakStillDismounts(GameTestHelper helper) {
    HappyGhast ghast = spawnHarnessedGhast(helper);
    mountGliding(helper, ghast);
    Player passenger = helper.makeMockPlayer(GameType.SURVIVAL);
    passenger.startRiding(ghast, true, false);
    ServerGlideSessions.update(
        passenger.getUUID(), GlideSettings.DEFAULTS.withControlScheme(ControlScheme.MOD));

    sneak(passenger);

    helper.assertFalse(passenger.isPassenger(), "sneak must dismount a passenger that is no pilot");
    helper.succeed();
  }

  private static HappyGhast spawnHarnessedGhast(GameTestHelper helper) {
    HappyGhast ghast = helper.spawn(EntityTypes.HAPPY_GHAST, GHAST_POSITION);
    ghast.setNoAi(true);
    ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.pick(DyeColor.WHITE)));
    return ghast;
  }

  private static Player mountGliding(GameTestHelper helper, HappyGhast ghast) {
    Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
    pilot.startRiding(ghast, true, false);
    ServerGlideSessions.update(
        pilot.getUUID(), GlideSettings.DEFAULTS.withControlScheme(ControlScheme.MOD));
    helper.assertTrue(ghast.getControllingPassenger() == pilot, "pilot must control the ghast");
    return pilot;
  }

  private static void assertPilotKeepsControl(
      GameTestHelper helper, HappyGhast ghast, Player pilot) {
    helper.assertFalse(ghast.isOnStillTimeout(), "a piloted ghast must not hold still");
    helper.assertTrue(ghast.getControllingPassenger() == pilot, "pilot must keep control");
    sneak(pilot);
    helper.assertTrue(pilot.getVehicle() == ghast, "pilot sneak must descend, not dismount");
  }

  private static void sneak(Player rider) {
    rider.setShiftKeyDown(true);
    rider.rideTick();
  }
}

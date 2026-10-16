package com.einsteinmods.ghastglide.gametest;

import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * A rider using a boat on their ghast hangs it below with a lead. Interactions go through {@code
 * Player#interactOn}, as {@code ServerGamePacketListenerImpl#handleInteract} does.
 */
public final class BoatHangingGameTest {
  // Ghast (4x4x4) spans y 3..7: the spot right under it (y ≈ 2.44..3) is free air.
  private static final Vec3 GHAST_POSITION = new Vec3(4.0, 3.0, 4.0);
  private static final int BOATS = 4;
  private static final int LEADS = 3;
  private static final int LEAD_SLOT = 20;
  private static final double SEARCH_RADIUS = 16.0;

  @GameTest(maxTicks = 20)
  public void riderHangsBoatAndUsesOneBoatAndOneLead(GameTestHelper helper) {
    HappyGhast ghast = ghast(helper);
    Player rider = rider(helper, ghast, GameType.SURVIVAL, GlideSettings.DEFAULTS, true);

    rider.interactOn(ghast, InteractionHand.MAIN_HAND, Vec3.ZERO);

    List<AbstractBoat> boats = boatsLeashedTo(helper, ghast);
    helper.assertTrue(boats.size() == 1, "one boat must hang from the ghast, got " + boats.size());
    helper.assertTrue(
        boats.getFirst().getType() == EntityTypes.OAK_BOAT, "boat type must follow the item");
    helper.assertTrue(
        boats.getFirst().getBoundingBox().maxY <= ghast.getBoundingBox().minY,
        "boat must hang under the ghast");
    helper.assertTrue(heldBoats(rider) == BOATS - 1, "one boat must be used");
    helper.assertTrue(leads(rider) == LEADS - 1, "one lead must be used");
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void withoutLeadNothingHappens(GameTestHelper helper) {
    HappyGhast ghast = ghast(helper);
    Player rider = rider(helper, ghast, GameType.SURVIVAL, GlideSettings.DEFAULTS, false);

    rider.interactOn(ghast, InteractionHand.MAIN_HAND, Vec3.ZERO);

    helper.assertTrue(boatsLeashedTo(helper, ghast).isEmpty(), "no boat without a lead");
    helper.assertTrue(heldBoats(rider) == BOATS, "no boat must be used");
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void disabledSettingKeepsVanilla(GameTestHelper helper) {
    HappyGhast ghast = ghast(helper);
    Player rider =
        rider(
            helper,
            ghast,
            GameType.SURVIVAL,
            GlideSettings.DEFAULTS.withHangBoatWithClick(false),
            true);

    rider.interactOn(ghast, InteractionHand.MAIN_HAND, Vec3.ZERO);

    helper.assertTrue(boatsLeashedTo(helper, ghast).isEmpty(), "no boat with the setting off");
    helper.assertTrue(heldBoats(rider) == BOATS, "no boat must be used");
    helper.assertTrue(leads(rider) == LEADS, "no lead must be used");
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void everyClickHangsAnotherBoat(GameTestHelper helper) {
    HappyGhast ghast = ghast(helper);
    Player rider = rider(helper, ghast, GameType.SURVIVAL, GlideSettings.DEFAULTS, true);

    rider.interactOn(ghast, InteractionHand.MAIN_HAND, Vec3.ZERO);
    rider.interactOn(ghast, InteractionHand.MAIN_HAND, Vec3.ZERO);

    List<AbstractBoat> boats = boatsLeashedTo(helper, ghast);
    helper.assertTrue(boats.size() == 2, "two boats must hang, got " + boats.size());
    helper.assertFalse(
        boats.get(0).getBoundingBox().intersects(boats.get(1).getBoundingBox()),
        "boats must not overlap");
    helper.assertTrue(heldBoats(rider) == BOATS - 2, "two boats must be used");
    helper.assertTrue(leads(rider) == LEADS - 2, "two leads must be used");
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void creativeConsumesNothing(GameTestHelper helper) {
    HappyGhast ghast = ghast(helper);
    Player rider = rider(helper, ghast, GameType.CREATIVE, GlideSettings.DEFAULTS, true);

    rider.interactOn(ghast, InteractionHand.MAIN_HAND, Vec3.ZERO);

    helper.assertTrue(boatsLeashedTo(helper, ghast).size() == 1, "creative must hang a boat");
    helper.assertTrue(heldBoats(rider) == BOATS, "creative must keep the boat");
    helper.assertTrue(leads(rider) == LEADS, "creative must keep the lead");
    helper.succeed();
  }

  private static HappyGhast ghast(GameTestHelper helper) {
    HappyGhast ghast = helper.spawn(EntityTypes.HAPPY_GHAST, GHAST_POSITION);
    ghast.setNoAi(true);
    ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.pick(DyeColor.WHITE)));
    return ghast;
  }

  private static Player rider(
      GameTestHelper helper,
      HappyGhast ghast,
      GameType gameType,
      GlideSettings settings,
      boolean withLeads) {
    Player rider = helper.makeMockPlayer(gameType);
    gameType.updatePlayerAbilities(rider.getAbilities());
    rider.startRiding(ghast, true, false);
    ServerGlideSessions.update(rider.getUUID(), settings);
    rider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_BOAT, BOATS));
    if (withLeads) {
      rider.getInventory().setItem(LEAD_SLOT, new ItemStack(Items.LEAD, LEADS));
    }
    helper.assertTrue(rider.getVehicle() == ghast, "rider must ride the ghast");
    return rider;
  }

  private static List<AbstractBoat> boatsLeashedTo(GameTestHelper helper, HappyGhast ghast) {
    return helper
        .getLevel()
        .getEntitiesOfClass(
            AbstractBoat.class,
            ghast.getBoundingBox().inflate(SEARCH_RADIUS),
            boat -> boat.getLeashHolder() == ghast);
  }

  private static int heldBoats(Player rider) {
    return rider.getMainHandItem().getCount();
  }

  private static int leads(Player rider) {
    return rider.getInventory().getItem(LEAD_SLOT).getCount();
  }
}

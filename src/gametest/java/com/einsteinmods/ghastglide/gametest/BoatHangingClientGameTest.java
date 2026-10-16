package com.einsteinmods.ghastglide.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A rider right-clicking their ghast with a boat hangs it, through the real client input path:
 * crosshair picking ({@code LocalPlayer#raycastHitResult}), {@code Minecraft#startUseItem}, the
 * interact packet and {@code ServerGamePacketListenerImpl#handleInteract}. Vanilla never picks the
 * player's own vehicle ({@code ProjectileUtil#getEntityHitResult}), which the server-side GameTests
 * cannot see because they call {@code Player#interactOn} directly.
 */
public final class BoatHangingClientGameTest implements FabricClientGameTest {
  /** Ghast spawn height above the player; high enough that no block is within reach below. */
  private static final double GHAST_HEIGHT_ABOVE_GROUND = 6.0;

  private static final int BOATS = 8;
  private static final int LEADS = 8;
  private static final int LEAD_SLOT = 20;
  private static final double SEARCH_RADIUS = 16.0;
  private static final int SETTLE_TICKS = 5;
  private static final int CLICK_TICKS = 5;

  /**
   * Views to right-click from, as {yaw offset, pitch, boats it must hang}. The rider's eye sits
   * about one block above the ghast's top and 0.3 blocks behind its front face, so: the head (front
   * of the top), straight down at the back, back over the body, and forward-down past the head (the
   * view ray misses the ghast: vanilla boat use, nothing in reach, nothing happens).
   */
  private static final float[][] VIEWS = {
    {0.0F, 80.0F, 1}, {0.0F, 90.0F, 1}, {180.0F, 45.0F, 1}, {0.0F, 30.0F, 0}
  };

  private static final int VIEW_YAW = 0;
  private static final int VIEW_PITCH = 1;
  private static final int VIEW_EXPECTED_BOATS = 2;

  @Override
  public void runTest(ClientGameTestContext context) {
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      TestServerConnection connection = world.getConnection();
      connection.waitForChunksRender();
      world.getServer().runOnServer(server -> mountGhastWithBoatsAndLeads(connection));
      context.waitFor(BoatHangingClientGameTest::ridesGhast);
      context.waitTicks(SETTLE_TICKS);

      float baseYaw = context.computeOnClient(mc -> mc.player.getYRot());
      int hangingBefore = 0;
      int expectedHung = 0;
      for (float[] view : VIEWS) {
        context.getInput().lookAt(baseYaw + view[VIEW_YAW], view[VIEW_PITCH]);
        context.waitTicks(SETTLE_TICKS);
        String crosshair = context.computeOnClient(BoatHangingClientGameTest::describeCrosshair);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(CLICK_TICKS);
        connection.waitForServerboundPackets();

        int hanging = world.getServer().computeOnServer(server -> boatsHanging(connection));
        int expected = (int) view[VIEW_EXPECTED_BOATS];
        if (hanging - hangingBefore != expected) {
          throw new AssertionError(
              "right-click at yaw +"
                  + view[VIEW_YAW]
                  + ", pitch "
                  + view[VIEW_PITCH]
                  + " must hang "
                  + expected
                  + " boat(s), hung "
                  + (hanging - hangingBefore)
                  + "; vanilla crosshair: "
                  + crosshair);
        }
        hangingBefore = hanging;
        expectedHung += expected;
      }
      int boatsUsed =
          BOATS
              - world
                  .getServer()
                  .computeOnServer(
                      server -> connection.getServerPlayer().getMainHandItem().getCount());
      if (boatsUsed != expectedHung) {
        throw new AssertionError("each hang must use one boat, used " + boatsUsed);
      }
    }
  }

  private static void mountGhastWithBoatsAndLeads(TestServerConnection connection) {
    ServerPlayer player = connection.getServerPlayer();
    ServerLevel level = connection.getServerLevel();
    player.setGameMode(GameType.SURVIVAL);
    HappyGhast ghast = EntityTypes.HAPPY_GHAST.create(level, EntitySpawnReason.COMMAND);
    if (ghast == null) {
      throw new AssertionError("happy ghast must spawn");
    }
    ghast.setPos(player.getX(), player.getY() + GHAST_HEIGHT_ABOVE_GROUND, player.getZ());
    ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.pick(DyeColor.WHITE)));
    level.addFreshEntity(ghast);
    if (!player.startRiding(ghast, true, true)) {
      throw new AssertionError("player must ride the ghast");
    }
    Inventory inventory = player.getInventory();
    inventory.setItem(inventory.getSelectedSlot(), new ItemStack(Items.OAK_BOAT, BOATS));
    inventory.setItem(LEAD_SLOT, new ItemStack(Items.LEAD, LEADS));
  }

  private static boolean ridesGhast(Minecraft minecraft) {
    return minecraft.player != null
        && minecraft.player.getVehicle() instanceof HappyGhast
        && minecraft.player.getMainHandItem().is(Items.OAK_BOAT);
  }

  private static String describeCrosshair(Minecraft minecraft) {
    HitResult hit = minecraft.hitResult;
    if (hit == null) {
      return "null";
    }
    return hit.getType()
        + (hit instanceof EntityHitResult entityHit ? " " + entityHit.getEntity().getType() : "")
        + " at "
        + hit.getLocation()
        + ", eye "
        + minecraft.player.getEyePosition()
        + ", ghast box "
        + minecraft.player.getVehicle().getBoundingBox();
  }

  private static int boatsHanging(TestServerConnection connection) {
    ServerPlayer player = connection.getServerPlayer();
    if (!(player.getVehicle() instanceof HappyGhast ghast)) {
      throw new AssertionError("player must still ride the ghast");
    }
    List<AbstractBoat> boats =
        connection
            .getServerLevel()
            .getEntitiesOfClass(
                AbstractBoat.class,
                ghast.getBoundingBox().inflate(SEARCH_RADIUS),
                boat -> boat.getLeashHolder() == ghast);
    return boats.size();
  }
}

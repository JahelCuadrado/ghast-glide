package com.einsteinmods.ghastglide.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Holding the real sneak key while gliding with the default (Mod) controls descends without
 * crouching: the client eye height ({@code Camera} eases toward {@code Entity#getEyeHeight} every
 * tick, so a constant eye height means a still camera), the box height and the pose on both sides
 * stay unchanged, and the rider keeps riding. Pose is checked directly: {@code
 * LocalPlayer#isCrouching} is false for any passenger, whatever the pose.
 */
public final class SneakDescendClientGameTest implements FabricClientGameTest {
  /** High enough above the ground that the ghast cannot land while descending. */
  private static final double GHAST_HEIGHT_ABOVE_GROUND = 30.0;

  /** Lets the server policy and the client's settings reach both sides before sneaking. */
  private static final int SETTLE_TICKS = 20;

  private static final int SNEAK_TICKS = 20;

  /** The ghast must drop clearly more than idle bobbing would move it. */
  private static final double MIN_DESCENT = 1.0;

  private static final double EPSILON = 1.0E-6;

  @Override
  public void runTest(ClientGameTestContext context) {
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      TestServerConnection connection = world.getConnection();
      connection.waitForChunksRender();
      world.getServer().runOnServer(server -> mountGhast(connection));
      context.waitFor(SneakDescendClientGameTest::ridesGhast);
      context.waitTicks(SETTLE_TICKS);

      float eyeHeight = context.computeOnClient(mc -> mc.player.getEyeHeight());
      double boxHeight = context.computeOnClient(mc -> mc.player.getBoundingBox().getYsize());
      double ghastYBefore = context.computeOnClient(mc -> mc.player.getVehicle().getY());

      context.getInput().holdKey(options -> options.keyShift);
      for (int tick = 0; tick < SNEAK_TICKS; tick++) {
        context.waitTick();
        String clientState =
            context.computeOnClient(mc -> describeChange(mc.player, eyeHeight, boxHeight));
        if (!clientState.isEmpty()) {
          throw new AssertionError("client, sneak tick " + tick + ": " + clientState);
        }
        boolean serverCrouching =
            world
                .getServer()
                .computeOnServer(server -> connection.getServerPlayer().hasPose(Pose.CROUCHING));
        if (serverCrouching) {
          throw new AssertionError("server player must not crouch, sneak tick " + tick);
        }
      }
      double ghastYAfter = context.computeOnClient(mc -> mc.player.getVehicle().getY());
      context.getInput().releaseKey(options -> options.keyShift);

      if (ghastYBefore - ghastYAfter < MIN_DESCENT) {
        throw new AssertionError(
            "sneak must descend, ghast went from y " + ghastYBefore + " to " + ghastYAfter);
      }
    }
  }

  private static void mountGhast(TestServerConnection connection) {
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
  }

  private static boolean ridesGhast(Minecraft minecraft) {
    return minecraft.player != null && minecraft.player.getVehicle() instanceof HappyGhast;
  }

  /** Empty when nothing changed, otherwise what did (test failure message). */
  private static String describeChange(LocalPlayer player, float eyeHeight, double boxHeight) {
    if (!(player.getVehicle() instanceof HappyGhast)) {
      return "rider must keep riding";
    }
    if (player.hasPose(Pose.CROUCHING) || !player.isShiftKeyDown()) {
      return "pose " + player.getPose() + ", sneak held " + player.isShiftKeyDown();
    }
    if (player.getEyeHeight() != eyeHeight) {
      return "eye height " + player.getEyeHeight() + " instead of " + eyeHeight;
    }
    double height = player.getBoundingBox().getYsize();
    return Math.abs(height - boxHeight) < EPSILON
        ? ""
        : "box height " + height + " instead of " + boxHeight;
  }
}

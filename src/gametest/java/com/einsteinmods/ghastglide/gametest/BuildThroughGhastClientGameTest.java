package com.einsteinmods.ghastglide.gametest;

import com.einsteinmods.ghastglide.client.ClientGlideState;
import com.einsteinmods.ghastglide.client.GhastGlideClient;
import com.einsteinmods.ghastglide.client.config.ClientPreferences;
import com.einsteinmods.ghastglide.client.config.FirstPersonVisibility;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * "Build through your ghast" through the real client input path, with the ghast hidden in first
 * person: looking straight down and right-clicking with a block places it inside the ridden ghast's
 * box ({@code Minecraft#startUseItem}, client prediction, {@code
 * ServerGamePacketListenerImpl#handleUseItemOn}). Then, with a boat and a lead, the same click
 * still hangs the boat ({@code RiddenGhastTarget}) and places nothing.
 *
 * <p>The ghast floats too high for the ground to be in reach, so a support block is set inside the
 * ghast's box under the rider's eye: the click hits its top face and the new block goes above it,
 * still inside the ghast and below the rider's feet (ghast Y + 3.4).
 */
public final class BuildThroughGhastClientGameTest implements FabricClientGameTest {
  /** Ghast spawn height above the player; high enough that no ground block is within reach. */
  private static final double GHAST_HEIGHT_ABOVE_GROUND = 6.0;

  /** Support block layer above the ghast's feet block; the placed block goes one layer higher. */
  private static final int SUPPORT_LAYER_ABOVE_GHAST = 1;

  private static final float LOOK_STRAIGHT_DOWN = 90.0F;
  private static final int BLOCKS = 8;
  private static final int BOATS = 4;
  private static final int LEADS = 4;
  private static final int LEAD_SLOT = 20;
  private static final double SEARCH_RADIUS = 16.0;

  /** Lets the server policy and the client's settings reach both sides. */
  private static final int SETTLE_TICKS = 20;

  private static final int CLICK_TICKS = 5;

  @Override
  public void runTest(ClientGameTestContext context) {
    ClientGlideState state = GhastGlideClient.state();
    GlideSettings originalSettings = context.computeOnClient(mc -> state.requested());
    ClientPreferences originalPreferences = context.computeOnClient(mc -> state.preferences());
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      TestServerConnection connection = world.getConnection();
      connection.waitForChunksRender();
      context.runOnClient(
          mc ->
              state.update(
                  originalSettings.withBuildThroughGhast(true),
                  new ClientPreferences(
                      FirstPersonVisibility.HIDDEN, FirstPersonVisibility.HIDDEN)));
      world.getServer().runOnServer(server -> mountGhastWithBlocks(connection));
      context.waitFor(BuildThroughGhastClientGameTest::ridesGhastWithBlocks);
      context.waitTicks(SETTLE_TICKS);

      BlockPos support =
          world.getServer().computeOnServer(server -> placeSupportUnderEye(connection));
      BlockPos target = support.above();
      context
          .getInput()
          .lookAt(context.computeOnClient(mc -> mc.player.getYRot()), LOOK_STRAIGHT_DOWN);
      context.waitTicks(CLICK_TICKS);
      String crosshair = context.computeOnClient(BuildThroughGhastClientGameTest::describe);
      context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
      context.waitTicks(CLICK_TICKS);
      connection.waitForServerboundPackets();

      boolean placed =
          world
              .getServer()
              .computeOnServer(
                  server -> connection.getServerLevel().getBlockState(target).is(Blocks.STONE));
      if (!placed) {
        throw new AssertionError(
            "looking down must place a block inside the ghast at " + target + "; " + crosshair);
      }

      world.getServer().runOnServer(server -> holdBoatWithLeads(connection));
      context.waitFor(mc -> mc.player.getMainHandItem().is(Items.OAK_BOAT));
      context.waitTicks(CLICK_TICKS);
      String boatCrosshair = context.computeOnClient(BuildThroughGhastClientGameTest::describe);
      context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
      context.waitTicks(CLICK_TICKS);
      connection.waitForServerboundPackets();

      String boatResult =
          world.getServer().computeOnServer(server -> describeBoatClick(connection));
      if (!boatResult.isEmpty()) {
        throw new AssertionError(boatResult + "; " + boatCrosshair);
      }
    } finally {
      context.runOnClient(mc -> state.update(originalSettings, originalPreferences));
    }
  }

  private static void mountGhastWithBlocks(TestServerConnection connection) {
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
    inventory.setItem(inventory.getSelectedSlot(), new ItemStack(Items.STONE, BLOCKS));
  }

  /** Sets the support block inside the ghast, straight under the rider's eye; returns its pos. */
  private static BlockPos placeSupportUnderEye(TestServerConnection connection) {
    ServerPlayer player = connection.getServerPlayer();
    if (!(player.getVehicle() instanceof HappyGhast ghast)) {
      throw new AssertionError("player must ride the ghast");
    }
    BlockPos support =
        BlockPos.containing(player.getX(), ghast.getY(), player.getZ())
            .above(SUPPORT_LAYER_ABOVE_GHAST);
    connection.getServerLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
    return support;
  }

  private static void holdBoatWithLeads(TestServerConnection connection) {
    Inventory inventory = connection.getServerPlayer().getInventory();
    inventory.setItem(inventory.getSelectedSlot(), new ItemStack(Items.OAK_BOAT, BOATS));
    inventory.setItem(LEAD_SLOT, new ItemStack(Items.LEAD, LEADS));
  }

  /** Empty when the click hung exactly one boat and placed no other, else what went wrong. */
  private static String describeBoatClick(TestServerConnection connection) {
    ServerPlayer player = connection.getServerPlayer();
    if (!(player.getVehicle() instanceof HappyGhast ghast)) {
      return "player must still ride the ghast";
    }
    ServerLevel level = connection.getServerLevel();
    int hanging =
        level
            .getEntitiesOfClass(
                AbstractBoat.class,
                ghast.getBoundingBox().inflate(SEARCH_RADIUS),
                boat -> boat.getLeashHolder() == ghast)
            .size();
    int allBoats =
        level
            .getEntitiesOfClass(AbstractBoat.class, ghast.getBoundingBox().inflate(SEARCH_RADIUS))
            .size();
    if (hanging != 1 || allBoats != 1) {
      return "boat click must hang one boat, hung " + hanging + " of " + allBoats + " boats";
    }
    return "";
  }

  private static boolean ridesGhastWithBlocks(Minecraft minecraft) {
    return minecraft.player != null
        && minecraft.player.getVehicle() instanceof HappyGhast
        && minecraft.player.getMainHandItem().is(Items.STONE);
  }

  private static String describe(Minecraft minecraft) {
    HitResult hit = minecraft.hitResult;
    String target =
        hit instanceof BlockHitResult blockHit
            ? "block " + blockHit.getBlockPos() + " " + blockHit.getDirection()
            : String.valueOf(hit == null ? null : hit.getType());
    return "crosshair "
        + target
        + ", eye "
        + minecraft.player.getEyePosition()
        + ", ghast box "
        + minecraft.player.getVehicle().getBoundingBox();
  }
}

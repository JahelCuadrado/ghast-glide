package com.einsteinmods.ghastglide.gametest;

import com.einsteinmods.ghastglide.client.ClientGlideState;
import com.einsteinmods.ghastglide.client.GhastGlideClient;
import com.einsteinmods.ghastglide.client.config.ClientPreferences;
import com.einsteinmods.ghastglide.core.GlideSettings;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * "Hide ghast in first person" also hides the boats leashed to the ridden ghast and every rope tied
 * to it, checked on what the real client extracts for rendering ({@code
 * LevelRenderState#entityRenderStates}): a boat and a raft hang from the ghast (a pig sits in the
 * boat) and a sheep is leashed to it. In first person the boat, the raft and the sheep's rope are
 * gone while the pig and the sheep stay; in third person, or with the option off, all render.
 */
public final class HideHungBoatsClientGameTest implements FabricClientGameTest {
  /** Ghast spawn height above the player; leaves room for the boats below it. */
  private static final double GHAST_HEIGHT_ABOVE_GROUND = 6.0;

  /** Boats sit just under the ghast's box, one on each side of its centre. */
  private static final double BOAT_SIDE_OFFSET = 1.0;

  private static final double BOAT_DROP_BELOW_GHAST = 0.7;
  private static final double SHEEP_SIDE_OFFSET = 3.0;
  private static final float LOOK_STRAIGHT_DOWN = 90.0F;
  private static final int QUAD_LEASH_ROPES = 4;
  private static final int SINGLE_LEASH_ROPES = 1;

  /** Lets the camera type and the leashes reach the rendered frames. */
  private static final int SETTLE_TICKS = 10;

  /** Last frame's counts; written and read on the render thread only. */
  private static @Nullable FrameCounts lastFrame;

  @Override
  public void runTest(ClientGameTestContext context) {
    LevelExtractionEvents.END_EXTRACTION.register(HideHungBoatsClientGameTest::recordFrame);
    ClientGlideState state = GhastGlideClient.state();
    GlideSettings originalSettings = context.computeOnClient(mc -> state.requested());
    ClientPreferences originalPreferences = context.computeOnClient(mc -> state.preferences());
    CameraType originalCamera = context.computeOnClient(mc -> mc.options.getCameraType());
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      TestServerConnection connection = world.getConnection();
      connection.waitForChunksRender();
      world.getServer().runOnServer(server -> mountGhastWithHungBoats(connection));
      context.waitFor(HideHungBoatsClientGameTest::seesHungBoatsAndLeashedSheep);
      context
          .getInput()
          .lookAt(context.computeOnClient(mc -> mc.player.getYRot()), LOOK_STRAIGHT_DOWN);

      expectFrame(context, state, originalSettings, true, CameraType.FIRST_PERSON, true);
      expectFrame(context, state, originalSettings, true, CameraType.THIRD_PERSON_BACK, false);
      expectFrame(context, state, originalSettings, false, CameraType.FIRST_PERSON, false);
    } finally {
      context.runOnClient(
          mc -> {
            state.update(originalSettings, originalPreferences);
            mc.options.setCameraType(originalCamera);
          });
    }
  }

  /** Sets the option and camera, waits for fresh frames and checks what they extracted. */
  private static void expectFrame(
      ClientGameTestContext context,
      ClientGlideState state,
      GlideSettings settings,
      boolean hideOption,
      CameraType camera,
      boolean hidden) {
    context.runOnClient(
        mc -> {
          state.update(settings, new ClientPreferences(hideOption));
          mc.options.setCameraType(camera);
        });
    context.waitTicks(SETTLE_TICKS);
    String problem = context.computeOnClient(mc -> describeFrame(hidden));
    if (!problem.isEmpty()) {
      throw new AssertionError("option " + hideOption + ", camera " + camera + ": " + problem);
    }
  }

  /** Empty when the last extracted frame matches {@code hidden}, else what is wrong. */
  private static String describeFrame(boolean hidden) {
    FrameCounts frame = lastFrame;
    if (frame == null) {
      return "no frame extracted";
    }
    int expectedBoats = hidden ? 0 : 2;
    FrameCounts expected =
        new FrameCounts(
            hidden ? 0 : 1,
            expectedBoats,
            expectedBoats * QUAD_LEASH_ROPES,
            1,
            1,
            hidden ? 0 : SINGLE_LEASH_ROPES);
    return frame.equals(expected) ? "" : "expected " + expected + ", got " + frame;
  }

  /**
   * Counts the entity render states of one frame. Read at the end of extraction: vanilla clears
   * them while drawing ({@code LevelRenderer#submitFeatures}), so they are gone between frames.
   */
  private static void recordFrame(LevelExtractionContext context) {
    List<EntityRenderState> states =
        Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.entityRenderStates;
    int ghasts = 0;
    int boats = 0;
    int boatRopes = 0;
    int pigs = 0;
    int sheep = 0;
    int sheepRopes = 0;
    for (EntityRenderState entity : states) {
      int ropes = entity.leashStates == null ? 0 : entity.leashStates.size();
      if (entity.entityType == EntityTypes.HAPPY_GHAST) {
        ghasts++;
      } else if (isHungBoatType(entity.entityType)) {
        boats++;
        boatRopes += ropes;
      } else if (entity.entityType == EntityTypes.PIG) {
        pigs++;
      } else if (entity.entityType == EntityTypes.SHEEP) {
        sheep++;
        sheepRopes += ropes;
      }
    }
    lastFrame = new FrameCounts(ghasts, boats, boatRopes, pigs, sheep, sheepRopes);
  }

  /** Entity render states of one frame, by kind; ropes are leash states. */
  private record FrameCounts(
      int ghasts, int boats, int boatRopes, int pigs, int sheep, int sheepRopes) {}

  private static boolean isHungBoatType(EntityType<?> type) {
    return type == EntityTypes.OAK_BOAT || type == EntityTypes.BAMBOO_RAFT;
  }

  private static void mountGhastWithHungBoats(TestServerConnection connection) {
    ServerPlayer player = connection.getServerPlayer();
    ServerLevel level = connection.getServerLevel();
    player.setGameMode(GameType.CREATIVE);
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
    AbstractBoat boat = hangBoat(level, ghast, EntityTypes.OAK_BOAT, BOAT_SIDE_OFFSET);
    hangBoat(level, ghast, EntityTypes.BAMBOO_RAFT, -BOAT_SIDE_OFFSET);
    Mob pig = spawnMob(level, EntityTypes.PIG, boat.getX(), boat.getY(), boat.getZ());
    if (!pig.startRiding(boat, true, true)) {
      throw new AssertionError("pig must sit in the boat");
    }
    Mob sheep =
        spawnMob(
            level,
            EntityTypes.SHEEP,
            ghast.getX() + SHEEP_SIDE_OFFSET,
            ghast.getY() - BOAT_DROP_BELOW_GHAST,
            ghast.getZ());
    sheep.setLeashedTo(ghast, true);
  }

  private static AbstractBoat hangBoat(
      ServerLevel level,
      HappyGhast ghast,
      EntityType<? extends AbstractBoat> type,
      double sideOffset) {
    AbstractBoat boat = type.create(level, EntitySpawnReason.COMMAND);
    if (boat == null) {
      throw new AssertionError(type + " must spawn");
    }
    boat.setPos(
        ghast.getX() + sideOffset,
        ghast.getBoundingBox().minY - BOAT_DROP_BELOW_GHAST,
        ghast.getZ());
    level.addFreshEntity(boat);
    boat.setLeashedTo(ghast, true);
    return boat;
  }

  private static Mob spawnMob(
      ServerLevel level, EntityType<? extends Mob> type, double x, double y, double z) {
    Mob mob = type.create(level, EntitySpawnReason.COMMAND);
    if (mob == null) {
      throw new AssertionError(type + " must spawn");
    }
    mob.setPos(x, y, z);
    mob.setNoAi(true);
    level.addFreshEntity(mob);
    return mob;
  }

  /** The client knows both hung boats, the seated pig and the leashed sheep. */
  private static boolean seesHungBoatsAndLeashedSheep(Minecraft minecraft) {
    if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof HappyGhast ghast)) {
      return false;
    }
    int hungBoats = 0;
    boolean seatedPig = false;
    boolean leashedSheep = false;
    for (Entity entity : minecraft.level.entitiesForRendering()) {
      if (entity instanceof AbstractBoat boat && boat.getLeashHolder() == ghast) {
        hungBoats++;
        seatedPig |= boat.getFirstPassenger() != null;
      } else if (entity.getType() == EntityTypes.SHEEP
          && entity instanceof Mob sheep
          && sheep.getLeashHolder() == ghast) {
        leashedSheep = true;
      }
    }
    return hungBoats == 2 && seatedPig && leashedSheep;
  }
}

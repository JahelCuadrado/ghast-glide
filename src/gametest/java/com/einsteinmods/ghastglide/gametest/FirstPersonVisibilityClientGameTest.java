package com.einsteinmods.ghastglide.gametest;

import com.einsteinmods.ghastglide.client.ClientGlideState;
import com.einsteinmods.ghastglide.client.FirstPersonGhastHiding;
import com.einsteinmods.ghastglide.client.GhastGlideClient;
import com.einsteinmods.ghastglide.client.config.ClientPreferences;
import com.einsteinmods.ghastglide.client.config.FirstPersonVisibility;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.ghast.HappyGhastHarnessModel;
import net.minecraft.client.model.animal.ghast.HappyGhastModel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
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
 * "Ghast in first person" and "Cargo in first person", checked on what the real client extracts for
 * rendering ({@code LevelRenderState#entityRenderStates}): a boat and a raft hang from the ridden
 * ghast (a pig sits in the boat), a sheep is leashed to it and a cow rides it with the player.
 * Hidden skips the ghast or its cargo (boats, the pig in the boat, the sheep) with their ropes;
 * semi-transparent extracts them marked, without ropes or shadow; the cow, another rider, is never
 * cargo. In third person everything is normal. The ghast's extracted state is also submitted to a
 * recording collector: a semi-transparent ghast draws its harness translucent with the body's alpha
 * and no rope knots; a normal one draws harness and knots as vanilla.
 */
public final class FirstPersonVisibilityClientGameTest implements FabricClientGameTest {
  /** Ghast spawn height above the player; leaves room for the boats below it. */
  private static final double GHAST_HEIGHT_ABOVE_GROUND = 6.0;

  /** Boats sit just under the ghast's box, one on each side of its centre. */
  private static final double BOAT_SIDE_OFFSET = 1.0;

  private static final double BOAT_DROP_BELOW_GHAST = 0.7;
  private static final double SHEEP_SIDE_OFFSET = 3.0;
  private static final float LOOK_STRAIGHT_DOWN = 90.0F;
  private static final int QUAD_LEASH_ROPES = 4;
  private static final int SINGLE_LEASH_ROPES = 1;
  private static final int HUNG_BOATS = 2;

  /** Ghast models a normal leash-holding ghast submits: body and rope knots. */
  private static final int NORMAL_GHAST_MODELS = 2;

  private static final int SEMI_TRANSPARENT_ALPHA =
      ARGB.alpha(FirstPersonGhastHiding.SEMI_TRANSPARENT_TINT);

  /** The pig in the boat and the leashed sheep. */
  private static final int CARGO_MOBS = 2;

  /** Lets the camera type and the leashes reach the rendered frames. */
  private static final int SETTLE_TICKS = 10;

  private static final FirstPersonVisibility NORMAL = FirstPersonVisibility.NORMAL;
  private static final FirstPersonVisibility HIDDEN = FirstPersonVisibility.HIDDEN;
  private static final FirstPersonVisibility SEMI = FirstPersonVisibility.SEMI_TRANSPARENT;

  /** Last frame's counts; written and read on the render thread only. */
  private static @Nullable FrameCounts lastFrame;

  @Override
  public void runTest(ClientGameTestContext context) {
    LevelExtractionEvents.END_EXTRACTION.register(FirstPersonVisibilityClientGameTest::recordFrame);
    ClientGlideState state = GhastGlideClient.state();
    GlideSettings originalSettings = context.computeOnClient(mc -> state.requested());
    ClientPreferences originalPreferences = context.computeOnClient(mc -> state.preferences());
    CameraType originalCamera = context.computeOnClient(mc -> mc.options.getCameraType());
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      TestServerConnection connection = world.getConnection();
      connection.waitForChunksRender();
      world.getServer().runOnServer(server -> mountGhastWithCargo(connection));
      context.waitFor(FirstPersonVisibilityClientGameTest::seesCargoAndCowRider);
      context
          .getInput()
          .lookAt(context.computeOnClient(mc -> mc.player.getYRot()), LOOK_STRAIGHT_DOWN);

      expect(context, state, originalSettings, NORMAL, NORMAL, CameraType.FIRST_PERSON);
      expect(context, state, originalSettings, HIDDEN, HIDDEN, CameraType.FIRST_PERSON);
      expect(context, state, originalSettings, SEMI, SEMI, CameraType.FIRST_PERSON);
      expect(context, state, originalSettings, HIDDEN, NORMAL, CameraType.FIRST_PERSON);
      expect(context, state, originalSettings, NORMAL, HIDDEN, CameraType.FIRST_PERSON);
      expect(context, state, originalSettings, SEMI, SEMI, CameraType.THIRD_PERSON_BACK);
      expect(context, state, originalSettings, HIDDEN, HIDDEN, CameraType.THIRD_PERSON_BACK);
    } finally {
      context.runOnClient(
          mc -> {
            state.update(originalSettings, originalPreferences);
            mc.options.setCameraType(originalCamera);
          });
    }
  }

  /** Sets the options and camera, waits for fresh frames and checks what they extracted. */
  private static void expect(
      ClientGameTestContext context,
      ClientGlideState state,
      GlideSettings settings,
      FirstPersonVisibility ghast,
      FirstPersonVisibility cargo,
      CameraType camera) {
    context.runOnClient(
        mc -> {
          state.update(settings, new ClientPreferences(ghast, cargo));
          mc.options.setCameraType(camera);
        });
    context.waitTicks(SETTLE_TICKS);
    boolean firstPerson = camera == CameraType.FIRST_PERSON;
    FrameCounts expected =
        expectedFrame(firstPerson ? ghast : NORMAL, firstPerson ? cargo : NORMAL);
    String problem = context.computeOnClient(mc -> describeFrame(expected));
    if (problem.isEmpty()) {
      problem = context.computeOnClient(FirstPersonVisibilityClientGameTest::describeCowRider);
    }
    if (!problem.isEmpty()) {
      throw new AssertionError(
          "ghast " + ghast + ", cargo " + cargo + ", camera " + camera + ": " + problem);
    }
  }

  /** What one frame extracts with these effective visibilities. */
  private static FrameCounts expectedFrame(
      FirstPersonVisibility ghast, FirstPersonVisibility cargo) {
    boolean ropes = ghast == NORMAL && cargo == NORMAL;
    int ghasts = ghast == HIDDEN ? 0 : 1;
    int cargoCount = cargo == HIDDEN ? 0 : 1;
    int boats = cargoCount * HUNG_BOATS;
    int semiTransparent = (ghast == SEMI ? 1 : 0) + (cargo == SEMI ? HUNG_BOATS + CARGO_MOBS : 0);
    int ghastModels = ghast == HIDDEN ? 0 : ghast == SEMI ? 1 : NORMAL_GHAST_MODELS;
    return new FrameCounts(
        ghasts,
        boats,
        ropes ? boats * QUAD_LEASH_ROPES : 0,
        cargoCount,
        cargoCount,
        ropes ? cargoCount * SINGLE_LEASH_ROPES : 0,
        semiTransparent,
        0,
        ghastModels,
        ghasts,
        ghast == SEMI ? 1 : 0);
  }

  /** Empty when the last extracted frame matches {@code expected}, else what is wrong. */
  private static String describeFrame(FrameCounts expected) {
    FrameCounts frame = lastFrame;
    if (frame == null) {
      return "no frame extracted";
    }
    return frame.equals(expected) ? "" : "expected " + expected + ", got " + frame;
  }

  /** Empty when the cow riding the ghast is drawn normally, as every rider of the ghast is. */
  private static String describeCowRider(Minecraft minecraft) {
    for (Entity entity : minecraft.level.entitiesForRendering()) {
      if (entity.getType() == EntityTypes.COW) {
        FirstPersonVisibility visibility =
            FirstPersonGhastHiding.visibility(
                minecraft, minecraft.gameRenderer.mainCamera(), entity);
        return visibility == NORMAL ? "" : "cow rider must stay normal, was " + visibility;
      }
    }
    return "no cow rider";
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
    int semiTransparent = 0;
    int semiTransparentShadows = 0;
    SubmitRecorder recorder = new SubmitRecorder();
    for (EntityRenderState entity : states) {
      int ropes = entity.leashStates == null ? 0 : entity.leashStates.size();
      if (FirstPersonGhastHiding.isSemiTransparent(entity)) {
        semiTransparent++;
        if (entity.shadowRadius > 0.0F || !entity.shadowPieces.isEmpty()) {
          semiTransparentShadows++;
        }
      }
      if (entity.entityType == EntityTypes.HAPPY_GHAST) {
        ghasts++;
        recorder.submit(entity);
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
    lastFrame =
        new FrameCounts(
            ghasts,
            boats,
            boatRopes,
            pigs,
            sheep,
            sheepRopes,
            semiTransparent,
            semiTransparentShadows,
            recorder.ghastModels,
            recorder.harnesses,
            recorder.semiTransparentHarnesses);
  }

  /** Entity render states of one frame, by kind; ropes are leash states. */
  private record FrameCounts(
      int ghasts,
      int boats,
      int boatRopes,
      int pigs,
      int sheep,
      int sheepRopes,
      int semiTransparent,
      int semiTransparentShadows,
      int ghastModels,
      int harnesses,
      int semiTransparentHarnesses) {}

  /**
   * Submits a render state through its real renderer to a collector that only counts the ghast's
   * models: harness submits are semi-transparent when drawn with {@code entity_translucent_cull}
   * and the mod's alpha. Test-only, render thread.
   */
  private static final class SubmitRecorder implements InvocationHandler {
    private static final String SUBMIT_MODEL = "submitModel";
    private static final int SUBMIT_MODEL_ARGUMENTS = 9;
    private static final int RENDER_TYPE_ARGUMENT = 3;
    private static final int TINT_ARGUMENT = 6;

    private final SubmitNodeCollector collector =
        (SubmitNodeCollector)
            Proxy.newProxyInstance(
                SubmitNodeCollector.class.getClassLoader(),
                new Class<?>[] {SubmitNodeCollector.class},
                this);

    private int ghastModels;
    private int harnesses;
    private int semiTransparentHarnesses;

    void submit(EntityRenderState state) {
      Minecraft minecraft = Minecraft.getInstance();
      CameraRenderState camera =
          minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
      minecraft
          .getEntityRenderDispatcher()
          .getRenderer(state)
          .submit(state, new PoseStack(), collector, camera);
    }

    @Override
    public @Nullable Object invoke(Object proxy, Method method, @Nullable Object[] arguments)
        throws Throwable {
      if (method.isDefault()) {
        return InvocationHandler.invokeDefault(proxy, method, arguments);
      }
      if (method.getReturnType() == OrderedSubmitNodeCollector.class) {
        return proxy;
      }
      if (arguments != null
          && method.getName().equals(SUBMIT_MODEL)
          && arguments.length == SUBMIT_MODEL_ARGUMENTS) {
        record(arguments);
      }
      return null;
    }

    private void record(Object[] arguments) {
      if (arguments[0] instanceof HappyGhastModel) {
        ghastModels++;
      } else if (arguments[0] instanceof HappyGhastHarnessModel) {
        harnesses++;
        if (arguments[RENDER_TYPE_ARGUMENT] instanceof RenderType renderType
            && renderType.pipeline() == RenderPipelines.ENTITY_TRANSLUCENT_CULL
            && arguments[TINT_ARGUMENT] instanceof Integer tint
            && ARGB.alpha(tint) == SEMI_TRANSPARENT_ALPHA) {
          semiTransparentHarnesses++;
        }
      }
    }
  }

  private static boolean isHungBoatType(EntityType<?> type) {
    return type == EntityTypes.OAK_BOAT || type == EntityTypes.BAMBOO_RAFT;
  }

  private static void mountGhastWithCargo(TestServerConnection connection) {
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
    Mob cow = spawnMob(level, EntityTypes.COW, ghast.getX(), ghast.getY(), ghast.getZ());
    if (!cow.startRiding(ghast, true, true)) {
      throw new AssertionError("cow must ride the ghast");
    }
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

  /** The client knows both hung boats, the seated pig, the leashed sheep and the cow rider. */
  private static boolean seesCargoAndCowRider(Minecraft minecraft) {
    if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof HappyGhast ghast)) {
      return false;
    }
    int hungBoats = 0;
    boolean seatedPig = false;
    boolean leashedSheep = false;
    boolean cowRider = false;
    for (Entity entity : minecraft.level.entitiesForRendering()) {
      if (entity instanceof AbstractBoat boat && boat.getLeashHolder() == ghast) {
        hungBoats++;
        seatedPig |= boat.getFirstPassenger() != null;
      } else if (entity.getType() == EntityTypes.SHEEP
          && entity instanceof Mob sheep
          && sheep.getLeashHolder() == ghast) {
        leashedSheep = true;
      } else if (entity.getType() == EntityTypes.COW && entity.getVehicle() == ghast) {
        cowRider = true;
      }
    }
    return hungBoats == HUNG_BOATS && seatedPig && leashedSheep && cowRider;
  }
}

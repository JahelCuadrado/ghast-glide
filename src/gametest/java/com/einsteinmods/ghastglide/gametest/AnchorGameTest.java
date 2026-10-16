package com.einsteinmods.ghastglide.gametest;

import com.einsteinmods.ghastglide.anchor.AnchorRest;
import com.einsteinmods.ghastglide.anchor.CallResult;
import com.einsteinmods.ghastglide.anchor.GhastAnchors;
import com.einsteinmods.ghastglide.anchor.GhastAttachments;
import com.einsteinmods.ghastglide.anchor.GhastCalls;
import com.einsteinmods.ghastglide.core.CreativeFlightModel;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.core.LeaveMode;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Anchoring a dismounted ghast and calling it back, with AI enabled. */
public final class AnchorGameTest {
  // The 8x8x8 test structure is encased in barriers, so the 4-block ghast (box x/z 0..4 here)
  // flies diagonally towards the far corner: it ends centred 6 blocks from the player (~7.8 at the
  // start), so it only flies ~1.8 blocks.
  private static final Vec3 GHAST_POSITION = new Vec3(2.0, 2.0, 2.0);
  private static final Vec3 NEAR_PLAYER_POSITION = new Vec3(7.5, 2.0, 7.5);
  // Vanilla keeps a dismounted ghast still for 10 ticks after a 60-tick load grace; later its
  // move control would fly towards a wanted position like this one (reachable inside barriers).
  private static final int WANDER_REQUEST_TICK = 80;
  private static final Vec3 WANDER_TARGET = new Vec3(3.0, 2.0, 3.0);
  private static final double FAR_CALL_OFFSET = 150.0;
  private static final int ANCHOR_CHECK_TICKS = 1200;
  private static final int BLOCKED_CHECK_TICKS = 60;
  private static final double MAX_ANCHOR_DRIFT = 0.5;
  // Beyond the 6-block stop distance by less than a wall-blocked ghast (~6.9 away) is.
  private static final double CALL_REACH = 6.5;
  private static final int WALL_X = 4;
  private static final int STRUCTURE_SIZE = 8;

  // Natural call: the ghast starts facing away from the player (yaw 135 points to -x/-z).
  private static final float FACING_AWAY_YAW = 135.0F;
  // Ramp: the spawn yaw varies; from this one the last braking step is under 0.01 blocks.
  private static final float SHORT_LAST_STEP_YAW = -3.0F;
  private static final double MAX_FLIGHT_YAW_ERROR = 30.0;
  private static final double MAX_ARRIVAL_YAW_ERROR = 15.0;
  private static final double MIN_MEASURED_STEP = 0.05;
  private static final double SPEED_CHANGE_EPSILON = 1.0E-6;
  private static final int CALL_ARRIVAL_TICKS = 150;
  private static final int FACING_HOLD_TICKS = 200;
  // Far call: 30 blocks above the usual start (~31 from the player; sky access, no barrier roof).
  private static final Vec3 HIGH_GHAST_POSITION = GHAST_POSITION.add(0.0, 30.0, 0.0);
  private static final int FAR_CALL_ARRIVAL_TICKS = 300;
  private static final double CALL_STOP_DISTANCE = 6.0;
  private static final double STOP_DISTANCE_TOLERANCE = 0.1;
  // Near call: the ghast centre starts ~2.8 blocks from the player, inside the stop distance.
  private static final Vec3 NEAR_GHAST_POSITION = new Vec3(5.5, 2.0, 5.5);
  private static final double MAX_NEAR_CALL_DISPLACEMENT = 0.01;

  // Water: sources fill the structure up to this layer; the ghast box (y 2..6) starts submerged.
  private static final int WATER_TOP_LAYER = 6;
  private static final int SURFACING_MAX_TICKS = 100;

  // Ground: dirt fills layers 0..3; the ghast box (y 2..6) starts sunk 2 blocks, sky open above.
  private static final int GROUND_TOP_LAYER = 3;

  // Hollow: stone walls at x 0 and 7 (free x 1..7, centre x 4) under a 1-block stone ceiling at
  // y 5; barriers bound z (free z 0..8). The ghast (box x 0..4, y 0..4, z 2..6) starts 1 block
  // inside the west wall and must end centred between the walls, still under the ceiling.
  private static final Vec3 HOLLOW_GHAST_POSITION = new Vec3(2.0, 0.0, 4.0);
  private static final int HOLLOW_WEST_WALL_X = 0;
  private static final int HOLLOW_EAST_WALL_X = 7;
  private static final int HOLLOW_CEILING_Y = 5;
  private static final double HOLLOW_CENTRE_X = 4.0;
  private static final double CENTRE_TOLERANCE = 0.5;
  private static final int UNSTUCK_MAX_TICKS = 100;

  // Lowest tentacle tip of the adult HappyGhastModel: 1.755 blocks below the box bottom. The risen
  // ghast must keep this clear of the ground so its tentacles are visible.
  private static final double MIN_TENTACLE_CLEARANCE = 1.75;

  // A player standing on the buried ghast's head holds it in place this long, then walks away to
  // this spot (on the ground, outside vanilla's 1-block "player above" margin around the box).
  private static final int PLAYER_ON_HEAD_TICKS = 100;
  private static final Vec3 AWAY_FROM_GHAST_POSITION = new Vec3(7.5, GROUND_TOP_LAYER + 1, 7.5);

  // Idle drift: watched this long, it must move a little, stay within the radius of where it was
  // left and keep that height.
  private static final int DRIFT_WATCH_TICKS = 1200;
  // Half of the idle actions are turns: watched longer, a glide is all but certain.
  private static final int DRIFT_MOVE_WATCH_TICKS = 2400;
  private static final double MIN_DRIFT_PATH = 0.5;
  private static final double MAX_DRIFT_RADIUS = 4.0;
  private static final double DRIFT_RADIUS_EPSILON = 1.0E-6;
  private static final double MAX_DRIFT_HEIGHT_CHANGE = 0.05;

  // Idle turns: with the player left standing on its head (where vanilla dismounts riders), the
  // ghast must turn clearly at least once, one smooth step at a time (largest step: 60 degrees
  // times the 0.08 turn rate).
  private static final double MIN_IDLE_TURN_DEGREES = 20.0;
  private static final double MAX_TURN_STEP_DEGREES = 60.0 * 0.08 + 1.0E-3;

  // Vanilla mode: vanilla's 10-tick still timeout only counts down after a 60-tick load grace.
  private static final int VANILLA_RELEASE_CHECK_TICKS = 100;

  // Saved attachments: the rest as Fabric stores it, and the field the first version used.
  private static final String ANCHOR_REST_KEY = "ghastglide:anchor_rest";
  private static final String LEAVE_MODE_FIELD = "leave_mode";
  private static final String FORMER_IDLE_DRIFT_FIELD = "idle_drift";

  // Tight enclosure: stone at x 5 and z 5 leaves the ghast (box x/z 0..4) one block of room.
  private static final int ENCLOSURE_WALL = 5;
  private static final int ENCLOSURE_WATCH_TICKS = 600;

  @GameTest(maxTicks = ANCHOR_CHECK_TICKS + 20)
  public void stillModeGhastStaysInPlace(GameTestHelper helper) {
    HappyGhast ghast = dismount(helper, LeaveMode.STILL).ghast();
    Vec3 start = ghast.position();

    helper.assertTrue(GhastAnchors.isAnchored(ghast), "dismounted ghast must be anchored");
    helper.runAfterDelay(
        WANDER_REQUEST_TICK,
        () -> {
          Vec3 target = helper.absoluteVec(WANDER_TARGET);
          ghast.getMoveControl().setWantedPosition(target.x, target.y, target.z, 1.0);
        });
    helper.onEachTick(
        () -> {
          double drift = ghast.position().distanceTo(start);
          helper.assertTrue(drift < MAX_ANCHOR_DRIFT, "anchored ghast drifted " + drift);
        });
    helper.runAfterDelay(
        ANCHOR_CHECK_TICKS,
        () -> {
          helper.assertTrue(GhastAnchors.isAnchored(ghast), "ghast must stay anchored");
          helper.succeed();
        });
  }

  @GameTest(maxTicks = DRIFT_MOVE_WATCH_TICKS + 20)
  public void idleGhastDriftsGentlyAroundWhereItWasLeft(GameTestHelper helper) {
    HappyGhast ghast = dismount(helper, LeaveMode.NATURAL).ghast();
    AnchorRest rest = ghast.getAttached(GhastAttachments.ANCHOR_REST);
    helper.assertTrue(
        rest != null && rest.leaveMode() == LeaveMode.NATURAL, "anchor must keep the natural mode");
    Vec3 origin = rest.origin();
    helper.assertTrue(origin.equals(ghast.position()), "origin must be where it was left");
    FlightRecorder recorder = new FlightRecorder(origin);

    helper.onEachTick(
        () -> {
          recorder.path += recorder.advance(ghast.position()).length();
          Vec3 offset = ghast.position().subtract(origin);
          helper.assertTrue(
              offset.horizontalDistance() <= MAX_DRIFT_RADIUS + DRIFT_RADIUS_EPSILON,
              "ghast drifted " + offset.horizontalDistance() + " blocks from its origin");
          helper.assertTrue(
              Math.abs(offset.y) <= MAX_DRIFT_HEIGHT_CHANGE, "ghast changed height by " + offset.y);
        });
    helper.runAfterDelay(
        DRIFT_MOVE_WATCH_TICKS,
        () -> {
          helper.assertTrue(
              recorder.path > MIN_DRIFT_PATH, "ghast only drifted " + recorder.path + " blocks");
          helper.assertTrue(GhastAnchors.isAnchored(ghast), "ghast must stay anchored");
          helper.succeed();
        });
  }

  @GameTest(maxTicks = DRIFT_WATCH_TICKS + 20)
  public void idleGhastTurnsSmoothlyWithPlayerOnItsHead(GameTestHelper helper) {
    Dismounted dismounted = dismount(helper, LeaveMode.NATURAL);
    HappyGhast ghast = dismounted.ghast();
    Player player = dismounted.rider();
    // As vanilla HappyGhast#getDismountLocationForPassenger: on top of the box, centred. Added as a
    // plain entity (mock players are not in the player list); no gravity so it stays there.
    player.setNoGravity(true);
    player.setPos(ghast.getX(), ghast.getBoundingBox().maxY, ghast.getZ());
    helper.assertTrue(helper.getLevel().addFreshEntity(player), "player must join the level");
    Vec3 start = ghast.position();
    float startYaw = ghast.getYRot();
    YawRecorder recorder = new YawRecorder(startYaw);

    helper.onEachTick(
        () -> {
          float yaw = ghast.getYRot();
          double step = Math.abs(Mth.wrapDegrees(yaw - recorder.lastYaw));
          helper.assertTrue(
              step <= MAX_TURN_STEP_DEGREES, "yaw jumped " + step + " degrees in one tick");
          helper.assertTrue(
              Math.abs(Mth.wrapDegrees(ghast.getYHeadRot() - yaw)) < 1.0E-3,
              "head must follow the yaw");
          recorder.lastYaw = yaw;
          recorder.maxTurn = Math.max(recorder.maxTurn, Math.abs(Mth.wrapDegrees(yaw - startYaw)));
          double moved = ghast.position().distanceTo(start);
          helper.assertTrue(moved < MAX_ANCHOR_DRIFT, "ghast slid " + moved + " under the player");
        });
    helper.runAfterDelay(
        DRIFT_WATCH_TICKS,
        () -> {
          helper.assertTrue(
              recorder.maxTurn > MIN_IDLE_TURN_DEGREES,
              "ghast only turned " + recorder.maxTurn + " degrees");
          helper.succeed();
        });
  }

  @GameTest(maxTicks = ENCLOSURE_WATCH_TICKS + 20)
  public void idleDriftNeverEntersBlocksInTightEnclosure(GameTestHelper helper) {
    for (int y = 0; y < STRUCTURE_SIZE; y++) {
      for (int i = 0; i < STRUCTURE_SIZE; i++) {
        helper.setBlock(ENCLOSURE_WALL, y, i, Blocks.STONE);
        helper.setBlock(i, y, ENCLOSURE_WALL, Blocks.STONE);
      }
    }
    HappyGhast ghast = dismount(helper, LeaveMode.NATURAL).ghast();
    helper.assertTrue(
        helper.getLevel().noBlockCollision(ghast, ghast.getBoundingBox()),
        "ghast must start free of blocks");

    helper.onEachTick(
        () ->
            helper.assertTrue(
                helper.getLevel().noBlockCollision(ghast, ghast.getBoundingBox()),
                "drifting ghast overlaps blocks at " + ghast.position()));
    helper.runAfterDelay(ENCLOSURE_WATCH_TICKS, helper::succeed);
  }

  @GameTest(maxTicks = 20)
  public void anchorSurvivesSaveAndLoad(GameTestHelper helper) {
    HappyGhast ghast = dismount(helper, LeaveMode.STILL).ghast();
    AnchorRest rest = ghast.getAttached(GhastAttachments.ANCHOR_REST);
    TagValueOutput output =
        TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
    ghast.saveWithoutId(output);
    CompoundTag saved = output.buildResult();

    HappyGhast loaded = EntityTypes.HAPPY_GHAST.create(helper.getLevel(), EntitySpawnReason.LOAD);
    helper.assertTrue(loaded != null, "ghast must be created");
    loaded.load(
        TagValueInput.create(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));

    helper.assertTrue(GhastAnchors.isAnchored(loaded), "anchor must survive save/load");
    AnchorRest loadedRest = loaded.getAttached(GhastAttachments.ANCHOR_REST);
    helper.assertTrue(rest != null && rest.equals(loadedRest), "origin and mode must load");
    helper.assertTrue(loadedRest.leaveMode() == LeaveMode.STILL, "still mode must survive");
    helper.succeed();
  }

  @GameTest(maxTicks = 20)
  public void anchorSavedWithFormerIdleDriftFlagLoadsAsStill(GameTestHelper helper) {
    HappyGhast ghast = dismount(helper, LeaveMode.NATURAL).ghast();
    TagValueOutput output =
        TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
    ghast.saveWithoutId(output);
    CompoundTag saved = output.buildResult();
    // Rewrite the saved rest as the first idle drift version did: a boolean instead of the mode.
    CompoundTag rest = savedAttachment(saved, ANCHOR_REST_KEY);
    helper.assertTrue(rest.contains(LEAVE_MODE_FIELD), "saved rest must hold the mode");
    rest.remove(LEAVE_MODE_FIELD);
    rest.putBoolean(FORMER_IDLE_DRIFT_FIELD, false);

    HappyGhast loaded = EntityTypes.HAPPY_GHAST.create(helper.getLevel(), EntitySpawnReason.LOAD);
    helper.assertTrue(loaded != null, "ghast must be created");
    loaded.load(
        TagValueInput.create(
            ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));

    AnchorRest loadedRest = loaded.getAttached(GhastAttachments.ANCHOR_REST);
    helper.assertTrue(loadedRest != null, "former rest must load");
    helper.assertTrue(loadedRest.leaveMode() == LeaveMode.STILL, "drift off must load as still");
    helper.assertTrue(loadedRest.origin().equals(ghast.position()), "origin must load");
    helper.succeed();
  }

  @GameTest(maxTicks = VANILLA_RELEASE_CHECK_TICKS + 20)
  public void vanillaModeDismountLeavesGhastToVanilla(GameTestHelper helper) {
    HappyGhast ghast = dismount(helper, LeaveMode.VANILLA).ghast();

    helper.assertFalse(GhastAnchors.isAnchored(ghast), "vanilla mode must not anchor");
    helper.assertFalse(ghast.hasAttached(GhastAttachments.ANCHOR_REST), "no rest without anchor");
    helper.runAfterDelay(
        VANILLA_RELEASE_CHECK_TICKS,
        () -> {
          helper.assertFalse(GhastAnchors.isAnchored(ghast), "ghast must stay unanchored");
          helper.assertFalse(
              ghast.isOnStillTimeout(), "vanilla still timeout must run out, not be renewed");
          helper.succeed();
        });
  }

  @GameTest(maxTicks = UNSTUCK_MAX_TICKS, skyAccess = true)
  public void vanillaModeGhastRisesOutOfGroundThenIsReleased(GameTestHelper helper) {
    fill(helper, 0, GROUND_TOP_LAYER, 0, STRUCTURE_SIZE - 1, Blocks.DIRT);
    HappyGhast ghast = dismount(helper, LeaveMode.VANILLA).ghast();
    helper.assertTrue(GhastAnchors.isAnchored(ghast), "stuck ghast must be held to get out");

    helper.succeedWhen(
        () -> {
          helper.assertTrue(
              helper.getLevel().noBlockCollision(ghast, ghast.getBoundingBox()),
              "ghast box still overlaps the ground");
          helper.assertFalse(ghast.hasAttached(GhastAttachments.UNSTUCK), "move must finish");
          helper.assertFalse(GhastAnchors.isAnchored(ghast), "freed ghast must be released");
        });
  }

  @GameTest(maxTicks = 20)
  public void ridingAgainReleasesAnchor(GameTestHelper helper) {
    HappyGhast ghast = dismount(helper).ghast();
    Player rider = helper.makeMockPlayer(GameType.SURVIVAL);

    rider.startRiding(ghast, true, false);

    helper.assertFalse(GhastAnchors.isAnchored(ghast), "riding must release the anchor");
    helper.succeed();
  }

  @GameTest(maxTicks = 200)
  public void callBringsGhastBesidePlayer(GameTestHelper helper) {
    Player player = nearbyCaller(helper, false);

    CallResult result = GhastCalls.requestCall(player, helper.getLevel());

    helper.assertTrue(result == CallResult.STARTED, "call must start, got " + result);
    succeedWhenGhastNear(helper, player);
  }

  @GameTest(maxTicks = 200)
  public void noclipCallPassesThroughWall(GameTestHelper helper) {
    Player player = nearbyCaller(helper, true);
    buildWall(helper);

    helper.assertTrue(
        GhastCalls.requestCall(player, helper.getLevel()) == CallResult.STARTED, "call must start");
    succeedWhenGhastNear(helper, player);
  }

  @GameTest(maxTicks = FAR_CALL_ARRIVAL_TICKS, skyAccess = true)
  public void farCallStopsAtStopDistance(GameTestHelper helper) {
    Dismounted dismounted = dismount(helper, HIGH_GHAST_POSITION);
    HappyGhast ghast = dismounted.ghast();
    Player player = dismounted.rider();
    player.setPos(helper.absoluteVec(NEAR_PLAYER_POSITION));

    helper.assertTrue(
        GhastCalls.requestCall(player, helper.getLevel()) == CallResult.STARTED, "call must start");
    helper.succeedWhen(
        () -> {
          helper.assertFalse(ghast.hasAttached(GhastAttachments.CALL), "call must finish");
          double distance = horizontalDistance(ghast.position(), player.position());
          helper.assertTrue(
              Math.abs(distance - CALL_STOP_DISTANCE) <= STOP_DISTANCE_TOLERANCE,
              "ghast stopped " + distance + " away");
          helper.assertTrue(GhastAnchors.isAnchored(ghast), "arrived ghast must anchor");
        });
  }

  @GameTest(maxTicks = CALL_ARRIVAL_TICKS)
  public void callWhenAlreadyNearOnlyTurnsToCaller(GameTestHelper helper) {
    Dismounted dismounted = dismount(helper, NEAR_GHAST_POSITION);
    HappyGhast ghast = dismounted.ghast();
    Player player = dismounted.rider();
    player.setPos(helper.absoluteVec(NEAR_PLAYER_POSITION));
    // Drift would move it after anchoring; only the call must keep it in place.
    ServerGlideSessions.update(
        player.getUUID(), GlideSettings.DEFAULTS.withLeaveMode(LeaveMode.STILL));
    face(ghast, FACING_AWAY_YAW);
    Vec3 start = ghast.position();

    helper.assertTrue(
        GhastCalls.requestCall(player, helper.getLevel()) == CallResult.STARTED, "call must start");
    helper.onEachTick(
        () -> {
          double moved = ghast.position().distanceTo(start);
          helper.assertTrue(moved <= MAX_NEAR_CALL_DISPLACEMENT, "near ghast moved " + moved);
        });
    helper.succeedWhen(() -> assertFacesAnchored(helper, ghast, player));
  }

  @GameTest(maxTicks = BLOCKED_CHECK_TICKS + 20)
  public void callWithoutNoclipCollidesWithWall(GameTestHelper helper) {
    Player player = nearbyCaller(helper, false);
    buildWall(helper);

    helper.assertTrue(
        GhastCalls.requestCall(player, helper.getLevel()) == CallResult.STARTED, "call must start");
    helper.runAfterDelay(
        BLOCKED_CHECK_TICKS,
        () -> {
          HappyGhast ghast = helper.findOneEntity(EntityTypes.HAPPY_GHAST);
          double distance = ghast.position().distanceTo(player.position());
          helper.assertTrue(distance > CALL_REACH, "wall should stop the ghast at " + distance);
          helper.succeed();
        });
  }

  @GameTest(maxTicks = 40)
  public void callBeyondRangeDoesNothing(GameTestHelper helper) {
    Dismounted dismounted = dismount(helper);
    HappyGhast ghast = dismounted.ghast();
    Player player = dismounted.rider();
    player.setPos(helper.absoluteVec(GHAST_POSITION.add(FAR_CALL_OFFSET, 0.0, 0.0)));
    Vec3 start = ghast.position();

    CallResult result = GhastCalls.requestCall(player, helper.getLevel());

    helper.assertTrue(result == CallResult.OUT_OF_RANGE, "far call must fail, got " + result);
    helper.runAfterDelay(
        20,
        () -> {
          double moved = ghast.position().distanceTo(start);
          helper.assertTrue(moved < MAX_ANCHOR_DRIFT, "out-of-range ghast moved " + moved);
          helper.assertTrue(GhastAnchors.isAnchored(ghast), "ghast must stay anchored");
          helper.succeed();
        });
  }

  @GameTest(maxTicks = 20)
  public void callWithoutRiddenGhastIsOutOfRange(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);

    CallResult result = GhastCalls.requestCall(player, helper.getLevel());

    helper.assertTrue(result == CallResult.OUT_OF_RANGE, "no ridden ghast, got " + result);
    helper.succeed();
  }

  @GameTest(maxTicks = CALL_ARRIVAL_TICKS)
  public void calledGhastFacesWhereItFlies(GameTestHelper helper) {
    Player player = nearbyCaller(helper, false);
    HappyGhast ghast = helper.findOneEntity(EntityTypes.HAPPY_GHAST);
    face(ghast, FACING_AWAY_YAW);
    FlightRecorder recorder = new FlightRecorder(ghast.position());

    helper.assertTrue(
        GhastCalls.requestCall(player, helper.getLevel()) == CallResult.STARTED, "call must start");
    helper.onEachTick(
        () -> {
          Vec3 step = recorder.advance(ghast.position());
          if (step.horizontalDistance() < MIN_MEASURED_STEP) {
            return;
          }
          double error = yawError(ghast.getYRot(), step.x, step.z);
          if (error <= MAX_FLIGHT_YAW_ERROR) {
            recorder.turned = true;
          } else {
            helper.assertFalse(recorder.turned, "ghast flies " + error + " degrees off its yaw");
          }
        });
    helper.succeedWhen(
        () -> {
          helper.assertTrue(recorder.turned, "ghast never faced its flight direction");
          helper.assertFalse(ghast.hasAttached(GhastAttachments.CALL), "call must finish");
        });
  }

  @GameTest(maxTicks = CALL_ARRIVAL_TICKS)
  public void calledGhastSpeedsUpAndSlowsDownGradually(GameTestHelper helper) {
    Player player = nearbyCaller(helper, false);
    HappyGhast ghast = helper.findOneEntity(EntityTypes.HAPPY_GHAST);
    face(ghast, SHORT_LAST_STEP_YAW);
    double impulse =
        CreativeFlightModel.impulse(
            CreativeFlightModel.horizontalTopSpeed(GlideSettings.DEFAULTS, false),
            GlideSettings.DEFAULTS);
    FlightRecorder recorder = new FlightRecorder(ghast.position());

    helper.assertTrue(
        GhastCalls.requestCall(player, helper.getLevel()) == CallResult.STARTED, "call must start");
    helper.onEachTick(
        () -> {
          double speed = recorder.advance(ghast.position()).length();
          double change = Math.abs(speed - recorder.lastSpeed);
          recorder.lastSpeed = speed;
          helper.assertTrue(
              change <= impulse + SPEED_CHANGE_EPSILON,
              "speed changed by " + change + " in one tick (impulse " + impulse + ")");
        });
    helper.succeedWhen(
        () -> {
          helper.assertFalse(ghast.hasAttached(GhastAttachments.CALL), "call must finish");
          helper.assertTrue(recorder.lastSpeed == 0.0, "ghast must stop");
        });
  }

  @GameTest(maxTicks = CALL_ARRIVAL_TICKS + FACING_HOLD_TICKS + 20)
  public void arrivedGhastFacesCallerAndKeepsFacing(GameTestHelper helper) {
    Player player = nearbyCaller(helper, false);
    // Drift would move it away from facing the caller exactly; that is covered elsewhere.
    ServerGlideSessions.update(
        player.getUUID(), GlideSettings.DEFAULTS.withLeaveMode(LeaveMode.STILL));
    HappyGhast ghast = helper.findOneEntity(EntityTypes.HAPPY_GHAST);
    face(ghast, FACING_AWAY_YAW);

    helper.assertTrue(
        GhastCalls.requestCall(player, helper.getLevel()) == CallResult.STARTED, "call must start");
    helper.runAfterDelay(CALL_ARRIVAL_TICKS, () -> assertFacesAnchored(helper, ghast, player));
    helper.runAfterDelay(
        CALL_ARRIVAL_TICKS + FACING_HOLD_TICKS,
        () -> {
          assertFacesAnchored(helper, ghast, player);
          helper.succeed();
        });
  }

  @GameTest(maxTicks = CALL_ARRIVAL_TICKS)
  public void calledGhastAnchorsWithCallersLeaveMode(GameTestHelper helper) {
    Player player = nearbyCaller(helper, false);
    HappyGhast ghast = helper.findOneEntity(EntityTypes.HAPPY_GHAST);
    helper.assertTrue(
        ghast.getAttached(GhastAttachments.ANCHOR_REST).leaveMode() == LeaveMode.NATURAL,
        "rider without a setting gets the default (natural)");
    ServerGlideSessions.update(
        player.getUUID(), GlideSettings.DEFAULTS.withLeaveMode(LeaveMode.STILL));

    helper.assertTrue(
        GhastCalls.requestCall(player, helper.getLevel()) == CallResult.STARTED, "call must start");
    helper.succeedWhen(
        () -> {
          helper.assertFalse(ghast.hasAttached(GhastAttachments.CALL), "call must finish");
          AnchorRest rest = ghast.getAttached(GhastAttachments.ANCHOR_REST);
          helper.assertTrue(
              rest != null && rest.leaveMode() == LeaveMode.STILL, "caller's mode must apply");
          helper.assertTrue(rest.origin().equals(ghast.position()), "origin must be arrival");
        });
  }

  @GameTest(maxTicks = CALL_ARRIVAL_TICKS)
  public void vanillaModeCallerGetsGhastReleasedOnArrival(GameTestHelper helper) {
    Player player = nearbyCaller(helper, false);
    HappyGhast ghast = helper.findOneEntity(EntityTypes.HAPPY_GHAST);
    ServerGlideSessions.update(
        player.getUUID(), GlideSettings.DEFAULTS.withLeaveMode(LeaveMode.VANILLA));

    helper.assertTrue(
        GhastCalls.requestCall(player, helper.getLevel()) == CallResult.STARTED, "call must start");
    helper.succeedWhen(
        () -> {
          helper.assertFalse(ghast.hasAttached(GhastAttachments.CALL), "call must finish");
          double distance = ghast.position().distanceTo(player.position());
          helper.assertTrue(distance < CALL_REACH, "ghast still " + distance + " away");
          helper.assertFalse(GhastAnchors.isAnchored(ghast), "arrived ghast must be released");
          helper.assertFalse(
              ghast.hasAttached(GhastAttachments.ANCHOR_REST), "released ghast keeps no rest");
        });
  }

  @GameTest(maxTicks = SURFACING_MAX_TICKS, skyAccess = true)
  public void ghastSurfacesAfterWaterDismount(GameTestHelper helper) {
    for (int y = 0; y <= WATER_TOP_LAYER; y++) {
      for (int x = 0; x < STRUCTURE_SIZE; x++) {
        for (int z = 0; z < STRUCTURE_SIZE; z++) {
          helper.setBlock(x, y, z, Blocks.WATER);
        }
      }
    }
    HappyGhast ghast = dismount(helper).ghast();
    helper.assertTrue(hasWaterInside(helper, ghast.getBoundingBox()), "ghast must start in water");

    helper.succeedWhen(
        () -> {
          helper.assertFalse(
              hasWaterInside(helper, ghast.getBoundingBox()), "ghast box still has water");
          helper.assertTrue(GhastAnchors.isAnchored(ghast), "surfaced ghast must anchor");
        });
  }

  @GameTest(maxTicks = UNSTUCK_MAX_TICKS, skyAccess = true)
  public void ghastRisesOutOfGroundAfterDismount(GameTestHelper helper) {
    fill(helper, 0, GROUND_TOP_LAYER, 0, STRUCTURE_SIZE - 1, Blocks.DIRT);
    HappyGhast ghast = dismount(helper).ghast();
    helper.assertFalse(
        helper.getLevel().noBlockCollision(ghast, ghast.getBoundingBox()),
        "ghast must start sunk in the ground");

    succeedWhenRisenAboveGround(helper, ghast);
  }

  @GameTest(maxTicks = PLAYER_ON_HEAD_TICKS + UNSTUCK_MAX_TICKS, skyAccess = true)
  public void buriedGhastRisesOnlyAfterPlayerLeavesItsHead(GameTestHelper helper) {
    fill(helper, 0, GROUND_TOP_LAYER, 0, STRUCTURE_SIZE - 1, Blocks.DIRT);
    Dismounted dismounted = dismount(helper);
    HappyGhast ghast = dismounted.ghast();
    Player player = dismounted.rider();
    // Mock players are not in level.players(); added as a plain entity, the player is found by an
    // entity search like any real player. No gravity: it must not slide off the ghast's head.
    player.setNoGravity(true);
    player.setPos(ghast.getX(), ghast.getBoundingBox().maxY, ghast.getZ());
    helper.assertTrue(helper.getLevel().addFreshEntity(player), "player must join the level");
    Vec3 start = ghast.position();

    helper.runAfterDelay(
        PLAYER_ON_HEAD_TICKS,
        () -> {
          double moved = ghast.position().distanceTo(start);
          helper.assertTrue(moved < MAX_ANCHOR_DRIFT, "ghast moved " + moved + " under player");
          helper.assertFalse(
              ghast.hasAttached(GhastAttachments.UNSTUCK), "ghast must not move yet");
          helper.assertFalse(
              helper.getLevel().noBlockCollision(ghast, ghast.getBoundingBox()),
              "ghast must still be buried while the player stands on it");
          player.setPos(helper.absoluteVec(AWAY_FROM_GHAST_POSITION));
        });
    helper.runAfterDelay(
        PLAYER_ON_HEAD_TICKS + 1, () -> succeedWhenRisenAboveGround(helper, ghast));
  }

  @GameTest(maxTicks = UNSTUCK_MAX_TICKS)
  public void ghastCentresInHollowUnderCeiling(GameTestHelper helper) {
    fill(helper, HOLLOW_CEILING_Y, HOLLOW_CEILING_Y, 0, STRUCTURE_SIZE - 1, Blocks.STONE);
    fill(helper, 0, HOLLOW_CEILING_Y - 1, HOLLOW_WEST_WALL_X, HOLLOW_WEST_WALL_X, Blocks.STONE);
    fill(helper, 0, HOLLOW_CEILING_Y - 1, HOLLOW_EAST_WALL_X, HOLLOW_EAST_WALL_X, Blocks.STONE);
    HappyGhast ghast = dismount(helper, HOLLOW_GHAST_POSITION).ghast();
    helper.assertFalse(
        helper.getLevel().noBlockCollision(ghast, ghast.getBoundingBox()),
        "ghast must start partly inside the wall");
    double centreX = helper.absoluteVec(new Vec3(HOLLOW_CENTRE_X, 0.0, 0.0)).x;

    helper.succeedWhen(
        () -> {
          helper.assertTrue(
              helper.getLevel().noBlockCollision(ghast, ghast.getBoundingBox()),
              "ghast box still overlaps blocks");
          double offCentre = Math.abs(ghast.getX() - centreX);
          helper.assertTrue(offCentre <= CENTRE_TOLERANCE, "ghast is " + offCentre + " off centre");
          helper.assertFalse(ghast.hasAttached(GhastAttachments.UNSTUCK), "move must finish");
          helper.assertTrue(GhastAnchors.isAnchored(ghast), "centred ghast must anchor");
        });
  }

  /** Box free of the ground, tentacles clear of it too, move finished and anchored. */
  private static void succeedWhenRisenAboveGround(GameTestHelper helper, HappyGhast ghast) {
    helper.succeedWhen(
        () -> {
          AABB box = ghast.getBoundingBox();
          helper.assertTrue(
              helper.getLevel().noBlockCollision(ghast, box),
              "ghast box still overlaps the ground");
          helper.assertTrue(
              helper
                  .getLevel()
                  .noBlockCollision(ghast, box.expandTowards(0.0, -MIN_TENTACLE_CLEARANCE, 0.0)),
              "ghast tentacles are still in the ground");
          helper.assertFalse(ghast.hasAttached(GhastAttachments.UNSTUCK), "move must finish");
          helper.assertTrue(GhastAnchors.isAnchored(ghast), "risen ghast must anchor");
        });
  }

  /** The saved attachment {@code key}, wherever Fabric nests its attachments in {@code saved}. */
  private static CompoundTag savedAttachment(CompoundTag saved, String key) {
    for (String name : saved.keySet()) {
      CompoundTag attachments = saved.getCompoundOrEmpty(name);
      if (attachments.contains(key)) {
        return attachments.getCompoundOrEmpty(key);
      }
    }
    return new CompoundTag();
  }

  /** Fills layers {@code minY..maxY} of columns {@code minX..maxX} across the whole z range. */
  private static void fill(
      GameTestHelper helper, int minY, int maxY, int minX, int maxX, Block block) {
    for (int y = minY; y <= maxY; y++) {
      for (int x = minX; x <= maxX; x++) {
        for (int z = 0; z < STRUCTURE_SIZE; z++) {
          helper.setBlock(x, y, z, block);
        }
      }
    }
  }

  private static void assertFacesAnchored(GameTestHelper helper, HappyGhast ghast, Player player) {
    helper.assertFalse(ghast.hasAttached(GhastAttachments.CALL), "call must have finished");
    helper.assertTrue(GhastAnchors.isAnchored(ghast), "arrived ghast must anchor");
    double error =
        yawError(ghast.getYRot(), player.getX() - ghast.getX(), player.getZ() - ghast.getZ());
    helper.assertTrue(error <= MAX_ARRIVAL_YAW_ERROR, "ghast faces " + error + " degrees off");
  }

  private static void face(HappyGhast ghast, float yaw) {
    ghast.setYRot(yaw);
    ghast.setYBodyRot(yaw);
    ghast.setYHeadRot(yaw);
  }

  private static double horizontalDistance(Vec3 from, Vec3 to) {
    return Math.hypot(to.x - from.x, to.z - from.z);
  }

  /** Absolute angle between {@code yaw} and the horizontal direction (dx, dz), in degrees. */
  private static double yawError(float yaw, double deltaX, double deltaZ) {
    float directionYaw = (float) -Math.toDegrees(Math.atan2(deltaX, deltaZ));
    return Math.abs(Mth.wrapDegrees(directionYaw - yaw));
  }

  /** True when a water fluid surface lies above the bottom of {@code box} in any block it spans. */
  private static boolean hasWaterInside(GameTestHelper helper, AABB box) {
    for (BlockPos pos :
        BlockPos.betweenClosed(
            Mth.floor(box.minX),
            Mth.floor(box.minY),
            Mth.floor(box.minZ),
            Mth.ceil(box.maxX) - 1,
            Mth.ceil(box.maxY) - 1,
            Mth.ceil(box.maxZ) - 1)) {
      FluidState fluid = helper.getLevel().getFluidState(pos);
      if (fluid.is(FluidTags.WATER)
          && pos.getY() + fluid.getHeight(helper.getLevel(), pos) > box.minY) {
        return true;
      }
    }
    return false;
  }

  private static Player nearbyCaller(GameTestHelper helper, boolean noclip) {
    Player player = dismount(helper).rider();
    player.setPos(helper.absoluteVec(NEAR_PLAYER_POSITION));
    ServerGlideSessions.update(player.getUUID(), GlideSettings.DEFAULTS.withNoclipEnabled(noclip));
    return player;
  }

  private static void buildWall(GameTestHelper helper) {
    for (int y = 1; y < STRUCTURE_SIZE; y++) {
      for (int z = 0; z < STRUCTURE_SIZE; z++) {
        helper.setBlock(WALL_X, y, z, Blocks.STONE);
      }
    }
  }

  private static void succeedWhenGhastNear(GameTestHelper helper, Player player) {
    helper.succeedWhen(
        () -> {
          HappyGhast ghast = helper.findOneEntity(EntityTypes.HAPPY_GHAST);
          double distance = ghast.position().distanceTo(player.position());
          helper.assertTrue(distance < CALL_REACH, "ghast still " + distance + " away");
          helper.assertTrue(GhastAnchors.isAnchored(ghast), "arrived ghast must anchor");
        });
  }

  /** Spawns a harnessed ghast, rides it with a mock player and dismounts. */
  private static Dismounted dismount(GameTestHelper helper) {
    return dismount(helper, GHAST_POSITION);
  }

  private static Dismounted dismount(GameTestHelper helper, Vec3 position) {
    return dismount(helper, position, rider -> {});
  }

  /** As {@link #dismount(GameTestHelper)}, by a rider whose leave mode is given. */
  private static Dismounted dismount(GameTestHelper helper, LeaveMode leaveMode) {
    return dismount(
        helper,
        GHAST_POSITION,
        rider ->
            ServerGlideSessions.update(
                rider.getUUID(), GlideSettings.DEFAULTS.withLeaveMode(leaveMode)));
  }

  private static Dismounted dismount(
      GameTestHelper helper, Vec3 position, Consumer<Player> beforeRiding) {
    HappyGhast ghast = helper.spawn(EntityTypes.HAPPY_GHAST, position);
    ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.pick(DyeColor.WHITE)));
    Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
    beforeRiding.accept(rider);
    rider.startRiding(ghast, true, false);
    helper.assertTrue(ghast.getControllingPassenger() == rider, "rider must control the ghast");
    ghast.ejectPassengers();
    return new Dismounted(ghast, rider);
  }

  private record Dismounted(HappyGhast ghast, Player rider) {}

  /** Per-tick yaw bookkeeping for the idle turn assertions. */
  private static final class YawRecorder {
    private float lastYaw;
    private double maxTurn;

    private YawRecorder(float startYaw) {
      lastYaw = startYaw;
    }
  }

  /** Per-tick displacement bookkeeping for the flight assertions. */
  private static final class FlightRecorder {
    private Vec3 lastPosition;
    private double lastSpeed;
    private double path;
    private boolean turned;

    private FlightRecorder(Vec3 start) {
      lastPosition = start;
    }

    private Vec3 advance(Vec3 position) {
      Vec3 step = position.subtract(lastPosition);
      lastPosition = position;
      return step;
    }
  }
}

package com.einsteinmods.ghastglide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CreativeFlightModelTest {
  private static final double EPSILON = 1.0E-9;
  private static final float NO_YAW = 0.0F;
  private static final int STEADY_STATE_TICKS = 2000;
  private static final double SPEED_TOLERANCE = 0.01;
  private static final double NINETY_PERCENT = 0.9;

  private static FlightInput forward(boolean sprint) {
    return new FlightInput(0.0F, 1.0F, false, false, sprint, NO_YAW);
  }

  private static GlideSettings withAcceleration(double seconds) {
    GlideSettings defaults = GlideSettings.DEFAULTS;
    return new GlideSettings(
        defaults.horizontalMultiplier(),
        defaults.verticalMultiplier(),
        defaults.sprintMultiplier(),
        seconds,
        defaults.noclipEnabled(),
        defaults.leaveMode(),
        ControlScheme.MOD,
        true,
        false,
        true,
        true);
  }

  private static Motion tick(Motion velocity, FlightInput input, GlideSettings settings) {
    return CreativeFlightModel.applyDrag(
        CreativeFlightModel.accelerate(velocity, input, settings), settings);
  }

  private static Motion fly(FlightInput input, GlideSettings settings, int ticks) {
    Motion velocity = Motion.ZERO;
    for (int tick = 0; tick < ticks; tick++) {
      velocity = tick(velocity, input, settings);
    }
    return velocity;
  }

  private static double blocksPerSecond(double blocksPerTick) {
    return blocksPerTick * CreativeFlightModel.TICKS_PER_SECOND;
  }

  private static double impulse(GlideSettings settings, double topSpeed) {
    double drag = CreativeFlightModel.drag(settings);
    return topSpeed * (1.0 - drag) / drag;
  }

  @Test
  void defaultTopSpeedIsSixBlocksPerSecond() {
    double speed =
        blocksPerSecond(fly(forward(false), GlideSettings.DEFAULTS, STEADY_STATE_TICKS).z());

    assertEquals(6.0, speed, 6.0 * SPEED_TOLERANCE);
  }

  @Test
  void defaultSprintTopSpeedIsNineBlocksPerSecond() {
    double speed =
        blocksPerSecond(fly(forward(true), GlideSettings.DEFAULTS, STEADY_STATE_TICKS).z());

    assertEquals(9.0, speed, 9.0 * SPEED_TOLERANCE);
  }

  @Test
  void defaultAccelerationReachesNinetyPercentInFortyTicks() {
    double top = CreativeFlightModel.horizontalTopSpeed(GlideSettings.DEFAULTS, false);
    Motion velocity = Motion.ZERO;
    int ticks = 0;
    while (velocity.z() < NINETY_PERCENT * top - EPSILON) {
      velocity = tick(velocity, forward(false), GlideSettings.DEFAULTS);
      ticks++;
    }

    assertEquals(40, ticks, 1);
  }

  @Test
  void accelerationDoesNotChangeTopSpeed() {
    double fast = fly(forward(true), withAcceleration(0.1), STEADY_STATE_TICKS).z();
    double slow = fly(forward(true), withAcceleration(4.0), STEADY_STATE_TICKS).z();

    double expected = CreativeFlightModel.horizontalTopSpeed(GlideSettings.DEFAULTS, true);
    assertEquals(expected, fast, 1.0E-6);
    assertEquals(expected, slow, 1.0E-6);
  }

  @Test
  void verticalTopSpeedIsCreativeAndIndependentOfAcceleration() {
    FlightInput up = new FlightInput(0.0F, 0.0F, true, false, false, NO_YAW);

    for (double seconds : new double[] {0.1, 2.0, 4.0}) {
      double speed = fly(up, withAcceleration(seconds), STEADY_STATE_TICKS).y();

      assertEquals(CreativeFlightModel.CREATIVE_VERTICAL_TOP_SPEED, speed, 1.0E-6);
    }
  }

  @Test
  void forwardAtYawZeroMovesTowardsPositiveZ() {
    GlideSettings settings = GlideSettings.DEFAULTS;
    Motion result = CreativeFlightModel.accelerate(Motion.ZERO, forward(false), settings);

    assertEquals(0.0, result.x(), EPSILON);
    assertEquals(0.0, result.y(), EPSILON);
    assertEquals(
        impulse(settings, CreativeFlightModel.horizontalTopSpeed(settings, false)),
        result.z(),
        EPSILON);
  }

  @Test
  void yawNinetyRotatesForwardTowardsNegativeX() {
    FlightInput input = new FlightInput(0.0F, 1.0F, false, false, false, 90.0F);

    Motion result = CreativeFlightModel.accelerate(Motion.ZERO, input, GlideSettings.DEFAULTS);

    assertTrue(result.x() < 0.0);
    assertEquals(0.0, result.z(), EPSILON);
  }

  @Test
  void sprintAndHorizontalMultiplierScaleHorizontalSpeed() {
    GlideSettings settings =
        new GlideSettings(
            3.0,
            1.0,
            2.0,
            2.0,
            true,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true);

    Motion result = CreativeFlightModel.accelerate(Motion.ZERO, forward(true), settings);

    double expectedTop = CreativeFlightModel.CREATIVE_HORIZONTAL_TOP_SPEED * 3.0 * 2.0;
    assertEquals(impulse(settings, expectedTop), result.z(), EPSILON);
  }

  @Test
  void diagonalInputIsNormalisedSoItIsNotFaster() {
    FlightInput input = new FlightInput(1.0F, 1.0F, false, false, false, NO_YAW);
    GlideSettings settings = GlideSettings.DEFAULTS;

    Motion result = CreativeFlightModel.accelerate(Motion.ZERO, input, settings);

    double speed = Math.hypot(result.x(), result.z());
    assertEquals(
        impulse(settings, CreativeFlightModel.horizontalTopSpeed(settings, false)), speed, EPSILON);
  }

  @Test
  void ascendAndDescendUseVerticalMultiplierAndCancelOut() {
    GlideSettings settings =
        new GlideSettings(
            1.0,
            2.0,
            2.0,
            2.0,
            true,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true);
    FlightInput up = new FlightInput(0.0F, 0.0F, true, false, false, NO_YAW);
    FlightInput both = new FlightInput(0.0F, 0.0F, true, true, false, NO_YAW);

    double expected = impulse(settings, CreativeFlightModel.CREATIVE_VERTICAL_TOP_SPEED * 2.0);
    assertEquals(expected, CreativeFlightModel.accelerate(Motion.ZERO, up, settings).y(), EPSILON);
    assertEquals(0.0, CreativeFlightModel.accelerate(Motion.ZERO, both, settings).y(), EPSILON);
  }

  @Test
  void sprintDoesNotAffectVerticalSpeedLikeCreative() {
    FlightInput up = new FlightInput(0.0F, 0.0F, true, false, true, NO_YAW);
    GlideSettings settings = GlideSettings.DEFAULTS;

    Motion result = CreativeFlightModel.accelerate(Motion.ZERO, up, settings);

    assertEquals(
        impulse(settings, CreativeFlightModel.verticalTopSpeed(settings)), result.y(), EPSILON);
  }

  @Test
  void dragLeavesTenPercentAfterAccelerationTime() {
    GlideSettings settings = GlideSettings.DEFAULTS;
    double ticks = settings.accelerationSeconds() * CreativeFlightModel.TICKS_PER_SECOND;

    Motion result = CreativeFlightModel.applyDrag(new Motion(1.0, 1.0, 1.0), settings);

    double expected =
        Math.pow(CreativeFlightModel.REMAINING_FRACTION_AFTER_ACCELERATION, 1.0 / ticks);
    assertEquals(expected, result.x(), EPSILON);
    assertEquals(expected, result.y(), EPSILON);
    assertEquals(expected, result.z(), EPSILON);
  }

  @Test
  void dragFollowsSettingsWhenTheyChange() {
    double[] secondsInOrder = {1.0, 3.0, 1.0, 3.0};

    for (double seconds : secondsInOrder) {
      double expected =
          Math.pow(
              CreativeFlightModel.REMAINING_FRACTION_AFTER_ACCELERATION,
              1.0 / (seconds * CreativeFlightModel.TICKS_PER_SECOND));
      assertEquals(expected, CreativeFlightModel.drag(withAcceleration(seconds)));
    }
  }

  private static FlightInput forwardWithPitch(float pitchDegrees) {
    return new FlightInput(0.0F, 1.0F, false, false, false, NO_YAW, pitchDegrees);
  }

  private static Motion terminalPitchSteeredVelocity(FlightInput input) {
    Motion velocity = Motion.ZERO;
    for (int tick = 0; tick < STEADY_STATE_TICKS; tick++) {
      velocity =
          CreativeFlightModel.applyDrag(
              CreativeFlightModel.acceleratePitchSteered(velocity, input, GlideSettings.DEFAULTS),
              GlideSettings.DEFAULTS);
    }
    return velocity;
  }

  @Test
  void pitchSteeredLookingStraightDownDescendsWithoutHorizontalMotion() {
    Motion result =
        CreativeFlightModel.acceleratePitchSteered(
            Motion.ZERO, forwardWithPitch(90.0F), GlideSettings.DEFAULTS);

    assertEquals(0.0, result.x(), EPSILON);
    assertEquals(0.0, result.z(), EPSILON);
    assertTrue(result.y() < 0.0);
  }

  @Test
  void pitchSteeredLookingStraightUpAscends() {
    Motion result =
        CreativeFlightModel.acceleratePitchSteered(
            Motion.ZERO, forwardWithPitch(-90.0F), GlideSettings.DEFAULTS);

    assertTrue(result.y() > 0.0);
    assertEquals(0.0, result.z(), EPSILON);
  }

  @Test
  void pitchSteeredDiveReachesHorizontalTerminalSpeed() {
    Motion velocity = terminalPitchSteeredVelocity(forwardWithPitch(90.0F));

    double horizontalTerminal =
        CreativeFlightModel.horizontalTopSpeed(GlideSettings.DEFAULTS, false);
    assertEquals(-horizontalTerminal, velocity.y(), 1.0E-6);
    assertEquals(0.0, velocity.z(), 1.0E-6);
  }

  @Test
  void pitchSteeredTerminalVelocityFollowsLookDirection() {
    Motion velocity = terminalPitchSteeredVelocity(forwardWithPitch(45.0F));

    assertEquals(velocity.z(), -velocity.y(), 1.0E-6);
  }

  @Test
  void pitchSteeredWithLevelLookEqualsCreativeVariant() {
    GlideSettings settings =
        new GlideSettings(
            2.5,
            1.5,
            3.0,
            2.0,
            false,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true);
    Motion current = new Motion(0.1, -0.2, 0.3);
    FlightInput[] inputs = {
      new FlightInput(0.0F, 1.0F, false, false, false, 37.0F),
      new FlightInput(1.0F, 1.0F, true, false, true, -120.0F),
      new FlightInput(-0.5F, -1.0F, true, false, false, 200.0F),
      new FlightInput(0.0F, 0.0F, true, false, true, NO_YAW),
    };

    for (FlightInput input : inputs) {
      Motion creative = CreativeFlightModel.accelerate(current, input, settings);
      Motion pitchSteered = CreativeFlightModel.acceleratePitchSteered(current, input, settings);
      assertEquals(creative.x(), pitchSteered.x(), EPSILON);
      assertEquals(creative.y(), pitchSteered.y(), EPSILON);
      assertEquals(creative.z(), pitchSteered.z(), EPSILON);
    }
  }

  @Test
  void pitchSteeredIgnoresSneakBecauseVanillaDismountsOnIt() {
    FlightInput sneak = new FlightInput(0.0F, 0.0F, false, true, false, NO_YAW);

    Motion result =
        CreativeFlightModel.acceleratePitchSteered(Motion.ZERO, sneak, GlideSettings.DEFAULTS);

    assertEquals(Motion.ZERO, result);
  }

  @Test
  void pitchSteeredMaxSettingsClimbStaysUnderVanillaVehicleSpeedLimit() {
    GlideSettings maxSettings =
        SettingsResolver.effective(
            new GlideSettings(
                GlideSettings.MAX_SPEED_MULTIPLIER,
                GlideSettings.MAX_SPEED_MULTIPLIER,
                GlideSettings.MAX_SPRINT_MULTIPLIER,
                GlideSettings.DEFAULT_ACCELERATION_SECONDS,
                false,
                LeaveMode.NATURAL,
                ControlScheme.MOD,
                true,
                false,
                true,
                true),
            ServerPolicy.WITHOUT_SERVER_MOD);
    FlightInput climb = new FlightInput(0.0F, 1.0F, true, false, true, NO_YAW, -90.0F);

    Motion velocity = Motion.ZERO;
    double maxObservedSpeed = 0.0;
    for (int tick = 0; tick < 200; tick++) {
      Motion moved = CreativeFlightModel.acceleratePitchSteered(velocity, climb, maxSettings);
      double speed =
          Math.sqrt(moved.x() * moved.x() + moved.y() * moved.y() + moved.z() * moved.z());
      maxObservedSpeed = Math.max(maxObservedSpeed, speed);
      velocity = CreativeFlightModel.applyDrag(moved, maxSettings);
    }

    assertTrue(maxObservedSpeed <= CreativeFlightModel.MAX_PITCH_STEERED_SPEED + EPSILON);
    assertTrue(maxObservedSpeed > CreativeFlightModel.MAX_PITCH_STEERED_SPEED - 1.0E-3);
  }

  @Test
  void pitchSteeredDefaultsAreNotClamped() {
    FlightInput climb = new FlightInput(0.0F, 1.0F, true, false, true, NO_YAW, -90.0F);

    Motion velocity = Motion.ZERO;
    for (int tick = 0; tick < STEADY_STATE_TICKS; tick++) {
      velocity =
          CreativeFlightModel.applyDrag(
              CreativeFlightModel.acceleratePitchSteered(velocity, climb, GlideSettings.DEFAULTS),
              GlideSettings.DEFAULTS);
    }

    // Unclamped steady state: look part reaches sprint top speed, jump adds vertical top speed.
    double expected =
        CreativeFlightModel.horizontalTopSpeed(GlideSettings.DEFAULTS, true)
            + CreativeFlightModel.verticalTopSpeed(GlideSettings.DEFAULTS);
    assertEquals(expected, velocity.y(), 1.0E-6);
  }
}

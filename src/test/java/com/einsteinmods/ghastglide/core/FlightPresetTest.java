package com.einsteinmods.ghastglide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FlightPresetTest {
  private static final float NO_YAW = 0.0F;
  private static final int STEADY_STATE_TICKS = 2000;
  private static final double NINETY_PERCENT = 0.9;
  private static final double EPSILON = 1.0E-9;
  private static final double VANILLA_TOLERANCE = 0.02;

  // Vanilla Happy Ghast, with the same float arithmetic as HappyGhast / LivingEntity.
  private static final double VANILLA_RIDDEN_SCALE = 3.9F * 0.05;
  private static final float VANILLA_TRAVEL_SPEED = (float) 0.05 * 5.0F / 3.0F;
  private static final double VANILLA_DRAG = 0.91F;
  private static final double VANILLA_JUMP_UP = 0.5F;

  private static final FlightInput FORWARD = input(1.0F, false, false);
  private static final FlightInput SPRINT_FORWARD = input(1.0F, false, true);
  private static final FlightInput ASCEND = input(0.0F, true, false);

  private static FlightInput input(float forward, boolean ascend, boolean sprint) {
    return new FlightInput(0.0F, forward, ascend, false, sprint, NO_YAW);
  }

  private static GlideSettings settings(FlightPreset preset) {
    return preset.applyTo(GlideSettings.DEFAULTS);
  }

  private static Motion tick(Motion velocity, FlightInput input, GlideSettings settings) {
    return CreativeFlightModel.applyDrag(
        CreativeFlightModel.accelerate(velocity, input, settings), settings);
  }

  private static Motion terminal(FlightInput input, GlideSettings settings) {
    Motion velocity = Motion.ZERO;
    for (int tick = 0; tick < STEADY_STATE_TICKS; tick++) {
      velocity = tick(velocity, input, settings);
    }
    return velocity;
  }

  private static double horizontalBlocksPerSecond(FlightPreset preset, boolean sprint) {
    return terminal(sprint ? SPRINT_FORWARD : FORWARD, settings(preset)).z()
        * CreativeFlightModel.TICKS_PER_SECOND;
  }

  private static int ticksToNinetyPercent(FlightPreset preset) {
    GlideSettings settings = settings(preset);
    double top = CreativeFlightModel.horizontalTopSpeed(settings, false);
    Motion velocity = Motion.ZERO;
    int ticks = 0;
    while (velocity.z() < NINETY_PERCENT * top - EPSILON) {
      velocity = tick(velocity, FORWARD, settings);
      ticks++;
    }
    return ticks;
  }

  /** Vanilla ridden ghast: impulse {@code input * 0.195 * 0.0833}, then move, then drag 0.91. */
  private static double vanillaTerminalSpeed(double input) {
    double impulse = input * VANILLA_RIDDEN_SCALE * VANILLA_TRAVEL_SPEED;
    double velocity = 0.0;
    for (int tick = 0; tick < STEADY_STATE_TICKS; tick++) {
      velocity = (velocity + impulse) * VANILLA_DRAG;
    }
    return velocity;
  }

  private static int vanillaTicksToNinetyPercent() {
    double top = vanillaTerminalSpeed(1.0);
    double impulse = VANILLA_RIDDEN_SCALE * VANILLA_TRAVEL_SPEED;
    double velocity = 0.0;
    int ticks = 0;
    while (velocity < NINETY_PERCENT * top - EPSILON) {
      velocity = (velocity + impulse) * VANILLA_DRAG;
      ticks++;
    }
    return ticks;
  }

  @Test
  void recommendedIsTheDefaults() {
    assertEquals(GlideSettings.DEFAULTS, settings(FlightPreset.RECOMMENDED));
    assertSame(FlightPreset.RECOMMENDED, FlightPreset.matching(GlideSettings.DEFAULTS));
  }

  @Test
  void calmIsFourAndSixBlocksPerSecondInTwoAndAHalfSeconds() {
    assertEquals(4.0, horizontalBlocksPerSecond(FlightPreset.CALM, false), 1.0E-6);
    assertEquals(6.0, horizontalBlocksPerSecond(FlightPreset.CALM, true), 1.0E-6);
    assertEquals(50, ticksToNinetyPercent(FlightPreset.CALM), 1);
  }

  @Test
  void recommendedIsSixAndNineBlocksPerSecondInTwoSeconds() {
    assertEquals(6.0, horizontalBlocksPerSecond(FlightPreset.RECOMMENDED, false), 1.0E-6);
    assertEquals(9.0, horizontalBlocksPerSecond(FlightPreset.RECOMMENDED, true), 1.0E-6);
    assertEquals(40, ticksToNinetyPercent(FlightPreset.RECOMMENDED), 1);
  }

  @Test
  void fastIsEightAndTwelveBlocksPerSecondInOneAndAHalfSeconds() {
    assertEquals(8.0, horizontalBlocksPerSecond(FlightPreset.FAST, false), 1.0E-6);
    assertEquals(12.0, horizontalBlocksPerSecond(FlightPreset.FAST, true), 1.0E-6);
    assertEquals(30, ticksToNinetyPercent(FlightPreset.FAST), 1);
  }

  @Test
  void creativeMatchesVanillaCreativeFlight() {
    GlideSettings creative = settings(FlightPreset.CREATIVE);

    assertEquals(
        CreativeFlightModel.CREATIVE_HORIZONTAL_TOP_SPEED, terminal(FORWARD, creative).z(), 1.0E-9);
    assertEquals(
        2.0 * CreativeFlightModel.CREATIVE_HORIZONTAL_TOP_SPEED,
        terminal(SPRINT_FORWARD, creative).z(),
        1.0E-9);
    assertEquals(
        CreativeFlightModel.CREATIVE_VERTICAL_TOP_SPEED, terminal(ASCEND, creative).y(), 1.0E-9);
    assertEquals(CreativeFlightModel.HORIZONTAL_DRAG, CreativeFlightModel.drag(creative), 1.0E-12);
    int ticks = ticksToNinetyPercent(FlightPreset.CREATIVE);
    assertEquals(24.5, ticks, 0.5);
  }

  @Test
  void vanillaMatchesTheRiddenHappyGhast() {
    GlideSettings vanilla = settings(FlightPreset.VANILLA);
    double expectedHorizontal = vanillaTerminalSpeed(1.0);
    double expectedVertical = vanillaTerminalSpeed(VANILLA_JUMP_UP);

    assertEquals(
        expectedHorizontal, terminal(FORWARD, vanilla).z(), expectedHorizontal * VANILLA_TOLERANCE);
    // Vanilla has no sprint boost.
    assertEquals(
        expectedHorizontal,
        terminal(SPRINT_FORWARD, vanilla).z(),
        expectedHorizontal * VANILLA_TOLERANCE);
    assertEquals(
        expectedVertical, terminal(ASCEND, vanilla).y(), expectedVertical * VANILLA_TOLERANCE);
    assertEquals(vanillaTicksToNinetyPercent(), ticksToNinetyPercent(FlightPreset.VANILLA), 1);
  }

  @Test
  void vanillaDerivedSpeedsAreDocumentedNumbers() {
    double horizontalBlocksPerSecond = horizontalBlocksPerSecond(FlightPreset.VANILLA, false);
    double verticalBlocksPerSecond =
        terminal(ASCEND, settings(FlightPreset.VANILLA)).y() * CreativeFlightModel.TICKS_PER_SECOND;

    assertEquals(3.29, horizontalBlocksPerSecond, 0.01);
    assertEquals(1.64, verticalBlocksPerSecond, 0.01);
    assertEquals(0.325, settings(FlightPreset.VANILLA).horizontalMultiplier(), 1.0E-9);
  }

  @ParameterizedTest
  @EnumSource(value = FlightPreset.class, names = "CUSTOM", mode = EnumSource.Mode.EXCLUDE)
  void everyPresetIsDetectedFromItsValuesAndSurvivesSanitizing(FlightPreset preset) {
    GlideSettings values = settings(preset);

    assertEquals(values, values.sanitized());
    assertSame(preset, FlightPreset.matching(values));
    assertSame(preset, FlightPreset.matching(values.withNoclipEnabled(false)));
    assertSame(preset, FlightPreset.matching(values.withLeaveMode(LeaveMode.VANILLA)));
  }

  @Test
  void otherValuesAreCustom() {
    GlideSettings tweaked =
        new GlideSettings(
            GlideSettings.DEFAULTS.horizontalMultiplier(),
            GlideSettings.DEFAULTS.verticalMultiplier(),
            GlideSettings.DEFAULTS.sprintMultiplier(),
            GlideSettings.DEFAULTS.accelerationSeconds() + 0.1,
            true,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true);

    assertSame(FlightPreset.CUSTOM, FlightPreset.matching(tweaked));
  }

  @Test
  void customKeepsTheCurrentValues() {
    GlideSettings current =
        new GlideSettings(
            2.0,
            3.0,
            1.2,
            0.5,
            false,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true);

    assertEquals(current, FlightPreset.CUSTOM.applyTo(current));
  }

  @Test
  void applyingAPresetKeepsNoclip() {
    GlideSettings withoutNoclip = GlideSettings.DEFAULTS.withNoclipEnabled(false);

    assertFalse(FlightPreset.FAST.applyTo(withoutNoclip).noclipEnabled());
  }

  @Test
  void applyingAPresetKeepsBoatHanging() {
    GlideSettings withoutHanging = GlideSettings.DEFAULTS.withHangBoatWithClick(false);

    assertFalse(FlightPreset.FAST.applyTo(withoutHanging).hangBoatWithClick());
  }

  @Test
  void applyingAPresetKeepsBuildThroughGhast() {
    GlideSettings building = GlideSettings.DEFAULTS.withBuildThroughGhast(true);

    assertTrue(FlightPreset.FAST.applyTo(building).buildThroughGhast());
  }

  @Test
  void applyingAPresetKeepsMiningAtNormalSpeedDisabled() {
    GlideSettings slowMining = GlideSettings.DEFAULTS.withMineAtNormalSpeed(false);

    assertFalse(FlightPreset.FAST.applyTo(slowMining).mineAtNormalSpeed());
  }

  @Test
  void applyingAPresetKeepsPassThroughEntitiesDisabled() {
    GlideSettings colliding = GlideSettings.DEFAULTS.withPassThroughEntities(false);

    assertFalse(FlightPreset.FAST.applyTo(colliding).passThroughEntities());
  }

  @Test
  void applyingAPresetKeepsLeaveMode() {
    GlideSettings still = GlideSettings.DEFAULTS.withLeaveMode(LeaveMode.STILL);

    GlideSettings applied = FlightPreset.CALM.applyTo(still);

    assertSame(LeaveMode.STILL, applied.leaveMode());
    assertSame(FlightPreset.CALM, FlightPreset.matching(applied));
  }

  @Test
  void applyingAPresetKeepsControlSchemeAndSchemeDoesNotChangeTheMatch() {
    GlideSettings vanillaControls = GlideSettings.DEFAULTS.withControlScheme(ControlScheme.VANILLA);

    GlideSettings applied = FlightPreset.FAST.applyTo(vanillaControls);

    assertSame(ControlScheme.VANILLA, applied.controlScheme());
    assertSame(FlightPreset.FAST, FlightPreset.matching(applied));
    assertSame(FlightPreset.RECOMMENDED, FlightPreset.matching(vanillaControls));
  }
}

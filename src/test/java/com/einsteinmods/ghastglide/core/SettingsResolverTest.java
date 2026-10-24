package com.einsteinmods.ghastglide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsResolverTest {
  private static final double EPSILON = 1.0E-9;

  @Test
  void unrestrictedPolicyKeepsSettings() {
    GlideSettings requested =
        new GlideSettings(
            10.0,
            10.0,
            5.0,
            3.0,
            true,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true);

    GlideSettings effective = SettingsResolver.effective(requested, ServerPolicy.UNRESTRICTED);

    assertEquals(requested, effective);
  }

  @Test
  void policyCapsHorizontalSpeedIncludingSprint() {
    GlideSettings requested =
        new GlideSettings(
            6.0,
            2.0,
            5.0,
            4.0,
            true,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true);
    ServerPolicy policy = new ServerPolicy(true, 12.0);

    GlideSettings effective = SettingsResolver.effective(requested, policy);

    assertEquals(6.0, effective.horizontalMultiplier(), EPSILON);
    assertEquals(2.0, effective.sprintMultiplier(), EPSILON);
    assertEquals(2.0, effective.verticalMultiplier(), EPSILON);
    assertEquals(4.0, effective.accelerationSeconds(), EPSILON);
  }

  @Test
  void leaveModePassesThroughAnyPolicy() {
    for (LeaveMode mode : LeaveMode.values()) {
      GlideSettings requested = GlideSettings.DEFAULTS.withLeaveMode(mode);

      assertEquals(
          mode, SettingsResolver.effective(requested, new ServerPolicy(false, 1.0)).leaveMode());
      assertEquals(
          mode, SettingsResolver.effective(requested, ServerPolicy.WITHOUT_SERVER_MOD).leaveMode());
    }
  }

  @Test
  void boatHangingPassesThroughAnyPolicy() {
    GlideSettings requested = GlideSettings.DEFAULTS.withHangBoatWithClick(false);

    assertFalse(
        SettingsResolver.effective(requested, ServerPolicy.WITHOUT_SERVER_MOD).hangBoatWithClick());
  }

  @Test
  void buildThroughGhastPassesThroughAnyPolicy() {
    GlideSettings requested = GlideSettings.DEFAULTS.withBuildThroughGhast(true);

    assertTrue(
        SettingsResolver.effective(requested, ServerPolicy.WITHOUT_SERVER_MOD).buildThroughGhast());
  }

  @Test
  void policyCanDisableNoclip() {
    GlideSettings effective =
        SettingsResolver.effective(GlideSettings.DEFAULTS, new ServerPolicy(false, 15.0));

    assertFalse(effective.noclipEnabled());
  }

  @Test
  void invalidValuesAreSanitized() {
    GlideSettings requested =
        new GlideSettings(
            Double.NaN,
            -4.0,
            99.0,
            0.0,
            true,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true);

    GlideSettings effective = SettingsResolver.effective(requested, ServerPolicy.UNRESTRICTED);

    assertEquals(GlideSettings.DEFAULTS.horizontalMultiplier(), effective.horizontalMultiplier());
    assertEquals(GlideSettings.MIN_SPEED_MULTIPLIER, effective.verticalMultiplier());
    assertEquals(GlideSettings.MAX_SPRINT_MULTIPLIER, effective.sprintMultiplier());
    assertEquals(GlideSettings.DEFAULT_ACCELERATION_SECONDS, effective.accelerationSeconds());
    assertTrue(effective.noclipEnabled());
  }

  @Test
  void withoutServerModForcesNoclipOffAndCapsSpeedLikeDedicatedDefaults() {
    GlideSettings requested =
        new GlideSettings(
            10.0,
            20.0,
            5.0,
            GlideSettings.DEFAULT_ACCELERATION_SECONDS,
            true,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true);

    GlideSettings effective =
        SettingsResolver.effective(requested, ServerPolicy.WITHOUT_SERVER_MOD);

    double cap = ServerPolicy.DEDICATED_DEFAULTS.maxSpeedMultiplier();
    assertFalse(effective.noclipEnabled());
    assertEquals(10.0, effective.horizontalMultiplier(), EPSILON);
    assertEquals(cap / 10.0, effective.sprintMultiplier(), EPSILON);
    assertEquals(GlideSettings.MAX_SPEED_MULTIPLIER, effective.verticalMultiplier(), EPSILON);
    assertTrue(effective.horizontalMultiplier() * effective.sprintMultiplier() <= cap + EPSILON);
  }
}

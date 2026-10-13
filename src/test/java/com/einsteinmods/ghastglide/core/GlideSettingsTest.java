package com.einsteinmods.ghastglide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GlideSettingsTest {

  @Test
  void sanitizedClampsOutOfRangeValuesAndReplacesNan() {
    GlideSettings result =
        new GlideSettings(50.0, -3.0, Double.NaN, 99.0, true, LeaveMode.NATURAL).sanitized();

    assertEquals(GlideSettings.MAX_SPEED_MULTIPLIER, result.horizontalMultiplier());
    assertEquals(GlideSettings.MIN_SPEED_MULTIPLIER, result.verticalMultiplier());
    assertEquals(GlideSettings.DEFAULTS.sprintMultiplier(), result.sprintMultiplier());
    assertEquals(GlideSettings.MAX_ACCELERATION_SECONDS, result.accelerationSeconds());
  }

  @Test
  void missingOrInvalidAccelerationFallsBackToDefault() {
    for (double invalid : new double[] {0.0, -1.0, Double.NaN}) {
      GlideSettings result =
          new GlideSettings(1.0, 1.0, 1.0, invalid, true, LeaveMode.NATURAL).sanitized();

      assertEquals(GlideSettings.DEFAULT_ACCELERATION_SECONDS, result.accelerationSeconds());
    }
  }

  @Test
  void defaultsAreRecommendedWithPassThroughAndNaturalLeaveMode() {
    assertSame(FlightPreset.RECOMMENDED, FlightPreset.matching(GlideSettings.DEFAULTS));
    assertTrue(GlideSettings.DEFAULTS.noclipEnabled());
    assertSame(LeaveMode.NATURAL, GlideSettings.DEFAULTS.leaveMode());
  }

  @Test
  void leaveModeSurvivesSanitizingAndMissingOneBecomesDefault() {
    for (LeaveMode mode : LeaveMode.values()) {
      assertSame(mode, GlideSettings.DEFAULTS.withLeaveMode(mode).sanitized().leaveMode());
    }
    assertSame(
        LeaveMode.DEFAULT, GlideSettings.DEFAULTS.withLeaveMode(null).sanitized().leaveMode());
  }

  @Test
  void tooShortAccelerationIsClampedToMinimum() {
    GlideSettings result =
        new GlideSettings(1.0, 1.0, 1.0, 0.01, true, LeaveMode.NATURAL).sanitized();

    assertEquals(GlideSettings.MIN_ACCELERATION_SECONDS, result.accelerationSeconds());
  }
}

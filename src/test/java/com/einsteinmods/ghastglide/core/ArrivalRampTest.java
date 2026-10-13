package com.einsteinmods.ghastglide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArrivalRampTest {
  private static final double EPSILON = 1.0E-9;
  private static final double FULL_THRUST = 1.0;
  private static final int MAX_TICKS = 10_000;
  private static final double LONG_DISTANCE = 50.0;
  private static final double SPEED_TOLERANCE = 0.01;

  private static final GlideSettings SETTINGS = GlideSettings.DEFAULTS;
  private static final double DRAG = CreativeFlightModel.drag(SETTINGS);
  private static final double TOP_SPEED = CreativeFlightModel.horizontalTopSpeed(SETTINGS, false);
  private static final double IMPULSE = CreativeFlightModel.impulse(TOP_SPEED, SETTINGS);

  @ParameterizedTest
  @ValueSource(doubles = {0.001, 0.01, 0.3, 1.0, 4.85, 17.3, LONG_DISTANCE})
  void landsExactlyOnTargetChangingSpeedByAtMostOneImpulse(double distance) {
    double remaining = distance;
    double speed = 0.0;
    double previousStep = 0.0;
    int ticks = 0;
    while (remaining > 0.0 && ticks < MAX_TICKS) {
      double step = ArrivalRamp.step(speed, remaining, FULL_THRUST, IMPULSE);
      assertTrue(step <= remaining + EPSILON, "overshoot at tick " + ticks);
      assertTrue(Math.abs(step - previousStep) <= IMPULSE + EPSILON, "jerk at tick " + ticks);
      remaining -= step;
      speed = step * DRAG;
      previousStep = step;
      ticks++;
    }
    assertEquals(0.0, remaining, EPSILON);
    assertTrue(previousStep <= IMPULSE + EPSILON, "last step must be at most one impulse");
  }

  @Test
  void cruisesAtCreativeTerminalSpeed() {
    double remaining = LONG_DISTANCE;
    double speed = 0.0;
    double fastest = 0.0;
    while (remaining > 0.0) {
      double step = ArrivalRamp.step(speed, remaining, FULL_THRUST, IMPULSE);
      fastest = Math.max(fastest, step * DRAG);
      remaining -= step;
      speed = step * DRAG;
    }
    assertEquals(TOP_SPEED, fastest, SPEED_TOLERANCE);
  }

  @Test
  void noThrustOnlyCoasts() {
    double speed = 0.1;
    assertEquals(speed, ArrivalRamp.step(speed, LONG_DISTANCE, 0.0, IMPULSE), EPSILON);
  }

  @Test
  void brakingStepDropsByOneImpulsePerTick() {
    double braking = ArrivalRamp.brakingStep(LONG_DISTANCE, IMPULSE);
    double next = ArrivalRamp.brakingStep(LONG_DISTANCE - braking, IMPULSE);
    assertEquals(braking - IMPULSE, next, EPSILON);
  }
}

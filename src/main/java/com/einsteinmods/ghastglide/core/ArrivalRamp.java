package com.einsteinmods.ghastglide.core;

/**
 * Speed along a straight path to a target for autonomous flight (calls, surfacing). Speeding up
 * follows {@link CreativeFlightModel}: the velocity left after drag plus one impulse. Slowing down
 * uses the same impulse as constant braking, timed so the last step lands exactly on the target.
 * Either way the step length changes by at most one impulse per tick.
 */
public final class ArrivalRamp {
  private ArrivalRamp() {}

  /**
   * Distance to move this tick.
   *
   * @param speed velocity left after last tick's drag, in blocks per tick
   * @param distance remaining distance to the target
   * @param thrust fraction of the impulse applied this tick, from 0 to 1
   * @param impulse full per-tick impulse ({@link CreativeFlightModel#impulse})
   */
  public static double step(double speed, double distance, double thrust, double impulse) {
    if (distance <= 0.0) {
      return 0.0;
    }
    double accelerated = speed + impulse * thrust;
    return Math.min(Math.min(accelerated, brakingStep(distance, impulse)), distance);
  }

  /**
   * Largest step from which braking by {@code impulse} per tick ends exactly on the target: the
   * steps are {@code f + (n-1)i, ..., f + i, f} with {@code 0 < f <= i}, summing to {@code
   * distance}. Following it, the next tick's braking step is exactly one impulse smaller.
   */
  static double brakingStep(double distance, double impulse) {
    if (distance <= 0.0) {
      return 0.0;
    }
    // Fewest ticks n with impulse * n(n+1)/2 >= distance.
    long ticks =
        Math.max(1L, (long) Math.ceil((Math.sqrt(1.0 + 8.0 * distance / impulse) - 1.0) / 2.0));
    while (impulse * ticks * (ticks + 1) / 2.0 < distance) {
      ticks++;
    }
    while (ticks > 1 && impulse * (ticks - 1) * ticks / 2.0 >= distance) {
      ticks--;
    }
    double finalStep = (distance - impulse * ticks * (ticks - 1) / 2.0) / ticks;
    return finalStep + impulse * (ticks - 1);
  }
}

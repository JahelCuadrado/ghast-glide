package com.einsteinmods.ghastglide.core;

/** Immutable 3D velocity in blocks per tick, free of Minecraft types so it is unit-testable. */
public record Motion(double x, double y, double z) {
  public static final Motion ZERO = new Motion(0.0, 0.0, 0.0);
}

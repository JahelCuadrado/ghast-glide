package com.einsteinmods.ghastglide.core;

/**
 * Per-player flight settings.
 *
 * @param horizontalMultiplier multiplier over creative horizontal top speed
 * @param verticalMultiplier multiplier over creative vertical top speed
 * @param sprintMultiplier horizontal top speed factor while sprinting (creative uses 2.0)
 * @param accelerationSeconds seconds needed to reach 90% of top speed (and to lose 90% of it)
 * @param noclipEnabled whether the ghast ignores block collisions
 * @param leaveMode what a ghast this player dismounts (or calls) does once left
 * @param controlScheme how the player flies: the mod's controls or vanilla's (speeds unused then)
 * @param hangBoatWithClick whether using a boat on the ridden ghast hangs it below with a lead
 * @param buildThroughGhast whether the ridden ghast (and its other passengers) never obstruct the
 *     blocks this player places while riding it
 */
public record GlideSettings(
    double horizontalMultiplier,
    double verticalMultiplier,
    double sprintMultiplier,
    double accelerationSeconds,
    boolean noclipEnabled,
    LeaveMode leaveMode,
    ControlScheme controlScheme,
    boolean hangBoatWithClick,
    boolean buildThroughGhast) {

  public static final double MIN_SPEED_MULTIPLIER = 0.1;
  public static final double MAX_SPEED_MULTIPLIER = 10.0;
  public static final double MIN_SPRINT_MULTIPLIER = 1.0;
  public static final double MAX_SPRINT_MULTIPLIER = 5.0;
  public static final double MIN_ACCELERATION_SECONDS = 0.1;
  public static final double MAX_ACCELERATION_SECONDS = 4.0;

  /** Default horizontal top speed: safe while elytra-flying players stay chunk-loaded. */
  public static final double DEFAULT_HORIZONTAL_BLOCKS_PER_SECOND = 6.0;

  /** {@code 6 / 10.11 ≈ 0.5934}: creative top speed is {@code 0.05 * 0.91 / 0.09} blocks/tick. */
  public static final double DEFAULT_HORIZONTAL_MULTIPLIER =
      DEFAULT_HORIZONTAL_BLOCKS_PER_SECOND
          / CreativeFlightModel.CREATIVE_HORIZONTAL_BLOCKS_PER_SECOND;

  /** Sprint reaches {@code 6 * 1.5 = 9} blocks/s with the default horizontal multiplier. */
  public static final double DEFAULT_SPRINT_MULTIPLIER = 1.5;

  public static final double DEFAULT_ACCELERATION_SECONDS = 2.0;

  public static final GlideSettings DEFAULTS =
      new GlideSettings(
          DEFAULT_HORIZONTAL_MULTIPLIER,
          1.0,
          DEFAULT_SPRINT_MULTIPLIER,
          DEFAULT_ACCELERATION_SECONDS,
          true,
          LeaveMode.DEFAULT,
          ControlScheme.DEFAULT,
          true,
          false);

  /**
   * Returns a copy with every value forced into its allowed range (NaN falls back to default). A
   * non-positive acceleration also falls back to default: files written before the field existed
   * deserialize it as 0. A missing leave mode or control scheme becomes the default.
   */
  public GlideSettings sanitized() {
    return new GlideSettings(
        clamp(
            horizontalMultiplier,
            MIN_SPEED_MULTIPLIER,
            MAX_SPEED_MULTIPLIER,
            DEFAULTS.horizontalMultiplier),
        clamp(
            verticalMultiplier,
            MIN_SPEED_MULTIPLIER,
            MAX_SPEED_MULTIPLIER,
            DEFAULTS.verticalMultiplier),
        clamp(
            sprintMultiplier,
            MIN_SPRINT_MULTIPLIER,
            MAX_SPRINT_MULTIPLIER,
            DEFAULTS.sprintMultiplier),
        accelerationSeconds > 0.0
            ? clamp(
                accelerationSeconds,
                MIN_ACCELERATION_SECONDS,
                MAX_ACCELERATION_SECONDS,
                DEFAULTS.accelerationSeconds)
            : DEFAULTS.accelerationSeconds,
        noclipEnabled,
        leaveMode == null ? LeaveMode.DEFAULT : leaveMode,
        controlScheme == null ? ControlScheme.DEFAULT : controlScheme,
        hangBoatWithClick,
        buildThroughGhast);
  }

  public GlideSettings withNoclipEnabled(boolean enabled) {
    return new GlideSettings(
        horizontalMultiplier,
        verticalMultiplier,
        sprintMultiplier,
        accelerationSeconds,
        enabled,
        leaveMode,
        controlScheme,
        hangBoatWithClick,
        buildThroughGhast);
  }

  public GlideSettings withLeaveMode(LeaveMode mode) {
    return new GlideSettings(
        horizontalMultiplier,
        verticalMultiplier,
        sprintMultiplier,
        accelerationSeconds,
        noclipEnabled,
        mode,
        controlScheme,
        hangBoatWithClick,
        buildThroughGhast);
  }

  public GlideSettings withControlScheme(ControlScheme scheme) {
    return new GlideSettings(
        horizontalMultiplier,
        verticalMultiplier,
        sprintMultiplier,
        accelerationSeconds,
        noclipEnabled,
        leaveMode,
        scheme,
        hangBoatWithClick,
        buildThroughGhast);
  }

  public GlideSettings withHangBoatWithClick(boolean enabled) {
    return new GlideSettings(
        horizontalMultiplier,
        verticalMultiplier,
        sprintMultiplier,
        accelerationSeconds,
        noclipEnabled,
        leaveMode,
        controlScheme,
        enabled,
        buildThroughGhast);
  }

  public GlideSettings withBuildThroughGhast(boolean enabled) {
    return new GlideSettings(
        horizontalMultiplier,
        verticalMultiplier,
        sprintMultiplier,
        accelerationSeconds,
        noclipEnabled,
        leaveMode,
        controlScheme,
        hangBoatWithClick,
        enabled);
  }

  static double clamp(double value, double min, double max, double fallback) {
    if (Double.isNaN(value)) {
      return fallback;
    }
    return Math.clamp(value, min, max);
  }
}

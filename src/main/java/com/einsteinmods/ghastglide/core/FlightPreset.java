package com.einsteinmods.ghastglide.core;

/**
 * Named sets of speed values for {@link GlideSettings}. A preset never stores anything itself: the
 * settings values are the single source of truth and {@link #matching} derives the preset from
 * them, so the network payload and the server never need it.
 *
 * <p>Speeds below are post-drag terminal speeds, the same convention as {@link
 * CreativeFlightModel}: creative horizontal is {@code 0.05 * 0.91 / 0.09 ≈ 0.5056} blocks/tick
 * (≈10.11 blocks/s) and creative vertical is {@code 0.225} blocks/tick (4.5 blocks/s).
 */
public enum FlightPreset {
  /**
   * Vanilla ridden Happy Ghast (≈3.29 blocks/s forward, ≈1.64 blocks/s up, no sprint, 1.22 s).
   *
   * <p>{@code HappyGhast#getRiddenInput} returns {@code (strafe, up, forward) * 3.9 * FLYING_SPEED}
   * with {@code FLYING_SPEED = 0.05}: forward is {@code cos(pitch)} (1 with a level look, whatever
   * the key strength), jump adds {@code 0.5} to up. {@code HappyGhast#travel} calls {@code
   * travelFlying} with {@code speed = FLYING_SPEED * 5 / 3}; {@code Entity#getInputVector} only
   * normalises inputs longer than 1 (here at most 0.2925), so the impulse per tick is {@code input
   * * speed} and the drag is 0.91 on every axis:
   *
   * <ul>
   *   <li>forward impulse {@code 1 * 0.195 * 0.08333 = 0.01625}, terminal {@code 0.01625 * 0.91 /
   *       0.09 ≈ 0.1643} blocks/tick: {@code 0.01625 / 0.05 = 0.325} times creative (whose impulse
   *       is 0.05 with the same drag);
   *   <li>jump impulse {@code 0.5 * 0.195 * 0.08333 = 0.008125}, terminal {@code ≈ 0.08215}
   *       blocks/tick: {@code ≈ 0.3651} times creative vertical (0.225);
   *   <li>drag 0.91 reaches 90% in {@code ln(0.1) / ln(0.91) ≈ 24.4} ticks ≈ 1.22 s.
   * </ul>
   *
   * Climbing by looking up (vanilla adds {@code -sin(pitch)} to up) has no equivalent: this mod
   * ignores pitch, so Space is the comparable input.
   */
  VANILLA(
      VanillaGhast.HORIZONTAL_MULTIPLIER,
      VanillaGhast.VERTICAL_MULTIPLIER,
      GlideSettings.MIN_SPRINT_MULTIPLIER,
      VanillaGhast.DRAG_ACCELERATION_SECONDS),

  /** ≈4 blocks/s, sprint x1.5 = 6 blocks/s, creative vertical, 2.5 s. */
  CALM(4.0 / CreativeFlightModel.CREATIVE_HORIZONTAL_BLOCKS_PER_SECOND, 1.0, 1.5, 2.5),

  /** The defaults: 6 blocks/s, sprint x1.5 = 9 blocks/s, creative vertical, 2.0 s. */
  RECOMMENDED(
      GlideSettings.DEFAULTS.horizontalMultiplier(),
      GlideSettings.DEFAULTS.verticalMultiplier(),
      GlideSettings.DEFAULTS.sprintMultiplier(),
      GlideSettings.DEFAULTS.accelerationSeconds()),

  /** ≈8 blocks/s, sprint x1.5 = 12 blocks/s, creative vertical, 1.5 s. */
  FAST(8.0 / CreativeFlightModel.CREATIVE_HORIZONTAL_BLOCKS_PER_SECOND, 1.0, 1.5, 1.5),

  /**
   * Exactly creative flight: ≈10.11 blocks/s, sprint x2 ≈ 20.2 blocks/s, 4.5 blocks/s vertical, and
   * the horizontal drag 0.91 (90% in ≈24.4 ticks ≈ 1.22 s). Creative vertical damping (0.6) is
   * faster; this model uses one drag for every axis.
   */
  CREATIVE(1.0, 1.0, 2.0, VanillaGhast.DRAG_ACCELERATION_SECONDS),

  /** Whatever the player set; applying it keeps the current values. */
  CUSTOM(Double.NaN, Double.NaN, Double.NaN, Double.NaN);

  /** Values closer than this count as equal (JSON round-trips doubles exactly). */
  private static final double TOLERANCE = 1.0E-9;

  private final double horizontalMultiplier;
  private final double verticalMultiplier;
  private final double sprintMultiplier;
  private final double accelerationSeconds;

  FlightPreset(
      double horizontalMultiplier,
      double verticalMultiplier,
      double sprintMultiplier,
      double accelerationSeconds) {
    this.horizontalMultiplier = horizontalMultiplier;
    this.verticalMultiplier = verticalMultiplier;
    this.sprintMultiplier = sprintMultiplier;
    this.accelerationSeconds = accelerationSeconds;
  }

  /** Returns {@code current} with this preset's speeds; {@link #CUSTOM} returns it unchanged. */
  public GlideSettings applyTo(GlideSettings current) {
    if (this == CUSTOM) {
      return current;
    }
    return new GlideSettings(
        horizontalMultiplier,
        verticalMultiplier,
        sprintMultiplier,
        accelerationSeconds,
        current.noclipEnabled(),
        current.leaveMode());
  }

  /**
   * The preset whose speeds equal the given settings (noclip and leave mode ignored), else {@link
   * #CUSTOM}.
   */
  public static FlightPreset matching(GlideSettings settings) {
    for (FlightPreset preset : values()) {
      if (preset != CUSTOM && preset.matches(settings)) {
        return preset;
      }
    }
    return CUSTOM;
  }

  private boolean matches(GlideSettings settings) {
    return Math.abs(horizontalMultiplier - settings.horizontalMultiplier()) < TOLERANCE
        && Math.abs(verticalMultiplier - settings.verticalMultiplier()) < TOLERANCE
        && Math.abs(sprintMultiplier - settings.sprintMultiplier()) < TOLERANCE
        && Math.abs(accelerationSeconds - settings.accelerationSeconds()) < TOLERANCE;
  }

  /** Vanilla ridden Happy Ghast constants; see {@link #VANILLA}. */
  static final class VanillaGhast {
    /** {@code Attributes.FLYING_SPEED} of {@code HappyGhast}. */
    static final double FLYING_SPEED = 0.05;

    /** {@code HappyGhast#getRiddenInput} input scale factor. */
    static final double RIDDEN_INPUT_SCALE = 3.9;

    /** Up input added by jump in {@code HappyGhast#getRiddenInput}. */
    static final double JUMP_UP_INPUT = 0.5;

    /** {@code HappyGhast#travel}: {@code travelFlying} speed is {@code FLYING_SPEED * 5 / 3}. */
    static final double TRAVEL_SPEED = FLYING_SPEED * 5.0 / 3.0;

    /** {@code LivingEntity#travelFlying} air drag: the same 0.91 as creative horizontal flight. */
    static final double DRAG = CreativeFlightModel.HORIZONTAL_DRAG;

    static final double FORWARD_IMPULSE = RIDDEN_INPUT_SCALE * FLYING_SPEED * TRAVEL_SPEED;

    static final double JUMP_IMPULSE = JUMP_UP_INPUT * FORWARD_IMPULSE;

    /** Post-drag terminal speeds, blocks per tick. */
    static final double HORIZONTAL_TOP_SPEED = FORWARD_IMPULSE * DRAG / (1.0 - DRAG);

    static final double VERTICAL_TOP_SPEED = JUMP_IMPULSE * DRAG / (1.0 - DRAG);

    static final double HORIZONTAL_MULTIPLIER =
        HORIZONTAL_TOP_SPEED / CreativeFlightModel.CREATIVE_HORIZONTAL_TOP_SPEED;

    static final double VERTICAL_MULTIPLIER =
        VERTICAL_TOP_SPEED / CreativeFlightModel.CREATIVE_VERTICAL_TOP_SPEED;

    /** Seconds for drag 0.91 to reach 90%: {@code ln(0.1) / ln(0.91) / 20 ≈ 1.2207}. */
    static final double DRAG_ACCELERATION_SECONDS =
        Math.log(CreativeFlightModel.REMAINING_FRACTION_AFTER_ACCELERATION)
            / Math.log(DRAG)
            / CreativeFlightModel.TICKS_PER_SECOND;

    private VanillaGhast() {}
  }
}

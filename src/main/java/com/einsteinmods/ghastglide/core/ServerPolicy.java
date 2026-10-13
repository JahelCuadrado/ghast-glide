package com.einsteinmods.ghastglide.core;

/**
 * Server-side limits applied to every player's {@link GlideSettings}.
 *
 * @param allowNoclip whether players may make their ghast pass through blocks
 * @param maxSpeedMultiplier upper bound for vertical speed and for horizontal speed including the
 *     sprint factor
 */
public record ServerPolicy(boolean allowNoclip, double maxSpeedMultiplier) {

  /** Highest reachable combined horizontal multiplier (speed x sprint). */
  public static final double MAX_COMBINED_MULTIPLIER =
      GlideSettings.MAX_SPEED_MULTIPLIER * GlideSettings.MAX_SPRINT_MULTIPLIER;

  /** Used in singleplayer / LAN host: no limits. */
  public static final ServerPolicy UNRESTRICTED = new ServerPolicy(true, MAX_COMBINED_MULTIPLIER);

  /**
   * Default for dedicated servers. Vanilla rejects vehicle moves over 10 blocks/tick ("moved too
   * quickly"); creative terminal speed is ~0.506 blocks/tick per multiplier unit, so 15 keeps a
   * safe margin.
   */
  public static final ServerPolicy DEDICATED_DEFAULTS = new ServerPolicy(true, 15.0);

  /**
   * Applied by the client on servers without the mod: vanilla replays every vehicle move with
   * normal collision (noclip would be snapped back) and keeps the "moved too quickly" limit.
   */
  public static final ServerPolicy WITHOUT_SERVER_MOD =
      new ServerPolicy(false, DEDICATED_DEFAULTS.maxSpeedMultiplier());

  public ServerPolicy sanitized() {
    return new ServerPolicy(
        allowNoclip,
        GlideSettings.clamp(
            maxSpeedMultiplier,
            GlideSettings.MIN_SPEED_MULTIPLIER,
            MAX_COMBINED_MULTIPLIER,
            DEDICATED_DEFAULTS.maxSpeedMultiplier));
  }
}

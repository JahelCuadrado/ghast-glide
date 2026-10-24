package com.einsteinmods.ghastglide.core;

/** Combines a player's requested settings with the server policy. Pure and side-agnostic. */
public final class SettingsResolver {
  private SettingsResolver() {}

  public static GlideSettings effective(GlideSettings requested, ServerPolicy policy) {
    GlideSettings settings = requested.sanitized();
    ServerPolicy limits = policy.sanitized();
    double cap = limits.maxSpeedMultiplier();
    double horizontal = Math.min(settings.horizontalMultiplier(), cap);
    double sprint =
        Math.max(
            GlideSettings.MIN_SPRINT_MULTIPLIER,
            Math.min(settings.sprintMultiplier(), cap / horizontal));
    double vertical = Math.min(settings.verticalMultiplier(), cap);
    // Acceleration only changes how fast top speed is reached, never the top speed: not capped.
    return new GlideSettings(
        horizontal,
        vertical,
        sprint,
        settings.accelerationSeconds(),
        settings.noclipEnabled() && limits.allowNoclip(),
        settings.leaveMode(),
        settings.controlScheme(),
        settings.hangBoatWithClick(),
        settings.buildThroughGhast(),
        settings.mineAtNormalSpeed(),
        settings.passThroughEntities());
  }
}

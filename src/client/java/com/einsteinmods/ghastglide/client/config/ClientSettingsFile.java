package com.einsteinmods.ghastglide.client.config;

import com.einsteinmods.ghastglide.core.ControlScheme;
import com.einsteinmods.ghastglide.core.FlightPreset;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.core.LeaveMode;
import org.jspecify.annotations.Nullable;

/**
 * On-disk shape of {@code config/ghastglide.json}.
 *
 * <p>Schema 3 adds {@code preset}, written as the preset matching the saved speeds. On load a named
 * preset (anything but {@code CUSTOM}) wins over the stored speeds, so retuning a preset in a later
 * version reaches players who chose it; edit speeds by hand only together with {@code "preset":
 * "CUSTOM"}. Older files have no preset: their speeds are used as stored, which reads as {@code
 * RECOMMENDED} when they equal the defaults and {@code CUSTOM} otherwise.
 *
 * <p>Schema 4 adds the client-only {@link ClientPreferences} ({@code hideGhastInFirstPerson});
 * older files lack it and Gson reads it as {@code false}, its default.
 *
 * <p>Schema 5 added the boolean {@code idleDriftEnabled}; schema 6 replaces it with {@code
 * leaveMode}. On load a schema 5 file maps true to {@code NATURAL} and false to {@code STILL};
 * older files, a missing or an unknown mode (Gson reads both as null) get the default ({@code
 * NATURAL}). Saving writes only {@code leaveMode} (Gson skips the null {@code idleDriftEnabled}).
 *
 * <p>Schema 7 adds {@code hangBoatWithClick}; older files lack it and Gson reads the boxed value as
 * null, which loads as {@code true}, its default.
 *
 * <p>Schema 8 adds {@code buildThroughGhast}; older files lack it and Gson reads the boxed value as
 * null, which loads as {@code false}, its default.
 *
 * <p>{@code controlScheme} needs no schema bump: older files lack it, and a missing or an unknown
 * scheme (Gson reads both as null) becomes the default ({@code MOD}).
 */
public record ClientSettingsFile(
    int schemaVersion,
    @Nullable FlightPreset preset,
    double horizontalMultiplier,
    double verticalMultiplier,
    double sprintMultiplier,
    double accelerationSeconds,
    boolean noclipEnabled,
    @Nullable Boolean idleDriftEnabled,
    @Nullable LeaveMode leaveMode,
    @Nullable ControlScheme controlScheme,
    boolean hideGhastInFirstPerson,
    @Nullable Boolean hangBoatWithClick,
    @Nullable Boolean buildThroughGhast) {
  public static final int CURRENT_SCHEMA_VERSION = 8;
  public static final String FILE_NAME = "ghastglide.json";

  /** First schema with the {@code preset} field. */
  private static final int PRESET_SCHEMA_VERSION = 3;

  /** The only schema with the {@code idleDriftEnabled} field. */
  private static final int IDLE_DRIFT_SCHEMA_VERSION = 5;

  public static final ClientSettingsFile DEFAULTS =
      from(GlideSettings.DEFAULTS, ClientPreferences.DEFAULTS);

  public static ClientSettingsFile from(GlideSettings settings, ClientPreferences preferences) {
    return new ClientSettingsFile(
        CURRENT_SCHEMA_VERSION,
        FlightPreset.matching(settings),
        settings.horizontalMultiplier(),
        settings.verticalMultiplier(),
        settings.sprintMultiplier(),
        settings.accelerationSeconds(),
        settings.noclipEnabled(),
        null,
        settings.leaveMode(),
        settings.controlScheme(),
        preferences.hideGhastInFirstPerson(),
        settings.hangBoatWithClick(),
        settings.buildThroughGhast());
  }

  /**
   * Schema 1 files lack {@code accelerationSeconds}; it reads as 0 and sanitizes to default. A
   * missing or unknown {@code preset} (Gson reads both as null) keeps the stored speeds.
   */
  public GlideSettings toSettings() {
    GlideSettings stored =
        new GlideSettings(
                horizontalMultiplier,
                verticalMultiplier,
                sprintMultiplier,
                accelerationSeconds,
                noclipEnabled,
                storedLeaveMode(),
                controlScheme,
                hangBoatWithClick == null
                    ? GlideSettings.DEFAULTS.hangBoatWithClick()
                    : hangBoatWithClick,
                buildThroughGhast == null
                    ? GlideSettings.DEFAULTS.buildThroughGhast()
                    : buildThroughGhast)
            .sanitized();
    if (schemaVersion < PRESET_SCHEMA_VERSION || preset == null) {
      return stored;
    }
    return preset.applyTo(stored);
  }

  private LeaveMode storedLeaveMode() {
    if (schemaVersion == IDLE_DRIFT_SCHEMA_VERSION && idleDriftEnabled != null) {
      return LeaveMode.fromIdleDrift(idleDriftEnabled);
    }
    if (schemaVersion > IDLE_DRIFT_SCHEMA_VERSION && leaveMode != null) {
      return leaveMode;
    }
    return LeaveMode.DEFAULT;
  }

  public ClientPreferences toPreferences() {
    return new ClientPreferences(hideGhastInFirstPerson);
  }
}

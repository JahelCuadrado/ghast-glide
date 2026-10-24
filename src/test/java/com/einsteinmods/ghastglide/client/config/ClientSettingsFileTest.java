package com.einsteinmods.ghastglide.client.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einsteinmods.ghastglide.config.JsonConfigFile;
import com.einsteinmods.ghastglide.core.ControlScheme;
import com.einsteinmods.ghastglide.core.FlightPreset;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.core.LeaveMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientSettingsFileTest {
  private static final String SCHEMA_1_JSON =
      """
      {
        "schemaVersion": 1,
        "horizontalMultiplier": 2.0,
        "verticalMultiplier": 1.5,
        "sprintMultiplier": 3.0,
        "noclipEnabled": false
      }
      """;

  private static final String SCHEMA_2_TEMPLATE =
      """
      {
        "schemaVersion": 2,
        "horizontalMultiplier": %s,
        "verticalMultiplier": %s,
        "sprintMultiplier": %s,
        "accelerationSeconds": %s,
        "noclipEnabled": true
      }
      """;

  private static final String SCHEMA_3_STALE_FAST_JSON =
      """
      {
        "schemaVersion": 3,
        "preset": "FAST",
        "horizontalMultiplier": 0.5,
        "verticalMultiplier": 0.5,
        "sprintMultiplier": 1.0,
        "accelerationSeconds": 3.0,
        "noclipEnabled": false
      }
      """;

  private static final String SCHEMA_3_UNKNOWN_PRESET_JSON =
      """
      {
        "schemaVersion": 3,
        "preset": "WARP_SPEED",
        "horizontalMultiplier": 2.0,
        "verticalMultiplier": 1.5,
        "sprintMultiplier": 3.0,
        "accelerationSeconds": 1.0,
        "noclipEnabled": true
      }
      """;

  private static final String SCHEMA_4_JSON =
      """
      {
        "schemaVersion": 4,
        "preset": "FAST",
        "horizontalMultiplier": 0.8,
        "verticalMultiplier": 1.0,
        "sprintMultiplier": 1.5,
        "accelerationSeconds": 1.5,
        "noclipEnabled": false,
        "hideGhastInFirstPerson": true
      }
      """;

  private static final String SCHEMA_8_JSON =
      """
      {
        "schemaVersion": 8,
        "preset": "RECOMMENDED",
        "horizontalMultiplier": 0.5934,
        "verticalMultiplier": 1.0,
        "sprintMultiplier": 1.5,
        "accelerationSeconds": 2.0,
        "noclipEnabled": true,
        "leaveMode": "NATURAL",
        "controlScheme": "MOD",
        "hideGhastInFirstPerson": false,
        "hangBoatWithClick": true,
        "buildThroughGhast": true
      }
      """;

  private static final String SCHEMA_9_HIDDEN_JSON =
      """
      {
        "schemaVersion": 9,
        "preset": "RECOMMENDED",
        "horizontalMultiplier": 0.5934,
        "verticalMultiplier": 1.0,
        "sprintMultiplier": 1.5,
        "accelerationSeconds": 2.0,
        "noclipEnabled": true,
        "leaveMode": "NATURAL",
        "controlScheme": "MOD",
        "hideGhastInFirstPerson": true,
        "hangBoatWithClick": true,
        "buildThroughGhast": false,
        "mineAtNormalSpeed": true,
        "passThroughEntities": true
      }
      """;

  private static final String SCHEMA_10_UNKNOWN_VISIBILITY_JSON =
      """
      {
        "schemaVersion": 10,
        "preset": "RECOMMENDED",
        "horizontalMultiplier": 0.5934,
        "verticalMultiplier": 1.0,
        "sprintMultiplier": 1.5,
        "accelerationSeconds": 2.0,
        "noclipEnabled": true,
        "hideGhastInFirstPerson": true,
        "ghastFirstPerson": "GLASS",
        "cargoFirstPerson": "SEMI_TRANSPARENT"
      }
      """;

  private static final ClientPreferences HIDDEN_BOTH =
      new ClientPreferences(FirstPersonVisibility.HIDDEN, FirstPersonVisibility.HIDDEN);

  private static final String SCHEMA_3_CALM_JSON =
      """
      {
        "schemaVersion": 3,
        "preset": "CALM",
        "horizontalMultiplier": 0.4,
        "verticalMultiplier": 1.0,
        "sprintMultiplier": 1.5,
        "accelerationSeconds": 2.5,
        "noclipEnabled": true
      }
      """;

  private static final String SCHEMA_5_TEMPLATE =
      """
      {
        "schemaVersion": 5,
        "preset": "RECOMMENDED",
        "horizontalMultiplier": 0.5934,
        "verticalMultiplier": 1.0,
        "sprintMultiplier": 1.5,
        "accelerationSeconds": 2.0,
        "noclipEnabled": true,
        "idleDriftEnabled": %s,
        "hideGhastInFirstPerson": false
      }
      """;

  private static final String SCHEMA_5_WITHOUT_DRIFT_JSON =
      """
      {
        "schemaVersion": 5,
        "preset": "RECOMMENDED",
        "noclipEnabled": true
      }
      """;

  private static final String SCHEMA_6_UNKNOWN_MODE_JSON =
      """
      {
        "schemaVersion": 6,
        "preset": "RECOMMENDED",
        "noclipEnabled": true,
        "leaveMode": "TELEPORT_HOME"
      }
      """;

  private static final String SCHEMA_6_UNKNOWN_SCHEME_JSON =
      """
      {
        "schemaVersion": 6,
        "preset": "RECOMMENDED",
        "noclipEnabled": true,
        "leaveMode": "STILL",
        "controlScheme": "JOYSTICK"
      }
      """;

  private static ClientSettingsFile load(Path directory, String json) throws IOException {
    Path path = directory.resolve(ClientSettingsFile.FILE_NAME);
    Files.writeString(path, json, StandardCharsets.UTF_8);
    return new JsonConfigFile<>(path, ClientSettingsFile.class, ClientSettingsFile.DEFAULTS).load();
  }

  private static String schemaTwo(GlideSettings settings) {
    return SCHEMA_2_TEMPLATE.formatted(
        settings.horizontalMultiplier(),
        settings.verticalMultiplier(),
        settings.sprintMultiplier(),
        settings.accelerationSeconds());
  }

  @Test
  void schemaTwoDefaultsMigrateToRecommended(@TempDir Path directory) throws IOException {
    GlideSettings settings = load(directory, schemaTwo(GlideSettings.DEFAULTS)).toSettings();

    assertEquals(GlideSettings.DEFAULTS, settings);
    ClientSettingsFile migrated = ClientSettingsFile.from(settings, ClientPreferences.DEFAULTS);
    assertSame(FlightPreset.RECOMMENDED, migrated.preset());
    assertEquals(ClientSettingsFile.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
  }

  @Test
  void schemaTwoOtherValuesMigrateToCustom(@TempDir Path directory) throws IOException {
    GlideSettings custom =
        new GlideSettings(
            2.0,
            1.5,
            3.0,
            1.0,
            true,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true);

    GlideSettings settings = load(directory, schemaTwo(custom)).toSettings();

    assertEquals(custom, settings);
    assertSame(
        FlightPreset.CUSTOM,
        ClientSettingsFile.from(settings, ClientPreferences.DEFAULTS).preset());
  }

  @Test
  void namedPresetWinsOverStoredSpeeds(@TempDir Path directory) throws IOException {
    GlideSettings settings = load(directory, SCHEMA_3_STALE_FAST_JSON).toSettings();

    assertEquals(FlightPreset.FAST.applyTo(settings), settings);
    assertSame(FlightPreset.FAST, FlightPreset.matching(settings));
    assertFalse(settings.noclipEnabled());
  }

  @Test
  void unknownPresetKeepsStoredSpeeds(@TempDir Path directory) throws IOException {
    GlideSettings settings = load(directory, SCHEMA_3_UNKNOWN_PRESET_JSON).toSettings();

    assertEquals(
        new GlideSettings(
            2.0,
            1.5,
            3.0,
            1.0,
            true,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true),
        settings);
  }

  @Test
  void schemaOneFileLoadsWithDefaultAcceleration(@TempDir Path directory) throws IOException {
    Path path = directory.resolve(ClientSettingsFile.FILE_NAME);
    Files.writeString(path, SCHEMA_1_JSON, StandardCharsets.UTF_8);

    GlideSettings settings =
        new JsonConfigFile<>(path, ClientSettingsFile.class, ClientSettingsFile.DEFAULTS)
            .load()
            .toSettings();

    assertEquals(
        new GlideSettings(
            2.0,
            1.5,
            3.0,
            2.0,
            false,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true),
        settings);
    assertEquals(GlideSettings.DEFAULT_ACCELERATION_SECONDS, settings.accelerationSeconds());
  }

  @Test
  void roundTripKeepsAcceleration(@TempDir Path directory) {
    Path path = directory.resolve(ClientSettingsFile.FILE_NAME);
    JsonConfigFile<ClientSettingsFile> file =
        new JsonConfigFile<>(path, ClientSettingsFile.class, ClientSettingsFile.DEFAULTS);
    GlideSettings saved =
        new GlideSettings(
            1.0,
            1.0,
            1.5,
            0.7,
            true,
            LeaveMode.NATURAL,
            ControlScheme.MOD,
            true,
            false,
            true,
            true);

    file.save(ClientSettingsFile.from(saved, ClientPreferences.DEFAULTS));

    assertEquals(saved, file.load().toSettings());
  }

  @Test
  void roundTripKeepsEveryPreset(@TempDir Path directory) {
    Path path = directory.resolve(ClientSettingsFile.FILE_NAME);
    JsonConfigFile<ClientSettingsFile> file =
        new JsonConfigFile<>(path, ClientSettingsFile.class, ClientSettingsFile.DEFAULTS);

    for (FlightPreset preset : FlightPreset.values()) {
      GlideSettings saved =
          preset.applyTo(
              new GlideSettings(
                  2.0,
                  1.5,
                  3.0,
                  1.0,
                  false,
                  LeaveMode.NATURAL,
                  ControlScheme.MOD,
                  true,
                  false,
                  true,
                  true));
      file.save(ClientSettingsFile.from(saved, ClientPreferences.DEFAULTS));

      ClientSettingsFile loaded = file.load();
      assertEquals(saved, loaded.toSettings());
      assertSame(preset, loaded.preset());
    }
  }

  @Test
  void schemaFourMigratesToNaturalLeaveMode(@TempDir Path directory) throws IOException {
    ClientSettingsFile loaded = load(directory, SCHEMA_4_JSON);

    GlideSettings settings = loaded.toSettings();
    assertSame(LeaveMode.NATURAL, settings.leaveMode());
    assertFalse(settings.noclipEnabled());
    assertSame(FlightPreset.FAST, FlightPreset.matching(settings));
    assertEquals(HIDDEN_BOTH, loaded.toPreferences());
    ClientSettingsFile migrated = ClientSettingsFile.from(settings, loaded.toPreferences());
    assertEquals(ClientSettingsFile.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
    assertSame(LeaveMode.NATURAL, migrated.leaveMode());
    assertNull(migrated.idleDriftEnabled());
  }

  @Test
  void schemaFiveIdleDriftMigratesToLeaveMode(@TempDir Path directory) throws IOException {
    assertSame(
        LeaveMode.NATURAL,
        load(directory, SCHEMA_5_TEMPLATE.formatted(true)).toSettings().leaveMode());
    assertSame(
        LeaveMode.STILL,
        load(directory, SCHEMA_5_TEMPLATE.formatted(false)).toSettings().leaveMode());
    assertSame(
        LeaveMode.NATURAL, load(directory, SCHEMA_5_WITHOUT_DRIFT_JSON).toSettings().leaveMode());
  }

  @Test
  void unknownLeaveModeBecomesNatural(@TempDir Path directory) throws IOException {
    assertSame(
        LeaveMode.NATURAL, load(directory, SCHEMA_6_UNKNOWN_MODE_JSON).toSettings().leaveMode());
  }

  @Test
  void missingBoatHangingLoadsEnabled(@TempDir Path directory) throws IOException {
    assertTrue(load(directory, SCHEMA_6_UNKNOWN_MODE_JSON).toSettings().hangBoatWithClick());
  }

  @Test
  void roundTripKeepsBoatHangingDisabled(@TempDir Path directory) {
    Path path = directory.resolve(ClientSettingsFile.FILE_NAME);
    JsonConfigFile<ClientSettingsFile> file =
        new JsonConfigFile<>(path, ClientSettingsFile.class, ClientSettingsFile.DEFAULTS);
    GlideSettings saved = GlideSettings.DEFAULTS.withHangBoatWithClick(false);

    file.save(ClientSettingsFile.from(saved, ClientPreferences.DEFAULTS));

    assertEquals(saved, file.load().toSettings());
  }

  @Test
  void missingBuildThroughGhastLoadsDisabled(@TempDir Path directory) throws IOException {
    assertFalse(load(directory, SCHEMA_6_UNKNOWN_MODE_JSON).toSettings().buildThroughGhast());
  }

  @Test
  void roundTripKeepsBuildThroughGhastEnabled(@TempDir Path directory) {
    Path path = directory.resolve(ClientSettingsFile.FILE_NAME);
    JsonConfigFile<ClientSettingsFile> file =
        new JsonConfigFile<>(path, ClientSettingsFile.class, ClientSettingsFile.DEFAULTS);
    GlideSettings saved = GlideSettings.DEFAULTS.withBuildThroughGhast(true);

    file.save(ClientSettingsFile.from(saved, ClientPreferences.DEFAULTS));

    assertEquals(saved, file.load().toSettings());
  }

  @Test
  void schemaEightFileLoadsMiningAndPassingThroughEntities(@TempDir Path directory)
      throws IOException {
    GlideSettings settings = load(directory, SCHEMA_8_JSON).toSettings();

    assertTrue(settings.mineAtNormalSpeed());
    assertTrue(settings.passThroughEntities());
    assertTrue(settings.buildThroughGhast());
  }

  @Test
  void roundTripKeepsMiningAtNormalSpeedDisabled(@TempDir Path directory) {
    Path path = directory.resolve(ClientSettingsFile.FILE_NAME);
    JsonConfigFile<ClientSettingsFile> file =
        new JsonConfigFile<>(path, ClientSettingsFile.class, ClientSettingsFile.DEFAULTS);
    GlideSettings saved = GlideSettings.DEFAULTS.withMineAtNormalSpeed(false);

    file.save(ClientSettingsFile.from(saved, ClientPreferences.DEFAULTS));

    assertEquals(saved, file.load().toSettings());
  }

  @Test
  void roundTripKeepsPassThroughEntitiesDisabled(@TempDir Path directory) {
    Path path = directory.resolve(ClientSettingsFile.FILE_NAME);
    JsonConfigFile<ClientSettingsFile> file =
        new JsonConfigFile<>(path, ClientSettingsFile.class, ClientSettingsFile.DEFAULTS);
    GlideSettings saved = GlideSettings.DEFAULTS.withPassThroughEntities(false);

    file.save(ClientSettingsFile.from(saved, ClientPreferences.DEFAULTS));

    assertEquals(saved, file.load().toSettings());
  }

  @Test
  void roundTripKeepsEveryLeaveMode(@TempDir Path directory) throws IOException {
    Path path = directory.resolve(ClientSettingsFile.FILE_NAME);
    JsonConfigFile<ClientSettingsFile> file =
        new JsonConfigFile<>(path, ClientSettingsFile.class, ClientSettingsFile.DEFAULTS);

    for (LeaveMode mode : LeaveMode.values()) {
      GlideSettings saved = GlideSettings.DEFAULTS.withLeaveMode(mode);
      file.save(ClientSettingsFile.from(saved, ClientPreferences.DEFAULTS));

      GlideSettings loaded = file.load().toSettings();
      assertEquals(saved, loaded);
      assertSame(FlightPreset.RECOMMENDED, FlightPreset.matching(loaded));
      assertFalse(
          Files.readString(path, StandardCharsets.UTF_8).contains("idleDriftEnabled"),
          "the former boolean must not be written");
    }
  }

  @Test
  void filesWithoutControlSchemeLoadModControls(@TempDir Path directory) throws IOException {
    assertSame(ControlScheme.MOD, load(directory, SCHEMA_4_JSON).toSettings().controlScheme());
    assertSame(
        ControlScheme.MOD,
        load(directory, SCHEMA_6_UNKNOWN_MODE_JSON).toSettings().controlScheme());
  }

  @Test
  void unknownControlSchemeBecomesModControls(@TempDir Path directory) throws IOException {
    GlideSettings settings = load(directory, SCHEMA_6_UNKNOWN_SCHEME_JSON).toSettings();

    assertSame(ControlScheme.MOD, settings.controlScheme());
    assertSame(LeaveMode.STILL, settings.leaveMode());
  }

  @Test
  void roundTripKeepsEveryControlScheme(@TempDir Path directory) {
    Path path = directory.resolve(ClientSettingsFile.FILE_NAME);
    JsonConfigFile<ClientSettingsFile> file =
        new JsonConfigFile<>(path, ClientSettingsFile.class, ClientSettingsFile.DEFAULTS);

    for (ControlScheme scheme : ControlScheme.values()) {
      GlideSettings saved =
          FlightPreset.FAST.applyTo(GlideSettings.DEFAULTS.withControlScheme(scheme));
      file.save(ClientSettingsFile.from(saved, ClientPreferences.DEFAULTS));

      ClientSettingsFile loaded = file.load();
      assertEquals(saved, loaded.toSettings());
      assertSame(FlightPreset.FAST, loaded.preset());
    }
  }

  @Test
  void defaultsAreRecommendedPassThroughNaturalAndGhastShown() {
    GlideSettings settings = ClientSettingsFile.DEFAULTS.toSettings();

    assertSame(FlightPreset.RECOMMENDED, ClientSettingsFile.DEFAULTS.preset());
    assertSame(FlightPreset.RECOMMENDED, FlightPreset.matching(settings));
    assertTrue(settings.noclipEnabled());
    assertSame(LeaveMode.NATURAL, settings.leaveMode());
    assertEquals(ClientPreferences.DEFAULTS, ClientSettingsFile.DEFAULTS.toPreferences());
  }

  @Test
  void schemaThreeMigratesWithGhastShown(@TempDir Path directory) throws IOException {
    ClientSettingsFile loaded = load(directory, SCHEMA_3_CALM_JSON);

    assertEquals(ClientPreferences.DEFAULTS, loaded.toPreferences());
    GlideSettings settings = loaded.toSettings();
    assertSame(FlightPreset.CALM, FlightPreset.matching(settings));
    assertTrue(settings.noclipEnabled());
    ClientSettingsFile migrated = ClientSettingsFile.from(settings, loaded.toPreferences());
    assertEquals(ClientSettingsFile.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
  }

  @Test
  void roundTripKeepsClientPreferences(@TempDir Path directory) {
    Path path = directory.resolve(ClientSettingsFile.FILE_NAME);
    JsonConfigFile<ClientSettingsFile> file =
        new JsonConfigFile<>(path, ClientSettingsFile.class, ClientSettingsFile.DEFAULTS);
    for (FirstPersonVisibility ghast : FirstPersonVisibility.values()) {
      for (FirstPersonVisibility cargo : FirstPersonVisibility.values()) {
        ClientPreferences preferences = new ClientPreferences(ghast, cargo);

        file.save(ClientSettingsFile.from(GlideSettings.DEFAULTS, preferences));

        ClientSettingsFile loaded = file.load();
        assertEquals(preferences, loaded.toPreferences());
        assertNull(loaded.hideGhastInFirstPerson());
        assertEquals(GlideSettings.DEFAULTS, loaded.toSettings());
      }
    }
  }

  @Test
  void defaultsShowTheGhastAndCargo() {
    ClientPreferences preferences = ClientSettingsFile.DEFAULTS.toPreferences();

    assertSame(FirstPersonVisibility.NORMAL, preferences.ghastFirstPerson());
    assertSame(FirstPersonVisibility.NORMAL, preferences.cargoFirstPerson());
    assertFalse(preferences.changesFirstPerson());
  }

  @Test
  void schemaNineHiddenGhastMigratesToHiddenGhastAndCargo(@TempDir Path directory)
      throws IOException {
    ClientSettingsFile loaded = load(directory, SCHEMA_9_HIDDEN_JSON);

    assertEquals(HIDDEN_BOTH, loaded.toPreferences());
    ClientSettingsFile migrated = ClientSettingsFile.from(loaded.toSettings(), HIDDEN_BOTH);
    assertNull(migrated.hideGhastInFirstPerson());
    assertSame(FirstPersonVisibility.HIDDEN, migrated.ghastFirstPerson());
  }

  @Test
  void schemaNineShownGhastMigratesToNormal(@TempDir Path directory) throws IOException {
    assertEquals(ClientPreferences.DEFAULTS, load(directory, SCHEMA_8_JSON).toPreferences());
  }

  @Test
  void unknownOrMissingVisibilityLoadsNormal(@TempDir Path directory) throws IOException {
    ClientPreferences preferences =
        load(directory, SCHEMA_10_UNKNOWN_VISIBILITY_JSON).toPreferences();

    assertEquals(
        new ClientPreferences(FirstPersonVisibility.NORMAL, FirstPersonVisibility.SEMI_TRANSPARENT),
        preferences);
  }
}

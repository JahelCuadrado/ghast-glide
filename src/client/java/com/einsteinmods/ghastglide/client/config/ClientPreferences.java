package com.einsteinmods.ghastglide.client.config;

/**
 * Settings that only affect this client's view. Kept apart from {@code GlideSettings}, which holds
 * exactly what the server needs ({@code GlideSettingsPayload}), so these are never sent.
 *
 * @param ghastFirstPerson how the Happy Ghast the local player rides is drawn while the camera is
 *     in first person
 * @param cargoFirstPerson how that ghast's cargo is drawn in first person: boats leashed to it,
 *     whatever sits in those boats and mobs leashed to it (its other riders are always drawn)
 */
public record ClientPreferences(
    FirstPersonVisibility ghastFirstPerson, FirstPersonVisibility cargoFirstPerson) {
  public static final ClientPreferences DEFAULTS =
      new ClientPreferences(FirstPersonVisibility.DEFAULT, FirstPersonVisibility.DEFAULT);

  /** True when either the ghast or its cargo is drawn differently in first person. */
  public boolean changesFirstPerson() {
    return ghastFirstPerson != FirstPersonVisibility.NORMAL
        || cargoFirstPerson != FirstPersonVisibility.NORMAL;
  }
}

package com.einsteinmods.ghastglide.client.config;

/**
 * Settings that only affect this client's view. Kept apart from {@code GlideSettings}, which holds
 * exactly what the server needs ({@code GlideSettingsPayload}), so these are never sent.
 *
 * @param hideGhastInFirstPerson do not render the Happy Ghast the local player rides while the
 *     camera is in first person
 */
public record ClientPreferences(boolean hideGhastInFirstPerson) {
  public static final ClientPreferences DEFAULTS = new ClientPreferences(false);
}

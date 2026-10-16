package com.einsteinmods.ghastglide.core;

/**
 * How a player flies a Happy Ghast. Stable ids are what the network payload sends; names are what
 * files store.
 */
public enum ControlScheme {
  /** Creative-flight controls: sneak descends, the mod's key dismounts, mod speeds. */
  MOD(0),
  /** Vanilla controls: look to climb or descend, vanilla speeds, sneak dismounts. */
  VANILLA(1);

  public static final ControlScheme DEFAULT = MOD;

  private final int id;

  ControlScheme(int id) {
    this.id = id;
  }

  public int id() {
    return id;
  }

  /** The scheme with {@code id}, or {@link #DEFAULT} for any unknown id. */
  public static ControlScheme fromId(int id) {
    for (ControlScheme scheme : values()) {
      if (scheme.id == id) {
        return scheme;
      }
    }
    return DEFAULT;
  }
}

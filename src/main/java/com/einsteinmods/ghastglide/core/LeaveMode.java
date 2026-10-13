package com.einsteinmods.ghastglide.core;

/**
 * What a Happy Ghast does once its player dismounts (or after it answers that player's call).
 * Stable ids are what the network payload sends; names are what files store.
 */
public enum LeaveMode {
  /** Not anchored: vanilla AI takes over (after getting unstuck, if it was left inside blocks). */
  VANILLA(0),
  /** Anchored perfectly still where it was left. */
  STILL(1),
  /** Anchored, drifting gently around where it was left. */
  NATURAL(2);

  public static final LeaveMode DEFAULT = NATURAL;

  private final int id;

  LeaveMode(int id) {
    this.id = id;
  }

  public int id() {
    return id;
  }

  /** The mode with {@code id}, or {@link #DEFAULT} for any unknown id. */
  public static LeaveMode fromId(int id) {
    for (LeaveMode mode : values()) {
      if (mode.id == id) {
        return mode;
      }
    }
    return DEFAULT;
  }

  /** The mode named {@code name} (exact enum name), or {@link #DEFAULT} for any other text. */
  public static LeaveMode fromName(String name) {
    for (LeaveMode mode : values()) {
      if (mode.name().equals(name)) {
        return mode;
      }
    }
    return DEFAULT;
  }

  /** Migration of the former on/off idle drift setting. */
  public static LeaveMode fromIdleDrift(boolean idleDrift) {
    return idleDrift ? NATURAL : STILL;
  }
}

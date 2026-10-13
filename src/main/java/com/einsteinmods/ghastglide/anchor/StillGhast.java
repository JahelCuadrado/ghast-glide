package com.einsteinmods.ghastglide.anchor;

/** Implemented on HappyGhast by mixin: access to vanilla's private "stay still" timeout. */
public interface StillGhast {
  /** Sets vanilla {@code HappyGhast#serverStillTimeout} (synced to clients as STAYS_STILL). */
  void ghastglide$setStillTimeout(int ticks);
}

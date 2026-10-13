package com.einsteinmods.ghastglide.glide;

/**
 * Implemented on HappyGhast by mixin. A phasing ghast moved through blocks and is immune to
 * suffocation until it is clear of them, even after the rider dismounts.
 */
public interface PhasingGhast {
  boolean ghastglide$isPhasing();

  void ghastglide$setPhasing(boolean phasing);
}

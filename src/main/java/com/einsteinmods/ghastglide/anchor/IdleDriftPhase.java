package com.einsteinmods.ghastglide.anchor;

import net.minecraft.world.phys.Vec3;

/**
 * What an idle anchored ghast is doing ({@link GhastIdleDrift}). Every phase ends at {@code
 * endTick} (level game time) at the latest, so a pause costs one comparison per tick.
 */
public sealed interface IdleDriftPhase {
  long endTick();

  /** Floating still until {@code endTick}. */
  record Pause(long endTick) implements IdleDriftPhase {}

  /** Gliding to {@code target}; {@code speed} is the speed left after last tick's drag. */
  record Glide(Vec3 target, double speed, long endTick) implements IdleDriftPhase {
    Glide advanced(double nextSpeed) {
      return new Glide(target, nextSpeed, endTick);
    }
  }

  /** Turning in place to {@code targetYaw}. */
  record Turn(float targetYaw, long endTick) implements IdleDriftPhase {}
}

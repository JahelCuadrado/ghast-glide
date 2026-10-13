package com.einsteinmods.ghastglide.anchor;

import net.minecraft.world.phys.Vec3;

/**
 * An ongoing move of a dismounted ghast out of blocks or water: the ghast flies to {@code target}
 * (its position, i.e. box bottom centre). {@code speed} is the speed left after last tick's drag.
 */
public record UnstuckMove(Vec3 target, int remainingTicks, double speed) {
  public UnstuckMove advanced(Vec3 nextTarget, double nextSpeed) {
    return new UnstuckMove(nextTarget, remainingTicks - 1, nextSpeed);
  }
}

package com.einsteinmods.ghastglide.anchor;

import com.einsteinmods.ghastglide.core.LeaveMode;
import net.minecraft.world.entity.player.Player;

/**
 * An ongoing call: the ghast flies to {@code caller}. {@code noclip} and {@code leaveMode} are the
 * caller's effective settings when the call started ({@code leaveMode} applies once the ghast
 * arrives); {@code alreadyNear} means the ghast was already near the caller at that moment, so it
 * only turns to face them; {@code speed} is the flight speed left after last tick's drag.
 */
public record GhastCall(
    Player caller,
    boolean noclip,
    LeaveMode leaveMode,
    boolean alreadyNear,
    int remainingTicks,
    double speed) {
  public GhastCall advanced(double nextSpeed) {
    return new GhastCall(caller, noclip, leaveMode, alreadyNear, remainingTicks - 1, nextSpeed);
  }
}

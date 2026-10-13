package com.einsteinmods.ghastglide.anchor;

import com.einsteinmods.ghastglide.core.LeaveMode;
import net.minecraft.world.entity.player.Player;

/**
 * An ongoing call: the ghast flies to {@code caller}. {@code noclip} and {@code leaveMode} are the
 * caller's effective settings when the call started ({@code leaveMode} applies once the ghast
 * arrives); {@code speed} is the flight speed left after last tick's drag.
 */
public record GhastCall(
    Player caller, boolean noclip, LeaveMode leaveMode, int remainingTicks, double speed) {
  public GhastCall advanced(double nextSpeed) {
    return new GhastCall(caller, noclip, leaveMode, remainingTicks - 1, nextSpeed);
  }
}

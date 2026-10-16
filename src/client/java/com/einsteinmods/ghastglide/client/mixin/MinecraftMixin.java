package com.einsteinmods.ghastglide.client.mixin;

import com.einsteinmods.ghastglide.client.RiddenGhastTarget;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * While {@code Minecraft#startUseItem} runs, aims at the ridden Happy Ghast when {@link
 * RiddenGhastTarget} says the click hangs a boat, then restores the picked hit. Vanilla's own
 * entity branch then does the rest (world border, range, {@code MultiPlayerGameMode#interact}, the
 * interact packet and the hand swing). Cost: one {@code instanceof} on the vehicle per use-key
 * press; nothing per frame or tick.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
  @Shadow public @Nullable HitResult hitResult;
  @Shadow public @Nullable LocalPlayer player;

  @WrapMethod(method = "startUseItem")
  private void ghastglide$targetRiddenGhast(Operation<Void> original) {
    EntityHitResult ghastTarget =
        player == null ? null : RiddenGhastTarget.boatTarget(player, hitResult);
    if (ghastTarget == null) {
      original.call();
      return;
    }
    HitResult pickedHit = hitResult;
    hitResult = ghastTarget;
    try {
      original.call();
    } finally {
      hitResult = pickedHit;
    }
  }
}

package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.glide.BoatHanging;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a rider hang a boat under the ghast by using a boat on it. Runs before vanilla {@code
 * HappyGhast#mobInteract}, which would otherwise try the item and then (re)mount the player; any
 * case {@link BoatHanging} declines falls through to it unchanged.
 */
@Mixin(HappyGhast.class)
abstract class HappyGhastBoatMixin {
  @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
  private void ghastglide$hangBoat(
      Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
    InteractionResult result = BoatHanging.tryHang((HappyGhast) (Object) this, player, hand);
    if (result.consumesAction()) {
      cir.setReturnValue(result);
    }
  }
}

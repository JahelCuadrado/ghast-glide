package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.glide.GlideRules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While gliding with the mod's controls, sneak means "descend"; dismounting uses the mod's own key
 * instead. With vanilla controls vanilla {@code Player#wantsToStopRiding} (sneak) stays, checked by
 * {@code Player#rideTick} on the server.
 */
@Mixin(Player.class)
abstract class PlayerMixin {
  @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
  private void ghastglide$keepRidingWhileGliding(CallbackInfoReturnable<Boolean> cir) {
    if (GlideRules.isGliding((Player) (Object) this)) {
      cir.setReturnValue(false);
    }
  }

  /**
   * Sneak while gliding only descends, so the rider keeps its standing pose. Vanilla {@code
   * Player#getDesiredPose} returns {@code CROUCHING} for sneak, and {@code Player#updatePlayerPose}
   * applies it to passengers without a fit check: crouching would lower the eye (camera dip) and
   * shrink the box to 1.5, which grows back unchecked on release. Runs every player tick on both
   * sides; the pose comparison returns early unless the player sneaks.
   */
  @ModifyReturnValue(method = "getDesiredPose", at = @At("RETURN"))
  private Pose ghastglide$standWhileGliding(Pose desired) {
    return desired == Pose.CROUCHING && GlideRules.isGliding((Player) (Object) this)
        ? Pose.STANDING
        : desired;
  }

  /**
   * Vanilla {@code Player#getDestroySpeed} divides mining speed by 5 when {@code !onGround()}, and
   * {@code Player#tick} keeps every passenger off the ground, so a ghast rider would always mine 5
   * times slower. With "Mine at normal speed" the rider counts as grounded for that check only.
   * Runs only while a block is being mined; a grounded player returns at the first check.
   */
  @ModifyExpressionValue(
      method = "getDestroySpeed",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"))
  private boolean ghastglide$mineAtGroundSpeedWhileRiding(boolean onGround) {
    return onGround || GlideRules.minesAtGroundSpeed((Player) (Object) this);
  }
}

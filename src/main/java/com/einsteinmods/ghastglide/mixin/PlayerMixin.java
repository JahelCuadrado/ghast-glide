package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.glide.GlideRules;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While gliding, sneak means "descend"; dismounting uses the mod's own key instead. */
@Mixin(Player.class)
abstract class PlayerMixin {
  @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
  private void ghastglide$keepRidingWhileGliding(CallbackInfoReturnable<Boolean> cir) {
    if (GlideRules.isGliding((Player) (Object) this)) {
      cir.setReturnValue(false);
    }
  }
}

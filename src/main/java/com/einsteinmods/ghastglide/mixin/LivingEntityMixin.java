package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.glide.PhasingGhast;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A phasing ghast does not suffocate; phasing ends once it is clear of blocks. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
  @Inject(method = "isInWall", at = @At("RETURN"), cancellable = true)
  private void ghastglide$ignoreWallWhilePhasing(CallbackInfoReturnable<Boolean> cir) {
    if (!((Object) this instanceof PhasingGhast ghast) || !ghast.ghastglide$isPhasing()) {
      return;
    }
    if (cir.getReturnValueZ()) {
      cir.setReturnValue(false);
    } else {
      ghast.ghastglide$setPhasing(false);
    }
  }
}

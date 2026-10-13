package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.glide.GlideMovement;
import com.einsteinmods.ghastglide.glide.GlideRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Noclip: a gliding ghast collides only through its passengers' boxes. */
@Mixin(Entity.class)
abstract class EntityMixin {
  @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
  private void ghastglide$collideWithPassengersOnly(
      Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
    if ((Object) this instanceof HappyGhast ghast && GlideRules.isNoclipActive(ghast)) {
      cir.setReturnValue(GlideMovement.collideWithPassengersOnly(ghast, movement));
    }
  }
}

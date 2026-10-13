package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.anchor.GhastAnchors;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a still Happy Ghast's body on its synced head yaw; see GhastAnchors. */
@Mixin(BodyRotationControl.class)
abstract class BodyRotationControlMixin {
  @Shadow @Final private Mob mob;

  @Inject(method = "clientTick", at = @At("TAIL"))
  private void ghastglide$keepStillGhastBodyOnHead(CallbackInfo ci) {
    if (mob instanceof HappyGhast ghast) {
      GhastAnchors.keepBodyOnHeadWhileStill(ghast);
    }
  }
}

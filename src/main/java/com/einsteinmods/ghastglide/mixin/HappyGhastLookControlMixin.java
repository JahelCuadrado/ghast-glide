package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.anchor.GhastAnchors;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps an anchored ghast's yaw instead of the still-state cardinal snap; see GhastAnchors. */
@Mixin(targets = "net.minecraft.world.entity.animal.happyghast.HappyGhast$HappyGhastLookControl")
abstract class HappyGhastLookControlMixin extends LookControl {
  private HappyGhastLookControlMixin(Mob mob) {
    super(mob);
  }

  @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
  private void ghastglide$holdAnchoredFacing(CallbackInfo ci) {
    if (mob instanceof HappyGhast ghast && GhastAnchors.holdFacing(ghast)) {
      ci.cancel();
    }
  }
}

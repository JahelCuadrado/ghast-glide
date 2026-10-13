package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.anchor.GhastAnchors;
import com.einsteinmods.ghastglide.anchor.StillGhast;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anchors a ghast when its riders leave and drives calls; logic lives in GhastAnchors. */
@Mixin(HappyGhast.class)
abstract class HappyGhastAnchorMixin implements StillGhast {
  @Shadow
  private void setServerStillTimeout(int serverStillTimeout) {}

  @Inject(method = "addPassenger", at = @At("TAIL"))
  private void ghastglide$releaseOnRide(Entity passenger, CallbackInfo ci) {
    GhastAnchors.onPassengerAdded((HappyGhast) (Object) this, passenger);
  }

  @Inject(method = "removePassenger", at = @At("TAIL"))
  private void ghastglide$anchorOnDismount(Entity passenger, CallbackInfo ci) {
    GhastAnchors.onPassengerRemoved((HappyGhast) (Object) this, passenger);
  }

  @Inject(method = "tick", at = @At("TAIL"))
  private void ghastglide$tickAnchor(CallbackInfo ci) {
    GhastAnchors.tick((HappyGhast) (Object) this);
  }

  @Override
  public void ghastglide$setStillTimeout(int ticks) {
    setServerStillTimeout(ticks);
  }
}

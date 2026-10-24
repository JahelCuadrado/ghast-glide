package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.anchor.GhastAnchors;
import com.einsteinmods.ghastglide.anchor.StillGhast;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

  /**
   * Vanilla {@code HappyGhast#removePassenger} starts the still timeout whenever any passenger
   * leaves; skipped while a player still pilots it (see {@code GhastAnchors#keepsPilotControl}).
   */
  @WrapWithCondition(
      method = "removePassenger",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/animal/happyghast/HappyGhast;setServerStillTimeout(I)V"))
  private boolean ghastglide$keepPilotOnPassengerLeave(HappyGhast ghast, int ticks) {
    return !GhastAnchors.keepsPilotControl(ghast);
  }

  @Inject(method = "removePassenger", at = @At("TAIL"))
  private void ghastglide$anchorOnDismount(Entity passenger, CallbackInfo ci) {
    GhastAnchors.onPassengerRemoved((HappyGhast) (Object) this, passenger);
  }

  /**
   * Vanilla {@code HappyGhast#tick} renews the still timeout while {@code scanPlayerAboveGhast}
   * finds a player on its head; while a player pilots it the scan is skipped (reported as no
   * player), see {@code GhastAnchors#keepsPilotControl}. Server only, once per Happy Ghast tick.
   */
  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/animal/happyghast/HappyGhast;scanPlayerAboveGhast()Z"))
  private boolean ghastglide$keepPilotWithPlayerAbove(
      HappyGhast ghast, Operation<Boolean> original) {
    return !GhastAnchors.keepsPilotControl(ghast) && original.call(ghast);
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

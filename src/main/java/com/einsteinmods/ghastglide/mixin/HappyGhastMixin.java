package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.glide.GlideMovement;
import com.einsteinmods.ghastglide.glide.GlideRules;
import com.einsteinmods.ghastglide.glide.PhasingGhast;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creative-flight travel (mod controls only) and collision-safe turning while a gliding player
 * controls the ghast.
 */
@Mixin(HappyGhast.class)
abstract class HappyGhastMixin implements PhasingGhast {
  @Unique private boolean ghastglide$phasing;
  @Unique private float ghastglide$yawBeforeTurn;

  @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
  private void ghastglide$creativeTravel(Vec3 input, CallbackInfo ci) {
    HappyGhast self = (HappyGhast) (Object) this;
    if (!(self.getControllingPassenger() instanceof Player rider)) {
      return;
    }
    GlideSettings settings = GlideRules.settingsFor(rider);
    // Vanilla controls keep vanilla HappyGhast#travel (look to climb, vanilla speeds).
    if (settings != null && GlideRules.usesModControls(settings)) {
      GlideMovement.travel(self, rider, settings);
      ci.cancel();
    }
  }

  @Inject(method = "tickRidden", at = @At("HEAD"))
  private void ghastglide$rememberYaw(Player controller, Vec3 riddenInput, CallbackInfo ci) {
    ghastglide$yawBeforeTurn = ((HappyGhast) (Object) this).getYRot();
  }

  @Inject(method = "tickRidden", at = @At("TAIL"))
  private void ghastglide$keepRidersClear(Player controller, Vec3 riddenInput, CallbackInfo ci) {
    GlideMovement.keepRidersClearAfterTurn((HappyGhast) (Object) this, ghastglide$yawBeforeTurn);
  }

  @Override
  public boolean ghastglide$isPhasing() {
    return ghastglide$phasing;
  }

  @Override
  public void ghastglide$setPhasing(boolean phasing) {
    ghastglide$phasing = phasing;
  }
}

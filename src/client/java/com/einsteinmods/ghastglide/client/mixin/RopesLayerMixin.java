package com.einsteinmods.ghastglide.client.mixin;

import com.einsteinmods.ghastglide.client.FirstPersonGhastHiding;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.layers.RopesLayer;
import net.minecraft.client.renderer.entity.state.HappyGhastRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drops the rope knots of a Happy Ghast marked semi-transparent by {@link FirstPersonGhastHiding}:
 * vanilla {@code RopesLayer#submit} draws them opaque while the ghast holds a leash ({@code
 * isLeashHolder}), and the ropes themselves are already dropped for that ghast. Only Happy Ghasts
 * pay one boolean read per frame.
 */
@Mixin(RopesLayer.class)
abstract class RopesLayerMixin {
  @ModifyExpressionValue(
      method =
          "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
              + "ILnet/minecraft/client/renderer/entity/state/HappyGhastRenderState;FF)V",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lnet/minecraft/client/renderer/entity/state/HappyGhastRenderState;isLeashHolder:Z"))
  private boolean ghastglide$dropSemiTransparentKnots(
      boolean leashHolder, @Local(argsOnly = true) HappyGhastRenderState state) {
    return leashHolder && !FirstPersonGhastHiding.isSemiTransparent(state);
  }
}

package com.einsteinmods.ghastglide.client.mixin;

import com.einsteinmods.ghastglide.client.FirstPersonGhastHiding;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws living entities marked semi-transparent by {@link FirstPersonGhastHiding} (the ridden
 * ghast, mobs leashed to it, mobs and players in its hung boats). Vanilla {@code
 * LivingEntityRenderer#submit} already has a see-through path for invisible entities a spectator
 * sees ({@code forceTransparent}: {@code RenderTypes#entityTranslucentCull} and a tint with low
 * alpha); this reuses its render type with the mod's alpha. {@code entityTranslucentCull} is a
 * one-key memoized lookup, while {@code entityTranslucent} builds a key pair on every call. Layers
 * (armour, saddles, held items, wool...) draw with their own opaque render types, so a marked
 * entity skips them ({@link FirstPersonGhastHiding#skipsLayers}); a marked Happy Ghast keeps its
 * layers for the harness ({@code EquipmentLayerRendererMixin}, {@code RopesLayerMixin}). Every
 * living entity pays one boolean read per hook and frame.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
  private static final String SUBMIT =
      "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
          + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
          + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

  @Shadow
  public abstract Identifier getTextureLocation(LivingEntityRenderState state);

  @ModifyExpressionValue(
      method = SUBMIT,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
  private @Nullable RenderType ghastglide$semiTransparentRenderType(
      @Nullable RenderType renderType, @Local(argsOnly = true) LivingEntityRenderState state) {
    return renderType != null && FirstPersonGhastHiding.isSemiTransparent(state)
        ? RenderTypes.entityTranslucentCull(getTextureLocation(state))
        : renderType;
  }

  @ModifyExpressionValue(
      method = SUBMIT,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I"))
  private int ghastglide$semiTransparentTint(
      int tint, @Local(argsOnly = true) LivingEntityRenderState state) {
    return FirstPersonGhastHiding.isSemiTransparent(state)
        ? ARGB.multiply(tint, FirstPersonGhastHiding.SEMI_TRANSPARENT_TINT)
        : tint;
  }

  @ModifyExpressionValue(
      method = SUBMIT,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"))
  private boolean ghastglide$skipOpaqueLayers(
      boolean renderLayers, @Local(argsOnly = true) LivingEntityRenderState state) {
    return renderLayers && !FirstPersonGhastHiding.skipsLayers(state);
  }
}

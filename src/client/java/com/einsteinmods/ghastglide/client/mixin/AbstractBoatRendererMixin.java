package com.einsteinmods.ghastglide.client.mixin;

import com.einsteinmods.ghastglide.client.FirstPersonGhastHiding;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws a boat or raft marked semi-transparent by {@link FirstPersonGhastHiding} (a boat hung from
 * the ridden ghast). Vanilla {@code AbstractBoatRenderer#submit} submits the hull with the texture
 * overload of {@code OrderedSubmitNodeCollector#submitModel}, which picks the model's opaque render
 * type and no tint; for a marked boat that call is replaced by the overload taking a render type
 * and tint, with {@code RenderTypes#entityTranslucentCull}. The water mask of {@code
 * BoatRenderer#submitTypeAdditions} stays: it only keeps water out of the hull and draws no colour.
 * Other boats pay one boolean read per frame.
 */
@Mixin(AbstractBoatRenderer.class)
abstract class AbstractBoatRendererMixin {
  @WrapWithCondition(
      method =
          "submit(Lnet/minecraft/client/renderer/entity/state/BoatRenderState;"
              + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
              + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;III)V"))
  private boolean ghastglide$submitSemiTransparentHull(
      SubmitNodeCollector collector,
      Model<? super BoatRenderState> model,
      Object state,
      PoseStack poseStack,
      Identifier texture,
      int lightCoords,
      int overlayCoords,
      int outlineColor,
      @Local(argsOnly = true) BoatRenderState boat) {
    if (!FirstPersonGhastHiding.isSemiTransparent(boat)) {
      return true;
    }
    collector.submitModel(
        model,
        boat,
        poseStack,
        RenderTypes.entityTranslucentCull(texture),
        lightCoords,
        overlayCoords,
        FirstPersonGhastHiding.SEMI_TRANSPARENT_TINT,
        null,
        outlineColor);
    return false;
  }
}

package com.einsteinmods.ghastglide.client.mixin;

import com.einsteinmods.ghastglide.client.FirstPersonGhastHiding;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws the harness of a Happy Ghast marked semi-transparent by {@link FirstPersonGhastHiding}.
 * Vanilla {@code HappyGhastRenderer} draws it with a {@code SimpleEquipmentLayer}, which calls
 * {@code EquipmentLayerRenderer#renderLayers}; that submits each equipment layer with {@code
 * RenderTypes#armorCutoutNoCull} (or its glint variant), opaque, and the layer colour. For a marked
 * state the render type becomes {@code RenderTypes#entityTranslucentCull} (one-key memoized, as for
 * the body; vanilla has no translucent armour type, and the glint is dropped) and the colour gets
 * the body's alpha. Only marked ghasts reach this with a marked state ({@code
 * FirstPersonGhastHiding#skipsLayers}). Every equipment layer drawn pays one type check and one
 * boolean read per hook.
 */
@Mixin(EquipmentLayerRenderer.class)
abstract class EquipmentLayerRendererMixin {
  private static final String RENDER_LAYERS =
      "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
          + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;"
          + "Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;"
          + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
          + "ILnet/minecraft/resources/Identifier;II)V";

  @ModifyExpressionValue(
      method = RENDER_LAYERS,
      at = {
        @At(
            value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"),
        @At(
            value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCullGlint(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;")
      })
  private RenderType ghastglide$semiTransparentRenderType(
      RenderType renderType,
      @Local(argsOnly = true) Object state,
      @Local(ordinal = 1) Identifier layerTexture) {
    return FirstPersonGhastHiding.isSemiTransparentEquipment(state)
        ? RenderTypes.entityTranslucentCull(layerTexture)
        : renderType;
  }

  @ModifyExpressionValue(
      method = RENDER_LAYERS,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;getColorForLayer(Lnet/minecraft/client/resources/model/EquipmentClientInfo$Layer;I)I"))
  private int ghastglide$semiTransparentColor(int color, @Local(argsOnly = true) Object state) {
    return FirstPersonGhastHiding.isSemiTransparentEquipment(state)
        ? ARGB.multiply(color, FirstPersonGhastHiding.SEMI_TRANSPARENT_TINT)
        : color;
  }
}

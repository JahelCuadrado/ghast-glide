package com.einsteinmods.ghastglide.client.mixin;

import com.einsteinmods.ghastglide.client.FirstPersonGhastHiding;
import com.einsteinmods.ghastglide.client.config.FirstPersonVisibility;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies the first-person visibility of the ridden Happy Ghast and its cargo ({@link
 * FirstPersonGhastHiding}). Hooked on the per-entity visibility result in {@code
 * extractVisibleEntities} rather than {@code EntityRenderer#shouldRender}: {@code isEntityVisible}
 * forces the player's own vehicle visible ({@code || entity.hasIndirectPassenger(player)}) whatever
 * {@code shouldRender} says. Skipping a hidden entity there skips its whole render state, so model,
 * harness, ropes, name tag and shadow all disappear together; a leash rope belongs to the leashed
 * entity's state ({@code EntityRenderer#extractRenderState}), so a skipped boat takes its ropes
 * with it. A semi-transparent entity is extracted as usual and its fresh state marked right after
 * {@code extractEntity}. Other entities pay one preferences check while every option is Normal.
 */
@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
  @Shadow @Final private Minecraft minecraft;

  /**
   * Visibility of the entity the loop is on, from the visibility hook to the extraction hook of the
   * same iteration (vanilla extracts only entities that passed the visibility check). Render thread
   * only, like the rest of {@code LevelExtractor}.
   */
  @Unique private FirstPersonVisibility ghastglide$visibility = FirstPersonVisibility.NORMAL;

  @ModifyExpressionValue(
      method = "extractVisibleEntities",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/extract/LevelExtractor;isEntityVisible(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDFJ)Z"))
  private boolean ghastglide$applyFirstPersonVisibility(
      boolean visible, @Local Entity entity, @Local(argsOnly = true) Camera camera) {
    if (!visible) {
      return false;
    }
    FirstPersonVisibility visibility = FirstPersonGhastHiding.visibility(minecraft, camera, entity);
    ghastglide$visibility = visibility;
    return visibility != FirstPersonVisibility.HIDDEN;
  }

  @ModifyExpressionValue(
      method = "extractVisibleEntities",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
  private EntityRenderState ghastglide$markSemiTransparent(EntityRenderState state) {
    if (ghastglide$visibility == FirstPersonVisibility.SEMI_TRANSPARENT) {
      FirstPersonGhastHiding.makeSemiTransparent(state);
    }
    return state;
  }
}

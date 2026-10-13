package com.einsteinmods.ghastglide.client.mixin;

import com.einsteinmods.ghastglide.client.GhastGlideClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hides the Happy Ghast the local player rides while the camera is in first person, when the player
 * enabled it. Hooked on the per-entity visibility result in {@code extractVisibleEntities} rather
 * than {@code EntityRenderer#shouldRender}: {@code isEntityVisible} forces the player's own vehicle
 * visible ({@code || entity.hasIndirectPassenger(player)}) whatever {@code shouldRender} says.
 * Skipping the entity there skips its whole render state, so model, harness, ropes, name tag and
 * shadow all disappear together. Other entities pay one {@code instanceof} check.
 */
@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
  @Shadow @Final private Minecraft minecraft;

  @ModifyExpressionValue(
      method = "extractVisibleEntities",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/extract/LevelExtractor;isEntityVisible(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDFJ)Z"))
  private boolean ghastglide$hideRiddenGhastInFirstPerson(
      boolean visible, @Local Entity entity, @Local(argsOnly = true) Camera camera) {
    if (!visible
        || !(entity instanceof HappyGhast)
        || !GhastGlideClient.state().preferences().hideGhastInFirstPerson()
        || camera.isDetached()) {
      return visible;
    }
    LocalPlayer player = minecraft.player;
    return player == null || player.getVehicle() != entity;
  }
}

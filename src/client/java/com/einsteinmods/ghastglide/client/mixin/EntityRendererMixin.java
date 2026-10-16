package com.einsteinmods.ghastglide.client.mixin;

import com.einsteinmods.ghastglide.client.FirstPersonGhastHiding;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drops the leash ropes tied to the Happy Ghast hidden in first person ({@link
 * FirstPersonGhastHiding}) from any leashed entity that stays visible (mobs; leashed boats are
 * skipped whole by {@code LevelExtractorMixin}). Vanilla {@code EntityRenderer#extractRenderState}
 * builds the ropes from the leashed entity toward its holder only when {@code
 * Leashable#getLeashHolder} is non-null at its first call, else sets {@code leashStates = null};
 * reporting no holder there takes that branch, so the entity itself still renders. Only leashed
 * entities reach the check: one {@code instanceof} on the holder per extracted leashed entity.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
  @ModifyExpressionValue(
      method = "extractRenderState",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Leashable;getLeashHolder()Lnet/minecraft/world/entity/Entity;",
              ordinal = 0))
  private @Nullable Entity ghastglide$hideRopesToHiddenGhast(@Nullable Entity holder) {
    if (!(holder instanceof HappyGhast)) {
      return holder;
    }
    Minecraft minecraft = Minecraft.getInstance();
    return FirstPersonGhastHiding.hiddenGhast(minecraft, minecraft.gameRenderer.mainCamera())
            == holder
        ? null
        : holder;
  }
}

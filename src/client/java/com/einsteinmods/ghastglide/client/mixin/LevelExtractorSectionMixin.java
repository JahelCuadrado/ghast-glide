package com.einsteinmods.ghastglide.client.mixin;

import com.einsteinmods.ghastglide.client.GhastSectionVisibility;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps a partly buried Happy Ghast rendered: when vanilla {@code LevelExtractor#isEntityVisible}
 * rejects it because its feet section is not compiled, any compiled section its box touches is
 * accepted instead (see {@link GhastSectionVisibility}). Other entities pay one {@code instanceof}
 * check, and only when vanilla already culls them.
 */
@Mixin(LevelExtractor.class)
abstract class LevelExtractorSectionMixin implements GhastSectionVisibility.SectionTest {
  @Shadow @Final private LevelRenderer levelRenderer;
  @Shadow private @Nullable ClientLevel level;

  /** Scratch section origin; LevelExtractor only runs on the render thread. */
  @Unique
  private final BlockPos.MutableBlockPos ghastglide$sectionOrigin = new BlockPos.MutableBlockPos();

  @ModifyExpressionValue(
      method = "isEntityVisible",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiledAndVisible(Lnet/minecraft/core/BlockPos;J)Z"))
  private boolean ghastglide$renderPartlyBuriedGhast(
      boolean feetSectionVisible,
      @Local(argsOnly = true) Entity entity,
      @Local(argsOnly = true) long chunkFadeDuration) {
    if (feetSectionVisible || !(entity instanceof HappyGhast)) {
      return feetSectionVisible;
    }
    return GhastSectionVisibility.isAnySectionVisible(
        entity.getBoundingBox(), chunkFadeDuration, this, ghastglide$sectionOrigin);
  }

  /**
   * Same test vanilla applies to the feet section. Only reached from {@code isEntityVisible} after
   * its null check of {@code level}, which is the level the entity is in.
   */
  @Override
  public boolean ghastglide$isSectionVisible(BlockPos sectionOrigin, long chunkFadeDuration) {
    return (level != null && level.isOutsideBuildHeight(sectionOrigin.getY()))
        || levelRenderer.isSectionCompiledAndVisible(sectionOrigin, chunkFadeDuration);
  }
}

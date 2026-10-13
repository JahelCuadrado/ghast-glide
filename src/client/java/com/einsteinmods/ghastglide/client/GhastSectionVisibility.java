package com.einsteinmods.ghastglide.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

/**
 * Render-section test for a Happy Ghast. Vanilla {@code LevelExtractor#isEntityVisible} draws an
 * entity only if the section of its feet block ({@code Entity#blockPosition}) is compiled ({@code
 * LevelRenderer#isSectionCompiledAndVisible}). A section is compiled only after the section
 * occlusion graph ({@code SectionOcclusionGraph#runUpdates}) reaches it inside the frustum, and the
 * graph does not enter a fully solid section through solid faces. A partly buried ghast keeps its
 * feet in such a never-compiled section while its top sticks out of the ground, so it vanished
 * until the camera moved into a neighbouring section.
 */
public final class GhastSectionVisibility {
  private GhastSectionVisibility() {}

  /**
   * Per-section test, implemented by the {@code LevelExtractor} mixin itself so the per-frame call
   * allocates no lambda.
   */
  @FunctionalInterface
  public interface SectionTest {
    boolean ghastglide$isSectionVisible(BlockPos sectionOrigin, long chunkFadeDuration);
  }

  /**
   * Whether any render section the box touches passes {@code test}. An adult Happy Ghast (4 blocks
   * per side) touches at most 2 sections per axis, so at most 8 checks. {@code sectionOrigin} is
   * the caller's scratch position (overwritten), so nothing is allocated.
   */
  public static boolean isAnySectionVisible(
      AABB box, long chunkFadeDuration, SectionTest test, BlockPos.MutableBlockPos sectionOrigin) {
    int minX = SectionPos.blockToSectionCoord(box.minX);
    int minY = SectionPos.blockToSectionCoord(box.minY);
    int minZ = SectionPos.blockToSectionCoord(box.minZ);
    int maxX = SectionPos.blockToSectionCoord(box.maxX);
    int maxY = SectionPos.blockToSectionCoord(box.maxY);
    int maxZ = SectionPos.blockToSectionCoord(box.maxZ);
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          sectionOrigin.set(
              SectionPos.sectionToBlockCoord(x),
              SectionPos.sectionToBlockCoord(y),
              SectionPos.sectionToBlockCoord(z));
          if (test.ghastglide$isSectionVisible(sectionOrigin, chunkFadeDuration)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}

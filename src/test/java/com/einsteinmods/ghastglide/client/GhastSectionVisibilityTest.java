package com.einsteinmods.ghastglide.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class GhastSectionVisibilityTest {
  // Partly buried adult ghast: feet at y 63 (section y 3), top at y 67 (section y 4), straddling
  // the section boundary x = 16.
  private static final AABB PARTLY_BURIED_BOX = new AABB(12.5, 63.0, -1.5, 16.5, 67.0, 2.5);
  private static final long TOP_SECTION = SectionPos.asLong(0, 4, 0);
  private static final int MAX_SECTIONS_OF_ADULT_GHAST = 8;
  private static final long NO_FADE = 0L;

  @Test
  void rendersWhenOnlyTheSectionAboveTheFeetIsCompiled() {
    assertTrue(isAnySectionVisible((pos, fade) -> SectionPos.asLong(pos) == TOP_SECTION));
  }

  @Test
  void staysCulledWhenNoSectionOfTheBoxIsCompiled() {
    assertFalse(isAnySectionVisible((pos, fade) -> false));
  }

  @Test
  void checksEachSectionTheBoxTouchesOnce() {
    Set<Long> checked = new HashSet<>();
    int[] calls = {0};

    isAnySectionVisible(
        (pos, fade) -> {
          calls[0]++;
          checked.add(SectionPos.asLong(pos));
          return false;
        });

    assertEquals(MAX_SECTIONS_OF_ADULT_GHAST, calls[0]);
    assertEquals(MAX_SECTIONS_OF_ADULT_GHAST, checked.size());
    assertTrue(checked.contains(SectionPos.asLong(BlockPos.containing(12.5, 63.0, -1.5))));
    assertTrue(checked.contains(SectionPos.asLong(BlockPos.containing(16.5, 67.0, 2.5))));
  }

  private static boolean isAnySectionVisible(GhastSectionVisibility.SectionTest test) {
    return GhastSectionVisibility.isAnySectionVisible(
        PARTLY_BURIED_BOX, NO_FADE, test, new BlockPos.MutableBlockPos());
  }
}

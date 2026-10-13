package com.einsteinmods.ghastglide.glide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GhastLightProbeTest {
  // Adult ghast (4 wide) with feet at y 62: box top 66, eyes at y 64.6 (block 64).
  private static final Vec3 EYE = new Vec3(8.5, 64.6, 0.5);
  private static final double BOX_TOP = 66.0;
  private static final double BOX_WIDTH = 4.0;
  private static final int SKY = 15;
  private static final int DARK = 0;
  private static final BlockPos EYE_BLOCK = new BlockPos(8, 64, 0);
  private static final BlockPos ABOVE_CENTRE = new BlockPos(8, 66, 0);
  private static final BlockPos TOP_LAYER_CORNER = new BlockPos(10, 65, 2);

  @Test
  void litEyeKeepsVanillaProbe() {
    assertEquals(EYE, probe(Map.of(EYE_BLOCK, SKY)));
  }

  @Test
  void darkEyeSamplesAboveTheCentreWhenItIsLit() {
    assertEquals(ABOVE_CENTRE, BlockPos.containing(probe(Map.of(ABOVE_CENTRE, SKY))));
  }

  @Test
  void hillOverTheCentreSamplesTheExposedTopCorner() {
    assertEquals(TOP_LAYER_CORNER, BlockPos.containing(probe(Map.of(TOP_LAYER_CORNER, SKY))));
  }

  @Test
  void dimEyeSamplesABrighterExposedPart() {
    assertEquals(ABOVE_CENTRE, BlockPos.containing(probe(Map.of(EYE_BLOCK, 3, ABOVE_CENTRE, SKY))));
  }

  @Test
  void keepsEyeWhenNothingIsBrighter() {
    assertEquals(EYE, probe(Map.of()));
  }

  private static Vec3 probe(Map<BlockPos, Integer> lit) {
    return GhastLightProbe.brightestProbe(
        EYE,
        BOX_TOP,
        BOX_WIDTH,
        lit,
        (levels, pos) -> levels.getOrDefault(pos.immutable(), DARK),
        new BlockPos.MutableBlockPos());
  }
}

package com.einsteinmods.ghastglide.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.Vec3;

/** Light probe of a Happy Ghast whose eyes are inside blocks. */
public final class GhastLightGameTest {
  private static final float PARTIAL_TICK = 1.0F;

  // Ghast (4x4x4) at x=4, z=2 spans x 2..6, z 0..4 and y 1..5; eyes at y 3.6 (block layer 3).
  private static final Vec3 GHAST_POSITION = new Vec3(4.0, 1.0, 2.0);
  private static final int EYE_LAYER_Y = 3;
  private static final int MIN_X = 1;
  private static final int MAX_X = 7;
  private static final int MIN_Z = 0;
  private static final int MAX_Z = 4;

  @GameTest(maxTicks = 20)
  public void buriedEyesSampleLightAboveTheBody(GameTestHelper helper) {
    HappyGhast ghast = spawnGhast(helper);
    for (int x = MIN_X; x <= MAX_X; x++) {
      for (int z = MIN_Z; z <= MAX_Z; z++) {
        helper.setBlock(x, EYE_LAYER_Y, z, Blocks.STONE);
      }
    }

    // The probe picks the brightest sample, so wait until the light engine lit the new blocks.
    helper.succeedWhen(
        () -> {
          Vec3 probe = ghast.getLightProbePosition(PARTIAL_TICK);
          int dampening =
              helper.getLevel().getBlockState(BlockPos.containing(probe)).getLightDampening();
          helper.assertTrue(
              dampening < LightEngine.MAX_LEVEL,
              "probe " + probe + " must not be inside opaque blocks");
          helper.assertTrue(
              probe.y > ghast.getBoundingBox().maxY, "probe " + probe + " must be above the body");
        });
  }

  @GameTest(maxTicks = 20)
  public void openAirKeepsVanillaEyeProbe(GameTestHelper helper) {
    HappyGhast ghast = spawnGhast(helper);

    Vec3 probe = ghast.getLightProbePosition(PARTIAL_TICK);

    helper.assertValueEqual(probe, ghast.getEyePosition(PARTIAL_TICK), "light probe");
    helper.succeed();
  }

  private static HappyGhast spawnGhast(GameTestHelper helper) {
    HappyGhast ghast = helper.spawn(EntityTypes.HAPPY_GHAST, GHAST_POSITION);
    ghast.setNoAi(true);
    return ghast;
  }
}

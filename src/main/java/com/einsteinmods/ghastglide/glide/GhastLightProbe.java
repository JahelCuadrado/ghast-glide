package com.einsteinmods.ghastglide.glide;

import java.util.function.ToIntBiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.Vec3;

/**
 * Where a Happy Ghast samples light for rendering ({@code EntityRenderer#getPackedLightCoords}).
 * Vanilla samples at the eyes; a phasing ghast's eyes can sit in a block that stores no light (an
 * opaque block, or the dark lower half of a top slab), which renders the whole ghast black. A
 * partly buried ghast is then lit from its exposed part instead: the brightest of a few points on
 * the top layer of its box and just above it. The column above the box centre alone is not enough:
 * on uneven ground it can still be inside terrain while the edges stick out.
 */
public final class GhastLightProbe {
  /** No light combines above this raw value, so a fully lit eye needs no other sample. */
  private static final int FULL_BRIGHTNESS = LightEngine.MAX_LEVEL;

  /** Lifts a sample from the box top into the block just above it. */
  private static final double ABOVE_TOP_OFFSET = 0.5;

  /** Lowers a sample from the box top into the box's top block layer. */
  private static final double TOP_LAYER_OFFSET = -0.5;

  /** Keeps corner samples half a block inside the box edges. */
  private static final double CORNER_INSET = 0.5;

  /** No sky darkening: compares stored light, so the choice does not change with time of day. */
  private static final int NO_SKY_DARKENING = 0;

  // Sample offsets {x factor, z factor, y offset from box top}, in tie-break order: above the
  // centre first, as before, then the top layer at the centre and at the four inset corners.
  private static final double[][] SAMPLES = {
    {0, 0, ABOVE_TOP_OFFSET},
    {0, 0, TOP_LAYER_OFFSET},
    {1, 1, TOP_LAYER_OFFSET},
    {1, -1, TOP_LAYER_OFFSET},
    {-1, 1, TOP_LAYER_OFFSET},
    {-1, -1, TOP_LAYER_OFFSET},
  };

  private GhastLightProbe() {}

  /**
   * Returns {@code eyeProbe}, or a brighter point on the exposed top of the interpolated box. Runs
   * per Happy Ghast per rendered frame ({@code EntityRenderer#getPackedLightCoords}, render thread
   * only), so it allocates nothing unless a brighter sample wins: {@code samplePos} is the caller's
   * scratch position and the brightness lookup is a non-capturing method reference.
   */
  public static Vec3 lightProbe(
      Entity ghast, Vec3 eyeProbe, float partialTick, BlockPos.MutableBlockPos samplePos) {
    double boxTop = Mth.lerp(partialTick, ghast.yo, ghast.getY()) + ghast.getBbHeight();
    return brightestProbe(
        eyeProbe,
        boxTop,
        ghast.getBbWidth(),
        ghast.level(),
        GhastLightProbe::storedBrightness,
        samplePos);
  }

  private static int storedBrightness(Level level, BlockPos pos) {
    return level.getRawBrightness(pos, NO_SKY_DARKENING);
  }

  /**
   * Returns the brightest of {@code eyeProbe} and the {@link #SAMPLES} around the top of a box
   * centred on it horizontally; on ties the earlier point wins, so a lit eye keeps vanilla. {@code
   * samplePos} is overwritten.
   */
  static <L> Vec3 brightestProbe(
      Vec3 eyeProbe,
      double boxTop,
      double boxWidth,
      L level,
      ToIntBiFunction<L, BlockPos> rawBrightness,
      BlockPos.MutableBlockPos samplePos) {
    int bestBrightness =
        rawBrightness.applyAsInt(level, samplePos.set(eyeProbe.x, eyeProbe.y, eyeProbe.z));
    if (bestBrightness >= FULL_BRIGHTNESS) {
      return eyeProbe;
    }
    double cornerOffset = Math.max(0.0, boxWidth / 2.0 - CORNER_INSET);
    Vec3 best = eyeProbe;
    for (double[] sample : SAMPLES) {
      double x = eyeProbe.x + sample[0] * cornerOffset;
      double y = boxTop + sample[2];
      double z = eyeProbe.z + sample[1] * cornerOffset;
      int brightness = rawBrightness.applyAsInt(level, samplePos.set(x, y, z));
      if (brightness > bestBrightness) {
        bestBrightness = brightness;
        best = new Vec3(x, y, z);
        if (bestBrightness >= FULL_BRIGHTNESS) {
          break;
        }
      }
    }
    return best;
  }
}

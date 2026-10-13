package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.glide.GhastLightProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Keeps a Happy Ghast lit while its eyes are inside blocks. HappyGhast does not override {@code
 * Entity#getLightProbePosition}, so this adds the override: only Happy Ghasts pay for it.
 */
@Mixin(HappyGhast.class)
abstract class HappyGhastLightMixin extends Animal {
  /** Scratch for light samples; only the render thread calls getLightProbePosition. */
  @Unique
  private final BlockPos.MutableBlockPos ghastglide$lightSample = new BlockPos.MutableBlockPos();

  private HappyGhastLightMixin(EntityType<? extends Animal> type, Level level) {
    super(type, level);
  }

  @Override
  public Vec3 getLightProbePosition(float partialTickTime) {
    return GhastLightProbe.lightProbe(
        this,
        super.getLightProbePosition(partialTickTime),
        partialTickTime,
        ghastglide$lightSample);
  }
}

package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.glide.BuildThroughGhast;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Same as {@link BlockItemMixin} for standing/wall items (heads, signs, torches, coral fans), whose
 * {@code StandingAndWallBlockItem#getPlacementState} runs its own {@code
 * LevelReader#isUnobstructed(BlockState, BlockPos, CollisionContext)} check; see {@link
 * BuildThroughGhast}. Cost: one boolean test per accepted placement.
 */
@Mixin(StandingAndWallBlockItem.class)
abstract class StandingAndWallBlockItemMixin {
  @WrapOperation(
      method = "getPlacementState",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/LevelReader;isUnobstructed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Z"))
  private boolean ghastglide$buildThroughGhast(
      LevelReader level,
      BlockState state,
      BlockPos pos,
      CollisionContext collisionContext,
      Operation<Boolean> original,
      BlockPlaceContext context) {
    return BuildThroughGhast.isUnobstructed(
        original.call(level, state, pos, collisionContext),
        context.getLevel(),
        state,
        pos,
        collisionContext,
        context.getPlayer());
  }
}

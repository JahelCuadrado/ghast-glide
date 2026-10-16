package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.glide.BuildThroughGhast;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets the entity check of vanilla {@code BlockItem#canPlace} ({@code Level#isUnobstructed(
 * BlockState, BlockPos, CollisionContext)}) ignore the Happy Ghast the placing player rides; see
 * {@link BuildThroughGhast}. Cost: one boolean test per accepted placement.
 */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
  @WrapOperation(
      method = "canPlace",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;isUnobstructed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Z"))
  private boolean ghastglide$buildThroughGhast(
      Level level,
      BlockState state,
      BlockPos pos,
      CollisionContext collisionContext,
      Operation<Boolean> original,
      BlockPlaceContext context) {
    return BuildThroughGhast.isUnobstructed(
        original.call(level, state, pos, collisionContext),
        level,
        state,
        pos,
        collisionContext,
        context.getPlayer());
  }
}

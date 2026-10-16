package com.einsteinmods.ghastglide.glide;

import com.einsteinmods.ghastglide.core.GlideSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Lets a Happy Ghast rider place blocks where the ghast they ride is. Vanilla block placement
 * ({@code BlockItem#canPlace}, {@code StandingAndWallBlockItem#getPlacementState}) calls {@code
 * CollisionGetter#isUnobstructed(BlockState, BlockPos, CollisionContext)}, which checks {@code
 * EntityGetter#isUnobstructed} with a null source: every entity with {@code blocksBuilding} rejects
 * the block, the ridden ghast included.
 *
 * <p>Both sides run the same check with the player's effective {@link GlideSettings}, so the client
 * prediction matches the server. Cost: nothing per tick or frame; only a placement vanilla
 * rejected, by a ghast rider with the setting on, repeats vanilla's entity query once (the same
 * vanilla allocations as that query; none of its own).
 */
public final class BuildThroughGhast {
  private BuildThroughGhast() {}

  /**
   * Vanilla's verdict, except that a rejected block placed by a ghast rider with {@link
   * GlideSettings#buildThroughGhast} on is accepted when only that ghast or its other passengers
   * obstruct it. The rider's own body and every other entity still obstruct as in vanilla.
   */
  public static boolean isUnobstructed(
      boolean vanillaUnobstructed,
      Level level,
      BlockState state,
      BlockPos pos,
      CollisionContext context,
      @Nullable Player player) {
    if (vanillaUnobstructed || player == null || !(player.getVehicle() instanceof HappyGhast)) {
      return vanillaUnobstructed;
    }
    GlideSettings settings = GlideRules.settingsFor(player);
    if (settings == null || !settings.buildThroughGhast()) {
      return false;
    }
    VoxelShape shape = state.getCollisionShape(level, pos, context).move(pos);
    if (shape.isEmpty()) {
      return true;
    }
    // Same loop as EntityGetter#isUnobstructed, skipping the rider's vehicle and fellow passengers.
    for (Entity entity : level.getEntities((Entity) null, shape.bounds())) {
      if (!entity.isRemoved()
          && entity.blocksBuilding
          && !ridesWith(entity, player)
          && Shapes.joinIsNotEmpty(shape, Shapes.create(entity.getBoundingBox()), BooleanOp.AND)) {
        return false;
      }
    }
    return true;
  }

  /** The ghast {@code player} rides (the root vehicle) or another passenger of it. */
  private static boolean ridesWith(Entity entity, Player player) {
    return entity != player && entity.isPassengerOfSameVehicle(player);
  }
}

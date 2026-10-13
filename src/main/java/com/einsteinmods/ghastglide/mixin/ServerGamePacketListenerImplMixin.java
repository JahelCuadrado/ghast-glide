package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.glide.GlideRules;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vehicle moves are validated against the ghast's box; a noclip ghast may overlap blocks. Rider
 * collision is still enforced because the server replays the move through Entity#collide.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
  @Inject(method = "isEntityCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
  private void ghastglide$allowNoclipGhast(
      LevelReader level,
      Entity entity,
      AABB oldAabb,
      double newX,
      double newY,
      double newZ,
      CallbackInfoReturnable<Boolean> cir) {
    if (entity instanceof HappyGhast ghast && GlideRules.isNoclipActive(ghast)) {
      cir.setReturnValue(false);
    }
  }
}

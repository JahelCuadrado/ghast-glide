package com.einsteinmods.ghastglide.client.mixin;

import com.einsteinmods.ghastglide.client.SemiTransparentRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds the semi-transparent mark: one boolean per render state, no allocation. */
@Mixin(EntityRenderState.class)
abstract class EntityRenderStateMixin implements SemiTransparentRenderState {
  @Unique private boolean ghastglide$semiTransparent;

  @Override
  public boolean ghastglide$isSemiTransparent() {
    return ghastglide$semiTransparent;
  }

  @Override
  public void ghastglide$markSemiTransparent() {
    ghastglide$semiTransparent = true;
  }
}

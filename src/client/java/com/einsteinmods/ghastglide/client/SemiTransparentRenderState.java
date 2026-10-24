package com.einsteinmods.ghastglide.client;

/**
 * Added to every {@code EntityRenderState} by {@code EntityRenderStateMixin}: marks the states
 * {@link FirstPersonGhastHiding} draws semi-transparent. Vanilla builds a new state per entity and
 * frame ({@code EntityRenderer#createRenderState}), so the mark starts false every frame.
 */
public interface SemiTransparentRenderState {
  boolean ghastglide$isSemiTransparent();

  void ghastglide$markSemiTransparent();
}

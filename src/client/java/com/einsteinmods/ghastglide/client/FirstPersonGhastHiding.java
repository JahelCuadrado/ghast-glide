package com.einsteinmods.ghastglide.client;

import com.einsteinmods.ghastglide.client.config.ClientPreferences;
import com.einsteinmods.ghastglide.client.config.FirstPersonVisibility;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HappyGhastRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import org.jspecify.annotations.Nullable;

/**
 * How the local player's ridden Happy Ghast and its cargo are drawn this frame, from the "Ghast in
 * first person" and "Cargo in first person" options. Only while the camera is not detached; cargo
 * is every entity leashed to that ghast (boats, mobs) and whatever rides such an entity (the pig or
 * player in a hung boat). The ghast's own riders are never cargo.
 *
 * <p>Single source for every render hook: {@code LevelExtractorMixin} skips hidden entities whole
 * and marks semi-transparent states, {@code EntityRendererMixin} drops ropes tied to the ghast,
 * {@code LivingEntityRendererMixin} and {@code AbstractBoatRendererMixin} draw marked states,
 * {@code EquipmentLayerRendererMixin} draws a marked ghast's harness and {@code RopesLayerMixin}
 * drops its rope knots. Stateless, so hooks never see a stale ghast; each call costs a few field
 * reads and allocates nothing.
 */
public final class FirstPersonGhastHiding {
  /** Alpha of semi-transparent entities, about 40 % opacity. */
  private static final int SEMI_TRANSPARENT_ALPHA = 0x66;

  /** ARGB multiplier that keeps the colour and sets {@link #SEMI_TRANSPARENT_ALPHA}. */
  public static final int SEMI_TRANSPARENT_TINT = SEMI_TRANSPARENT_ALPHA << 24 | 0xFFFFFF;

  private FirstPersonGhastHiding() {}

  /** How {@code entity} is drawn this frame. Cheapest checks first. */
  public static FirstPersonVisibility visibility(
      Minecraft minecraft, Camera camera, Entity entity) {
    ClientPreferences preferences = GhastGlideClient.state().preferences();
    if (!preferences.changesFirstPerson() || camera.isDetached()) {
      return FirstPersonVisibility.NORMAL;
    }
    Entity ghast = riddenGhast(minecraft);
    if (ghast == null) {
      return FirstPersonVisibility.NORMAL;
    }
    if (entity == ghast) {
      return preferences.ghastFirstPerson();
    }
    return isCargo(entity, ghast) ? preferences.cargoFirstPerson() : FirstPersonVisibility.NORMAL;
  }

  /**
   * True when a rope tied to {@code holder} is dropped: it is the ridden ghast and the ghast or its
   * cargo is not drawn normally (a rope to a hidden ghast would float; semi-transparent ropes need
   * their own pipeline, so they are hidden too).
   */
  public static boolean dropsRopesTo(Minecraft minecraft, Camera camera, Entity holder) {
    return GhastGlideClient.state().preferences().changesFirstPerson()
        && !camera.isDetached()
        && riddenGhast(minecraft) == holder;
  }

  /** Marks {@code state} semi-transparent, without its ropes and shadow. */
  public static void makeSemiTransparent(EntityRenderState state) {
    ((SemiTransparentRenderState) state).ghastglide$markSemiTransparent();
    state.leashStates = null;
    state.shadowPieces.clear();
    state.shadowRadius = 0.0F;
  }

  public static boolean isSemiTransparent(EntityRenderState state) {
    return ((SemiTransparentRenderState) state).ghastglide$isSemiTransparent();
  }

  /**
   * True when a marked state skips its render layers, which draw with their own opaque render
   * types. A Happy Ghast keeps them: its harness (equipment layer) is drawn semi-transparent and
   * its rope knots (ropes layer) are dropped, like the ropes themselves.
   */
  public static boolean skipsLayers(EntityRenderState state) {
    return isSemiTransparent(state) && !(state instanceof HappyGhastRenderState);
  }

  /** True when {@code state}, the untyped state of an equipment layer, is marked. */
  public static boolean isSemiTransparentEquipment(Object state) {
    return state instanceof EntityRenderState entity && isSemiTransparent(entity);
  }

  private static @Nullable Entity riddenGhast(Minecraft minecraft) {
    LocalPlayer player = minecraft.player;
    if (player == null) {
      return null;
    }
    Entity vehicle = player.getVehicle();
    return vehicle instanceof HappyGhast ? vehicle : null;
  }

  private static boolean isCargo(Entity entity, Entity ghast) {
    if (leashHolder(entity) == ghast) {
      return true;
    }
    Entity vehicle = entity.getVehicle();
    return vehicle != null && leashHolder(vehicle) == ghast;
  }

  private static @Nullable Entity leashHolder(Entity entity) {
    return entity instanceof Leashable leashable ? leashable.getLeashHolder() : null;
  }
}

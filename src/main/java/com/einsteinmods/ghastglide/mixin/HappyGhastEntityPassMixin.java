package com.einsteinmods.ghastglide.mixin;

import com.einsteinmods.ghastglide.glide.GlideRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * "Pass through entities": a ghast flown with the setting on neither collides with nor pushes other
 * entities. The methods are added to {@link HappyGhast} as overrides (it declares none of them), so
 * every other entity keeps the vanilla methods and pays nothing.
 *
 * <ul>
 *   <li>{@code Entity#canCollideWith}: {@code Entity#collide} gets its entity colliders from {@code
 *       EntityGetter#getEntityCollisions}, which keeps only entities the mover can collide with.
 *   <li>{@code LivingEntity#pushEntities}: the ghast's own push of the entities it overlaps.
 *   <li>{@code LivingEntity#isPushable}: vanilla pushes go both ways. A mob's own {@code
 *       pushEntities} finds the ghast through {@code EntitySelector#pushableBy} and {@code
 *       Entity#push} then moves the mob away (the ghast itself stays: it is a vehicle). Not
 *       pushable also lets boats pass ({@code AbstractBoat#canVehicleCollide}).
 * </ul>
 *
 * <p>Being pushed is already vanilla-blocked for a ridden ghast ({@code Entity#push} checks {@code
 * isVehicle}).
 */
@Mixin(HappyGhast.class)
abstract class HappyGhastEntityPassMixin extends Animal {
  protected HappyGhastEntityPassMixin(EntityType<? extends Animal> type, Level level) {
    super(type, level);
  }

  @Override
  public boolean canCollideWith(Entity entity) {
    return super.canCollideWith(entity) && !ghastglide$passesThroughEntities();
  }

  @Override
  public boolean isPushable() {
    return super.isPushable() && !ghastglide$passesThroughEntities();
  }

  @Override
  protected void pushEntities() {
    if (!ghastglide$passesThroughEntities()) {
      super.pushEntities();
    }
  }

  @Unique
  private boolean ghastglide$passesThroughEntities() {
    return GlideRules.passesThroughEntities((HappyGhast) (Object) this);
  }
}

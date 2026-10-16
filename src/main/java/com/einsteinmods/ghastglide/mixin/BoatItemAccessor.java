package com.einsteinmods.ghastglide.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.BoatItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The boat type a {@link BoatItem} places, as {@code BoatItem#getBoat} creates it. */
@Mixin(BoatItem.class)
public interface BoatItemAccessor {
  @Accessor("entityType")
  EntityType<? extends AbstractBoat> ghastglide$entityType();
}

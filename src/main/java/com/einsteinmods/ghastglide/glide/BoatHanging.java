package com.einsteinmods.ghastglide.glide;

import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.mixin.BoatItemAccessor;
import java.util.OptionalDouble;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Hangs a boat under the Happy Ghast its rider uses a boat item on, tied with vanilla's quad leash
 * ({@code AbstractBoat#supportQuadLeash}, {@code HappyGhast#supportQuadLeashAsHolder}). Costs
 * nothing per tick or frame: it only runs on that interaction.
 *
 * <p>Both sides run the same checks, so the client predicts the hand swing and does not go on to
 * use the boat on a block; only the server spawns the boat and consumes items.
 */
public final class BoatHanging {
  /**
   * How many block steps the search goes down when the spot right under the ghast is blocked. The
   * lowest candidate keeps the boat centre about {@code 2 + 0.56 + 8 - 0.28 ≈ 10.3} blocks from the
   * ghast centre, inside the boat's leash snap distance ({@code Leashable#leashSnapDistance}, 12),
   * so the new leash never breaks on its first tick.
   */
  static final int MAX_DROP_BLOCKS = 8;

  /**
   * Gap between the ghast's bottom and the boat's top. Vanilla entity collision checks inflate the
   * box by {@code 1e-7} ({@code EntityGetter#getEntityCollisions}), so a boat touching the ghast
   * would count as blocked whenever the ghast is collidable.
   */
  static final double GAP_BELOW_GHAST = 1.0E-3;

  private static final int NO_SLOT = -1;

  private BoatHanging() {}

  /**
   * True when {@link #tryHang} would hang the boat held in {@code hand}: {@code player} rides this
   * adult ghast, has the setting on, carries a lead and a free spot exists below. Lets the client
   * target its own vehicle only when the click will really hang a boat.
   */
  public static boolean canHang(HappyGhast ghast, Player player, InteractionHand hand) {
    return hangBottom(ghast, player, hand).isPresent();
  }

  /**
   * Hangs the held boat if {@code player} rides this adult ghast, has the setting on and carries a
   * lead; returns {@link InteractionResult#PASS} (vanilla goes on) otherwise or when no free spot
   * is found. Survival and adventure consume one boat and one lead; creative consumes nothing.
   */
  public static InteractionResult tryHang(HappyGhast ghast, Player player, InteractionHand hand) {
    OptionalDouble boatBottom = hangBottom(ghast, player, hand);
    if (boatBottom.isEmpty()) {
      return InteractionResult.PASS;
    }
    if (!(player.level() instanceof ServerLevel level)) {
      return InteractionResult.SUCCESS;
    }
    ItemStack boatStack = player.getItemInHand(hand);
    if (!(boatStack.getItem() instanceof BoatItem boatItem)) {
      return InteractionResult.PASS;
    }
    AbstractBoat boat = boatType(boatItem).create(level, EntitySpawnReason.SPAWN_ITEM_USE);
    if (boat == null) {
      return InteractionResult.PASS;
    }
    // Same setup as BoatItem#use / BoatItem#getBoat.
    boat.setInitialPos(ghast.getX(), boatBottom.getAsDouble(), ghast.getZ());
    EntityType.<AbstractBoat>createDefaultStackConfig(level, boatStack, player).apply(boat);
    boat.setYRot(ghast.getYRot());
    if (!level.addFreshEntity(boat)) {
      return InteractionResult.PASS;
    }
    boat.setLeashedTo(ghast, true);
    boat.playSound(SoundEvents.LEAD_TIED);
    level.gameEvent(player, GameEvent.ENTITY_PLACE, boat.position());
    boatStack.consume(1, player);
    Inventory inventory = player.getInventory();
    inventory.getItem(findLeadSlot(inventory)).consume(1, player);
    player.awardStat(Stats.ITEM_USED.get(boatItem));
    return InteractionResult.SUCCESS;
  }

  /** Bottom Y of the boat to hang, or empty when any {@link #tryHang} condition fails. */
  private static OptionalDouble hangBottom(HappyGhast ghast, Player player, InteractionHand hand) {
    if (player.getVehicle() != ghast || ghast.isBaby()) {
      return OptionalDouble.empty();
    }
    if (!(player.getItemInHand(hand).getItem() instanceof BoatItem boatItem)) {
      return OptionalDouble.empty();
    }
    GlideSettings settings = GlideRules.settingsFor(player);
    if (settings == null
        || !settings.hangBoatWithClick()
        || findLeadSlot(player.getInventory()) == NO_SLOT) {
      return OptionalDouble.empty();
    }
    return freeBoatBottom(ghast, boatType(boatItem).getDimensions());
  }

  private static EntityType<? extends AbstractBoat> boatType(BoatItem boatItem) {
    return ((BoatItemAccessor) boatItem).ghastglide$entityType();
  }

  /** First inventory slot (hands included) holding a lead, else {@link #NO_SLOT}. */
  private static int findLeadSlot(Inventory inventory) {
    int size = inventory.getContainerSize();
    for (int slot = 0; slot < size; slot++) {
      if (inventory.getItem(slot).is(Items.LEAD)) {
        return slot;
      }
    }
    return NO_SLOT;
  }

  /**
   * Bottom Y for a boat centred under the ghast: right under its box when free, else the first free
   * spot resting on a block surface going down, at most {@link #MAX_DROP_BLOCKS} steps.
   */
  private static OptionalDouble freeBoatBottom(HappyGhast ghast, EntityDimensions boatSize) {
    Level level = ghast.level();
    double x = ghast.getX();
    double z = ghast.getZ();
    double underGhast = ghast.getBoundingBox().minY - GAP_BELOW_GHAST - boatSize.height();
    if (level.noCollision(boatSize.makeBoundingBox(x, underGhast, z))) {
      return OptionalDouble.of(underGhast);
    }
    int highestBlockSurface = Mth.floor(underGhast);
    for (int drop = 0; drop < MAX_DROP_BLOCKS; drop++) {
      double bottom = highestBlockSurface - drop;
      if (level.noCollision(boatSize.makeBoundingBox(x, bottom, z))) {
        return OptionalDouble.of(bottom);
      }
    }
    return OptionalDouble.empty();
  }
}

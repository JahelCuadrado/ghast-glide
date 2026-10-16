package com.einsteinmods.ghastglide.client;

import com.einsteinmods.ghastglide.glide.GlideRules;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;

/**
 * Picks the key named in vanilla's "mount.onboard" message ("Press %s to dismount"). On a server
 * with the mod the pilot of a Happy Ghast flying with the mod's controls dismounts with the mod's
 * key, because sneak descends there; everywhere else (vanilla controls included) sneak really
 * dismounts, so vanilla's text stays. An unbound dismount key also keeps vanilla's text: there is
 * no key to name, and that is the player's binding choice.
 */
public final class MountMessageKey {
  private MountMessageKey() {}

  /** Key name to show when {@code passenger} has just mounted {@code vehicle}. */
  public static Component keyName(Component vanillaKeyName, Entity vehicle, Entity passenger) {
    KeyMapping dismount = GhastGlideClient.keyMappings().dismount();
    boolean namesDismountKey =
        namesDismountKey(
            isHappyGhastPilot(vehicle, passenger),
            GhastGlideClient.state().hasModdedServer(),
            GlideRules.usesModControls(GhastGlideClient.state().requested()),
            !dismount.isUnbound());
    return namesDismountKey ? dismount.getTranslatedKeyMessage() : vanillaKeyName;
  }

  /**
   * Pure decision: name the dismount key only for the pilot of a Happy Ghast on a server with the
   * mod flying with the mod's controls (glide replaces sneak there) and only if that key is bound.
   */
  static boolean namesDismountKey(
      boolean happyGhastPilot,
      boolean moddedServer,
      boolean modControls,
      boolean dismountKeyBound) {
    return happyGhastPilot && moddedServer && modControls && dismountKeyBound;
  }

  /**
   * First passenger of a harnessed Happy Ghast, i.e. who will steer it. Deliberately not {@code
   * getControllingPassenger()}: that is null during the ghast's still timeout, whose synced flag
   * may not have cleared yet when the passengers packet arrives.
   */
  private static boolean isHappyGhastPilot(Entity vehicle, Entity passenger) {
    return vehicle instanceof HappyGhast ghast
        && ghast.isWearingBodyArmor()
        && ghast.getFirstPassenger() == passenger;
  }
}

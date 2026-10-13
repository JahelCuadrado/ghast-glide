package com.einsteinmods.ghastglide.client.mixin;

import com.einsteinmods.ghastglide.client.MountMessageKey;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Names the mod's dismount key, instead of sneak, in the "mount.onboard" overlay and narration
 * shown when the local player mounts. Hooked on the sneak key name passed to that message, so the
 * vanilla text and translations are reused.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
  @ModifyExpressionValue(
      method = "handleSetEntityPassengersPacket",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/KeyMapping;getTranslatedKeyMessage()Lnet/minecraft/network/chat/Component;"))
  private Component ghastglide$nameDismountKey(
      Component shiftKeyName,
      @Local(ordinal = 0) Entity vehicle,
      @Local(ordinal = 1) Entity passenger) {
    return MountMessageKey.keyName(shiftKeyName, vehicle, passenger);
  }
}

package com.einsteinmods.ghastglide.network;

import com.einsteinmods.ghastglide.GhastGlide;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: call the player's last-ridden Happy Ghast. */
public record CallGhastPayload() implements CustomPacketPayload {
  public static final CallGhastPayload INSTANCE = new CallGhastPayload();
  public static final Type<CallGhastPayload> TYPE = new Type<>(GhastGlide.id("call_ghast"));
  public static final StreamCodec<RegistryFriendlyByteBuf, CallGhastPayload> CODEC =
      StreamCodec.unit(INSTANCE);

  @Override
  public Type<CallGhastPayload> type() {
    return TYPE;
  }
}

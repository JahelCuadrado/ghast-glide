package com.einsteinmods.ghastglide.network;

import com.einsteinmods.ghastglide.GhastGlide;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: dismount request (sneak no longer dismounts while gliding). */
public record DismountPayload() implements CustomPacketPayload {
  public static final DismountPayload INSTANCE = new DismountPayload();
  public static final Type<DismountPayload> TYPE = new Type<>(GhastGlide.id("dismount"));
  public static final StreamCodec<RegistryFriendlyByteBuf, DismountPayload> CODEC =
      StreamCodec.unit(INSTANCE);

  @Override
  public Type<DismountPayload> type() {
    return TYPE;
  }
}

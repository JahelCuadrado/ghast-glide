package com.einsteinmods.ghastglide.network;

import com.einsteinmods.ghastglide.GhastGlide;
import com.einsteinmods.ghastglide.core.ServerPolicy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: limits in force. Its arrival also tells the client the server has the mod. */
public record ServerPolicyPayload(ServerPolicy policy) implements CustomPacketPayload {
  public static final Type<ServerPolicyPayload> TYPE = new Type<>(GhastGlide.id("policy"));

  public static final StreamCodec<RegistryFriendlyByteBuf, ServerPolicyPayload> CODEC =
      StreamCodec.of(
          (buffer, payload) -> {
            buffer.writeBoolean(payload.policy().allowNoclip());
            buffer.writeDouble(payload.policy().maxSpeedMultiplier());
          },
          buffer ->
              new ServerPolicyPayload(
                  new ServerPolicy(buffer.readBoolean(), buffer.readDouble()).sanitized()));

  @Override
  public Type<ServerPolicyPayload> type() {
    return TYPE;
  }
}

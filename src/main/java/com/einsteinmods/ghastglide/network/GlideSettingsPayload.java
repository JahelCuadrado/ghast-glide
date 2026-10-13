package com.einsteinmods.ghastglide.network;

import com.einsteinmods.ghastglide.GhastGlide;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.core.LeaveMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the player's requested settings. Sent on join and whenever they change. */
public record GlideSettingsPayload(GlideSettings settings) implements CustomPacketPayload {
  public static final Type<GlideSettingsPayload> TYPE = new Type<>(GhastGlide.id("settings"));

  public static final StreamCodec<RegistryFriendlyByteBuf, GlideSettingsPayload> CODEC =
      StreamCodec.of(
          (buffer, payload) -> {
            GlideSettings value = payload.settings();
            buffer.writeDouble(value.horizontalMultiplier());
            buffer.writeDouble(value.verticalMultiplier());
            buffer.writeDouble(value.sprintMultiplier());
            buffer.writeDouble(value.accelerationSeconds());
            buffer.writeBoolean(value.noclipEnabled());
            buffer.writeByte(value.leaveMode().id());
          },
          buffer ->
              new GlideSettingsPayload(
                  new GlideSettings(
                          buffer.readDouble(),
                          buffer.readDouble(),
                          buffer.readDouble(),
                          buffer.readDouble(),
                          buffer.readBoolean(),
                          LeaveMode.fromId(buffer.readByte()))
                      .sanitized()));

  @Override
  public Type<GlideSettingsPayload> type() {
    return TYPE;
  }
}

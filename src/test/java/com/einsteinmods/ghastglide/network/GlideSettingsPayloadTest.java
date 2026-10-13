package com.einsteinmods.ghastglide.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.core.LeaveMode;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class GlideSettingsPayloadTest {
  private static final int UNKNOWN_LEAVE_MODE_ID = 99;

  private static GlideSettings roundTrip(GlideSettings settings) {
    RegistryFriendlyByteBuf buffer =
        new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    GlideSettingsPayload.CODEC.encode(buffer, new GlideSettingsPayload(settings));
    return GlideSettingsPayload.CODEC.decode(buffer).settings();
  }

  @Test
  void roundTripKeepsDefaults() {
    assertEquals(GlideSettings.DEFAULTS, roundTrip(GlideSettings.DEFAULTS));
  }

  @Test
  void roundTripKeepsEveryLeaveMode() {
    for (LeaveMode mode : LeaveMode.values()) {
      GlideSettings settings = GlideSettings.DEFAULTS.withLeaveMode(mode);

      assertEquals(settings, roundTrip(settings));
    }
  }

  @Test
  void unknownLeaveModeIdDecodesAsDefault() {
    RegistryFriendlyByteBuf buffer =
        new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    GlideSettings defaults = GlideSettings.DEFAULTS;
    buffer.writeDouble(defaults.horizontalMultiplier());
    buffer.writeDouble(defaults.verticalMultiplier());
    buffer.writeDouble(defaults.sprintMultiplier());
    buffer.writeDouble(defaults.accelerationSeconds());
    buffer.writeBoolean(defaults.noclipEnabled());
    buffer.writeByte(UNKNOWN_LEAVE_MODE_ID);

    assertEquals(
        LeaveMode.DEFAULT, GlideSettingsPayload.CODEC.decode(buffer).settings().leaveMode());
  }
}

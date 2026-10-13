package com.einsteinmods.ghastglide.glide;

import com.einsteinmods.ghastglide.core.FlightInput;
import com.einsteinmods.ghastglide.core.GlideSettings;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Client-side view of glide state, installed by the client entrypoint. */
public interface ClientGlideBridge {
  /** Used on dedicated servers and before the client entrypoint runs. */
  ClientGlideBridge NONE =
      new ClientGlideBridge() {
        @Override
        public @Nullable GlideSettings effectiveSettings(Player rider) {
          return null;
        }

        @Override
        public FlightInput input(Player rider) {
          return FlightInput.NONE;
        }

        @Override
        public boolean pitchSteering(Player rider) {
          return false;
        }
      };

  /** Effective settings for {@code rider}, or null when glide is unavailable for them. */
  @Nullable GlideSettings effectiveSettings(Player rider);

  FlightInput input(Player rider);

  /** True when forward follows the camera pitch (servers without the mod). */
  boolean pitchSteering(Player rider);
}

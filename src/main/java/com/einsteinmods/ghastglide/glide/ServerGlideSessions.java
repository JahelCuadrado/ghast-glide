package com.einsteinmods.ghastglide.glide;

import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.core.PlayerPolicyResolver;
import com.einsteinmods.ghastglide.core.ServerPolicy;
import com.einsteinmods.ghastglide.core.SettingsResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Policy and effective glide settings of every player that announced the mod. Accessed only from
 * the server thread (connection events, payload handlers and entity ticks).
 */
public final class ServerGlideSessions {
  private static final Map<UUID, GlideSettings> SESSIONS = new HashMap<>();
  private static final Map<UUID, ServerPolicy> PLAYER_POLICIES = new HashMap<>();
  private static ServerPolicy filePolicy = ServerPolicy.UNRESTRICTED;

  private ServerGlideSessions() {}

  public static void start(ServerPolicy serverFilePolicy) {
    SESSIONS.clear();
    PLAYER_POLICIES.clear();
    filePolicy = serverFilePolicy.sanitized();
  }

  public static void stop() {
    SESSIONS.clear();
    PLAYER_POLICIES.clear();
  }

  /** Policy loaded from the server file; applies to every player except the singleplayer host. */
  public static ServerPolicy filePolicy() {
    return filePolicy;
  }

  /** Records the policy of a joining player and returns it. */
  public static ServerPolicy join(UUID playerId, boolean singleplayerOwner) {
    ServerPolicy policy = PlayerPolicyResolver.forPlayer(singleplayerOwner, filePolicy);
    PLAYER_POLICIES.put(playerId, policy);
    return policy;
  }

  public static void update(UUID playerId, GlideSettings requested) {
    ServerPolicy policy = PLAYER_POLICIES.getOrDefault(playerId, filePolicy);
    SESSIONS.put(playerId, SettingsResolver.effective(requested, policy));
  }

  public static void remove(UUID playerId) {
    SESSIONS.remove(playerId);
    PLAYER_POLICIES.remove(playerId);
  }

  public static @Nullable GlideSettings get(Player player) {
    return SESSIONS.get(player.getUUID());
  }
}

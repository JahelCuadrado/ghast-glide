package com.einsteinmods.ghastglide.config;

import com.einsteinmods.ghastglide.core.ServerPolicy;

/**
 * On-disk shape of {@code config/ghastglide-server.json} (every server; never applied to the
 * singleplayer/LAN host).
 */
public record ServerPolicyFile(int schemaVersion, boolean allowNoclip, double maxSpeedMultiplier) {
  public static final int CURRENT_SCHEMA_VERSION = 1;
  public static final String FILE_NAME = "ghastglide-server.json";

  public static final ServerPolicyFile DEFAULTS = from(ServerPolicy.DEDICATED_DEFAULTS);

  public static ServerPolicyFile from(ServerPolicy policy) {
    return new ServerPolicyFile(
        CURRENT_SCHEMA_VERSION, policy.allowNoclip(), policy.maxSpeedMultiplier());
  }

  public ServerPolicy toPolicy() {
    return new ServerPolicy(allowNoclip, maxSpeedMultiplier).sanitized();
  }
}

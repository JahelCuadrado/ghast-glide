package com.einsteinmods.ghastglide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlayerPolicyResolverTest {
  private static final ServerPolicy RESTRICTIVE_FILE_POLICY = new ServerPolicy(false, 2.0);

  @Test
  void singleplayerOwnerIsNeverLimited() {
    assertEquals(
        ServerPolicy.UNRESTRICTED, PlayerPolicyResolver.forPlayer(true, RESTRICTIVE_FILE_POLICY));
  }

  @Test
  void otherPlayersFollowFilePolicy() {
    assertEquals(
        RESTRICTIVE_FILE_POLICY, PlayerPolicyResolver.forPlayer(false, RESTRICTIVE_FILE_POLICY));
  }

  @Test
  void filePolicyIsSanitized() {
    ServerPolicy outOfRange = new ServerPolicy(true, Double.NaN);

    assertEquals(outOfRange.sanitized(), PlayerPolicyResolver.forPlayer(false, outOfRange));
  }
}

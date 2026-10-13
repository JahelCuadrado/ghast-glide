package com.einsteinmods.ghastglide.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MountMessageKeyTest {
  @Test
  void namesDismountKeyForPilotOnModdedServerWithBoundKey() {
    assertTrue(MountMessageKey.namesDismountKey(true, true, true));
  }

  @Test
  void keepsSneakForPassengersAndOtherVehicles() {
    assertFalse(MountMessageKey.namesDismountKey(false, true, true));
  }

  @Test
  void keepsSneakInClientOnlyModeOrBeforeServerPolicy() {
    assertFalse(MountMessageKey.namesDismountKey(true, false, true));
  }

  @Test
  void keepsSneakWhenDismountKeyIsUnbound() {
    assertFalse(MountMessageKey.namesDismountKey(true, true, false));
  }
}

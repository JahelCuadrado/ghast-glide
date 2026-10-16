package com.einsteinmods.ghastglide.core;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ControlSchemeTest {
  @Test
  void idsRoundTripAndUnknownOnesAreModControls() {
    for (ControlScheme scheme : ControlScheme.values()) {
      assertSame(scheme, ControlScheme.fromId(scheme.id()));
    }
    assertSame(ControlScheme.DEFAULT, ControlScheme.fromId(-1));
    assertSame(ControlScheme.MOD, ControlScheme.DEFAULT);
  }
}

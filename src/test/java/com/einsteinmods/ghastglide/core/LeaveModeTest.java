package com.einsteinmods.ghastglide.core;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class LeaveModeTest {
  @Test
  void idsAndNamesRoundTripAndUnknownOnesAreDefault() {
    for (LeaveMode mode : LeaveMode.values()) {
      assertSame(mode, LeaveMode.fromId(mode.id()));
      assertSame(mode, LeaveMode.fromName(mode.name()));
    }
    assertSame(LeaveMode.DEFAULT, LeaveMode.fromId(-1));
    assertSame(LeaveMode.DEFAULT, LeaveMode.fromName("natural"));
    assertSame(LeaveMode.NATURAL, LeaveMode.DEFAULT);
  }

  @Test
  void formerIdleDriftMapsToNaturalOrStill() {
    assertSame(LeaveMode.NATURAL, LeaveMode.fromIdleDrift(true));
    assertSame(LeaveMode.STILL, LeaveMode.fromIdleDrift(false));
  }
}

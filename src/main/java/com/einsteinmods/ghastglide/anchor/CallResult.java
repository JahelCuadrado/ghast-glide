package com.einsteinmods.ghastglide.anchor;

/** Outcome of a call request. */
public enum CallResult {
  /** The ghast is on its way. */
  STARTED,
  /** No last-ridden ghast loaded in the caller's level within range. */
  OUT_OF_RANGE,
  /** The ghast is ridden or leashed; the call is ignored. */
  BUSY
}

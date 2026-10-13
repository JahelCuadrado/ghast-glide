package com.einsteinmods.ghastglide.core;

/**
 * Rider input for one tick.
 *
 * @param strafe left/right impulse as computed by the client (vanilla {@code xxa})
 * @param forward forward/backward impulse (vanilla {@code zza})
 * @param ascend jump key held
 * @param descend sneak key held
 * @param sprint sprint key held
 * @param yawDegrees rider camera yaw
 * @param pitchDegrees rider camera pitch (vanilla {@code xRot}: positive looks down)
 */
public record FlightInput(
    float strafe,
    float forward,
    boolean ascend,
    boolean descend,
    boolean sprint,
    float yawDegrees,
    float pitchDegrees) {

  public static final FlightInput NONE = new FlightInput(0.0F, 0.0F, false, false, false, 0.0F);

  /** Input with a level look (pitch 0). */
  public FlightInput(
      float strafe,
      float forward,
      boolean ascend,
      boolean descend,
      boolean sprint,
      float yawDegrees) {
    this(strafe, forward, ascend, descend, sprint, yawDegrees, 0.0F);
  }

  /** -1 descend, 0 hover, 1 ascend; both keys cancel out like in creative. */
  public int verticalDirection() {
    return (ascend ? 1 : 0) - (descend ? 1 : 0);
  }
}

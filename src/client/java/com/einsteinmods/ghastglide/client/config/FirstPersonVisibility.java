package com.einsteinmods.ghastglide.client.config;

/** How the local player sees part of their ridden Happy Ghast while the camera is first person. */
public enum FirstPersonVisibility {
  NORMAL,
  HIDDEN,
  SEMI_TRANSPARENT;

  public static final FirstPersonVisibility DEFAULT = NORMAL;
}

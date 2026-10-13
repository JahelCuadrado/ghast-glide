package com.einsteinmods.ghastglide.anchor;

import com.einsteinmods.ghastglide.core.LeaveMode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/**
 * Where an anchored ghast was left: {@code origin} is its position (box bottom centre) at dismount
 * or arrival, {@code restYaw} its yaw then, and {@code leaveMode} the dismounting (or calling)
 * player's setting. Natural idle drift stays within a few blocks of {@code origin} at its height
 * and turns within a bounded angle of {@code restYaw}; it never changes either. A {@link
 * LeaveMode#VANILLA} anchor only lasts until the ghast is unstuck or has arrived.
 */
public record AnchorRest(Vec3 origin, float restYaw, LeaveMode leaveMode) {
  /** Unknown names read as the default mode. */
  private static final Codec<LeaveMode> LEAVE_MODE_CODEC =
      Codec.STRING.xmap(LeaveMode::fromName, LeaveMode::name);

  /**
   * Also reads anchors saved with the former {@code idle_drift} boolean (true → NATURAL, false →
   * STILL); writes only {@code leave_mode}.
   */
  public static final Codec<AnchorRest> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Vec3.CODEC.fieldOf("origin").forGetter(AnchorRest::origin),
                      Codec.FLOAT.fieldOf("rest_yaw").forGetter(AnchorRest::restYaw),
                      LEAVE_MODE_CODEC
                          .optionalFieldOf("leave_mode")
                          .forGetter(rest -> Optional.of(rest.leaveMode())),
                      Codec.BOOL.optionalFieldOf("idle_drift").forGetter(rest -> Optional.empty()))
                  .apply(
                      instance,
                      (origin, restYaw, leaveMode, idleDrift) ->
                          new AnchorRest(
                              origin,
                              restYaw,
                              leaveMode.orElseGet(
                                  () ->
                                      idleDrift
                                          .map(LeaveMode::fromIdleDrift)
                                          .orElse(LeaveMode.DEFAULT)))));

  public AnchorRest withOrigin(Vec3 newOrigin) {
    return new AnchorRest(newOrigin, restYaw, leaveMode);
  }
}

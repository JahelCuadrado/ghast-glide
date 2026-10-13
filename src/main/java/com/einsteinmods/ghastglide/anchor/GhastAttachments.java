package com.einsteinmods.ghastglide.anchor;

import com.einsteinmods.ghastglide.GhastGlide;
import com.mojang.serialization.Codec;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;

/** Data attachments for anchoring and calling Happy Ghasts. Server-side only (never synced). */
public final class GhastAttachments {
  /** Present on an anchored ghast. Saved with the entity. */
  public static final AttachmentType<Boolean> ANCHORED =
      AttachmentRegistry.createPersistent(GhastGlide.id("anchored"), Codec.BOOL);

  /**
   * Present on an anchored ghast: where it was left and whether it drifts idly there. Saved with
   * the entity; anchors saved before it existed get one where they stand ({@link GhastIdleDrift}).
   */
  public static final AttachmentType<AnchorRest> ANCHOR_REST =
      AttachmentRegistry.createPersistent(GhastGlide.id("anchor_rest"), AnchorRest.CODEC);

  /** Current idle drift phase of an anchored ghast. Transient: a reload starts with a pause. */
  public static final AttachmentType<IdleDriftPhase> IDLE_DRIFT =
      AttachmentRegistry.create(GhastGlide.id("idle_drift"));

  /** Present while a ghast flies to the player that called it. Transient: a call is short. */
  public static final AttachmentType<GhastCall> CALL =
      AttachmentRegistry.create(GhastGlide.id("call"));

  /** Present while an anchored ghast moves out of blocks or water. Transient: a reload ends it. */
  public static final AttachmentType<UnstuckMove> UNSTUCK =
      AttachmentRegistry.create(GhastGlide.id("unstuck"));

  /**
   * Present while a dismounted ghast left inside blocks or water waits for players to step off its
   * head before it starts its {@link #UNSTUCK} move. Transient: a reload ends it.
   */
  public static final AttachmentType<Boolean> UNSTUCK_PENDING =
      AttachmentRegistry.create(GhastGlide.id("unstuck_pending"));

  /** On a player: the Happy Ghast they rode last. Saved with the player and kept on respawn. */
  public static final AttachmentType<UUID> LAST_RIDDEN_GHAST =
      AttachmentRegistry.create(
          GhastGlide.id("last_ridden_ghast"),
          builder -> builder.persistent(UUIDUtil.CODEC).copyOnDeath());

  private GhastAttachments() {}

  /** Forces class initialisation so the types are registered during mod init. */
  public static void register() {
    // Registration happens in the static field initialisers above.
  }
}

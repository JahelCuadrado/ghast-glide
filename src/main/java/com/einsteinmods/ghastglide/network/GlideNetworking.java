package com.einsteinmods.ghastglide.network;

import com.einsteinmods.ghastglide.anchor.GhastCalls;
import com.einsteinmods.ghastglide.core.ServerPolicy;
import com.einsteinmods.ghastglide.glide.ServerGlideSessions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;

/** Registers payloads and server-side handlers. Handlers run on the server thread. */
public final class GlideNetworking {
  private GlideNetworking() {}

  public static void register() {
    PayloadTypeRegistry.serverboundPlay()
        .register(GlideSettingsPayload.TYPE, GlideSettingsPayload.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(DismountPayload.TYPE, DismountPayload.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(CallGhastPayload.TYPE, CallGhastPayload.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(ServerPolicyPayload.TYPE, ServerPolicyPayload.CODEC);

    ServerPlayNetworking.registerGlobalReceiver(
        GlideSettingsPayload.TYPE,
        (payload, context) ->
            ServerGlideSessions.update(context.player().getUUID(), payload.settings()));

    ServerPlayNetworking.registerGlobalReceiver(
        DismountPayload.TYPE,
        (payload, context) -> {
          if (context.player().getVehicle() instanceof HappyGhast) {
            context.player().stopRiding();
          }
        });

    ServerPlayNetworking.registerGlobalReceiver(
        CallGhastPayload.TYPE,
        (payload, context) -> GhastCalls.requestCall(context.player(), context.player().level()));

    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, server) -> {
          ServerPolicy policy =
              ServerGlideSessions.join(
                  handler.player.getUUID(), server.isSingleplayerOwner(handler.player.nameAndId()));
          if (ServerPlayNetworking.canSend(handler.player, ServerPolicyPayload.TYPE)) {
            sender.sendPacket(new ServerPolicyPayload(policy));
          }
        });
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> ServerGlideSessions.remove(handler.player.getUUID()));
  }
}

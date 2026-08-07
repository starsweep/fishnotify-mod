package com.fishnotify.mixin;

import com.fishnotify.client.FishNotifyClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bite detection that actually works on real dedicated multiplayer servers,
 * not just singleplayer/LAN.
 *
 * Confirmed against the decompiled bytecode of XPlus AutoFish 1.5.1
 * (mc26.2 build)'s MixinClientPacketListener + FishMonitorMPSound classes,
 * which use this exact technique: the server broadcasts a
 * "minecraft:entity.fishing_bobber.splash" sound packet
 * (ClientboundSoundPacket, which carries a raw world position rather than
 * an entity reference) any time a nearby bobber splashes - including bite
 * splashes. We filter that down to "near our own hook" so we don't alert on
 * other players' bites too.
 *
 * This is purely a client-side network observation: no server-only game
 * logic needs to run for it to fire. That's the key difference from the
 * earlier approach of hooking FishingHook#catchingFish() directly - that
 * method only ever executes on the authoritative server, which on a real
 * dedicated server is a separate process our client-only mod never touches
 * at all. This sound-packet approach works identically in singleplayer,
 * LAN, and public multiplayer, because the client always receives this
 * packet regardless of where the server logic actually ran.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void fishnotify$onPlaySound(ClientboundSoundPacket packet, CallbackInfo ci) {
        Holder<SoundEvent> soundHolder = packet.getSound();
        if (soundHolder == null) return;

        String id = soundHolder.value().location().toString();
        if (!"minecraft:entity.fishing_bobber.splash".equalsIgnoreCase(id)) return;

        FishNotifyClient.onSplashSoundPacket(packet.getX(), packet.getY(), packet.getZ());
    }
}

package com.fishnotify.client;

import com.fishnotify.config.FishNotifyConfig;
import com.fishnotify.gui.FishNotifyConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
// CONFIRMED: ResourceLocation was renamed to Identifier in 1.21.11, before 26.1 existed.
// 26.2 still uses Identifier - this is not a guess.
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

public class FishNotifyClient implements ClientModInitializer {

    // How close (squared, in blocks²) a splash sound has to be to our own
    // hook to count as "our bite" rather than some other player's bobber
    // splashing nearby. 4.0 = within 2 blocks. Loosen this if you get
    // missed bites, tighten it if you get pinged by neighbors fishing.
    private static final double HOOK_SOUND_DISTANCE_SQ_THRESHOLD = 4.0;

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("fishnotify", "fishnotify"));

    private static KeyMapping openConfigKey;

    // Tracks whether we're still waiting on a reel-in, for the repeat-alert
    // feature. We watch client.player.fishing for going null as the signal
    // that the bobber is gone (reeled in or despawned) - that field is kept
    // in sync with the server's actual fishing state regardless of how we
    // detected the bite.
    private static boolean awaitingReel;
    private static int ticksSinceBite;
    private static int ticksSinceLastRepeat;

    @Override
    public void onInitializeClient() {
        FishNotifyConfig.init();

        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fishnotify.open_config",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_APOSTROPHE,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(FishNotifyClient::onClientTick);
    }

    /**
     * Called from ClientPacketListenerMixin the instant the client receives
     * a "fishing_bobber.splash" sound packet from the server. This is a
     * purely client-side signal (see the mixin's javadoc for why that
     * matters), so it works on real dedicated multiplayer servers, not just
     * singleplayer.
     */
    public static void onSplashSoundPacket(double x, double y, double z) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        FishingHook hook = player.fishing;
        if (hook == null) return; // we're not even fishing right now - ignore

        double distSq = hook.distanceToSqr(x, y, z);
        if (distSq > HOOK_SOUND_DISTANCE_SQ_THRESHOLD) return; // someone else's bobber, not ours

        FishNotifyConfig cfg = FishNotifyConfig.get();
        if (!cfg.enabled) return;

        if (cfg.requireRodInHand && !isHoldingFishingRod(player)) return;

        awaitingReel = true;
        ticksSinceBite = 0;
        ticksSinceLastRepeat = 0;

        // Packet handling already runs on the client's main thread (unlike
        // the old catchingFish-based approach, which could fire from the
        // integrated-server thread in singleplayer), but we still hop
        // through client.execute() defensively - it's a no-op if we're
        // already on the right thread.
        client.execute(() -> triggerAlert(cfg));
    }

    private static void triggerAlert(FishNotifyConfig cfg) {
        SoundPlayer.playAlert();

        if (cfg.showActionBarAlert) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                // UNVERIFIED: displayClientMessage(component, boolean) is gone in 26.2 and the
                // action-bar flag doesn't appear to have a documented 1:1 replacement yet.
                // sendSystemMessage puts this in chat rather than the action bar - if you need
                // a real action-bar toast, look at Hud#setOverlayMessage
                // (Minecraft.getInstance().gui.hud.setOverlayMessage(...)) instead, which the
                // 26.2 primer does confirm as the action-bar/overlay message API.
                client.player.sendSystemMessage(Component.translatable("message.fishnotify.bite"));
            }
        }
    }

    private static void onClientTick(Minecraft client) {
        if (openConfigKey.consumeClick()) {
            client.gui.setScreen(new FishNotifyConfigScreen(client.gui.screen()));
        }

        if (!awaitingReel) return;

        FishNotifyConfig cfg = FishNotifyConfig.get();
        ticksSinceBite++;
        ticksSinceLastRepeat++;

        boolean hookGone = client.player == null || client.player.fishing == null;

        if (hookGone) {
            awaitingReel = false;
            return;
        }

        if (cfg.alertRepeatTicks > 0 && ticksSinceLastRepeat >= cfg.alertRepeatTicks) {
            ticksSinceLastRepeat = 0;
            triggerAlert(cfg);
        }

        // Safety valve: stop repeating after 20s even if something upstream
        // never clears the hook reference (e.g. an odd disconnect edge case).
        if (ticksSinceBite > 400) {
            awaitingReel = false;
        }
    }

    private static boolean isHoldingFishingRod(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.getItem() instanceof FishingRodItem || off.getItem() instanceof FishingRodItem;
    }
}

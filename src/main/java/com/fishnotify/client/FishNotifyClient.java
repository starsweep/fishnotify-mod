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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

public class FishNotifyClient implements ClientModInitializer {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("fishnotify", "fishnotify"));

    private static KeyMapping openConfigKey;

    // Tracks the currently-biting hook so we can (a) stop repeat-alerts once
    // it's gone (reeled in or despawned) and (b) avoid re-triggering on the
    // same bite every tick.
    private static FishingHook activeBitingHook;
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

    /** Called from FishingHookMixin the instant a fish takes the bait. */
    public static void onFishBite(FishingHook hook) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        Object owner = hook.getOwner();
        if (owner != player) return; // only alert for the local player's own bobber

        FishNotifyConfig cfg = FishNotifyConfig.get();
        if (!cfg.enabled) return;

        if (cfg.requireRodInHand && !isHoldingFishingRod(player)) return;

        activeBitingHook = hook;
        ticksSinceBite = 0;
        ticksSinceLastRepeat = 0;

        triggerAlert(cfg);
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

        if (activeBitingHook == null) return;

        FishNotifyConfig cfg = FishNotifyConfig.get();
        ticksSinceBite++;
        ticksSinceLastRepeat++;

        boolean hookGone = activeBitingHook.isRemoved()
                || client.player == null
                || client.player.fishing != activeBitingHook;

        if (hookGone) {
            activeBitingHook = null;
            return;
        }

        if (cfg.alertRepeatTicks > 0 && ticksSinceLastRepeat >= cfg.alertRepeatTicks) {
            ticksSinceLastRepeat = 0;
            triggerAlert(cfg);
        }

        // Safety valve: stop repeating after 20s even if something upstream
        // never clears the hook reference (e.g. an odd disconnect edge case).
        if (ticksSinceBite > 400) {
            activeBitingHook = null;
        }
    }

    private static boolean isHoldingFishingRod(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.getItem() instanceof FishingRodItem || off.getItem() instanceof FishingRodItem;
    }
}

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

public class FishNotifyClient implements ClientModInitializer {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("fishnotify", "fishnotify"));

    private static KeyMapping openConfigKey;

    // Tracks whether we're still waiting on a reel-in, for the repeat-alert
    // feature. Deliberately doesn't hold a reference to the FishingHook
    // itself: the hook instance passed into onFishBite() may be the
    // integrated-server's copy (see the UUID comparison above for why), so
    // comparing it against client.player.fishing (the client-side synced
    // copy) later would be comparing two different objects. Instead we just
    // watch client.player.fishing for going null, which is a reliable
    // client-side signal that the bobber is gone (reeled in or despawned).
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

    /** Called from FishingHookMixin the instant a fish takes the bait. */
    public static void onFishBite(FishingHook hook) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        Object owner = hook.getOwner();
        // Compare by UUID, not object identity: in singleplayer, catchingFish()
        // runs on the integrated server thread against a ServerPlayer instance,
        // which is a different Java object than the client's LocalPlayer even
        // though it's the same real player. A reference-equality check here
        // silently fails every single bite.
        if (!(owner instanceof Player ownerPlayer) || !ownerPlayer.getUUID().equals(player.getUUID())) {
            return; // only alert for the local player's own bobber
        }

        FishNotifyConfig cfg = FishNotifyConfig.get();
        if (!cfg.enabled) return;

        if (cfg.requireRodInHand && !isHoldingFishingRod(player)) return;

        awaitingReel = true;
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

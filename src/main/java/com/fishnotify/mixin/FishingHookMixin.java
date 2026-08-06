package com.fishnotify.mixin;

import com.fishnotify.client.FishNotifyClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FishingHook#catchingFish(BlockPos) is the exact vanilla method that fires
 * the moment a fish bites and the bobber starts its splash animation -
 * i.e. the moment you're supposed to reel in. Confirmed against the
 * decompiled bytecode of XPlus AutoFish 1.5.1 (mc26.2 build), which hooks
 * the same method for its own bite-detection logic.
 */
@Mixin(FishingHook.class)
public class FishingHookMixin {

    @Inject(method = "catchingFish", at = @At("TAIL"))
    private void fishnotify$onCatchingFish(BlockPos pos, CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        FishNotifyClient.onFishBite(self);
    }
}

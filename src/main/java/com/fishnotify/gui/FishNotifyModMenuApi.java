package com.fishnotify.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Only loaded by Fabric Loader if Mod Menu is actually installed (it's
 * invoked via the "modmenu" entrypoint, which Mod Menu itself scans for -
 * see fabric.mod.json's "suggests" entry for modmenu, not "depends").
 * Lets FishNotify's settings show up in the normal Mods screen in
 * addition to the in-game keybind.
 */
public class FishNotifyModMenuApi implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FishNotifyConfigScreen::new;
    }
}

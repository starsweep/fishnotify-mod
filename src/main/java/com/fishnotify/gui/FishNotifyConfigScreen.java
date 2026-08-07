package com.fishnotify.gui;

import com.fishnotify.client.SoundPlayer;
import com.fishnotify.config.FishNotifyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FishNotifyConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_WIDTH = 240;

    private final Screen parent;
    private final FishNotifyConfig cfg;

    private VolumeSlider volumeSlider;
    private List<String> soundOptions;
    private Button removeSoundButton;

    public FishNotifyConfigScreen(Screen parent) {
        super(Component.translatable("screen.fishnotify.title"));
        this.parent = parent;
        this.cfg = FishNotifyConfig.get();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 110;

        this.addRenderableWidget(new StringWidget(
                centerX - WIDGET_WIDTH / 2, this.height / 2 - 130, WIDGET_WIDTH, 20,
                this.title, this.font
        ));

        // Enabled toggle
        this.addRenderableWidget(CycleButton.onOffBuilder(cfg.enabled)
                .create(centerX - WIDGET_WIDTH / 2, y, WIDGET_WIDTH, 20,
                        Component.translatable("option.fishnotify.enabled"),
                        (button, value) -> {
                            cfg.enabled = value;
                            cfg.save();
                        }));
        y += ROW_HEIGHT;

        // Volume slider (0-200%)
        volumeSlider = new VolumeSlider(centerX - WIDGET_WIDTH / 2, y, WIDGET_WIDTH, 20, cfg.volume);
        this.addRenderableWidget(volumeSlider);
        y += ROW_HEIGHT;

        // Sound selector
        soundOptions = buildSoundOptionList();
        String initialSound = soundOptions.contains(cfg.selectedSoundId) ? cfg.selectedSoundId : FishNotifyConfig.defaultSoundId();
        CycleButton<String> soundButton = CycleButton.<String>builder(this::soundOptionLabel, initialSound)
                .withValues(soundOptions)
                .create(centerX - WIDGET_WIDTH / 2, y, WIDGET_WIDTH, 20,
                        Component.translatable("option.fishnotify.sound"),
                        (button, value) -> {
                            cfg.selectedSoundId = value;
                            cfg.save();
                            updateRemoveButtonState();
                        });
        this.addRenderableWidget(soundButton);
        y += ROW_HEIGHT;

        // Preview / Upload / Remove row
        int thirdWidth = (WIDGET_WIDTH - 8) / 3;
        this.addRenderableWidget(Button.builder(Component.translatable("button.fishnotify.preview"), b ->
                        SoundPlayer.previewSound(cfg.selectedSoundId, volumeSlider.currentVolume()))
                .bounds(centerX - WIDGET_WIDTH / 2, y, thirdWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("button.fishnotify.upload"), b -> openUploadDialog())
                .bounds(centerX - WIDGET_WIDTH / 2 + thirdWidth + 4, y, thirdWidth, 20)
                .build());

        removeSoundButton = Button.builder(Component.translatable("button.fishnotify.remove"), b -> removeSelectedSound())
                .bounds(centerX - WIDGET_WIDTH / 2 + (thirdWidth + 4) * 2, y, thirdWidth, 20)
                .build();
        this.addRenderableWidget(removeSoundButton);
        updateRemoveButtonState();
        y += ROW_HEIGHT;

        // Action bar alert toggle
        this.addRenderableWidget(CycleButton.onOffBuilder(cfg.showActionBarAlert)
                .create(centerX - WIDGET_WIDTH / 2, y, WIDGET_WIDTH, 20,
                        Component.translatable("option.fishnotify.action_bar"),
                        (button, value) -> {
                            cfg.showActionBarAlert = value;
                            cfg.save();
                        }));
        y += ROW_HEIGHT;

        // Require fishing rod in hand toggle
        this.addRenderableWidget(CycleButton.onOffBuilder(cfg.requireRodInHand)
                .create(centerX - WIDGET_WIDTH / 2, y, WIDGET_WIDTH, 20,
                        Component.translatable("option.fishnotify.require_rod"),
                        (button, value) -> {
                            cfg.requireRodInHand = value;
                            cfg.save();
                        }));
        y += ROW_HEIGHT;

        // Repeat alert interval
        this.addRenderableWidget(CycleButton.<Integer>builder(this::repeatLabel, cfg.alertRepeatTicks)
                .withValues(0, 20, 40, 60)
                .create(centerX - WIDGET_WIDTH / 2, y, WIDGET_WIDTH, 20,
                        Component.translatable("option.fishnotify.repeat"),
                        (button, value) -> {
                            cfg.alertRepeatTicks = value;
                            cfg.save();
                        }));
        y += ROW_HEIGHT + 10;

        // Done
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(centerX - 75, y, 150, 20)
                .build());
    }

    private List<String> buildSoundOptionList() {
        List<String> options = new ArrayList<>();
        options.add(FishNotifyConfig.defaultSoundId());
        options.addAll(cfg.importedSoundFiles);
        return options;
    }

    private Component soundOptionLabel(String id) {
        if (FishNotifyConfig.defaultSoundId().equals(id)) {
            return Component.translatable("option.fishnotify.sound.default");
        }
        return Component.literal(id);
    }

    private Component repeatLabel(int ticks) {
        if (ticks <= 0) return Component.translatable("option.fishnotify.repeat.off");
        return Component.translatable("option.fishnotify.repeat.seconds", ticks / 20);
    }

    private void updateRemoveButtonState() {
        if (removeSoundButton != null) {
            removeSoundButton.active = !FishNotifyConfig.defaultSoundId().equals(cfg.selectedSoundId);
        }
    }

    private void removeSelectedSound() {
        if (FishNotifyConfig.defaultSoundId().equals(cfg.selectedSoundId)) return;
        cfg.removeImportedSound(cfg.selectedSoundId);
        // Rebuild the screen so the cycle button's option list reflects the removal.
        Minecraft.getInstance().gui.setScreen(new FishNotifyConfigScreen(parent));
    }

    /**
     * Opens a native OS file picker on a background thread (java.awt.FileDialog
     * keeps its own event loop and won't block Minecraft's render/tick thread),
     * then hops back onto the client thread to actually copy the file and
     * touch our config/widgets.
     */
    private void openUploadDialog() {
        Thread t = new Thread(() -> {
            FileDialog dialog = new FileDialog((Frame) null, "Select a sound file (WAV recommended)", FileDialog.LOAD);
            dialog.setFilenameFilter((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".mp3") || lower.endsWith(".aiff") || lower.endsWith(".au");
            });
            dialog.setVisible(true);
            String file = dialog.getFile();
            String dir = dialog.getDirectory();
            if (file == null || dir == null) return; // user cancelled

            Path selected = new File(dir, file).toPath();
            Minecraft.getInstance().execute(() -> {
                try {
                    String id = cfg.importSound(selected);
                    cfg.selectedSoundId = id;
                    cfg.save();
                    // Rebuild so the sound cycle button picks up the new option.
                    Minecraft.getInstance().gui.setScreen(new FishNotifyConfigScreen(parent));
                } catch (IOException e) {
                    System.err.println("FishNotify: failed to import sound: " + e.getMessage());
                }
            });
        }, "FishNotify-FileDialog");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onClose() {
        cfg.volume = volumeSlider.currentVolume();
        cfg.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A 0-200% volume slider that writes straight into the live config on release. */
    private class VolumeSlider extends AbstractSliderButton {
        VolumeSlider(int x, int y, int width, int height, float initialVolume) {
            super(x, y, width, height, Component.empty(), initialVolume / 2.0);
            updateMessage();
        }

        float currentVolume() {
            return (float) (this.value * 2.0);
        }

        @Override
        protected void updateMessage() {
            int percent = Math.round(currentVolume() * 100);
            this.setMessage(Component.translatable("option.fishnotify.volume", percent));
        }

        @Override
        protected void applyValue() {
            cfg.volume = currentVolume();
            cfg.save();
        }
    }
}

package com.fishnotify.client;

import com.fishnotify.config.FishNotifyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.*;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Plays the bite-alert sound.
 *
 * The built-in default uses Minecraft's own sound engine (the vanilla
 * experience-orb pickup "ding", SoundEvents.EXPERIENCE_ORB_PICKUP) so it
 * benefits from 3D positioning niceties and respects being routed through
 * the Master/Players volume sliders like any other UI cue.
 *
 * Custom uploaded sounds are NOT run through Minecraft's sound engine -
 * teaching the vanilla engine a brand new sound at runtime means writing a
 * dynamic resource pack, which is a lot of moving parts for a notification
 * ping. Instead we play them directly via javax.sound.sampled, with our
 * own independent gain control. This only reliably supports WAV/AIFF/AU
 * (Java's built-in decoders) - if you upload an OGG or MP3, convert it to
 * WAV first (e.g. with Audacity or ffmpeg: `ffmpeg -i in.ogg out.wav`).
 */
public final class SoundPlayer {
    private static Clip activeClip;

    private SoundPlayer() {}

    public static void playAlert() {
        FishNotifyConfig cfg = FishNotifyConfig.get();
        if (!cfg.enabled) return;

        if (FishNotifyConfig.defaultSoundId().equals(cfg.selectedSoundId)) {
            playVanillaDing(cfg.volume);
        } else {
            playCustomWav(cfg.resolveImportedSoundPath(cfg.selectedSoundId), cfg.volume);
        }
    }

    /** Used by the "Preview" button in the config screen. */
    public static void previewSound(String soundId, float volume) {
        if (FishNotifyConfig.defaultSoundId().equals(soundId)) {
            playVanillaDing(volume);
        } else {
            playCustomWav(FishNotifyConfig.get().resolveImportedSoundPath(soundId), volume);
        }
    }

    private static void playVanillaDing(float volume) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        // volume is user-facing 0.0-2.0; pitch fixed slightly high so it reads as a crisp "notification" rather than the muffled pickup sound
        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                volume,
                1.4f,
                false
        );
    }

    private static void playCustomWav(Path path, float volume) {
        if (path == null || !path.toFile().exists()) {
            System.err.println("FishNotify: selected custom sound file is missing, falling back to default ding: " + path);
            playVanillaDing(volume);
            return;
        }
        // Run on a separate thread - AudioSystem file I/O can briefly block and we never want to hitch the render/tick thread.
        Thread t = new Thread(() -> {
            try {
                stopActiveClip();
                AudioInputStream stream = AudioSystem.getAudioInputStream(path.toFile());
                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                applyGain(clip, volume);
                clip.start();
                synchronized (SoundPlayer.class) {
                    activeClip = clip;
                }
            } catch (UnsupportedAudioFileException e) {
                System.err.println("FishNotify: unsupported audio format for " + path.getFileName()
                        + " - please use a WAV file (convert OGG/MP3 with a tool like ffmpeg or Audacity).");
            } catch (IOException | LineUnavailableException e) {
                System.err.println("FishNotify: failed to play custom sound: " + e.getMessage());
            }
        }, "FishNotify-SoundPlayer");
        t.setDaemon(true);
        t.start();
    }

    private static void applyGain(Clip clip, float volume) {
        // volume is linear 0.0-2.0 (100% = unchanged file volume). Convert to decibels for MASTER_GAIN.
        float clamped = Math.max(0.0001f, volume);
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float db = (float) (20.0 * Math.log10(clamped));
            db = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db));
            gain.setValue(db);
        }
    }

    private static synchronized void stopActiveClip() {
        if (activeClip != null) {
            try {
                if (activeClip.isRunning()) activeClip.stop();
                activeClip.close();
            } catch (Exception ignored) {
            }
            activeClip = null;
        }
    }
}

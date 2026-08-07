package com.fishnotify.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles FishNotify's settings and the custom bite-alert sounds folder.
 *
 * Layout on disk:
 *   config/fishnotify/config.json  - settings (volume, enabled, selected sound, etc.)
 *   config/fishnotify/sounds/*     - imported custom sound files (WAV recommended, see SoundPlayer)
 */
public class FishNotifyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // Sentinel value
    private static final String DEFAULT_SOUND_ID = "__default_ding__";

    private static Path configDir;
    private static Path configFile;
    private static Path soundsDir;

    private static FishNotifyConfig instance;

    // ---- persisted fields ----
    public boolean enabled = true;
    public float volume = 1.0f; // linear scale, 0.0-2.0 (100% = unmodified volume)
    public String selectedSoundId = DEFAULT_SOUND_ID;
    public boolean showActionBarAlert = false; // off by default
    public boolean requireRodInHand = true;
    public int alertRepeatTicks = 0; // 0 = play once; >0 repeats the alert every N ticks until you reel in
    public List<String> importedSoundFiles = new ArrayList<>();

    public static void init() {
        configDir = FabricLoader.getInstance().getConfigDir().resolve("fishnotify");
        configFile = configDir.resolve("config.json");
        soundsDir = configDir.resolve("sounds");
        try {
            Files.createDirectories(soundsDir);
        } catch (IOException e) {
            throw new RuntimeException("FishNotify: could not create config directory", e);
        }
        instance = load();
    }

    public static FishNotifyConfig get() {
        if (instance == null) init();
        return instance;
    }

    public static Path getSoundsDir() {
        return soundsDir;
    }

    public static String defaultSoundId() {
        return DEFAULT_SOUND_ID;
    }

    private static FishNotifyConfig load() {
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                FishNotifyConfig loaded = GSON.fromJson(reader, FishNotifyConfig.class);
                if (loaded != null) return loaded;
            } catch (IOException e) {
                System.err.println("FishNotify: failed to read config, using defaults: " + e.getMessage());
            }
        }
        return new FishNotifyConfig();
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            System.err.println("FishNotify: failed to save config: " + e.getMessage());
        }
    }

    /**
     * Copies a user-picked sound file into the mod's sounds folder and
     * registers it as an importable option. Custom sounds are played back
     * via javax.sound.sampled (see SoundPlayer), which reliably supports
     * WAV; other formats will be copied but may fail to play.
     */
    public String importSound(Path sourceFile) throws IOException {
        String fileName = sourceFile.getFileName().toString();
        Path dest = soundsDir.resolve(fileName);

        int counter = 1;
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
        while (Files.exists(dest)) {
            dest = soundsDir.resolve(baseName + "_" + counter + ext);
            counter++;
        }

        Files.copy(sourceFile, dest, StandardCopyOption.REPLACE_EXISTING);

        String id = dest.getFileName().toString();
        if (!importedSoundFiles.contains(id)) {
            importedSoundFiles.add(id);
        }
        save();
        return id;
    }

    public void removeImportedSound(String fileName) {
        importedSoundFiles.remove(fileName);
        try {
            Files.deleteIfExists(soundsDir.resolve(fileName));
        } catch (IOException e) {
            System.err.println("FishNotify: failed to delete sound file: " + e.getMessage());
        }
        if (selectedSoundId.equals(fileName)) {
            selectedSoundId = DEFAULT_SOUND_ID;
        }
        save();
    }

    public Path resolveImportedSoundPath(String fileName) {
        return soundsDir.resolve(fileName);
    }
}

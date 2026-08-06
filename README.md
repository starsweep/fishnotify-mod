# FishNotify

A Fabric client mod for Minecraft 26.2 ("Chaos Cubed") that plays an alert
the instant a fish bites your line, so you never miss the window to reel in.

## Features

- **Bite detection** hooked directly into `FishingHook.catchingFish(BlockPos)`
  - the exact vanilla method that fires at the moment of the bite (found by
  decompiling XPlus AutoFish's 26.2 build and reusing its proven hook point).
- **Default alert sound**: the vanilla experience-orb pickup "ding"
  (`minecraft:entity.experience_orb.pickup`), played through Minecraft's own
  sound engine so it's positioned on you and respects the game's normal
  audio pipeline.
- **Custom sound upload**: pick any local audio file via a native file
  picker and it plays instead of the default. Played via `javax.sound.sampled`
  with its own independent gain control, so it works even if you have the
  in-game Players/Master volume sliders turned down. **Use WAV files** - Java's
  built-in audio decoder doesn't reliably handle OGG/MP3; convert those with
  something like `ffmpeg -i in.ogg out.wav` or Audacity first.
- **Independent volume slider** (0-200%) for the alert, separate from every
  other in-game volume slider.
- **In-game GUI** (default keybind: `'` apostrophe, rebindable in Controls)
  to toggle alerts on/off, pick/preview/upload/remove sounds, adjust volume,
  toggle an action-bar text alert, require the rod to be in hand before
  alerting, and optionally repeat the alert every 1-3 seconds until you
  actually reel in (handy if you tab out or aren't looking at the screen).
- Settings persist to `config/fishnotify/config.json`; uploaded sounds are
  copied into `config/fishnotify/sounds/`.
- **Mod Menu integration** (optional/soft dependency): if you have Mod Menu
  installed, FishNotify's settings button shows up right on its entry in the
  mods list, opening the same screen as the keybind.

This mod is intentionally **alert-only** - it does not auto-reel or
auto-recast for you. Full auto-fishing bots (like the AutoFish mod this was
built alongside) can violate a lot of servers' rules; a notification sound
keeps you in control while still solving the "I looked away and missed it"
problem.

## Project layout

```
build.gradle, settings.gradle, gradle.properties   - Gradle/Loom build config
src/main/java/com/fishnotify/
  mixin/FishingHookMixin.java     - hooks the vanilla bite method
  client/FishNotifyClient.java    - client entrypoint, keybind, alert logic
  client/SoundPlayer.java         - plays the default or custom sound
  config/FishNotifyConfig.java    - settings persistence + sound import
  gui/FishNotifyConfigScreen.java - the settings screen
src/main/resources/
  fabric.mod.json, fishnotify.mixins.json
  assets/fishnotify/lang/en_us.json
```

## Dependencies

| Mod | Version | Used for |
|---|---|---|
| Fabric API | 0.156.0+26.2 | `fabric-key-mapping-api-v1` (the settings keybind) and `fabric-lifecycle-events-v1` (the client tick loop that drives repeat-alerts and the keybind check) |
| Mod Menu | 20.0.1 | Optional — adds FishNotify's settings screen to the normal Mods list. Confirmed against the real `ModMenuApi`/`ConfigScreenFactory` classes in your jar (`getModConfigScreenFactory()` returning a `ConfigScreenFactory<Screen>`). Not required to launch; declared under `"suggests"` in `fabric.mod.json`, and referenced with `modCompileOnly` in Gradle so the jar builds fine without it and just won't add the Mods-screen entry if it's absent. |

## Building

Requires **JDK 25** (Minecraft 26.x's new minimum) and Gradle (the wrapper
will bootstrap itself - if `gradle-wrapper.jar` isn't present, run
`gradle wrapper` once with any Gradle 9.x install, or open the folder in
IntelliJ IDEA with the Fabric/Loom plugin, which will generate it for you).

```bash
./gradlew build
```

The built mod jar will be in `build/libs/fishnotify-1.0.0.jar`. Drop it in
your `mods/` folder alongside Fabric Loader 0.19.3+ and Fabric API
0.156.0+26.2 for Minecraft 26.2.

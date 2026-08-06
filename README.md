# FishNotify

A Fabric client mod for Minecraft 26.2  that plays an alert
when a fish bites your line.

## Features

- **Bite detection** hooks into `FishingHook.catchingFish(BlockPos)`
- **Default alert sound**: plays default exp chime (`minecraft:entity.experience_orb.pickup`) directly through sound engine so you can hear and control the volume
- **Custom sound upload**: replace the default noise and play your own sounds via `javax.sound.sampled`
  with independent gain control. Supports .wav
- **Independent volume slider** (0-200%) for the alert, separate from
  other in-game volume sliders.
- **In-game GUI** (default keybind: `'` apostrophe)
- Settings persist to `config/fishnotify/config.json`; uploaded sounds are
  copied into `config/fishnotify/sounds/`.
- **Mod Menu integration** (optional/soft dependency)

This mod is intentionally **alert-only** - it does NOT auto-reel or
auto-recast for you. Built for server rule compliance.

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
| Mod Menu | 20.0.1 | Optional - adds FishNotify's settings screen to the normal Mods list. Confirmed against the real `ModMenuApi`/`ConfigScreenFactory` classes in your jar (`getModConfigScreenFactory()` returning a `ConfigScreenFactory<Screen>`). Not required to launch; declared under `"suggests"` in `fabric.mod.json`, and referenced with `modCompileOnly` in Gradle so the jar builds fine without it and just won't add the Mods-screen entry if it's absent. |

## Building

Requires **JDK 25** and **Gradle 9.x**

```bash
./gradlew build
```

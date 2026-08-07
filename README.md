# FishNotify

A Fabric client mod for Minecraft 26.2 that plays an alert when a fish bites your line.

## Features

- **Alert sound**: plays default exp chime (`minecraft:entity.experience_orb.pickup`) directly through sound engine. you can replace the default noise and play your own sounds via `javax.sound.sampled` with independent gain control. Supports .wav.
- **In-game GUI** (default keybind: `'` apostrophe)
- Settings persist to `config/fishnotify/config.json`; uploaded sounds are copied into `config/fishnotify/sounds/`.
- **Mod Menu integration** (optional/soft dependency)

This mod is intentionally **alert-only** - it does NOT auto-reel or auto-recast for you. Built for server rule compliance.

## Project layout

```
build.gradle, settings.gradle, gradle.properties   - Gradle/Loom build config
src/main/java/com/fishnotify/
  mixin/ClientPacketListenerMixin.java - watches for bite splash sound packet
  client/FishNotifyClient.java    - client entrypoint, keybind, alert logic
  client/SoundPlayer.java         - plays sound
  config/FishNotifyConfig.java    - settings persistence + sound import
  gui/FishNotifyConfigScreen.java - settings screen
src/main/resources/
  fabric.mod.json, fishnotify.mixins.json
  assets/fishnotify/lang/en_us.json
```

## Dependencies

| Mod | Version | Used for |
|---|---|---|
| Fabric API | 0.156.0+26.2 | `fabric-key-mapping-api-v1` and `fabric-lifecycle-events-v1` |
| Mod Menu | 20.0.1 | Optional |

## Building

Requires **JDK 25** and **Gradle 9.x**

```bash
./gradlew build
```

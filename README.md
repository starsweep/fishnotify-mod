# FishNotify

A Fabric client mod for Minecraft 26.2  that plays an alert
when a fish bites your line.

## Features

- **Bite detection**, two independent signals, either can trigger the alert:
  1. Primary: watches for the `minecraft:entity.fishing_bobber.splash`
     sound packet the server broadcasts near your bobber - a purely
     client-side network observation, so it works on real dedicated
     multiplayer servers as well as singleplayer.
  2. Fallback: watches your own hook's Y position each client tick for the
     sharp downward jerk a bite causes. This catches the (rare) case where
     you're outside the server's sound-broadcast radius for that packet -
     you're still synced to your own hook's position regardless, so this
     signal doesn't depend on proximity/broadcast range the way the sound
     packet does. Modeled on AutoFish's `FishMonitorMPMotion`.
- **Default alert sound**: plays default exp chime (`minecraft:entity.experience_orb.pickup`) directly through sound engine.
- **Custom sound upload**: replace the default noise and play your own sounds via `javax.sound.sampled` with independent gain control. Supports .wav.
- **Independent volume slider** (0-200%) for the alert, separate from other in-game volume sliders.
- **In-game GUI** (default keybind: `'` apostrophe)
- Settings persist to `config/fishnotify/config.json`; uploaded sounds are copied into `config/fishnotify/sounds/`.
- **Mod Menu integration** (optional/soft dependency)

This mod is intentionally **alert-only** - it does NOT auto-reel or
auto-recast for you. Built for server rule compliance.

## Project layout

```
build.gradle, settings.gradle, gradle.properties   - Gradle/Loom build config
src/main/java/com/fishnotify/
  mixin/ClientPacketListenerMixin.java - watches for the bite splash sound packet
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
| Fabric API | 0.156.0+26.2 | `fabric-key-mapping-api-v1` and `fabric-lifecycle-events-v1` |
| Mod Menu | 20.0.1 | Optional |

## Building

Requires **JDK 25** and **Gradle 9.x**

```bash
./gradlew build
```

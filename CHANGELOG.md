# Changelog

## 1.0.0 — 2026-10-13

First public release for Minecraft 26.3 (Fabric).

- Fly a harnessed Happy Ghast like creative flight: WASD relative to the camera, Space to rise,
  Sneak to descend, Sprint to speed up; smooth, adjustable acceleration.
- Speed presets: Vanilla, Calm, Recommended (default, ~6 blocks/s), Fast, Creative and Custom.
- Optional block pass-through: the ghast ignores blocks while the rider still collides, never
  ending up inside blocks.
- No suffocation, no dark texture and no disappearing when the ghast is partly inside blocks.
- When you leave the ghast: Natural (gentle drift and turns, default), Still or Vanilla.
- Call your last ridden ghast with a key (default `G`); it flies to you and faces you.
- A ghast left in water or buried in terrain rises out of it once you step off its head.
- Dismount with `Left Alt` (rebindable); the mount message shows your key.
- Settings screen with no extra dependencies (Mod Menu optional), key bindings inside it and an
  option to hide the ghast in first person.
- Works on servers without the mod (creative-style flight controls, no pass-through). Servers and
  LAN hosts can limit speed and pass-through for other players.
- Translated into 17 languages.

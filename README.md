# Ghast Glide

The Happy Ghast as it always should have been: a little bit of creative inside survival, to build comfortably anywhere.

Fabric mod for Minecraft 26.3.

## Features
- **Fly like in creative:** WASD moves where you look, Space goes up, Sneak goes down, Sprint for
  extra speed. Smooth, natural acceleration.
- **Pass through blocks:** your ghast glides through terrain, so you can stay with it comfortably
  while building in the air, inside mountains or in tight spaces. Turn it off whenever you want.
- **Balanced speed:** faster than a vanilla Happy Ghast, but much slower than elytra, so it is a
  building tool, not a replacement. Pick a preset (Vanilla, Calm, Recommended, Fast, Creative) or
  tune your own.
- **Call your ghast:** press a key (`G` by default) and it flies back to you.
- **Leave it how you like:** when you get off, it floats around naturally, stays completely still
  or wanders like vanilla. Your choice.
- **Easy dismount:** `Left Alt` gets you off, since Sneak is used to descend. Every key can be
  changed.
- **Simple settings screen** with no extra libraries (Mod Menu supported), including an option to
  hide the ghast in first person for a clear view while you build.
- **Lightweight:** no noticeable impact on performance.
- **Languages:** English, Español (España), Español (México), Português (Brasil), Português (Portugal),
  Français, Deutsch, Italiano, Nederlands, Polski, Türkçe, Русский, Українська, 简体中文, 繁體中文,
  日本語 and 한국어.

## Servers

Install it on both client and server for the full experience (singleplayer works out of the box).
On servers without the mod you still get the creative-style flight controls, without passing
through blocks. Server owners can limit speed and block pass-through for other players in
`config/ghastglide-server.json` (the singleplayer/LAN host is never limited):

```json
{ "schemaVersion": 1, "allowNoclip": true, "maxSpeedMultiplier": 15.0 }
```

## Development

Requires JDK 25. `./gradlew runClient` · `./gradlew build` (compile, format check, JUnit, GameTests)
· `./gradlew spotlessApply`.

## License

MIT.

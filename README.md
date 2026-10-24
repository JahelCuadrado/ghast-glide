# Ghast Glide

This mod is designed to make the Happy Ghast the tool it always should have been: it goes from
barely useful to an essential building tool.

A little bit of creative inside survival, to build comfortably anywhere.

Fabric mod for Minecraft 26.3.

## Features
- **Fly like in creative:** WASD moves where you look, Space goes up, Sneak goes down, Sprint for
  extra speed. Smooth, natural acceleration.
- **Pass through blocks:** your ghast glides through terrain, so you can stay with it comfortably
  while building in the air, inside mountains or in tight spaces. Turn it off whenever you want.
- **Glide through mobs and boats:** your ghast passes through boats, mobs and other ghasts
  without bumping into them or pushing them away (can be turned off).
- **Mine at normal speed:** break blocks while riding as fast as standing on the ground, instead
  of 5 times slower (can be turned off).
- **Balanced speed:** faster than a vanilla Happy Ghast, but much slower than elytra, so it is a
  building tool, not a replacement. Pick a preset (Vanilla, Calm, Recommended, Fast, Creative) or
  tune your own.
- **Call your ghast:** press a key (`G` by default) and it flies back, stopping a few blocks away.
- **Hang a boat from it:** right-click your ghast with a boat and a lead in your inventory to carry
  friends or mobs underneath.
- **Build through your ghast** (optional): place blocks where its body is while you ride.
- **Vanilla controls option:** fly the base-game way if you prefer, keeping every other feature.
- **Leave it how you like:** when you get off, it floats around naturally, stays completely still
  or wanders like vanilla. Your choice.
- **Easy dismount:** `Left Alt` gets you off, since Sneak is used to descend. Every key can be
  changed.
- **Clear view while you build:** choose how your ghast and its cargo (hanging boats, whoever sits
  in them, mobs on its leads) look in first person: Normal, Hidden or Semi-transparent.
- **Simple settings screen** with no extra libraries (Mod Menu supported).
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

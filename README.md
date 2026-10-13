# Ghast Glide

Fabric mod for Minecraft 26.3: fly a harnessed Happy Ghast like creative flight.

## Features

- WASD moves relative to the camera (pitch ignored), Space ascends, Sneak descends, Sprint speeds up.
- Optional block pass-through: the ghast ignores blocks while riders still collide.
- No suffocation damage and no dark texture for a ghast inside blocks.
- Default top speed ≈ 6 blocks/s (≈ 9 sprinting), so it does not replace elytra; ~2 s to reach it.
- Your ghast stays where you leave it, and you can call it back with a key. "When left" chooses
  what it does then: Natural (default) floats gently around that spot (up to 4 blocks sideways, or
  turning to look around; never upwards nor into blocks, and it only turns while someone stands
  on it), Still keeps it perfectly still, Vanilla lets it wander like a vanilla Happy Ghast.
- Built-in settings screen, no extra libraries needed, laid out like YACL config screens (options on
  the left, description of the hovered option on the right, Save / Undo / Reset): speed preset,
  horizontal / vertical speed, sprint multiplier, acceleration time, pass-through, when left, hide
  the ridden ghast in first person (only on your screen) and the mod keys. Open it with its key or
  from Mod Menu (optional).
- Translated into English, Spanish (Spain, Mexico), Portuguese (Brazil, Portugal), French, German,
  Italian, Russian, Ukrainian, Polish, Turkish, Dutch, Chinese (Simplified, Traditional), Japanese
  and Korean, using each language's official Minecraft terms.

## Speed presets

| Preset | Speed (sprint) | Up | Time to top speed |
| --- | --- | --- | --- |
| Vanilla | ≈3.3 blocks/s (no sprint) | ≈1.6 blocks/s | ≈1.2 s |
| Calm | 4 (6) blocks/s | 4.5 blocks/s | 2.5 s |
| Recommended (default) | 6 (9) blocks/s | 4.5 blocks/s | 2 s |
| Fast | 8 (12) blocks/s | 4.5 blocks/s | 1.5 s |
| Creative | ≈10.1 (≈20.2) blocks/s | 4.5 blocks/s | ≈1.2 s |
| Custom | your slider values | | |

Vanilla matches a vanilla ridden Happy Ghast flying level; Creative matches creative flight.
Moving any slider switches to Custom. Server speed caps still apply.

## Keys (rebindable in the mod settings or Options → Controls)

| Action | Default |
| --- | --- |
| Dismount | `Left Alt` |
| Call your last ridden ghast | `G` |
| Toggle pass-through | unbound |
| Open settings | unbound |

## Servers

Install on the client **and** the server (singleplayer works out of the box). On servers without
the mod you still get creative-style flight controls (look + forward to climb or dive) without
pass-through. Servers (dedicated or LAN) can restrict it in `config/ghastglide-server.json`; the
singleplayer/LAN host is never limited:

```json
{ "schemaVersion": 1, "allowNoclip": true, "maxSpeedMultiplier": 15.0 }
```

## Development

Requires JDK 25. `./gradlew runClient` · `./gradlew build` (compile, format check, JUnit, GameTests)
· `./gradlew spotlessApply`.

## License

MIT.

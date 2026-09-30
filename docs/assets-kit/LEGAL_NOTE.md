# Legal note — Battle City prototype assets

## Summary

Everything in `battle_city/` is original work created for this project. No data, code,
graphics or audio was extracted from any commercial ROM, emulator dump or third-party
game archive.

- **Graphics** — every sprite the game ships is drawn by a script in this repository:
  the board (tanks, tiles, the nest-and-egg base, bonuses, effects) by `docs/art/sprites.js`,
  the logotype by `docs/art/logo.js`, at a 16x16 unit tile size. The shapes are Ironroost's
  own — slate "crow" enemies with red eyes, a green player tank with a gold hatch, an iron egg
  in a steel nest for the base — and none is a copy of, or traced from, another game's art.
  Rerun the scripts rather than editing the PNGs.
- **Font** — Ironroost Pixel, derived from Pixelify Sans under the SIL Open Font License
  (`docs/fonts/`).
- **Audio** — the effects are original synthesised WAVs generated for this project. The menu
  and battle music is from "Retro Game Music Pack" by Juhani Junkala, released under CC0 and
  credited on the About screen.
- **Level data** (`data/levels/prototype_13x13/`) — all 35 stage layouts are generated
  procedurally for this project from an original set of layout families, then validated
  for symmetry, reachability and uniqueness; stage 1 was redrawn by hand on the same rules.
  They are not transcriptions of any published stage maps.

## Naming

"Battle City" is used in this repository only as an internal working name for the game
mode and its package (`com.aectann.classicgames.battlecity`). It is not used as a product
name, store title, or user-facing label. If this mode is ever shipped as a standalone
product, pick a distinct name and check it for trademark conflicts first.

## If you extend these assets

Keep the rule simple: anything added here must be either created for this project or
carry a licence that permits redistribution in a closed-source Android application.
Record the origin of any new file in `asset_manifest.json` and, when the origin is
external, add its licence text to this directory.

## Files

`asset_manifest.json` in the parent directory lists every file shipped with this kit.
Keep it in step with the contents of the folder.

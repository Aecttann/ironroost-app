# Legal note — Battle City prototype assets

## Summary

Everything in `battle_city/` is original work created for this project. No data, code,
graphics or audio was extracted from any commercial ROM, emulator dump or third-party
game archive.

- **Graphics** (`assets/graphics/`) — original pixel art drawn for this project at a
  16x16 unit tile size. The visual style follows the general conventions of 1980s NES-era
  tile games; no sprite is a copy of, or traced from, another game's artwork.
- **Audio** (`assets/audio/`) — original synthesised WAV effects and loops generated for
  this project. No sampled or recorded material from another game is included.
- **Level data** (`data/levels/prototype_13x13/`) — all 35 stage layouts are generated
  procedurally for this project from an original set of layout families, then validated
  for symmetry, reachability and uniqueness. They are not transcriptions of any published
  stage maps.

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

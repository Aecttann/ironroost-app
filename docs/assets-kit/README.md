# Battle City asset kit

Art, audio and level data for the tank game mode. See `LICENSES/LEGAL_NOTE.md` for the
origin of every category of file.

## Layout

```
asset_manifest.json          list of every shipped file
LICENSES/LEGAL_NOTE.md       provenance and naming rules
assets/graphics/tiles/       brick, steel, water, forest, ice (16x16)
assets/graphics/tanks/       player (green, 4 upgrade levels) and enemy tanks, 4 facings
assets/graphics/effects/     bullets, explosion, spawn and shield animations
assets/graphics/powerups/    the eight bonuses dropped by flashing enemies
assets/graphics/ui/          base eagle, life icon, stage flag
assets/graphics/spritesheets/ atlases of the same art, kept for editing convenience
assets/audio/sfx/            shot, explosions, wall hits, bonus, extra life, game over
assets/audio/music_loops/    stage start jingle, engine rumble loop
data/levels/prototype_13x13/ 35 stage files
```

## Level format

Each stage is a JSON file with a 13x13 `grid` of tile characters:

| char | tile   | blocks tanks | blocks bullets |
|------|--------|--------------|----------------|
| `.`  | empty  | no           | no             |
| `B`  | brick  | per quarter  | yes            |
| `S`  | steel  | yes          | yes (level 4 shells break it) |
| `W`  | water  | yes (unless the boat bonus is held) | no |
| `F`  | forest | no           | no             |
| `I`  | ice    | no, but the tank slides | no  |
| `H`  | base   | yes          | ends the stage |

`difficulty` is a 1..5 star rating and rises with the stage number; the engine uses it to
scale enemy fire and decision rates, and the stage picker shows it as stars.

`enemyGroups` totals 20 tanks per stage. The engine spreads the groups evenly across the
wave rather than sending them in blocks.

Stage thumbnails are drawn at runtime from `grid`, so there are no preview images to keep
in sync.

## Regenerating levels

Level files are produced by a standalone generator that guarantees, for every stage:
13x13 size, mirror symmetry, a walled base at (6,12), clear player and enemy spawns, a
route from every enemy spawn to the base, and a grid unique across all 35 stages.

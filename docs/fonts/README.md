# Fonts

## Ironroost Pixel

The UI font, `shared/src/commonMain/composeResources/font/ironroost_pixel.ttf`. It is
[Pixelify Sans](https://github.com/eifetx/Pixelify-Sans) by Stefie Justprince, licensed under the
SIL Open Font License 1.1 ([OFL.txt](OFL.txt)), with changes made by
[patch-pixelify.js](patch-pixelify.js):

- Pixelify Sans leaves four code points Ukrainian needs out of its character map: the capitals
  `О` (U+041E), `П` (U+041F), `І` (U+0406) and the apostrophe `ʼ` (U+02BC). The patch points them
  at glyphs already in the font with the same shape (Latin O, Greek Π, Latin I, `’`), so weights
  and kerning carry over. No outline is added or redrawn.
- It also has the capital `К` (U+041A) and the Macedonian `Ќ` (U+040C) the wrong way round: `К`
  was drawn with an acute, so "Колекція" and "Коротше" wore an accent. The patch points each at
  the other's glyph.
- The family is renamed "Ironroost Pixel", because "Pixelify Sans" is its author's trademark and
  the OFL asks that a modified font not present itself as the original. Copyright, licence and
  designer records inside the font are unchanged.

The modified font is under the same OFL 1.1. The credit in the game's About screen names Pixelify
Sans, its author and the licence.

### Rebuilding it

Source: `ofl/pixelifysans/PixelifySans[wght].ttf` in [google/fonts](https://github.com/google/fonts),
commit `9ce5017522020232f525003b39971ddb67e33243`, SHA-256
`9ba86cd010a4de309d263ceff8e8044092c9db7efda869620cb9ff1c4389e8a5`.

```powershell
node docs/fonts/patch-pixelify.js "PixelifySans[wght].ttf" shared/src/commonMain/composeResources/font/ironroost_pixel.ttf
```

The result's SHA-256 is `fc37426e40abbf5f443564babf7de87bf945da269ba8974047321377a2e2ae6f`. The
script refuses to run if a newer upstream file already maps any of the four code points, or no
longer has `К` as the taller of the pair.

### Coverage

Checked against every string in `composeResources/values*/strings.xml`: English, German, Turkish
and Ukrainian are fully covered. Chinese, Japanese, Korean and Hindi are not, and are not meant to
be: those locales exist only in the Android and iOS builds, which draw them with the system fonts.
The web build ships English only.

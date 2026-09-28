# Fonts

## Ironroost Pixel

The UI font, `shared/src/commonMain/composeResources/font/ironroost_pixel.ttf`. It is
[Pixelify Sans](https://github.com/eifetx/Pixelify-Sans) by Stefie Justprince, licensed under the
SIL Open Font License 1.1 ([OFL.txt](OFL.txt)), with one change made by
[patch-pixelify.js](patch-pixelify.js):

- Pixelify Sans leaves four code points Ukrainian needs out of its character map: the capitals
  `О` (U+041E), `П` (U+041F), `І` (U+0406) and the apostrophe `ʼ` (U+02BC). The patch points them
  at glyphs already in the font with the same shape (Latin O, Greek Π, Latin I, `’`), so weights
  and kerning carry over. No outline is added or redrawn.
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

The result's SHA-256 is `854a9a687a161452e9db241a2f1684b6e475410e54e9576387da87b7fe399c03`. The
script refuses to run if a newer upstream file already maps any of the four code points.

### Coverage

Checked against every string in `composeResources/values*/strings.xml`: English, German, Turkish
and Ukrainian are fully covered. Chinese, Japanese, Korean and Hindi are not, and are not meant to
be: those locales exist only in the Android and iOS builds, which draw them with the system fonts.
The web build ships English only.

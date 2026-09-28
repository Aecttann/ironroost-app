package com.aectann.battlecity.ui

import androidx.compose.ui.graphics.Color

/** One palette for every screen, matching the sprite set's own colours. */
internal val BoardBackground = Color(0xFF111111)
internal val ScreenBackground = Color(0xFF202020)
internal val MenuBackground = Color(0xFF1A1F28)
internal val AccentGold = Color(0xFFE0A03C)
internal val MutedText = Color(0xFFBFC5CC)
internal val PlayerGreen = Color(0xFF9BD16B)

/** Player two's colour, matching the yellow tank sprite. */
internal val PlayerYellow = Color(0xFFE8C15A)
internal val BrickRed = Color(0xFFB4522A)

// The pixel UI's materials: each is a face with a lit edge and a shaded one, the way the board's
// own brick and steel tiles are drawn, so a button reads as a block cut from the same map.

/** Outlines and hard text shadows. Not black: pure black swallows the board's darkest tiles. */
internal val Ink = Color(0xFF0B0D10)
internal val DisabledText = Color(0xFF6C7480)

internal val BrickFace = BrickRed
internal val BrickLight = Color(0xFFE07A48)
internal val BrickDark = Color(0xFF6E2C12)
internal val BrickMortar = Color(0xFF97431F)

internal val SteelFace = Color(0xFF55606E)
internal val SteelLight = Color(0xFF97A3B3)
internal val SteelDark = Color(0xFF30363F)

internal val GoldLight = Color(0xFFF6C874)
internal val GoldDark = Color(0xFF8A5C1A)

internal val PanelFace = Color(0xFF232A35)
internal val PanelLight = Color(0xFF354050)
internal val PanelDark = Color(0xFF12161C)

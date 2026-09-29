package com.aectann.battlecity.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aectann.battlecity.TanksClip

/**
 * What a control is cut from. Brick carries the main actions — it is the board's own wall, the
 * thing the whole game is about breaking. Steel carries everything secondary, gold the one
 * reward-shaped thing on a screen, and panel the quiet surfaces text sits on.
 */
internal enum class PixelMaterial(val face: Color, val light: Color, val dark: Color, val content: Color) {
    Brick(BrickFace, BrickLight, BrickDark, Color.White),
    Steel(SteelFace, SteelLight, SteelDark, Color.White),
    Gold(AccentGold, GoldLight, GoldDark, Ink),
    Panel(PanelFace, PanelLight, PanelDark, Color.White),
    /** The trigger: the one control that must be found without looking. */
    Alarm(Color(0xFFC8372A), Color(0xFFF07060), Color(0xFF6E1A12), Color.White),
    Disabled(Color(0xFF363C45), Color(0xFF434A54), Color(0xFF22262C), DisabledText)
}

/** How far a button's face stands above its base, in pixel units; a press takes one away. */
private const val BlockDepth = 2

/**
 * Draws a raised block: a one-unit outline with its corners cut, a base in the material's shade,
 * and the face on top of it lit along the top and left edges. [lift] is how many units of base
 * show below the face — [BlockDepth] at rest, less while pressed — so pressing reads as the
 * block sinking into the screen rather than as a colour change.
 */
internal fun DrawScope.drawPixelBlock(
    material: PixelMaterial,
    lift: Int,
    outline: Color = Ink,
    faceTint: Float = 0f,
    texture: Boolean = true,
    /** Where the block goes, for drawing several into one canvas; the whole node by default. */
    topLeft: Offset = Offset.Zero,
    blockSize: Size = size
) {
    val u = pixelUnit()
    val x = topLeft.x
    val y = topLeft.y
    val w = blockSize.width
    val h = blockSize.height
    drawRect(outline, Offset(x + u, y), Size(w - 2 * u, h))
    drawRect(outline, Offset(x, y + u), Size(w, h - 2 * u))
    drawRect(material.dark, Offset(x + u, y + u), Size(w - 2 * u, h - 2 * u))

    val top = y + u + (BlockDepth - lift) * u
    val faceHeight = h - 2 * u - BlockDepth * u
    val face = if (faceTint > 0f) lerp(material.face, material.light, faceTint) else material.face
    drawRect(face, Offset(x + u, top), Size(w - 2 * u, faceHeight))
    if (texture) {
        when (material) {
            PixelMaterial.Brick -> drawMortar(Offset(x + u, top), Size(w - 2 * u, faceHeight), u)
            PixelMaterial.Steel -> drawRivets(Offset(x + u, top), Size(w - 2 * u, faceHeight), u)
            else -> Unit
        }
    }
    drawRect(material.light, Offset(x + u, top), Size(w - 2 * u, u))
    drawRect(material.light, Offset(x + u, top), Size(u, faceHeight))
    drawRect(lerp(material.face, material.dark, 0.55f), Offset(x + w - 2 * u, top + u), Size(u, faceHeight - u))
}

/** Brick courses, staggered, faint enough that a label on top still reads. */
private fun DrawScope.drawMortar(origin: Offset, area: Size, u: Float) {
    val course = 6 * u
    val brick = 12 * u
    var row = 0
    var y = origin.y + course
    while (y < origin.y + area.height - u) {
        drawRect(BrickMortar, Offset(origin.x, y), Size(area.width, u))
        row++
        y += course
    }
    for (index in 0..row) {
        val rowTop = origin.y + index * course + if (index == 0) 0f else u
        val rowBottom = minOf(origin.y + (index + 1) * course, origin.y + area.height)
        var x = origin.x + if (index % 2 == 0) brick / 2 else brick
        while (x < origin.x + area.width - u) {
            drawRect(BrickMortar, Offset(x, rowTop), Size(u, rowBottom - rowTop))
            x += brick
        }
    }
}

/** A rivet in each corner of a steel plate: one lit unit with its shadow under it. */
private fun DrawScope.drawRivets(origin: Offset, area: Size, u: Float) {
    if (area.width < 12 * u || area.height < 8 * u) return
    val inset = 2 * u
    listOf(
        Offset(origin.x + inset, origin.y + inset),
        Offset(origin.x + area.width - inset - u, origin.y + inset),
        Offset(origin.x + inset, origin.y + area.height - inset - 2 * u),
        Offset(origin.x + area.width - inset - u, origin.y + area.height - inset - 2 * u)
    ).forEach { at ->
        drawRect(SteelLight, at, Size(u, u))
        drawRect(SteelDark, at + Offset(0f, u), Size(u, u))
    }
}

/**
 * The one button every screen is built from. Pointer, touch and keys all press it: Enter or
 * Space on the focused block press on the way down and click on the way up, like a real key
 * would, and the block sinks while it is held either way.
 *
 * The keyboard cursor — a lit outline and a pointer at the left of the face — shows only while
 * the player is steering with keys (see [PixelInputMode]).
 */
@Composable
internal fun PixelBlockButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    material: PixelMaterial = PixelMaterial.Brick,
    enabled: Boolean = true,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    minHeight: Dp = 44.dp,
    /** Hangs the gold "something is waiting" tag on the corner. */
    badge: Boolean = false,
    clickSound: Boolean = true,
    /** What a screen reader announces, for a button whose content is not text. */
    semanticLabel: String? = null,
    content: @Composable BoxScope.(contentColor: Color) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pointerPressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    var keyPressed by remember { mutableStateOf(false) }
    val inputMode = LocalPixelInputMode.current
    val sound = LocalTanksSound.current
    val press = {
        if (clickSound) sound?.play(TanksClip.MenuSelect)
        onClick()
    }

    val shown = if (enabled) material else PixelMaterial.Disabled
    val cursor = enabled && focused && inputMode.keyboard
    val sunk = enabled && (pointerPressed || keyPressed)
    val lift = when {
        sunk -> 0
        selected -> 1
        else -> BlockDepth
    }
    val unitPx = with(LocalDensity.current) { pixelUnit() }
    val unitDp = with(LocalDensity.current) { unitPx.toDp() }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = minHeight)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (semanticLabel != null) Modifier.semantics { contentDescription = semanticLabel } else Modifier)
            .onPreviewKeyEvent { event ->
                if (!enabled) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        if (event.type == KeyEventType.KeyDown) {
                            keyPressed = true
                            inputMode.keyboard = true
                        } else if (event.type == KeyEventType.KeyUp && keyPressed) {
                            keyPressed = false
                            press()
                        }
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { if (!it.isFocused) keyPressed = false }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = press
            )
            .drawWithContent {
                drawPixelBlock(
                    material = shown,
                    lift = lift,
                    outline = if (cursor) GoldLight else Ink,
                    faceTint = if (enabled && (hovered || cursor) && !sunk) 0.16f else 0f
                )
                if (cursor) {
                    val u = pixelUnit()
                    val face = size.height - 2 * u - BlockDepth * u
                    val top = u + (BlockDepth - lift) * u
                    drawPixelIcon(
                        PixelIcons.Cursor,
                        Offset(3 * u, top + ((face - PixelIcons.Cursor.height * u) / 2f / u).toInt() * u),
                        u,
                        Color.White
                    )
                }
                drawContent()
                if (badge) drawPixelBadge()
            }
            .padding(top = unitDp, bottom = unitDp * (1 + BlockDepth), start = unitDp, end = unitDp)
            .offset(y = unitDp * (BlockDepth - lift))
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        content(shown.content)
    }
}

/**
 * The "something is waiting" tag: a small gold block with a pixel exclamation mark, hanging over
 * the top-right corner of the button it belongs to.
 *
 * Drawn with the button rather than laid out beside it. As a separate child it had to be sized
 * and aligned against the button's own constraints, and wherever a button filled its column the
 * tag ended up out of sight; drawn here it sits on the corner at any width.
 */
private fun DrawScope.drawPixelBadge() {
    val u = pixelUnit()
    val side = 11 * u
    val topLeft = Offset(size.width - side + 3 * u, -4 * u)
    translate(topLeft.x, topLeft.y) {
        drawRect(Ink, Offset(u, 0f), Size(side - 2 * u, side))
        drawRect(Ink, Offset(0f, u), Size(side, side - 2 * u))
        drawRect(GoldDark, Offset(u, u), Size(side - 2 * u, side - 2 * u))
        drawRect(AccentGold, Offset(u, u), Size(side - 2 * u, side - 3 * u))
        drawRect(GoldLight, Offset(u, u), Size(side - 2 * u, u))
        val mark = PixelIcons.Alert
        drawPixelIcon(mark, Offset(((side - mark.width * u) / 2f / u).toInt() * u, 2 * u), u, Ink, shadow = null)
    }
}

/** A button with a label: the common case of [PixelBlockButton]. */
@Composable
internal fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    material: PixelMaterial = PixelMaterial.Brick,
    enabled: Boolean = true,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    textStyle: TextStyle = LocalPixelType.current.label,
    minHeight: Dp = 44.dp,
    badge: Boolean = false
) {
    PixelBlockButton(
        onClick = onClick,
        modifier = modifier,
        material = material,
        enabled = enabled,
        selected = selected,
        focusRequester = focusRequester,
        minHeight = minHeight,
        badge = badge
    ) { color ->
        Text(
            text = text,
            color = color,
            style = if (color == Ink) textStyle else textStyle.shadowed(),
            textAlign = TextAlign.Center
        )
    }
}

/** A square button carrying one [PixelIcon]. [label] is what screen readers announce. */
@Composable
internal fun PixelIconButton(
    icon: PixelIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    material: PixelMaterial = PixelMaterial.Steel,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    size: Dp = 52.dp,
    badge: Boolean = false
) {
    PixelBlockButton(
        onClick = onClick,
        modifier = modifier.size(size),
        material = material,
        enabled = enabled,
        focusRequester = focusRequester,
        contentPadding = PaddingValues(0.dp),
        minHeight = size,
        badge = badge,
        semanticLabel = label
    ) { color ->
        PixelIconImage(icon, color, shadow = if (color == Ink) null else Ink)
    }
}

/**
 * A raised plate for grouping: dialogs, overlays, the cards of a list. [framed] rings it in steel
 * with rivets, for the surfaces that float over the board.
 */
@Composable
internal fun PixelPanel(
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .drawBehind { drawPixelPanel(framed) }
            .padding(if (framed) 8.dp else 2.dp)
            .padding(contentPadding),
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}

internal fun DrawScope.drawPixelPanel(framed: Boolean) {
    val u = pixelUnit()
    val w = size.width
    val h = size.height
    drawRect(Ink, Offset(u, 0f), Size(w - 2 * u, h))
    drawRect(Ink, Offset(0f, u), Size(w, h - 2 * u))
    val ring = if (framed) 3 * u else 0f
    if (framed) {
        drawRect(SteelFace, Offset(u, u), Size(w - 2 * u, h - 2 * u))
        drawRect(SteelLight, Offset(u, u), Size(w - 2 * u, u))
        drawRect(SteelLight, Offset(u, u), Size(u, h - 2 * u))
        drawRect(SteelDark, Offset(u, h - 2 * u), Size(w - 2 * u, u))
        drawRect(SteelDark, Offset(w - 2 * u, u), Size(u, h - 2 * u))
        listOf(
            Offset(2 * u, 2 * u), Offset(w - 3 * u, 2 * u),
            Offset(2 * u, h - 3 * u), Offset(w - 3 * u, h - 3 * u)
        ).forEach { drawRect(SteelLight, it, Size(u, u)) }
        drawRect(Ink, Offset(ring, ring), Size(w - 2 * ring, h - 2 * ring))
    }
    val inner = ring + u
    drawRect(PanelFace, Offset(inner, inner), Size(w - 2 * inner, h - 2 * inner))
    drawRect(PanelLight, Offset(inner, inner), Size(w - 2 * inner, u))
}

/**
 * A recessed well — the opposite of a block: shaded along the top and left, lit along the bottom
 * and right. Rows of a table, cells of a calendar, a card in the collection. [accent] draws a
 * one-unit ring inside it to pick one out.
 */
internal fun Modifier.pixelInset(accent: Color? = null, fill: Color = PanelDark): Modifier = drawBehind {
    val u = pixelUnit()
    val w = size.width
    val h = size.height
    drawRect(fill, Offset.Zero, size)
    drawRect(Ink, Offset.Zero, Size(w, u))
    drawRect(Ink, Offset.Zero, Size(u, h))
    drawRect(PanelLight, Offset(0f, h - u), Size(w, u))
    drawRect(PanelLight, Offset(w - u, 0f), Size(u, h))
    if (accent != null) {
        drawRect(accent, Offset(u, u), Size(w - 2 * u, u))
        drawRect(accent, Offset(u, h - 2 * u), Size(w - 2 * u, u))
        drawRect(accent, Offset(u, u), Size(u, h - 2 * u))
        drawRect(accent, Offset(w - 2 * u, u), Size(u, h - 2 * u))
    }
}

/** A screen or dialog title: gold, lettered with a hard shadow. */
@Composable
internal fun PixelTitle(text: String, modifier: Modifier = Modifier, color: Color = AccentGold, style: TextStyle = LocalPixelType.current.heading) {
    Text(text = text, color = color, style = style.shadowed(), textAlign = TextAlign.Center, modifier = modifier)
}

/**
 * A modal question on a steel plate. [buttons] go in a row under the body; the caller gives the
 * safe choice initial focus, so Enter on a destructive question cancels rather than confirms.
 *
 * [menuKeys] lets the arrows move between the buttons. A dialog built around a text field turns
 * it off, so the arrows move the caret as they should; Tab still gets to the buttons.
 */
@Composable
internal fun PixelDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    menuKeys: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PixelPanel(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 460.dp)
                .then(if (menuKeys) Modifier.pixelMenuKeys(letters = false) else Modifier),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
        ) {
            PixelTitle(title)
            Spacer(Modifier.height(14.dp))
            content()
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), content = buttons)
        }
    }
}

/**
 * Two or more exclusive choices side by side. The chosen one is brick and sits pressed in; the
 * rest are steel. A disabled option stays visible — hiding it would read as the mode not
 * existing (see the seat picker).
 */
@Composable
internal fun <T> PixelSegmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: (T) -> Boolean = { true },
    textStyle: TextStyle = LocalPixelType.current.label
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            PixelButton(
                text = label(option),
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
                material = if (option == selected) PixelMaterial.Brick else PixelMaterial.Steel,
                selected = option == selected,
                enabled = isEnabled(option),
                textStyle = textStyle
            )
        }
    }
}

/** An on/off switch as a lamp and a word, so its state reads without knowing which way is on. */
@Composable
internal fun PixelToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onLabel: String,
    offLabel: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    PixelBlockButton(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.widthIn(min = 112.dp),
        material = PixelMaterial.Steel,
        focusRequester = focusRequester,
        contentPadding = PaddingValues(horizontal = 14.dp)
    ) { color ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(12.dp)) {
                val u = pixelUnit()
                drawRect(Ink, Offset.Zero, size)
                drawRect(if (checked) PlayerGreen else SteelDark, Offset(u, u), Size(size.width - 2 * u, size.height - 2 * u))
                if (checked) drawRect(Color.White.copy(alpha = 0.7f), Offset(u, u), Size(u * 2, u * 2))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (checked) onLabel else offLabel,
                color = color,
                style = LocalPixelType.current.label.shadowed()
            )
        }
    }
}

/** A text box sunk into the panel it sits on, with a gold ring and caret while typing. */
@Composable
internal fun PixelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val type = LocalPixelType.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = type.heading.copy(color = if (enabled) Color.White else DisabledText),
        cursorBrush = SolidColor(GoldLight),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { field ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pixelInset(
                        accent = when {
                            isError -> BrickLight
                            focused -> GoldLight
                            else -> null
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) { field() }
        }
    )
}

/** Three blocks lighting in turn: the wait indicator, in place of a spinner. */
@Composable
internal fun PixelLoading(modifier: Modifier = Modifier, color: Color = AccentGold) {
    val phase by rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "loading-phase"
    )
    Canvas(modifier.size(40.dp, 12.dp)) {
        val u = pixelUnit()
        val block = 5 * u
        val gap = (size.width - 3 * block) / 2
        repeat(3) { index ->
            val lit = phase.toInt() == index
            val x = index * (block + gap)
            drawRect(Ink, Offset(x, 0f), Size(block, block))
            drawRect(if (lit) color else SteelDark, Offset(x + u, u), Size(block - 2 * u, block - 2 * u))
        }
    }
}

/** Full-width column of stacked buttons with the gaps the menus use. */
@Composable
internal fun PixelButtonColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

package com.meow.music.ui.player

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.meow.music.data.canvas.CanvasArtwork
import com.meow.music.data.lyrics.LyricLine
import com.meow.music.data.model.Song
import com.meow.music.data.model.hiRes
import com.meow.music.data.prefs.ImmersiveArt
import com.meow.music.data.prefs.ImmersiveLyrics
import com.meow.music.ui.components.ActionGroup
import com.meow.music.ui.components.ActionHeader
import com.meow.music.ui.components.ActionItem
import com.meow.music.ui.components.ActionSheet
import com.meow.music.ui.components.FloatingDisc
import com.meow.music.ui.components.runAction
import com.meow.music.ui.theme.LocalContainer
import com.meow.music.ui.theme.PrismIcons
import com.meow.music.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val lineShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 14f)

/**
 * Immersive mode (the player's ⋯ menu): the cover fills the screen and the lyrics play over its lower part.
 */
@Composable
fun ImmersivePlayer(
    song: Song,
    canvas: CanvasArtwork?,
    canvasSaved: Boolean,
    isPlaying: Boolean,
    lyricsState: LyricsState,
    covered: Boolean,
    onExit: () -> Unit,
    onLyricsTiming: () -> Unit,
    onLyricsSource: () -> Unit,
    onSongOptions: () -> Unit,
    onCollapse: () -> Unit,
) {
    val view = LocalView.current
    val c = LocalContainer.current
    val settings by c.settings.flow.collectAsState()
    val art = settings.immersiveArt
    val place = settings.immersiveLyrics
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var button by remember { mutableStateOf(false) }
    var pressing by remember { mutableStateOf(false) }
    LaunchedEffect(button, menu, pressing) { if (button && !menu && !pressing) { delay(4000); button = false } }

    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }

    var drag by remember { mutableFloatStateOf(0f) }
    var swipe by remember { mutableFloatStateOf(0f) }
    val blurred = rememberFullArtworkBlur(song.thumbnail)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { translationY = drag.coerceAtLeast(0f) }
            .pointerInput(Unit) { detectTapGestures(onTap = { button = !button }) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (drag > 220f) onCollapse(); drag = 0f },
                    onDragCancel = { drag = 0f },
                ) { change, amount -> change.consume(); drag += amount }
            }
            .pointerInput(Unit) {
                val mark = 72.dp.toPx()
                fun settle() { val from = swipe; scope.launch { animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> swipe = v } } }
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipe <= -mark) { haptics.gestureEnd(); c.player.next() }
                        else if (swipe >= mark) { haptics.gestureEnd(); c.player.previous() }
                        settle()
                    },
                    onDragCancel = { settle() },
                ) { change, d ->
                    change.consume()
                    if ((abs(swipe) < mark) != (abs(swipe + d) < mark)) haptics.threshold()
                    swipe += d
                }
            },
    ) {
        if (art != ImmersiveArt.FILL) FullArtworkBlurBackdrop(blurred, scrim = 0.8f)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fill = art == ImmersiveArt.FILL
            val side = lerp(minOf(maxWidth, maxHeight), maxOf(maxWidth, maxHeight), art.zoom)
            val fade = if (fill) 0f else 0.3f
            var canvasCover by remember(canvas) { mutableFloatStateOf(0f) }
            Box(
                (if (fill) Modifier.fillMaxSize() else Modifier.align(Alignment.TopCenter).requiredSize(side))
                    .graphicsLayer { translationX = swipe * 0.35f },
            ) {
                AsyncImage(
                    hiRes(song.thumbnail, 1200), null, contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - canvasCover; compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (fade > 0f) drawRect(
                                Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height * (1f - fade), endY = size.height),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                )
                val clip = remember(canvas, fill) {
                    canvas?.let { cv -> if (fill) cv.fallbackUrl?.let { tall -> cv.copy(url = tall, fallbackUrl = cv.url) } ?: cv else cv }
                }
                clip?.let {
                    CanvasPlayer(it, canvasSaved, isPlaying, Modifier.fillMaxSize(), bottomFade = fade, onCoverChanged = { v -> canvasCover = v })
                }
            }
        }

        val black = { a: Float -> Color.Black.copy(alpha = a) }
        Crossfade(place, label = "immersiveShade") { p ->
            val stops = when (p) {
                ImmersiveLyrics.HIGH -> arrayOf(0f to black(0.62f), 0.42f to black(0.18f), 0.62f to Color.Transparent, 0.84f to black(0.2f), 1f to black(0.55f))
                ImmersiveLyrics.MIDDLE -> arrayOf(0f to black(0.25f), 0.28f to black(0.3f), 0.5f to black(0.5f), 0.72f to black(0.3f), 1f to black(0.55f))
                ImmersiveLyrics.LOW -> arrayOf(0f to black(0.28f), 0.16f to Color.Transparent, 0.5f to Color.Transparent, 0.78f to black(0.42f), 1f to black(0.78f))
                ImmersiveLyrics.FULL -> arrayOf(0f to black(0.5f), 0.5f to black(0.46f), 1f to black(0.66f))
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(*stops)))
        }

        val insets = WindowInsets.safeDrawing.asPaddingValues()
        when (place) {
            ImmersiveLyrics.FULL -> LyricsOverAll(song, lyricsState, isPlaying, Modifier.fillMaxSize().padding(horizontal = 28.dp))
            else -> {
                val align = when (place) { ImmersiveLyrics.HIGH -> Alignment.TopCenter; ImmersiveLyrics.MIDDLE -> Alignment.Center; else -> Alignment.BottomCenter }
                Box(
                    Modifier
                        .align(align)
                        .fillMaxWidth()
                        .then(if (place == ImmersiveLyrics.MIDDLE) Modifier else Modifier.fillMaxHeight(0.5f))
                        .padding(
                            start = 28.dp, end = 28.dp,
                            top = if (place == ImmersiveLyrics.HIGH) insets.calculateTopPadding() + 72.dp else 0.dp,
                            bottom = if (place == ImmersiveLyrics.LOW) insets.calculateBottomPadding() + 132.dp else 0.dp,
                        ),
                    contentAlignment = align,
                ) {
                    LineAtATime(song, lyricsState, isPlaying, align)
                }
            }
        }

        AnimatedVisibility(
            button || menu, Modifier.align(Alignment.BottomCenter).padding(bottom = insets.calculateBottomPadding() + 28.dp),
            enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f), exit = fadeOut(tween(300)),
        ) {
            HoldToLeaveButton(
                onTap = { menu = true },
                onPressing = { pressing = it },
                onHeld = { haptics.gestureEnd(); onExit() },
            )
        }
    }

    if (menu) ActionSheet(song.thumbnail, { menu = false; button = false }) { close ->
        ActionHeader(song.thumbnail, song.title, song.artistText)
        ActionGroup(
            "Immersive mode",
            listOf(
                ActionItem(PrismIcons.FullscreenExit, "Show the player", onClick = onExit),
                ActionItem(PrismIcons.AspectRatio, "Artwork", value = art.label, keepOpen = true) {
                    c.settings.setImmersiveArt(ImmersiveArt.entries[(art.ordinal + 1) % ImmersiveArt.entries.size])
                },
                ActionItem(PrismIcons.More, "Song options", onClick = onSongOptions),
            ),
        ) { runAction(it, close) }
        ActionGroup(
            "Lyrics",
            listOf(
                ActionItem(PrismIcons.Lyrics, "Lyrics position", value = place.label, keepOpen = true) {
                    c.settings.setImmersiveLyrics(ImmersiveLyrics.entries[(place.ordinal + 1) % ImmersiveLyrics.entries.size])
                },
                ActionItem(PrismIcons.Timer, "Lyrics timing", onClick = onLyricsTiming),
                ActionItem(PrismIcons.Lyrics, "Lyrics source", onClick = onLyricsSource),
            ),
        ) { runAction(it, close) }
    }
}

@Composable
private fun LineAtATime(song: Song, state: LyricsState, active: Boolean, align: Alignment) {
    val c = com.meow.music.ui.theme.LocalContainer.current
    val settings by c.settings.flow.collectAsState()
    val lines = state.lines
    val synced = lines.any { it.timeMs >= 0 }
    val lineStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center, textDirection = TextDirection.Content, shadow = lineShadow,
    )

    val translateTo = settings.lyricsTranslateTo.ifBlank { defaultTranslateTarget() }
    val geminiKey = settings.geminiApiKey
    val groqKey = settings.groqApiKey
    val model = settings.geminiModel
    var translation by remember(lines, settings.lyricsTranslate, translateTo, geminiKey, model, groqKey) { mutableStateOf<com.meow.music.data.lyrics.LyricsTranslator.Translation?>(null) }
    LaunchedEffect(lines, settings.lyricsTranslate, translateTo, geminiKey, model, groqKey) {
        if (!settings.lyricsTranslate || lines.isEmpty()) return@LaunchedEffect
        val texts = lines.map { l -> if (l.isGap || (l.text.startsWith("[") && l.text.endsWith("]"))) "" else l.text }
        translation = runCatching { c.translator.translate(texts, to = translateTo, geminiApiKey = geminiKey, geminiModel = model, groqApiKey = groqKey) }.getOrNull()
    }
    val translations = translation?.translations

    if (lines.isEmpty() || !synced) {
        TitleCard(song)
        return
    }
    val clock = rememberLyricClock(active)
    val index by remember(lines) { derivedStateOf { lines.indexAtOrBefore(clock.value) } }
    val firstSung = remember(lines) { lines.indexOfFirst { !it.isGap } }
    AnimatedContent(
        index,
        transitionSpec = {
            (fadeIn(tween(420, easing = FastOutSlowInEasing)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 3 }) togetherWith
                (fadeOut(tween(260)) + slideOutVertically(tween(420, easing = FastOutSlowInEasing)) { -it / 3 }) using SizeTransform(clip = false)
        },
        contentAlignment = align,
        label = "immersiveLine",
    ) { i ->
        val line = lines.getOrNull(i)?.takeIf { !it.isGap }
        val transText = if (i >= 0 && line != null) translations?.getOrNull(i)?.takeUnless { it.equals(line.text.trim(), ignoreCase = true) } else null
        val next = remember(lines, i) { lines.drop(i + 1).firstOrNull { !it.isGap } }
        val arrange = when (align) { Alignment.TopCenter -> Arrangement.Top; Alignment.Center -> Arrangement.Center; else -> Arrangement.Bottom }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = arrange) {
            when {
                line == null && i < firstSung -> TitleCard(song)
                line == null -> Text("♪", style = lineStyle, color = Color.White)
                line.isWordSynced -> SweptLine(line, clock, lineStyle, Modifier.fillMaxWidth(), maxLines = 4, dimAlpha = 0.45f)
                else -> Text(line.text, style = lineStyle, color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            }
            if (transText != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    transText,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = lineStyle.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            next?.let { n ->
                Spacer(Modifier.height(14.dp))
                Text(
                    n.text, color = Color.White.copy(alpha = 0.55f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, textDirection = TextDirection.Content, shadow = lineShadow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TitleCard(song: Song) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            song.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, shadow = lineShadow),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            song.artistText, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, shadow = lineShadow),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun List<LyricLine>.indexAtOrBefore(ms: Long): Int {
    var idx = -1
    for (i in indices) if (this[i].timeMs in 0..ms) idx = i else if (this[i].timeMs > ms) break
    return idx
}

private const val HOLD_TO_LEAVE_MS = 2500

@Composable
private fun HoldToLeaveButton(onTap: () -> Unit, onPressing: (Boolean) -> Unit, onHeld: () -> Unit) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var held by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(held, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Text(
                "Keep holding to leave immersive mode", color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(shadow = lineShadow),
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Box(
            Modifier
                .size(54.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        held = true
                        onPressing(true)
                        val fill = scope.launch {
                            progress.animateTo(1f, tween((HOLD_TO_LEAVE_MS * (1f - progress.value)).toInt(), easing = LinearEasing))
                            onHeld()
                        }
                        val up = waitForUpOrCancellation()
                        up?.consume()
                        held = false
                        onPressing(false)
                        if (progress.value < 1f) {
                            val tap = up != null && progress.value < 0.15f
                            fill.cancel()
                            scope.launch { progress.animateTo(0f, tween(250)) }
                            if (tap) onTap()
                        }
                    }
                }
                .drawBehind {
                    val stroke = 3.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    val at = Offset(inset, inset)
                    if (progress.value > 0f || held) drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, at, arc, style = Stroke(stroke))
                    drawArc(Color.White, -90f, 360f * progress.value, false, at, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                },
            contentAlignment = Alignment.Center,
        ) {
            FloatingDisc { ink -> Icon(PrismIcons.SettingsFilled, "Immersive mode settings (hold to leave)", Modifier.size(22.dp), tint = ink) }
        }
    }
}

@Composable
private fun LyricsOverAll(song: Song, state: LyricsState, active: Boolean, modifier: Modifier) {
    val lines = state.lines
    if (lines.isEmpty() || lines.none { it.timeMs >= 0 }) {
        Box(modifier, contentAlignment = Alignment.Center) { TitleCard(song) }
        return
    }
    val clock = rememberLyricClock(active)
    val index by remember(lines) { derivedStateOf { lines.indexAtOrBefore(clock.value) } }
    val list = rememberLazyListState()
    val style = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center, textDirection = TextDirection.Content, shadow = lineShadow,
    )
    LaunchedEffect(lines, index) {
        val target = index.coerceAtLeast(0)
        val seen = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
        if (seen != null) list.animateScrollBy(seen.offset.toFloat(), tween(700, easing = FastOutSlowInEasing)) else list.scrollToItem(target)
    }
    BoxWithConstraints(modifier) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.16f to Color.Black, 0.78f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
            state = list,
            contentPadding = PaddingValues(top = maxHeight * 0.36f, bottom = maxHeight * 0.6f),
            userScrollEnabled = false,
        ) {
            itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                val current = i == index
                val away = abs(i - index).coerceAtMost(4)
                val alpha by animateFloatAsState(if (current) 1f else 0.5f - 0.07f * away, tween(500), label = "lineAlpha")
                val scale by animateFloatAsState(if (current) 1f else 0.86f, tween(600, easing = FastOutSlowInEasing), label = "lineScale")
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp).graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        line.isGap -> Text("♪", style = style, color = Color.White)
                        current && line.isWordSynced -> SweptLine(line, clock, style, Modifier.fillMaxWidth(), dimAlpha = 0.45f)
                        else -> Text(line.text, style = style, color = Color.White, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

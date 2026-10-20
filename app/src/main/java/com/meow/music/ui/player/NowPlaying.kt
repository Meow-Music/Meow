package com.meow.music.ui.player

import com.meow.music.ui.theme.MeowIcons
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset as offsetConstraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.meow.music.R
import com.meow.music.data.canvas.CanvasArtwork
import com.meow.music.data.model.Song
import com.meow.music.data.model.hiRes
import com.meow.music.ui.LocalNavigator
import com.meow.music.ui.Routes
import com.meow.music.ui.theme.LocalContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val THUMB_SIZE = 54.dp
private val HEADER_HEIGHT = 60.dp
private val ART_TITLE_GAP = 20.dp
private val DISMISS_STRIP = 32.dp
private val ART_BOX_TOP_PAD = 8.dp
/** How far the title may drop below the cover toward the scrubber, when the screen has room to spare. */
private val TITLE_DROP_MAX = 24.dp
private const val HERO_FADE = 0.42f

@Composable
fun rememberPosition(fast: Boolean, hold: Boolean = false): Long {
    val pc = LocalContainer.current.player
    var pos by remember { mutableLongStateOf(pc.position) }
    // [hold]: hidden under a sheet, the player isn't recomposed four times a second for a clock nobody sees.
    LaunchedEffect(fast, hold) {
        pos = pc.position
        while (isActive && !hold) {
            pos = pc.position
            delay(if (fast) 60 else 250)
        }
    }
    return pos
}

/** A square of [side]; it may be wider than the slot (a Large / Full width cover reaches into the side margins). */
private fun Modifier.measuredSquare(side: () -> Dp): Modifier = layout { measurable, constraints ->
    val px = side().roundToPx().coerceAtMost(constraints.maxHeight)
    val p = measurable.measure(Constraints.fixed(px, px))
    layout(minOf(p.width, constraints.maxWidth), p.height) { p.placeRelative(0, 0) }
}

/**
 * Now Playing, modelled on BitChord v1.7 (itself after Apple Music): artwork
 * that shrinks while paused, a hairline scrubber, oversized transport glyphs, a
 * volume capsule, and lyrics / Song·Video / queue along the bottom. Lyrics and
 * the queue live inside the player; the sleeve collapses into a header for them.
 */
@Composable
fun NowPlayingScreen(onCollapse: () -> Unit) {
    val c = LocalContainer.current
    val pc = c.player
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val view = LocalView.current
    val settings by c.settings.flow.collectAsState()
    val song = pc.currentSong.collectAsState().value ?: return
    val isPlaying by pc.isPlaying.collectAsState()
    val buffering by pc.buffering.collectAsState()
    val videoMode by pc.videoMode.collectAsState()
    val videoAvailable by pc.videoAvailable.collectAsState()
    val hasNext by pc.hasNext.collectAsState()
    val hasPrevious by pc.hasPrevious.collectAsState()
    val liked by c.library.likedIds.collectAsState()

    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var lyricsControlsOpen by remember { mutableStateOf(true) }
    var queueControlsOpen by remember { mutableStateOf(true) }
    var showProviders by remember { mutableStateOf(false) }
    var statsOpen by rememberSaveable { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showTiming by remember { mutableStateOf(false) }
    var fullscreenVideo by rememberSaveable { mutableStateOf(false) }
    var videoFill by rememberSaveable { mutableStateOf(true) }

    // A sheet over the player (⋯, lyrics source, sleep timer): what moves underneath it every frame (the animated
    // cover, the word sweep, the title marquee) holds still, since both windows draw on the same thread and dragging
    // the sheet stuttered. The timing sheet isn't counted: the lyrics are what's being lined up there.
    val covered = showActions || showProviders || showSleep
    val lyricsState = rememberLyricsState(song)
    val position = rememberPosition(fast = false, hold = covered)
    val volume = rememberPlayerVolume()
    val scope = rememberCoroutineScope()

    // The player always owns light status-bar icons.
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = ctl?.isAppearanceLightStatusBars
        ctl?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) ctl.isAppearanceLightStatusBars = previous }
    }
    DisposableEffect(lyricsOpen) {
        view.keepScreenOn = lyricsOpen
        onDispose { view.keepScreenOn = false }
    }

    val openLyrics = { lyricsControlsOpen = true; lyricsOpen = true; queueOpen = false; statsOpen = false }
    val closeLyrics = { lyricsControlsOpen = false; lyricsOpen = false }
    val toggleQueue = {
        val opening = !queueOpen
        queueOpen = opening
        if (opening) { closeLyrics(); statsOpen = false; queueControlsOpen = true }
    }
    val toggleStats = {
        val opening = !statsOpen
        statsOpen = opening
        if (opening) { closeLyrics(); queueOpen = false }
    }
    LaunchedEffect(lyricsOpen, lyricsControlsOpen, volume.dragging) {
        if (lyricsOpen && lyricsControlsOpen && !volume.dragging) { delay(5000); lyricsControlsOpen = false }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // How far the player has been pulled down (px). Closing slides it the rest of the way, from wherever it is, the same
    // way for Back and for a swipe (#26: a swipe used to hand over to a different exit animation part-way down).
    val dismiss = remember { Animatable(0f) }
    val screenPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var closing by remember { mutableStateOf(false) }
    fun slideClosed() {
        if (closing) return
        closing = true
        scope.launch {
            dismiss.animateTo(screenPx, tween(if (settings.reduceMotion) 240 else 340, easing = FastOutSlowInEasing))
            onCollapse()
        }
    }
    BackHandler {
        when {
            fullscreenVideo -> fullscreenVideo = false
            lyricsOpen -> closeLyrics()
            queueOpen -> queueOpen = false
            statsOpen -> statsOpen = false
            else -> slideClosed()
        }
    }
    // Picture-in-picture: a music video playing here floats over other apps when the listener
    // leaves Meow (or taps the PiP button); the window then shows just the video.
    val inPip = LocalPip.current
    val activity = view.context as? com.meow.music.MainActivity
    DisposableEffect(videoMode, isPlaying, activity) {
        val size = pc.player?.videoSize
        activity?.setPipEligible(
            videoMode && isPlaying,
            ((size?.width ?: 0) * (size?.pixelWidthHeightRatio ?: 1f)).toInt(), size?.height ?: 0,
        )
        onDispose { activity?.setPipEligible(false) }
    }
    if (videoMode && (fullscreenVideo || landscape || inPip)) {
        FullscreenVideo(song, forceLandscape = fullscreenVideo && !inPip, pip = inPip, onExit = { fullscreenVideo = false })
        return
    }

    // ---------------------------------------------------------------- Motion
    val collapse = animateFloatAsState(if (lyricsOpen || queueOpen || statsOpen) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "collapse")
    val p: () -> Float = { collapse.value }
    val collapsePastHalf = collapse.value >= 0.5f
    val panelsSettled = collapse.value >= 1f
    val panelFade by animateFloatAsState(if (panelsSettled) 1f else 0f, tween(200, easing = FastOutSlowInEasing), label = "panelFade")
    val artScale by animateFloatAsState(
        if (isPlaying || !settings.artShrinkOnPause || settings.reduceMotion) 1f else 0.86f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "artScale",
    )
    val swipe = remember { Animatable(0f) }
    val haptics = com.meow.music.ui.theme.rememberHaptics()
    var seekFlash by remember { mutableStateOf<SeekFlash?>(null) }
    LaunchedEffect(seekFlash?.at) { if (seekFlash != null) { delay(750); seekFlash = null } }

    // ---------------------------------------------------------------- Canvas
    val metered = remember { context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true }
    val savedCanvases by c.canvas.saved.collectAsState()
    val canvasSaved = savedCanvases.has(song.id, com.meow.music.data.canvas.CanvasRepository.albumKey(song))
    val songCanvas by produceState<CanvasArtwork?>(c.canvas.cached(song), song.id, song.album?.title, settings.canvasEnabled) {
        // Saved clips play from the phone, so mobile data doesn't matter for them.
        if (!settings.canvasEnabled || (metered && (!settings.canvasOnCellular || settings.dataSaver) && !c.canvas.isSaved(song))) { value = null; return@produceState }
        val known = c.canvas.cached(song)
        value = known
        if (known == null) delay(500)
        value = c.canvas.canvasFor(song)
    }
    // Animated artwork can be switched off per song from the song options.
    val canvasHidden by c.canvas.hidden.collectAsState()
    val canvas = songCanvas.takeIf { !videoMode && song.id !in canvasHidden }
    var canvasCover by remember(canvas) { mutableFloatStateOf(0f) }

    // ---------------------------------------------------------------- Artwork
    val artUrl = hiRes(song.thumbnail, 1200)
    // Decoded at full size whatever size the cover is first laid out at (#86): opened mid-animation or with lyrics
    // open it starts out small, and the picture decoded for that size was then stretched over the whole cover.
    val artRequest = remember(artUrl) { coil3.request.ImageRequest.Builder(context).data(artUrl).size(1200).build() }
    var artLoaded by remember(artUrl) { mutableStateOf(false) }
    val heroMode = settings.fullBleedArtwork && !videoMode
    var heroHeight by remember { mutableStateOf(0.dp) }
    val heroT = animateFloatAsState(if (heroMode && (artLoaded || canvasCover > 0f)) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "hero")
    val heroVisible: () -> Float = { heroT.value * (1f - p()) }
    val mesh = rememberArtworkMesh(song.thumbnail)
    val blurImage = rememberFullArtworkBlur(song.thumbnail)
    val fullBlur by animateFloatAsState(if (lyricsOpen || queueOpen || statsOpen) 1f else 0f, tween(360, easing = FastOutSlowInEasing), label = "fullBlur")
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Collapsed into the header thumbnail, the sleeve keeps gentle corners whatever the setting.
    // A Full width cover meets the screen's edges, so it has square corners until it shrinks into the header.
    val artCorners = if (settings.coverSize == com.meow.music.data.prefs.CoverSize.FULL) 0.dp else settings.playerArtCorners.dp
    val artShape = RoundedCornerShape(lerp(artCorners, 8.dp, collapse.value))
    val roomyCover = settings.coverSize != com.meow.music.data.prefs.CoverSize.STANDARD

    // Only ever the song's album (tapping it opens the album); nothing for songs without one.
    val originAlbum = song.album?.takeIf { it.id != null && it.title.isNotBlank() }

    // The sheets that open over the player (in immersive mode too).
    val playerSheets = @Composable {
        if (showProviders) LyricsProviderSheet(lyricsState) { showProviders = false }
        if (showActions) PlayerOptionsSheet(
            song,
            hasCanvas = songCanvas != null,
            statsShown = statsOpen,
            hasNext = hasNext,
            onLyricsSource = { showProviders = true },
            onLyricsTiming = { showTiming = true },
            onRedownloadLyrics = { lyricsState.redownload() },
            onToggleStats = { toggleStats() },
            onSleepTimer = { showSleep = true },
            onCollapse = onCollapse,
        ) { showActions = false }
        if (showSleep) SleepTimerDialog { showSleep = false }
        if (showTiming) LyricsTimingSheet { showTiming = false }
    }

    // ------------------------------------------------ Immersive mode (⋯ menu): the cover fills the screen, lyrics over it
    if (settings.immersivePlayer && !videoMode) {
        ImmersivePlayer(
            song, canvas, canvasSaved, isPlaying, lyricsState, covered,
            onExit = { c.settings.setImmersivePlayer(false) },
            onLyricsTiming = { showTiming = true },
            onLyricsSource = { showProviders = true },
            onSongOptions = { showActions = true },
            onCollapse = onCollapse,
        )
        playerSheets()
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dismiss.value.roundToInt()) }
            .graphicsLayer {
                val f = (dismiss.value / 1800f).coerceIn(0f, 1f)
                scaleX = 1f - f * 0.06f; scaleY = 1f - f * 0.06f
                shape = RoundedCornerShape((f * 140).dp); clip = f > 0f
            }
            .background(PlayerFallbackBackdrop),
    ) {
        // ------------------------------------------------ Backdrop
        when (settings.playerBackground) {
            com.meow.music.data.prefs.PlayerBackground.MESH -> {
                if (fullBlur < 1f) ArtworkMeshBackdrop(mesh, Modifier.graphicsLayer { alpha = 1f - fullBlur }, seam = if (heroMode) heroHeight else 0.dp)
                if (fullBlur > 0f) FullArtworkBlurBackdrop(blurImage, Modifier.graphicsLayer { alpha = fullBlur })
            }
            // "Show background" lifts the darkening: about as before at its default, down to a light veil at full.
            com.meow.music.data.prefs.PlayerBackground.BLUR -> FullArtworkBlurBackdrop(blurImage, scrim = 1.3f - 0.95f * settings.playerBackdropShow)
            com.meow.music.data.prefs.PlayerBackground.APPEARANCE -> {
                AppearanceBackdrop(settings.playerBackdropShow)
                if (fullBlur > 0f) FullArtworkBlurBackdrop(blurImage, Modifier.graphicsLayer { alpha = fullBlur })
            }
            com.meow.music.data.prefs.PlayerBackground.THEME -> ThemeBackdrop()
            com.meow.music.data.prefs.PlayerBackground.BLACK -> Box(Modifier.fillMaxSize().background(Color.Black))
        }

        // ------------------------------------------------ Full-bleed banner (+ canvas)
        if (heroMode && heroHeight > 0.dp && !(collapsePastHalf && heroVisible() < 0.001f)) {
            AsyncImage(
                artRequest, null, contentScale = ContentScale.Crop,
                onState = { if (it is AsyncImagePainter.State.Success) artLoaded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .graphicsLayer {
                        alpha = heroVisible() * (1f - canvasCover)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height * (1f - HERO_FADE), endY = size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
            canvas?.takeIf { !collapsePastHalf }?.let { clip ->
                CanvasPlayer(
                    clip, canvasSaved, isPlaying,
                    Modifier.fillMaxWidth().height(heroHeight),
                    bottomFade = HERO_FADE,
                    presentationAlpha = { heroT.value * (1f - 2f * p()).coerceIn(0f, 1f) },
                    onCoverChanged = { canvasCover = it },
                )
            }
        }
        // Status-bar scrim
        Box(Modifier.fillMaxWidth().height(statusTop + DISMISS_STRIP).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent))))

        val dismissGesture = Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = { if (dismiss.value > 220f) slideClosed() else scope.launch { dismiss.animateTo(0f, spring()) } },
                onDragCancel = { if (!closing) scope.launch { dismiss.animateTo(0f) } },
            ) { change, amount ->
                change.consume()
                if (!closing) scope.launch { dismiss.snapTo((dismiss.value + amount).coerceAtLeast(0f)) }
            }
        }
        // Drag on the sleeve: down closes the player, up pulls the queue in.
        val sleeveGesture = Modifier.pointerInput(lyricsOpen, queueOpen, statsOpen) {
            if (lyricsOpen || queueOpen || statsOpen) return@pointerInput
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    if (dismiss.value > 220f) slideClosed()
                    else scope.launch {
                        if (total < -90f) { dismiss.snapTo(0f); toggleQueue() } else dismiss.animateTo(0f, spring())
                    }
                },
                // An interrupted drag (a system gesture, a sheet opening) used to leave the player part-way down.
                onDragCancel = { if (!closing) scope.launch { dismiss.animateTo(0f, spring()) } },
            ) { change, amount ->
                change.consume()
                total += amount
                // Back above where it started: follow the finger back to the top rather than stopping short of it.
                if (!closing) scope.launch { dismiss.snapTo(total.coerceAtLeast(0f)) }
            }
        }
        // Double-tap the cover's left or right half to seek back or ahead (#79), as on YouTube Music.
        val seekStep = settings.doubleTapSeekSeconds
        val doubleTapSeek = Modifier.pointerInput(lyricsOpen, queueOpen, statsOpen, seekStep) {
            if (lyricsOpen || queueOpen || statsOpen || seekStep <= 0) return@pointerInput
            detectTapGestures(onDoubleTap = { at ->
                val ahead = at.x > size.width / 2
                haptics.gestureEnd()
                pc.seekBy(if (ahead) seekStep * 1000L else -seekStep * 1000L)
                // Taps in a row add up on the label, as YouTube's does.
                seekFlash = SeekFlash(ahead, if (seekFlash?.ahead == ahead) seekFlash!!.steps + 1 else 1, System.nanoTime())
            })
        }
        val skipGesture = Modifier.pointerInput(lyricsOpen, queueOpen, statsOpen) {
            if (lyricsOpen || queueOpen || statsOpen) return@pointerInput
            var total = 0f
            val threshold = 72.dp.toPx()
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                onDragEnd = {
                    if (total <= -threshold) { haptics.gestureEnd(); pc.next() }
                    else if (total >= threshold) { haptics.gestureEnd(); pc.previous() }
                    scope.launch { swipe.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                },
            ) { _, d ->
                if ((kotlin.math.abs(total) < threshold) != (kotlin.math.abs(total + d) < threshold)) haptics.threshold()
                total += d
                scope.launch { swipe.snapTo(total * 0.35f) }
            }
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().then(skipGesture), horizontalAlignment = Alignment.CenterHorizontally) {
            // ------------------------------------------------ Handle + caption
            Box(Modifier.fillMaxWidth().height(DISMISS_STRIP).then(dismissGesture)) {
                Box(
                    Modifier.align(Alignment.TopCenter)
                        .offset { IntOffset(0, lerp(6.dp, (DISMISS_STRIP - 5.dp) / 2, p()).roundToPx()) }
                        .size(38.dp, 5.dp)
                        .shadow(2.dp, RoundedCornerShape(3.dp), clip = false)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.70f)),
                )
                if (collapse.value < 0.999f && settings.showPlayingFrom && originAlbum != null) PlaybackOriginCaption(
                    "Playing from ${originAlbum.title}",
                    Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = 1f - p() },
                ) {
                    onCollapse(); nav.go(Routes.album(originAlbum.id!!))
                }
            }

            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PLAYER_GUTTER), horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = ART_BOX_TOP_PAD, bottom = if (roomyCover) 12.dp else 18.dp)) {
                    // Settings → Player → Cover size: Large and Full width reach into the side margins (still square).
                    val fullArt = minOf(maxWidth + settings.coverSize.extra.dp * 2, maxHeight - ART_TITLE_GAP - HEADER_HEIGHT).coerceAtLeast(THUMB_SIZE)
                    // Room the cover can't use (a tall screen) is shared above and below it, as before 1.6.4 (all of it
                    // above pushed the whole player down). The title drops into part of the room below, so no blank
                    // band opens up between the artist and the scrubber (issue #9).
                    val slack = (maxHeight - fullArt - ART_TITLE_GAP - HEADER_HEIGHT).coerceAtLeast(0.dp)
                    val groupTop = slack / 2
                    val titleDrop = minOf(slack / 2, TITLE_DROP_MAX)
                    val bannerBottom = statusTop + DISMISS_STRIP + ART_BOX_TOP_PAD + groupTop + fullArt + ART_TITLE_GAP / 2
                    if (!lyricsOpen && !queueOpen && !statsOpen && bannerBottom != heroHeight) SideEffect { heroHeight = bannerBottom }
                    val sleeveMaxWidth = maxWidth
                    fun artSize(): Dp = lerp(fullArt, THUMB_SIZE, p())
                    fun artTop(): Dp = lerp(groupTop, 0.dp, p())
                    fun artStart(): Dp = lerp((maxWidth - fullArt) / 2, 0.dp, p())
                    fun titleTop(): Dp = lerp(groupTop + fullArt + ART_TITLE_GAP + titleDrop, 0.dp, p())
                    fun titleStart(): Dp = lerp(0.dp, THUMB_SIZE + 12.dp, p())

                    // ---- Music video: spans the full screen width, filling the artwork slot.
                    if (videoMode && !collapsePastHalf) {
                        Box(
                            Modifier
                                // requiredWidth wider than the column overflows equally on
                                // both sides, so this already reaches both screen edges.
                                .offset { IntOffset(0, artTop().roundToPx()) }
                                .requiredWidth(sleeveMaxWidth + PLAYER_GUTTER * 2)
                                .height(fullArt + ART_TITLE_GAP / 2)
                                .graphicsLayer { alpha = 1f - 2f * p() }
                                .then(sleeveGesture),
                        ) {
                            MusicVideoSurface(Modifier.fillMaxSize(), fill = videoFill)
                            Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                VideoChip(MeowIcons.AspectRatio, if (videoFill) "Fit video" else "Fill") { videoFill = !videoFill }
                                if (activity != null) VideoChip(MeowIcons.PictureInPicture, "Picture-in-picture") {
                                    if (!activity.enterPip()) android.widget.Toast.makeText(
                                        context, "Picture-in-picture is off for Meow in Android's settings", android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                VideoChip(MeowIcons.Fullscreen, "Full screen") { fullscreenVideo = true }
                            }
                        }
                    }

                    // ---- The sleeve (collapses to a header thumbnail)
                    Box(
                        Modifier
                            .offset { IntOffset(artStart().roundToPx(), artTop().roundToPx()) }
                            .measuredSquare(::artSize)
                            .then(if (!videoMode) sleeveGesture.then(doubleTapSeek) else Modifier)
                            .graphicsLayer {
                                val idle = artScale + (1f - artScale) * p()
                                scaleX = idle; scaleY = idle
                                translationX = swipe.value * (1f - p())
                            }
                            .then(
                                if (lyricsOpen || queueOpen || statsOpen) Modifier.clickable { queueOpen = false; statsOpen = false; closeLyrics() } else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = when {
                                        videoMode -> (2f * p() - 1f).coerceIn(0f, 1f)
                                        heroMode && artLoaded -> 1f - heroVisible()
                                        else -> 1f
                                    }
                                }
                                .shadow(if (artLoaded) 10.dp else 0.dp, artShape)
                                .clip(artShape)
                                .background(Color.Black.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!artLoaded) Icon(MeowIcons.Song, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(40.dp))
                            AsyncImage(
                                artRequest, null, contentScale = ContentScale.Crop,
                                onState = { if (it is AsyncImagePainter.State.Success) artLoaded = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (!heroMode && !videoMode) canvas?.takeIf { !collapsePastHalf }?.let { clip ->
                                CanvasPlayer(clip, canvasSaved, isPlaying, Modifier.fillMaxSize(), onCoverChanged = { canvasCover = it })
                            }
                        }
                        seekFlash?.takeIf { !collapsePastHalf }?.let { f ->
                            SeekLabel(f, seekStep, Modifier.align(if (f.ahead) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 18.dp))
                        }
                    }

                    // ---- Credits: title, artist, like, menu
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, (titleTop() - lerp(0.dp, (HEADER_HEIGHT - THUMB_SIZE) / 2, p())).roundToPx()) }
                            .layout { measurable, constraints ->
                                val start = titleStart().roundToPx()
                                val placeable = measurable.measure(constraints.offsetConstraints(horizontal = -start))
                                layout(constraints.constrainWidth(placeable.width + start), constraints.constrainHeight(placeable.height)) {
                                    placeable.placeRelative(start, 0)
                                }
                            }
                            .height(HEADER_HEIGHT)
                            .then(sleeveGesture),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(1f).graphicsLayer {
                                val s = 1f - 0.2f * p()
                                scaleX = s; scaleY = s
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            },
                        ) {
                            val scrolls = collapse.value < 0.01f && !covered
                            PlayerMarquee(
                                // The title leads, the artist under it a size smaller (#41): they were both 20 sp.
                                song.title, MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp), Color.White, scrolls,
                                Modifier.clickable(enabled = song.album?.id != null) { song.album?.id?.let { onCollapse(); nav.go(Routes.album(it)) } },
                            )
                            // With more than one artist, the name asks which one to open (#42).
                            // Artists YouTube names without a link open by name (#78).
                            val linked = com.meow.music.ui.components.openableArtists(song)
                            var pickArtist by remember(song.id) { mutableStateOf(false) }
                            Box {
                                PlayerMarquee(
                                    song.artistText, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W500, fontSize = 17.sp, lineHeight = 22.sp), Color.White.copy(alpha = 0.6f), scrolls,
                                    Modifier.clickable(enabled = linked.isNotEmpty()) {
                                        if (linked.size > 1) pickArtist = true else linked.firstOrNull()?.let { onCollapse(); nav.go(Routes.artist(it)) }
                                    },
                                    delayMs = 5500,
                                )
                                androidx.compose.material3.DropdownMenu(pickArtist, { pickArtist = false }) {
                                    linked.forEach { a ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            { Text(a.name) }, { pickArtist = false; onCollapse(); nav.go(Routes.artist(a)) },
                                            leadingIcon = { androidx.compose.material3.Icon(MeowIcons.Person, null) },
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        val isLiked = song.id in liked
                        CircleGlyph(if (isLiked) MeowIcons.HeartFilled else MeowIcons.Heart, if (isLiked) "Remove from liked" else "Like", isLiked) {
                            c.library.toggleLike(song)
                        }
                        Spacer(Modifier.width(8.dp))
                        CircleGlyph(MeowIcons.More, "Song options") { showActions = true }
                    }

                    // ---- Lyrics and queue panels, under the collapsed header
                    if (lyricsOpen && panelsSettled) {
                        LyricsPanel(
                            lyricsState, isPlaying && !covered, lyricsControlsOpen,
                            onRevealControls = { lyricsControlsOpen = true },
                            onHideControls = { lyricsControlsOpen = false },
                            modifier = Modifier.fillMaxSize().padding(top = HEADER_HEIGHT).graphicsLayer { alpha = panelFade },
                        )
                    }
                    if (statsOpen && panelsSettled) {
                        NerdStatsPanel(
                            Modifier.fillMaxSize().padding(top = HEADER_HEIGHT + 8.dp).graphicsLayer {
                                alpha = panelFade
                                translationY = (1f - panelFade) * 26.dp.toPx()
                            },
                        )
                    }
                    if (queueOpen && panelsSettled) {
                        InlineQueue(
                            onRevealPlayer = { queueControlsOpen = true },
                            onHidePlayer = { queueControlsOpen = false },
                            modifier = Modifier.fillMaxSize().padding(top = HEADER_HEIGHT + 8.dp).graphicsLayer {
                                alpha = panelFade
                                translationY = (1f - panelFade) * 26.dp.toPx()
                            },
                        )
                    }
                }

                // ------------------------------------------------ The deck
                AnimatedVisibility(
                    (!lyricsOpen || lyricsControlsOpen) && (!queueOpen || queueControlsOpen),
                    enter = expandVertically(tween(420, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(420)),
                    exit = shrinkVertically(tween(420, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(300)),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (lyricsOpen) LyricsStatusWithChange(lyricsState) { showProviders = true }
                        else if (settings.lyricsOnPlayer) CurrentLyricStrip(lyricsState, isPlaying && !covered, openLyrics)
                        // With "Current lyric" off no blank line is kept for it: the cover and title get the room.
                        PlayerScrubber(position, pc.duration) {
                            // Settings → Player → "Stream quality" hides it (the full stats stay in the ⋯ menu).
                            if (settings.showQuality) QualityLabel(Modifier.align(Alignment.Center), selected = statsOpen) { toggleStats() }
                        }
                        Spacer(Modifier.height(8.dp))
                        TransportRow(isPlaying, buffering, previousEnabled = hasPrevious || position > 3000, nextEnabled = hasNext)
                        Spacer(Modifier.height(12.dp))
                        // A hidden volume bar keeps its room, so the transport sits where it always has (giving it to
                        // a Large / Full width cover in 1.6.4 dropped the controls toward the bottom row).
                        if (!settings.hideVolumeBar) VolumeRow(volume) else Spacer(Modifier.height(VOLUME_ROW_HEIGHT))
                        Spacer(Modifier.height(6.dp))
                        PlayerActionRow(
                            lyricsOpen, queueOpen, videoMode, videoAvailable,
                            onToggleLyrics = { if (lyricsOpen) closeLyrics() else openLyrics() },
                            onToggleQueue = toggleQueue,
                            onVideoMode = { pc.setVideoMode(it) },
                        )
                        val deckGap = if (roomyCover) 12.dp else 18.dp
                        Spacer(Modifier.height(deckGap))
                        if (settings.showOutputDevice) OutputCaption() else if (!roomyCover) Spacer(Modifier.height(14.dp))
                        Spacer(Modifier.height(deckGap))
                    }
                }
            }
        }
    }

    playerSheets()
}

/** The "Theme" player background: a deep wash of the accent colour, lighter at the top. */
@Composable
private fun ThemeBackdrop() {
    val scheme = MaterialTheme.colorScheme
    fun Color.deep(f: Float) = Color(red * f, green * f, blue * f, 1f)
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(scheme.primary.deep(0.55f), scheme.tertiary.deep(0.28f), Color.Black.copy(alpha = 0.92f).compositeOver(scheme.primary.deep(0.1f))))
        ),
    )
}

/**
 * The background picked for Home in Appearance → Backgrounds. The player is always dark (white text and
 * controls), so the background is drawn in its dark version whatever the app's theme, under a veil whose
 * strength [show] sets, heavier toward the controls at the bottom.
 */
@Composable
private fun AppearanceBackdrop(show: Float) {
    val s = com.meow.music.ui.theme.LocalAppSettings.current
    val (style, photo) = com.meow.music.ui.theme.Backdrop.parse(s.backdrops[com.meow.music.ui.theme.BackdropScreen.HOME.name])
    val primary = MaterialTheme.colorScheme.primary
    val darkScheme = remember(primary) { com.meow.music.ui.theme.schemeFromSeed(primary, dark = true, pureBlack = false) }
    val veil = 1f - show
    MaterialTheme(colorScheme = darkScheme) {
        androidx.compose.runtime.CompositionLocalProvider(com.meow.music.ui.theme.LocalIsDark provides true) {
            Box(Modifier.fillMaxSize().background(darkScheme.background)) {
                com.meow.music.ui.theme.BackdropLayer(
                    style, photo, dim = 0.7f * veil, motion = s.backdropMotion && !s.reduceMotion, modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.10f * veil), 0.55f to Color.Black.copy(alpha = 0.18f * veil + 0.04f),
                            1f to Color.Black.copy(alpha = 0.45f * veil + 0.18f)),
                    ),
                )
            }
        }
    }
}

@Composable
private fun VideoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

/** The main player's video, drawn on a TextureView so it composes with the layout. */
@OptIn(UnstableApi::class)
@Composable
fun MusicVideoSurface(modifier: Modifier, fill: Boolean) {
    val pc = LocalContainer.current.player
    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.texture_player, null) as PlayerView).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setKeepContentOnPlayerReset(true)
                player = pc.player
            }
        },
        update = {
            it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            if (it.player !== pc.player) it.player = pc.player
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

/** True while Meow is a picture-in-picture window (provided by MainActivity). */
val LocalPip = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Edge-to-edge landscape video with tap-to-show controls. In picture-in-picture ([pip]) it's the
 * video alone: Android draws the window's own controls (from the media session) over it.
 */
@Composable
private fun FullscreenVideo(song: Song, forceLandscape: Boolean, pip: Boolean = false, onExit: () -> Unit) {
    val pc = LocalContainer.current.player
    val view = LocalView.current
    val activity = view.context as? Activity
    val isPlaying by pc.isPlaying.collectAsState()
    var controls by remember { mutableStateOf(true) }
    val position = rememberPosition(fast = false)
    DisposableEffect(forceLandscape) {
        val window = activity?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousOrientation = activity?.requestedOrientation
        if (forceLandscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            if (forceLandscape) activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(controls, isPlaying) { if (controls && isPlaying) { delay(3500); controls = false } }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls }, onDoubleTap = { pc.togglePlay() }) },
    ) {
        MusicVideoSurface(Modifier.fillMaxSize(), fill = false)
        AnimatedVisibility(controls && !pip, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                Column(Modifier.align(Alignment.TopStart).padding(24.dp)) {
                    Text(song.title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(song.artistText, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(16.dp).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onExit),
                    contentAlignment = Alignment.Center,
                ) { Icon(MeowIcons.FullscreenExit, "Exit full screen", tint = Color.White) }
                Box(
                    Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { pc.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (isPlaying) MeowIcons.Pause else MeowIcons.Play, "Play/pause", tint = Color.White, modifier = Modifier.size(44.dp)) }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp)) {
                    PlayerScrubber(position, pc.duration)
                }
            }
        }
    }
}

/** The last double-tap seek on the cover (#79): which way, how many taps in a row, and when (to time the label out). */
private data class SeekFlash(val ahead: Boolean, val steps: Int, val at: Long)

/** "−10 s" / "+10 s" over the cover's side while double-taps seek. */
@Composable
private fun SeekLabel(f: SeekFlash, step: Int, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!f.ahead) Icon(MeowIcons.Previous, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(
            (if (f.ahead) "+" else "−") + "${f.steps * step} s",
            color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp),
        )
        if (f.ahead) Icon(MeowIcons.Next, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

package com.meow.music.ui.screens

import com.meow.music.ui.theme.MeowIcons
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.meow.music.ui.theme.LocalContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Signs in through Google's own web sign-in page. Meow never sees your
 * password — it only keeps the resulting YouTube Music session cookies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit, onBack: () -> Unit, addAccount: Boolean = false) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0f) }
    var finishing by remember { mutableStateOf(false) }
    var pasteDialog by remember { mutableStateOf(false) }
    // Adding an account goes through Google's "add another account", which keeps the ones already signed in on this
    // web session. Signed in with a pasted cookie, there's no such session here: it's then an ordinary sign-in.
    val adding = remember {
        addAccount && CookieManager.getInstance().getCookie("https://music.youtube.com")?.contains("SAPISID=") == true
    }
    /** While adding: Google's pages have been shown, so landing back on YouTube Music means the account is in. */
    var sawGoogle by remember { mutableStateOf(false) }

    /** The session now holds one more Google account: kept, and the account list opens from Settings. */
    fun completeAdd(cookie: String) {
        if (finishing) return
        finishing = true
        scope.launch {
            CookieManager.getInstance().flush()
            val s = c.settings.current
            c.settings.saveIdentity(s.authUser, s.dataSyncId, cookie)
            android.widget.Toast.makeText(
                c.app, "Account added. Pick its channel under Switch account", android.widget.Toast.LENGTH_LONG,
            ).show()
            onBack()
        }
    }

    fun complete(cookie: String) {
        if (finishing) return
        // The web page goes away as soon as the session exists, so nobody is left on YouTube's site.
        finishing = true
        scope.launch {
            CookieManager.getInstance().flush()
            val (visitor, dataSyncFromSession) = withTimeoutOrNull(10_000) { c.innerTube.sessionInfo(cookie) } ?: (null to null)
            val accounts = withTimeoutOrNull(10_000) { runCatching { c.ytm.accounts(cookie) }.getOrNull() } ?: emptyList()
            val primaryAccount = accounts.firstOrNull()
            val finalDataSync = primaryAccount?.dataSyncId ?: dataSyncFromSession.orEmpty()

            c.settings.saveAccount(cookie, visitor.orEmpty(), finalDataSync)
            c.settings.setOnboarded(true)

            if (primaryAccount != null) {
                c.settings.saveAccountInfo(primaryAccount.name, primaryAccount.email, primaryAccount.avatar)
            }

            // Wait until the saved cookie is what requests will actually use,
            // otherwise the first account/library calls go out signed-out.
            c.settings.flow.first { it.cookie == cookie }

            if (primaryAccount == null) {
                c.scope.launch {
                    val info = withTimeoutOrNull(15_000) { runCatching { c.ytm.accountInfo() }.getOrNull() }
                    if (info != null) c.settings.saveAccountInfo(info.name, info.email, info.avatar)
                }
            }
            c.scope.launch {
                c.library.syncInBackground()
            }
            onDone()
        }
    }

    /** Signed in once the YouTube session cookies exist, whatever page the WebView is on. */
    fun checkCookies() {
        // Adding: the cookies are there from the start; [completeAdd] waits for the way back to YouTube Music.
        if (finishing || adding) return
        val cm = CookieManager.getInstance()
        val cookie = cm.getCookie("https://music.youtube.com") ?: cm.getCookie("https://www.youtube.com") ?: return
        if (cookie.contains("SAPISID=") || cookie.contains("__Secure-3PAPISID=")) complete(cookie)
    }

    LaunchedEffect(Unit) {
        while (isActive && !finishing) {
            checkCookies()
            delay(500)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Sign in to YouTube Music") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(MeowIcons.ArrowBack, "Back") } },
            actions = { TextButton(onClick = { pasteDialog = true }) { Text("Use cookie") } },
        )
        if (finishing) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(20.dp))
                Text("Signing you in to Meow…", style = MaterialTheme.typography.titleMedium)
            }
            return@Column
        }
        if (progress < 1f) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.stopLoading(); it.destroy() },
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Google blocks sign-in from user agents flagged as embedded web views.
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress / 100f }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                            checkCookies()
                            if (finishing) return true
                            // Keep everything inside this WebView; app-link schemes
                            // (intent://, vnd.youtube://) would otherwise dead-end.
                            val scheme = request.url.scheme ?: return false
                            if (scheme != "http" && scheme != "https") {
                                if (view.url?.contains("music.youtube.com") != true) view.loadUrl("https://music.youtube.com/")
                                return true
                            }
                            return false
                        }

                        // Catch the session the moment a redirect sets it, before YouTube's page draws.
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { checkCookies() }
                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) { checkCookies() }

                        override fun onPageFinished(view: WebView, url: String) {
                            checkCookies()
                            if (finishing) return
                            val host = android.net.Uri.parse(url).host.orEmpty()
                            if (adding) {
                                if (host == "accounts.google.com") sawGoogle = true
                                else if (sawGoogle && host == "music.youtube.com") {
                                    CookieManager.getInstance().getCookie("https://music.youtube.com")?.let(::completeAdd)
                                }
                                return
                            }
                            val cm = CookieManager.getInstance()
                            // Signed in, but left on a YouTube or Google page that isn't Music
                            // (youtube.com's home, Google's account page): hop to Music so its session cookies get set.
                            val strayYouTube = host.endsWith("youtube.com") && host != "music.youtube.com" &&
                                cm.getCookie("https://www.youtube.com")?.contains("SID=") == true
                            val strayGoogle = (host == "myaccount.google.com" || host == "www.google.com") &&
                                cm.getCookie("https://accounts.google.com")?.contains("SID=") == true
                            if (strayYouTube || strayGoogle) view.loadUrl("https://music.youtube.com/")
                        }
                    }
                    loadUrl(
                        if (adding) "https://accounts.google.com/AddSession?continue=https%3A%2F%2Fmusic.youtube.com%2F&service=youtube"
                        else "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com%2F&service=youtube&passive=true",
                    )
                }
            },
        )
    }

    if (pasteDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasteDialog = false },
            title = { Text("Sign in with a cookie") },
            text = {
                Column {
                    Text(
                        "If Google blocks the embedded sign-in, copy the Cookie header from a signed-in music.youtube.com browser session and paste it here. It's stored only on this device.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().height(140.dp), placeholder = { Text("SAPISID=…; __Secure-3PAPISID=…; …") })
                }
            },
            confirmButton = {
                TextButton(enabled = text.contains("SAPISID"), onClick = { pasteDialog = false; complete(text.trim()) }) { Text("Sign in") }
            },
            dismissButton = { TextButton(onClick = { pasteDialog = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Signs in to Discord for "Listening to Meow" presence on the listener's profile via OAuth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordLoginScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val user by c.discord.userName.collectAsState()
    val signedIn = c.discord.signedIn

    var showManualTokenDialog by remember { mutableStateOf(false) }
    var manualTokenInput by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(signedIn) {
        if (signedIn) {
            c.settings.setDiscordPresence(true)
            onBack()
        }
    }

    fun launchOAuth() {
        val authUri = c.discord.startAuthorization()
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, authUri)
        context.startActivity(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Sign in to Discord") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(MeowIcons.ArrowBack, "Back") } },
            actions = {
                TextButton(onClick = { showManualTokenDialog = true }) {
                    Text("Enter token")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(com.meow.music.R.drawable.app_icon_meow_logo),
                contentDescription = "Meow",
                modifier = Modifier.size(72.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Discord Rich Presence",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Display what you're listening to on Meow directly on your Discord profile with song names, artists, and album artwork.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(32.dp))

            if (signedIn) {
                Text(
                    "Signed in as ${user ?: "Discord User"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { c.discord.signOut() }) {
                    Text("Sign out")
                }
            } else {
                Button(
                    onClick = { launchOAuth() },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign in with Discord", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showManualTokenDialog) {
        AlertDialog(
            onDismissRequest = { showManualTokenDialog = false },
            title = { Text("Enter Discord Token") },
            text = {
                Column {
                    Text(
                        "Paste your Discord account token directly to sign in.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualTokenInput,
                        onValueChange = { manualTokenInput = it },
                        label = { Text("Discord Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualTokenInput.isNotBlank()) {
                            showManualTokenDialog = false
                            c.discord.signInWithToken(manualTokenInput.trim()) { success ->
                                if (success) {
                                    c.settings.setDiscordPresence(true)
                                }
                            }
                        }
                    },
                    enabled = manualTokenInput.isNotBlank(),
                ) {
                    Text("Sign in")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualTokenDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun WelcomeScreen(onSignIn: () -> Unit, onSkip: () -> Unit) {
    val t = rememberInfiniteTransition(label = "welcome")
    val a by t.animateFloat(0f, (Math.PI * 2).toFloat(), infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "a")
    Box(Modifier.fillMaxSize().background(Color(0xFF07060C))) {
        Canvas(Modifier.fillMaxSize()) {
            val cols = listOf(Color(0xFF7C5CFF), Color(0xFFFF3B5C), Color(0xFF00B4D8), Color(0xFFFFB627))
            cols.forEachIndexed { i, col ->
                val ph = a + i * 1.6f
                val p = Offset(size.width * (0.5f + 0.35f * cos(ph)), size.height * (0.35f + 0.22f * sin(ph * 1.3f)))
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), p, size.width * 0.7f), size.width * 0.7f, p)
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF07060C)), startY = size.height * 0.35f))
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text("Meow", color = Color.White, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
            Text("Your YouTube Music, refracted.", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(28.dp))
            Feature(MeowIcons.Spatial, "Meow Spatial audio & a 10-band EQ")
            Feature(MeowIcons.Lyrics, "Live lyrics on the player from ten sources")
            Feature(MeowIcons.Category, "Sort any playlist by genre")
            Feature(MeowIcons.Download, "Smart downloads & offline liked songs")
            Feature(MeowIcons.Palette, "Make it yours: fonts, layout, player & more")
            Spacer(Modifier.height(32.dp))
            Button(onClick = onSignIn, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                Text("Sign in with Google", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onSkip, Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Continue without an account", color = Color.White.copy(alpha = 0.8f))
            }
            WelcomeRestoreButton()
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}

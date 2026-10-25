package hu.tvbrowser

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Könnyűsúlyú Android TV / Google TV Webböngésző és Natív ExoPlayer Lejátszó
 * 2 GB RAM-mal rendelkező TV Boxokhoz optimalizálva (Amlogic, Allwinner, Rockchip).
 */
class MainActivity : AppCompatActivity(), VideoDetectionInterface.VideoDetectedListener {

    private lateinit var recyclerViewBookmarks: RecyclerView
    private lateinit var webViewContainer: View
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var playerView: PlayerView
    private lateinit var textStatus: TextView

    private lateinit var bookmarkAdapter: BookmarkAdapter
    private lateinit var bookmarkManager: BookmarkManager

    // Media3 ExoPlayer alacsony memória-fogyasztású konfigurációval
    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null

    // Állapotkezelés
    private enum class ScreenState {
        HOME, BROWSER, EXOPLAYER
    }
    private var currentState = ScreenState.HOME
    private var currentActiveUrl: String? = null

    // 2 GB RAM-hoz beállított korlátok (720p / 1080p)
    private val maxVideoWidth = 1280
    private val maxVideoHeight = 720

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bookmarkManager = BookmarkManager(this)

        initViews()
        setupRecyclerView()
        setupWebView()
        setupExoPlayer()

        showHomeScreen()
    }

    private fun initViews() {
        recyclerViewBookmarks = findViewById(R.id.recyclerViewBookmarks)
        webViewContainer = findViewById(R.id.webViewContainer)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        playerView = findViewById(R.id.playerView)
        textStatus = findViewById(R.id.textStatus)

        findViewById<Button>(R.id.btnSaveBookmark)?.setOnClickListener {
            saveCurrentPageAsBookmark()
        }

        findViewById<Button>(R.id.btnHome)?.setOnClickListener {
            showHomeScreen()
        }
    }

    /**
     * 1. MODUL: KEZDŐKÉPERNYŐ (Távirányító-barát csempés felület)
     * GridLayoutManager 3-4 oszloppal és fókuszálható kártyákkal.
     */
    private fun setupRecyclerView() {
        recyclerViewBookmarks.layoutManager = GridLayoutManager(this, 3)
        bookmarkAdapter = BookmarkAdapter(bookmarkManager.getBookmarks()) { bookmark ->
            openBrowser(bookmark.url)
        }
        recyclerViewBookmarks.adapter = bookmarkAdapter
    }

    /**
     * 2. MODUL: MEMÓRIA-OPTIMALIZÁLT WEBVIEW ÉS REKLÁMBLOKKOLÓ
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // 2 GB RAM kímélése: ne tartson a gyorsítótárban felesleges elemeket
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        // 4. MODUL: JavaScript Interfész a videók felismeréséhez
        webView.addJavascriptInterface(VideoDetectionInterface(this), "AndroidVideoBridge")

        webView.webViewClient = object : WebViewClient() {

            /**
             * 2. MODUL: Ad-Blocker interceptor
             * Üres WebResourceResponse-t ad vissza a reklámhálózatoknak
             */
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (AdBlockerHelper.isAdUrl(url)) {
                    // Blokkolás és üres válasz a RAM megtakarításához
                    return AdBlockerHelper.createEmptyResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                currentActiveUrl = url

                // 4. MODUL: Injektáljuk a videófelismerő JS szkriptet
                injectVideoDetectionScript()
            }
        }
    }

    /**
     * JavaScript kód injektálása, ami megkeresi a <video> és <source> elemeket
     */
    private fun injectVideoDetectionScript() {
        val jsScript = """
            (function() {
                function scanForVideos() {
                    const videos = document.querySelectorAll('video');
                    for (let v of videos) {
                        let src = v.currentSrc || v.src;
                        if (!src) {
                            const source = v.querySelector('source');
                            if (source) src = source.src;
                        }
                        if (src && src.startsWith('http') && !v.dataset.notified) {
                            v.dataset.notified = 'true';
                            window.AndroidVideoBridge.onVideoDetected(
                                src,
                                'video/mp4',
                                document.title || 'Videó'
                            );
                            return;
                        }
                    }
                }

                // Azonnali keresés és dinamikus DOM-változások figyelése (SPA oldalakhoz)
                scanForVideos();
                const observer = new MutationObserver(scanForVideos);
                observer.observe(document.body, { childList: true, subtree: true });
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsScript, null)
    }

    /**
     * 4. & 5. MODUL: AUTOMATIKUS VIDEÓ ÉSZLELÉS ÉS PÁRBESZÉDABLAK
     */
    override fun onVideoFound(videoUrl: String, mimeType: String, title: String) {
        runOnUiThread {
            if (isFinishing || isDestroyed || currentState == ScreenState.EXOPLAYER) return@runOnUiThread

            // Leanback TV Dialog megjelenítése
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_video_prompt)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val textTitle = dialog.findViewById<TextView>(R.id.dialogVideoTitle)
            val btnPlay = dialog.findViewById<Button>(R.id.btnPlayNative)
            val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)

            textTitle.text = title.ifEmpty { "Videó észlelve a weboldalon" }

            btnPlay.setOnClickListener {
                dialog.dismiss()
                startNativeExoPlayer(videoUrl, mimeType)
            }

            btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
            // Távirányító fókusz azonnal a lejátszás gombra kerüljön
            btnPlay.requestFocus()
        }
    }

    /**
     * 5. MODUL: MEMÓRIA ÉS FELBONTÁS KORLÁTOZÁS (ExoPlayer Media3)
     */
    private fun setupExoPlayer() {
        // 1. Felbontás korlátozása (max 720p vagy 1080p a 2 GB RAM miatt)
        trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(maxVideoWidth, maxVideoHeight)
                    .setMaxVideoFrameRate(60)
                    .setExceedVideoConstraintsIfNecessary(false)
            )
        }

        // 2. Puffer szigorú korlátozása: max 30 másodperc (DefaultLoadControl)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // minBufferMs: 15 mp
                30_000, // maxBufferMs: szigorúan max 30 mp
                500,    // bufferForPlaybackMs: 500 ms azonnali indítás
                1_000   // bufferForPlaybackAfterRebufferMs: 1 mp újrapufferelés
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector!!)
            .setLoadControl(loadControl)
            .build()

        playerView.player = exoPlayer
        playerView.keepScreenOn = true

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    exitExoPlayer()
                }
            }
        })
    }

    /**
     * Natív Media3 lejátszó indítása teljes képernyőn
     * KRITIKUS: A WebView-t kiürítjük, hogy felszabadítsuk a 2 GB RAM-ot!
     */
    private fun startNativeExoPlayer(videoUrl: String, mimeType: String) {
        currentState = ScreenState.EXOPLAYER

        // 1. WebView agresszív memóriamentesítése a videó idejére
        webView.onPause()
        webView.loadUrl("about:blank")
        webView.clearCache(true)

        // 2. Nézetek váltása
        webViewContainer.visibility = View.GONE
        recyclerViewBookmarks.visibility = View.GONE
        playerView.visibility = View.VISIBLE

        // 3. Videó betöltése és lejátszása
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(videoUrl))
            .setMimeType(
                if (videoUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4
            )
            .build()

        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true

        playerView.requestFocus()
        Toast.makeText(this, "Natív ExoPlayer aktív (Max felbontás: 720p/1080p)", Toast.LENGTH_SHORT).show()
    }

    private fun exitExoPlayer() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        playerView.visibility = View.GONE

        if (currentActiveUrl != null) {
            openBrowser(currentActiveUrl!!)
        } else {
            showHomeScreen()
        }
    }

    /**
     * Böngésző nézet megnyitása
     */
    private fun openBrowser(url: String) {
        currentState = ScreenState.BROWSER
        currentActiveUrl = url

        recyclerViewBookmarks.visibility = View.GONE
        playerView.visibility = View.GONE
        webViewContainer.visibility = View.VISIBLE

        webView.onResume()
        webView.loadUrl(url)
        webView.requestFocus()
    }

    /**
     * Visszatérés a csempés kezdőképernyőre és memória felszabadítása
     */
    private fun showHomeScreen() {
        currentState = ScreenState.HOME

        // WebView kiürítése a RAM felszabadítására
        webView.onPause()
        webView.loadUrl("about:blank")
        webView.clearCache(true)

        webViewContainer.visibility = View.GONE
        playerView.visibility = View.GONE
        recyclerViewBookmarks.visibility = View.VISIBLE

        // Könyvjelzők frissítése (ha új került mentésre)
        bookmarkAdapter.updateData(bookmarkManager.getBookmarks())
        recyclerViewBookmarks.requestFocus()
    }

    /**
     * 3. MODUL: KÖNYVJELZŐ MENTÉSE
     */
    private fun saveCurrentPageAsBookmark() {
        val url = webView.url ?: currentActiveUrl
        val title = webView.title ?: "Mentett oldal"
        if (!url.isNullOrEmpty() && url != "about:blank") {
            bookmarkManager.addBookmark(title, url)
            Toast.makeText(this, "Könyvjelző elmentve: $title", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * TÁVIRÁNYÍTÓ D-PAD ÉS VISSZA GOMB KEZELÉSE
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    when (currentState) {
                        ScreenState.EXOPLAYER -> {
                            exitExoPlayer()
                            return true
                        }
                        ScreenState.BROWSER -> {
                            if (webView.canGoBack()) {
                                webView.goBack()
                            } else {
                                showHomeScreen()
                            }
                            return true
                        }
                        ScreenState.HOME -> {
                            // Normál Android kilépés
                        }
                    }
                }
                KeyEvent.KEYCODE_MENU -> {
                    if (currentState == ScreenState.BROWSER) {
                        saveCurrentPageAsBookmark()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 5. MODUL: Teljes memóriakiürítés az Activity megsemmisülésekor
        webView.loadUrl("about:blank")
        webView.clearCache(true)
        webView.destroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}

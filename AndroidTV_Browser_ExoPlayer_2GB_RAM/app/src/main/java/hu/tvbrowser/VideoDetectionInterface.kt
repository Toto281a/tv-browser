package hu.tvbrowser

import android.webkit.JavascriptInterface

/**
 * JavaScript Bridge osztály a WebView-ban futó weboldalak videó-felismeréséhez.
 */
class VideoDetectionInterface(private val listener: VideoDetectedListener) {

    interface VideoDetectedListener {
        fun onVideoFound(videoUrl: String, mimeType: String, title: String)
    }

    /**
     * A weboldalba injektált JavaScript hívja meg, amikor <video> vagy HLS/m3u8 linket talál
     */
    @JavascriptInterface
    fun onVideoDetected(videoUrl: String, mimeType: String, title: String) {
        if (videoUrl.isNotEmpty()) {
            listener.onVideoFound(videoUrl, mimeType, title)
        }
    }
}

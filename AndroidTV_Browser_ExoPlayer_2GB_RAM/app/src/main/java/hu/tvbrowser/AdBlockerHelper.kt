package hu.tvbrowser

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Könnyűsúlyú, memória-hatékony reklámblokkoló segédosztály.
 * Null-overhead hash/kulcsszó szűrést alkalmaz, elkerülve a nehéz külső adatbázisokat.
 */
object AdBlockerHelper {

    // Gyakori hirdetési hálózatok és tracker kulcsszavak
    private val AD_KEYWORDS = arrayOf(
        "googleads",
        "doubleclick",
        "googlesyndication",
        "adservice",
        "pagead",
        "popunder",
        "popcash",
        "adnxs",
        "adroll",
        "outbrain",
        "taboola",
        "scorecardresearch",
        "moatads",
        "criteo",
        "rubiconproject",
        "pubmatic",
        "amazon-adsystem",
        "smartadserver",
        "adsystem",
        "advert",
        "banner",
        "pixel.facebook",
        "analytics.tiktok"
    )

    /**
     * Megvizsgálja, hogy a kért URL tartalmaz-e hirdetési kulcsszót
     */
    fun isAdUrl(url: String): Boolean {
        val lowerUrl = url.lowercase(Locale.ROOT)
        for (keyword in AD_KEYWORDS) {
            if (lowerUrl.contains(keyword)) {
                return true
            }
        }
        return false
    }

    /**
     * Üres WebResourceResponse-t hoz létre, amivel megakadályozzuk
     * a hirdetés letöltését és a RAM pazarlását.
     */
    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }
}

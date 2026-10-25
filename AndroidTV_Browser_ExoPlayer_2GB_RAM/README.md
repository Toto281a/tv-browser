# Android TV / Google TV Könnyűsúlyú Webböngésző (2 GB RAM TV Box Edition)

Ez a projekt kifejezetten a korlátozott memóriájú (2 GB RAM) Android TV és Google TV eszközökhöz készült (pl. Amlogic S905W2/Y4, Allwinner H616/H618, Xiaomi Mi Box, Chromecast HD).

## Főbb modulok megvalósítása

1. **Kezdőképernyő (RecyclerView + GridLayoutManager)**:
   - Fókuszálható `CardView` elemek (`android:focusable="true"`).
   - Távirányító nyilaival (D-Pad) akadálymentesen navigálható csempék fókusz-kiemeléssel.

2. **Ad-Blocker (Beépített reklámszűrő)**:
   - A `WebViewClient.shouldInterceptRequest` metódusában elfogja a hirdetési kulcsszavakat (`googleads`, `doubleclick`, `popunder`, stb.).
   - Üres `WebResourceResponse`-t ad vissza, drasztikusan csökkentve a processzor- és memóriaterhelést.

3. **Könyvjelzőkezelés**:
   - Mentés közvetlenül a böngészőből vagy a távirányító `MENU` gombjával.
   - Könnyűsúlyú `SharedPreferences` tárolás JSON formátumban.

4. **Automatikus videó felismerés és Natív ExoPlayer indítás**:
   - Dinamikus JavaScript injektálás (`MutationObserver` és `HTMLVideoElement` figyelő).
   - Videó észlelésekor Leanback párbeszédablakot nyit a távirányítóhoz.
   - Elfogadáskor a nehéz WebView-t azonnal kiüríti (`about:blank` + `clearCache`), és egy natív, hardveresen gyorsított Media3 ExoPlayert indít el.

5. **Memória és felbontás korlátozás**:
   - `DefaultTrackSelector`: Szigorúan 1280x720 (vagy 1080p) maximális felbontás a dekódoló memóriatúllépés megakadályozására.
   - `DefaultLoadControl`: A puffer maximum 30 másodpercre van korlátozva (15-30 mp).

## Fordítás és telepítés TV Boxra

1. Nyisd meg a projektet az **Android Studio**-ban.
2. Engedélyezd a TV Boxon a **Fejlesztői beállításokat (Developer Options)** és az **USB / Hálózati hibakeresést (ADB Debugging)**.
3. Csatlakozz ADB-n keresztül a TV-hez a hálózaton:
   ```bash
   adb connect <A_TV_IP_CÍME>:5555
   ```
4. Kattints a **Run (Futtatás)** gombra az Android Studio-ban, vagy építs APK-t:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

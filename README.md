# Daedalus Inventory Scanner

Android app that adds parts to a [Daedalus](../daedalus-electron) inventory by pointing the phone's camera at them. It reads the part number off a chip's markings or a distributor's bag label, matches it to a Daedalus library part, and adds it to the paired computer's inventory through [daedalus-api](../daedalus-api).

Native Kotlin, Jetpack Compose, CameraX, and ML Kit text recognition and barcode scanning. Recognition runs on the phone with bundled models: it works offline, needs no Google Play services, and no image leaves the phone. Only the part you choose to add is sent.

## Using it

1. **Pair.** On the computer, open **Parts Inventory → Pair Phone…**. In the app, scan the QR code, or type the code shown under it. A phone camera app can also open the QR, which launches the scanner. The code works once and expires after 5 minutes.
2. **Scan.** Point at a chip or bag label. The camera fills the top half; likely part numbers list in the bottom half, and each stays at least 10 seconds after its last good read. Pinch to zoom and tap to focus; turn on **Light** for small print. Pick the profile to add to from the top bar.
3. **Add.** Tap **Add** on a candidate, or **Type it in instead**. Check the part number, linked library part, value, package, quantity and location, then add it. With **Add to the existing line** on, a part that matches an existing line's name, value and package gets its quantity added to that line.

The computer's **Pair Phone…** panel lists paired phones and can unpair any of them. **Unpair this phone** in the app's menu does the same from the phone.

## How a part number is found

`scan/` holds the logic, with no Android dependencies, so it is unit-tested on the JVM:

- **`DistributorBarcode`** reads bag labels first, because they name the part outright. ECIA labels (Digi-Key, Mouser, Arrow) give `1P` (manufacturer part number) and `Q` (quantity). LCSC QR labels give `pm` and `qty`.
- **`PartNumberExtractor`** scores each word ML Kit reads:
  - **Shape:** letters and digits, as in `NE555P`, `74HC595` or `2N2222`.
  - **Family prefix:** a known one such as `SN`, `LM`, `ATMEGA` or `74`.
  - **Size:** a chip's part number is usually its largest print.
  - **Library match:** above all, whether a library part's id is inside the word (`SN74HC595N` contains `74HC595`).
  - **Low scores:** date codes (`2231`), lot codes (`9AK2C3M`) and words like `MALAYSIA`.
  - **OCR fixes:** letters misread between two digits are corrected, so `NE5S5P` becomes `NE555P`.
- **`PackageGuess`** fills in the package from manufacturer suffix conventions: `N`/`P` → DIP, `D`/`DR` → SOIC, `PW` → TSSOP, `-PU`/`-AU` on ATmega, `CV` on L78xx. The library part's pin count is added, giving `DIP-8`.
- **`CandidateTracker`** adds up scores over frames with decay. A part number read in many frames rises, and a one-frame misread fades. Once shown, a candidate is held 10 seconds after its last strong read and keeps its place in the list.

The library index is built-in parts from `app/src/main/assets/library.json` plus the parts the computer has imported (`GET /parts`). After adding built-in parts to Daedalus, regenerate the index with `npm run scanner:library` in `daedalus-electron`.

## Security

- **Pairing:** pairing gives the phone its own token, linked to the computer's anonymous device. The computer's token never leaves the computer. The API stores only a hash of each token.
- **What a phone can do:** read profiles and parts, add inventory items, and unpair itself. Everything else gets 403.
- **Token storage:** on the phone, the token is encrypted with an Android Keystore key and excluded from backups.
- **Links from other apps:** a pairing link opened from another app asks before pairing. It also shows the server when that isn't the default one.
- **HTTP:** release builds accept only `https://` servers. Debug builds also accept plain `http://`, for a local `wrangler dev`.

## Building

Requires the Android SDK (compileSdk 37) and JDK 17+.

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug         # onto a connected phone
./gradlew :app:testDebugUnitTest    # recognition, barcode and pairing-link tests
```

A typed-in code pairs with `https://daedalus-api.cd-cloud.workers.dev` by default. Set `daedalusApiUrl` in `local.properties` to build against another API. A scanned QR carries its own server address, so pairing with a dev API just works. From the emulator, the host's `wrangler dev` is at `http://10.0.2.2:8787`.

`ApiClientLiveTest` pairs with a running API and adds an item. It runs only when the environment provides a server and a fresh code:

```bash
DAEDALUS_LIVE_API=http://127.0.0.1:8787 DAEDALUS_LIVE_CODE=XXXX-XXXX ./gradlew :app:testDebugUnitTest --tests '*ApiClientLiveTest*'
```

Release signing reads `keystore.properties` (git-ignored: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`).

## Not done yet

- **AI identification** for markings OCR can't separate (laser-etched, faded, logo-only). The plan is a `POST /identify` on the Worker calling Claude vision with the photo and OCR text.
- **Resistor colour bands and SMD capacitor values:** passives are typed in for now.
- **Smaller APK:** split APKs per ABI, since the bundled ML Kit models make the debug APK about 80 MB.

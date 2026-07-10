# Bebin dnevnik

Potpuno lokalna, nativna Android aplikacija za evidenciju obroka, Waya kapi, vježbanja i tummy-time sesija. Nema profila djeteta, korisničkog računa, interneta, oglasa, analitike ni telemetrije.

## Značajke

- Kotlin, Jetpack Compose, Material 3 i single-activity arhitektura
- zasloni Danas, Kalendar, Statistika i Postavke
- šifrirana Room baza s SQLCipherom
- ključ baze generira se sigurnim generatorom, a u privatnoj pohrani ostaje samo AES-GCM omotan ključ; zaštitni ključ živi u Android Keystoreu
- validirani obroci, trostanja Waya kapi i vježbanja te dnevna potpunost
- monotona tummy-time štoperica koja se poništava čim aplikacija ode u pozadinu
- objedinjeni, odgodivi dnevni podsjetnik putem WorkManagera
- šifrirana, verzionirana sigurnosna kopija i transakcijski uvoz sa zamjenom podataka
- ZIP izvoz tri UTF-8 BOM CSV datoteke za pregled
- svijetla, tamna i sistemska tema, pristupačne oznake i dodirne površine
- automatski CI, API 29 emulator, potpisani GitHub Release i GitHub Pages

## Privatnost i sigurnost

Manifest namjerno **nema dozvolu `INTERNET`**. Androidova automatska cloud sigurnosna kopija i prijenos podataka na drugi uređaj onemogućeni su s `allowBackup=false` i pravilima `dataExtractionRules`. Izvoz i uvoz koriste Storage Access Framework, bez dozvole pristupa cijeloj pohrani.

SQLCipher baza obuhvaća obroke, dnevne statuse, tummy-time sesije i postavke. Nasumični 256-bitni ključ baze omotan je AES-256-GCM ključem iz Android Keystorea. Ako ključ ili baza nisu dostupni, aplikacija ne stvara tiho novu praznu bazu.

Obavijesti sadrže samo nazive neevidentiranih kategorija, nikada sadržaj zapisa. Aplikacija ne zapisuje obroke, evidencije, lozinke ni sadržaj sigurnosne kopije u logove.

## Preduvjeti i lokalni build

- Android Studio Quail 1 ili noviji
- JDK 21 (minimalno JDK 17)
- Android SDK Platform 37 i Build Tools 37.0.0

```bash
git clone https://github.com/OWNER/bebin-dnevnik.git
cd bebin-dnevnik
./gradlew :app:assembleDebug
```

Debug APK nastaje u `app/build/outputs/apk/debug/app-debug.apk`.

## Provjere i testovi

```bash
./gradlew :app:formatCheck :app:detekt :app:lintDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:connectedDebugAndroidTest
```

Unit testovi pokrivaju izračune, validacije, dnevnu potpunost, podsjetnik, sigurnosnu kopiju, krive lozinke, oštećene datoteke, CSV escaping, UTF-8 i prazne/veće skupove podataka. Instrumentation testovi pokrivaju šifriranu bazu i CRUD, zaštitu ključa, transakcijsko očuvanje podataka te Compose navigaciju i glavne kontrole.

CI workflow pokreće obvezne provjere i debug build na svakom pushu i pull requestu. Zaseban job pokreće instrumentation i Compose UI testove na emulatoru Android 10 / API 29.

## Format sigurnosne kopije

Datoteka `.bdk` ima binarno, verzionirano zaglavlje:

1. magic `BDK1`
2. verziju formata
3. broj PBKDF2 iteracija
4. duljine soli, noncea i šifriranog sadržaja
5. nasumičnu sol i 96-bitni nonce
6. AES-256-GCM šifrirani UTF-8 JSON sadržaj s autentikacijskim tagom

Ključ se izvodi s PBKDF2-HMAC-SHA-256 i 310.000 iteracija. Lozinka se ne sprema. Sadržaj uključuje verziju aplikacije, vrijeme izvoza, sve zapise i postavke. Uvoz prvo provjerava cijelu datoteku i GCM integritet, prikazuje sažetak, a zatim u jednoj Room transakciji zamjenjuje postojeće podatke. Nepoznata verzija, pogrešna lozinka ili korupcija ne mijenjaju bazu.

## Release keystore i GitHub Secrets

Generirajte vlastiti keystore; nikada ga nemojte commitati:

```bash
keytool -genkeypair -v -keystore release.keystore -alias bebin-dnevnik -keyalg RSA -keysize 4096 -validity 10000
```

Za lokalni potpisani build stvorite ignoriranu datoteku `keystore.properties`:

```properties
storeFile=release.keystore
storePassword=VAŠA_LOZINKA
keyAlias=bebin-dnevnik
keyPassword=VAŠA_LOZINKA_KLJUČA
```

Zatim pokrenite `./gradlew :app:assembleRelease` i provjerite APK s `apksigner verify --verbose app/build/outputs/apk/release/app-release.apk`.

Za GitHub Release pretvorite keystore u Base64 bez prijeloma redaka:

```bash
base64 -w 0 release.keystore > release.keystore.base64
```

U repozitoriju otvorite **Settings → Secrets and variables → Actions → New repository secret** i dodajte:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Push oznake `v*`, primjerice `git tag v1.0.0 && git push origin v1.0.0`, pokreće sve provjere. Tek nakon uspjeha workflow dekodira keystore, gradi i provjerava potpis, objavljuje `BebinDnevnik.apk` i `BebinDnevnik.apk.sha256`, te briše privremene tajne. Bez svih tajni workflow jasno prekida i ne tvrdi da je APK potpisan.

## GitHub Pages

Workflow `pages.yml` automatski objavljuje responzivnu stranicu iz mape `docs`. Na prvoj upotrebi u **Settings → Pages → Source** odaberite **GitHub Actions**. Predviđeni URL je `https://OWNER.github.io/bebin-dnevnik/`. Prije prvog potpisanog izdanja gumb vodi na posljednji uspješni CI i debug APK artefakt; nakon objave automatski vodi izravno na najnoviji `BebinDnevnik.apk` release asset.

## Licenca

[MIT](LICENSE)

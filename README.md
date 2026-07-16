# Bebin dnevnik

Local-first, nativna Android aplikacija za evidenciju mliječnih obroka, dohrane, Waya kapi, vježbanja, stolice, tummy-time sesija te profila i rasta djeteta. Nema oglasa, analitike ni telemetrije. Lokalna šifrirana baza potpuno radi bez interneta; mreža se koristi samo za GitHub ažuriranja i dobrovoljnu Google Drive sigurnosnu kopiju.

## Značajke

- Kotlin, Jetpack Compose, Material 3 i single-activity arhitektura
- zasloni Danas, Kalendar, Statistika, Rast i Postavke
- šifrirana Room baza s SQLCipherom
- ključ baze generira se sigurnim generatorom, a u privatnoj pohrani ostaje samo AES-GCM omotan ključ; zaštitni ključ živi u Android Keystoreu
- odvojeni validirani mliječni obroci i obroci dohrane s više namirnica te odvojenim jedinicama g/ml
- profil djeteta, mjerenja rasta i lokalni WHO referentni izračuni; Fenton rezultati nisu izmišljeni bez licence za podatke
- trostanja Waya kapi i vježbanja te dnevna potpunost u koju opcionalna dohrana ne ulazi
- monotona tummy-time štoperica koja se poništava čim aplikacija ode u pozadinu
- objedinjeni, odgodivi dnevni podsjetnik putem WorkManagera
- šifrirana, verzionirana sigurnosna kopija i transakcijski uvoz sa zamjenom podataka
- sigurni GitHub updater koji prije instalacije provjerava SHA-256, package name, versionCode i produkcijski certifikat
- lokalni, verzionirani i medicinski oprezni okvirni ciljevi mliječnog hranjenja i tummy timea ([izvori i formule](docs/GUIDELINE_TARGETS.md))
- dobrovoljni, klijentski šifrirani Google Drive `appDataFolder` backup s pet verzija
- ZIP izvoz osam UTF-8 BOM CSV datoteka za pregled
- svijetla, tamna i sistemska tema, pristupačne oznake i dodirne površine
- automatski CI, API 29 emulator, potpisani GitHub Release i GitHub Pages

## Privatnost i sigurnost

Manifest ima samo mrežne dozvole potrebne za updater i dobrovoljni cloud backup (`INTERNET`, `ACCESS_NETWORK_STATE`) te `REQUEST_INSTALL_PACKAGES` za otvaranje Androidova sistemskog instalacijskog dijaloga. Androidova automatska cloud kopija i prijenos na drugi uređaj ostaju onemogućeni s `allowBackup=false`; izvoz i uvoz koriste Storage Access Framework bez pristupa cijeloj pohrani.

SQLCipher baza obuhvaća mliječne obroke, dohranu, dnevne statuse, tummy-time sesije, profil, mjerenja rasta i postavke. Nasumični 256-bitni ključ baze omotan je AES-256-GCM ključem iz Android Keystorea. Ako ključ ili baza nisu dostupni, aplikacija ne stvara tiho novu praznu bazu.

Normalno ažuriranje preko postojeće aplikacije zadržava lokalnu bazu jer produkcijski `applicationId` ostaje `hr.bebindnevnik.app`, versionCode raste, a svaki release koristi isti certifikat SHA-256 `f1b4b84d9b0c729bd2ddf56309d581f0a541a80c822f18e69f2b3bbe659d2d5e`. Deinstalacija ipak uklanja lokalnu bazu i Android Keystore ključeve. Za vraćanje nakon deinstalacije potreban je isti Google račun i cloud-backup lozinka ili ručno izvezena `.bdk` datoteka. Deinstalacija nije postupak ažuriranja.

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

### Cloud format

Cloud kopija `.bdc` nikada ne sadrži nešifriranu bazu, JSON, CSV, SQLCipher ključ ili Android Keystore ključ. Jedan slučajni 256-bitni DEK šifrira sadržaj s AES-256-GCM i novim nonceom za svaku kopiju. Korisnička lozinka preko PBKDF2-HMAC-SHA-256 sa 600.000 iteracija izvodi KEK koji AES-GCM-om omata DEK. Zaglavlje sadrži samo verziju formata, KDF parametre, sol, nonceove, omotani DEK, vrijeme, verzije aplikacije/baze i broj zapisa. Za automatski backup DEK je lokalno dodatno omotan zasebnim Android Keystore ključem; nakon deinstalacije vraća se samo korisničkom lozinkom.

## Google Cloud Console i Drive API

Cloud backup zahtijeva jednokratnu vanjsku konfiguraciju:

1. U Google Cloud Console stvorite projekt i uključite **Google Drive API**.
2. Konfigurirajte OAuth consent screen.
3. Dodajte Android OAuth klijent za paket `hr.bebindnevnik.app` i produkcijski SHA-1 certifikata `da267ebff4a180c0a577ab71fa6357a6ceff08ac`.
4. Dodajte Web OAuth klijent za Credential Manager / Sign in with Google.
5. Web client ID lokalno upišite u ignorirani `local.properties` kao `GOOGLE_WEB_CLIENT_ID=...apps.googleusercontent.com`.
6. Na GitHubu isti client ID spremite kao Actions secret `GOOGLE_WEB_CLIENT_ID`.

Aplikacija traži samo scope `https://www.googleapis.com/auth/drive.appdata`. Skrivena mapa dostupna je samo ovoj aplikaciji. Ne traži pristup cijelom Driveu. Credential Manager služi za izbor računa, a Google Identity Services `AuthorizationClient` za granularno Drive dopuštenje.

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
- `GOOGLE_WEB_CLIENT_ID`

Push semantičke oznake, primjerice `v1.2.0`, izvodi monotoni versionCode formulom `major*1.000.000 + minor*1.000 + patch`. Workflow preuzima prethodni produkcijski APK i prekida ako novi code nije veći. Nakon builda provjerava paket `hr.bebindnevnik.app`, očekivani certifikat, versionCode i APK potpis, objavljuje `BebinDnevnik.apk`, `BebinDnevnik.apk.sha256`, broj verzije, datum i bilješke, pa briše privremeni ključ.

Trajni keystore `C:\Users\lukaj\Documents\bebin-dnevnik-release.jks` mora se čuvati u najmanje dvije sigurne offline kopije. Gubitak privatnog ključa znači da se budući APK ne može instalirati preko postojeće aplikacije bez valjanog proof-of-rotation postupka.

## GitHub Pages

Workflow `pages.yml` automatski objavljuje responzivnu stranicu iz mape `docs`. Na prvoj upotrebi u **Settings → Pages → Source** odaberite **GitHub Actions**. Predviđeni URL je `https://OWNER.github.io/bebin-dnevnik/`. Prije prvog potpisanog izdanja gumb vodi na posljednji uspješni CI i debug APK artefakt; nakon objave automatski vodi izravno na najnoviji `BebinDnevnik.apk` release asset.

## Licenca

[MIT](LICENSE)

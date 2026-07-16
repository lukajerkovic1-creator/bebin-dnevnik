# Obvezni završetak korisničkih izmjena

Za ovaj projekt lokalno dovršena promjena nije korisniku isporučena dok nije dostupna kroz ugrađenu opciju **Postavke → Provjeri i preuzmi novu verziju**.

Kada korisnik zatraži implementaciju i objavu, završni postupak mora:

1. povećati semantičku verziju i monotoni produkcijski `versionCode`;
2. proći build, lint, statičku analizu i testove;
3. objaviti potpisani `BebinDnevnik.apk` i njegov SHA-256 kroz postojeći release workflow;
4. pričekati uspješan GitHub Actions release i GitHub Pages deployment;
5. uživo provjeriti da `/releases/latest`, `version.json` i APK imaju istu novu verziju i očekivani certifikat;
6. tek tada korisniku dati izravni link i potvrditi da updater vidi novu verziju.

Ne tvrditi da je nova verzija dostupna dok ove provjere nisu završene.

# Referentni podaci modula Rast

## WHO Child Growth Standards (0–5 godina)

- Izvor: službeni repozitorij Svjetske zdravstvene organizacije, [`WorldHealthOrganization/anthro`](https://github.com/WorldHealthOrganization/anthro), mapa `data-raw/growthstandards`.
- Ugrađena revizija: `b776d8a12b1c97369c748b561159fd2ec4f4db58` (anthro 1.1.0.9000), preuzeto 16.07.2026.
- Licenca izvornog paketa i tablica: GNU General Public License v3.0. Tekst licence nalazi se u `third_party/who-anthro-LICENSE.md`.
- Ugrađene su dnevne LMS tablice za težinu/dob, duljinu-visinu/dob i opseg glave/dob te tablice u koraku 0,1 cm za težinu/duljinu i težinu/visinu.
- Izračun: službena LMS formula. Za ekstremne WHO rezultate težine/dobi koristi se WHO prilagodba izvan ±3 z. Vrijednosti se ne ekstrapoliraju izvan službenog raspona.
- Prijelaz duljina/visina: kada vrsta mjerenja ne odgovara dobnoj tablici, samo se za izračun primjenjuje +0,7 cm ili −0,7 cm; izvorna vrijednost ostaje nepromijenjena.

SHA-256 ugrađenih datoteka:

| Datoteka | SHA-256 |
|---|---|
| `head_circumference_for_age.tsv` | `e794e46f06b91223ad2c6435148dc08794a1d75b67613a652c3151201a98bf7c` |
| `length_height_for_age.tsv` | `709f7a11881451daf7820f022d363d5bdb93746b5361d6bd9218af6ff838e0c2` |
| `weight_for_age.tsv` | `bc15a6a623dd1d5beaeed1497666332aa54bc4ccd15ff9658c487d79694ab77b` |
| `weight_for_height.tsv` | `0050d31041c2f7d4f8a34f27e8066fd73a24806835b9e4a0de1a7ee46d54d582` |
| `weight_for_length.tsv` | `ad470cf41b147bd2a16026e57fd17968df7bf746b9f0bf2ff85df95239643778` |

## Fenton referentni sustav za nedonoščad

- Metodološki izvor: Fenton TR, Kim JH. *A systematic review and meta-analysis to revise the Fenton growth chart for preterm infants*. BMC Pediatrics 2013;13:59. DOI: [10.1186/1471-2431-13-59](https://doi.org/10.1186/1471-2431-13-59).
- Članak je objavljen pod CC BY 2.0, ali numerički LMS parametri nisu licencirani za slobodnu redistribuciju u aplikaciji. Dokumentacija PediTools projekta također izričito navodi ograničenje dijeljenja Fentonovih parametara.
- Zbog toga aplikacija ne ugrađuje, ne rekonstruira i ne ekstrapolira Fenton percentile. Za razdoblje do 50 postmenstrualnih tjedana mjerenje se čuva i prikazuje s jasnom oznakom da percentil nije dostupan. Nakon toga koristi se WHO prema pravilima korigirane dobi.

Ovo ograničenje je namjerno: medicinski rezultat ne smije se proizvoditi iz izmišljenih ili nelicenciranih referentnih podataka.

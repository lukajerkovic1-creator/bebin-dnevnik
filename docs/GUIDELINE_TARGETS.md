# Okvirni ciljevi hranjenja i tummy timea

Provjera izvora: **16. srpnja 2026.** Pravila su ugrađena lokalno u
`GuidelineCatalog`; aplikacija ne dohvaća medicinske podatke s interneta i ne
sprema izvedene postotke, raspone ni statuse.

Ovi izračuni nisu medicinski nalog, dijagnoza ni zamjena za individualni plan
pedijatra, neonatologa ili fizioterapeuta. Individualni cilj uvijek ima prednost.

## Mliječno hranjenje

### Od kraja prvog tjedna do navršenih 6 mjeseci

- ID/verzija: `NHS_FORMULA_ML_KG_2026_01` / `1.0.0`
- Organizacija: NHS (službeni NHS pružatelji zdravstvene skrbi)
- Primarni izvor: [Formula feeding – Nottinghamshire Healthcare NHS Foundation Trust](https://www.nottinghamshirehealthcare.nhs.uk/bis-formula-feeding)
- Potvrda: [Infant formula: common questions – NHS 111 Wales](https://www.111.wales.nhs.uk/livewell/pregnancy/bottleformulacommonquest/)
- Dob: od završetka prvog tjedna do približno 6 mjeseci
- Pravilo izvora: okvirno `150–200 ml/kg/dan`
- Formula aplikacije: `težina u kg × 150` do `težina u kg × 200`; obje granice
  zaokružuju se na najbližih 10 ml kako se ne bi prikazivala lažna preciznost.
- Ograničenja: NHS pravilo opisuje hranjenje formulom. Aplikacija ne razlikuje
  izdojeno majčino mlijeko i adaptirano mlijeko pa raspon prikazuje samo kao
  okvirnu informaciju. Ne procjenjuje količinu izravnog dojenja.

Prije završetka prvog tjedna nema generičkog brojčanog cilja. Za nedonošče prije
40+0 postmenstrualnih tjedana nema generičkog cilja. U oba slučaja prikazuje se
uputa za individualni plan.

### Od 6 do 12 mjeseci

- ID/verzija: `NHS_COMPLEMENTARY_MILK_2026_01` / `1.0.0`
- Organizacija: NHS / First Steps Nutrition Trust
- Izvor: [Eating well: the first year – West London Healthier Together NHS](https://healthiertogether.westlondon.nhs.uk/download_file/view/1389/4737)
- Dob: 6–12 mjeseci, uz dohranu
- Pravilo: široki informativni raspon `400–600 ml/dan`, izveden iz približnih
  vrijednosti istog izvora od 600 ml/dan u 7–9 mjeseci i 400 ml/dan u 10–12
  mjeseci.
- Ograničenja: ovo nije stroga nutritivna meta niti se prilagođava količini,
  kalorijama ili sastavu dohrane. Izravno dojenje nije moguće kvantificirati.

Nakon 12 mjeseci aplikacija ne prikazuje generički postotni cilj mlijeka. I dalje
prikazuje stvarno evidentirani unos i eventualni individualni cilj.

## Tummy time

- ID/verzija: `WHO_TUMMY_TIME_2019_01` / `1.0.0`
- Organizacija: World Health Organization (WHO)
- Izvor: [WHO Guidelines on physical activity, sedentary behaviour and sleep for children under 5 years of age](https://www.who.int/publications/i/item/9789241550536), 2019.
- Dob: dojenčad mlađa od 1 godine koja još nije samostalno pokretna
- Cilj: najmanje `30 min/dan` u potrbušnom položaju, budno i pod nadzorom,
  raspoređeno tijekom dana.
- Potvrda za postupni početak: [American Academy of Pediatrics – Back to Sleep, Tummy to Play](https://www.healthychildren.org/English/ages-stages/baby/sleep/Pages/Back-to-Sleep-Tummy-to-Play.aspx) preporučuje kratke sesije od 3–5 minuta, 2–3 puta dnevno, uz postupno povećavanje prema 15–30 minuta do približno 7. tjedna.
- Ograničenja: aplikacija ne propisuje trajanje pojedinačne sesije i nikada ne
  sugerira tummy time tijekom spavanja. Za nedonošče prije termina nema
  generičkog cilja. Nakon datuma samostalne pokretljivosti generički se postotak
  uklanja, ali štoperica i evidencija ostaju dostupne.

## Dob i tjelesna težina na povijesni datum

- Terminsko dijete koristi kronološku dob.
- Dijete rođeno prije 37+0 koristi korigiranu dob nakon dosezanja termina do
  drugog rođendana; zatim koristi kronološku dob. To je ista centralna
  implementacija koju koristi modul Rast.
- Potvrda: [AAP – Well Care for Formerly Preterm Infants](https://publications.aap.org/first1000days/module/27506/section/b941fe12-6cf2-410b-9a1a-a4f60a054f32) navodi korekciju gestacijske dobi do 24 mjeseca.
- Za svaki datum bira se posljednja težina izmjerena toga dana ili ranije, a kod
  više mjerenja istoga dana posljednje prema vremenu. Buduća mjerenja nikad se ne
  koriste. Porođajna težina privremeni je fallback samo dok nema kasnijeg
  mjerenja dostupnog za taj datum.
- Težina starija od 30 dana ostaje uporabiva, uz neutralnu oznaku da cilj postaje
  okvirniji.

## Povijest i potpunost evidencije

Postavka potpunosti evidentiranih mililitara, ručni očekivani broj obroka,
individualni ciljevi i datum samostalne pokretljivosti verzionirani su po datumu.
Povijesni dan zato uvijek koristi pravilo koje je tada vrijedilo. Ako evidentirani
mililitri nisu sav dnevni unos, aplikacija prikazuje stvarno evidentirane ml i
okvirni raspon, ali skriva postotak i klasifikaciju ispod/unutar/iznad raspona.

Automatska procjena broja obroka koristi prosjek prethodnih sedam kalendarskih
dana, uključujući odabrani dan, ali samo dane s najmanje jednim mliječnim obrokom
i potpunom evidencijom. Za stabilniji prikaz potrebna su najmanje tri takva dana.
Ručna vrijednost ima prednost i vrijedi od zadanog datuma.

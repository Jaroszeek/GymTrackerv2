# Decyzje

Format: data, decyzja, powód, alternatywy. Wersje sprawdzone 2026-10-18 w Google Maven, Maven Central i dokumentacji Androida.

## 2026-10-19 — Etap 0 (plan zaakceptowany)

### D-001 Jeden moduł `:app`
- **Decyzja:** cała aplikacja w `:app`, podział na pakiety `designsystem/`, `navigation/`, `core/`, `domain/`, `data/`, `feature/`. W Etapie 10 dochodzi `:baselineprofile` (wymaga go plugin Baseline Profile).
- **Powód:** jeden deweloper, jedna aplikacja; moduły dałyby głównie narzut konfiguracji.
- **Alternatywy:** moduły per warstwa lub per funkcja.

### D-002 Build: AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21
- **Decyzja:**
  - AGP 9.4.1 (max API 37) i Gradle wrapper 9.8.1 (AGP 9.4 wymaga ≥ 9.6.0).
  - Kotlin 2.4.21 podany przez `buildscript { classpath(kotlin-gradle-plugin) }`, bo AGP 9.4.1 ma wbudowany KGP 2.2.10, a Backdrop 2.0.1 i Coil 3.6 są zbudowane Kotlinem 2.4.
  - KSP 2.3.12, Java 17.
  - minSdk 35, targetSdk 36, compileSdk 37 (najnowszy wspierany przez AGP 9.4).
- **Powód:** najnowsze stabilne wersje.
- **Konsekwencja:** Android Studio musi być co najmniej Quail 4 (2026.1.4); Quail 1 obsługuje AGP tylko do 9.2. Właściciel aktualizuje AS.
- **Alternatywy:** AGP 9.2.1 bez aktualizacji AS.

### D-003 Nawigacja: Navigation 3 (1.2.0)
- **Decyzja:**
  - `androidx.navigation3` 1.2.0 (stabilne) z `lifecycle-viewmodel-navigation3`.
  - Osobny back stack dla każdej zakładki (oficjalny przepis „multiple back stacks”), klucze `@Serializable`.
  - Back z pierwszego ekranu zakładki wraca do Startu, a ze Startu wychodzi z aplikacji.
  - Aktywny trening to globalna warstwa poza stosami (z mini-paskiem nad navbarem), nie wpis w back stacku.
- **Powód:** Nav3 jest stabilne i daje wprost wiele stosów, predictive back i shared elements.
- **Alternatywy:** Navigation Compose 2.10.2 z type-safe routes.

### D-004 Baza: Room 3 (3.0.3) z `AndroidSQLiteDriver`
- **Decyzja:**
  - `androidx.room3` 3.0.3, KSP, `exportSchema` przez plugin `androidx.room3` do `app/schemas`.
  - Sterownik `AndroidSQLiteDriver` z `androidx.sqlite:sqlite-framework` 2.7.1, czyli systemowy SQLite.
- **Powód:**
  - Room 3 jest stabilny od 1.07.2026, a nowy projekt nie będzie musiał później migrować z Room 2.
  - DAO są suspend/Flow, a `MigrationTestHelper` jest w `room3-testing`.
  - Sterownik systemowy nie dokłada natywnych `.so`.
- **Ryzyko:** młoda wersja z mniejszą liczbą materiałów. Powrót do Room 2.8.5 jest możliwy w Etapie 2, jeśli coś zablokuje.
- **Alternatywy:** Room 2.8.5; `BundledSQLiteDriver` (stała wersja SQLite, +ok. 1 MB na ABI).

### D-005 Schemat bazy (zarys całej specyfikacji)
Każda wersja schematu oznacza migrację i test migracji. Czas zapisujemy jako epoch millis UTC, masę jako kg (`REAL`).

| Wersja / etap | Tabele |
|---|---|
| v1 / Etap 2 | `exercises` (external_id UNIQUE, name, aliases_pl, search_text bez diakrytyków, category, equipment, force, level, mechanic, instructions JSON, images JSON, source), `exercise_muscles` (exercise_id, muscle, is_primary) |
| v2 / Etap 3 | `routines`, `routine_blocks` (routine_id, position), `routine_variants` (block_id, exercise_id RESTRICT, target_sets, reps_min, reps_max, target_weight_kg, rest_seconds NULL = domyślna) |
| v3 / Etap 4 | `workouts` (routine_id SET NULL, name, started_at, ended_at NULL = trwa, updated_at, rest_ends_at), `workout_exercises` (workout_id, exercise_id RESTRICT, routine_variant_id SET NULL, position), `workout_sets` (type, weight_kg, reps, completed, completed_at) |
| v4 / Etap 6 | `personal_records`: cache przeliczany po zakończeniu, edycji i usunięciu treningu; unikalność na (exercise_id, type, weight_kg) |
| v5 / Etap 7 | `hc_exercise_sessions` (hc_id PK i podsumowanie tętna, stref oraz kcal), `workouts.hc_session_id` UNIQUE (łączenie 1:1), `workouts.hc_written_id`, `body_measurements` (type, value, measured_at, source, hc_id UNIQUE), `sleep_sessions`, `vital_samples` (RHR, HRV), `hc_sync_tokens` |

- **Aktywny trening:** wiersz `workouts` z `ended_at = NULL`, zapisywany z debounce. Porzucony ponad 24 h (według `updated_at`) jest usuwany przy starcie aplikacji. Dzięki temu nie ma osobnego snapshotu.
- **Ochrona ćwiczeń używanych w historii:** zapewnia FK `RESTRICT` w bazie, a nie kod aplikacji.
- **Wyszukiwanie:** `LIKE` na `search_text`. Przy ok. 900 wierszach wystarcza, więc FTS nie jest potrzebne.
- **Szablony rutyn:** JSON w assets, przy kopiowaniu zapisywany do tabel rutyn.
- **Czego nie trzymamy w Room:**
  - próbek tętna: wykres czytamy z HC na żądanie, w sesji zostaje podsumowanie;
  - kroków, dystansu i kalorii: Aggregate API na żądanie.
- **Tokeny zmian HC:** w bazie, nie w DataStore, żeby kopia zapasowa była z nimi spójna.
- **DataStore:** ustawienia, układ dashboardu, data urodzenia, ręczne tętno maksymalne.

### D-006 Szkło: Kyant Backdrop 2.0.1 ukryty w `GlassSurface`
- **Decyzja:** `io.github.kyant0:backdrop` 2.0.1 i `io.github.kyant0:shapes` 1.2.1 (narożniki o ciągłej krzywiźnie).
  - Jedna warstwa próbkowania (`layerBackdrop`) leży pod całą treścią.
  - Blur z vibrancy mają tylko warstwy pływające: navbar, mini-pasek, top bar po przewinięciu, sheety, dialogi, nakładka przerwy.
  - Refrakcję (`lens`) mają tylko navbar, mini-pasek i główny przycisk.
  - Karty z treścią są matowe, bez blur.
  - Nakładki to własne warstwy, a nie `Popup`.
  - Tryb „Ogranicz przezroczystość” daje nieprzezroczyste wypełnienie bez backdropu.
- **Zgodność:**
  - Backdrop 2.0.1 to Compose Multiplatform 1.12.0, który na Androidzie zależy od `androidx.compose.ui` 1.12.0.
  - BOM 2026.09.00 daje 1.12.1, więc Gradle wybierze 1.12.1 i konfliktu nie ma.
  - Biblioteka wymaga Kotlina 2.4 (patrz D-002).
- **Ryzyka:** jeden autor i 41 otwartych issues, m.in. #100 (szkło wewnątrz własnego źródła), #41 (przycinanie w listach), #91 (`Popup`). Dlatego biblioteka jest używana tylko w `GlassSurface`, więc wymiana silnika to zmiana w jednym miejscu.
- **Licencja:** Apache-2.0. Wpływ na rozmiar APK zmierzę w Etapie 1.
- **Alternatywy:** Haze 2.0.1 (Apache-2.0, tylko blur) jako plan B; Backdrop 1.0.x; własne shadery AGSL.

### D-007 Typografia: Geist + Instrument Serif
- **Decyzja:**
  - **Geist** w statycznych wagach 400, 500, 600 i 700 (OFL-1.1) do całego UI i wszystkich liczb, z `fontFeatureSettings = "tnum"`.
  - **Instrument Serif** Regular i Italic (OFL-1.1) tylko do dużych momentów display: powitanie, nagłówek podsumowania, puste stany. Nigdy do liczb, bo nie ma `tnum`.
- **Powód:**
  - W plikach fontu sprawdziłem, że Geist ma `tnum`, `pnum`, `frac` i komplet polskich znaków, a w małych rozmiarach jest czytelny.
  - Statyczne pliki omijają problem osi wagi w fontach zmiennych.
- **Alternatywy:** Inter Tight (za ciasny w 11–14 sp); Manrope (używa go vynox).

### D-008 Kolor
- **Decyzja:**
  - **Bazy:** prawie czarna z chłodnym odcieniem, AMOLED z czystą czernią, jasna złamana biel.
  - **Akcenty:** Volt (limonka, domyślny), Ember, Rose, Aqua, Azure, Amber; do tego monochromatyczne Onyx (ciemny) i Porcelain (jasny). Material You jako opcja.
  - **Tekst na akcencie:** kolor liczony z luminancji.
  - **Tło:** aurora z 2–3 plam w tonach akcentu, dryf 60–90 s, statyczna przy ograniczeniu animacji.
  - Dokładne wartości kolorów i pomiar kontrastu ≥ 4.5:1 robimy w Etapie 1.
- **Alternatywy:** szablonowe palety M3 (odrzucone w A4).

### D-009 Ikony: Material Symbols Rounded jako wektory
- **Decyzja:** importujemy tylko używane ikony jako vector drawable XML (warianty outline i filled dla navbara). Licencja Apache-2.0.
- **Powód:** jeden spójny zestaw bez biblioteki i bez dodatkowego rozmiaru.
- **Alternatywy:** `material-icons-extended` (duża, to Material Icons, a nie Symbols).

### D-010 Navbar z vynox
- **Źródło:** `Jaroszeek/vynox` (sklonowane poza projekt), pliki `ui/glass/GlassBottomBar.kt`, `LiquidSpan.kt`, `GlassSurface.kt`, `GlassScaffold.kt` oraz `ui/navigation/HideOnScrollState.kt`.
- **Decyzja:** pasek piszemy od nowa w `designsystem/`, wiernie odtwarzając vynox:
  - pigułka 64 dp z refrakcją;
  - kropla na dwóch sprężynach: krawędź prowadząca `spring(0.62, 900)`, ciągnięta `spring(0.8, 260)`, spłaszczenie o 18%;
  - wciśnięcie 0,88 na `spring(0.45, 600)`;
  - ikony outline → filled, haptyka `SegmentTick`, ponowne tapnięcie wraca do pierwszego ekranu zakładki;
  - chowanie przy przewijaniu (próg 48 dp);
  - wyróżniony środkowy element (ikona 28 dp i poświata akcentu).
- **Zmiany względem vynox:**
  - 5 zakładek w kolejności **Start · Ćwiczenia · Trening · Postępy · Profil**, z Treningiem jako wyróżnionym środkiem (wybór właściciela);
  - margines boczny 16 dp (zamiast 32) i maksymalna szerokość ok. 400 dp, bo zmieszczą się w nim etykiety 5 zakładek;
  - hairline jako specular z A4;
  - wartości z tokenów;
  - tryby „Ogranicz przezroczystość” i ograniczenia animacji.
- **Mini-pasek treningu:** dokuje nad paskiem, jak mini-player w vynox.

### D-011 Health Connect
- **Decyzja:**
  - `connect-client` 1.1.0, czyli stabilne (1.2.0 jest jeszcze alpha).
  - Do Room synchronizujemy przyrostowo (changes tokens): pomiary ciała, sesje ćwiczeń, sen, RHR i HRV.
  - Kroki, dystans, kalorie i serię tętna czytamy na żądanie przez Aggregate API lub odczyt.
  - Etap 7 zaczynamy od debugowego inspektora HC na telefonie właściciela.
  - `connect-testing` istnieje tylko w wersji alpha, więc o zgodę na nią zapytam w Etapie 7.
- **Powód:** mało danych w bazie i kopii zapasowej, a sumy z Aggregate API są poprawnie deduplikowane przez HC.

### D-012 Obrazy i sieć: Coil 3 + OkHttp
- **Decyzja:** Coil 3.6.3 (`coil-compose` i `coil-network-okhttp`, czyli OkHttp 5.5.0) z cache dyskowym. Z tego samego OkHttp skorzysta aktualizator w Etapie 11.
- **Licencja:** Apache-2.0. Wpływ na rozmiar zmierzę w Etapie 2.
- **Alternatywy:** `coil-network-ktor3` (większy, dokłada Ktor).

### D-013 Testy i CI
- **Decyzja:**
  - CI (`ci.yml`) uruchamia lint, testy jednostkowe i `assembleDebug`.
  - Testy instrumentalne (DAO, migracje, UI) uruchamiamy lokalnie przez `connectedDebugAndroidTest` przed każdym PR, a wynik trafia do raportu.
  - W Etapie 2 rozstrzygniemy, czy testy DAO wejdą do CI przez Robolectric 4.17.
- **Powód:** emulator w GitHub Actions jest wolny i niestabilny.

### D-014 Korekty planu etapów (zaakceptowane)
1. Etap 1 dostaje sondę Live Update (tylko debug):
   - przycisk w galerii publikuje powiadomienie `ProgressStyle` z `setRequestPromotedOngoing`;
   - pokazuje stany `hasPromotableCharacteristics`, `canPostPromotedNotifications` i `FLAG_PROMOTED_ONGOING`;
   - nowe uprawnienia `POST_NOTIFICATIONS` i `POST_PROMOTED_NOTIFICATIONS` trafiają tylko do manifestu debug (zgoda właściciela 2026-10-19).
2. Etap 1 dostaje szybki pomiar płynności blur: macrobenchmark `FrameTimingMetric` na przewijanej galerii pod navbarem.
3. Etap 7 zaczyna się od inspektora HC.
4. Testy instrumentalne uruchamiamy lokalnie, nie w CI (D-013).

### D-015 Czego nie dodajemy
- `core-splashscreen`: wystarcza API platformy, bo minSdk 35.
- `material-icons-extended`: zastępują go wektory z D-009.
- Turbine: wystarcza `kotlinx-coroutines-test`.
- Biblioteki wykresów: wykres jest własny na Canvas (A5.7).
- Accompanist.
- Haze: zostaje planem B.

### D-016 Środowisko (poza repo)
- Daemon Gradle działa na Javie 21 przez `org.gradle.java.installations.paths` w `%USERPROFILE%\.gradle\gradle.properties`, który wskazuje JBR z Android Studio. W repo nie ma ścieżek z tej maszyny.
- `platform-tools` jest w PATH użytkownika.
- AVD `gt-api36` (Pixel 6, `system-images;android-36;google_apis;x86_64`).
- Commity podpisuje `Jaroszeek <jaroszjasiek26@gmail.com>`. Właściciel zgodził się na publiczny adres email w historii.

## Zależności (plan; każda dostaje wpis z rozmiarem w etapie, w którym wchodzi)

| Biblioteka | Wersja | Licencja | Etap | Po co |
|---|---|---|---|---|
| AGP / Gradle | 9.4.1 / 9.8.1 | Apache-2.0 | 1 | build |
| Kotlin (KGP, compose, serialization) / KSP | 2.4.21 / 2.3.12 | Apache-2.0 | 1 | język, Compose, generowanie kodu |
| Compose BOM | 2026.09.00 (ui 1.12.1, material3 1.4.0) | Apache-2.0 | 1 | UI |
| activity-compose / core-ktx / lifecycle | 1.13.0 / 1.19.1 / 2.11.0 | Apache-2.0 | 1 | podstawa |
| navigation3 + lifecycle-viewmodel-navigation3 | 1.2.0 / 2.11.0 | Apache-2.0 | 1 | nawigacja (D-003) |
| Hilt / androidx.hilt | 2.60.1 / 1.4.0 | Apache-2.0 | 1 | DI |
| kotlinx-serialization-json | 1.11.0 | Apache-2.0 | 1 | klucze nawigacji, JSON |
| datastore-preferences | 1.2.1 | Apache-2.0 | 1 | ustawienia |
| backdrop / shapes | 2.0.1 / 1.2.1 | Apache-2.0 | 1 | szkło (D-006) |
| room3 / sqlite-framework | 3.0.3 / 2.7.1 | Apache-2.0 | 2 | baza (D-004) |
| Coil 3 + OkHttp | 3.6.3 / 5.5.0 | Apache-2.0 | 2 | zdjęcia ćwiczeń (D-012) |
| reorderable (Calvin-LL), kandydat | 3.1.0 | Apache-2.0 | 3 | drag & drop; decyzja w Etapie 3 (alternatywa: własna implementacja) |
| work-runtime-ktx / hilt-work | 2.12.0 / 1.4.0 | Apache-2.0 | 7 | synchronizacja HC w tle |
| health connect-client | 1.1.0 | Apache-2.0 | 7 | Health Connect (D-011) |
| profileinstaller / baselineprofile / benchmark-macro | 1.4.1 / 1.5.0 / 1.5.0 | Apache-2.0 | 1 (pomiar), 10 | Baseline Profile, pomiary |
| JUnit / coroutines-test / androidx.test runner, ext-junit / room3-testing | 4.13.2 / 1.11.0 / 1.7.0, 1.3.0 / 3.0.3 | EPL-1.0 / Apache-2.0 | 1–2 | testy |
| Fonty Geist, Instrument Serif | — | OFL-1.1 | 1 | typografia (D-007) |
| free-exercise-db | — | Unlicense | 2 | baza ćwiczeń (atrybucja w „O aplikacji”) |

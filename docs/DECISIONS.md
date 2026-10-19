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
| v3 / Etap 4 | `workouts` (routine_id SET NULL, name, started_at, ended_at), `workout_exercises` (workout_id, exercise_id RESTRICT, routine_variant_id SET NULL, position), `workout_sets` (type, weight_kg, reps) — tylko zakończone treningi (D-038) |
| v4 / Etap 6 | `personal_records`: cache przeliczany po zakończeniu, edycji i usunięciu treningu; unikalność na (exercise_id, type, weight_kg) |
| v5 / Etap 7 | `hc_exercise_sessions` (hc_id PK i podsumowanie tętna, stref oraz kcal), `workouts.hc_session_id` UNIQUE (łączenie 1:1), `workouts.hc_written_id`, `body_measurements` (type, value, measured_at, source, hc_id UNIQUE), `sleep_sessions`, `vital_samples` (RHR, HRV), `hc_sync_tokens` |

- **Aktywny trening:** ~~wiersz `workouts` z `ended_at = NULL`~~ — zmienione w D-038: plik JSON z debounce, w bazie tylko zakończone treningi.
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

## 2026-10-19 — Etap 1 (fundament i design system)

### D-017 Środowisko: Android Studio 2026.2.1, daemon Gradle na Javie 25
- **Decyzja:** daemon JVM 25 (`gradle/gradle-daemon-jvm.properties`), bo AS 2026.2.1 ma JBR 25, a JBR 21 zniknął razem ze starym AS. CI używa Temurin 25.
- **Poza repo:** `%USERPROFILE%\.gradle\gradle.properties` wskazuje teraz `C:/Program Files/Android/Android Studio1/jbr`. Stary katalog `Android Studio` jest niekompletny (brak `jvm.cfg`) i nieużywany.
- **Alternatywy:** pobranie JDK 21 przez foojay (dodatkowa instalacja bez potrzeby).

### D-018 Kolory i kontrast
- **Bazy:** ciemna `#0A0B0E` (karta `#14161B`), AMOLED `#000000`, jasna `#F3F2EE` (karta `#FFFFFF`).
- **Akcenty (wypełnienie / tekst na jasnym):** Volt `#C6F135`/`#3F6B00`, Ember `#FF6A3D`/`#B23A0E`, Rose `#FF6A94`/`#B21D50`, Aqua `#3EE6C8`/`#0B6B63`, Azure `#4DA3FF`/`#1A5BC4`, Amber `#FFB020`/`#8A5000`.
- **Token `accentText`:** jasne akcenty są czytelne jako wypełnienie z ciemnym tekstem, ale nie jako tekst na jasnym tle, więc tekst w kolorze akcentu ma osobny, ciemniejszy wariant.
- **Onyx i Porcelain** to pełne motywy mono z narzuconą jasnością: Onyx zawsze ciemny, Porcelain zawsze jasny; przełącznik System/Jasny/Ciemny ich nie zmienia. AMOLED działa też z Onyx.
- **Material You:** akcent = `primary`, aurora = `primary`, `tertiary`, `secondary` ze schematu systemowego; nie dotyczy Onyx i Porcelain.
- **Kontrast:** `ContrastTest` sprawdza ≥ 4.5:1 dla 8 palet × jasny/ciemny × AMOLED: wszystkie teksty na tle, karcie i podniesionej powierzchni, `onAccent` na akcencie, `accentText` na tle i na akcentowym tle odznak, tekst na szkle nałożonym na środek każdej plamy aurory (najgorszy przypadek) oraz pod kroplą navbara. Test wymusił rozjaśnienie Rose z `#FF5C8A` (4,44:1) na `#FF6A94` (4,71:1). Material You nie da się sprawdzić testem (zależy od tapety).

### D-019 Szkło: jak działa i ile kosztuje
- `GlassScaffold` ma dwie warstwy: treść z aurorą (źródło do rozmycia) i chrome (paski, nakładki). Szkło rozmywa tylko w chrome; w treści jest półprzezroczystym wypełnieniem.
- Dialog i bottom sheet to własne warstwy w chrome (host nakładek), a nie `Popup` ani okno platformy (D-006). Arkusz jest pływający, z marginesem od krawędzi; przeciąganie w dół do zamknięcia dojdzie, gdy będzie potrzebne.
- Aurora odświeża fazę co ok. 33 ms (30 fps): ruch na klatkę jest poniżej piksela, a warstwa pod szkłem nie przerysowuje się 120 razy na sekundę.
- **Rozmiar:** release z R8 (bez podpisu) ma 2,0 MB; Backdrop + Shapes to ok. 12 KB dexu.

### D-020 Galeria komponentów i build `benchmark`
- **Decyzja:** galeria to osobna aktywność w source secie `debug` z własną ikoną „GT Galeria” (od Etapu 6 bez ikony, D-058). To narzędzie deweloperskie, więc nie łamie zasady single-activity aplikacji. Motyw można ustawić przez extras intencji (skrypty zrzutów).
- Build type `benchmark` (`initWith(release)`, podpis debug, sufiks `.benchmark`) kompiluje źródła i manifest `debug`, żeby macrobenchmark przewijał galerię na kodzie po R8. W AGP 9 źródła Kotlina dodaje się przez `kotlin.directories`, nie `java.directories`.
- **Alternatywy:** galeria w `main` wycinana przez R8 (kod galerii trafiałby do kompilacji release).

### D-021 Ikony zakładek
- Start `home`, Ćwiczenia `menu_book`, Trening `exercise` (wyróżniony środek), Postępy `monitoring`, Profil `person`; warianty outline i filled z Material Symbols Rounded.
- Pierwsza wersja miała hantle w Ćwiczeniach i w Treningu; dwie podobne ikony obok siebie myliły.

### D-022 Live Updates: ustalenia z sondy (ważne dla Etapu 5)
- `Notification.Builder.setRequestPromotedOngoing()` i stała `EXTRA_REQUEST_PROMOTED_ONGOING` są dopiero w SDK **36.1**. Na urządzeniu z 36.0 wywołanie kończy się `NoSuchMethodError`. `NotificationCompat` (core 1.19.1) ma `setRequestPromotedOngoing` i `ProgressStyle` i ustawia extra `android.requestPromotedOngoing` na każdym poziomie API, więc w Etapie 5 używamy `NotificationCompat`.
- Akcja ustawień nazywa się `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` (`android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS`). Nazwy `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` z dokumentacji i z A7 w SDK 37 nie ma.
- Emulator `gt-api36` ma obraz 36.0 (build BE2A.250530): `hasPromotableCharacteristics = false`, `canPostPromotedNotifications = false`, powiadomienie nie jest promowane, a ekranu ustawień promocji brak (sonda wraca wtedy do ustawień powiadomień aplikacji). Kryterium Etapu 5 („promowane na emulatorze API 36”) wymaga obrazu 36.1 lub nowszego.

### D-023 Pomiar płynności blur
- Moduł `:baselineprofile` (`com.android.test`) z macrobenchmarkiem `FrameTimingMetric` przewija galerię z blurem i bez. Na emulatorze test uruchamia aplikację, ale metryka zwraca 0 klatek; po 3 podejściach odłożone do pomiaru na telefonie.
- Pomiar zastępczy na emulatorze (`dumpsys gfxinfo`, build `benchmark` z R8, 12 szybkich przewinięć): z blurem p50 28 ms, p90 42 ms, jank 28,4%; bez blura p50 23 ms, p90 36 ms, jank 25,7%. Emulator jest wolny także bez blura, więc liczby mówią tylko, że blur kosztuje ok. 5 ms na klatkę. Miarodajny będzie telefon.

### D-024 Drobne decyzje Etapu 1
- **DataStore** wchodzi dopiero w Etapie 9: w Etapie 1 nic nie jest zapisywane, aplikacja używa domyślnego motywu, a galeria trzyma motyw w stanie lokalnym.
- **CI** uruchamia się na push do `main` i na PR (bez podwójnych przebiegów dla branchy z PR).
- **Pole liczbowe:** przyciski −/+ pod wartością, żeby dwa pola mieściły się obok siebie na szerokości telefonu.
- **Wersja:** `versionName 0.1.0`, `versionCode 1`; od Etapu 11 wersja wynika z tagu.
- **Lint:** jedyne ostrzeżenie to `OldTargetApi` (targetSdk 36 jest ustalony w CLAUDE.md).

## 2026-10-19 — Etap 2 (ćwiczenia)

### D-025 Dane ćwiczeń
- **free-exercise-db** (Unlicense, 876 ćwiczeń) w `assets/exercises/free-exercise-db.json`, przypięty do commita `f00c92c7`; zdjęcia z `raw.githubusercontent.com` z tego samego commita, więc nie znikną po zmianach w repo źródłowym. Licencja w `assets/licenses/`, źródło pokazane w szczegółach ćwiczenia; pełna atrybucja w „O aplikacji” w Etapie 9.
- **26 wariantów z programów Jeffa Nipparda** (lista zaakceptowana w planie) w `assets/exercises/nippard.json`, w formacie free-exercise-db, bez zdjęć.
- **Polskie aliasy** dla 135 popularnych ćwiczeń (`aliases_pl.json`). Nazwy i instrukcje zostają po angielsku, jak w źródle.
- **Uprawnienie `INTERNET`** w manifeście głównym — zgoda właściciela 2026-10-19. Bez sieci zostają placeholdery.

### D-026 Schemat v1 i wyszukiwanie (zmiana względem D-005)
- Tabele `exercises` i `exercise_muscles` (PK `exercise_id` + `muscle`, FK CASCADE); schemat w `app/schemas/.../1.json`.
- **Bez kolumny `search_text`:** wyszukiwanie i filtry liczy w pamięci `ExerciseIndex` (domain, w pełni testowany). Teksty normalizowane raz na listę; każde słowo zapytania musi wystąpić w nazwie albo aliasie; nazwy zaczynające się od zapytania idą wyżej.
- **Filtry:** 8 partii ciała (dopasowanie po mięśniach głównych) i 8 grup sprzętu (gryf łamany = sztanga, brak sprzętu = inne). W grupie chipów OR, między grupami AND.
- Gdy źródło podaje mięsień jako główny i pomocniczy (9 ćwiczeń), zostaje główny.
- **Ochrona usuwania** ćwiczeń użytych w rutynach/treningach (FK RESTRICT i komunikat) przechodzi do Etapu 3, razem z pierwszą tabelą, która się do nich odwołuje — w Etapie 2 nie ma czego chronić ani jak tego przetestować. Wbudowanych ćwiczeń nie da się usunąć (warunek w DAO).

### D-027 Seed
- Leniwie przy pierwszym użyciu listy (stan ładowania w UI), w jednej transakcji, tylko gdy w bazie nie ma ćwiczeń wbudowanych; Mutex chroni przed podwójnym uruchomieniem. Drugi start nie dubluje danych (test + sprawdzone na emulatorze: 902 po restarcie).
- Dwa wsadowe inserty zamiast ok. 1800 pojedynczych: na emulatorze (debug) 8 s → 1,2 s; build z R8: 278 ms.

### D-028 Testy bazy na JVM (zamyka pytanie z D-013)
- Room 3 ma `inMemoryDatabaseBuilder` bez `Context`, a `androidx.sqlite:sqlite-bundled-jvm` 2.7.1 (tylko `testImplementation`) daje sterownik SQLite na JVM. Testy DAO, seeda i repozytorium są zwykłymi testami jednostkowymi i idą w CI — bez Robolectric i bez emulatora.
- `unitTests.isReturnDefaultValues = true`, żeby `android.util.Log` w kodzie produkcyjnym nie wywracał testów JVM.
- Testy migracji (`MigrationTestHelper`) zostają instrumentalne, gdy pojawi się v2.

### D-029 Decyzje UI Etapu 2
- **Tekst wyszukiwarki** trzymany w stanie Compose w ViewModelu, a nie w `StateFlow` stanu ekranu: pole zasilane asynchronicznym flow gubi znaki przy szybkim pisaniu. Wyszukiwarka ma wyłączoną autokorektę (polska autokorekta przerabiała angielskie nazwy).
- **ViewModel na wpis nawigacji** (`rememberViewModelStoreNavEntryDecorator`), parametry przez Hilt assisted injection. Klucze nawigacji mają sufiks `Key` (`ExerciseDetailKey`), bo nazwy `*Route` są zajęte przez composable.
- **Shared element** miniatura → zdjęcie w szczegółach (`SharedTransitionLayout` wokół `NavDisplay`).
- **Szczegóły:** zdjęcia pozycji startowej i końcowej przenikają się co 1,5 s (statycznie przy ograniczeniu animacji); „Wideo” otwiera wyszukiwanie YouTube.
- **Mięśnie w edytorze** ułożone wg partii ciała (`BodyPart.orderedMuscles`), nie alfabetycznie po angielsku.
- **Przyciski Secondary w treści** mają nieprzezroczyste wypełnienie: w treści nie ma czego rozmywać, a półprzezroczysty przycisk nad przewijanym tekstem wyglądał jak błąd.
- **„Ogranicz przezroczystość”** nie nagrywa już warstwy backdrop (nikt jej nie próbkuje).

### D-030 Rozmiar
- Release (R8, bez podpisu) 3,5 MB, rozmiar pobierania 2,0 MB (Etap 1: 2,0 MB).
- Dex: Room + SQLite ok. 53 KB, Coil + OkHttp + Okio ok. 280 KB, kod aplikacji 97 KB; Compose urósł z 0,77 do 1,2 MB (więcej używanych komponentów). Assets ćwiczeń 1 MB przed kompresją.

### D-031 Płynność listy
- Na emulatorze lista i galeria mają w tych samych warunkach identyczne wyniki, a praca wątku UI na klatkę to mediana ok. 1,4 ms — lista nie dokłada nic ponad samo szkło. Emulator jest niestabilny (galeria: 28 ms p50 przy Etapie 1, 93 ms dziś po restarcie emulatora), więc liczby nie nadają się do oceny. Pomiar na telefonie razem z Etapem 1 (D-023).
- Emulator: Gboard pokazywał samouczek rysika i przechwytywał tekst wpisywany przez `adb`; wyłączone `settings put secure stylus_handwriting_enabled 0` (tylko emulator).

## 2026-10-19 — Etap 3 (rutyny i szablony)

### D-032 Tryb pracy
- Właściciel: „nie musisz się za każdym razem pytać, po prostu rób”. Od Etapu 3 plan etapu nie czeka na akceptację; decyzje i tak trafiają tutaj, a akceptacja odbywa się na PR. To odstępstwo od punktu 2 „Sposobu pracy” w CLAUDE.md.

### D-033 Schemat v2 i migracja
- Tabele `routines`, `routine_blocks` (CASCADE z rutyny), `routine_variants` (CASCADE z bloku, **RESTRICT** na ćwiczenie). Migracja automatyczna 1 → 2 (tylko nowe tabele); schemat `app/schemas/.../2.json`.
- **Test migracji na JVM:** baza w starej wersji budowana z wyeksportowanego JSON-a schematu (to samo robi `MigrationTestHelper`), potem otwierana przez obecny Room — migracja i walidacja schematu idą w CI. Wzorzec dla kolejnych wersji: `MigrationTest.createFromSchema(version)`.
- **Ochrona ćwiczeń w użyciu** (zamyka D-026): `deleteCustom` najpierw sprawdza, czy ćwiczenie jest w jakiejś rutynie (`DeleteResult.InUse`), a FK RESTRICT jest siatką bezpieczeństwa. Sprawdzenie zamiast łapania wyjątku, bo sterownik systemowy i testowy zgłaszają naruszenie FK różnymi typami wyjątków. Etap 4 dopisze tabele treningów do tego sprawdzenia.

### D-034 Edycja rutyn
- **Zapis różnicowy:** zapisane bloki i warianty zachowują id (aktualizacja pozycji i celów, usuwanie tylko tego, czego nie ma w szkicu). Etap 4 łączy treningi z wariantami, więc „usuń wszystko i wstaw od nowa” zerwałoby te powiązania.
- **Szkic** (`RoutineDraft`, domain) ma czyste operacje; nowe elementy dostają ujemne tymczasowe id, które są też kluczami listy i drag & drop.
- **Wybór ćwiczenia** to warstwa w edytorze, która używa listy z Etapu 2 w trybie wyboru (osobny `ExercisesViewModel` per edytor) — bez przekazywania wyników między ekranami nawigacji.
- **Cele** edytowane w arkuszu i stosowane na „Gotowe”; normalizacja (min. 1 seria i powtórzenie, odwrócony zakres zamieniany, 0 = brak ciężaru / przerwa domyślna).
- **Wyjście z niezapisanymi zmianami** pyta o odrzucenie (porównanie szkicu z ostatnio zapisanym).
- Kolejność rutyn: data utworzenia. „Start” to zaślepka do Etapu 4.

### D-035 Szablony
- Zawartość z planu Etapu 3 (PPL, Upper/Lower, FBW A/B; 43 bloki, warianty jako bloki z alternatywą), w `assets/templates/templates.json`, ćwiczenia po `external_id`. Test sprawdza, że każde ćwiczenie szablonu istnieje w seedzie.
- Nazwy programów i rutyn są w JSON-ie (to nazwy własne, po skopiowaniu stają się danymi użytkownika); opisy programów są w `strings.xml`.
- Kopia tworzy wszystkie rutyny programu pod ich nazwami („Push”, „Pull”, „Legs”).

### D-036 Double progression (`domain/routine/DoubleProgression`)
- Gdy wszystkie serie robocze (robocze i do upadku; bez rozgrzewkowych i drop setów), co najmniej tyle ile cel, osiągnęły górę zakresu przy co najmniej docelowym ciężarze → następny ciężar = najmniejszy użyty ciężar + krok (domyślnie 2,5 kg, zaokrąglenie do 0,01). W przeciwnym razie brak propozycji (zostań przy ciężarze). Bez ciężaru (masa ciała) lub bez historii — brak propozycji. UI w Etapie 4.

### D-037 Zależność: Reorderable 3.1.0
- `sh.calvin.reorderable:reorderable` 3.1.0 (Apache-2.0, kandydat z planu Etapu 0): drag & drop w `LazyColumn` z autoscrollem; zależy od Compose 1.7, BOM 1.12 wygrywa. Rozmiar: ok. 17 KB dexu. Alternatywa: własna implementacja gestów (ok. 150 linii trudnej logiki).
- Release: 3,7 MB, rozmiar pobierania 2,1 MB.

## 2026-10-19 — Etap 4 (aktywny trening)

### D-038 Trwający trening w pliku, w bazie tylko zakończone (zmienia D-005)
- Trwający trening to `ActiveWorkout` (domain, `@Serializable`) w `files/active_workout.json`, zapisywany atomowo (plik tymczasowy, potem rename) z debounce 500 ms. Start, zakończenie i odrzucenie zapisują od razu. Po zabiciu procesu menedżer odtwarza go, zanim cokolwiek z niego skorzysta (`ready`).
- W bazie (schemat v3, migracja automatyczna 2 → 3 z testem na JVM) są tylko zakończone treningi i tylko odhaczone serie: `workouts` (routine_id SET NULL, name jako kopia, started_at, ended_at), `workout_exercises` (exercise_id RESTRICT, routine_variant_id SET NULL — wybrany wariant), `workout_sets` (type, weight_kg NULL = masa ciała, reps). Kolumny `completed`, `rest_ends_at`, `updated_at` z planu D-005 nie są potrzebne.
- Powód: historia (Etap 6) nie musi odfiltrowywać trwającego treningu; cały stan w jednym pliku jest prostszy niż różnicowanie wierszy przy każdej zmianie i trzyma razem przerwę oraz podpowiedzi z poprzedniej sesji.
- Rutyna, wariant albo ćwiczenie usunięte w trakcie treningu: zapis pomija zerwane powiązanie zamiast się wywrócić. Ćwiczenia z trwającego treningu nie da się usunąć (osobne sprawdzenie w menedżerze, bo trwającego treningu nie ma w bazie).
- **Porzucony ponad 24 h** (licząc od ostatniej zmiany) nie wraca, ale jego odhaczone serie trafiają do historii, z końcem w chwili ostatniej zmiany. D-005 zakładał usunięcie; wybrałem zapis, żeby zapomniany „Zakończ” nie kasował treningu. Do potwierdzenia przez właściciela.
- Znane ograniczenie: śmierć procesu dokładnie między zapisem do bazy a usunięciem pliku przywróci trening jeszcze raz (komentarz `ponytail:` w kodzie).

### D-039 Menedżer aktywnego treningu
- `ActiveWorkoutManager` (singleton w DI, `@ApplicationScope`) to jedno źródło prawdy: `StateFlow<ActiveWorkout?>` i `ready`. Zmiany są czystymi funkcjami `ActiveWorkout` (domain, testy); menedżer dokłada czas, zapis i koniec przerwy. W Etapie 5 czyta go serwis.
- Przerwa opiera się na absolutnych `startsAt` i `endsAt`. Kończy ją menedżer (czeka do `endsAt`, po −15 s / +30 s od nowa), więc działa też bez otwartego ekranu. Długość: cel z rutyny, inaczej 90 s (stała do Etapu 9).
- Drugi start przy trwającym treningu jest odrzucany, a dialog prowadzi do trwającego.

### D-040 Planowanie serii i ekran treningu
- **Serie na start:** liczba z rutyny (bez rutyny: jak ostatnio, inaczej 3). Ciężar: propozycja progresji, inaczej seria z poprzedniej sesji na tej samej pozycji, inaczej cel. Powtórzenia: dół zakresu przy progresji, inaczej poprzednie, inaczej dół zakresu. Rozgrzewki dopasowują się do rozgrzewek.
- **Otwarta jest jedna seria** (pierwsza nieodhaczona albo stuknięta) z dużymi polami; pozostałe to jedna linia z wartościami, którą można od razu odhaczyć. Bez powtórzeń nie da się odhaczyć.
- **Nowy ciężar spływa w dół** do nieodhaczonych serii tego samego rodzaju, które miały ten sam ciężar: zmiana ciężaru roboczego to jeden wpis.
- Usunięcie ostatniej serii usuwa ćwiczenie z treningu (bez osobnej akcji). Typ i usuwanie serii są w arkuszu pod numerem serii.
- Wariant można zmienić w trakcie; dopóki żadna seria nie jest odhaczona, serie planują się od nowa dla nowego wariantu.
- Haptyka: `Confirm` przy odhaczeniu, `ToggleOff` przy cofnięciu.
- **Nakładka przerwy** to pełnoekranowe szkło; zamknięta zostawia w nagłówku przycisk „Przerwa m:ss”. Po ponownym wejściu na ekran w trakcie przerwy pokazuje się znowu.
- **Mini-pasek** nad navbarem, gdy trening trwa, a jego ekran nie jest na wierzchu: nazwa, czas (w przerwie odliczanie) i postęp serii. Gdy navbar się chowa, pasek opada na dół; treść ekranów dostaje dodatkowy dolny padding.
- Start z karty rutyny albo „Pusty trening”; ćwiczenia dochodzą z tej samej listy wyboru co w edytorze rutyn (`ExercisePicker`).

### D-041 Zakończenie
- Zapisują się tylko odhaczone serie; trening bez odhaczonych serii się nie zapisuje (dialog to mówi).
- Podsumowanie: czas, objętość i liczba serii **bez rozgrzewek** (objętość = suma ciężar × powtórzenia), liczba ćwiczeń. Nowe rekordy dochodzą w Etapie 6.
- Zmiany celów, tylko dla ćwiczeń z rutyny: serie = odhaczone serie robocze (robocze i do upadku), ciężar = najcięższy roboczy; zakres powtórzeń i przerwa zostają. Domyślnie „Zostaw stare”; rutyna zmienia się dopiero po wyborze „Zastosuj jako nowe cele” i „Gotowe”.

### D-042 Test UI i zależności testowe
- `ui-test-junit4` i `ui-test-manifest` (z BOM, `ui-test-manifest` dokłada pustą aktywność tylko do manifestu debug), `androidx.test:runner` 1.7.0, `androidx.test.ext:junit` 1.3.0. Apache-2.0, tylko testy, nie trafiają do release.
- `WorkoutFlowTest` (instrumentacyjny): prawdziwe repozytoria na bazie w pamięci i prawdziwy seed; przebieg od karty rutyny przez serie, przerwę (+30 s, pomiń, zamknij), zakończenie i nowe cele do historii. Komenda: `.\gradlew.bat :app:connectedDebugAndroidTest`.
- CI nie ma emulatora, więc test UI nie idzie w CI (testy JVM, w tym menedżer i migracja, idą). Alternatywy: Robolectric (kolejna zależność, szkło renderuje się tam niepełnie), emulator w CI (ok. +10 min na build).
- Sam `connectedDebugAndroidTest` (bez `:app:`) uruchamia też macrobenchmarki w wariancie debug, które tam nie przechodzą; benchmark ma własną komendę (`connectedBenchmarkAndroidTest`).

### D-043 Design system
- Nowe: `GlassProgressRing` (łuk w `accentText`, bo żywy akcent ginie na jasnym tle), `GlassMiniBar`, `GlassFullScreenOverlay`; placeholder w `GlassNumberField`; `GlassChip` ucina długi tekst. Wszystko w galerii (sekcje „Nakładki” i „Postęp i trening”).
- Release: 3,6 MB.

### D-044 Live Updates: wymagania i jak je spełniamy (Etap 5)
Źródła (sprawdzone 2026-10-19): developer.android.com/develop/ui/views/notifications/live-update; documentation.onesignal.com/docs/en/android-live-notifications; androidauthority.com/one-ui-8-live-updates-support-3573794; sammyfans.com (One UI 8 Beta 3, 2025-07-04); wątki r2.community.samsung.com.
- Wymagania z dokumentacji Androida i jak je spełnia `WorkoutNotifications`:
  - styl Standard, BigText, Call, **Progress** albo Metric → `NotificationCompat.ProgressStyle` (segment na ćwiczenie, postęp = ukończone serie);
  - `POST_PROMOTED_NOTIFICATIONS` w manifeście (zwykłe, nie runtime) → manifest główny;
  - prośba o promocję → `NotificationCompat.Builder.setRequestPromotedOngoing(true)` (ustawia extra na każdym API, D-022);
  - `ongoing`, tytuł (nazwa rutyny), bez `customContentView`, bez `setGroupSummary`, bez `setColorized(true)` → spełnione;
  - kanał nie może mieć `IMPORTANCE_MIN` → kanał „Trening w toku” ma `IMPORTANCE_DEFAULT`, bez dźwięku, wibracji i odznaki. Najpierw był `IMPORTANCE_LOW` (zalecenie OneSignal), ale test na emulatorze pokazał, że Android traktuje go jako cichy i domyślnie chowa z ekranu blokady (`lock_screen_show_silent_notifications`), a A5.4 wymaga tam odliczania przerwy. Kanał bez dźwięku nie hałasuje także przy `DEFAULT`; heads-up jest dopiero od `IMPORTANCE_HIGH`.
- Chip na pasku stanu („fasolka”, widoczny po wyjściu z aplikacji) pokazuje zegar: czas treningu (`setWhen(start)` + `setUsesChronometer`), a w przerwie odliczanie (`setWhen(koniec przerwy)` + `setChronometerCountDown`). Dokumentacja: „A timer in the chip can be shown when using Chronometer for when time… The Chronometer timer is shown in the chip as long as it is positive”. Krótkiego tekstu (`setShortCriticalText`) nie ustawiamy: dokumentacja nie opisuje, co wygrywa, gdy są oba, a tekst mógłby zastąpić zegar. Najpierw chip pokazywał postęp „3/8” (A7); właściciel poprosił o zegar, A7 zmienione 2026-10-19. Postęp serii zostaje na pasku postępu powiadomienia.
- Akcje: „Ukończ serię” (poza przerwą, gdy bieżąca seria ma powtórzenia) i „Pomiń przerwę” (w przerwie). Dokumentacja nie zabrania akcji.
- Odrzucone przez użytkownika powiadomienie nie wraca, dopóki nie otworzy aplikacji (`setDeleteIntent`; dokumentacja: „Don't repost Live Updates that the user dismissed”).
- API: według `api-versions.xml` z SDK 37 `canPostPromotedNotifications()`, `hasPromotableCharacteristics()` i `FLAG_PROMOTED_ONGOING` są od API 36, a samo promowanie działa od Androida 16 QPR1 (36.1; D-022). `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` z dokumentacji i A7 w SDK nie istnieje; używamy `ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`.
- One UI 8.5 (wiedza z raportów, nie z dokumentacji Samsunga): Live Updates trafiają do Now Bar i na AOD; część wersji One UI 8 wpuszcza aplikacje spoza listy Samsunga dopiero po włączeniu opcji programisty „Live notifications for all apps”; w One UI 8.5 ma być przełącznik powiadomień na żywo per aplikacja; spełnienie wymagań Androida nie gwarantuje promocji (OEM może dodać własne kryteria). Dlatego ekran diagnostyki (D-049) i sekcja README.

### D-045 Nowe uprawnienia w manifeście (zaakceptowane z Etapem 5)
CLAUDE.md wymaga zgody na nowe uprawnienia. Wszystkie wynikają wprost z A7 i A5.4 w PROMPT.md, więc dodałem je i zgłaszam w raporcie:
- `POST_NOTIFICATIONS` (runtime) i `POST_PROMOTED_NOTIFICATIONS` (zwykłe) — przeniesione z manifestu debug do głównego;
- `FOREGROUND_SERVICE` i `FOREGROUND_SERVICE_SPECIAL_USE` (zwykłe) — serwis typu `specialUse` z `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`;
- `SCHEDULE_EXACT_ALARM` (dostęp specjalny, domyślnie wyłączony; użytkownik włącza go w ustawieniach „Alarmy i przypomnienia”);
- `WAKE_LOCK` i `VIBRATE` (zwykłe) — zapasowa blokada uśpienia na czas przerwy i wibracja na koniec.
Odrzucone: `USE_EXACT_ALARM` (przyznawane automatycznie, ale zarezerwowane dla budzików i kalendarzy), typ serwisu `health` (wymaga uprawnień do czujników albo aktywności fizycznej, A7 mówi `specialUse`).

### D-046 Serwis treningu i przerwa w tle
- `WorkoutService` startuje tylko z `MainActivity` w stanie RESUMED, gdy trwa trening (klik „Start” albo powrót z treningiem odtworzonym po śmierci procesu). Przy każdym starcie z aplikacji od razu woła `startForeground`; jeśli system odmówi, loguje błąd i się zatrzymuje. `START_NOT_STICKY`: system nie wskrzesza serwisu po śmierci procesu, bo byłby to start z tła.
- Treść pochodzi z `ActiveWorkoutManager.workout` i nazw ćwiczeń; gdy treningu nie ma (zakończenie, odrzucenie), serwis usuwa powiadomienie i się zatrzymuje. Po śmierci procesu system sam usuwa powiadomienie serwisu.
- Tapnięcie otwiera trening (`ACTION_OPEN_WORKOUT`, aktywność dostaje intent przez `onNewIntent`).
- Koniec przerwy: timer menedżera liczy na zegarze monotonicznym, który stoi w głębokim uśpieniu, więc przy wygaszonym ekranie budzi telefon `RestAlarm`: exact alarm (`setExactAndAllowWhileIdle`, RTC_WAKEUP), gdy `canScheduleExactAlarms()`, inaczej częściowa blokada uśpienia na czas przerwy + 5 s. Na koniec wibracja (`USAGE_ALARM`, wzór 400-200-400-200-700 ms) i `manager.endRestIfDue()`. Pominięta albo skrócona przerwa kasuje budzenie; przerwa, która skończyła się sama, je zachowuje, więc wibracja zawsze następuje. Odbiornik alarmu działa także w nowym procesie, gdy system zabił aplikację.
- Dźwięk na koniec przerwy (A5.4: opcjonalny) na razie wyłączony; przełącznik dojdzie z ustawieniami w Etapie 9.

### D-047 Zgoda na powiadomienia i karta „Trening w tle”
- Przy pierwszym starcie (flaga w DataStore) dialog wyjaśnia powiadomienie treningu, potem systemowa prośba o `POST_NOTIFICATIONS`. „Nie teraz” też zapisuje flagę, więc dialog nie wraca.
- Na ekranie treningu karta „Trening w tle” wylicza, czego brakuje: powiadomień, baterii „Bez ograniczeń” (`isIgnoringBatteryOptimizations`; „Ograniczona” = `isBackgroundRestricted`) i zgody na alarmy, każdą pozycję z przyciskiem do właściwego ekranu ustawień, plus „Diagnostyka” i „Ukryj” (do zamknięcia ekranu treningu). Stan odświeża się przy każdym powrocie na ekran.

### D-048 Zależność: DataStore Preferences 1.2.1
- Po co: flaga „prośba o powiadomienia pokazana”; w Etapie 9 ustawienia. DataStore jest w ustalonym stosie (CLAUDE.md).
- Alternatywy: `SharedPreferences` (bez zależności, ale synchroniczny I/O i poza ustalonym stosem), tabela w Room (za ciężkie na flagi).
- Licencja Apache-2.0. Wersja 1.2.1 to najnowsza stabilna (1.3.0 jest w RC).
- Rozmiar release: patrz D-050.

### D-049 Ekran „Diagnostyka powiadomień na żywo”
- Otwierany przyciskiem na dole ekranu treningu i z karty „Trening w tle” (w Etapie 9 także z ustawień).
- Pokazuje i loguje (`adb logcat -s LiveUpdate`): wersję Androida z `SDK_INT_FULL` (np. 36.1), urządzenie, `areNotificationsEnabled`, ważność kanału, `canPostPromotedNotifications`, `hasPromotableCharacteristics` powiadomienia zbudowanego teraz (bez treningu: przykładowego), `FLAG_PROMOTED_ONGOING` opublikowanego powiadomienia, zgodę na dokładne alarmy i stan baterii. Na Samsungu dodatkowa karta z przełącznikami One UI.
- Deep-linki: ustawienia powiadomień aplikacji, powiadomienia na żywo (`ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`, poniżej API 36 i przy braku ekranu: ustawienia powiadomień), informacje o aplikacji (bateria), „Alarmy i przypomnienia”, opcje programisty (gdy są wyłączone, ich ekran od razu się zamyka, więc przycisk otwiera wtedy „Informacje o telefonie” z numerem wersji, którym się je włącza).
- Sonda z galerii debug (D-014) zostaje na razie bez zmian; dubluje część diagnostyki, więc można ją usunąć (pytanie w raporcie).

### D-050 Emulator 36.1, wyniki testów Etapu 5, rozmiar
- Zainstalowany obraz `system-images;android-36.1;google_apis;x86_64` rev 4 (build BE4B.251210.005) i AVD `gt-api36_1` (pixel_6). Pułapki:
  - wpis `hw.ramSize` z `config.ini` nie zadziałał: gość dostał 256 MB, jądro panikowało („System is deadlocked on memory”) i emulator restartował się w kółko; uruchamiać z `-memory 3072`;
  - na tym obrazie każdy odczyt bufora GPU przez CPU kończy się asercją `!rcEnc->featureInfo()->hasReadColorBufferDma` w `GoldfishMapper::readFromHost`: pada `screencap`, SurfaceFlinger (próbkowanie pod paskiem gestów) i `system_server` (miniatury w „ostatnich” po Home). Bez zmian przy `swiftshader_indirect`, `-gpu host`, bez okna i z ukrytym oknem, z wymuszonym `-feature GLDMA2,GLDirectMem`. Obejścia: nawigacja trzema przyciskami, zrzuty od strony hosta (`adb emu screenrecord screenshot <katalog>`), aplikacja tylko na pierwszym planie. Testy z wyjściem w tło robione na `gt-api36` (36.0).
  - Emulator + demon Gradle + demon Kotlina wyczerpały plik stronicowania (JVM-y nie startowały: `hs_err_pid*.log`). Przy emulatorze: `-Pkotlin.compiler.execution.strategy=in-process` i `.\gradlew.bat --stop` po buildzie.
- Wyniki (2026-10-19):
  - 36.1: powiadomienie ma `FLAG_PROMOTED_ONGOING` (także po odtworzeniu po śmierci procesu i z kanałem `IMPORTANCE_DEFAULT`); diagnostyka: `canPostPromoted`, `promotable`, `promoted` = true; akcje „Ukończ serię” i „Pomiń przerwę” z panelu działają; odliczanie przerwy w powiadomieniu; tap otwiera trening; alarm dokładny kończy przerwę co do kilku ms i wibruje; `kill -9` procesu nie zostawia powiadomienia ani serwisu, po powrocie trening i powiadomienie wracają; zakończenie usuwa powiadomienie. `WorkoutNotificationsTest` 3/3 (przez `am instrument`; `connectedDebugAndroidTest` na tym emulatorze zgłaszał 0 testów).
  - 36.0: koniec przerwy przy wygaszonym ekranie wibruje (blokada uśpienia, a po włączeniu zgody alarm dokładny także w wymuszonym głębokim Doze); odliczanie na ekranie blokady (po zmianie kanału na `DEFAULT`, D-044); usunięcie z „ostatnich” nie zatrzymuje serwisu; tap w powiadomienie bez zadania otwiera trening; odrzucenie usuwa powiadomienie; deep-linki (powiadomienia, informacje o aplikacji, „Alarmy i przypomnienia”, a ekranu promocji brak i jest fallback). `connectedDebugAndroidTest` 4/4.
  - Chipa na pasku stanu i Now Bar nie dało się obejrzeć: chip widać dopiero z aplikacją w tle, a na 36.1 wyjście w tło wywala emulator. Sprawdzenie na telefonie (PROGRESS.md).
- Release: 3,88 MB (było 3,6 MB; +0,28 MB, głównie DataStore).

### D-051 Wydanie testowe przed v1.0.0
- Na polecenie właściciela (2026-10-19) GitHub Release `preview-stage-5`: oznaczony jako pre-release, z APK debug zbudowanym lokalnie z brancha etapu.
- APK debug, bo ma osobny `applicationId` (`.debug`, nazwa „GymTracker Debug”): instaluje się obok przyszłej wersji podpisanej kluczem właściciela i jej nie blokuje. Kolejne podglądy budowane na tym samym komputerze mają ten sam klucz debug, więc instalują się jako aktualizacja bez utraty danych. Build debug nie ma R8, więc animacje mogą być mniej płynne niż w wydaniu.
- Tag poza schematem `v*`: A8 rezerwuje `v*` dla podpisanych wydań (versionCode z tagu), a Etap 11 zaczyna od `v1.0.0`. Aktualizator z A5.11 czyta `releases/latest`, które pomija pre-release, więc podglądu nie zaproponuje.
- Odrzucone: release podpisany kluczem debug (po zainstalowaniu zablokowałby właściwą wersję — inny podpis, trzeba by odinstalować i stracić dane), tag `v0.5.0` (miesza się ze schematem wersji z A8).

### D-052 Import kopii z GymTracker v1 (zakres)
- Na prośbę właściciela (2026-10-19) dopisany do A5.10 i Etapu 9: v2 wczytuje kopię zapasową z pierwszej wersji. Z repo `Jaroszeek/GymTracker` bierzemy tylko format pliku kopii, i to dopiero w Etapie 9; kodu, UI ani funkcji v1 nie przenosimy.
- Domyślnie import kopii v1 zastępuje dane v2 jak zwykły import (poprzednia baza zostaje w kopii). Łączenie z danymi v2 wymagałoby deduplikacji ćwiczeń i treningów — do decyzji właściciela w planie Etapu 9.
- Test na syntetycznej kopii w formacie v1; prawdziwa kopia właściciela nie trafia do repo (dane zdrowotne).

### D-053 Historia i postępy: układ (Etap 6)
- Zakładka Postępy ma cztery segmenty: Historia, Ćwiczenia, Rutyny, Partie. Historia mieszka w Postępach, bo navbar ma stałe pięć zakładek (A3). Lista treningów jest pogrupowana po miesiącach.
- Szczegóły treningu pokazują wszystkie serie z odznakami rekordów. Edycja jest osobnym ekranem: nazwa, czas trwania w minutach, ciężar, powtórzenia i typ serii, dodawanie i usuwanie serii oraz ćwiczeń, pytanie o odrzucenie zmian. Usunięcie wymaga potwierdzenia.
- Edycja nie zmienia daty ani godziny startu; czas trwania przesuwa koniec (zmiana daty w `IDEAS.md`).
- Szczegóły ćwiczenia: wykres (ciężar, e1RM, a przy ćwiczeniach z masą ciała powtórzenia) z zakresami i lista rekordów z datami, przy e1RM porównanie z Brzyckim.
- Postęp rutyny: zakres, wykres objętości treningu, wykres najcięższej serii i lista sesji prowadząca do szczegółów.

### D-054 Rekordy i e1RM: reguły
- e1RM liczymy wzorem Epleya (jedno powtórzenie = ciężar), a Brzyckiego tylko do porównania na liście rekordów. Liczą się serie 1–12 powtórzeń z ciężarem większym od 0, bez rozgrzewkowych.
- Rekord wymaga wcześniejszego wyniku: pierwszy trening ćwiczenia tylko ustala punkt odniesienia. Inaczej pierwszy trening z każdym ćwiczeniem byłby samymi rekordami. Tak samo dla każdego typu rekordu osobno: po samej masie ciała pierwszy trening z dodatkowym ciężarem, a po samych seriach powyżej 12 powtórzeń pierwszy trening z e1RM tylko ustalają punkt odniesienia (poprawka po przeglądzie kodu).
- Ciężar, e1RM i objętość serii: rekordem jest wynik wyższy od najlepszego z wcześniejszych treningów (równy nie). Powtórzenia: więcej niż kiedykolwiek przy tym samym lub większym ciężarze; masa ciała porównywana tylko z masą ciała. Objętość treningu: odznakę dostaje seria, która przekroczyła poprzedni najlepszy wynik.
- To samo ćwiczenie dwa razy w treningu to jedna sesja.
- Rekordy liczymy przy odczycie historii, nie zapisujemy ich. Edycja i usunięcie treningu przeliczają je bez dodatkowego kodu i bez migracji (baza zostaje w wersji 3). Data rekordu to pierwszy trening, w którym padł.
- Lista „najwięcej powtórzeń” pomija wyniki, które przebija cięższa seria z co najmniej taką samą liczbą powtórzeń.
- W trakcie treningu rekordy liczą się względem historii z chwili dodania ćwiczenia. Edycja albo usunięcie treningu w historii w trakcie treningu nie zmienia ich do końca treningu; potem historia przelicza je sama. Odświeżanie w locie odrzucone: przypadek rzadki, a dokładałoby obserwację historii do aktywnego treningu.

### D-055 Tygodniowe serie na partię
- Tydzień zaczyna się w poniedziałek w strefie urządzenia. Liczą się serie bez rozgrzewkowych. Seria liczy się raz dla każdej partii, do której należą mięśnie główne ćwiczenia; pomocnicze się nie liczą. Suma „Serie robocze w tygodniu” liczy każdą serię raz, także gdy seria liczy się dla kilku partii.
- Odrzucone: pół serii dla mięśni pomocniczych. Trudniej to wyjaśnić w jednym zdaniu pod wykresem, a liczby w bazie ćwiczeń są do tego zbyt zgrubne.

### D-056 Komponenty: wykres, segmenty, pasek, odznaki rekordów
- `GlassLineChart` na Canvas:
  - linia monotoniczna (Fritsch–Carlson), która nie wychodzi ponad ani poniżej punktów, więc nie pokazuje wartości, których nie było;
  - oś czasu proporcjonalna, okrągłe podziałki (1, 2, 2,5 lub 5 × 10ⁿ);
  - gradient od linii w dół i glow jako rozmyta kopia linii (`BlurEffect`);
  - animacja odsłaniania, wyłączona przy ograniczeniu animacji;
  - tap i przeciąganie wybierają najbliższy punkt z tyknięciem haptycznym; wybrany punkt zostaje w nagłówku do zmiany zakresu;
  - kropki do 24 punktów; czytnik ekranu dostaje liczbę punktów oraz pierwszą i ostatnią wartość.
- `GlassSegmentedControl` (wskaźnik `LiquidSpan` jak w navbarze, `Role.Tab`) i `GlassBar` (pasek serii na partię). Wszystkie trzy są w galerii.
- Odrzucone: biblioteka wykresów (Vico, MPAndroidChart). Własny komponent to ok. 300 linii, daje pełną kontrolę nad estetyką glass i nie dodaje zależności.
- Odznaki rekordów: osobna na każdy typ, zawijane. Jedna długa odznaka ucinała się przy trzech rekordach w wierszu serii.

### D-057 Wydajność i dane testowe
- Agregaty liczy SQLite (zapytania w `WorkoutDao`): najlepsze wyniki każdej sesji, pierwsze wystąpienie każdej pary ciężar × powtórzenia i tygodniowe serie. e1RM w SQL korzysta z tych samych stałych co domena, a test JVM porównuje wyniki SQL z domeną na losowych danych.
- Generator w debug (`DebugData`, galeria z `--ei seedWorkouts 300`): kopiuje szablon PPL i dodaje treningi co dwa dni wstecz z rosnącymi ciężarami. Tylko dodaje wiersze; dane usuwa wyczyszczenie danych aplikacji. Jest też w buildzie `benchmark` (źródła debug).
- Pomiar na emulatorze (build `benchmark` z R8, 300 treningów, `dumpsys gfxinfo`):
  - przewijanie historii: p50 20–25 ms, p90 28–48 ms, jank 1–10%;
  - przeciąganie po wykresie ze 100 punktami: p50 26 ms, p90 32 ms, jank 7,5%;
  - punkt odniesienia z Etapu 1 (galeria z blurem na tym samym emulatorze): p50 28 ms, p90 42 ms, jank 28%.
  Nowe ekrany nie są wolniejsze od reszty aplikacji na emulatorze. Miarodajny pomiar będzie na telefonie.
- Bez nowych zależności. Release: 4,04 MB (było 3,88 MB; +0,16 MB).

### D-058 Wydanie testowe po każdym etapie, galeria bez ikony
- Na polecenie właściciela (2026-10-19) każdy etap dostaje wydanie testowe `preview-stage-<nr>` na zasadach z D-051 (pre-release, APK debug zbudowany lokalnie). Etap 6 dostał je po merge'u, z `main`. Kolejne etapy dostaną je razem z PR, żeby dało się je sprawdzić na telefonie przed akceptacją.
- Galeria komponentów nie ma już ikony w launcherze, bo właściciel nie chce jej na telefonie. Zostaje jako narzędzie deweloperskie, bo CLAUDE.md wymaga galerii dla każdego komponentu. Otwiera ją `adb shell am start -n …` (README) i macrobenchmark, który startuje ją po nazwie.

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
| datastore-preferences | 1.2.1 | Apache-2.0 | 5 | flagi aplikacji, w Etapie 9 ustawienia (D-048) |
| backdrop / shapes | 2.0.1 / 1.2.1 | Apache-2.0 | 1 | szkło (D-006) |
| room3 / sqlite-framework | 3.0.3 / 2.7.1 | Apache-2.0 | 2 | baza (D-004) |
| sqlite-bundled-jvm | 2.7.1 | Apache-2.0 | 2 | tylko testy: Room na JVM (D-028) |
| hilt-lifecycle-viewmodel-compose / lifecycle-viewmodel-navigation3, lifecycle-runtime-compose | 1.4.0 / 2.11.0 | Apache-2.0 | 2 | ViewModele per wpis nawigacji, `collectAsStateWithLifecycle` |
| Coil 3 + OkHttp | 3.6.3 / 5.5.0 | Apache-2.0 | 2 | zdjęcia ćwiczeń (D-012) |
| reorderable (Calvin-LL) | 3.1.0 | Apache-2.0 | 3 | drag & drop w edytorze rutyn (D-037), ok. 17 KB |
| work-runtime-ktx / hilt-work | 2.12.0 / 1.4.0 | Apache-2.0 | 7 | synchronizacja HC w tle |
| health connect-client | 1.1.0 | Apache-2.0 | 7 | Health Connect (D-011) |
| profileinstaller / baselineprofile / benchmark-macro | 1.4.1 / 1.5.0 / 1.5.0 | Apache-2.0 | 1 (pomiar), 10 | Baseline Profile, pomiary |
| JUnit / coroutines-test / androidx.test runner, ext-junit / compose ui-test-junit4, ui-test-manifest | 4.13.2 / 1.11.0 / 1.7.0, 1.3.0 / BOM | EPL-1.0 / Apache-2.0 | 1–4 | testy (D-042) |
| benchmark-macro-junit4 / uiautomator | 1.5.0 / 2.4.0 | Apache-2.0 | 1 | macrobenchmark w `:baselineprofile` (nie trafia do APK) |
| Fonty Geist, Instrument Serif | 1.8.1 / — | OFL-1.1 | 1 | typografia (D-007), ok. 650 KB TTF |
| free-exercise-db | — | Unlicense | 2 | baza ćwiczeń (atrybucja w „O aplikacji”) |

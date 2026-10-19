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
- **Decyzja:** galeria to osobna aktywność w source secie `debug` z własną ikoną „GT Galeria”. To narzędzie deweloperskie, więc nie łamie zasady single-activity aplikacji. Motyw można ustawić przez extras intencji (skrypty zrzutów).
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
| sqlite-bundled-jvm | 2.7.1 | Apache-2.0 | 2 | tylko testy: Room na JVM (D-028) |
| hilt-lifecycle-viewmodel-compose / lifecycle-viewmodel-navigation3, lifecycle-runtime-compose | 1.4.0 / 2.11.0 | Apache-2.0 | 2 | ViewModele per wpis nawigacji, `collectAsStateWithLifecycle` |
| Coil 3 + OkHttp | 3.6.3 / 5.5.0 | Apache-2.0 | 2 | zdjęcia ćwiczeń (D-012) |
| reorderable (Calvin-LL), kandydat | 3.1.0 | Apache-2.0 | 3 | drag & drop; decyzja w Etapie 3 (alternatywa: własna implementacja) |
| work-runtime-ktx / hilt-work | 2.12.0 / 1.4.0 | Apache-2.0 | 7 | synchronizacja HC w tle |
| health connect-client | 1.1.0 | Apache-2.0 | 7 | Health Connect (D-011) |
| profileinstaller / baselineprofile / benchmark-macro | 1.4.1 / 1.5.0 / 1.5.0 | Apache-2.0 | 1 (pomiar), 10 | Baseline Profile, pomiary |
| JUnit / coroutines-test / androidx.test runner, ext-junit / room3-testing | 4.13.2 / 1.11.0 / 1.7.0, 1.3.0 / 3.0.3 | EPL-1.0 / Apache-2.0 | 1–2 | testy |
| benchmark-macro-junit4 / uiautomator | 1.5.0 / 2.4.0 | Apache-2.0 | 1 | macrobenchmark w `:baselineprofile` (nie trafia do APK) |
| Fonty Geist, Instrument Serif | 1.8.1 / — | OFL-1.1 | 1 | typografia (D-007), ok. 650 KB TTF |
| free-exercise-db | — | Unlicense | 2 | baza ćwiczeń (atrybucja w „O aplikacji”) |

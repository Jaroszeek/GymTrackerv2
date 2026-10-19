# CLAUDE.md — zasady pracy w projekcie GymTracker v2

## Projekt w skrócie
GymTracker v2 to natywna aplikacja Android (Kotlin, Jetpack Compose) do śledzenia treningów siłowych, z integracją Health Connect i designem „liquid glass”. Właściciel: Jaroszeek. Repo: `Jaroszeek/GymTrackerv2`.

Pliki projektu:
- `PROMPT.md` — specyfikacja (część A), plan etapów (część B), prompty pomocnicze (część C). Źródło prawdy dla tego, CO budujemy.
- `CLAUDE.md` (ten plik) — JAK pracujesz.
- `docs/PROGRESS.md` — status etapów i bieżący stan prac.
- `docs/DECISIONS.md` — decyzje (data, decyzja, powód, alternatywy).
- `docs/IDEAS.md` — pomysły poza zakresem.

Gdy polecenie w sesji jest sprzeczne z tym plikiem albo z PROMPT.md, wskaż konflikt i zapytaj, zanim cokolwiek zmienisz.

## Komunikacja
- Odpowiadaj po polsku.
- Kod, identyfikatory, komentarze w kodzie, commity i nazwy branchy piszesz po angielsku.
- Teksty UI są po polsku, w `strings.xml`.
- Pisz zwięźle i konkretnie. Nie powtarzaj tego, co już wiem.
- Nigdy nie twierdź, że coś działa, jeśli tego nie sprawdziłeś. Rozróżniaj:
  - „kompiluje się”
  - „testy przechodzą”
  - „sprawdzone na emulatorze”
  - „sprawdzone na telefonie”
- Pytaj tylko wtedy, gdy decyzja jest moja (zakres, UX, kompromis, koszt). Gdy istnieje rozsądny domyślny wybór, wybierz go, zapisz w `docs/DECISIONS.md` i poinformuj mnie jednym zdaniem.
- Pytanie zadawaj z konkretnymi opcjami i swoją rekomendacją.
- Jeśli po 2–3 podejściach coś nadal nie działa, zatrzymaj się. Opisz, co próbowałeś, jakie masz hipotezy i jakie widzisz opcje.

## Sposób pracy
1. Pracujemy etapami z `PROMPT.md` (część B), po kolei. Nie zaczynaj kolejnego etapu bez mojej akceptacji poprzedniego.
2. Na starcie etapu:
   - przeczytaj `docs/PROGRESS.md` i wskazane sekcje specyfikacji;
   - w trybie planowania przedstaw plan etapu: ekrany, klasy i pliki, zmiany schematu bazy, nowe zależności, ryzyka, sposób testowania;
   - poczekaj na akceptację.
3. W trakcie etapu pracuj małymi krokami. Rób build po każdej większej zmianie i commit po każdym spójnym kawałku.
4. Na koniec etapu:
   - przejdź checklistę „Definicja ukończenia”;
   - zaktualizuj `docs/PROGRESS.md` i `docs/DECISIONS.md`;
   - otwórz PR i daj raport w formacie opisanym niżej.
5. Nie rozszerzaj zakresu na własną rękę i nie dodawaj funkcji „przy okazji”. Pomysły zapisuj w `docs/IDEAS.md`.
6. Nie refaktoryzuj kodu niezwiązanego z bieżącym zadaniem bez pytania.
7. Przed użyciem biblioteki albo API sprawdź aktualną dokumentację i najnowszą stabilną wersję (oficjalne docs, Maven Central, Context7 MCP, jeśli jest dostępny). Nie polegaj na pamięci.
8. Gdy kontekst robi się długi, zapisz dokładny stan prac w `docs/PROGRESS.md`, zacommituj i zaproponuj `/clear`.

## Środowisko
- Windows i PowerShell. Gradle uruchamiasz przez `.\gradlew.bat`, JDK to JBR z Android Studio.
- Urządzenie testowe: Samsung z One UI 8.5 (Android 16). Emulatory: API 35 i API 36.
- Najczęstsze komendy:
  - build: `.\gradlew.bat assembleDebug`
  - testy: `.\gradlew.bat testDebugUnitTest`
  - testy UI (emulator): `.\gradlew.bat :app:connectedDebugAndroidTest`
  - lint: `.\gradlew.bat lintDebug`
  - instalacja: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
  - logi: `adb logcat -s <TAG>`
  - zrzut ekranu: `adb shell screencap -p /sdcard/shot.png; adb pull /sdcard/shot.png docs\screenshots\<nazwa>.png`
  - galeria (debug): `adb shell am start -n com.jaroszeek.gymtracker.debug/com.jaroszeek.gymtracker.designsystem.gallery.GalleryActivity --es palette <volt|onyx|...> --es mode <light|dark> --ez reduceTransparency true`
  - macrobenchmark: `.\gradlew.bat :baselineprofile:connectedBenchmarkAndroidTest`
- Nie używaj `adb exec-out screencap -p > plik.png`, bo przekierowanie w PowerShellu psuje pliki binarne.
- Nie wpisuj do repo ścieżek specyficznych dla mojej maszyny (np. `org.gradle.java.home`). Jeśli systemowe JDK nie pasuje do Gradle, użyj toolchaina albo `%USERPROFILE%\.gradle\gradle.properties`.
- Nie instaluj komponentów SDK ani narzędzi systemowych bez poinformowania mnie, co i po co.

## Ustalenia, których nie zmieniasz bez pytania
- minSdk 35, targetSdk 36, compileSdk = najnowszy stabilny wspierany przez stabilny AGP (minimum 36).
- applicationId `com.jaroszeek.gymtracker`, wariant debug z sufiksem `.debug`.
- Kotlin i Jetpack Compose, single-activity, edge-to-edge, predictive back.
- Biblioteki: Hilt (KSP), Room (KSP), DataStore, WorkManager, Coil 3, kotlinx.serialization, Health Connect `connect-client`.
- Wszystkie wersje są w `gradle/libs.versions.toml`. Nie używasz wersji alpha/beta bez mojej zgody.
- Każda nowa zależność wymaga wpisu w `docs/DECISIONS.md`: po co, rozważone alternatywy, licencja, wpływ na rozmiar.
- Dane są tylko lokalne. Dane zdrowotne nigdy nie opuszczają telefonu.
- Żadnej analityki, crash-reportingu ani SDK wysyłających dane bez mojej zgody.

## Architektura i kod
- UDF/MVVM:
  - ViewModel wystawia jeden `StateFlow<XxxUiState>`, a zdarzenia przyjmuje przez metody.
  - Composable nie zawierają logiki biznesowej.
  - Ekrany dzielisz na bezstanowe komponenty z `@Preview`.
- Pakiety:
  - `designsystem/` — tokeny, motywy, komponenty glass, galeria komponentów (tylko debug)
  - `core/` — database, datastore, health, notifications, update, util
  - `domain/` — czysta logika w Kotlinie (1RM, rekordy, streak, BMI, gotowość, progresja), bez zależności od Androida, w pełni pokryta testami
  - `data/` — repozytoria i mapowanie
  - `feature/<nazwa>/` — UI i ViewModel danej funkcji
- Encje Room nie trafiają do UI; mapujesz je na modele domenowe.
- Room:
  - `exportSchema = true`, schematy w `app/schemas` są commitowane;
  - każda zmiana schematu oznacza migrację i test migracji;
  - nigdy nie używasz `fallbackToDestructiveMigration`.
- Korutyny:
  - dispatchery są wstrzykiwane, nie używasz `GlobalScope`;
  - odczyty zwracasz jako `Flow`;
  - ciężką pracę robisz poza wątkiem głównym.
- Błędy: nie połykasz wyjątków, logujesz z tagiem modułu. UI ma stany ładowania, pusty i błędu.
- Czas: używasz `java.time`. Zapisujesz w UTC (epoch millis albo Instant), wyświetlasz w strefie urządzenia.
- Masę zapisujesz w kg; konwersja na lb jest tylko w UI.
- Wszystkie teksty są w `strings.xml`. Liczby mnogie robisz przez `<plurals>` z polskimi formami (one, few, many, other).
- Stosujesz oficjalny Kotlin coding style. Jeśli w projekcie jest spotless albo ktlint, uruchamiasz go przed commitem.

## Design system
- Kolory, typografia, promienie, elewacje, blur, odstępy i animacje bierzesz wyłącznie z tokenów w `designsystem/`. W kodzie funkcji nie ma `Color(0x…)` ani „magicznych” wartości dp.
- Każdy nowy komponent trafia do galerii komponentów z preview.
- Szkło (prawdziwy backdrop blur) stosujesz tylko dla warstw pływających. Karty z treścią są bardziej matowe.
- Kontrast tekstu ma wynosić co najmniej 4.5:1. Sprawdzasz go w każdym motywie (ciemny, jasny, AMOLED, Onyx, Porcelain) i w trybie „Ogranicz przezroczystość”.
- Szanujesz systemowe ograniczenie animacji i skalowanie czcionki.
- Po każdej istotnej zmianie wizualnej robisz zrzut ekranu i pokazujesz mi go.

## Znane pułapki
- Powierzchnie glass nie są M3 `Surface`. Ustaw globalnie `LocalContentColor`, inaczej tekst bez jawnego koloru będzie prawie czarny na ciemnym tle.
- Kolor tekstu na akcencie licz z luminancji akcentu. Jasne akcenty i motywy mono wymagają ciemnego tekstu.
- W zmiennych fontach oś wagi potrafi się nie zastosować na urządzeniu (cyfry wychodzą jako cienkie kontury). Używaj statycznych plików wag albo jawnie `FontVariation` i sprawdź to na telefonie.
- Pływający navbar musi być wystarczająco nieprzezroczysty, a treść potrzebuje dolnego paddingu, żeby nie chowała się pod paskiem.
- Foreground service startowany z tła na Androidzie 12+ kończy się crashem:
  - startuj go tylko z kontekstu foreground;
  - wywołuj `startForeground` od razu;
  - jeśli się nie da, zatrzymaj serwis zamiast połykać błąd.
- Samsung agresywnie ubija procesy. Gdy trening albo timer działa, informuj o ustawieniu baterii „Bez ograniczeń”.
- Na One UI sama poprawna implementacja Live Update nie wystarcza, bo dochodzą przełączniki systemowe. Zawsze sprawdzaj stan przez ekran diagnostyki (PROMPT.md, A7).

## Health Connect
- Aplikacja w pełni działa bez HC i bez zgód. Przed odczytem zawsze sprawdzaj `getSdkStatus` i uprawnienia.
- Wywołania HC nigdy nie blokują UI.
- Batchuj zapytania, paginuj i używaj changes tokens. Sumy liczysz przez Aggregate API.
- Zapisujesz `dataOrigin` i deduplikujesz dane.
- O uprawnienia prosisz w kontekście funkcji, z wyjaśnieniem. Brak zgody to normalny stan, nie błąd.

## Git i GitHub
- Każdy etap ma własny branch `feat/stage-<nr>-<nazwa>`. Etap 0 commitujesz bezpośrednio na `main`.
- Commity piszesz po angielsku w formacie Conventional Commits, małe i częste. Pushujesz regularnie.
- Na koniec etapu:
  - otwierasz PR przez `gh pr create` z opisem, checklistą DoD i zrzutami ekranu;
  - CI musi być zielone;
  - merge robisz dopiero po mojej akceptacji: `gh pr merge --merge --delete-branch`.
- Nigdy nie robisz:
  - force pusha na `main`;
  - przepisywania opublikowanej historii;
  - commitu sekretów (`*.jks`, `*.keystore`, `keystore.properties`, `local.properties`, tokeny).
- Tagi i GitHub Releases tworzysz tylko na moje polecenie.
- Repo `vynox` jest tylko do odczytu i sklonowane poza projektem. Nie dodajesz go do GymTrackerv2 i bierzesz z niego wyłącznie navbar.
- Nie korzystasz z kodu pierwszej wersji GymTrackera; wszystko piszemy od nowa.

## Operacje wymagające mojej zgody
- Usuwanie plików poza katalogami build, `git reset --hard`, usuwanie branchy, `git push --force`.
- Zmiana czegokolwiek z sekcji „Ustalenia, których nie zmieniasz bez pytania”.
- Zmiana schematu bazy wymagająca migracji danych użytkownika (po wydaniu v1.0.0).
- Nowe uprawnienia w manifeście.
- Zmiana zasad w tym pliku: proponujesz, ale nie wprowadzasz sam. Dopisywanie faktów, np. wybranych bibliotek czy komend, jest OK.

## Testy
- `domain/`: testy jednostkowe są obowiązkowe, razem z przypadkami brzegowymi.
- Room: testy DAO (in-memory) i testy migracji.
- Krytyczne przepływy (trening, przerwa, zakończenie, import kopii) mają testy UI Compose.
- Przed końcem etapu uruchamiasz pełny zestaw testów i lint, a do raportu dołączasz manualną checklistę testów na telefonie.

## Definicja ukończenia (zadanie lub etap)
- [ ] `.\gradlew.bat assembleDebug` zielony, `testDebugUnitTest` i `lintDebug` bez nowych błędów
- [ ] Kryteria akceptacji etapu z PROMPT.md spełnione
- [ ] Brak hardcoded tekstów i kolorów poza `designsystem/`
- [ ] Działa bez HC, bez zgód i bez internetu (jeśli dotyczy)
- [ ] Zrzuty nowych ekranów w `docs/screenshots/`
- [ ] `docs/PROGRESS.md` i `docs/DECISIONS.md` zaktualizowane
- [ ] PR otwarty, CI zielone

## Raport na koniec zadania lub etapu
1. Co zrobione (krótko)
2. Jak przetestować: komendy PowerShell i kroki na telefonie
3. Wyniki testów i lintu
4. Znane problemy i ograniczenia
5. Decyzje podjęte po drodze
6. Następny krok i pytania do mnie
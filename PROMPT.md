# PROMPT.md — GymTracker v2: specyfikacja, plan etapów, prompty

## Jak z tego korzystać
1. Przygotuj projekt. W Android Studio utwórz Empty Activity o nazwie `GymTrackerv2`, z pakietem `com.jaroszeek.gymtracker`, Minimum SDK API 35 i Kotlin DSL. Możesz też zostawić pusty katalog, a Claude Code wygeneruje projekt sam.
2. Skopiuj `CLAUDE.md` i `PROMPT.md` do katalogu głównego projektu.
3. W terminalu w tym katalogu uruchom `gh auth status`, a potem `claude`.
4. Wklej prompt Etapu 0.
5. Po każdym etapie przejrzyj PR, przetestuj na telefonie i zaakceptuj. Potem zrób `/clear` i wklej prompt kolejnego etapu. Stan projektu jest w repo i w `docs/PROGRESS.md`, więc czysty kontekst niczego nie gubi.
6. Część C zawiera prompty pomocnicze: wznowienie pracy, błędy, design review, wydanie.

---

# Część A — Specyfikacja

## A1. Cel
GymTracker v2 to druga wersja mojej aplikacji do śledzenia treningów siłowych.
- Wszystkie funkcje pierwszej wersji zostają (bez dopisku). Nowe funkcje są oznaczone NOWE.
- Kod, architektura i design są całkowicie nowe.
- Aplikacja działa lokalnie i integruje się z Health Connect. U mnie dane pochodzą z Samsung Health i Galaxy Watch.
- Dystrybucja: APK przez GitHub Releases, z aktualizatorem w aplikacji.

Zasady pracy, konwencje kodu i ustalenia techniczne opisuje `CLAUDE.md`.

## A2. Stack
- minSdk 35, targetSdk 36, compileSdk = najnowszy stabilny (minimum 36).
- applicationId `com.jaroszeek.gymtracker`, wariant debug `.debug`.
- Kotlin i Jetpack Compose. Material 3 jako baza, mocno przestylowana.
- Single-activity, edge-to-edge, predictive back.
- Hilt (KSP).
- Nawigacja: Navigation 3, jeśli jest stabilne i pasuje do navbara; w przeciwnym razie Navigation Compose z type-safe routes.
- Room (KSP), DataStore, WorkManager, Coil 3, kotlinx.serialization, Health Connect `connect-client`.
- UI po polsku (`strings.xml`, gotowe na EN). Jednostki: kg, z przełącznikiem kg/lb.

## A3. Navbar z repo vynox
Repo https://github.com/Jaroszeek/vynox (prywatne) to mój poprzedni projekt. Bierzemy z niego wyłącznie pasek nawigacji.
- Sklonuj repo przez `gh repo clone Jaroszeek/vynox` do katalogu tymczasowego poza projektem.
- Zachowaj kształt, układ, animacje (wskaźnik, przejścia, reakcję na dotyk) i charakter paska.
- Przestyluj go w systemie glass z A4, z prawdziwym rozmyciem tła.
- Jeśli vynox nie jest w Compose, odtwórz pasek wiernie w Compose.
- Zakładki: Start, Trening (rutyny), Ćwiczenia, Postępy, Profil. Ustawienia i Historia są dostępne z poziomu ekranów. Liczbę zakładek dopasuj do możliwości navbara.
- Gdy trening trwa, nad navbarem pływa mini-pasek treningu (A5.3).

## A4. Design — drogi, nowoczesny „liquid glass”
Aplikacja ma wyglądać jak produkt premium, a nie jak szablon Material. Dark-first, z wariantem jasnym.

- **Tło:** głęboka, prawie czarna baza z bardzo subtelnym, powoli dryfującym gradientem (mesh/aurora) w 2–3 tonach powiązanych z akcentem. Ruch ma być ledwo zauważalny i wyłączany, gdy system ogranicza animacje.
- **Szkło:** prawdziwe rozmycie i refrakcja tła (backdrop), a nie `Modifier.blur` na treści.
  - Oceń bibliotekę `io.github.kyant0:backdrop` (Liquid Glass). Wersja 2.x jest Compose Multiplatform, więc sprawdź zgodność z AndroidX Compose BOM.
  - W razie konfliktu użyj wersji 1.0.x, biblioteki Haze albo własnych shaderów AGSL (minSdk 35 daje RenderEffect i RuntimeShader).
  - Decyzję zapisz w `docs/DECISIONS.md`.
- **Hierarchia:** szkło dla warstw pływających: navbar, top bar po przewinięciu, bottom sheety, dialogi, nakładka przerwy, mini-pasek treningu. Karty z treścią są bardziej matowe, żeby były czytelne.
- **Wykończenie:**
  - specular hairline 1 px (gradient jaśniejszy u góry);
  - delikatny wewnętrzny połysk u góry paneli;
  - miękkie, szerokie cienie;
  - duże promienie zaokrągleń (squircle/continuous, jeśli dostępne).
- **Kolor:** jeden sygnaturowy akcent na ekran.
  - Kuratorowane palety: 6 akcentów oraz 2 motywy monochromatyczne (ciemny „Onyx”, jasny „Porcelain”).
  - Tryb AMOLED (czysta czerń).
  - Material You (dynamic color) jako opcja, nie domyślnie.
- **Typografia:**
  - Grotesk do UI (np. Inter Tight, Geist albo Manrope — wybierz i uzasadnij).
  - Opcjonalnie elegancki serif do dużych momentów display (np. Instrument Serif).
  - Wszystkie liczby (ciężary, czasy, powtórzenia, streak) piszesz cyframi tabularnymi i traktujesz jako główny element ekranu.
  - Fonty na licencji OFL, zbundlowane, w statycznych wagach.
- **Ruch i haptyka:**
  - animacje sprężynowe;
  - shared element transitions: karta rutyny → trening, ćwiczenie → szczegóły;
  - płynny wskaźnik navbara;
  - haptyka przy odhaczeniu serii, końcu przerwy i nowym rekordzie.
  - Rekord to subtelny glow z haptyką, bez konfetti.
- **Ikony:** jeden spójny zestaw (np. Material Symbols Rounded). Bez emoji w UI.
- **Dostępność:** kontrast tekstu co najmniej 4.5:1, tryb „Ogranicz przezroczystość”, obsługa dużych czcionek.
- **Wydajność:** płynny scroll (60/120 fps) mimo rozmycia, sprawdzony pomiarem. Baseline Profile.
- **Unikaj:** domyślnego wyglądu M3, szablonowego fioletu, niestylizowanych TopAppBar i Card.

## A5. Funkcje

### A5.1 Start (dashboard)
- Nagłówek: powitanie z datą oraz ikony edycji układu i ustawień, bez klasycznego app bara.
- Widżety, które w trybie edycji można przestawiać (drag & drop) i ukrywać. Układ zapisany w DataStore.
  - hero ze streakiem tygodniowym (ile tygodni z rzędu z co najmniej jednym treningiem) i statystykami tygodnia
  - ostatni trening
  - kalendarz aktywności (dni treningowe wyróżnione, dzisiaj zaznaczony); NOWE (2026-10-25): także dni z sesjami z zegarka (HC), oznaczone inaczej niż treningi z aplikacji
  - kroki dziś z wykresem
  - mini-wykres wagi i BMI
  - NOWE: karta gotowości (sen, tętno spoczynkowe, HRV)
  - NOWE: rekordy z ostatnich 7 dni
- Szybki start treningu z proponowaną lub ostatnio używaną rutyną.

### A5.2 Rutyny i szablony
- Tworzenie, edycja, duplikowanie i usuwanie nazwanych rutyn.
- Zmiana kolejności ćwiczeń (drag & drop), dodawanie i usuwanie ćwiczeń.
- Bloki: pojedyncze ćwiczenie albo blok z alternatywnymi wariantami (np. wyciskanie sztangą lub hantlami). Wariant wybiera się w trakcie treningu.
- Cele dla każdego ćwiczenia: liczba serii, powtórzenia (liczba lub zakres), ciężar, czas przerwy.
- NOWE: biblioteka gotowych szablonów (Push/Pull/Legs, Upper/Lower, FBW) do skopiowania i edycji.
- NOWE: opcjonalna podpowiedź progresji (double progression). Gdy wszystkie serie osiągnęły górę zakresu powtórzeń, aplikacja proponuje większy ciężar następnym razem.
- NOWE (2026-10-25): niezapisany szkic edytora rutyny przetrwa ubicie procesu w tle.

### A5.3 Aktywny trening
- Start z rutyny albo pusty trening. Licznik czasu sesji.
- Każda seria ma:
  - ciężar i powtórzenia;
  - typ: rozgrzewkowa, robocza, drop set, do upadku;
  - możliwość odhaczenia jako ukończona.
- Dodawanie serii (kopiuje wartości poprzedniej) i usuwanie serii.
- Przy każdej serii podpowiedź z poprzedniej sesji tego ćwiczenia (ciężar × powtórzenia).
- Wybór wariantu w blokach z alternatywami.
- Zakończenie treningu:
  - podsumowanie: czas, objętość, liczba serii, nowe rekordy;
  - dialog z listą zmian względem celów rutyny, per ćwiczenie: „Zastosuj jako nowe cele” albo „Zostaw stare”.
- Odrzucenie treningu z potwierdzeniem.
- Trwałość: stan zapisywany na bieżąco (z debounce) i odtwarzany po zabiciu procesu albo restarcie. Trening porzucony ponad 24 h nie jest wskrzeszany.
- Gdy trening trwa, nad navbarem pływa mini-pasek (nazwa, czas, postęp serii), który prowadzi z powrotem do treningu.
- Foreground service, powiadomienie i Live Update opisuje A7.

### A5.4 Timer przerwy
- Odhaczenie serii automatycznie uruchamia przerwę. Domyślnie 90 s; czas można zmienić globalnie i per ćwiczenie w rutynie. Odznaczenie serii anuluje przerwę.
- Pełnoekranowa nakładka glass:
  - pierścień postępu i duże mm:ss;
  - „Następne: ćwiczenie • seria X”;
  - przyciski −15 s, +30 s, Pomiń oraz zamknięcie;
  - automatyczne zamknięcie przy zerze.
- Stan przerwy opiera się na absolutnym czasie końca.
- NOWE — w tle:
  - odliczanie w powiadomieniu (Live Update) i na ekranie blokady;
  - na koniec wibracja i opcjonalny dźwięk, także przy wygaszonym ekranie.
  - Niezawodność zapewnia `AlarmManager.setExactAndAllowWhileIdle`, gdy `canScheduleExactAlarms()` zwraca true (z wyjaśnieniem i deep-linkiem do zgody).
  - Fallback: krótki partial wakelock w serwisie na czas przerwy.

### A5.5 Ćwiczenia
- Baza ok. 870 ćwiczeń ze zdjęciami z open-source free-exercise-db (github.com/yuhonas/free-exercise-db):
  - JSON zbundlowany w assets i zaseedowany do Room przy pierwszym starcie;
  - zdjęcia z CDN, z cache dyskowym Coil;
  - licencja sprawdzona, atrybucja w „O aplikacji”.
- Dodatkowy zestaw ćwiczeń używanych w programach Jeffa Nipparda (warianty, których brakuje w bazie).
- Nazwy ćwiczeń zostają po angielsku. Popularne ćwiczenia dostają polskie aliasy do wyszukiwania.
- Wyszukiwarka i filtry (chipsy): partia ciała, sprzęt, NOWE (2026-10-25): kategoria (np. siłowe, rozciąganie, cardio). Badge sprzętu na liście i oznaczenie ćwiczeń własnych.
- Własne ćwiczenia: dodawanie, edycja, usuwanie (z ochroną, jeśli ćwiczenie jest w historii).
- Szczegóły ćwiczenia:
  - zdjęcia, instrukcje (NOWE 2026-10-25: po polsku), partie mięśni;
  - wykres maksymalnego ciężaru i e1RM w czasie;
  - rekordy;
  - przycisk „Wideo”, który otwiera wyszukiwanie na YouTube.

### A5.6 Historia
- Lista treningów: data, czas trwania, liczba ćwiczeń i serii, objętość.
- Szczegóły sesji ze wszystkimi seriami. Edycja i usuwanie zakończonego treningu.
- NOWE (2026-10-25): edycja zmienia też datę i godzinę startu; tap w ćwiczenie w szczegółach otwiera szczegóły ćwiczenia; niezapisany szkic edycji przetrwa ubicie procesu.
- Sesje zaimportowane z Health Connect (np. spacer czy bieg z zegarka) są widoczne w historii z polską nazwą typu i oznaczeniem źródła.
- Łączenie zapisanego treningu z sesją z zegarka (ExerciseSession w HC):
  - automatyczne połączenie, gdy czasy się nakładają (zmiana 2026-10-24: wcześniej propozycja do potwierdzenia, D-084);
  - ręczne łączenie i rozłączanie;
  - połączony trening pokazuje tętno i kalorie z zegarka;
  - NOWE (2026-10-24): wykres tętna w każdym ćwiczeniu, ze znacznikami serii (D-084).

### A5.7 Postępy: rekordy, 1RM, wykresy
- Wykresy dla rutyny: objętość i maksymalny ciężar w czasie.
- Wykresy dla ćwiczenia: maksymalny ciężar i e1RM.
- NOWE: szacowany 1RM. Wzór Epleya, Brzycki do porównania. Liczony tylko dla serii do 12 powtórzeń, bez serii rozgrzewkowych.
- NOWE: automatyczne rekordy, wykrywane w trakcie treningu i listowane per ćwiczenie:
  - najcięższy ciężar
  - najlepszy e1RM
  - najwięcej powtórzeń przy danym ciężarze
  - największa objętość serii i sesji
- NOWE: tygodniowa liczba serii na partię mięśniową.
- Własny komponent wykresów na Canvas w estetyce glass:
  - gradient pod linią i glow;
  - przeciąganie palcem ze wskaźnikiem wartości i haptyką;
  - zakresy 1M / 3M / 6M / 1R / Wszystko.

### A5.8 Profil i ciało
- Waga, wzrost i wiek; BMI liczone automatycznie.
- Skład ciała: % tkanki tłuszczowej, masa tłuszczu, masa beztłuszczowa (NOWE 2026-10-25: bez % tłuszczu z masy beztłuszczowej z HC). Masa mięśni szkieletowych to wpis ręczny, bo Health Connect nie ma tego typu danych.
- Wykresy wagi, BMI i % tłuszczu. Ręczne dodawanie pomiaru; NOWE (2026-10-25): z wybraną datą i godziną, a niezapisany pomiar przetrwa ubicie procesu.
- Dane z HC scalane z wpisami ręcznymi, ze źródłem i bez duplikatów.
- Kalorie aktywne i całkowite z HC.

### A5.9 Ustawienia
- Motyw: System / Jasny / Ciemny, paleta i akcent, AMOLED, Material You, „Ogranicz przezroczystość”.
- Jednostki, domyślny czas przerwy, dźwięk i wibracja końca przerwy.
- Health Connect: połącz / rozłącz, auto-sync, „Synchronizuj teraz”, data ostatniej synchronizacji, stan uprawnień.
- Dane: eksport i import (A5.10).
- Aktualizacje: „Sprawdź teraz” i automatyczne sprawdzanie.
- „Diagnostyka powiadomień na żywo” (A7).
- O aplikacji: wersja z BuildConfig w formacie MAJOR.MINOR.PATCH, licencje i atrybucje.

### A5.10 Kopia zapasowa
- Eksport całej bazy do jednego pliku przez Storage Access Framework, jako spójny snapshot (checkpoint WAL przed kopiowaniem).
- Import z walidacją:
  - nagłówek SQLite, wersja schematu, integralność;
  - próbna migracja (dry-run);
  - dialog potwierdzenia z podsumowaniem kopii: liczba rutyn, treningów i pomiarów, zakres dat.
- Podmiana pliku bazy przy następnym starcie procesu. Poprzednia baza zostaje jako kopia z timestampem.
- Android Auto Backup z regułami (bez plików tymczasowych).
- NOWE: import kopii zapasowej z pierwszej wersji GymTrackera (repo `Jaroszeek/GymTracker`), żeby przenieść dane ze starej aplikacji na nową:
  - ten sam przycisk importu; aplikacja sama rozpoznaje kopię v1 i konwertuje jej dane do bazy v2;
  - walidacja i dialog z podsumowaniem jak przy zwykłym imporcie, plus lista tego, czego nie dało się przenieść;
  - ćwiczenia z kopii v1 są dopasowane do bazy ćwiczeń v2, a niedopasowane trafiają do własnych ćwiczeń;
  - import zastępuje dane v2 tak jak zwykły import (poprzednia baza zostaje jako kopia z timestampem);
  - z v1 bierzemy wyłącznie znajomość formatu pliku kopii. Kodu, UI, architektury ani funkcji v1 nie przenosimy.

### A5.11 Aktualizacje w aplikacji
- Sprawdzanie przy starcie (maksymalnie raz na 24 h) i ręcznie, przez GitHub API `releases/latest` dla `Jaroszeek/GymTrackerv2`, z porównaniem versionCode.
- Dialog glass z notatkami wydania.
- Pobranie APK do cache.
- Weryfikacja, że certyfikat podpisu pobranego APK jest taki sam jak zainstalowanej aplikacji.
- Instalacja przez PackageInstaller (`REQUEST_INSTALL_PACKAGES`), z obsługą zgody „Instaluj nieznane aplikacje”.

## A6. Health Connect
Wszystkie dane zostają lokalnie.

Odczyt:
- **Trening:** HeartRate, ActiveCaloriesBurned, TotalCaloriesBurned, ExerciseSession. Dla każdego treningu: średnie i maksymalne tętno, wykres tętna, czas w strefach (tętno maksymalne z wieku albo wpisane ręcznie).
- **Ciało:** Weight, Height, BodyFat, LeanBodyMass; BoneMass i BodyWaterMass, jeśli są dostępne.
- **Aktywność:** Steps, Distance.
- **Sen i regeneracja:** SleepSession, RestingHeartRate, HeartRateVariabilityRmssd. Z nich karta gotowości, która porównuje dane z bazą z 7 dni i jest opisana jako orientacyjna, a nie medyczna.

Uprawnienia specjalne (opcjonalne, z wyjaśnieniem dla użytkownika):
- `READ_HEALTH_DATA_HISTORY` — dane starsze niż 30 dni przed pierwszą zgodą.
- `READ_HEALTH_DATA_IN_BACKGROUND` — synchronizacja w tle przez WorkManager.

Zapis (opcjonalny, domyślnie wyłączony): zakończony trening trafia do HC jako sesja siłowa. Użytkownik dostaje ostrzeżenie o duplikatach, jeśli zegarek też nagrywa sesję.

Implementacja:
- `getSdkStatus` przed każdym użyciem. Aplikacja w pełni działa bez HC.
- W manifeście: uprawnienia oraz activity-alias z `VIEW_PERMISSION_USAGE` i kategorią `HEALTH_PERMISSIONS`, prowadzący do ekranu polityki prywatności w aplikacji.
- Onboarding uprawnień per funkcja, z wyjaśnieniem. W ustawieniach ekran stanu uprawnień.
- Synchronizacja:
  - przy otwarciu aplikacji (jeśli włączona), ręcznie i okresowo w tle;
  - przyrostowo przez changes tokens (`getChanges`), z paginacją;
  - sumy przez Aggregate API;
  - deduplikacja po `dataOrigin`;
  - z poszanowaniem limitów API.
- README opisuje, jak włączyć udostępnianie danych z Samsung Health do Health Connect.

## A7. Powiadomienie treningu i Live Update (Samsung Now Bar)
W poprzednim podejściu na moim telefonie (One UI 8.5, Android 16) powiadomienie działało jako zwykłe, ale nigdy nie zostało promowane do Live Update. Nie było go w Now Bar, a aplikacji nie było w ustawieniach Now Bar. Tym razem ma działać.

Foreground service:
- Typ `specialUse` z `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
- Start tylko z kontekstu foreground (kliknięcie Start albo onResume po odtworzeniu treningu); `startForeground` od razu.
- Jeśli promocja do foreground się nie uda, serwis się zatrzymuje.
- Powiadomienie jest usuwane na każdej ścieżce zakończenia.

Treść powiadomienia:
- nazwa rutyny oraz „ćwiczenie • seria”
- chronometr sesji
- postęp serii (ProgressStyle: ukończone / wszystkie)
- w trakcie przerwy — odliczanie
- akcje „Ukończ serię” i „Pomiń przerwę”, o ile nie blokują promocji

Live Update:
- `POST_PROMOTED_NOTIFICATIONS` w manifeście i `setRequestPromotedOngoing(true)`.
- NOWE: po wyjściu z aplikacji chip na pasku stanu („fasolka”) z zegarem: czas treningu, a w przerwie odliczanie. Zegar daje chronometr powiadomienia (`setWhen` + `setUsesChronometer`, w przerwie `setChronometerCountDown`); bez `setShortCriticalText`, bo krótki tekst mógłby zastąpić zegar.
- Styl dozwolony dla promocji; powiadomienie ongoing z tytułem, bez colorize i bez custom RemoteViews.
- Pełną listę wymagań sprawdź w aktualnej dokumentacji Androida „Live Updates”.

Ekran „Diagnostyka powiadomień na żywo” pokazuje (i loguje):
- `areNotificationsEnabled`
- `NotificationManager.canPostPromotedNotifications()`
- `hasPromotableCharacteristics()` zbudowanego powiadomienia
- czy opublikowane powiadomienie ma `FLAG_PROMOTED_ONGOING`
- stan optymalizacji baterii

Na tym ekranie są deep-linki: ustawienia powiadomień aplikacji, `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS`, ustawienia baterii.

One UI: README opisuje przełączniki Samsunga potrzebne do działania:
- powiadomienia na żywo per aplikacja
- ustawienia Now Bar
- opcja deweloperska „Live notifications for all apps”
- bateria „Bez ograniczeń”

POST_NOTIFICATIONS: prośba przy pierwszym starcie (z wyjaśnieniem) i baner na ekranie treningu, jeśli zgody brak.

## A8. Wydania
- Repo jest publiczne, żeby aktualizator mógł pobierać Releases bez tokenu.
- `ci.yml`: na push i PR uruchamia lint, testy i assembleDebug.
- `release.yml`: na tag `v*` buduje podpisany assembleRelease (R8) i tworzy GitHub Release z APK oraz changelogiem.
- versionCode i versionName wynikają z tagu.
- Keystore i hasła są wyłącznie w sekretach GitHub (`gh secret set`) i w lokalnym `keystore.properties` (poza repo).
- Keystore generuję sam i trzymam jego kopię zapasową. Bez niego nie da się zaktualizować zainstalowanej aplikacji. (2026-10-25: na moją prośbę keystore wygenerował Claude; kopię zapasową trzymam ja, D-086.)

## A9. Definicja ukończenia projektu
- Wszystkie funkcje z części A działają.
- `.\gradlew.bat lint testDebugUnitTest assembleRelease` przechodzi lokalnie i w CI.
- Brak crashy bez Health Connect, bez zgód i bez internetu.
- Zrzuty kluczowych ekranów są w `docs/screenshots/` i w README.
- README po polsku: opis, funkcje, build, instalacja APK, konfiguracja Samsung Health → Health Connect, konfiguracja Now Bar, proces wydawania wersji.

---

# Część B — Plan etapów

| Etap | Nazwa | Sekcje | Wynik |
|---|---|---|---|
| 0 | Start i plan | całość | analiza vynox, plan, repo na GitHubie |
| 1 | Fundament i design system | A2, A3, A4 | szkielet, galeria komponentów, navbar, CI |
| 2 | Ćwiczenia | A5.5 | baza ćwiczeń, lista, szczegóły |
| 3 | Rutyny i szablony | A5.2 | edytor rutyn, szablony |
| 4 | Aktywny trening | A5.3, A5.4 (UI) | trening od startu do zakończenia |
| 5 | Powiadomienie, Live Update, przerwa w tle | A7, A5.4 (tło) | foreground service, Now Bar |
| 6 | Historia i postępy | A5.6, A5.7 | historia, rekordy, 1RM, wykresy |
| 7 | Health Connect | A6, A5.6 (łączenie) | synchronizacja, tętno, gotowość |
| 8 | Start i Profil | A5.1, A5.8 | dashboard, profil |
| 9 | Ustawienia i kopia zapasowa | A5.9, A5.10 | ustawienia, eksport i import |
| 10 | Szlif, wydajność, QA | A4, A9 | gotowość do wydania |
| 11 | Wydanie i aktualizator | A8, A5.11 | v1.0.0 na GitHub Releases |
| 12 | Szlif 1.1 (pomysły z `IDEAS.md`) | A4, A5.1, A5.2, A5.5, A5.6, A5.8 | wersja 1.1.0 |
| 13 | Wersja 1.2: polskie instrukcje, poprawki, zależności | A5.1, A5.5, C8 | wersja 1.2.0 |

## Etap 0 — Start i plan
**Kryteria akceptacji**
- [ ] Środowisko sprawdzone, braki zgłoszone
- [ ] Navbar z vynox przeanalizowany i opisany
- [ ] Plan zaakceptowany: architektura, schemat bazy, mapa ekranów, kierunek designu, zależności, ryzyka
- [ ] Repo `GymTrackerv2` utworzone (publiczne), pierwszy commit wypchnięty
- [ ] `docs/PROGRESS.md`, `docs/DECISIONS.md` i `docs/IDEAS.md` utworzone

**Prompt**
```
Zaczynamy projekt GymTracker v2. Przeczytaj w całości CLAUDE.md i PROMPT.md — to Twoje zasady i specyfikacja.

Etap 0 — Start i plan. Pracuj w trybie planowania; nie pisz jeszcze kodu aplikacji.

1. Środowisko: sprawdź `gh auth status`, konfigurację git (user.name, user.email), JDK z Android Studio, Android SDK, adb i dostępne emulatory. Zgłoś braki.
2. Stan katalogu: jeśli jest tu projekt z kreatora Android Studio, oceń, co zostawić. Jeśli katalog jest pusty, zaplanuj wygenerowanie kompletnego projektu z Gradle Wrapper.
3. vynox: sklonuj `gh repo clone Jaroszeek/vynox` do katalogu tymczasowego poza projektem. Znajdź implementację navbara i opisz: technologię, pliki, strukturę, animacje, zależności oraz co i jak przeniesiesz (A3).
4. Sprawdź aktualne stabilne wersje: AGP, Kotlin, Compose BOM, Hilt, Room, Navigation (czy Navigation 3 jest stabilne), Health Connect connect-client, Coil, WorkManager. Sprawdź też bibliotekę backdrop (`io.github.kyant0:backdrop`) pod kątem zgodności z AndroidX Compose oraz jej alternatywy.
5. Przedstaw plan:
   - architektura i struktura pakietów;
   - schemat bazy Room (encje, relacje, indeksy) dla całej specyfikacji;
   - mapa ekranów i nawigacji (zakładki navbara);
   - kierunek designu: fonty, palety, podejście do szkła;
   - lista zależności z wersjami i licencjami;
   - ryzyka (Live Update na One UI 8.5, blur a wydajność, Health Connect na Samsungu) i jak je zmniejszysz;
   - ewentualne korekty planu etapów z części B.
6. Po mojej akceptacji:
   - git init (jeśli trzeba) i `.gitignore`;
   - `docs/PROGRESS.md`, `docs/DECISIONS.md` (z decyzjami z planu) i `docs/IDEAS.md`;
   - pierwszy commit na main;
   - `gh repo create GymTrackerv2 --public --source=. --remote=origin --push`;
   - raport zgodnie z CLAUDE.md.
```

## Etap 1 — Fundament i design system
**Kryteria akceptacji**
- [ ] Projekt buduje się lokalnie i w CI
- [ ] Galeria komponentów zaakceptowana przeze mnie (zrzuty w każdym motywie)
- [ ] Navbar wierny vynox, w stylu glass, z działającą nawigacją
- [ ] Kontrast ≥ 4.5:1 we wszystkich motywach, brak hardcoded kolorów

**Prompt**
```
Realizujemy Etap 1 — Fundament i design system. Sekcje A2, A3 i A4 w PROMPT.md. Branch: feat/stage-1-foundation.

Najpierw plan etapu w trybie planowania (pliki, zależności, tokeny), potem realizacja po mojej akceptacji.

Zakres:
1. Projekt Gradle zgodny z A2:
   - version catalog i Hilt;
   - minSdk, targetSdk i compileSdk;
   - applicationId z wariantem `.debug`;
   - BuildConfig z wersją;
   - buildy debug i release (release jeszcze bez podpisywania).
2. Design system w `designsystem/`:
   - tokeny: palety (6 akcentów, Onyx, Porcelain, AMOLED, jasny i ciemny), typografia z cyframi tabularnymi, promienie, elewacje, blur, spacing, motion;
   - tło z subtelnym gradientem;
   - komponenty glass: karta, panel pływający, top bar, bottom sheet, dialog, przycisk, chip, badge, pole liczbowe, przełącznik;
   - tryb „Ogranicz przezroczystość”.
3. Galeria komponentów (tylko debug) pokazująca wszystkie komponenty we wszystkich motywach.
4. Navbar przeniesiony z vynox (A3), w stylu glass:
   - zakładki Start, Trening, Ćwiczenia, Postępy, Profil;
   - puste ekrany-zaślepki w nowym stylu;
   - nawigacja, predictive back, edge-to-edge.
5. Tymczasowa ikona i tymczasowy splash.
6. `.github/workflows/ci.yml`: lint, testy i assembleDebug na push i PR.
7. Szkic README po polsku.

Przed zamknięciem etapu zrób zrzuty galerii (ciemny, jasny, Onyx, Porcelain, „Ogranicz przezroczystość”) i navbara na emulatorze API 36. Pokaż mi je do akceptacji. Spodziewaj się kilku rund poprawek wyglądu.
```

## Etap 2 — Ćwiczenia
**Kryteria akceptacji**
- [ ] Baza zaseedowana raz (drugi start nie dubluje danych)
- [ ] Wyszukiwanie (także po polskich aliasach) i filtry działają płynnie na pełnej liście
- [ ] Szczegóły działają bez internetu (placeholdery zamiast zdjęć)
- [ ] Własne ćwiczenia: dodawanie, edycja i usuwanie z ochroną

**Prompt**
```
Realizujemy Etap 2 — Ćwiczenia. Sekcja A5.5. Branch: feat/stage-2-exercises.

Najpierw plan etapu (schemat, seed, ekrany), potem realizacja.

Zakres:
1. Room:
   - encje ćwiczeń: nazwa, polskie aliasy, partie mięśni główne i pomocnicze, sprzęt, kategoria, instrukcje, zdjęcia, flaga własne/wbudowane;
   - DAO;
   - eksport schematu od pierwszej wersji.
2. Seed:
   - free-exercise-db (JSON w assets) przy pierwszym starcie;
   - sprawdź licencję i zapisz atrybucję;
   - dodatkowy zestaw ćwiczeń z programów Jeffa Nipparda (warianty, których brakuje w bazie). Pokaż mi listę, zanim ją dodasz.
3. Lista:
   - wyszukiwarka, także po polskich aliasach;
   - filtry chipsami (partia ciała, sprzęt);
   - badge sprzętu i oznaczenie ćwiczeń własnych;
   - płynny scroll po pełnej liście.
4. Szczegóły:
   - zdjęcia (Coil z cache dyskowym, placeholder bez internetu), instrukcje, partie mięśni;
   - przycisk „Wideo” otwierający wyszukiwanie na YouTube;
   - miejsce na wykres i rekordy (wypełnimy w Etapie 6).
5. Własne ćwiczenia: dodawanie, edycja, usuwanie. Gdy ćwiczenie jest używane, blokada albo ostrzeżenie.
6. Testy: DAO, wyszukiwanie i filtrowanie, idempotentny seed.
```

## Etap 3 — Rutyny i szablony
**Kryteria akceptacji**
- [ ] Rutyny z blokami i wariantami zapisują się i edytują poprawnie
- [ ] Drag & drop kolejności działa płynnie
- [ ] Szablony zaakceptowane przeze mnie i kopiowalne do własnych rutyn
- [ ] Logika progresji pokryta testami

**Prompt**
```
Realizujemy Etap 3 — Rutyny i szablony. Sekcja A5.2. Branch: feat/stage-3-routines.

Najpierw plan etapu, potem realizacja.

Zakres:
1. Model: rutyna → bloki (pojedyncze ćwiczenie albo blok z alternatywnymi wariantami) → cele per wariant (serie, powtórzenia lub zakres, ciężar, czas przerwy). Migracja i test migracji.
2. Lista rutyn w zakładce Trening:
   - karty glass;
   - start treningu (na razie zaślepka);
   - menu: edytuj, duplikuj, usuń z potwierdzeniem.
3. Edytor rutyny:
   - nazwa;
   - dodawanie ćwiczeń z pickera (wyszukiwarka i filtry z Etapu 2);
   - dodawanie wariantów do bloku;
   - drag & drop kolejności;
   - edycja celów i usuwanie.
4. Biblioteka gotowych szablonów (Push/Pull/Legs, Upper/Lower, FBW). Pokaż mi proponowaną zawartość przed implementacją. Szablon można skopiować do własnych rutyn.
5. Logika podpowiedzi progresji (double progression) w `domain/` z testami. Podpięcie do UI nastąpi w Etapie 4.
6. Shared element transition: karta rutyny → edytor.
```

## Etap 4 — Aktywny trening
**Kryteria akceptacji**
- [ ] Pełny przebieg działa: start → serie → przerwa → zakończenie → zapis
- [ ] Trening przeżywa `adb shell am kill` i restart aplikacji
- [ ] Dialog zmian celów aktualizuje rutynę zgodnie z wyborem
- [ ] Test UI pełnego przebiegu przechodzi

**Prompt**
```
Realizujemy Etap 4 — Aktywny trening. Sekcje A5.3 i A5.4 (część UI). Powiadomienia, foreground service i przerwa w tle to Etap 5, tu ich nie robimy. Branch: feat/stage-4-workout.

Najpierw plan etapu, potem realizacja.

Zakres:
1. Model sesji i serii (typ serii, ciężar, powtórzenia, ukończona, kolejność, wybrany wariant). Migracja i test migracji.
2. Menedżer aktywnego treningu:
   - singleton w DI, jedno źródło prawdy dla UI, a w Etapie 5 także dla serwisu;
   - start z rutyny albo pusty trening;
   - edycja serii, dodawanie (kopiuje poprzednią) i usuwanie serii;
   - wybór wariantu;
   - odrzucenie treningu z potwierdzeniem.
3. Trwałość:
   - zapis stanu na bieżąco, z debounce;
   - odtworzenie po zabiciu procesu;
   - trening starszy niż 24 h nie jest wskrzeszany.
   - Przetestuj przez `adb shell am kill com.jaroszeek.gymtracker.debug` w trakcie treningu.
4. Ekran treningu:
   - licznik czasu i karty ćwiczeń;
   - wiersze serii z dużymi polami liczbowymi do szybkiej edycji jedną ręką;
   - podpowiedź z poprzedniej sesji i podpowiedź progresji;
   - haptyka przy odhaczeniu serii.
5. Przerwa (UI):
   - odhaczenie serii startuje przerwę (czas domyślny albo z celu ćwiczenia), odznaczenie ją anuluje;
   - nakładka glass z pierścieniem, mm:ss, „Następne: …”, przyciskami −15 s / +30 s / Pomiń i auto-zamknięciem;
   - stan oparty na absolutnym czasie końca.
6. Zakończenie: podsumowanie (czas, objętość, serie) i dialog zmian względem celów („Zastosuj jako nowe cele” / „Zostaw stare”, per ćwiczenie).
7. Mini-pasek trwającego treningu nad navbarem.
8. Testy: logika menedżera, odtwarzanie stanu, obliczenia podsumowania, test UI pełnego przebiegu.
```

## Etap 5 — Powiadomienie, Live Update i przerwa w tle
**Kryteria akceptacji**
- [ ] Na emulatorze API 36 powiadomienie jest promowane (`FLAG_PROMOTED_ONGOING`)
- [ ] Na moim telefonie trening widać w Now Bar albo przyczyna braku jest udokumentowana diagnostyką
- [ ] NOWE: po wyjściu z aplikacji chip na pasku stanu pokazuje zegar treningu, a w przerwie odliczanie
- [ ] Koniec przerwy wibruje przy wygaszonym ekranie
- [ ] Zabicie procesu i powrót nie zostawiają osieroconych powiadomień

**Prompt**
```
Realizujemy Etap 5 — Powiadomienie treningu, Live Update i przerwa w tle. Sekcje A7 i A5.4 (część w tle). Branch: feat/stage-5-live-update.

To najbardziej ryzykowny etap. W poprzednim podejściu na moim telefonie (One UI 8.5, Android 16) powiadomienie nigdy nie zostało promowane do Live Update i nie było go w Now Bar.

Zanim zaczniesz kodować, sprawdź aktualną dokumentację Androida „Live Updates”. Zbierz pełną listę wymagań promocji oraz to, co wiadomo o obsłudze Live Updates w One UI 8.5. Przedstaw to w planie etapu.

Zakres:
1. Foreground service zgodny z A7, zasilany stanem menedżera z Etapu 4.
2. Treść powiadomienia: ProgressStyle, chronometr (też w chipie na pasku stanu) i akcje zgodnie z A7.
3. Przerwa w tle:
   - odliczanie w powiadomieniu i na ekranie blokady;
   - wibracja (i opcjonalny dźwięk) na końcu, przy wygaszonym ekranie;
   - exact alarm z prośbą o zgodę, z fallbackiem na krótki wakelock.
4. POST_NOTIFICATIONS: prośba przy pierwszym starcie z wyjaśnieniem i baner na ekranie treningu.
5. Ekran „Diagnostyka powiadomień na żywo” ze wszystkimi stanami i deep-linkami z A7. Na razie dostępny z ekranu treningu; w Etapie 9 trafi do ustawień.
6. README: sekcja o konfiguracji One UI.
7. Testy:
   - najpierw emulator API 36;
   - potem przygotuj mi instrukcję testu na telefonie: co uruchomić, co zebrać z `adb logcat`, co sprawdzić w diagnostyce.

Etap zamykamy dopiero wtedy, gdy na moim telefonie trening widać w Now Bar albo mamy udokumentowaną przyczynę, dlaczego się nie da.
```

## Etap 6 — Historia i postępy
**Kryteria akceptacji**
- [ ] Historia z edycją i usuwaniem; rekordy przeliczają się po zmianach
- [ ] e1RM i rekordy pokryte testami z przypadkami brzegowymi
- [ ] Wykresy płynne przy kilkuset sesjach (dane testowe w debug)

**Prompt**
```
Realizujemy Etap 6 — Historia i postępy. Sekcje A5.6 (bez łączenia z HC, to Etap 7) i A5.7. Branch: feat/stage-6-history-progress.

Najpierw plan etapu, potem realizacja.

Zakres:
1. Historia:
   - lista sesji (data, czas, ćwiczenia i serie, objętość);
   - szczegóły ze wszystkimi seriami;
   - edycja i usuwanie zakończonego treningu, z przeliczeniem rekordów.
2. `domain/`, z pełnymi testami i przypadkami brzegowymi:
   - e1RM (Epley i Brzycki; tylko serie do 12 powtórzeń, bez rozgrzewkowych);
   - rekordy: najcięższy ciężar, najlepszy e1RM, najwięcej powtórzeń przy danym ciężarze, największa objętość serii i sesji;
   - tygodniowe serie na partię mięśniową.
3. Wykrywanie rekordów w trakcie treningu (glow i haptyka) oraz na podsumowaniu.
4. Komponent wykresu na Canvas (A5.7): gradient, glow, scrubbing z haptyką, zakresy 1M/3M/6M/1R/Wszystko. Dodaj go do galerii komponentów.
5. Zakładka Postępy:
   - wykresy dla rutyn (objętość, maksymalny ciężar);
   - wykresy dla ćwiczeń (maksymalny ciężar, e1RM);
   - serie na partię mięśniową;
   - uzupełnij wykres i rekordy w szczegółach ćwiczenia.
6. Wydajność:
   - zapytania agregujące w Room zamiast liczenia w pamięci;
   - generator danych testowych w debug;
   - płynność przy kilkuset sesjach.
```

## Etap 7 — Health Connect
**Kryteria akceptacji**
- [ ] Aplikacja działa normalnie bez HC i bez zgód
- [ ] Synchronizacja przy otwarciu, ręczna i w tle działa przyrostowo, bez duplikatów
- [ ] Trening połączony z sesją z zegarka pokazuje tętno, strefy i kalorie
- [ ] Przetestowane na moim telefonie z prawdziwymi danymi z Samsung Health

**Prompt**
```
Realizujemy Etap 7 — Health Connect. Sekcja A6 oraz łączenie sesji z A5.6. Branch: feat/stage-7-health-connect.

W planie etapu przedstaw:
- aktualną wersję connect-client;
- uprawnienia z A6;
- mapowanie rekordów HC na modele aplikacji;
- strategię synchronizacji (changes tokens, paginacja, agregacje, deduplikacja);
- co Samsung Health faktycznie udostępnia w HC, a co może nie przychodzić (np. HRV).

Zakres:
1. Manifest: uprawnienia, activity-alias z polityką prywatności (ekran w aplikacji), obsługa `getSdkStatus`.
2. Onboarding uprawnień per funkcja, z wyjaśnieniem, oraz ekran stanu uprawnień. Opcjonalne `READ_HEALTH_DATA_HISTORY` i `READ_HEALTH_DATA_IN_BACKGROUND`.
3. Synchronizacja:
   - przy otwarciu aplikacji (jeśli włączona), ręczna i okresowa w tle (WorkManager);
   - zapis daty ostatniej synchronizacji;
   - odporność na brak zgód i błędy limitów.
4. Dane do zaimportowania:
   - pomiary ciała, kroki i dystans, kalorie;
   - sesje ćwiczeń do historii jako zaimportowane, z polskimi nazwami typów i źródłem;
   - tętno, sen, tętno spoczynkowe, HRV.
5. Łączenie treningu z sesją z zegarka:
   - auto-propozycja, gdy czasy się nakładają;
   - ręczne łączenie i rozłączanie;
   - w szczegółach treningu: średnie i maksymalne tętno, wykres tętna, strefy, kalorie.
6. `domain/`: gotowość (sen, RHR i HRV względem bazy z 7 dni) z testami. W UI opisana jako orientacyjna.
7. Opcjonalny zapis treningów do HC, domyślnie wyłączony, z ostrzeżeniem o duplikatach.
8. README: konfiguracja Samsung Health → Health Connect.
9. Checklista testu na moim telefonie z prawdziwymi danymi.
```

## Etap 8 — Start i Profil
**Kryteria akceptacji**
- [ ] Wszystkie widżety z A5.1 działają; tryb edycji zapisuje układ
- [ ] Profil scala dane ręczne i z HC bez duplikatów
- [ ] Stany puste i stan bez HC wyglądają premium

**Prompt**
```
Realizujemy Etap 8 — Start (dashboard) i Profil. Sekcje A5.1 i A5.8. Branch: feat/stage-8-home-profile.

Najpierw plan etapu, potem realizacja.

Zakres:
1. Start:
   - nagłówek (powitanie, data, edycja układu, ustawienia);
   - wszystkie widżety z A5.1;
   - szybki start treningu.
2. Tryb edycji: przestawianie (drag & drop) i ukrywanie widżetów, zapis w DataStore. Zaproponuj sensowny układ domyślny.
3. `domain/`: streak tygodniowy i BMI, z testami.
4. Profil:
   - dane osobowe: waga, wzrost, wiek. Jeśli do stref tętna lub obliczeń potrzebne są inne dane, zapytaj mnie;
   - BMI, skład ciała, masa mięśni szkieletowych jako wpis ręczny;
   - wykresy wagi, BMI i % tłuszczu;
   - ręczne dodawanie pomiaru;
   - scalanie z HC ze źródłem i bez duplikatów;
   - kalorie z HC.
5. Stany puste i stan bez HC zaprojektowane tak, żeby nadal wyglądały premium.
```

## Etap 9 — Ustawienia i kopia zapasowa
**Kryteria akceptacji**
- [ ] Wszystkie ustawienia z A5.9 działają i przeżywają restart
- [ ] Po eksporcie i imporcie dane są identyczne
- [ ] Uszkodzony plik i plik z nowszym schematem są bezpiecznie odrzucane
- [ ] NOWE: kopia z GymTracker v1 importuje się do v2, a podsumowanie pokazuje, co przeniesiono i co pominięto

**Prompt**
```
Realizujemy Etap 9 — Ustawienia i kopia zapasowa. Sekcje A5.9 i A5.10. Branch: feat/stage-9-settings-backup.

Najpierw plan etapu, potem realizacja.

Zakres:
1. Ustawienia zgodne z A5.9. Diagnostyka powiadomień trafia tutaj. Sekcja aktualizacji jest na razie zaślepką do Etapu 11.
2. Eksport bazy do jednego pliku przez SAF, jako spójny snapshot.
3. Import zgodny z A5.10: walidacja, dry-run migracji, dialog z podsumowaniem, podmiana przy starcie procesu, kopia poprzedniej bazy z timestampem, czytelna obsługa błędów.
4. NOWE: import kopii z GymTracker v1 zgodnie z A5.10. Najpierw przeanalizuj wyłącznie format pliku kopii v1 (kod tworzenia kopii i schemat zapisanych danych w repo `Jaroszeek/GymTracker`) i pokaż mi w planie etapu mapowanie v1 → v2. Poza formatem kopii niczego z v1 nie bierzesz.
5. Android Auto Backup z regułami.
6. Testy:
   - eksport → import → identyczne dane;
   - odrzucenie uszkodzonego pliku i pliku z nowszą wersją schematu;
   - import syntetycznej kopii w formacie v1 → dane zgodne z mapowaniem (moja prawdziwa kopia nie trafia do repo, sprawdzamy ją tylko lokalnie);
   - test UI ścieżki importu.
```

## Etap 10 — Szlif, wydajność i QA
**Kryteria akceptacji**
- [ ] Lista poprawek designu zatwierdzona i zrealizowana
- [ ] Dostępność sprawdzona (kontrast, czcionka 200%, TalkBack)
- [ ] Baseline Profile; płynny scroll z blurem potwierdzony pomiarem
- [ ] Finalna ikona i splash zaakceptowane

**Prompt**
```
Realizujemy Etap 10 — Szlif, wydajność i QA. Sekcje A4 i A9. Branch: feat/stage-10-polish.

Najpierw plan etapu, potem realizacja.

Zakres:
1. Przegląd designu wszystkich ekranów: spójność z tokenami, hierarchia, odstępy, animacje, shared element transitions, haptyka. Zrób zrzuty wszystkich ekranów w ciemnym i jasnym motywie i zaproponuj listę poprawek. Zatwierdzę, co robimy.
2. Dostępność: kontrast w każdym motywie, czcionka 200%, TalkBack na kluczowych ekranach, minimalne obszary dotyku.
3. Wydajność: zmierz płynność scrolla z blurem i czas zimnego startu, dodaj Baseline Profile.
4. Ikona adaptacyjna z wersją monochromatyczną (themed icon) i finalny splash. Pokaż mi 2–3 propozycje ikony.
5. Odporność na scenariusze:
   - brak HC, brak zgód, brak internetu;
   - pusta baza i duża baza;
   - zabicie procesu w trakcie treningu i w trakcie przerwy;
   - obrót ekranu, tryb oszczędzania energii.
6. Przegląd lintu i ostrzeżeń, usunięcie martwego kodu, aktualizacja README.
```

## Etap 11 — Wydanie i aktualizator
**Kryteria akceptacji**
- [ ] Tag v1.0.0 buduje podpisany APK w CI i tworzy GitHub Release
- [ ] Aktualizacja v1.0.0 → v1.0.1 działa z poziomu aplikacji na moim telefonie
- [ ] README finalne zgodnie z A9

**Prompt**
```
Realizujemy Etap 11 — Wydanie i aktualizator. Sekcje A8 i A5.11. Branch: feat/stage-11-release.

Najpierw plan etapu, potem realizacja.

Zakres:
1. Podpisywanie release:
   - podaj mi komendę `keytool` do wygenerowania keystore (wygeneruję go sam);
   - konfiguracja podpisu czytana lokalnie z `keystore.properties`, a w CI z sekretów;
   - przypomnij mi o kopii zapasowej keystore.
2. `release.yml` zgodnie z A8 oraz instrukcja `gh secret set` dla wszystkich sekretów.
3. Aktualizator zgodnie z A5.11: sprawdzanie, dialog, pobranie, weryfikacja certyfikatu, instalacja przez PackageInstaller, obsługa zgody na instalację.
4. Testy logiki porównania wersji i parsowania odpowiedzi API.
5. Finalne README zgodnie z A9.
6. Po mojej akceptacji:
   - tag v1.0.0 i pierwszy Release;
   - potem testowy v1.0.1, żeby sprawdzić aktualizację z poziomu aplikacji na moim telefonie.
```

## Etap 12 — Szlif 1.1
Dodany 2026-10-25 na prośbę właściciela: pomysły z `docs/IDEAS.md` wybrane po Etapie 11. Bez migracji bazy i bez nowych uprawnień.

**Kryteria akceptacji**
- [ ] Bottom sheet zamyka się przeciągnięciem w dół
- [ ] Tap w ćwiczenie w szczegółach treningu otwiera szczegóły ćwiczenia
- [ ] Edycja treningu zmienia datę i godzinę startu
- [ ] Pomiar ciała można dodać z wybraną datą i godziną
- [ ] Filtr kategorii na liście ćwiczeń
- [ ] Szkice edytora rutyny, edycji treningu i pomiaru wracają po ubiciu procesu
- [ ] Kalendarz na Starcie pokazuje dni z sesjami z zegarka
- [ ] Masa beztłuszczowa z HC, gdy nie ma % tkanki tłuszczowej
- [ ] Wersja 1.1.0 wydana i zaktualizowana z aplikacji z 1.0.x

**Prompt**
```
Realizujemy Etap 12 — Szlif 1.1. Pomysły z docs/IDEAS.md wybrane po Etapie 11. Branch: feat/stage-12-polish.

Zakres:
1. Bottom sheet: zamykanie przeciągnięciem w dół.
2. Szczegóły treningu: tap w ćwiczenie otwiera szczegóły ćwiczenia.
3. Edycja treningu: zmiana daty i godziny startu.
4. Pomiar ciała z wybraną datą i godziną.
5. Filtr kategorii na liście ćwiczeń.
6. Szkice edytorów (rutyna, edycja treningu, pomiar) w SavedStateHandle.
7. Kalendarz na Starcie: dni z sesjami z zegarka.
8. Masa beztłuszczowa z HC (LeanBodyMassRecord), gdy nie ma tkanki tłuszczowej.
Na koniec: changelog 1.1.0 do akceptacji, potem tag v1.1.0.
```

## Etap 13 — Wersja 1.2
Dodany 2026-10-25 na prośbę właściciela: zakres wybrany po wydaniu 1.1.0. Bez migracji bazy i bez nowych uprawnień.

**Kryteria akceptacji**
- [ ] Szczegóły wbudowanego ćwiczenia pokazują instrukcje po polsku (także na instalacji zaseedowanej wcześniej po angielsku)
- [ ] Minuty w wyborze daty i godziny z zerem wiodącym („07”)
- [ ] Kalendarz na Starcie bez kropek za spacery
- [ ] Zależności zaktualizowane według C8, osobny PR, pełne testy zielone
- [ ] Wersja 1.2.0 wydana i zaktualizowana z aplikacji z 1.1.0

**Prompt**
```
Realizujemy Etap 13 — Wersja 1.2. Branch: feat/stage-13-polish-instructions, zależności osobno w chore/deps-2026-10 (C8).

Zakres:
1. Polskie instrukcje wbudowanych ćwiczeń (tłumaczenie w assets, podmiana przy odczycie, bez migracji).
2. Minuty z zerem wiodącym w wyborze daty i godziny.
3. Kalendarz na Starcie: bez kropek za spacery wykrywane automatycznie.
4. Aktualizacja zależności według C8.
Na koniec: changelog 1.2.0 do akceptacji, potem tag v1.2.0.
```

---

# Część C — Prompty pomocnicze

## C1. Wznowienie pracy po przerwie
```
Wracamy do pracy nad GymTracker v2. Przeczytaj CLAUDE.md, docs/PROGRESS.md, ostatnie wpisy w docs/DECISIONS.md i wynik `git log --oneline -20`. Podsumuj w 5 zdaniach, gdzie jesteśmy, co jest otwarte i jaki jest następny krok. Nic nie zmieniaj, dopóki nie potwierdzę.
```

## C2. Zgłoszenie błędu
```
Błąd: [opis].
Kroki: [1, 2, 3].
Oczekiwane: [...]. Faktyczne: [...].
Urządzenie: [telefon / emulator API xx].
Logi: [wklej albo „zbierz sam przez adb logcat”].

Najpierw odtwórz błąd i znajdź przyczynę, nie objaw. Przedstaw diagnozę i propozycję poprawki. Po mojej akceptacji: najpierw test, który łapie ten błąd, potem poprawka i commit `fix: ...`.
```

## C3. Przegląd designu ekranu
```
Zrób przegląd designu ekranu [nazwa]. Zrób zrzuty w motywach: ciemny, jasny, Onyx i „Ogranicz przezroczystość”.

Oceń ekran względem A4 i tokenów: hierarchia, kontrast, odstępy, typografia liczb, użycie szkła, ruch. Daj listę maksymalnie 10 konkretnych poprawek, posortowanych według wpływu. Nic nie zmieniaj, dopóki nie wybiorę.
```

## C4. Test na telefonie
```
Przygotuj test na moim telefonie (jest podłączony przez USB). Zbuduj wersję debug i zainstaluj ją przez adb. Podaj mi checklistę kroków dla [funkcja / etap] i komendę logcat do zebrania logów. Po moim teście przeanalizujesz wyniki.
```

## C5. Live Update / Now Bar nie działa
```
Live Update albo Now Bar nie działa na moim telefonie. Uruchom trening, a potem:
- odczytaj logi serwisu;
- odczytaj stany z ekranu diagnostyki: areNotificationsEnabled, canPostPromotedNotifications, hasPromotableCharacteristics, FLAG_PROMOTED_ONGOING, bateria.

Porównaj wynik z aktualną listą wymagań z dokumentacji Live Updates i z ustawieniami One UI. Podaj diagnozę z podziałem na to, co jest po stronie kodu, a co po stronie ustawień telefonu.
```

## C6. Nowa funkcja albo zmiana zakresu
```
Chcę dodać: [opis]. Najpierw oceń:
- gdzie pasuje w architekturze;
- wpływ na schemat bazy (czy potrzebna migracja);
- wpływ na design;
- ryzyka i szacowany rozmiar.

Zaproponuj, czy to osobny etap, czy część istniejącego. Dopisz to do PROMPT.md (część A i B) dopiero po mojej akceptacji.
```

## C7. Wydanie nowej wersji
```
Wydajemy wersję [x.y.z]. Sprawdź, że main jest zielony. Przygotuj changelog od ostatniego tagu, po polsku i z perspektywy użytkownika, i pokaż mi go.

Po mojej akceptacji: tag `v[x.y.z]`, push tagu, sprawdzenie, czy release.yml przeszedł i APK jest w Release.
```

## C8. Aktualizacja zależności
```
Sprawdź dostępne stabilne aktualizacje zależności z libs.versions.toml. Dla każdej podaj: obecna → nowa wersja, ważne zmiany i breaking changes, ryzyko. Zaproponuj, co aktualizujemy.

Po mojej akceptacji: aktualizacja w osobnym branchu, pełne testy i PR.
```

## C9. Zapis stanu przed czyszczeniem kontekstu
```
Kontekst robi się długi. Zapisz w docs/PROGRESS.md dokładny stan bieżącego etapu: co zrobione, co w toku, następne kroki, otwarte pytania. Zacommituj i daj znać — zrobię /clear i wkleję prompt C1.
```
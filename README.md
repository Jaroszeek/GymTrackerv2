# GymTracker v2

Natywna aplikacja na Androida do śledzenia treningów siłowych. Działa lokalnie, integruje się z Health Connect (dane z Samsung Health i Galaxy Watch) i ma design „liquid glass”: prawdziwe rozmycie tła pod pływającymi warstwami, ciemne tło z subtelną aurorą i duże, tabularne liczby.

> Szkic. Projekt jest w budowie, etapami opisanymi w `PROMPT.md`. Stan prac: `docs/PROGRESS.md`.
>
> Gotowe: design system i nawigacja (Etap 1), baza ćwiczeń z wyszukiwarką, filtrami i własnymi ćwiczeniami (Etap 2), rutyny z wariantami, drag & drop i gotowymi szablonami (Etap 3), aktywny trening z przerwą, podsumowaniem i podpowiedzią progresji (Etap 4), powiadomienie treningu z Live Update i przerwą w tle (Etap 5), historia z edycją, rekordy, e1RM i wykresy postępów (Etap 6).

<p>
  <img src="docs/screenshots/stage1-app-trening-dark.png" width="240" alt="Zakładka Trening, motyw ciemny">
  <img src="docs/screenshots/stage1-gallery-dark-2.png" width="240" alt="Galeria komponentów, motyw ciemny">
  <img src="docs/screenshots/stage1-gallery-light-2.png" width="240" alt="Galeria komponentów, motyw jasny">
</p>

## Funkcje (plan)

- Rutyny i szablony (Push/Pull/Legs, Upper/Lower, FBW), bloki z wariantami ćwiczeń.
- Aktywny trening z timerem przerwy, powiadomieniem i Live Update (Now Bar w One UI).
- Baza 902 ćwiczeń: free-exercise-db (zdjęcia z CDN, cache na dysku) i warianty z programów Jeffa Nipparda; wyszukiwanie także po polskich nazwach, filtry partii ciała i sprzętu, własne ćwiczenia.
- Historia, rekordy, szacowany 1RM, wykresy postępów.
- Health Connect: tętno, sen, HRV, skład ciała, kroki. Dane zdrowotne nie opuszczają telefonu.
- Kopia zapasowa do pliku (także import kopii z pierwszej wersji GymTrackera) i aktualizacje z GitHub Releases.

## Stack

- Kotlin, Jetpack Compose, Material 3 (mocno przestylowany), single-activity, edge-to-edge, predictive back.
- minSdk 35, targetSdk 36, compileSdk 37.
- Navigation 3 (osobny stos na każdą zakładkę), Hilt, kotlinx.serialization.
- Szkło: [Kyant Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) i Kyant Shapes (narożniki o ciągłej krzywiźnie).
- Fonty: Geist (UI i liczby, cyfry tabularne) oraz Instrument Serif (duże nagłówki), obie na licencji OFL.
- Ikony: Material Symbols Rounded jako vector drawable.

- Room 3 (sterownik systemowego SQLite), Coil 3 z OkHttp.

- DataStore (flagi aplikacji, w Etapie 9 ustawienia).
- Health Connect (`connect-client`) i WorkManager (synchronizacja w tle).

Wersje: `gradle/libs.versions.toml`, uzasadnienia: `docs/DECISIONS.md`.

## Budowanie

Wymagania: Android Studio 2026.2 lub nowsze (JBR 25), Android SDK z platformą 37.

```powershell
.\gradlew.bat assembleDebug          # APK debug: app\build\outputs\apk\debug
.\gradlew.bat testDebugUnitTest      # testy jednostkowe
.\gradlew.bat :app:connectedDebugAndroidTest  # testy UI na emulatorze
.\gradlew.bat lintDebug              # lint
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Wariant debug ma identyfikator `com.jaroszeek.gymtracker.debug`, więc instaluje się obok wersji release.

### Galeria komponentów (tylko debug)

Galeria ze wszystkimi komponentami i przełącznikami motywu nie ma ikony w launcherze (D-058). Otwiera się ją z linii poleceń, a parametrami można od razu ustawić motyw, np. do zrzutów ekranu:

```powershell
adb shell am start -n com.jaroszeek.gymtracker.debug/com.jaroszeek.gymtracker.designsystem.gallery.GalleryActivity --es palette onyx --ez reduceTransparency true
```

Parametry: `palette` (volt, ember, rose, aqua, azure, amber, onyx, porcelain), `mode` (system, light, dark), `amoled`, `dynamic`, `reduceTransparency`.

W galerii jest też sonda Live Update: publikuje powiadomienie z prośbą o promocję i pokazuje stany, od których zależy pokazanie go w Now Bar.

### Pomiar płynności

```powershell
.\gradlew.bat :baselineprofile:connectedBenchmarkAndroidTest
```

Macrobenchmark przewija galerię pod szklanym navbarem na buildzie `benchmark` (R8), z rozmyciem i bez niego. Miarodajny jest tylko wynik z telefonu.

## Health Connect (Samsung Health, Galaxy Watch)

GymTracker czyta z Health Connect:
- sesje z zegarka z tętnem i kaloriami;
- pomiary ciała;
- kroki i dystans;
- sen, tętno spoczynkowe i HRV.

Na tej podstawie pokazuje tętno, strefy i kalorie przy treningu połączonym z sesją z zegarka, a także sesje z zegarka w historii i orientacyjną gotowość do treningu. Dane zostają na telefonie w osobnej bazie, wyłączonej z kopii systemowej. Bez Health Connect i bez zgód aplikacja działa normalnie. Opcjonalnie zapisuje zakończone treningi do Health Connect (domyślnie wyłączone).

Konfiguracja Samsung Health → Health Connect:
1. Samsung Health → Ustawienia → Health Connect: zezwól Samsung Health na zapis danych, najlepiej wszystkich. Health Connect jest też w Ustawieniach telefonu (wyszukaj „Health Connect”).
2. Dane z Galaxy Watch trafiają do Health Connect przez Samsung Health, więc pojawią się po synchronizacji zegarka z telefonem.
3. Health Connect → Dane i dostęp → kategoria (np. Aktywność) → Źródła danych i priorytet: Samsung Health na górze. Sumy kalorii i kroków Health Connect liczy tylko z aplikacji z tej listy.
4. GymTracker → Profil → Health Connect → „Połącz” przy funkcji, której chcesz używać. Bez zgody „Starsze dane” Health Connect udostępnia tylko dane z 30 dni przed pierwszym połączeniem.

Samsung Health przekazuje do Health Connect m.in.:
- sesje ćwiczeń z tętnem;
- kroki;
- sen z fazami;
- wagę i skład ciała;
- kalorie.

Tętna spoczynkowego i HRV Samsung nie wymienia na liście danych udostępnianych w Health Connect. Jeśli nie przychodzą, gotowość opiera się na śnie.

Dane testowe na emulatorze daje build `benchmark` (inny pakiet, więc jego dane wyglądają jak z zegarka, D-066):

```powershell
.\gradlew.bat assembleBenchmark
adb install -r app\build\outputs\apk\benchmark\app-benchmark.apk
"EXERCISE","HEART_RATE","ACTIVE_CALORIES_BURNED","TOTAL_CALORIES_BURNED","STEPS","DISTANCE","WEIGHT","SLEEP","RESTING_HEART_RATE","HEART_RATE_VARIABILITY" | ForEach-Object { adb shell pm grant com.jaroszeek.gymtracker.benchmark "android.permission.health.WRITE_$_" }
# sesja siłowa w podanym przedziale (epoch ms; bez parametrów ostatnie 50 min), spacer i 9 dni snu, tętna spoczynkowego, HRV, kroków i wagi
adb shell am start -n com.jaroszeek.gymtracker.benchmark/com.jaroszeek.gymtracker.HealthSeedActivity --el start <ms> --el end <ms>
adb logcat -d -s HealthSeed
```

## Powiadomienie treningu i Now Bar (One UI)

W trakcie treningu działa usługa pierwszoplanowa z cichym powiadomieniem: nazwa rutyny, „ćwiczenie • seria”, czas treningu, pasek postępu serii, a w czasie przerwy odliczanie i akcja „Pomiń przerwę”. Powiadomienie prosi system o promocję do Live Update (powiadomienie na żywo), więc na Androidzie 16 QPR1 (36.1) i nowszym po wyjściu z aplikacji widać go na pasku stanu jako chip z zegarem (czas treningu, w przerwie odliczanie), a na Samsungu w Now Bar. Koniec przerwy wibruje także przy wygaszonym ekranie.

Samsung dokłada własne przełączniki. Żeby trening był widoczny w Now Bar:

1. **Powiadomienia aplikacji** włączone (prośba przy pierwszym starcie; potem: Ustawienia → Aplikacje → GymTracker → Powiadomienia).
2. **Powiadomienia na żywo dla aplikacji**: w ustawieniach powiadomień GymTrackera przełącznik powiadomień na żywo (Live notifications / Live updates). Na Androidzie 16 QPR1 to systemowy ekran, który otwiera przycisk „Powiadomienia na żywo” w diagnostyce.
3. **Now Bar**: Ustawienia → Ekran blokady i AOD → Now Bar. GymTracker musi być na liście aplikacji, które mogą się tam pokazywać.
4. **Opcja programisty „Live notifications for all apps”**: na części wersji One UI aplikacje spoza listy Samsunga trafiają do Now Bar dopiero po jej włączeniu (Ustawienia → Opcje programisty; opcje programisty włącza się, stukając 7 razy w „Numer wersji” w Ustawienia → Telefon — informacje → Informacje o oprogramowaniu).
5. **Bateria „Bez ograniczeń”**: Ustawienia → Aplikacje → GymTracker → Bateria. Inaczej One UI potrafi zamknąć aplikację w tle i przerwać odliczanie.
6. **Alarmy i przypomnienia** (opcjonalnie): Ustawienia → Aplikacje → GymTracker → Alarmy i przypomnienia. Z tą zgodą koniec przerwy budzi telefon dokładnym alarmem; bez niej telefon nie zasypia przez całą przerwę (blokada uśpienia).

Nazwy pozycji różnią się między wersjami One UI. Stan wszystkich warunków pokazuje ekran **Diagnostyka powiadomień na żywo** (przycisk na dole ekranu treningu) i zapisuje go w logu:

```powershell
adb logcat -s LiveUpdate WorkoutService RestAlarm Workout
```

Najważniejsze pozycje: „Zgoda na powiadomienia na żywo” (`canPostPromotedNotifications`), „Powiadomienie spełnia wymagania” (`hasPromotableCharacteristics`) i „Powiadomienie jest na żywo” (`FLAG_PROMOTED_ONGOING`). Gdy dwie pierwsze są na „Tak”, a trzecia na „Nie”, promocję blokuje ustawienie systemu albo One UI.

## Struktura

```
app/src/main/java/com/jaroszeek/gymtracker/
  designsystem/theme/      tokeny: palety, typografia, odstępy, kształty, blur, ruch
  designsystem/component/  komponenty glass, navbar
  navigation/              zakładki i stosy Navigation 3
  core/                    baza Room, DataStore, DI, powiadomienie treningu i przerwa w tle
  domain/                  czysta logika (modele, wyszukiwanie)
  data/                    repozytoria, seed
  feature/                 ekrany funkcji
app/src/debug/             galeria komponentów i sonda Live Update
baselineprofile/           macrobenchmarki (w Etapie 10 także Baseline Profile)
docs/                      postęp, decyzje, pomysły, zrzuty ekranu
```

## Licencje

Kod aplikacji: do ustalenia przed wydaniem. Baza ćwiczeń: [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (Unlicense, domena publiczna). Fonty Geist i Instrument Serif: SIL Open Font License 1.1 (teksty licencji w `app/src/main/assets/licenses/`). Ikony Material Symbols: Apache-2.0.

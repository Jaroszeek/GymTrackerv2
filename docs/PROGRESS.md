# Postęp prac

## Status etapów

| Etap | Nazwa | Status |
|---|---|---|
| 0 | Start i plan | ✅ zakończony (2026-10-19) |
| 1 | Fundament i design system | ✅ zakończony (2026-10-19) |
| 2 | Ćwiczenia | ✅ zakończony (2026-10-19) |
| 3 | Rutyny i szablony | ✅ zakończony (2026-10-19) |
| 4 | Aktywny trening | ✅ zakończony (2026-10-19) |
| 5 | Powiadomienie, Live Update, przerwa w tle | ✅ zaakceptowany (2026-10-19), sprawdzony na telefonie (2026-10-23) |
| 6 | Historia i postępy | ✅ zaakceptowany (2026-10-19), wydanie `preview-stage-6`, sprawdzony na telefonie (2026-10-23) |
| 7 | Health Connect | ✅ zaakceptowany (2026-10-20), wydanie `preview-stage-7`, sprawdzony na telefonie (2026-10-23) |
| 8 | Start i Profil | ✅ zaakceptowany (2026-10-24), wydanie `preview-stage-8`, sprawdzony na telefonie (2026-10-23) |
| 9 | Ustawienia i kopia zapasowa | 🔄 w toku (branch `feat/stage-9-settings-backup`) |
| 10 | Szlif, wydajność, QA | — |
| 11 | Wydanie i aktualizator | — |

## Bieżący stan

- **Etap 9** (branch `feat/stage-9-settings-backup`).
  - Zrobione:
    - Ustawienia (A5.9): wygląd (tryb, paleta z próbek, AMOLED, Material You, ograniczenie przezroczystości), jednostka masy kg / lb we wszystkich ekranach, domyślna przerwa, dźwięk i wibracja na koniec przerwy, wejścia do Health Connect (z datą ostatniej synchronizacji) i diagnostyki, Aktualizacje jako zaślepka, O aplikacji z wersją i licencjami;
    - kopia zapasowa (A5.10): eksport przez SAF (`VACUUM INTO`), import z walidacją, próbną migracją, dialogiem z podsumowaniem, podmianą przy starcie procesu i kopią poprzedniej bazy;
    - import kopii GymTracker v1 tym samym przyciskiem, z listą tego, co nie przeszło (D-076);
    - reguły Android Auto Backup (D-077);
    - decyzje D-074…D-077, DataStore 1.3.0.
  - Poprawki po przeglądzie kodu (agent): restart po potwierdzeniu importu z ViewModelu, odporny na drugie tapnięcie i wyjście z ekranu; podmiana bazy cofa się, gdy któryś plik się nie przeniesie; kopia z triggerem lub widokiem odrzucana; pliki robocze przerwanego importu sprzątane przy starcie; progresja w lb zaokrąglana w funtach; wibracja i dźwięk na koniec przerwy przed zwolnieniem blokady uśpienia (regresja z tego etapu); ustawienia czekają na wczytanie; serie z v1 grupowane po ćwiczeniu docelowym; wiek z v1 tylko w zakresie 1–120; grupa wyboru palety dla TalkBack.
  - Poprawki z testów na emulatorze: próbka koloru bliskiego tłu karty (Porcelana na ciemnym, Onyks na jasnym) dostaje obwódkę; tekst `GlassDialog` przewija się, więc przy dużej czcionce przyciski zostają na ekranie.
  - Sprawdzone na emulatorze (36.0, aplikacja po polsku):
    - ustawienia w motywie ciemnym i jasnym, zmiana trybu i jednostki, ustawienia po wymuszonym zatrzymaniu aplikacji zostają;
    - eksport przez „Zapisz jako” do Downloads (1,1 MB);
    - import tej kopii: dialog z licznikami, restart (nowy PID), dane wracają, poprzednia baza w `previous/`, katalog roboczy czysty; także na świeżej instalacji;
    - import syntetycznej kopii v1: dialog z listą pominiętych, po restarcie rutyna „Legacy” i trening z 2023;
    - anulowanie importu usuwa plik roboczy;
    - jednostka lb na Starcie, w Profilu, w historii i szczegółach treningu;
    - dialog v1 przy czcionce 200% (przewijanie);
    - ekran licencji.
  - Testy: JVM 261/261, UI (`feature`) 3/3 na 36.0, lint 0 błędów (1 znane ostrzeżenie o targetSdk 36).
  - Test `workoutAbandonedForADayGoesToHistoryInsteadOfComingBack` raz nie przeszedł: pierwszy menedżer („zmarły proces”) żył dalej w teście i po skoku zegara zapisywał trening ponownie. Teraz test kończy jego zakres; 4 kolejne przebiegi zielone.
  - Znane ograniczenia: po imporcie kopie treningów w Health Connect (opcjonalny zapis, D-064) mogą wskazywać inne treningi, bo są kluczowane id treningu (IDEAS); liczby mnogie na systemie w innym języku niż polski (IDEAS, pytanie do właściciela).
- **Etap 8** zaakceptowany i zmergowany (PR #8, 2026-10-24). Wydanie testowe `preview-stage-8`.
  - Zrobione:
    - Start: powitanie z datą, ikony edycji układu i ustawień, karta „Szybki start” (następna rutyna w kolejności albo powrót do trwającego treningu);
    - widżety: seria tygodni z tym tygodniem, ostatni trening, gotowość, rekordy z 7 dni, kalendarz aktywności, kroki z wykresem, waga z BMI i trendem;
    - tryb edycji: przeciąganie, ukrywanie, przywrócenie domyślnego; układ w DataStore;
    - `domain/`: seria tygodni, statystyki tygodnia, proponowana rutyna, układ Startu, BMI z kategoriami WHO, deduplikacja pomiarów, podsumowanie składu ciała;
    - Profil: waga, wzrost, wiek, BMI, skład ciała (tkanka tłuszczowa, masa tłuszczu i beztłuszczowa, mięśnie szkieletowe ręcznie), wykresy wagi, BMI i tkanki tłuszczowej, ręczny pomiar, dziennik pomiarów ze źródłem i usuwaniem ręcznych, kalorie z HC;
    - pomiary ręczne w bazie (schemat v5, migracja automatyczna 4 → 5 z testem);
    - tymczasowy ekran Ustawienia (Health Connect, diagnostyka) do Etapu 9;
    - wykres słupkowy i sparkline w design systemie i w galerii;
    - decyzje D-067…D-073.
  - Sprawdzone na emulatorze (36.0, dane testowe wstawione do bazy): Start pusty i z danymi, tryb edycji (przeciąganie, ukrywanie, układ po ponownym uruchomieniu), Profil pusty i z danymi, dodanie pomiaru, Ustawienia; motyw ciemny i jasny. Bez HC: karty z „Połącz”.
  - Niesprawdzone na emulatorze: kroki i kalorie z prawdziwymi danymi HC (sprawdzi test na telefonie).
  - Testy: JVM 239/239, lint 0 błędów (1 znane ostrzeżenie o targetSdk 36).
  - Poprawki po przeglądzie kodu (testy JVM, Wstecz w edycji i wykres sprawdzone na emulatorze): średnia kalorii bez dzisiejszego, niedokończonego dnia (liczona w `domain/`); edycja układu dopiero po jego wczytaniu; gest wstecz zamyka tryb edycji; kalendarz przechodzi na nowy miesiąc; nazwy ćwiczeń w rekordach odświeżają się po zmianie; punkty wykresów Profilu liczone w ViewModelu; jedno zapytanie o zgody HC zamiast kilku.
  - Sprawdzone na telefonie (2026-10-23): właściciel potwierdził, że Etapy 5–8 działają.
  - Po odpowiedziach właściciela (2026-10-23): import kopii v1 zastępuje dane (D-052), galeria zostaje w debug (D-058); usunięte `PlaceholderScreen` (wszystkie zakładki mają już ekrany), logi `hs_err_pid*.log` i stary worktree agenta z jego branchem.
- **Etap 7** zaakceptowany i zmergowany (PR #7).
- **Etapy 1–6** zmergowane (PR #1–#6). D-038 (porzucony trening trafia do historii) zostaje jako domyślne, właściciel nie zgłosił zmiany.
- **Etap 6** zaakceptowany przez właściciela. Wydanie testowe `preview-stage-6` (D-058); od teraz każdy etap dostaje takie wydanie razem z PR. Galeria komponentów bez ikony w launcherze (D-058).
  - Zrobione: zakładka Postępy (Historia, Ćwiczenia, Rutyny, Partie), szczegóły treningu z rekordami, edycja i usuwanie z przeliczeniem rekordów, e1RM (Epley, Brzycki), rekordy i tygodniowe serie w `domain/`, rekordy w trakcie treningu (glow i haptyka) i na podsumowaniu, wykres na Canvas z zakresami i przeciąganiem, wykres i rekordy w szczegółach ćwiczenia, postęp rutyny, agregaty w Room, generator danych w debug. Decyzje D-053…D-057.
  - Sprawdzone na emulatorze (36.0): wszystkie nowe ekrany w motywie ciemnym i jasnym, wykres w Onyx i Porcelain z ograniczoną przezroczystością, edycja serii daje rekord, usunięcie treningu przelicza rekordy późniejszego, rekord w trakcie treningu i na podsumowaniu, płynność przy 300 treningach (D-057). Testy: JVM 145/145, UI 4/4, lint bez nowych ostrzeżeń.
- **Etap 5** zaakceptowany przez właściciela. Uprawnienia z D-045 zaakceptowane razem z etapem.
  - Zrobione: `WorkoutService` (foreground `specialUse`, start tylko z pierwszego planu), powiadomienie jako Live Update (ProgressStyle, chronometr, chip na pasku stanu z zegarem treningu, a w przerwie z odliczaniem, akcje „Ukończ serię” i „Pomiń przerwę”), budzenie na koniec przerwy (exact alarm albo blokada uśpienia) z wibracją, prośba o zgodę przy pierwszym starcie (DataStore), karta „Trening w tle” na ekranie treningu, ekran „Diagnostyka powiadomień na żywo”, sekcja One UI w README, decyzje D-044…D-050.
  - Sprawdzone na emulatorze (D-050): promocja `FLAG_PROMOTED_ONGOING` na 36.1, akcje z panelu, wibracja na koniec przerwy przy wygaszonym ekranie (także w Doze), odliczanie na ekranie blokady, brak osieroconych powiadomień po `kill -9`, odtworzenie po powrocie. Testy: JVM 99/99, UI 4/4 (36.0), test powiadomienia 3/3 (36.1), lint 0 błędów.
  - Wydanie testowe `preview-stage-5` (pre-release z APK debug, D-051) na prośbę właściciela.
- **Tryb pracy:** plan etapu nie czeka na akceptację (D-032), akceptacja na PR.
- **Decyzje:** `docs/DECISIONS.md` (D-001 … D-077).
- **Środowisko:**
  - Android Studio 2026.2.1 w `C:\Program Files\Android\Android Studio1` (JBR 25). Gdy `java` z PATH nie startuje, ustaw `$env:JAVA_HOME` na JBR.
  - `adb`: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
  - AVD `gt-api36` (36.0, nie promuje, D-022) i `gt-api36_1` (36.1, promuje). `gt-api36_1`: start z `-memory 3072 -gpu host -qt-hide-window` (wpis `hw.ramSize` nie działa, gość dostawał 256 MB), nawigacja trzema przyciskami, zrzuty tylko od strony hosta (`adb emu screenrecord screenshot <katalog>`), aplikacji nie wysyłać w tło — odczyt bufora GPU wywala tam `system_server` (D-050).
  - Pamięć hosta: emulator + demon Gradle + demon Kotlina potrafią wyczerpać plik stronicowania. Przy pracy z emulatorem buduj z `-Pkotlin.compiler.execution.strategy=in-process` i zatrzymuj demona (`.\gradlew.bat --stop`).
  - Na emulatorze: język aplikacji pl-PL i wyłączone pismo rysikiem.
  - Test UI: `.\gradlew.bat :app:connectedDebugAndroidTest` (D-042).
  - Gdy emulator przestaje rysować (czarny ekran, tapnięcia bez reakcji, w logu `lowmemorykiller … device is not responding` i ANR „no focused window”), pomaga zimny start: `adb emu kill`, potem `emulator -avd gt-api36 -no-snapshot-load`.
- **vynox:** sklonowany do `%TEMP%\vynox-ref`, tylko do odczytu.

## Etap 9 — test na telefonie

1. Instalacja: APK z wydania `preview-stage-9` albo `.\gradlew.bat assembleDebug`, potem `adb install -r app\build\outputs\apk\debug\app-debug.apk`. Dane z poprzedniej wersji testowej zostają.
2. Ustawienia (zębatka na Starcie albo w Profilu) → Wygląd: zmień tryb, paletę (próbki), AMOLED, Material You, „Ogranicz przezroczystość”. Zamknij aplikację (usuń z ostatnich) i otwórz: wszystko zostało, bez mignięcia domyślnego motywu.
3. Trening → Jednostka masy „lb”: Start, Postępy (historia i szczegóły treningu), Profil i aktywny trening pokazują funty; pola serii i +/− idą co 5 lb, a podpowiedź progresji to pełne funty (np. 140 lb, nie 139,99). Wróć na „kg”.
4. Domyślna przerwa, np. 2:00: ćwiczenie dodane w treningu ręcznie (bez rutyny) ma przerwę 2:00. Wibracja i dźwięk na koniec przerwy przy wygaszonym ekranie zgodnie z przełącznikami (dźwięk milknie w trybie cichym).
5. Dane → „Eksportuj kopię” → zapisz w Pobranych albo na Dysku Google. Pod przyciskami: „Kopia zapisana.”
6. „Importuj kopię” → plik z kroku 5: dialog z liczbą rutyn, treningów, pomiarów i zakresem dat → „Zastąp”. Aplikacja uruchamia się ponownie, dane bez zmian.
7. Kopia z GymTrackera v1 (najpierw krok 5, żeby móc wrócić): „Importuj kopię” → plik v1 → „Zastąpić dane kopią z v1?” z listą tego, co nie przeszło → „Zastąp”. Porównaj z v1 rutyny, historię (liczba treningów, ciężary, serie), własne ćwiczenia i pomiary w Profilu. Rok urodzenia trafia do Profilu, jeśli wiek nie był wpisany. Powrót: import kopii z kroku 5.
8. Plik, który nie jest kopią (np. zdjęcie albo PDF): „To nie jest kopia GymTrackera. Dane się nie zmieniły.”
9. W trakcie treningu „Importuj kopię” jest wyłączone z wyjaśnieniem.
10. O aplikacji: wersja 0.1.0, „Licencje i atrybucje” z tekstami licencji.
11. Do raportu: zrzut dialogu importu kopii v1 i wszystko, co po imporcie różni się od v1.

## Etap 8 — test na telefonie

1. Instalacja: APK z wydania `preview-stage-8` albo `.\gradlew.bat assembleDebug`, potem `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
2. Start bez danych: powitanie z dzisiejszą datą, „Szybki start” z „Pusty trening” i „Utwórz rutynę”, widżety z pustymi stanami. Czy wyglądają dobrze?
3. Utwórz dwie rutyny i zrób krótki trening z pierwszej. Start: „Szybki start” proponuje drugą, seria tygodni = 1, „Ostatni trening” i dzień w kalendarzu.
4. Rozpocznij trening i wróć na Start: „Szybki start” pokazuje „Wróć do treningu”.
5. Ołówek → przeciągnij widżety, ukryj dwa → „Gotowe”. Zamknij aplikację (usuń z ostatnich) i otwórz: układ został. „Przywróć domyślny” wraca do kolejności fabrycznej.
6. Profil: wpisz wagę i wzrost (tap w kafelek albo „Dodaj pomiar”), wiek. BMI z kategorią, wykres wagi. Dodaj tkankę tłuszczową i mięśnie szkieletowe: masa tłuszczu i beztłuszczowa.
7. Z Samsung Health (Profil → Health Connect → „Pomiary ciała” i „Aktywność” → Połącz): ważenie z Samsung Health pojawia się w dzienniku z nazwą źródła. Wpisz tu tę samą wagę w ciągu 30 minut od ważenia: w dzienniku zostaje jeden wpis („Wpisane ręcznie”).
8. Start: kroki z dziś i wykres 7 dni. Profil → Kalorie: aktywne i łączne z dziś oraz średnia z 7 dni. Porównaj z Samsung Health.
9. Ustawienia (zębatka na Starcie albo Profilu): wejścia do Health Connect i diagnostyki.
10. Do raportu: zrzuty Startu, trybu edycji i Profilu oraz różnice kroków i kalorii względem Samsung Health.

## Etap 7 — test na telefonie (Samsung Health)

1. Przygotowanie: Samsung Health ma zgodę na zapis do Health Connect i jest na górze listy priorytetów źródeł (README, sekcja „Health Connect”).
2. Instalacja: APK z wydania `preview-stage-7` albo `.\gradlew.bat assembleDebug`, potem `adb install -r app\build\outputs\apk\debug\app-debug.apk`. Log w osobnym oknie: `adb logcat -c; adb logcat -s HealthSync HealthConnect HealthWriter`.
3. Bez zgód: Start pokazuje kartę „Gotowość do treningu” z „Połącz”. Profil → Health Connect: każda funkcja z „Połącz”. Trening i historia działają jak dotąd.
4. „Treningi z zegarka” → Połącz → zgody. Status zmienia się na „Połączono”, a „Synchronizacja” na „Zaimportowano: …”. Postępy → Historia: sesje z zegarka jako „Zaimportowane”, z polską nazwą typu i źródłem „Samsung Health”. Tap otwiera tętno sesji.
5. Trening nagrany też na zegarku (nowy albo z ostatnich 30 dni): szczegóły treningu → „Sesja z zegarka … To ten trening?” → Połącz. Sprawdź tętno średnie i maksymalne, wykres i kalorie. Strefy pojawią się po wpisaniu wieku w Profil → Health Connect → Strefy tętna. Potem „Odłącz sesję z zegarka” → „Połącz z sesją z zegarka” → wybór z listy.
6. „Pomiary ciała”, „Aktywność”, „Regeneracja” → Połącz. Start: poziom gotowości i wartości snu, tętna spoczynkowego i HRV. Zanotuj, czego brakuje (Samsung może nie udostępniać tętna spoczynkowego i HRV).
7. „Starsze dane” (jeśli jest) → Połącz: w historii pojawiają się starsze sesje.
8. „Synchronizuj teraz” kilka razy: liczby w „Zaimportowano” stoją w miejscu (brak duplikatów). Usuń sesję w Samsung Health, zsynchronizuj zegarek i GymTracker: sesja znika z historii.
9. „W tle” → zgoda. Po godzinie, bez otwierania aplikacji, „Ostatnio …” powinno się zmienić (bateria „Bez ograniczeń” pomaga na Samsungu).
10. „Zapis treningów” → ostrzeżenie → Włącz → zgoda. Zakończ krótki trening. Health Connect → Dane i dostęp → Ćwiczenia: trening z „GymTracker Debug”.
11. „Rozłącz i usuń dane”: komunikat o rozłączeniu. Zamknij aplikację (usuń z ostatnich) i otwórz ponownie: funkcje z „Połącz”, treningi zostały. Po ponownym połączeniu tętno w połączonym treningu wraca.
12. Do raportu:
    - zrzuty ekranu Health Connect w aplikacji, połączonego treningu i gotowości;
    - lista tego, co z Samsung Health nie przyszło;
    - log z punktu 2.

## Etap 6 — test na telefonie

1. Instalacja: APK z wydania `preview-stage-6` na GitHubie albo `.\gradlew.bat assembleDebug`, potem `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
2. Dane testowe (opcjonalnie, tylko dodaje treningi): `adb shell am start -n com.jaroszeek.gymtracker.debug/com.jaroszeek.gymtracker.designsystem.gallery.GalleryActivity --ei seedWorkouts 300`; w logu `adb logcat -s DebugData` pojawi się „Added 300 workouts”.
3. Postępy → Historia: przewiń szybko całą listę. Czy przewijanie jest płynne?
4. Tap w trening → szczegóły → ołówek: zmień ciężar jednej serii na wyższy niż kiedykolwiek → Zapisz. Czy seria dostała odznakę rekordu? Usuń ten trening (kosz): czy rekordy w szczegółach ćwiczenia wróciły do poprzednich?
5. Postępy → Ćwiczenia → dowolne ćwiczenie → wykres: przełącz Ciężar / e1RM / Powtórzenia i zakresy, przeciągnij palcem po wykresie. Czy czuć tyknięcia haptyczne i czy wartość w nagłówku nadąża za palcem?
6. Postępy → Rutyny → rutyna: wykresy objętości i najcięższej serii, tap w sesję otwiera szczegóły.
7. Postępy → Partie: strzałki tygodni, liczby serii na partię.
8. Trening: odhacz serię cięższą niż dotąd. Czy wiersz rozbłyska z wibracją? Na podsumowaniu sekcja „Nowe rekordy”.
9. Zmierz płynność: `adb shell dumpsys gfxinfo com.jaroszeek.gymtracker.debug reset`, przewiń historię 10 razy, potem `adb shell dumpsys gfxinfo com.jaroszeek.gymtracker.debug | Select-String "Janky|percentile"`. Wariant debug nie ma R8, więc liczby będą gorsze niż w wydaniu.

## Etap 5 — test na telefonie (Samsung, One UI 8.5)

1. Instalacja: APK z wydania `preview-stage-5` na GitHubie albo `.\gradlew.bat assembleDebug`, potem `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
2. Log w osobnym oknie: `adb logcat -c; adb logcat -s LiveUpdate WorkoutService RestAlarm Workout`.
3. Pierwszy start: dialog „Powiadomienie treningu” → „Zezwól” → systemowe „Zezwól”.
4. Trening → rutyna (np. z szablonu) → Start. W obszarze powiadomień: nazwa rutyny, „ćwiczenie • seria 1”, pasek postępu, czas, akcja „Ukończ serię”.
5. Na dole ekranu treningu „Diagnostyka powiadomień na żywo” → zrzut ekranu. Jeśli „Powiadomienie jest na żywo” = Nie, po kolei: „Powiadomienia na żywo” (włącz), Ustawienia → Ekran blokady i AOD → Now Bar (GymTracker na liście?), „Opcje programisty” → „Live notifications for all apps”, bateria „Bez ograniczeń”. Po każdej zmianie: wróć, „Odśwież”, a jeśli nic, zakończ i zacznij trening od nowa (promocja liczy się przy publikacji powiadomienia).
6. Wyjdź z aplikacji (Home): czy na pasku stanu jest chip („fasolka”) z czasem treningu? Zablokuj telefon: czy trening jest w Now Bar i na ekranie blokady? Zrzuty.
7. Przerwa: odhacz serię, zablokuj ekran, odczekaj do końca przerwy → wibracja? Odliczanie w chipie, w Now Bar i na ekranie blokady? Powtórz z włączonymi „Alarmami i przypomnieniami” (karta „Trening w tle”).
8. Akcje z powiadomienia: „Pomiń przerwę”, „Ukończ serię”; tapnięcie w powiadomienie otwiera trening.
9. Usuń aplikację z ostatnich (przesunięcie): powiadomienie zostaje albo znika razem z procesem, ale nie zostaje „martwe”; po otwarciu aplikacji trening i powiadomienie wracają.
10. Zakończ trening: powiadomienie znika.
11. Do raportu: zrzuty z punktów 5–6, log z punktu 2 oraz `adb shell "dumpsys notification --noredact | grep -A 30 'NotificationRecord.*gymtracker'" | Out-File -Encoding utf8 notif.txt` (pole `flags=`: `PROMOTED_ONGOING` albo bit `0x40000` = promocja). Wariant debug nazywa się „GymTracker Debug” — tak też będzie na liście Now Bar.

## Następny krok

- Akceptacja Etapu 8, potem Etap 9 — Ustawienia i kopia zapasowa.
- Wyniki testów Etapów 5–8 na telefonie (wyżej), gdy właściciel je przekaże.

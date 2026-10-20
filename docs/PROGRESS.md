# Postęp prac

## Status etapów

| Etap | Nazwa | Status |
|---|---|---|
| 0 | Start i plan | ✅ zakończony (2026-10-19) |
| 1 | Fundament i design system | ✅ zakończony (2026-10-19) |
| 2 | Ćwiczenia | ✅ zakończony (2026-10-19) |
| 3 | Rutyny i szablony | ✅ zakończony (2026-10-19) |
| 4 | Aktywny trening | ✅ zakończony (2026-10-19) |
| 5 | Powiadomienie, Live Update, przerwa w tle | ✅ zaakceptowany (2026-10-19), wyniki testu na telefonie jeszcze niezgłoszone |
| 6 | Historia i postępy | ✅ zaakceptowany (2026-10-19), wydanie `preview-stage-6`, wyniki testu na telefonie jeszcze niezgłoszone |
| 7 | Health Connect | 🔄 PR otwarty (2026-10-19), wydanie `preview-stage-7`; czeka na test na telefonie i akceptację |
| 8 | Start i Profil | — |
| 9 | Ustawienia i kopia zapasowa | — |
| 10 | Szlif, wydajność, QA | — |
| 11 | Wydanie i aktualizator | — |

## Bieżący stan

- **Etap 7** (branch `feat/stage-7-health-connect`, PR otwarty, wydanie testowe `preview-stage-7`).
  - Zrobione:
    - import z HC do osobnej bazy z changes tokens: sesje, tętno, kalorie, kroki, dystans, pomiary ciała, sen, tętno spoczynkowe, HRV;
    - synchronizacja przy starcie, ręczna, po nadaniu zgód i w tle;
    - ekran Health Connect ze zgodami per funkcja, statusem, strefami tętna i rozłączeniem;
    - polityka prywatności przez `activity-alias`;
    - łączenie treningu z sesją z zegarka (propozycja, ręcznie, odłączanie) z tętnem, wykresem, strefami i kaloriami;
    - zaimportowane sesje w historii;
    - gotowość w `domain/` i na Start;
    - opcjonalny zapis treningów do HC;
    - generator danych testowych w buildzie `benchmark`;
    - decyzje D-059…D-066.
  - Sprawdzone na emulatorze (36.0, dane z generatora):
    - zgody per funkcja, także „Częściowo”;
    - import (2 sesje, 21 pomiarów, 9 nocy, 31 dni) i powtórna synchronizacja bez duplikatów;
    - propozycja połączenia, połączenie, odłączenie i wybór z listy;
    - tętno, wykres, strefy i kalorie przy treningu;
    - historia z sesjami; ekran zaimportowanej sesji;
    - gotowość „Niska” zgodna z danymi;
    - zapis treningu widoczny w HC;
    - zaplanowana praca w tle (bez wymuszonego uruchomienia: WorkManager nie odpala okresowej pracy przed czasem);
    - polityka prywatności z ekranu uprawnień HC;
    - rozłączenie;
    - motyw ciemny i jasny.
  - Testy: JVM 208/208, lint 0 błędów (1 znane ostrzeżenie o targetSdk 36).
  - Poprawki po przeglądzie kodu (testy JVM, bez ponownego sprawdzenia na emulatorze): utrata dostępu w trakcie synchronizacji zostawia sesje do następnego przebiegu; prośba o synchronizację w trakcie przebiegu uruchamia kolejny; rozłączenie przerywa synchronizację i nie da się go zatrzymać wyjściem z ekranu; przełączniki „W tle” i „Zapis” idą za wynikiem okna zgód; ikona zegarka tylko przy zaimportowanej sesji; `health.db-journal` poza kopią; podsumowanie tętna liczone w ViewModelach.
  - Poprawione po teście na emulatorze:
    - navbar nie wracał po powrocie na Start/Profil;
    - nagłówek karty tętna ściskał się przy długiej nazwie źródła;
    - ekran po rozłączeniu dalej pokazywał „Połączono” (D-065).
  - Otwarte: test na telefonie z prawdziwymi danymi z Samsung Health (instrukcja niżej).
- **Etapy 1–6** zmergowane (PR #1–#6). D-038 (porzucony trening trafia do historii) zostaje jako domyślne, właściciel nie zgłosił zmiany.
- **Etap 6** zaakceptowany przez właściciela bez wyników testu na telefonie. Wydanie testowe `preview-stage-6` (D-058); od teraz każdy etap dostaje takie wydanie razem z PR. Galeria komponentów bez ikony w launcherze (D-058).
  - Zrobione: zakładka Postępy (Historia, Ćwiczenia, Rutyny, Partie), szczegóły treningu z rekordami, edycja i usuwanie z przeliczeniem rekordów, e1RM (Epley, Brzycki), rekordy i tygodniowe serie w `domain/`, rekordy w trakcie treningu (glow i haptyka) i na podsumowaniu, wykres na Canvas z zakresami i przeciąganiem, wykres i rekordy w szczegółach ćwiczenia, postęp rutyny, agregaty w Room, generator danych w debug. Decyzje D-053…D-057.
  - Sprawdzone na emulatorze (36.0): wszystkie nowe ekrany w motywie ciemnym i jasnym, wykres w Onyx i Porcelain z ograniczoną przezroczystością, edycja serii daje rekord, usunięcie treningu przelicza rekordy późniejszego, rekord w trakcie treningu i na podsumowaniu, płynność przy 300 treningach (D-057). Testy: JVM 145/145, UI 4/4, lint bez nowych ostrzeżeń.
  - Otwarte: test na telefonie (instrukcja niżej), wyniki testu Etapu 5 na telefonie.
- **Etap 5** zaakceptowany przez właściciela bez wyników testu na telefonie (Now Bar i chip z zegarem niepotwierdzone na telefonie); jeśli test coś pokaże, poprawki osobnym PR. Uprawnienia z D-045 zaakceptowane razem z etapem.
  - Zrobione: `WorkoutService` (foreground `specialUse`, start tylko z pierwszego planu), powiadomienie jako Live Update (ProgressStyle, chronometr, chip na pasku stanu z zegarem treningu, a w przerwie z odliczaniem, akcje „Ukończ serię” i „Pomiń przerwę”), budzenie na koniec przerwy (exact alarm albo blokada uśpienia) z wibracją, prośba o zgodę przy pierwszym starcie (DataStore), karta „Trening w tle” na ekranie treningu, ekran „Diagnostyka powiadomień na żywo”, sekcja One UI w README, decyzje D-044…D-050.
  - Sprawdzone na emulatorze (D-050): promocja `FLAG_PROMOTED_ONGOING` na 36.1, akcje z panelu, wibracja na koniec przerwy przy wygaszonym ekranie (także w Doze), odliczanie na ekranie blokady, brak osieroconych powiadomień po `kill -9`, odtworzenie po powrocie. Testy: JVM 99/99, UI 4/4 (36.0), test powiadomienia 3/3 (36.1), lint 0 błędów.
  - Wydanie testowe `preview-stage-5` (pre-release z APK debug, D-051) na prośbę właściciela.
  - Otwarte: test na telefonie (instrukcja niżej). Chipa na pasku stanu nie dało się obejrzeć na emulatorze.
- **Tryb pracy:** plan etapu nie czeka na akceptację (D-032), akceptacja na PR.
- **Decyzje:** `docs/DECISIONS.md` (D-001 … D-066).
- **Środowisko:**
  - Android Studio 2026.2.1 w `C:\Program Files\Android\Android Studio1` (JBR 25). Gdy `java` z PATH nie startuje, ustaw `$env:JAVA_HOME` na JBR.
  - `adb`: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
  - AVD `gt-api36` (36.0, nie promuje, D-022) i `gt-api36_1` (36.1, promuje). `gt-api36_1`: start z `-memory 3072 -gpu host -qt-hide-window` (wpis `hw.ramSize` nie działa, gość dostawał 256 MB), nawigacja trzema przyciskami, zrzuty tylko od strony hosta (`adb emu screenrecord screenshot <katalog>`), aplikacji nie wysyłać w tło — odczyt bufora GPU wywala tam `system_server` (D-050).
  - Pamięć hosta: emulator + demon Gradle + demon Kotlina potrafią wyczerpać plik stronicowania. Przy pracy z emulatorem buduj z `-Pkotlin.compiler.execution.strategy=in-process` i zatrzymuj demona (`.\gradlew.bat --stop`).
  - Na emulatorze: język aplikacji pl-PL i wyłączone pismo rysikiem.
  - Test UI: `.\gradlew.bat :app:connectedDebugAndroidTest` (D-042).
- **vynox:** sklonowany do `%TEMP%\vynox-ref`, tylko do odczytu.

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

- Akceptacja Etapu 7 po teście na telefonie (wyżej), potem Etap 8 — Start i Profil.
- Wyniki testów Etapów 5 i 6 na telefonie (wyżej), gdy właściciel je przekaże.

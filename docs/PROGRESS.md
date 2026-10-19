# Postęp prac

## Status etapów

| Etap | Nazwa | Status |
|---|---|---|
| 0 | Start i plan | ✅ zakończony (2026-10-19) |
| 1 | Fundament i design system | ✅ zakończony (2026-10-19) |
| 2 | Ćwiczenia | ✅ zakończony (2026-10-19) |
| 3 | Rutyny i szablony | ✅ zakończony (2026-10-19) |
| 4 | Aktywny trening | 🔍 PR #4, czeka na akceptację |
| 5 | Powiadomienie, Live Update, przerwa w tle | — |
| 6 | Historia i postępy | — |
| 7 | Health Connect | — |
| 8 | Start i Profil | — |
| 9 | Ustawienia i kopia zapasowa | — |
| 10 | Szlif, wydajność, QA | — |
| 11 | Wydanie i aktualizator | — |

## Bieżący stan

- **Etapy 1–3** zmergowane (PR #1, #2, #3).
- **Etap 4** na branchu `feat/stage-4-workout`, PR #4.
  - Zrobione: schemat v3 (zakończone treningi) z migracją i testem; `ActiveWorkoutManager` z zapisem do pliku, odtwarzaniem po zabiciu procesu i regułą 24 h; ekran treningu (karty ćwiczeń, warianty, podpowiedzi z poprzedniej sesji i progresji, duże pola, spływanie ciężaru, typ i usuwanie serii, dodawanie ćwiczeń); nakładka przerwy; podsumowanie i zmiany celów; odrzucenie; mini-pasek; start z rutyny i pusty trening; 97 testów JVM i test UI pełnego przebiegu; zrzuty `docs/screenshots/stage4-*`.
  - Czeka: akceptacja właściciela, w tym D-038 (porzucony trening trafia do historii zamiast znikać).
- **Tryb pracy:** plan etapu nie czeka na akceptację (D-032), akceptacja na PR.
- **Decyzje:** `docs/DECISIONS.md` (D-001 … D-043).
- **Środowisko:**
  - Android Studio 2026.2.1 w `C:\Program Files\Android\Android Studio1` (JBR 25).
  - `adb`: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
  - AVD `gt-api36` to obraz 36.0, który nie promuje Live Updates (D-022); wydajność emulatora jest niestabilna (D-031).
  - Na emulatorze: język aplikacji pl-PL i wyłączone pismo rysikiem.
  - Test UI: `.\gradlew.bat :app:connectedDebugAndroidTest` (D-042).
- **vynox:** sklonowany do `%TEMP%\vynox-ref`, tylko do odczytu.

## Następny krok

- Akceptacja Etapu 4 i merge PR #4.
- Potem Etap 5 — powiadomienie, Live Update i przerwa w tle (serwis czyta `ActiveWorkoutManager`; obraz emulatora 36.1 do sprawdzenia promocji, D-022).

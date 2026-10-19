# Postęp prac

## Status etapów

| Etap | Nazwa | Status |
|---|---|---|
| 0 | Start i plan | ✅ zakończony (2026-10-19) |
| 1 | Fundament i design system | ✅ zakończony (2026-10-19) |
| 2 | Ćwiczenia | ✅ zakończony (2026-10-19) |
| 3 | Rutyny i szablony | 🔍 PR #3, czeka na akceptację |
| 4 | Aktywny trening | — |
| 5 | Powiadomienie, Live Update, przerwa w tle | — |
| 6 | Historia i postępy | — |
| 7 | Health Connect | — |
| 8 | Start i Profil | — |
| 9 | Ustawienia i kopia zapasowa | — |
| 10 | Szlif, wydajność, QA | — |
| 11 | Wydanie i aktualizator | — |

## Bieżący stan

- **Etapy 1–2** zmergowane (PR #1, #2).
- **Etap 3** na branchu `feat/stage-3-routines`, PR #3.
  - Zrobione: schemat v2 z migracją i testem migracji na JVM, rutyny z blokami i wariantami, edytor z drag & drop, arkuszem celów i wyborem ćwiczeń, menu rutyny (edycja, duplikat, usunięcie), szablony PPL / Upper-Lower / FBW, double progression w `domain/`, ochrona ćwiczeń w użyciu, 59 testów, zrzuty `docs/screenshots/stage3-*`.
  - Czeka: akceptacja właściciela.
- **Tryb pracy:** plan etapu nie czeka na akceptację (D-032), akceptacja na PR.
- **Decyzje:** `docs/DECISIONS.md` (D-001 … D-037).
- **Środowisko:**
  - Android Studio 2026.2.1 w `C:\Program Files\Android\Android Studio1` (JBR 25).
  - `adb`: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
  - AVD `gt-api36` to obraz 36.0, który nie promuje Live Updates (D-022); wydajność emulatora jest niestabilna (D-031).
  - Na emulatorze: język aplikacji pl-PL i wyłączone pismo rysikiem.
- **vynox:** sklonowany do `%TEMP%\vynox-ref`, tylko do odczytu.

## Następny krok

- Akceptacja Etapu 3 i merge PR #3.
- Potem Etap 4 — Aktywny trening (schemat v3: treningi i serie; „Start” z karty rutyny; podpowiedź progresji z D-036; tabele treningów dochodzą do sprawdzenia ćwiczeń w użyciu).

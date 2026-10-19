# Postęp prac

## Status etapów

| Etap | Nazwa | Status |
|---|---|---|
| 0 | Start i plan | ✅ zakończony (2026-10-19) |
| 1 | Fundament i design system | ✅ zakończony (2026-10-19) |
| 2 | Ćwiczenia | 🔍 PR #2, czeka na akceptację |
| 3 | Rutyny i szablony | — |
| 4 | Aktywny trening | — |
| 5 | Powiadomienie, Live Update, przerwa w tle | — |
| 6 | Historia i postępy | — |
| 7 | Health Connect | — |
| 8 | Start i Profil | — |
| 9 | Ustawienia i kopia zapasowa | — |
| 10 | Szlif, wydajność, QA | — |
| 11 | Wydanie i aktualizator | — |

## Bieżący stan

- **Etap 1** zmergowany (PR #1).
- **Etap 2** na branchu `feat/stage-2-exercises`, PR #2.
  - Zrobione: Room v1 (ćwiczenia + mięśnie), seed free-exercise-db + 26 wariantów Nipparda + 135 aliasów PL, lista z wyszukiwarką i filtrami, szczegóły (zdjęcia, mięśnie, instrukcje, wideo, miejsca na wykres i rekordy), własne ćwiczenia (dodawanie, edycja, usuwanie), shared element, testy domeny i bazy na JVM (35 testów), zrzuty w `docs/screenshots/stage2-*`.
  - Czeka: akceptacja właściciela; pomiar płynności na telefonie (D-023, D-031).
- **Decyzje:** `docs/DECISIONS.md` (D-001 … D-031).
- **Środowisko:**
  - Android Studio 2026.2.1 w `C:\Program Files\Android\Android Studio1` (JBR 25).
  - `adb` nie zawsze jest w PATH sesji; pełna ścieżka: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
  - AVD `gt-api36` to obraz 36.0, który nie promuje Live Updates (D-022); wydajność emulatora jest niestabilna (D-031).
  - Na emulatorze: język aplikacji pl-PL (`cmd locale set-app-locales`) i wyłączone pismo rysikiem.
- **vynox:** sklonowany do `%TEMP%\vynox-ref`, tylko do odczytu.

## Następny krok

- Akceptacja Etapu 2 i merge PR #2.
- Potem Etap 3 — Rutyny i szablony (schemat v2 z migracją; ochrona usuwania ćwiczeń w użyciu, D-026).

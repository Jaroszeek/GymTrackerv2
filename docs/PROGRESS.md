# Postęp prac

## Status etapów

| Etap | Nazwa | Status |
|---|---|---|
| 0 | Start i plan | ✅ zakończony (2026-10-19) |
| 1 | Fundament i design system | 🔍 PR #1, czeka na akceptację |
| 2 | Ćwiczenia | — |
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

- **Etap 1** na branchu `feat/stage-1-foundation`, PR #1.
  - Zrobione: build (AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21, pakiet `com.jaroszeek.gymtracker`), tokeny i komponenty glass, navbar z vynox, Navigation 3 z 5 zaślepkami, galeria (debug) z sondą Live Update, testy jednostkowe (kontrast, pole liczbowe, chowanie paska, stosy zakładek), moduł `:baselineprofile`, CI, szkic README, zrzuty w `docs/screenshots/`.
  - Czeka: akceptacja wyglądu przez właściciela (spodziewane rundy poprawek), test na telefonie (płynność blur, sonda Live Update na One UI 8.5).
- **Decyzje:** `docs/DECISIONS.md` (D-001 … D-024).
- **Środowisko:**
  - Android Studio 2026.2.1 w `C:\Program Files\Android\Android Studio1` (JBR 25); stary katalog `Android Studio` jest niekompletny.
  - `adb` nie zawsze jest w PATH sesji; pełna ścieżka: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
  - AVD `gt-api36` to obraz 36.0, który nie promuje Live Updates (D-022).
  - Na emulatorze ustawiony język aplikacji pl-PL (`adb shell cmd locale set-app-locales com.jaroszeek.gymtracker.debug --locales pl-PL`), bo system jest po angielsku i polskie liczby mnogie wychodziłyby źle.
- **vynox:** sklonowany do `%TEMP%\vynox-ref`, tylko do odczytu.

## Następny krok

- Akceptacja Etapu 1 (wygląd, navbar, kontrast) i merge PR #1.
- Potem Etap 2 — Ćwiczenia.

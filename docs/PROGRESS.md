# Postęp prac

## Status etapów

| Etap | Nazwa | Status |
|---|---|---|
| 0 | Start i plan | ✅ zakończony (2026-10-19) |
| 1 | Fundament i design system | ⏳ następny, czeka na start |
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

- **W repo:**
  - `CLAUDE.md`, `PROMPT.md`, `docs/`;
  - szkielet z kreatora Android Studio: AGP 9.2.1, Gradle 9.4.1, pakiet `pl.gymtracker.pl`, przykładowy `MainActivity`. Kod aplikacji jeszcze nie istnieje.
- **Plan i decyzje:** `docs/DECISIONS.md` (D-001 … D-016).
- **Środowisko:**
  - Gradle daemon działa na JBR 21 z Android Studio.
  - `adb` jest w PATH (dopiero w nowych oknach terminala).
  - AVD: `gympixel` i `vynox-clean` (API 35) oraz `gt-api36` (API 36).
- **vynox:** sklonowany do `%TEMP%\vynox-ref`, tylko do odczytu. Jeśli katalogu już nie ma, sklonuj ponownie przez `gh repo clone Jaroszeek/vynox`.

## Następny krok: Etap 1

- Właściciel aktualizuje Android Studio do Quail 4 (2026.1.4) lub nowszego, bo AGP 9.4.1 nie działa w Quail 1.
- Na początku Etapu 1, na branchu `feat/stage-1-foundation`:
  - zmiana pakietu i applicationId na `com.jaroszeek.gymtracker`;
  - podbicie wersji zgodnie z D-002;
  - usunięcie kodu z kreatora.
- Do zakresu Etapu 1 dochodzą (D-014):
  - sonda Live Update (debug);
  - pomiar płynności blur.

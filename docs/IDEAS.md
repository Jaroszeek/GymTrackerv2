# Pomysły poza zakresem

Pomysły, które pojawiły się w trakcie pracy, ale nie należą do bieżącego etapu ani specyfikacji. Każdy wymaga akceptacji przed realizacją (prompt C6).

| Data | Pomysł | Kontekst |
|---|---|---|
| 2026-10-19 | Wymuszenie języka aplikacji na polski (per-app language), gdy system jest w innym języku | Teksty są tylko po polsku, a liczby mnogie liczą się według języka systemu: na angielskim systemie „2 serii” zamiast „2 serie”. **Zrobione w Etapie 10 (D-078).** |
| 2026-10-19 | Przeciąganie bottom sheetu w dół, żeby go zamknąć | Na razie zamyka go scrim i gest wstecz (D-019). **Etap 12.** |
| 2026-10-19 | Filtr kategorii (siłowe / rozciąganie / cardio) albo ukrycie rozciągania na liście | 123 z 876 ćwiczeń to rozciąganie; lista ich nie odróżnia poza badge w szczegółach. **Etap 12.** |
| 2026-10-19 | Zmiana daty i godziny startu w edycji treningu | Edycja (D-053) zmienia nazwę, czas trwania i serie; start zostaje. Przyda się przy treningach zapisanych z opóźnieniem. **Etap 12.** |
| 2026-10-19 | Tap w ćwiczenie w szczegółach treningu otwiera szczegóły ćwiczenia | Teraz do wykresu ćwiczenia prowadzi segment Ćwiczenia w Postępach. **Etap 12.** |
| 2026-10-19 | Polskie tłumaczenie instrukcji ćwiczeń | Źródło jest po angielsku (D-025); ręczne tłumaczenie ok. 4500 zdań. **Etap 13 (D-102).** |
| 2026-10-19 | Zapis serii do Health Connect jako segmentów sesji (ćwiczenie, powtórzenia) | Zapis treningu (D-064) daje tylko sesję: nazwę, początek i koniec. |
| 2026-10-19 | Propozycja połączenia z sesją z zegarka zaraz po zakończeniu treningu (podsumowanie albo powiadomienie) | Teraz propozycja pojawia się w szczegółach treningu, gdy zegarek zsynchronizuje sesję (D-062). **Nieaktualne od Etapu 10: trening łączy się z sesją sam po każdej synchronizacji (D-084), propozycji już nie ma.** |
| 2026-10-20 | Ręczny pomiar ciała z wybraną datą i godziną | Nowy pomiar dostaje bieżący czas (D-071); przyda się przy dopisywaniu starszych ważeń. **Etap 12.** |
| 2026-10-20 | Sesje z zegarka (HC) zaznaczone w kalendarzu aktywności na Starcie | Kalendarz pokazuje tylko treningi z aplikacji (D-070). **Etap 12.** |
| 2026-10-20 | Masa beztłuszczowa z HC (`LeanBodyMassRecord`), gdy nie ma tkanki tłuszczowej | Teraz liczona tylko z wagi i tkanki tłuszczowej (D-071). **Etap 12.** |
| 2026-10-24 | Kopie treningów w Health Connect po imporcie kopii zapasowej: usunąć stare i zapisać zaimportowane, kluczować je czymś stałym między bazami (np. czasem startu) | Opcjonalny zapis (D-064) kluczuje kopię id treningu. Po imporcie nowe treningi mogą dostać id dawnych i zastąpić ich kopie w HC, a kopie treningów spoza kopii zostają w HC. Zmiana klucza dubluje już zapisane kopie, więc wymaga migracji. |
| 2026-10-24 | Szkice edytorów (rutyna, trening z historii, pomiar) w `SavedStateHandle` | Gdy system ubije proces w tle, niezapisany szkic edytora znika; aktywny trening i przerwa wracają (plik i alarm). Wymaga serializacji szkiców do `Bundle`. **Etap 12.** |
| 2026-10-25 | Kalendarz na Starcie: kropki tylko dla wybranych typów sesji z zegarka (bez automatycznie wykrytych spacerów) | Jeśli Samsung Health zapisuje do HC wykryte spacery, kropek może być prawie codziennie (D-100). **Etap 13: spacery bez kropki (D-104); wybór typów w ustawieniach — jeśli wróci potrzeba.** |
| 2026-10-25 | Masa beztłuszczowa w narzędziu danych testowych (`HealthSeedActivity`) | Wymaga `WRITE_LEAN_BODY_MASS` w manifeście buildu `benchmark`; bez tego D-099 nie da się sprawdzić na emulatorze. |
| 2026-10-24 | Kolumna „siła” (push / pull) ćwiczeń w filtrach | Dane z bazy ćwiczeń mają to pole, ale schemat go nie przechowuje; dodanie to migracja i ponowny seed. |
| 2026-10-25 | Angielska wersja aplikacji (język z systemu) | Pytanie właściciela. Dziś aplikacja jest na stałe po polsku (D-078). Wymaga: ok. 730 tekstów i liczby mnogiej po angielsku, angielskie teksty domyślne i polskie w `values-pl`, wybór języka w ustawieniach, instrukcje ćwiczeń z oryginału, zmiany zasady „Teksty UI są po polsku” w CLAUDE.md i A-części PROMPT.md. **Etap 14 (D-105).** |
| 2026-10-25 | Język aplikacji też w ustawieniach systemu (Aplikacje → GymTracker → Język) | Wymaga `localeConfig` (AGP `generateLocaleConfig`); wybór w aplikacji już jest (D-105). |
| 2026-10-25 | Amerykańskie formaty dat i zegar 12 h w angielskiej wersji | Teraz kolejność brytyjska i 24 h z tych samych wzorców (D-105). |
| 2026-10-25 | Notatki do aktualizacji po angielsku | Okno aktualizacji pokazuje polski changelog z GitHub Release; wymagałoby dwujęzycznych sekcji w `CHANGELOG.md`. |

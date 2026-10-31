# Anarchia - 4 osobne pluginy (Paper 1.21.4, Java 21)

| Plugin | Co robi | Wymaga |
|---|---|---|
| **AnarchiaHearts** | Dodatkowe Serca (czerwone, do 30), Anarchiczne Serce (pomaranczowe, 30-40), `/wyplacserca`, `/wyplacserce`, `/serca` | nic |
| **AnarchiaScoreboard** | scoreboard, `/sb`, `/sbpokaz`, `/klan` | Vault + ekonomia |
| **AnarchiaShop** | `/sklep` (kupno/sprzedaz, edycja w grze) | Vault + ekonomia |
| **AnarchiaTab** | tablista 4-kolumnowa | Vault + ekonomia + PacketEvents |

Kazdy plugin jest niezalezny: mozesz wgrac tylko ten, ktory chcesz, i jeden nie wylacza drugiego.

## WAZNE: to jest kod zrodlowy
Na serwer trafiaja tylko pliki `.jar`. Trzeba je zbudowac (patrz nizej).
Zbudujesz od razu wszystkie 4 jedna komenda; gotowe pliki beda w `AnarchiaHearts/target/`, `AnarchiaScoreboard/target/` itd.

## Jak zbudowac
### Sposob 1: IntelliJ IDEA
1. Zainstaluj IntelliJ IDEA Community (zaproponuje tez pobranie JDK 21).
2. `File > Open` i wybierz folder `AnarchiaPlugins` (ten z glownym `pom.xml`).
3. Poczekaj na pobranie bibliotek przez Maven.
4. Panel `Maven` > `anarchia-parent` > `Lifecycle` > dwuklik `package`.
5. Gotowe jary: `AnarchiaHearts/target/AnarchiaHearts.jar`, `AnarchiaScoreboard/target/AnarchiaScoreboard.jar`, `AnarchiaShop/target/AnarchiaShop.jar`, `AnarchiaTab/target/AnarchiaTab.jar`.

### Sposob 2: wiersz polecen
Zainstaluj JDK 21 i Maven, w folderze z `pom.xml`:

    mvn clean package

### Sposob 3: GitHub (bez instalowania)
1. Zaloz repozytorium na github.com i wrzuc cala zawartosc folderu `AnarchiaPlugins` (razem z `.github`).
2. Zakladka `Actions` > `Build` > po zakonczeniu pobierz artefakt `Anarchia-pluginy` (4 pliki .jar).

## Instalacja
1. Wrzuc wybrane `.jar` do `plugins/`.
2. Zainstaluj zaleznosci z tabeli (Vault, ekonomia np. EssentialsX, PacketEvents dla tablisty).
3. Zrestartuj serwer (nie uzywaj `/reload`).
4. W konsoli ma byc np. `AnarchiaHearts wlaczony.` Jesli plugin jest czerwony w `/plugins`, wklej logi z `logs/latest.log`.

## Komendy
**AnarchiaHearts** (admin: `anarchiahearts.admin`)
- `/serca give <gracz> <red|orange> [ilosc]`, `/serca set <gracz> <serca>`, `/serca reload`
- `/wyplacserca <ilosc>`, `/wyplacserce`

**AnarchiaScoreboard** (`/sb reload`: `anarchiascoreboard.admin`)
- `/sb` (ukryj/pokaz), `/sb on`, `/sb off`, `/sbpokaz`
- `/klan stworz <nazwa>`, `/klan opusc`

**AnarchiaShop** (admin: `anarchia.shop.admin`)
- `/sklep`, `/sklep <kategoria>`; LPM kup 1, Shift+LPM kup stack, PPM sprzedaj 1, Shift+PPM sprzedaj wszystko
- Edycja (przedmiot w rece): `/sklep kategoria stworz <id> <nazwa>`, `/sklep dodaj <kategoria> <kupno> <sprzedaz>`, `/sklep cena <kupno> <sprzedaz>`, `/sklep usun`, `/sklep kategoria usun <id>`, `/sklep reload`
- Dane: `plugins/AnarchiaShop/shop.yml`. Cena 0 = nie mozna kupic / sprzedac.
- Serca do sklepu: wez serce komenda `/serca give`, potrzymaj w rece i uzyj `/sklep dodaj serca <kupno> <sprzedaz>` (wczesniej stworz kategorie `serca`).

## Pliki konfiguracyjne
Kazdy plugin ma wlasny `config.yml` w `plugins/<NazwaPluginu>/` (teksty, limity, wyglad, wiadomosci).

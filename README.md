# Expand Worldborder Challenge

Paper-Plugin fuer eine Multiplayer-Challenge, bei der die Worldborder durch Advancements waechst.

## Idee

- Spieler starten in einer kleinen Worldborder.
- Jedes neue, eindeutige Advancement erweitert die Border.
- Ein Advancement zaehlt pro Run nur einmal, auch wenn mehrere Spieler es erreichen.
- Stirbt ein aktiver Run-Spieler, wird der Run verloren.
- Danach werden Spieler in die Lobby geschickt und eine neue Run-Welt wird erzeugt.

## Voraussetzungen

- Paper 26.1.2
- Java 25
- Gradle 9.x

## Build

```powershell
gradle clean build
```

Die fertige Plugin-JAR liegt danach unter:

```text
build/libs/expand-worldborder-challenge-0.1.0.jar
```

## Installation

1. Paper-Server stoppen.
2. JAR in den `plugins`-Ordner kopieren.
3. Server starten.
4. Config unter `plugins/ExpandWorldborderChallenge/config.yml` pruefen.
5. Server neu starten oder Plugin erneut laden.

## Commands

Basiscommand:

```text
/borderchallenge
```

Alias:

```text
/ewbc
```

Wichtige Subcommands:

```text
/borderchallenge start
/borderchallenge pause
/borderchallenge resume
/borderchallenge reset
/borderchallenge stop
/borderchallenge status
/borderchallenge timer
/borderchallenge advancements
/borderchallenge missing
/borderchallenge border
/borderchallenge gui
/borderchallenge forcejoin
/borderchallenge reloadadvancements
```

## Permissions

```text
expandworldborder.admin
expandworldborder.view
```

`expandworldborder.admin` ist fuer OP/Admin-Aktionen.

`expandworldborder.view` ist fuer Status und GUI.

## Wichtige Config

```yaml
seed:
  randomize: true

border:
  start-size: 128
  growth-per-advancement: 128
  animation-seconds: 3
```

`start-size` und `growth-per-advancement` sind Worldborder-Durchmesser, nicht Radius.

## Hinweise

- Build-Dateien wie `build/` und `.gradle/` werden ignoriert.
- Die Run-Welten werden bei Paper 26.1 unter `world/dimensions/minecraft/...` verwaltet.
- Nach Plugin-Updates den Server neu starten, bevor weiter getestet wird.

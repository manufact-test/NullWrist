# nosmokeblle

Retro-pixel quit-smoking companion for Pebble, designed first for Pebble Time 2 / Emery.

## Features
- custom 5x7 bitmap text renderer with English and Russian uppercase glyphs
- no system-font dependency for app UI text
- smoke-free streak derived from last_smoked
- cigarettes avoided and money saved
- milestone progress and achievement screens
- relapse confirmation flow
- onboarding for last cigarette, cigarettes/day, cost/day and currency
- statistics/settings and a 10-attempt persistence ring buffer
- USD, EUR, PLN, RUB
- Russian for ru* locales; English otherwise

## Controls
Home: SELECT relapse confirmation, UP achievements, DOWN statistics, BACK exit.

## Platforms
aplite, basalt, chalk, diorite, emery. Emery is the primary target.

## Build
`pebble build`

## Structure
main.c lifecycle; ui.c custom UI/navigation; pixel_font.c bitmap renderer; icons.c pixel icons; storage.c persistence; calc.c math; milestones.c milestones; localization.c RU/EN.

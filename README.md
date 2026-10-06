# Phribs Gym Tracker

An Android app for logging gym workouts, built with Jetpack Compose and Room.

## Features

- Log workouts with exercises, sets, reps and weight
- Edit and delete past workouts
- Visit stats for this week, this month and all time
- Most frequently trained exercises
- Rest timer after each set, with a countdown notification and vibration
- Personal-record badges on workouts that beat the previous best
- Weekly streak (current and best)
- Activity heatmap for the last 18 weeks
- Dark, light or system theme, with optional system (Material You) accent colors
- Home screen widget
- kg or lb weight unit
- Export and import backups as JSON or CSV

## Ideas

### Most useful
- [x] **Per-exercise progress:** tap an exercise to see a weight or volume chart over time, plus the personal record
- [x] **Personal-record alerts:** show a badge when a logged set beats the previous best
- [x] **Rest timer:** countdown after each set, with a notification and vibration
- [ ] **Templates / routines:** save a routine (e.g. "Push day") and start a workout from it, pre-filled from last time

### Quality of life
- [x] **Per-set logging:** separate reps and weight for each set
- [ ] **Show last time:** display the previous session for an exercise while logging
- [x] **kg/lb setting**
- [x] **Export/import:** JSON or CSV backup

### Motivation
- [x] Streaks
- [x] Calendar heatmap
- [x] **Weekly goal:** e.g. 3 visits per week, shown against the This Week stat
- [x] **Home screen widget:** this week's visits and streak

### Structural
- [ ] Move navigation to Navigation Compose instead of boolean screen flags
- [x] Split `MainActivity.kt` into one file per screen

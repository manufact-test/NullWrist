#pragma once
#include <pebble.h>

typedef struct {
  time_t last_smoked;
  int cigarettes_per_day;
  int money_cents_per_day;
  int attempts;
  time_t best_streak;
  int32_t lifetime_avoided_milli;
  int32_t lifetime_saved_cents_milli;
  uint32_t notified_mask;
  bool configured;
} AppState;

void storage_load(AppState *state);
void storage_save(const AppState *state);
void storage_record_relapse(AppState *state, time_t now);

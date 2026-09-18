#pragma once
#include <pebble.h>
#define HISTORY_MAX 10
typedef enum { CUR_USD=0,CUR_EUR=1,CUR_PLN=2,CUR_RUB=3 } Currency;
typedef struct{int32_t start,end,duration,avoided,saved_cents;} AttemptRecord;
typedef struct{time_t last_smoked;int cigarettes_per_day;int money_cents_per_day;Currency currency;int attempts;time_t best_streak;int32_t lifetime_avoided_milli;int32_t lifetime_saved_cents_milli;uint32_t notified_mask;bool configured;AttemptRecord history[HISTORY_MAX];uint8_t history_count;uint8_t history_head;} AppState;
void storage_load(AppState *state);void storage_save(const AppState *state);void storage_record_relapse(AppState *state,time_t now);void storage_reset(AppState *state);

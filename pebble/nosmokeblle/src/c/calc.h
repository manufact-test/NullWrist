#pragma once
#include <pebble.h>
#include "storage.h"
typedef struct { time_t elapsed; int cigarettes_avoided; int saved_cents; } Stats;
Stats calc_stats(const AppState *state,time_t now);
void calc_split_elapsed(time_t sec,int *days,int *hours,int *mins,int *secs);
void calc_format_compact(time_t sec,char *buf,size_t size);

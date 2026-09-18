#pragma once
#include <pebble.h>
#include "localization.h"
typedef struct { int32_t seconds; const char *en_title; const char *ru_title; } Milestone;
extern const Milestone MILESTONES[];
extern const int MILESTONE_COUNT;
int milestone_next(time_t elapsed);
const char *milestone_title(int index, AppLang lang);

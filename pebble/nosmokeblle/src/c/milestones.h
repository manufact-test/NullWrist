#pragma once
#include <pebble.h>
#include "localization.h"
typedef struct{int32_t seconds;const char*en;const char*ru;const char*en_desc;const char*ru_desc;bool medical;} Milestone;
extern const Milestone MILESTONES[];extern const int MILESTONE_COUNT;
int milestone_next(time_t elapsed);int milestone_prev(time_t elapsed);const char*milestone_title(int i,AppLang l);const char*milestone_desc(int i,AppLang l);

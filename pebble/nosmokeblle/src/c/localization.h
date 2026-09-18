#pragma once
#include <pebble.h>
typedef enum { LANG_EN, LANG_RU } AppLang;
AppLang loc_detect(void);
const char *loc(AppLang lang, const char *key);

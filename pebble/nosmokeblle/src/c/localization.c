#include "localization.h"
#include <string.h>

AppLang loc_detect(void) {
  const char *locale=i18n_get_system_locale();
  return locale && (!strncmp(locale,"ru",2) || !strncmp(locale,"RU",2)) ? LANG_RU : LANG_EN;
}

const char *loc(AppLang l, const char *k) {
  bool r=l==LANG_RU;
  if(!strcmp(k,"smoke_free")) return r?"БЕЗ СИГАРЕТ":"SMOKE FREE";
  if(!strcmp(k,"avoided")) return r?"НЕ ВЫКУРЕНО":"NOT SMOKED";
  if(!strcmp(k,"saved")) return r?"СЭКОНОМЛЕНО":"SAVED";
  if(!strcmp(k,"smoked")) return r?"ПОКУРИЛ":"SMOKED";
  if(!strcmp(k,"confirm")) return r?"ТЫ ПОКУРИЛ?":"DID YOU SMOKE?";
  if(!strcmp(k,"yes")) return r?"ДА":"YES";
  if(!strcmp(k,"no")) return r?"НЕТ":"NO";
  if(!strcmp(k,"setup")) return r?"НАСТРОЙКА":"SETUP";
  if(!strcmp(k,"set_last")) return r?"ПОСЛЕДНЯЯ СИГАРЕТА":"LAST CIGARETTE";
  if(!strcmp(k,"cigs_day")) return r?"СИГАРЕТ В ДЕНЬ":"CIGS / DAY";
  if(!strcmp(k,"money_day")) return r?"РАСХОД В ДЕНЬ":"COST / DAY";
  if(!strcmp(k,"done")) return r?"ГОТОВО":"DONE";
  if(!strcmp(k,"next_goal")) return r?"СЛЕД. ЦЕЛЬ":"NEXT GOAL";
  if(!strcmp(k,"history")) return r?"ДОСТИЖЕНИЯ":"MILESTONES";
  if(!strcmp(k,"best")) return r?"ЛУЧШАЯ СЕРИЯ":"BEST STREAK";
  if(!strcmp(k,"attempts")) return r?"ПОПЫТКИ":"ATTEMPTS";
  if(!strcmp(k,"last_time")) return r?"ВРЕМЯ":"TIME";
  if(!strcmp(k,"date")) return r?"ДАТА":"DATE";
  if(!strcmp(k,"press_select")) return r?"SELECT — ДАЛЕЕ":"SELECT — NEXT";
  return k;
}

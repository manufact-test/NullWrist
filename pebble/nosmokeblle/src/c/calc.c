#include "calc.h"
Stats calc_stats(const AppState *s, time_t now) {
  Stats r={0};
  r.elapsed = now > s->last_smoked ? now - s->last_smoked : 0;
  r.cigarettes_avoided = (int)(((int64_t)r.elapsed * s->cigarettes_per_day) / 86400);
  r.saved_cents = (int)(((int64_t)r.elapsed * s->money_cents_per_day) / 86400);
  return r;
}
void calc_format_elapsed(time_t sec, char *b, size_t n) {
  int d=sec/86400; int h=(sec%86400)/3600; int m=(sec%3600)/60;
  if (d>0) snprintf(b,n,"%dd %02dh %02dm",d,h,m);
  else snprintf(b,n,"%02dh %02dm",h,m);
}

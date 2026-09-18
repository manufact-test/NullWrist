#include "calc.h"
Stats calc_stats(const AppState *s,time_t now){Stats r={0};r.elapsed=now>s->last_smoked?now-s->last_smoked:0;r.cigarettes_avoided=(int)(((int64_t)r.elapsed*s->cigarettes_per_day)/86400);r.saved_cents=(int)(((int64_t)r.elapsed*s->money_cents_per_day)/86400);return r;}
void calc_split_elapsed(time_t sec,int*d,int*h,int*m,int*s){*d=sec/86400;*h=(sec%86400)/3600;*m=(sec%3600)/60;*s=sec%60;}
void calc_format_compact(time_t sec,char*b,size_t n){int d,h,m,s;calc_split_elapsed(sec,&d,&h,&m,&s);if(d)snprintf(b,n,"%dD %02d:%02d",d,h,m);else snprintf(b,n,"%02d:%02d:%02d",h,m,s);}

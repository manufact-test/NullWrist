#include "milestones.h"
const Milestone MILESTONES[]={
 {1200,"20 MIN","20 МИН"},
 {3600,"1 HOUR","1 ЧАС"},
 {28800,"8 HOURS","8 ЧАСОВ"},
 {43200,"12 HOURS","12 ЧАСОВ"},
 {86400,"1 DAY","1 ДЕНЬ"},
 {172800,"2 DAYS","2 ДНЯ"},
 {259200,"3 DAYS","3 ДНЯ"},
 {604800,"1 WEEK","1 НЕДЕЛЯ"},
 {1209600,"2 WEEKS","2 НЕДЕЛИ"},
 {2592000,"1 MONTH","1 МЕСЯЦ"},
 {7776000,"3 MONTHS","3 МЕСЯЦА"},
 {15552000,"6 MONTHS","6 МЕСЯЦЕВ"},
 {23328000,"9 MONTHS","9 МЕСЯЦЕВ"},
 {31536000,"1 YEAR","1 ГОД"},
 {63072000,"2 YEARS","2 ГОДА"},
 {94608000,"3 YEARS","3 ГОДА"},
 {157680000,"5 YEARS","5 ЛЕТ"},
 {315360000,"10 YEARS","10 ЛЕТ"},
 {473040000,"15 YEARS","15 ЛЕТ"}
};
const int MILESTONE_COUNT=sizeof(MILESTONES)/sizeof(MILESTONES[0]);
int milestone_next(time_t elapsed){for(int i=0;i<MILESTONE_COUNT;i++)if(elapsed<MILESTONES[i].seconds)return i;return -1;}
const char *milestone_title(int i,AppLang l){if(i<0||i>=MILESTONE_COUNT)return "MAX";return l==LANG_RU?MILESTONES[i].ru_title:MILESTONES[i].en_title;}

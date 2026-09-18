#include <pebble.h>
#include "storage.h"
#include "calc.h"
#include "localization.h"
#include "milestones.h"

typedef enum { SCREEN_MAIN, SCREEN_SETUP, SCREEN_HISTORY } ScreenMode;
typedef enum { SETUP_DATE, SETUP_HOUR, SETUP_MINUTE, SETUP_CIGS, SETUP_MONEY } SetupStep;

static Window *s_window;
static TextLayer *s_title,*s_time,*s_line1,*s_line2,*s_line3,*s_hint;
static AppState s_state;
static AppLang s_lang;
static ScreenMode s_screen;
static SetupStep s_setup_step;
static struct tm s_setup_tm;
static bool s_confirm=false;
static AppTimer *s_timer;
static int s_history_index=0;

static TextLayer *mk(Layer *root,GRect frame,GFont font,GTextAlignment align){
  TextLayer *t=text_layer_create(frame);
  text_layer_set_background_color(t,GColorClear);
  text_layer_set_text_color(t,GColorWhite);
  text_layer_set_font(t,font);
  text_layer_set_text_alignment(t,align);
  layer_add_child(root,text_layer_get_layer(t));
  return t;
}

static void show_achievement_if_needed(time_t elapsed){
  for(int i=0;i<MILESTONE_COUNT && i<31;i++){
    uint32_t bit=(1u<<i);
    if(elapsed>=MILESTONES[i].seconds && !(s_state.notified_mask & bit)){
      s_state.notified_mask |= bit;
      storage_save(&s_state);
      vibes_double_pulse();
    }
  }
}

static void format_setup_value(char *buf,size_t n){
  if(s_setup_step==SETUP_DATE){
    snprintf(buf,n,"%04d-%02d-%02d",s_setup_tm.tm_year+1900,s_setup_tm.tm_mon+1,s_setup_tm.tm_mday);
  }else if(s_setup_step==SETUP_HOUR){
    snprintf(buf,n,"%02d:%02d",s_setup_tm.tm_hour,s_setup_tm.tm_min);
  }else if(s_setup_step==SETUP_MINUTE){
    snprintf(buf,n,"%02d:%02d",s_setup_tm.tm_hour,s_setup_tm.tm_min);
  }else if(s_setup_step==SETUP_CIGS){
    snprintf(buf,n,"%d",s_state.cigarettes_per_day);
  }else{
    snprintf(buf,n,"$%d.%02d",s_state.money_cents_per_day/100,s_state.money_cents_per_day%100);
  }
}

static void refresh_setup(void){
  static char value[40], line[48];
  format_setup_value(value,sizeof(value));
  text_layer_set_text(s_title,loc(s_lang,"setup"));
  if(s_setup_step==SETUP_DATE) snprintf(line,sizeof(line),"%s",loc(s_lang,"date"));
  else if(s_setup_step==SETUP_HOUR || s_setup_step==SETUP_MINUTE) snprintf(line,sizeof(line),"%s",loc(s_lang,"last_time"));
  else if(s_setup_step==SETUP_CIGS) snprintf(line,sizeof(line),"%s",loc(s_lang,"cigs_day"));
  else snprintf(line,sizeof(line),"%s",loc(s_lang,"money_day"));
  text_layer_set_text(s_line1,line);
  text_layer_set_text(s_time,value);
  text_layer_set_text(s_line2,"▲ / ▼");
  text_layer_set_text(s_line3,loc(s_lang,"press_select"));
  text_layer_set_text(s_hint,"");
}

static void refresh_history(void){
  static char title[48], status[48], pos[24];
  time_t elapsed=time(NULL)-s_state.last_smoked;
  snprintf(title,sizeof(title),"%s",milestone_title(s_history_index,s_lang));
  snprintf(status,sizeof(status),"%s",elapsed>=MILESTONES[s_history_index].seconds ? "✓ UNLOCKED" : "□ LOCKED");
  snprintf(pos,sizeof(pos),"%d/%d",s_history_index+1,MILESTONE_COUNT);
  text_layer_set_text(s_title,loc(s_lang,"history"));
  text_layer_set_text(s_time,title);
  text_layer_set_text(s_line1,status);
  text_layer_set_text(s_line2,pos);
  text_layer_set_text(s_line3,"▲ / ▼");
  text_layer_set_text(s_hint,"BACK");
}

static void refresh_main(void){
  time_t now=time(NULL);
  Stats st=calc_stats(&s_state,now);
  show_achievement_if_needed(st.elapsed);
  static char tb[40],cb[48],mb[48],nb[56],hb[48];
  calc_format_elapsed(st.elapsed,tb,sizeof(tb));
  snprintf(cb,sizeof(cb),"%s: %d",loc(s_lang,"avoided"),st.cigarettes_avoided);
  snprintf(mb,sizeof(mb),"%s: $%d.%02d",loc(s_lang,"saved"),st.saved_cents/100,st.saved_cents%100);
  int mi=milestone_next(st.elapsed);
  if(mi>=0){
    time_t rem=MILESTONES[mi].seconds-st.elapsed;
    char rb[24]; calc_format_elapsed(rem,rb,sizeof(rb));
    snprintf(nb,sizeof(nb),"%s: %s",milestone_title(mi,s_lang),rb);
  }else snprintf(nb,sizeof(nb),"%s","MAX STREAK");
  snprintf(hb,sizeof(hb),"%s",s_confirm?loc(s_lang,"confirm"):loc(s_lang,"smoked"));
  text_layer_set_text(s_title,loc(s_lang,"smoke_free"));
  text_layer_set_text(s_time,tb);
  text_layer_set_text(s_line1,cb);
  text_layer_set_text(s_line2,mb);
  text_layer_set_text(s_line3,nb);
  text_layer_set_text(s_hint,hb);
}

static void refresh(void){
  if(s_screen==SCREEN_SETUP) refresh_setup();
  else if(s_screen==SCREEN_HISTORY) refresh_history();
  else refresh_main();
}

static void tick(void *ctx){
  if(s_screen==SCREEN_MAIN) refresh();
  s_timer=app_timer_register(30000,tick,NULL);
}

static void setup_adjust(int delta){
  if(s_setup_step==SETUP_DATE){
    time_t t=mktime(&s_setup_tm)+(delta*86400);
    s_setup_tm=*localtime(&t);
  }else if(s_setup_step==SETUP_HOUR){
    s_setup_tm.tm_hour=(s_setup_tm.tm_hour+delta+24)%24;
  }else if(s_setup_step==SETUP_MINUTE){
    s_setup_tm.tm_min=(s_setup_tm.tm_min+delta+60)%60;
  }else if(s_setup_step==SETUP_CIGS){
    s_state.cigarettes_per_day+=delta;
    if(s_state.cigarettes_per_day<1) s_state.cigarettes_per_day=1;
    if(s_state.cigarettes_per_day>100) s_state.cigarettes_per_day=100;
  }else{
    s_state.money_cents_per_day+=delta*25;
    if(s_state.money_cents_per_day<0) s_state.money_cents_per_day=0;
    if(s_state.money_cents_per_day>50000) s_state.money_cents_per_day=50000;
  }
  refresh();
}

static void select_click(ClickRecognizerRef r,void *ctx){
  if(s_screen==SCREEN_SETUP){
    if(s_setup_step<SETUP_MONEY){s_setup_step++;refresh();return;}
    s_state.last_smoked=mktime(&s_setup_tm);
    if(s_state.last_smoked>time(NULL)) s_state.last_smoked=time(NULL);
    s_state.configured=true;
    s_state.notified_mask=0;
    storage_save(&s_state);
    s_screen=SCREEN_MAIN;
    refresh();
    return;
  }
  if(s_screen==SCREEN_HISTORY){s_screen=SCREEN_MAIN;refresh();return;}
  if(!s_confirm){s_confirm=true;refresh();return;}
  storage_record_relapse(&s_state,time(NULL));
  s_confirm=false;
  vibes_double_pulse();
  refresh();
}

static void back_click(ClickRecognizerRef r,void *ctx){
  if(s_screen==SCREEN_HISTORY){s_screen=SCREEN_MAIN;refresh();return;}
  if(s_screen==SCREEN_SETUP){
    if(s_setup_step>SETUP_DATE){s_setup_step--;refresh();return;}
    return;
  }
  if(s_confirm){s_confirm=false;refresh();return;}
  window_stack_pop_all(false);
}

static void up_click(ClickRecognizerRef r,void *ctx){
  if(s_screen==SCREEN_SETUP){setup_adjust(1);return;}
  if(s_screen==SCREEN_HISTORY){s_history_index=(s_history_index+MILESTONE_COUNT-1)%MILESTONE_COUNT;refresh();return;}
  s_screen=SCREEN_HISTORY;refresh();
}

static void down_click(ClickRecognizerRef r,void *ctx){
  if(s_screen==SCREEN_SETUP){setup_adjust(-1);return;}
  if(s_screen==SCREEN_HISTORY){s_history_index=(s_history_index+1)%MILESTONE_COUNT;refresh();return;}
}

static void clicks(void *ctx){
  window_single_click_subscribe(BUTTON_ID_SELECT,select_click);
  window_single_click_subscribe(BUTTON_ID_BACK,back_click);
  window_single_click_subscribe(BUTTON_ID_UP,up_click);
  window_single_click_subscribe(BUTTON_ID_DOWN,down_click);
}

static void load(Window *w){
  Layer *root=window_get_root_layer(w);
  GRect b=layer_get_bounds(root);
  int W=b.size.w,H=b.size.h;
  int pad=PBL_IF_RECT_ELSE(8,18);
  bool tall=H>=200;
  window_set_background_color(w,GColorBlack);
  s_title=mk(root,GRect(pad,6,W-pad*2,24),fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),GTextAlignmentCenter);
  s_time=mk(root,GRect(pad,tall?38:30,W-pad*2,tall?52:42),fonts_get_system_font(tall?FONT_KEY_BITHAM_42_BOLD:FONT_KEY_BITHAM_30_BLACK),GTextAlignmentCenter);
  s_line1=mk(root,GRect(pad,tall?98:78,W-pad*2,28),fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),GTextAlignmentLeft);
  s_line2=mk(root,GRect(pad,tall?128:101,W-pad*2,28),fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),GTextAlignmentLeft);
  s_line3=mk(root,GRect(pad,tall?158:126,W-pad*2,30),fonts_get_system_font(FONT_KEY_GOTHIC_14),GTextAlignmentLeft);
  s_hint=mk(root,GRect(pad,H-30,W-pad*2,24),fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),GTextAlignmentCenter);
  refresh();
}

static void unload(Window *w){
  text_layer_destroy(s_title);text_layer_destroy(s_time);text_layer_destroy(s_line1);
  text_layer_destroy(s_line2);text_layer_destroy(s_line3);text_layer_destroy(s_hint);
}

static void init(void){
  storage_load(&s_state);
  s_lang=loc_detect();
  time_t now=time(NULL);
  s_setup_tm=*localtime(&now);
  s_screen=s_state.configured?SCREEN_MAIN:SCREEN_SETUP;
  s_setup_step=SETUP_DATE;
  s_window=window_create();
  window_set_click_config_provider(s_window,clicks);
  window_set_window_handlers(s_window,(WindowHandlers){.load=load,.unload=unload});
  window_stack_push(s_window,true);
  s_timer=app_timer_register(30000,tick,NULL);
}

static void deinit(void){
  if(s_timer) app_timer_cancel(s_timer);
  window_destroy(s_window);
}

int main(void){init();app_event_loop();deinit();}

#include <pebble.h>
#include "storage.h"
#include "calc.h"
#include "localization.h"
#include "milestones.h"

static Window *s_window;
static TextLayer *s_title,*s_time,*s_cigs,*s_money,*s_next,*s_hint;
static AppState s_state;
static AppLang s_lang;
static bool s_confirm=false;
static AppTimer *s_timer;

static void refresh(void){
  time_t now=time(NULL);
  Stats st=calc_stats(&s_state,now);
  static char tb[40],cb[40],mb[40],nb[48],hb[48];
  calc_format_elapsed(st.elapsed,tb,sizeof(tb));
  snprintf(cb,sizeof(cb),"%s: %d",loc(s_lang,"avoided"),st.cigarettes_avoided);
  snprintf(mb,sizeof(mb),"%s: $%d.%02d",loc(s_lang,"saved"),st.saved_cents/100,st.saved_cents%100);
  int mi=milestone_next(st.elapsed);
  if(mi>=0){
    time_t rem=MILESTONES[mi].seconds-st.elapsed;
    char rb[24]; calc_format_elapsed(rem,rb,sizeof(rb));
    snprintf(nb,sizeof(nb),"%s: %s",milestone_title(mi,s_lang),rb);
  } else snprintf(nb,sizeof(nb),"%s","MAX STREAK");
  snprintf(hb,sizeof(hb),"%s",s_confirm?loc(s_lang,"confirm"):loc(s_lang,"smoked"));
  text_layer_set_text(s_title,loc(s_lang,"smoke_free"));
  text_layer_set_text(s_time,tb);
  text_layer_set_text(s_cigs,cb);
  text_layer_set_text(s_money,mb);
  text_layer_set_text(s_next,nb);
  text_layer_set_text(s_hint,hb);
}
static void tick(void *ctx){refresh();s_timer=app_timer_register(30000,tick,NULL);}
static void select_click(ClickRecognizerRef r,void *ctx){
  if(!s_state.configured){
    s_state.configured=true;s_state.last_smoked=time(NULL);storage_save(&s_state);refresh();return;
  }
  if(!s_confirm){s_confirm=true;refresh();return;}
  storage_record_relapse(&s_state,time(NULL));s_confirm=false;vibes_double_pulse();refresh();
}
static void back_click(ClickRecognizerRef r,void *ctx){if(s_confirm){s_confirm=false;refresh();}else window_stack_pop_all(false);}
static void up_click(ClickRecognizerRef r,void *ctx){if(!s_state.configured){s_state.cigarettes_per_day++;storage_save(&s_state);}}
static void down_click(ClickRecognizerRef r,void *ctx){if(!s_state.configured&&s_state.cigarettes_per_day>1){s_state.cigarettes_per_day--;storage_save(&s_state);}}
static void clicks(void *ctx){
  window_single_click_subscribe(BUTTON_ID_SELECT,select_click);
  window_single_click_subscribe(BUTTON_ID_BACK,back_click);
  window_single_click_subscribe(BUTTON_ID_UP,up_click);
  window_single_click_subscribe(BUTTON_ID_DOWN,down_click);
}
static TextLayer *mk(Layer *root,GRect frame,GFont font,GTextAlignment align){
  TextLayer *t=text_layer_create(frame);
  text_layer_set_background_color(t,GColorClear);
  text_layer_set_text_color(t,GColorWhite);
  text_layer_set_font(t,font);text_layer_set_text_alignment(t,align);
  layer_add_child(root,text_layer_get_layer(t));return t;
}
static void load(Window *w){
  Layer *root=window_get_root_layer(w);GRect b=layer_get_bounds(root);int W=b.size.w,H=b.size.h;
  window_set_background_color(w,GColorBlack);
  int pad=PBL_IF_RECT_ELSE(8,18);
  s_title=mk(root,GRect(pad,6,W-pad*2,24),fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),GTextAlignmentCenter);
  s_time=mk(root,GRect(pad,30,W-pad*2,42),fonts_get_system_font(FONT_KEY_BITHAM_30_BLACK),GTextAlignmentCenter);
  s_cigs=mk(root,GRect(pad,78,W-pad*2,24),fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),GTextAlignmentLeft);
  s_money=mk(root,GRect(pad,101,W-pad*2,24),fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),GTextAlignmentLeft);
  s_next=mk(root,GRect(pad,126,W-pad*2,28),fonts_get_system_font(FONT_KEY_GOTHIC_14),GTextAlignmentLeft);
  s_hint=mk(root,GRect(pad,H-28,W-pad*2,24),fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),GTextAlignmentCenter);
  refresh();
}
static void unload(Window *w){text_layer_destroy(s_title);text_layer_destroy(s_time);text_layer_destroy(s_cigs);text_layer_destroy(s_money);text_layer_destroy(s_next);text_layer_destroy(s_hint);}
static void init(void){storage_load(&s_state);s_lang=loc_detect();s_window=window_create();window_set_click_config_provider(s_window,clicks);window_set_window_handlers(s_window,(WindowHandlers){.load=load,.unload=unload});window_stack_push(s_window,true);s_timer=app_timer_register(30000,tick,NULL);}
static void deinit(void){if(s_timer)app_timer_cancel(s_timer);window_destroy(s_window);}
int main(void){init();app_event_loop();deinit();}

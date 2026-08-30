#include <pebble.h>

static Window *s_window;
static Layer *s_layer;
static GBitmap *s_bg;
static int s_watch_battery = 78;
static int s_phone_battery = 63;
static int s_hr = 78;
static int s_temp = 24;

static GFont F_BIG;
static GFont F_MED;
static GFont F_SMALL;
static GColor C_ORANGE;
static GColor C_MUTED;
static GColor C_RED;
static GColor C_BAR_BG;

static void draw_text(GContext *ctx, const char *text, GFont font, GColor color, GRect r) {
  graphics_context_set_text_color(ctx, color);
  graphics_draw_text(ctx, text, font, r, GTextOverflowModeFill, GTextAlignmentLeft, NULL);
}

static void draw_bar(GContext *ctx, int x, int y, int w, int value, GColor fill) {
  int v = value < 0 ? 0 : (value > 100 ? 100 : value);
  graphics_context_set_fill_color(ctx, C_BAR_BG);
  graphics_fill_rect(ctx, GRect(x,y,w,4), 0, GCornerNone);
  graphics_context_set_fill_color(ctx, fill);
  graphics_fill_rect(ctx, GRect(x,y,(w*v)/100,4), 0, GCornerNone);
}

static void canvas_update(Layer *layer, GContext *ctx) {
  if (s_bg) graphics_draw_bitmap_in_rect(ctx, s_bg, GRect(0,0,200,228));

  time_t now = time(NULL);
  struct tm *t = localtime(&now);
  char timebuf[8];
  char datebuf[16];
  char daybuf[24];
  static const char *MON[] = {"ЯНВ","ФЕВ","МАР","АПР","МАЙ","ИЮН","ИЮЛ","АВГ","СЕН","ОКТ","НОЯ","ДЕК"};
  static const char *DAY[] = {"ВОСКРЕСЕНЬЕ","ПОНЕДЕЛЬНИК","ВТОРНИК","СРЕДА","ЧЕТВЕРГ","ПЯТНИЦА","СУББОТА"};
  strftime(timebuf,sizeof(timebuf),"%H:%M",t);
  snprintf(datebuf,sizeof(datebuf),"%d %s",t->tm_mday,MON[t->tm_mon]);
  snprintf(daybuf,sizeof(daybuf),"%s",DAY[t->tm_wday]);

  draw_text(ctx,timebuf,F_BIG,C_ORANGE,GRect(5,17,89,50));
  draw_text(ctx,datebuf,F_MED,C_ORANGE,GRect(8,64,70,18));
  draw_text(ctx,daybuf,F_SMALL,C_MUTED,GRect(8,78,76,16));

  char hr[8], temp[8], wb[8], pb[8];
  snprintf(hr,sizeof(hr),"%d",s_hr);
  snprintf(temp,sizeof(temp),"%d°",s_temp);
  snprintf(wb,sizeof(wb),"%d%%",s_watch_battery);
  snprintf(pb,sizeof(pb),"%d%%",s_phone_battery);
  draw_text(ctx,hr,F_MED,C_ORANGE,GRect(34,113,28,22));
  draw_text(ctx,temp,F_MED,C_ORANGE,GRect(35,152,36,22));
  draw_text(ctx,wb,F_MED,C_ORANGE,GRect(33,191,42,22));
  draw_text(ctx,pb,F_MED,C_ORANGE,GRect(122,191,42,22));
  draw_bar(ctx,33,215,55,s_watch_battery,C_RED);
  draw_bar(ctx,121,215,60,s_phone_battery,C_ORANGE);
}

static void tick_handler(struct tm *tick_time, TimeUnits units_changed) { layer_mark_dirty(s_layer); }
static void battery_handler(BatteryChargeState state) { s_watch_battery = state.charge_percent; layer_mark_dirty(s_layer); }

#if defined(PBL_HEALTH)
static void refresh_hr(void) {
  HealthValue v = health_service_peek_current_value(HealthMetricHeartRateBPM);
  if (v > 0) s_hr = (int)v;
}
static void health_handler(HealthEventType event, void *context) { refresh_hr(); layer_mark_dirty(s_layer); }
#endif

static void inbox(DictionaryIterator *iter, void *context) {
  Tuple *phone = dict_find(iter, 1);
  Tuple *temp = dict_find(iter, 2);
  if (phone) s_phone_battery = (int)phone->value->int32;
  if (temp) s_temp = (int)temp->value->int32;
  layer_mark_dirty(s_layer);
}

static void window_load(Window *window) {
  C_ORANGE = GColorFromHEX(0xFF5500);
  C_MUTED = GColorFromHEX(0xAAAAAA);
  C_RED = GColorFromHEX(0xFF0000);
  C_BAR_BG = GColorFromHEX(0x330503);
  F_BIG = fonts_get_system_font(FONT_KEY_LECO_36_BOLD_NUMBERS);
  F_MED = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
  F_SMALL = fonts_get_system_font(FONT_KEY_GOTHIC_14);
  s_bg = gbitmap_create_with_resource(RESOURCE_ID_IMAGE_RED_GIRL_BASE);
  s_layer = layer_create(GRect(0,0,200,228));
  layer_set_update_proc(s_layer, canvas_update);
  layer_add_child(window_get_root_layer(window), s_layer);
}

static void window_unload(Window *window) {
  if (s_layer) layer_destroy(s_layer);
  if (s_bg) gbitmap_destroy(s_bg);
}

static void init(void) {
  s_window = window_create();
  window_set_window_handlers(s_window,(WindowHandlers){.load=window_load,.unload=window_unload});
  window_stack_push(s_window,true);
  tick_timer_service_subscribe(MINUTE_UNIT,tick_handler);
  battery_state_service_subscribe(battery_handler);
  battery_handler(battery_state_service_peek());
#if defined(PBL_HEALTH)
  refresh_hr();
  health_service_events_subscribe(health_handler,NULL);
#endif
  app_message_register_inbox_received(inbox);
  app_message_open(128,128);
}

static void deinit(void) {
  tick_timer_service_unsubscribe();
  battery_state_service_unsubscribe();
#if defined(PBL_HEALTH)
  health_service_events_unsubscribe();
#endif
  app_message_deregister_callbacks();
  window_destroy(s_window);
}

int main(void) { init(); app_event_loop(); deinit(); }

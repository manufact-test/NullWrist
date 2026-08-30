#include <pebble.h>

static Window *s_window;
static Layer *s_canvas;

static char s_time[8] = "14:37";
static char s_date[16] = "29 АВГ";
static char s_day[24] = "ПЯТНИЦА";
static int s_battery = 78;
static int s_phone_battery = 63;
static int s_hr = 78;
static int s_temp = 24;

static GColor C_BG;
static GColor C_PANEL;
static GColor C_PANEL_ALT;
static GColor C_BORDER;
static GColor C_ORANGE;
static GColor C_CREAM;
static GColor C_MUTED;
static GColor C_RED;
static GColor C_DARK_RED;
static GColor C_SKIN;
static GColor C_SKIN_DARK;
static GColor C_HAIR;
static GColor C_GLASS;

static GFont F9;
static GFont F14;
static GFont F18;
static GFont F30;

static void txt(GContext *ctx, const char *text, GFont font, GColor color,
                GRect box, GTextAlignment align) {
  graphics_context_set_text_color(ctx, color);
  graphics_draw_text(ctx, text, font, box, GTextOverflowModeWordWrap, align, NULL);
}

static void rr(GContext *ctx, GRect r, int radius, GColor fill, GColor stroke) {
  graphics_context_set_fill_color(ctx, fill);
  graphics_fill_round_rect(ctx, r, radius, GCornersAll);
  graphics_context_set_stroke_color(ctx, stroke);
  graphics_draw_round_rect(ctx, r, radius);
}

static void px_rect(GContext *ctx, int x, int y, int w, int h, GColor c) {
  graphics_context_set_fill_color(ctx, c);
  graphics_fill_rect(ctx, GRect(x, y, w, h), 0, GCornerNone);
}

static void draw_portrait(GContext *ctx) {
  /* background field */
  px_rect(ctx, 87, 0, 113, 182, GColorFromHEX(0x9B2312));
  px_rect(ctx, 94, 0, 106, 182, GColorFromHEX(0xB12A14));
  px_rect(ctx, 110, 0, 90, 182, GColorFromHEX(0xC73313));
  px_rect(ctx, 132, 0, 68, 182, GColorFromHEX(0xDC3A13));

  /* dither/noise */
  for (int y = 4; y < 178; y += 8) {
    for (int x = 96 + ((y / 8) % 2) * 4; x < 198; x += 12) {
      px_rect(ctx, x, y, 3, 3, ((x + y) % 24) ? C_DARK_RED : C_ORANGE);
    }
  }

  /* hair silhouette */
  px_rect(ctx, 102, 20, 55, 8, C_HAIR);
  px_rect(ctx, 94, 28, 70, 14, C_HAIR);
  px_rect(ctx, 88, 42, 83, 20, C_HAIR);
  px_rect(ctx, 84, 62, 92, 30, C_HAIR);
  px_rect(ctx, 87, 92, 82, 26, C_HAIR);
  px_rect(ctx, 95, 118, 60, 18, C_HAIR);
  px_rect(ctx, 108, 136, 36, 10, C_HAIR);

  /* skin */
  px_rect(ctx, 124, 44, 36, 12, C_SKIN);
  px_rect(ctx, 116, 56, 51, 20, C_SKIN);
  px_rect(ctx, 112, 76, 56, 27, C_SKIN);
  px_rect(ctx, 116, 103, 47, 20, C_SKIN);
  px_rect(ctx, 123, 123, 36, 18, C_SKIN);
  px_rect(ctx, 130, 141, 28, 18, C_SKIN_DARK);

  /* ear */
  px_rect(ctx, 102, 79, 12, 22, C_SKIN_DARK);
  px_rect(ctx, 105, 82, 6, 16, C_SKIN);
  px_rect(ctx, 108, 90, 3, 6, C_DARK_RED);
  px_rect(ctx, 108, 101, 4, 4, C_CREAM);

  /* facial shading */
  px_rect(ctx, 122, 64, 33, 5, C_SKIN_DARK);
  px_rect(ctx, 121, 69, 31, 6, GColorFromHEX(0xB94B29));
  px_rect(ctx, 125, 75, 26, 4, C_HAIR);
  px_rect(ctx, 151, 72, 12, 4, C_HAIR);
  px_rect(ctx, 145, 84, 14, 5, C_SKIN_DARK);
  px_rect(ctx, 149, 95, 11, 5, C_DARK_RED);
  px_rect(ctx, 147, 100, 14, 4, C_SKIN_DARK);

  /* neck / shoulder */
  px_rect(ctx, 118, 138, 41, 44, C_SKIN_DARK);
  px_rect(ctx, 102, 154, 57, 28, C_SKIN);
  px_rect(ctx, 86, 168, 48, 14, C_SKIN);
  px_rect(ctx, 130, 163, 29, 19, C_SKIN_DARK);

  /* glass */
  px_rect(ctx, 157, 70, 28, 8, GColorFromHEX(0xCFC8B0));
  px_rect(ctx, 153, 78, 37, 8, C_GLASS);
  px_rect(ctx, 149, 86, 44, 11, C_GLASS);
  px_rect(ctx, 151, 97, 45, 12, C_GLASS);
  px_rect(ctx, 156, 109, 40, 12, C_GLASS);
  px_rect(ctx, 162, 121, 33, 10, C_GLASS);
  px_rect(ctx, 169, 131, 24, 8, C_GLASS);
  graphics_context_set_stroke_color(ctx, C_CREAM);
  graphics_context_set_stroke_width(ctx, 2);
  graphics_draw_line(ctx, GPoint(156, 79), GPoint(191, 105));
  graphics_draw_line(ctx, GPoint(191, 105), GPoint(185, 132));
  graphics_draw_line(ctx, GPoint(185, 132), GPoint(162, 121));
  graphics_draw_line(ctx, GPoint(162, 121), GPoint(151, 93));
  graphics_draw_line(ctx, GPoint(151, 93), GPoint(156, 79));
  px_rect(ctx, 159, 109, 33, 13, GColorFromHEX(0xE7C9A0));

  /* hand + nails */
  px_rect(ctx, 181, 62, 9, 62, C_SKIN);
  px_rect(ctx, 190, 72, 8, 60, C_SKIN_DARK);
  px_rect(ctx, 176, 67, 11, 8, C_SKIN_DARK);
  px_rect(ctx, 179, 64, 8, 4, C_RED);
  px_rect(ctx, 188, 76, 7, 4, C_RED);
  px_rect(ctx, 194, 84, 5, 4, C_RED);

  /* drip */
  px_rect(ctx, 154, 119, 2, 11, C_CREAM);
  px_rect(ctx, 155, 130, 2, 13, C_CREAM);
  px_rect(ctx, 156, 144, 2, 17, C_CREAM);
}

static void draw_heart(GContext *ctx, int x, int y) {
  px_rect(ctx, x+3,y,5,5,C_RED);
  px_rect(ctx, x+10,y,5,5,C_RED);
  px_rect(ctx, x,y+4,18,7,C_RED);
  px_rect(ctx, x+3,y+11,12,4,C_RED);
  px_rect(ctx, x+6,y+15,6,4,C_RED);
}

static void draw_weather_icon(GContext *ctx, int x, int y) {
  px_rect(ctx, x+4,y,4,14,C_ORANGE);
  px_rect(ctx, x,y+5,13,4,C_ORANGE);
  px_rect(ctx, x+8,y+10,16,7,C_CREAM);
  px_rect(ctx, x+12,y+7,8,10,C_CREAM);
}

static void draw_watch(GContext *ctx, int x, int y) {
  graphics_context_set_stroke_color(ctx, C_RED);
  graphics_context_set_stroke_width(ctx, 2);
  graphics_draw_round_rect(ctx, GRect(x,y+3,11,20), 2);
  px_rect(ctx, x+3,y,5,3,C_RED);
  px_rect(ctx, x+3,y+23,5,3,C_RED);
}

static void draw_phone(GContext *ctx, int x, int y) {
  graphics_context_set_stroke_color(ctx, C_ORANGE);
  graphics_draw_round_rect(ctx, GRect(x,y,10,22), 1);
  px_rect(ctx, x+2,y+2,6,1,C_ORANGE);
}

static void draw_bar(GContext *ctx, GRect r, int value, GColor color) {
  px_rect(ctx, r.origin.x,r.origin.y,r.size.w,r.size.h,GColorFromHEX(0x40180F));
  int w = (r.size.w * CLAMP(value,0,100)) / 100;
  px_rect(ctx, r.origin.x,r.origin.y,w,r.size.h,color);
}

static void canvas_update(Layer *layer, GContext *ctx) {
  graphics_context_set_fill_color(ctx, C_BG);
  graphics_fill_rect(ctx, layer_get_bounds(layer), 0, GCornerNone);

  draw_portrait(ctx);

  /* dark left field */
  px_rect(ctx, 0,0,91,182,GColorFromHEX(0x160705));
  px_rect(ctx, 4,0,83,182,GColorFromHEX(0x210907));

  /* top accent */
  px_rect(ctx, 8,7,64,2,C_BORDER);
  px_rect(ctx, 8,7,33,2,C_ORANGE);

  txt(ctx, clock_is_24h_style()?"24H":"12H", F14, C_ORANGE, GRect(8,11,35,15), GTextAlignmentLeft);
  txt(ctx, s_time, F30, C_ORANGE, GRect(5,22,80,39), GTextAlignmentLeft);
  txt(ctx, s_date, F18, C_ORANGE, GRect(8,63,78,18), GTextAlignmentLeft);
  txt(ctx, s_day, F14, C_CREAM, GRect(8,80,80,16), GTextAlignmentLeft);

  rr(ctx,GRect(6,99,82,40),3,C_PANEL,C_BORDER);
  draw_heart(ctx,12,107);
  txt(ctx,"С ПУЛЬСОМ",F9,C_CREAM,GRect(40,102,45,11),GTextAlignmentLeft);
  txt(ctx,"ПОРЯДОК",F9,C_CREAM,GRect(40,111,45,11),GTextAlignmentLeft);
  char hrbuf[8]; snprintf(hrbuf,sizeof(hrbuf),"%d",s_hr);
  txt(ctx,hrbuf,F18,C_ORANGE,GRect(40,120,24,18),GTextAlignmentLeft);
  txt(ctx,"УД/МИН",F9,C_ORANGE,GRect(60,124,26,11),GTextAlignmentLeft);

  rr(ctx,GRect(6,143,82,36),3,C_PANEL_ALT,C_BORDER);
  draw_weather_icon(ctx,11,151);
  txt(ctx,"ПОГОДА",F9,C_CREAM,GRect(39,146,46,11),GTextAlignmentLeft);
  txt(ctx,"ВОЛШЕБНАЯ",F9,C_CREAM,GRect(39,155,47,11),GTextAlignmentLeft);
  char tbuf[8]; snprintf(tbuf,sizeof(tbuf),"%d°",s_temp);
  txt(ctx,tbuf,F18,C_ORANGE,GRect(39,163,38,16),GTextAlignmentLeft);

  px_rect(ctx,0,182,200,46,GColorFromHEX(0x120604));
  graphics_context_set_stroke_color(ctx,C_BORDER);
  graphics_draw_line(ctx,GPoint(0,182),GPoint(199,182));
  graphics_draw_line(ctx,GPoint(99,188),GPoint(99,220));

  draw_watch(ctx,8,189);
  txt(ctx,"ЧАСЫ",F14,C_MUTED,GRect(27,187,55,13),GTextAlignmentLeft);
  char bbuf[8]; snprintf(bbuf,sizeof(bbuf),"%d%%",s_battery);
  txt(ctx,bbuf,F18,C_ORANGE,GRect(27,197,55,18),GTextAlignmentLeft);
  draw_bar(ctx,GRect(27,216,57,3),s_battery,C_RED);

  draw_phone(ctx,107,190);
  txt(ctx,"ТЕЛЕФОН",F14,C_MUTED,GRect(126,187,68,13),GTextAlignmentLeft);
  char pbuf[8]; snprintf(pbuf,sizeof(pbuf),"%d%%",s_phone_battery);
  txt(ctx,pbuf,F18,C_ORANGE,GRect(126,197,58,18),GTextAlignmentLeft);
  draw_bar(ctx,GRect(126,216,64,3),s_phone_battery,C_ORANGE);
}

static void update_time(struct tm *t) {
  if (clock_is_24h_style()) strftime(s_time,sizeof(s_time),"%H:%M",t);
  else {
    strftime(s_time,sizeof(s_time),"%I:%M",t);
    if (s_time[0]=='0') memmove(s_time,s_time+1,strlen(s_time));
  }
  static const char *MON[]={"ЯНВ","ФЕВ","МАР","АПР","МАЙ","ИЮН","ИЮЛ","АВГ","СЕН","ОКТ","НОЯ","ДЕК"};
  static const char *DAY[]={"ВОСКРЕСЕНЬЕ","ПОНЕДЕЛЬНИК","ВТОРНИК","СРЕДА","ЧЕТВЕРГ","ПЯТНИЦА","СУББОТА"};
  snprintf(s_date,sizeof(s_date),"%d %s",t->tm_mday,MON[t->tm_mon]);
  snprintf(s_day,sizeof(s_day),"%s",DAY[t->tm_wday]);
}

static void tick_handler(struct tm *t, TimeUnits u){ update_time(t); layer_mark_dirty(s_canvas); }
static void battery_handler(BatteryChargeState s){ s_battery=s.charge_percent; layer_mark_dirty(s_canvas); }

#if defined(PBL_HEALTH)
static void update_hr(void){ HealthValue v=health_service_peek_current_value(HealthMetricHeartRateBPM); if(v>0)s_hr=(int)v; }
static void health_handler(HealthEventType e, void *c){ update_hr(); layer_mark_dirty(s_canvas); }
#endif

static void inbox(DictionaryIterator *iter, void *context){
  Tuple *t;
  if((t=dict_find(iter,1))) s_phone_battery=(int)t->value->int32;
  if((t=dict_find(iter,2))) s_temp=(int)t->value->int32;
  layer_mark_dirty(s_canvas);
}

static void window_load(Window *w){
  C_BG=GColorFromHEX(0x0D0D0F);
  C_PANEL=GColorFromHEX(0x35100A);
  C_PANEL_ALT=GColorFromHEX(0x45130B);
  C_BORDER=GColorFromHEX(0x8E2A15);
  C_ORANGE=GColorFromHEX(0xFF4D1A);
  C_CREAM=GColorFromHEX(0xE8D6B6);
  C_MUTED=GColorFromHEX(0xA68A72);
  C_RED=GColorFromHEX(0xE53316);
  C_DARK_RED=GColorFromHEX(0x7A1A0F);
  C_SKIN=GColorFromHEX(0xF47A2E);
  C_SKIN_DARK=GColorFromHEX(0xB74B27);
  C_HAIR=GColorFromHEX(0x140707);
  C_GLASS=GColorFromHEX(0x6D6259);
  F9=fonts_get_system_font(FONT_KEY_GOTHIC_09);
  F14=fonts_get_system_font(FONT_KEY_GOTHIC_14);
  F18=fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
  F30=fonts_get_system_font(FONT_KEY_BITHAM_30_BLACK);
  Layer *root=window_get_root_layer(w);
  s_canvas=layer_create(layer_get_bounds(root));
  layer_set_update_proc(s_canvas,canvas_update);
  layer_add_child(root,s_canvas);
}
static void window_unload(Window *w){ layer_destroy(s_canvas); }

static void init(void){
  s_window=window_create();
  window_set_window_handlers(s_window,(WindowHandlers){.load=window_load,.unload=window_unload});
  window_stack_push(s_window,true);
  time_t now=time(NULL); struct tm *t=localtime(&now); update_time(t);
  tick_timer_service_subscribe(MINUTE_UNIT,tick_handler);
  battery_state_service_subscribe(battery_handler); battery_handler(battery_state_service_peek());
#if defined(PBL_HEALTH)
  health_service_events_subscribe(health_handler,NULL); update_hr();
#endif
  app_message_register_inbox_received(inbox); app_message_open(128,128);
}
static void deinit(void){
  tick_timer_service_unsubscribe(); battery_state_service_unsubscribe();
#if defined(PBL_HEALTH)
  health_service_events_unsubscribe();
#endif
  app_message_deregister_callbacks(); window_destroy(s_window);
}
int main(void){ init(); app_event_loop(); deinit(); }

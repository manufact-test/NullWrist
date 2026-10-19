#include <pebble.h>

static Window *s_window;
static Layer *s_layer;
static GBitmap *s_bg;
static int s_watch_battery = 78;
static int s_phone_battery = 63;
static int s_hr = 78;
static int s_temp = 24;

static GFont F_TIME;
static GFont F_DATE;
static GFont F_DAY;
static GFont F_VALUE;
static GFont F_BATTERY;
static GFont F_SMALL;
static GFont F_TINY;
static GColor C_ORANGE;
static GColor C_MUTED;
static GColor C_RED;
static GColor C_BAR_BG;
static GColor C_BLACK;
static GColor C_CARD;

/* Measure against a full-width single line, not the small card. Otherwise the
 * Pebble text engine can replace the end of a value (even the minutes) with ... */
static bool text_fits(const char *text, GFont font, GRect r) {
  GSize measured = graphics_text_layout_get_content_size(
      text, font, GRect(0, 0, 200, r.size.h),
      GTextOverflowModeWordWrap, GTextAlignmentLeft);
  return measured.w <= r.size.w - 2 && measured.h <= r.size.h;
}

static void draw_text(GContext *ctx, const char *text, GFont preferred,
                      GFont smaller, GFont smallest, GColor color, GRect r) {
  GFont font = preferred;
  if (!text_fits(text, font, r)) {
    font = smaller;
  }
  if (!text_fits(text, font, r)) {
    font = smallest;
  }
  graphics_context_set_text_color(ctx, color);
  graphics_draw_text(ctx, text, font, r,
                     GTextOverflowModeWordWrap, GTextAlignmentLeft, NULL);
}

static void cover(GContext *ctx, GRect rect, GColor color) {
  graphics_context_set_fill_color(ctx, color);
  graphics_fill_rect(ctx, rect, 0, GCornerNone);
}

static void draw_bar(GContext *ctx, int x, int y, int w, int value, GColor fill) {
  int v = value < 0 ? 0 : (value > 100 ? 100 : value);
  graphics_context_set_fill_color(ctx, C_BAR_BG);
  graphics_fill_rect(ctx, GRect(x, y, w, 4), 0, GCornerNone);
  graphics_context_set_fill_color(ctx, fill);
  graphics_fill_rect(ctx, GRect(x, y, (w * v) / 100, 4), 0, GCornerNone);
}

static void canvas_update(Layer *layer, GContext *ctx) {
  if (s_bg) {
    graphics_draw_bitmap_in_rect(ctx, s_bg, GRect(0, 0, 200, 228));
  }

  /* Убираем старые подписи из фонового макета и освобождаем место под крупные значения. */
  cover(ctx, GRect(4, 3, 91, 15), C_BLACK);      /* 24H + верхняя полоска */
  cover(ctx, GRect(5, 17, 101, 47), C_BLACK);    /* старое время */
  cover(ctx, GRect(7, 64, 72, 29), C_BLACK);     /* дата / день */

  cover(ctx, GRect(31, 103, 40, 34), C_CARD);    /* текст пульса */
  cover(ctx, GRect(31, 143, 42, 34), C_CARD);    /* текст погоды */

  cover(ctx, GRect(29, 184, 58, 29), C_BLACK);   /* ЧАСЫ + старый процент */
  cover(ctx, GRect(118, 184, 62, 29), C_BLACK);  /* ТЕЛЕФОН + старый процент */

  time_t now = time(NULL);
  struct tm *t = localtime(&now);

  char timebuf[8];
  char datebuf[16];
  char daybuf[24];
  static const char *MON[] = {"ЯНВ", "ФЕВ", "МАР", "АПР", "МАЙ", "ИЮН", "ИЮЛ", "АВГ", "СЕН", "ОКТ", "НОЯ", "ДЕК"};
  static const char *DAY[] = {"ВОСКРЕСЕНЬЕ", "ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА"};
  static const char *DAY_SHORT[] = {"ВС", "ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ"};

  strftime(timebuf, sizeof(timebuf), "%H:%M", t);
  snprintf(datebuf, sizeof(datebuf), "%d %s", t->tm_mday, MON[t->tm_mon]);
  snprintf(daybuf, sizeof(daybuf), "%s", DAY[t->tm_wday]);

  /* Keep all five clock characters in the time box at every hour and minute. */
  draw_text(ctx, timebuf, F_TIME, F_VALUE, F_SMALL, C_ORANGE, GRect(5, 17, 111, 38));
  draw_text(ctx, datebuf, F_DATE, F_TINY, F_TINY, C_ORANGE, GRect(8, 54, 76, 22));

  GRect day_rect = GRect(8, 74, 88, 18);
  const char *day_text = daybuf;
  if (!text_fits(day_text, F_DAY, day_rect) &&
      !text_fits(day_text, F_TINY, day_rect)) {
    day_text = DAY_SHORT[t->tm_wday];
  }
  draw_text(ctx, day_text, F_DAY, F_TINY, F_TINY, C_MUTED, day_rect);

  char hr[8];
  char temp[8];
  char wb[8];
  char pb[8];
  snprintf(hr, sizeof(hr), "%d", s_hr);
  snprintf(temp, sizeof(temp), "%d°", s_temp);
  snprintf(wb, sizeof(wb), "%d%%", s_watch_battery);
  snprintf(pb, sizeof(pb), "%d%%", s_phone_battery);

  /* Scale crowded numeric values down rather than losing their last digit. */
  draw_text(ctx, hr, F_VALUE, F_SMALL, F_TINY, C_ORANGE, GRect(34, 104, 43, 34));
  draw_text(ctx, temp, F_VALUE, F_SMALL, F_TINY, C_ORANGE, GRect(34, 144, 45, 34));

  draw_text(ctx, wb, F_BATTERY, F_SMALL, F_TINY, C_ORANGE, GRect(31, 184, 58, 31));
  draw_text(ctx, pb, F_BATTERY, F_SMALL, F_TINY, C_ORANGE, GRect(120, 184, 61, 31));

  draw_bar(ctx, 33, 215, 55, s_watch_battery, C_RED);
  draw_bar(ctx, 121, 215, 60, s_phone_battery, C_ORANGE);
}

static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  layer_mark_dirty(s_layer);
}

static void battery_handler(BatteryChargeState state) {
  s_watch_battery = state.charge_percent;
  layer_mark_dirty(s_layer);
}

#if defined(PBL_HEALTH)
static void refresh_hr(void) {
  HealthValue v = health_service_peek_current_value(HealthMetricHeartRateBPM);
  if (v > 0) {
    s_hr = (int)v;
  }
}

static void health_handler(HealthEventType event, void *context) {
  refresh_hr();
  layer_mark_dirty(s_layer);
}
#endif

static void inbox(DictionaryIterator *iter, void *context) {
  Tuple *phone = dict_find(iter, 1);
  Tuple *temp = dict_find(iter, 2);
  if (phone) {
    s_phone_battery = (int)phone->value->int32;
  }
  if (temp) {
    s_temp = (int)temp->value->int32;
  }
  layer_mark_dirty(s_layer);
}

static void window_load(Window *window) {
  C_ORANGE = GColorFromHEX(0xFF5500);
  C_MUTED = GColorFromHEX(0xB8A39A);
  C_RED = GColorFromHEX(0xFF1A0A);
  C_BAR_BG = GColorFromHEX(0x330503);
  C_BLACK = GColorFromHEX(0x050303);
  C_CARD = GColorFromHEX(0x160604);

  F_TIME = fonts_get_system_font(FONT_KEY_LECO_26_BOLD_NUMBERS_AM_PM);
  F_DATE = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
  F_DAY = fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD);
  F_VALUE = fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD);
  F_BATTERY = fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD);
  F_SMALL = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
  F_TINY = fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD);

  s_bg = gbitmap_create_with_resource(RESOURCE_ID_IMAGE_RED_GIRL_BASE);
  s_layer = layer_create(GRect(0, 0, 200, 228));
  layer_set_update_proc(s_layer, canvas_update);
  layer_add_child(window_get_root_layer(window), s_layer);
}

static void window_unload(Window *window) {
  if (s_layer) {
    layer_destroy(s_layer);
  }
  if (s_bg) {
    gbitmap_destroy(s_bg);
  }
}

static void init(void) {
  s_window = window_create();
  window_set_window_handlers(s_window, (WindowHandlers){.load = window_load, .unload = window_unload});
  window_stack_push(s_window, true);

  tick_timer_service_subscribe(MINUTE_UNIT, tick_handler);
  battery_state_service_subscribe(battery_handler);
  battery_handler(battery_state_service_peek());

#if defined(PBL_HEALTH)
  refresh_hr();
  health_service_events_subscribe(health_handler, NULL);
#endif

  app_message_register_inbox_received(inbox);
  app_message_open(128, 128);
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

int main(void) {
  init();
  app_event_loop();
  deinit();
}

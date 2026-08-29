#include <pebble.h>

static Window *s_window;
static Layer *s_canvas;
static GFont s_font_regular;
static GFont s_font_bold;
static GBitmap *s_dog_bitmap;

static char s_time_text[16];
static char s_temp_text[12];
static char s_steps_text[16];
static char s_hr_text[12];

static int s_temp = 0;
static bool s_has_temp = false;
static bool s_use_24h = true;
static int s_watch_battery = 0;
static int s_phone_battery = -1;

static GColor s_bg;
static GColor s_bubble;
static GColor s_border;
static GColor s_text_color;
static GColor s_accent;
static GColor s_status_color;

enum {
  KEY_BG = 10,
  KEY_BUBBLE,
  KEY_BORDER,
  KEY_TEXT,
  KEY_ACCENT,
  KEY_STATUS,
  KEY_24H
};

static void defaults(void) {
  s_bg = GColorBlack;
  s_bubble = GColorWhite;
  s_border = GColorBlack;
  s_text_color = GColorBlack;
  s_accent = GColorOrange;
  s_status_color = GColorWhite;
  s_use_24h = true;

  if (persist_exists(KEY_BG)) s_bg.argb = persist_read_int(KEY_BG);
  if (persist_exists(KEY_BUBBLE)) s_bubble.argb = persist_read_int(KEY_BUBBLE);
  if (persist_exists(KEY_BORDER)) s_border.argb = persist_read_int(KEY_BORDER);
  if (persist_exists(KEY_TEXT)) s_text_color.argb = persist_read_int(KEY_TEXT);
  if (persist_exists(KEY_ACCENT)) s_accent.argb = persist_read_int(KEY_ACCENT);
  if (persist_exists(KEY_STATUS)) s_status_color.argb = persist_read_int(KEY_STATUS);
  if (persist_exists(KEY_24H)) s_use_24h = persist_read_bool(KEY_24H);
}

static int16_t text_width(const char *text, GFont font, int16_t max_w) {
  GSize size = graphics_text_layout_get_content_size(
    text,
    font,
    GRect(0, 0, max_w, 24),
    GTextOverflowModeFill,
    GTextAlignmentLeft
  );
  return size.w;
}

static int16_t draw_part(GContext *ctx, int16_t x, int16_t y, const char *text, GFont font, int16_t max_x, bool faux_bold) {
  int16_t w = text_width(text, font, max_x - x);
  if (w < 1) return x;

  graphics_draw_text(ctx, text, font, GRect(x, y, w + 4, 24), GTextOverflowModeFill, GTextAlignmentLeft, NULL);
  if (faux_bold) {
    graphics_draw_text(ctx, text, font, GRect(x + 1, y, w + 4, 24), GTextOverflowModeFill, GTextAlignmentLeft, NULL);
  }
  return x + w + (faux_bold ? 1 : 0);
}

static void compose(void) {
  time_t now = time(NULL);
  struct tm *t = localtime(&now);

  if (s_use_24h) {
    strftime(s_time_text, sizeof(s_time_text), "%H:%M", t);
  } else {
    strftime(s_time_text, sizeof(s_time_text), "%I:%M %p", t);
  }

  uint32_t steps = 0;
  uint32_t hr = 0;

#if defined(PBL_HEALTH)
  HealthServiceAccessibilityMask sm = health_service_metric_accessible(
    HealthMetricStepCount,
    time_start_of_today(),
    now
  );
  if (sm & HealthServiceAccessibilityMaskAvailable) {
    steps = (uint32_t)health_service_sum_today(HealthMetricStepCount);
  }

  HealthServiceAccessibilityMask hm = health_service_metric_accessible(
    HealthMetricHeartRateBPM,
    now,
    now
  );
  if (hm & HealthServiceAccessibilityMaskAvailable) {
    HealthValue current_hr = health_service_peek_current_value(HealthMetricHeartRateBPM);
    if (current_hr > 0) hr = (uint32_t)current_hr;
  }
#endif

  if (s_has_temp) snprintf(s_temp_text, sizeof(s_temp_text), "%d", s_temp);
  else snprintf(s_temp_text, sizeof(s_temp_text), "--");

  snprintf(s_steps_text, sizeof(s_steps_text), "%lu", (unsigned long)steps);
  if (hr) snprintf(s_hr_text, sizeof(s_hr_text), "%lu", (unsigned long)hr);
  else snprintf(s_hr_text, sizeof(s_hr_text), "--");

  if (s_canvas) layer_mark_dirty(s_canvas);
}

static void draw_watch_icon(GContext *ctx, int16_t x, int16_t y) {
  graphics_context_set_stroke_color(ctx, s_status_color);
  graphics_context_set_fill_color(ctx, s_status_color);
  graphics_fill_rect(ctx, GRect(x + 4, y, 4, 3), 0, GCornerNone);
  graphics_draw_rect(ctx, GRect(x + 1, y + 3, 10, 11));
  graphics_fill_rect(ctx, GRect(x + 4, y + 14, 4, 3), 0, GCornerNone);
}

static void draw_phone_icon(GContext *ctx, int16_t x, int16_t y) {
  graphics_context_set_stroke_color(ctx, s_status_color);
  graphics_draw_rect(ctx, GRect(x + 1, y, 10, 17));
  graphics_context_set_fill_color(ctx, s_status_color);
  graphics_fill_rect(ctx, GRect(x + 4, y + 14, 4, 1), 0, GCornerNone);
}

static void draw_status_bar(GContext *ctx, GRect b) {
  const int16_t bar_y = b.size.h - 53;

  graphics_context_set_fill_color(ctx, GColorBlack);
  graphics_fill_rect(ctx, GRect(0, bar_y, b.size.w, 53), 0, GCornerNone);

  if (s_dog_bitmap) {
    graphics_draw_bitmap_in_rect(ctx, s_dog_bitmap, GRect(7, bar_y + 5, 52, 44));
  }

  graphics_context_set_text_color(ctx, s_status_color);

  char watch_text[8];
  char phone_text[8];
  snprintf(watch_text, sizeof(watch_text), "%d%%", s_watch_battery);
  if (s_phone_battery >= 0) snprintf(phone_text, sizeof(phone_text), "%d%%", s_phone_battery);
  else snprintf(phone_text, sizeof(phone_text), "--%%");

  draw_watch_icon(ctx, 69, bar_y + 17);
  graphics_draw_text(ctx, watch_text, s_font_bold, GRect(84, bar_y + 10, 48, 24), GTextOverflowModeFill, GTextAlignmentLeft, NULL);

  draw_phone_icon(ctx, 137, bar_y + 17);
  graphics_draw_text(ctx, phone_text, s_font_bold, GRect(152, bar_y + 10, 46, 24), GTextOverflowModeFill, GTextAlignmentLeft, NULL);

  graphics_context_set_stroke_color(ctx, s_status_color);
  graphics_draw_line(ctx, GPoint(65, bar_y + 8), GPoint(65, bar_y + 45));
  graphics_draw_line(ctx, GPoint(133, bar_y + 8), GPoint(133, bar_y + 45));
}

static void canvas_update(Layer *layer, GContext *ctx) {
  GRect b = layer_get_bounds(layer);

  graphics_context_set_fill_color(ctx, s_bg);
  graphics_fill_rect(ctx, b, 0, GCornerNone);

  GRect bubble = GRect(7, 7, b.size.w - 14, b.size.h - 64);
  graphics_context_set_fill_color(ctx, s_bubble);
  graphics_fill_rect(ctx, bubble, 2, GCornersAll);

  graphics_context_set_stroke_color(ctx, s_border);
  graphics_context_set_stroke_width(ctx, 2);
  graphics_draw_rect(ctx, bubble);

  int16_t tail_y = bubble.origin.y + bubble.size.h - 1;
  graphics_context_set_fill_color(ctx, s_bubble);
  graphics_fill_rect(ctx, GRect(19, tail_y, 12, 3), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(21, tail_y + 3, 8, 3), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(23, tail_y + 6, 4, 4), 0, GCornerNone);

  graphics_context_set_stroke_color(ctx, s_border);
  graphics_draw_line(ctx, GPoint(18, tail_y), GPoint(23, tail_y + 10));
  graphics_draw_line(ctx, GPoint(31, tail_y), GPoint(27, tail_y + 10));

  graphics_context_set_text_color(ctx, s_text_color);

  const int16_t left = 14;
  const int16_t max_x = b.size.w - 12;
  const int16_t line = 18;
  int16_t y = 11;
  int16_t x;

  draw_part(ctx, left, y, "Привет! Время сейчас:", s_font_regular, max_x, false);
  y += line;
  draw_part(ctx, left, y, s_time_text, s_font_bold, max_x, true);
  y += line;
  draw_part(ctx, left, y, s_has_temp ? "Погода как и ожидалось:" : "Погода пока неизвестна:", s_font_regular, max_x, false);
  y += line;
  x = draw_part(ctx, left, y, s_temp_text, s_font_bold, max_x, true);
  draw_part(ctx, x + 2, y, "° Цельсия", s_font_regular, max_x, false);
  y += line;
  x = draw_part(ctx, left, y, "Прошел ты уже ", s_font_regular, max_x, false);
  draw_part(ctx, x, y, s_steps_text, s_font_bold, max_x, true);
  y += line;
  draw_part(ctx, left, y, "шагов. Пульс сейчас:", s_font_regular, max_x, false);
  y += line;
  x = draw_part(ctx, left, y, s_hr_text, s_font_bold, max_x, true);
  draw_part(ctx, x + 2, y, " ударов", s_font_regular, max_x, false);
  y += line;
  draw_part(ctx, left, y, "Хорошего дня!", s_font_regular, max_x, false);

  draw_status_bar(ctx, b);
}

static void request_weather(void) {
  DictionaryIterator *it;
  if (app_message_outbox_begin(&it) == APP_MSG_OK) {
    dict_write_uint8(it, MESSAGE_KEY_RequestWeather, 1);
    app_message_outbox_send();
  }
}

static void tick(struct tm *tick_time, TimeUnits units) {
  compose();
  if (tick_time->tm_min % 30 == 0) request_weather();
}

static void health(HealthEventType type, void *ctx) {
  compose();
}

static void battery_handler(BatteryChargeState state) {
  s_watch_battery = state.charge_percent;
  if (s_canvas) layer_mark_dirty(s_canvas);
}

static void inbox(DictionaryIterator *it, void *ctx) {
  Tuple *t;

  if ((t = dict_find(it, MESSAGE_KEY_Temperature))) {
    s_temp = t->value->int32;
    s_has_temp = true;
  }
  if ((t = dict_find(it, MESSAGE_KEY_PhoneBattery))) {
    s_phone_battery = t->value->int32;
  }
  if ((t = dict_find(it, MESSAGE_KEY_BackgroundColor))) {
    s_bg.argb = t->value->int32;
    persist_write_int(KEY_BG, s_bg.argb);
  }
  if ((t = dict_find(it, MESSAGE_KEY_BubbleColor))) {
    s_bubble.argb = t->value->int32;
    persist_write_int(KEY_BUBBLE, s_bubble.argb);
  }
  if ((t = dict_find(it, MESSAGE_KEY_BorderColor))) {
    s_border.argb = t->value->int32;
    persist_write_int(KEY_BORDER, s_border.argb);
  }
  if ((t = dict_find(it, MESSAGE_KEY_TextColor))) {
    s_text_color.argb = t->value->int32;
    persist_write_int(KEY_TEXT, s_text_color.argb);
  }
  if ((t = dict_find(it, MESSAGE_KEY_AccentColor))) {
    s_accent.argb = t->value->int32;
    persist_write_int(KEY_ACCENT, s_accent.argb);
  }
  if ((t = dict_find(it, MESSAGE_KEY_StatusColor))) {
    s_status_color.argb = t->value->int32;
    persist_write_int(KEY_STATUS, s_status_color.argb);
  }
  if ((t = dict_find(it, MESSAGE_KEY_Use24h))) {
    s_use_24h = t->value->int32;
    persist_write_bool(KEY_24H, s_use_24h);
  }

  compose();
}

static void load(Window *w) {
  Layer *root = window_get_root_layer(w);
  s_canvas = layer_create(layer_get_bounds(root));
  layer_set_update_proc(s_canvas, canvas_update);
  layer_add_child(root, s_canvas);

  s_font_regular = fonts_load_custom_font(resource_get_handle(RESOURCE_ID_FONT_DIALOG_15));
  s_font_bold = fonts_load_custom_font(resource_get_handle(RESOURCE_ID_FONT_DIALOG_BOLD_17));
  s_dog_bitmap = gbitmap_create_with_resource(RESOURCE_ID_IMAGE_GREYHOUND);

  compose();
}

static void unload(Window *w) {
  if (s_dog_bitmap) gbitmap_destroy(s_dog_bitmap);
  fonts_unload_custom_font(s_font_regular);
  fonts_unload_custom_font(s_font_bold);
  layer_destroy(s_canvas);
}

static void init(void) {
  defaults();

  s_window = window_create();
  window_set_window_handlers(s_window, (WindowHandlers){
    .load = load,
    .unload = unload
  });
  window_stack_push(s_window, true);

  tick_timer_service_subscribe(MINUTE_UNIT, tick);
  health_service_events_subscribe(health, NULL);
  battery_state_service_subscribe(battery_handler);
  battery_handler(battery_state_service_peek());

  app_message_register_inbox_received(inbox);
  app_message_open(512, 128);

  request_weather();
}

static void deinit(void) {
  tick_timer_service_unsubscribe();
  health_service_events_unsubscribe();
  battery_state_service_unsubscribe();
  window_destroy(s_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}

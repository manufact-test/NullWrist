#include <pebble.h>

static Window *s_window;
static Layer *s_canvas;
static GFont s_font_regular;
static GFont s_font_bold;

static char s_time_text[16];
static char s_temp_text[12];
static char s_steps_text[16];
static char s_hr_text[12];

static int s_temp = 0;
static bool s_has_temp = false;
static bool s_use_24h = true;
static GColor s_bg, s_bubble, s_border, s_text_color, s_accent;

enum { KEY_BG=10, KEY_BUBBLE, KEY_BORDER, KEY_TEXT, KEY_ACCENT, KEY_24H };

static void defaults(void) {
  s_bg = GColorBlack;
  s_bubble = GColorWhite;
  s_border = GColorBlack;
  s_text_color = GColorBlack;
  s_accent = GColorOrange;
  s_use_24h = true;

  if (persist_exists(KEY_BG)) s_bg.argb = persist_read_int(KEY_BG);
  if (persist_exists(KEY_BUBBLE)) s_bubble.argb = persist_read_int(KEY_BUBBLE);
  if (persist_exists(KEY_BORDER)) s_border.argb = persist_read_int(KEY_BORDER);
  if (persist_exists(KEY_TEXT)) s_text_color.argb = persist_read_int(KEY_TEXT);
  if (persist_exists(KEY_ACCENT)) s_accent.argb = persist_read_int(KEY_ACCENT);
  if (persist_exists(KEY_24H)) s_use_24h = persist_read_bool(KEY_24H);
}

static void draw_path(GContext *ctx, const GPoint *points, uint32_t count, GPoint offset, GColor color) {
  GPathInfo info = { .num_points = count, .points = (GPoint *)points };
  GPath *path = gpath_create(&info);
  if (!path) return;
  gpath_move_to(path, offset);
  graphics_context_set_fill_color(ctx, color);
  gpath_draw_filled(ctx, path);
  gpath_destroy(path);
}

static void draw_dog(GContext *ctx, GPoint o) {
  /* Отдельный pixel-art портрет грейхаунда в профиль: длинная морда, розовидные уши, тигровый окрас. */
  static const GPoint EAR_BACK[] = {{10,5},{17,0},{21,4},{20,17},{15,19},{11,13}};
  static const GPoint EAR_FRONT[] = {{22,4},{29,1},{33,5},{29,18},{24,19},{22,13}};
  static const GPoint HEAD_OUTLINE[] = {{11,15},{17,11},{29,11},{36,15},{40,21},{46,25},{58,28},{63,32},{61,37},{54,40},{42,40},{35,37},{31,42},{28,50},{17,50},{18,40},{11,35},{7,29},{7,21}};
  static const GPoint HEAD_FILL[] = {{12,17},{18,14},{28,14},{34,17},{38,23},{45,28},{57,31},{59,33},{58,36},{52,37},{41,37},{34,34},{29,39},{26,47},{20,47},{21,38},{14,33},{10,28},{10,22}};
  static const GPoint MUZZLE[] = {{37,27},{46,30},{57,32},{59,34},{56,36},{47,35},{39,33}};
  static const GPoint MUZZLE_WHITE[] = {{49,34},{58,33},{59,35},{55,38},{49,37}};
  static const GPoint NECK_WHITE[] = {{20,36},{28,38},{26,47},{21,47}};
  static const GPoint STRIPE_1[] = {{14,18},{18,15},{20,15},{18,24},{14,26}};
  static const GPoint STRIPE_2[] = {{22,14},{26,14},{24,24},{21,26}};
  static const GPoint STRIPE_3[] = {{28,15},{32,17},{29,26},{26,27}};
  static const GPoint STRIPE_4[] = {{13,28},{18,27},{22,34},{18,36}};
  static const GPoint STRIPE_5[] = {{25,28},{30,26},{33,33},{30,35}};
  static const GPoint STRIPE_6[] = {{34,24},{38,24},{40,30},{36,31}};

  GColor outline = GColorBlack;
  GColor tan = GColorRajah;
  GColor light = GColorMelon;
  GColor cream = GColorPastelYellow;
  GColor dark = GColorDarkCandyAppleRed;

  draw_path(ctx, EAR_BACK, ARRAY_LENGTH(EAR_BACK), o, outline);
  draw_path(ctx, EAR_FRONT, ARRAY_LENGTH(EAR_FRONT), o, outline);
  draw_path(ctx, HEAD_OUTLINE, ARRAY_LENGTH(HEAD_OUTLINE), o, outline);
  draw_path(ctx, HEAD_FILL, ARRAY_LENGTH(HEAD_FILL), o, tan);
  draw_path(ctx, MUZZLE, ARRAY_LENGTH(MUZZLE), o, light);
  draw_path(ctx, MUZZLE_WHITE, ARRAY_LENGTH(MUZZLE_WHITE), o, cream);
  draw_path(ctx, NECK_WHITE, ARRAY_LENGTH(NECK_WHITE), o, cream);

  draw_path(ctx, STRIPE_1, ARRAY_LENGTH(STRIPE_1), o, dark);
  draw_path(ctx, STRIPE_2, ARRAY_LENGTH(STRIPE_2), o, dark);
  draw_path(ctx, STRIPE_3, ARRAY_LENGTH(STRIPE_3), o, dark);
  draw_path(ctx, STRIPE_4, ARRAY_LENGTH(STRIPE_4), o, dark);
  draw_path(ctx, STRIPE_5, ARRAY_LENGTH(STRIPE_5), o, dark);
  draw_path(ctx, STRIPE_6, ARRAY_LENGTH(STRIPE_6), o, dark);

  /* Внутренняя часть ушей */
  graphics_context_set_fill_color(ctx, dark);
  graphics_fill_rect(ctx, GRect(o.x+15,o.y+5,3,10),0,GCornerNone);
  graphics_fill_rect(ctx, GRect(o.x+25,o.y+6,4,9),0,GCornerNone);

  /* Глаз с бликом */
  graphics_context_set_fill_color(ctx, dark);
  graphics_fill_rect(ctx, GRect(o.x+30,o.y+20,6,4),0,GCornerNone);
  graphics_context_set_fill_color(ctx, GColorBlack);
  graphics_fill_rect(ctx, GRect(o.x+32,o.y+20,3,3),0,GCornerNone);
  graphics_context_set_fill_color(ctx, GColorWhite);
  graphics_fill_rect(ctx, GRect(o.x+33,o.y+20,1,1),0,GCornerNone);

  /* Нос и линия пасти */
  graphics_context_set_fill_color(ctx, GColorBlack);
  graphics_fill_rect(ctx, GRect(o.x+58,o.y+31,5,6),0,GCornerNone);
  graphics_context_set_stroke_color(ctx, outline);
  graphics_context_set_stroke_width(ctx, 1);
  graphics_draw_line(ctx, GPoint(o.x+43,o.y+35), GPoint(o.x+56,o.y+37));

  /* Ошейник и жетон берут акцентный цвет из настроек */
  graphics_context_set_fill_color(ctx, outline);
  graphics_fill_rect(ctx, GRect(o.x+17,o.y+39,14,4),0,GCornerNone);
  graphics_context_set_fill_color(ctx, s_accent);
  graphics_fill_rect(ctx, GRect(o.x+18,o.y+40,12,2),0,GCornerNone);
  graphics_fill_rect(ctx, GRect(o.x+23,o.y+42,3,4),0,GCornerNone);
}

static int16_t text_width(const char *text, GFont font, int16_t max_w) {
  GSize size = graphics_text_layout_get_content_size(text, font, GRect(0,0,max_w,24), GTextOverflowModeFill, GTextAlignmentLeft);
  return size.w;
}

static int16_t draw_part(GContext *ctx, int16_t x, int16_t y, const char *text, GFont font, int16_t max_x, bool faux_bold) {
  int16_t w = text_width(text, font, max_x - x);
  if (w < 1) return x;
  graphics_draw_text(ctx, text, font, GRect(x,y,w+3,23), GTextOverflowModeFill, GTextAlignmentLeft, NULL);
  if (faux_bold) {
    graphics_draw_text(ctx, text, font, GRect(x+1,y,w+3,23), GTextOverflowModeFill, GTextAlignmentLeft, NULL);
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
  HealthServiceAccessibilityMask sm = health_service_metric_accessible(HealthMetricStepCount, time_start_of_today(), now);
  if (sm & HealthServiceAccessibilityMaskAvailable) {
    steps = (uint32_t)health_service_sum_today(HealthMetricStepCount);
  }

  HealthServiceAccessibilityMask hm = health_service_metric_accessible(HealthMetricHeartRateBPM, now, now);
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

static void canvas_update(Layer *layer, GContext *ctx) {
  GRect b = layer_get_bounds(layer);
  graphics_context_set_fill_color(ctx, s_bg);
  graphics_fill_rect(ctx, b, 0, GCornerNone);

  GRect bubble = GRect(7,7,b.size.w-14,b.size.h-64);
  graphics_context_set_fill_color(ctx, s_bubble);
  graphics_fill_rect(ctx, bubble, 2, GCornersAll);
  graphics_context_set_stroke_color(ctx, s_border);
  graphics_context_set_stroke_width(ctx, 2);
  graphics_draw_rect(ctx, bubble);

  /* Пиксельный хвост диалога направлен прямо к голове собаки. */
  int16_t tail_y = bubble.origin.y + bubble.size.h - 1;
  graphics_context_set_fill_color(ctx, s_bubble);
  graphics_fill_rect(ctx, GRect(19,tail_y,12,3),0,GCornerNone);
  graphics_fill_rect(ctx, GRect(21,tail_y+3,8,3),0,GCornerNone);
  graphics_fill_rect(ctx, GRect(23,tail_y+6,4,4),0,GCornerNone);
  graphics_context_set_stroke_color(ctx, s_border);
  graphics_draw_line(ctx, GPoint(18,tail_y), GPoint(23,tail_y+10));
  graphics_draw_line(ctx, GPoint(31,tail_y), GPoint(27,tail_y+10));

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
  draw_part(ctx, x+2, y, "° Цельсия", s_font_regular, max_x, false);
  y += line;
  x = draw_part(ctx, left, y, "Прошел ты уже ", s_font_regular, max_x, false);
  draw_part(ctx, x, y, s_steps_text, s_font_bold, max_x, true);
  y += line;
  draw_part(ctx, left, y, "шагов. Пульс сейчас:", s_font_regular, max_x, false);
  y += line;
  x = draw_part(ctx, left, y, s_hr_text, s_font_bold, max_x, true);
  draw_part(ctx, x+2, y, " ударов", s_font_regular, max_x, false);
  y += line;
  draw_part(ctx, left, y, "Хорошего дня!", s_font_regular, max_x, false);

  draw_dog(ctx, GPoint(8,b.size.h-56));
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

static void inbox(DictionaryIterator *it, void *ctx) {
  Tuple *t;

  if ((t=dict_find(it,MESSAGE_KEY_Temperature))) {
    s_temp=t->value->int32;
    s_has_temp=true;
  }
  if ((t=dict_find(it,MESSAGE_KEY_BackgroundColor))) {
    s_bg.argb=t->value->int32;
    persist_write_int(KEY_BG,s_bg.argb);
  }
  if ((t=dict_find(it,MESSAGE_KEY_BubbleColor))) {
    s_bubble.argb=t->value->int32;
    persist_write_int(KEY_BUBBLE,s_bubble.argb);
  }
  if ((t=dict_find(it,MESSAGE_KEY_BorderColor))) {
    s_border.argb=t->value->int32;
    persist_write_int(KEY_BORDER,s_border.argb);
  }
  if ((t=dict_find(it,MESSAGE_KEY_TextColor))) {
    s_text_color.argb=t->value->int32;
    persist_write_int(KEY_TEXT,s_text_color.argb);
  }
  if ((t=dict_find(it,MESSAGE_KEY_AccentColor))) {
    s_accent.argb=t->value->int32;
    persist_write_int(KEY_ACCENT,s_accent.argb);
  }
  if ((t=dict_find(it,MESSAGE_KEY_Use24h))) {
    s_use_24h=t->value->int32;
    persist_write_bool(KEY_24H,s_use_24h);
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
  compose();
}

static void unload(Window *w) {
  fonts_unload_custom_font(s_font_regular);
  fonts_unload_custom_font(s_font_bold);
  layer_destroy(s_canvas);
}

static void init(void) {
  defaults();
  s_window = window_create();
  window_set_window_handlers(s_window, (WindowHandlers){.load=load,.unload=unload});
  window_stack_push(s_window, true);

  tick_timer_service_subscribe(MINUTE_UNIT, tick);
  health_service_events_subscribe(health, NULL);
  app_message_register_inbox_received(inbox);
  app_message_open(512,128);
  request_weather();
}

static void deinit(void) {
  tick_timer_service_unsubscribe();
  health_service_events_unsubscribe();
  window_destroy(s_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}

#include <pebble.h>

static Window *s_window;
static Layer *s_canvas;
static GFont s_font;
static char s_text[420];
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

static void draw_dog(GContext *ctx, GPoint o) {
  GColor tan = GColorRajah;
  GColor dark = GColorDarkCandyAppleRed;
  GColor cream = GColorPastelYellow;

  graphics_context_set_fill_color(ctx, tan);
  graphics_fill_rect(ctx, GRect(o.x+5,o.y+2,18,22),0,GCornerNone);
  graphics_fill_rect(ctx, GRect(o.x+12,o.y+18,22,10),0,GCornerNone);
  graphics_fill_rect(ctx, GRect(o.x+22,o.y+24,16,7),0,GCornerNone);
  graphics_fill_rect(ctx, GRect(o.x+3,o.y,7,11),0,GCornerNone);
  graphics_fill_rect(ctx, GRect(o.x+19,o.y,7,10),0,GCornerNone);

  graphics_context_set_fill_color(ctx, dark);
  graphics_fill_rect(ctx,GRect(o.x+7,o.y+5,4,3),0,GCornerNone);
  graphics_fill_rect(ctx,GRect(o.x+15,o.y+2,4,5),0,GCornerNone);
  graphics_fill_rect(ctx,GRect(o.x+11,o.y+13,5,3),0,GCornerNone);
  graphics_fill_rect(ctx,GRect(o.x+19,o.y+17,4,4),0,GCornerNone);
  graphics_fill_rect(ctx,GRect(o.x+29,o.y+24,5,4),0,GCornerNone);

  graphics_context_set_fill_color(ctx, cream);
  graphics_fill_rect(ctx,GRect(o.x+14,o.y+18,7,6),0,GCornerNone);

  graphics_context_set_fill_color(ctx, GColorBlack);
  graphics_fill_rect(ctx,GRect(o.x+20,o.y+8,2,2),0,GCornerNone);
  graphics_fill_rect(ctx,GRect(o.x+36,o.y+26,3,3),0,GCornerNone);

  graphics_context_set_fill_color(ctx, s_accent);
  graphics_fill_rect(ctx,GRect(o.x+8,o.y+25,16,3),0,GCornerNone);
}

static void compose(void) {
  time_t now = time(NULL);
  struct tm *t = localtime(&now);
  char tm_buf[16];

  if (s_use_24h) {
    strftime(tm_buf,sizeof(tm_buf),"%H:%M",t);
  } else {
    char base[8];
    strftime(base,sizeof(base),"%I:%M",t);
    snprintf(tm_buf,sizeof(tm_buf),"%s %s",base,t->tm_hour < 12 ? "дп" : "пп");
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

  char weather[60];
  char pulse[64];

  if (s_has_temp) snprintf(weather,sizeof(weather),"погода как и ожидалось %d° Цельсия.",s_temp);
  else snprintf(weather,sizeof(weather),"погода пока неизвестна.");

  if (hr) snprintf(pulse,sizeof(pulse),"Пульс сейчас %lu ударов.",(unsigned long)hr);
  else snprintf(pulse,sizeof(pulse),"Пульс пока неизвестен.");

  snprintf(s_text,sizeof(s_text),"Привет! Время сейчас %s, %s\n\nПрошел ты уже %lu шагов. %s\n\nХорошего дня!",tm_buf,weather,(unsigned long)steps,pulse);
  if (s_canvas) layer_mark_dirty(s_canvas);
}

static void request_weather(void) {
  DictionaryIterator *it;
  if (app_message_outbox_begin(&it) == APP_MSG_OK) {
    dict_write_uint8(it, MESSAGE_KEY_RequestWeather, 1);
    app_message_outbox_send();
  }
}

static void canvas_update(Layer *layer, GContext *ctx) {
  GRect b = layer_get_bounds(layer);
  graphics_context_set_fill_color(ctx,s_bg);
  graphics_fill_rect(ctx,b,0,GCornerNone);

  GRect bubble = GRect(8,8,b.size.w-16,b.size.h-49);
  graphics_context_set_fill_color(ctx,s_bubble);
  graphics_fill_rect(ctx,bubble,2,GCornersAll);

  graphics_context_set_stroke_color(ctx,s_border);
  graphics_context_set_stroke_width(ctx,2);
  graphics_draw_rect(ctx,bubble);

  int tail_y = bubble.origin.y + bubble.size.h - 1;
  graphics_context_set_fill_color(ctx,s_bubble);
  graphics_fill_rect(ctx,GRect(18,tail_y,12,3),0,GCornerNone);
  graphics_fill_rect(ctx,GRect(20,tail_y+3,8,3),0,GCornerNone);
  graphics_fill_rect(ctx,GRect(22,tail_y+6,4,3),0,GCornerNone);
  graphics_context_set_stroke_color(ctx,s_border);
  graphics_draw_line(ctx,GPoint(18,tail_y),GPoint(22,tail_y+9));
  graphics_draw_line(ctx,GPoint(30,tail_y),GPoint(26,tail_y+9));

  graphics_context_set_text_color(ctx,s_text_color);
  graphics_draw_text(ctx,s_text,s_font,GRect(15,14,b.size.w-30,bubble.size.h-10),GTextOverflowModeTrailingEllipsis,GTextAlignmentLeft,NULL);
  draw_dog(ctx,GPoint(8,b.size.h-39));
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
  Layer *root=window_get_root_layer(w);
  s_canvas=layer_create(layer_get_bounds(root));
  layer_set_update_proc(s_canvas,canvas_update);
  layer_add_child(root,s_canvas);
  s_font=fonts_load_custom_font(resource_get_handle(RESOURCE_ID_FONT_DIALOG_14));
  compose();
}

static void unload(Window *w) {
  fonts_unload_custom_font(s_font);
  layer_destroy(s_canvas);
}

static void init(void) {
  defaults();
  s_window=window_create();
  window_set_window_handlers(s_window,(WindowHandlers){.load=load,.unload=unload});
  window_stack_push(s_window,true);
  tick_timer_service_subscribe(MINUTE_UNIT,tick);
  health_service_events_subscribe(health,NULL);
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

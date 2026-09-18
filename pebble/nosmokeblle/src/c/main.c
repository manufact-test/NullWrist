#include <pebble.h>
#include "storage.h"
#include "ui.h"
static AppState s_state;
static void init(void){storage_load(&s_state);ui_init(&s_state);}
static void deinit(void){ui_deinit();}
int main(void){init();app_event_loop();deinit();}

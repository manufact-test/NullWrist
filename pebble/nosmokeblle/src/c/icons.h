#pragma once
#include <pebble.h>
#include <pebble_fonts.h>
typedef enum { ICON_CIG, ICON_MONEY, ICON_CLOCK, ICON_LOCK, ICON_CHECK, ICON_BURST, ICON_UP, ICON_DOWN, ICON_SELECT } PixelIcon;
void icon_draw(GContext *ctx,PixelIcon icon,GPoint p,int scale,GColor color);

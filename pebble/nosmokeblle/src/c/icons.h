#pragma once
#include <pebble.h>
typedef enum { ICON_CIG, ICON_MONEY, ICON_CLOCK, ICON_LOCK, ICON_CHECK, ICON_BURST, ICON_UP, ICON_DOWN, ICON_SELECT } PixelIcon;
void icon_draw(GraphicsContext *ctx,PixelIcon icon,GPoint p,int scale,GColor color);

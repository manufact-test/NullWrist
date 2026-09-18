#pragma once
#include <pebble.h>
typedef enum { PF_ALIGN_LEFT, PF_ALIGN_CENTER, PF_ALIGN_RIGHT } PixelAlign;
int pixel_font_measure(const char *text,int scale);
int pixel_font_fit_scale(const char *text,int max_width,int preferred,int min_scale);
void pixel_font_draw(GraphicsContext *ctx,const char *text,GRect box,int scale,GColor color,PixelAlign align);
void pixel_font_draw_wrapped(GraphicsContext *ctx,const char *text,GRect box,int scale,GColor color,PixelAlign align,int line_gap);

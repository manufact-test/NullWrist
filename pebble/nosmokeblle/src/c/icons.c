#include "icons.h"
static void px(GraphicsContext*c,int x,int y,int s,GColor col){graphics_context_set_fill_color(c,col);graphics_fill_rect(c,GRect(x,y,s,s),0,GCornerNone);}
void icon_draw(GraphicsContext*c,PixelIcon i,GPoint p,int s,GColor col){static const uint8_t a[][7]={{0,0,0,30,1,0,0},{4,15,20,14,5,30,4},{14,17,21,21,19,17,14},{14,17,17,31,27,27,31},{0,1,2,20,8,8,0},{4,21,14,31,14,21,4},{4,14,21,4,4,4,4},{4,4,4,4,21,14,4},{0,14,17,21,17,14,0}};for(int y=0;y<7;y++)for(int x=0;x<5;x++)if(a[i][y]&(1<<(4-x)))px(c,p.x+x*s,p.y+y*s,s,col);}

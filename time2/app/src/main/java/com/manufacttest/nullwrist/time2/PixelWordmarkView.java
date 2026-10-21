package com.manufacttest.nullwrist.time2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.util.HashMap;
import java.util.Map;

/** Tiny embedded 5x7 bitmap alphabet: deliberately rasterized, no licensed fonts. */
public final class PixelWordmarkView extends View {
    private static final Map<Character,String[]> GLYPHS = new HashMap<>();
    static {
        GLYPHS.put('N',new String[]{"10001","11001","10101","10011","10001","10001","10001"});
        GLYPHS.put('U',new String[]{"10001","10001","10001","10001","10001","10001","01110"});
        GLYPHS.put('L',new String[]{"10000","10000","10000","10000","10000","10000","11111"});
        GLYPHS.put('W',new String[]{"10001","10001","10001","10101","10101","11011","10001"});
        GLYPHS.put('R',new String[]{"11110","10001","10001","11110","10100","10010","10001"});
        GLYPHS.put('I',new String[]{"11111","00100","00100","00100","00100","00100","11111"});
        GLYPHS.put('S',new String[]{"01111","10000","10000","01110","00001","00001","11110"});
        GLYPHS.put('T',new String[]{"11111","00100","00100","00100","00100","00100","00100"});
        GLYPHS.put('M',new String[]{"10001","11011","10101","10101","10001","10001","10001"});
        GLYPHS.put('E',new String[]{"11111","10000","10000","11110","10000","10000","11111"});
        GLYPHS.put('2',new String[]{"11110","00001","00001","01110","10000","10000","11111"});
        GLYPHS.put(' ',new String[]{"00000","00000","00000","00000","00000","00000","00000"});
    }

    private final Paint paint = new Paint();
    private String text = "NULLWRIST";
    private int ink = Color.rgb(255, 109, 0);

    public PixelWordmarkView(Context context) {
        super(context);
        paint.setAntiAlias(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setWordmark(String text, int ink) {
        this.text = text == null ? "" : text.toUpperCase();
        this.ink = ink;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int idealHeight = Math.min(70, Math.max(20, width / 9));
        setMeasuredDimension(width, resolveSize(idealHeight, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (text.isEmpty()) return;
        paint.setColor(ink);
        int unitCols = Math.max(1, text.length() * 6 - 1);
        int pixel = Math.max(1, Math.min(getHeight() / 7, getWidth() / unitCols));
        int wordWidth = unitCols * pixel;
        int x0 = (getWidth() - wordWidth) / 2;
        int y0 = (getHeight() - 7 * pixel) / 2;
        for (int i = 0; i < text.length(); i++) {
            String[] glyph = GLYPHS.get(text.charAt(i));
            if (glyph == null) continue;
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < 5; c++) {
                    if (glyph[r].charAt(c) == '1') {
                        int x = x0 + (i * 6 + c) * pixel;
                        int y = y0 + r * pixel;
                        canvas.drawRect(x, y, x + pixel, y + pixel, paint);
                    }
                }
            }
        }
    }
}

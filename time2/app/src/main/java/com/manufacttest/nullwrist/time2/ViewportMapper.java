package com.manufacttest.nullwrist.time2;

/**
 * Maps Android View-local touch pixels to the *real* PebbleOS LCD pixels,
 * with letterboxing and strict out-of-surface rejection.
 * This class has no Android dependencies so it can be exercised on the JVM.
 */
public final class ViewportMapper {
    public static final int EMERY_WIDTH = 200;
    public static final int EMERY_HEIGHT = 228;

    public final int sourceWidth;
    public final int sourceHeight;
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    private ViewportMapper(int sourceWidth, int sourceHeight,
                           float left, float top, float right, float bottom) {
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public static ViewportMapper fit(int sourceWidth, int sourceHeight,
                                     int viewWidth, int viewHeight) {
        if (sourceWidth < 1 || sourceHeight < 1 || viewWidth < 1 || viewHeight < 1) {
            throw new IllegalArgumentException("Display dimensions must be positive");
        }
        float scale = Math.min(viewWidth / (float) sourceWidth,
                               viewHeight / (float) sourceHeight);
        float width = sourceWidth * scale;
        float height = sourceHeight * scale;
        float left = (viewWidth - width) / 2f;
        float top = (viewHeight - height) / 2f;
        return new ViewportMapper(sourceWidth, sourceHeight,
                                  left, top, left + width, top + height);
    }

    /** @return null if outside the actual screen (the Android sidebands are not touchable). */
    public Point toPebble(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)
                || x < left || y < top || x >= right || y >= bottom) return null;
        int px = Math.min(sourceWidth - 1,
                (int) Math.floor(((x - left) * sourceWidth / (right - left)) + 0.0001d));
        int py = Math.min(sourceHeight - 1,
                (int) Math.floor(((y - top) * sourceHeight / (bottom - top)) + 0.0001d));
        return new Point(px, py);
    }

    public static final class Point {
        public final int x;
        public final int y;
        public Point(int x, int y) { this.x = x; this.y = y; }
        @Override public String toString() { return x + "," + y; }
    }
}

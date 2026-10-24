package com.manufacttest.nullwrist.time2;

import java.util.ArrayList;
import java.util.List;

/**
 * Main-thread gesture gate for the rear LCD. Touch events use Emery pixel
 * coordinates, never the Android display coordinates.
 *
 * Completed taps are delayed briefly so the first two taps of a triple-tap
 * cannot activate a watchapp accidentally. Drags and holds pass through as
 * soon as they can be distinguished from a tap. No Android dependencies.
 */
public final class TripleTapSelectRecognizer {
    public enum Type { DOWN, MOVE, UP, CANCEL }

    public interface Sink {
        void touch(Type type, int x, int y);
        void select();
    }

    public static final long TAP_MAX_MS = 220;
    public static final long GAP_MAX_MS = 330;
    public static final long SEQUENCE_MAX_MS = 900;
    public static final int SLOP_EMERY_PX = 10;

    private static final class Tap {
        final int sx, sy, ex, ey;
        Tap(int sx, int sy, int ex, int ey) {
            this.sx=sx; this.sy=sy; this.ex=ex; this.ey=ey;
        }
    }

    private final Sink sink;
    private final List<Tap> pending = new ArrayList<>(2);
    private boolean fingerDown;
    private boolean sentDown;
    private int downX, downY, previousX, previousY;
    private long downTime, lastUpTime, firstDownTime;

    public TripleTapSelectRecognizer(Sink sink) {
        if(sink==null)throw new IllegalArgumentException("Sink is required");
        this.sink=sink;
    }

    public void onEvent(Type type, int x, int y, long timeMs) {
        onTimeout(timeMs);
        switch(type) {
            case DOWN:
                if(fingerDown) {
                    if(sentDown)sink.touch(Type.CANCEL,previousX,previousY);
                    fingerDown=false;sentDown=false;
                    flushPending();
                }
                if(!pending.isEmpty() &&
                        (timeMs-lastUpTime > GAP_MAX_MS
                         || timeMs-firstDownTime > SEQUENCE_MAX_MS)) {
                    flushPending();
                }
                fingerDown=true;
                sentDown=false;
                downX=x;downY=y;previousX=x;previousY=y;downTime=timeMs;
                break;
            case MOVE:
                if(!fingerDown)return;
                if(!sentDown && (movedBeyondSlop(x,y)
                        || timeMs-downTime > TAP_MAX_MS)) {
                    flushPending();
                    sink.touch(Type.DOWN,downX,downY);
                    sentDown=true;
                }
                if(sentDown)sink.touch(Type.MOVE,x,y);
                previousX=x;previousY=y;
                break;
            case UP:
                if(!fingerDown)return;
                if(sentDown) {
                    sink.touch(Type.UP,x,y);
                } else if(timeMs-downTime <= TAP_MAX_MS && !movedBeyondSlop(x,y)) {
                    if(pending.isEmpty()) firstDownTime=downTime;
                    pending.add(new Tap(downX,downY,x,y));
                    lastUpTime=timeMs;
                    if(pending.size()==3) {
                        // Exactly one center-button click; no guest touchscreen taps.
                        if(timeMs-firstDownTime <= SEQUENCE_MAX_MS) {
                            pending.clear();
                            sink.select();
                        } else flushPending();
                    }
                } else {
                    flushPending();
                    sink.touch(Type.DOWN,downX,downY);
                    sink.touch(Type.UP,x,y);
                }
                fingerDown=false;
                sentDown=false;
                break;
            case CANCEL:
                if(sentDown && fingerDown)sink.touch(Type.CANCEL,previousX,previousY);
                fingerDown=false;sentDown=false;
                // Any earlier complete taps are ordinary touches, not a
                // triple tap. Never replay the cancelled current contact.
                flushPending();
                break;
        }
    }

    /** Called by the host at nextDeadlineMs(), and before new input. */
    public void onTimeout(long nowMs) {
        if(fingerDown && !sentDown && nowMs-downTime > TAP_MAX_MS) {
            flushPending();
            sink.touch(Type.DOWN,downX,downY);
            sentDown=true;
        }
        if(!fingerDown && !pending.isEmpty() &&
                (nowMs-lastUpTime > GAP_MAX_MS ||
                 nowMs-firstDownTime > SEQUENCE_MAX_MS)) {
            flushPending();
        }
    }

    /** Uptime timestamp, or -1 if no delayed callback is needed. */
    public long nextDeadlineMs() {
        if(fingerDown && !sentDown)return downTime+TAP_MAX_MS+1;
        if(fingerDown || pending.isEmpty())return -1;
        return Math.min(lastUpTime+GAP_MAX_MS+1,
                firstDownTime+SEQUENCE_MAX_MS+1);
    }

    /** Discard pending input on pause, disconnect or activity teardown. */
    public void reset() {
        if(fingerDown && sentDown)
            sink.touch(Type.CANCEL,previousX,previousY);
        fingerDown=false;sentDown=false;pending.clear();
    }

    private boolean movedBeyondSlop(int x,int y) {
        long dx=(long)x-downX, dy=(long)y-downY;
        return dx*dx+dy*dy > SLOP_EMERY_PX*SLOP_EMERY_PX;
    }

    private void flushPending() {
        for(Tap tap:pending) {
            sink.touch(Type.DOWN,tap.sx,tap.sy);
            sink.touch(Type.UP,tap.ex,tap.ey);
        }
        pending.clear();
    }
}

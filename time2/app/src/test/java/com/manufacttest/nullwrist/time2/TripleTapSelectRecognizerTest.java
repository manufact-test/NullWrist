package com.manufacttest.nullwrist.time2;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class TripleTapSelectRecognizerTest {
    private static final class Events implements TripleTapSelectRecognizer.Sink {
        final List<String> calls=new ArrayList<>();
        @Override public void touch(TripleTapSelectRecognizer.Type type,int x,int y) {
            calls.add(type.name()+":"+x+","+y);
        }
        @Override public void select(){ calls.add("SELECT"); }
    }
    private static final TripleTapSelectRecognizer.Type D=TripleTapSelectRecognizer.Type.DOWN;
    private static final TripleTapSelectRecognizer.Type M=TripleTapSelectRecognizer.Type.MOVE;
    private static final TripleTapSelectRecognizer.Type U=TripleTapSelectRecognizer.Type.UP;
    private static final TripleTapSelectRecognizer.Type C=TripleTapSelectRecognizer.Type.CANCEL;

    @Test public void tripleTapSendsExactlyOneSelectAndNoGuestTouches() {
        Events out=new Events();
        TripleTapSelectRecognizer gate=new TripleTapSelectRecognizer(out);
        gate.onEvent(D,5,6,0); gate.onEvent(U,5,6,55);
        gate.onEvent(D,60,70,165); gate.onEvent(U,60,70,220);
        gate.onEvent(D,120,125,330); gate.onEvent(U,120,125,390);
        gate.onTimeout(2000);
        assertEquals(Arrays.asList("SELECT"),out.calls);
        assertEquals(-1,gate.nextDeadlineMs());
    }
    @Test public void singleTapReplaysOnlyAfterTimeout() {
        Events out=new Events();
        TripleTapSelectRecognizer gate=new TripleTapSelectRecognizer(out);
        gate.onEvent(D,20,22,0);gate.onEvent(U,20,22,40);
        assertTrue(out.calls.isEmpty());
        assertEquals(371,gate.nextDeadlineMs());
        gate.onTimeout(371);
        assertEquals(Arrays.asList("DOWN:20,22","UP:20,22"),out.calls);
    }
    @Test public void doubleTapFallsBackToTwoRealTouches() {
        Events out=new Events();
        TripleTapSelectRecognizer gate=new TripleTapSelectRecognizer(out);
        gate.onEvent(D,20,22,0);gate.onEvent(U,20,22,40);
        gate.onEvent(D,23,25,160);gate.onEvent(U,23,25,190);
        gate.onTimeout(521);
        assertEquals(Arrays.asList("DOWN:20,22","UP:20,22",
                "DOWN:23,25","UP:23,25"),out.calls);
    }
    @Test public void swipeImmediatelyFlushesPriorTapAndForwardsMovement() {
        Events out=new Events();
        TripleTapSelectRecognizer gate=new TripleTapSelectRecognizer(out);
        gate.onEvent(D,20,20,0);gate.onEvent(U,20,20,20);
        gate.onEvent(D,31,33,110);
        gate.onEvent(M,55,34,130);
        gate.onEvent(U,90,45,155);
        assertEquals(Arrays.asList("DOWN:20,20","UP:20,20",
                "DOWN:31,33","MOVE:55,34","UP:90,45"),out.calls);
    }
    @Test public void longPressDownIsForwardedOnDeadlineAndCancelledSafely() {
        Events out=new Events();
        TripleTapSelectRecognizer gate=new TripleTapSelectRecognizer(out);
        gate.onEvent(D,50,40,0);
        gate.onTimeout(221);
        gate.onEvent(C,50,40,250);
        assertEquals(Arrays.asList("DOWN:50,40","CANCEL:50,40"),out.calls);
    }
    @Test public void pauseDropsIncompleteTapAndCancelsGuestDrag() {
        Events out=new Events();
        TripleTapSelectRecognizer gate=new TripleTapSelectRecognizer(out);
        gate.onEvent(D,10,10,0);gate.onEvent(U,10,10,20);
        gate.reset();
        gate.onTimeout(500);
        assertTrue(out.calls.isEmpty());
        gate.onEvent(D,10,10,600);gate.onEvent(M,99,99,610);
        gate.reset();
        assertEquals(Arrays.asList("DOWN:10,10","MOVE:99,99",
                "CANCEL:99,99"),out.calls);
    }
    @Test public void unrelatedLateTapDoesNotBecomeTripleSelect() {
        Events out=new Events();
        TripleTapSelectRecognizer gate=new TripleTapSelectRecognizer(out);
        gate.onEvent(D,1,1,0);gate.onEvent(U,1,1,20);
        gate.onEvent(D,2,2,450);gate.onEvent(U,2,2,470);
        assertEquals(Arrays.asList("DOWN:1,1","UP:1,1"),out.calls);
        gate.onTimeout(801);
        assertEquals(Arrays.asList("DOWN:1,1","UP:1,1","DOWN:2,2","UP:2,2"),out.calls);
    }
}

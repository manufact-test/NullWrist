package com.manufacttest.nullwrist.time2;

/**
 * Deterministic QMP commands for the official generic pebble-emery board.
 * These strings MUST be written only by a serialized, handshake-completed
 * backend. They are not executed by the preview UI.
 *
 * See coredevices/qemu hw/misc/pebble_touch.c:
 *   input-send-event ABS is 0..INPUT_EVENT_ABS_MAX; touch uses BTN left.
 */
public final class EmeryQmpCommands {
    private static final int ABS_MAX = 32767;
    private EmeryQmpCommands() {}

    public enum TouchPhase { DOWN, MOVE, UP, CANCEL }
    public enum Button { BACK, UP, SELECT, DOWN }

    public static String capabilities() {
        return "{\"execute\":\"qmp_capabilities\"}";
    }

    public static String button(Button button) {
        final String qcode;
        switch (button) {
            case BACK:   qcode="left"; break;
            case SELECT: qcode="right"; break;
            case UP:     qcode="up"; break;
            case DOWN:   qcode="down"; break;
            default: throw new IllegalArgumentException("Unknown button");
        }
        return "{\"execute\":\"send-key\",\"arguments\":{"
                + "\"keys\":[{\"type\":\"qcode\",\"data\":\""+qcode+"\"}],"
                + "\"hold-time\":85}}";
    }

    /**
     * Endpoints select the CENTER of the emulator pixel. A naive x/200
     * conversion is rounded down by the QEMU touch MMIO handler and would
     * make the rightmost column impossible to hit.
     */
    public static int absForPixel(int pixel, int dimension) {
        if (dimension < 1 || pixel < 0 || pixel >= dimension)
            throw new IllegalArgumentException("Out-of-bounds emulator coordinate");
        return Math.min(ABS_MAX, (int)Math.round((pixel + 0.5d) * ABS_MAX / dimension));
    }

    public static String touch(TouchPhase phase, int x, int y) {
        int ax=absForPixel(x,ViewportMapper.EMERY_WIDTH);
        int ay=absForPixel(y,ViewportMapper.EMERY_HEIGHT);
        String events="{\"type\":\"abs\",\"data\":{\"axis\":\"x\",\"value\":"+ax+"}},"
                + "{\"type\":\"abs\",\"data\":{\"axis\":\"y\",\"value\":"+ay+"}}";
        if (phase==TouchPhase.DOWN) {
            events+=",{\"type\":\"btn\",\"data\":{\"button\":\"left\",\"down\":true}}";
        } else if (phase==TouchPhase.UP || phase==TouchPhase.CANCEL) {
            events+=",{\"type\":\"btn\",\"data\":{\"button\":\"left\",\"down\":false}}";
        }
        return "{\"execute\":\"input-send-event\",\"arguments\":{\"events\":["
                + events + "]}}";
    }
}

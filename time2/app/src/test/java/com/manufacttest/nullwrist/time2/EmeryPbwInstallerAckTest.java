package com.manufacttest.nullwrist.time2;

import org.junit.Test;
import static org.junit.Assert.*;

/** Guards against the firmware-specific PutBytes INSTALL ACK regression. */
public final class EmeryPbwInstallerAckTest {
    @Test public void genuineEmeryNonzeroTransferCookieAccepted() {
        int cookie=1264670356; // observed in user-supplied physical Titan 2 log
        assertTrue(EmeryPbwInstaller.isExpectedInstallCookie(cookie,cookie));
    }

    @Test public void zeroInstallAckAllowedForOtherPebbleFirmware() {
        assertTrue(EmeryPbwInstaller.isExpectedInstallCookie(0x12345678,0));
    }

    @Test public void unrelatedAckCookieNeverAccepted() {
        assertFalse(EmeryPbwInstaller.isExpectedInstallCookie(0x12345678,0x12345679));
    }
}

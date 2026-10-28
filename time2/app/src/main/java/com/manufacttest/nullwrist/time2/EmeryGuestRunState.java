package com.manufacttest.nullwrist.time2;

import java.util.UUID;

/** PebbleOS AppRunState: command 0x01 followed by the current UUID.
 * Unknown reply and launcher are not errors or proof of an app crash.
 */
final class EmeryGuestRunState {
    static final long FRESH_MS=15_000;
    private EmeryGuestRunState(){}
    static boolean valid(byte[] p){return p!=null && p.length>=17 && p[0]==1;}
    static UUID uuid(byte[] p){
        if(!valid(p))return null;
        long high=0,low=0;
        for(int i=1;i<9;i++)high=(high<<8)|(p[i]&255L);
        for(int i=9;i<17;i++)low=(low<<8)|(p[i]&255L);
        if(high==0 && low==0)return null;
        return new UUID(high,low);
    }
    static boolean fresh(boolean session,long timestamp,long now){
        return session && timestamp>0 && now>=timestamp && now-timestamp<=FRESH_MS;
    }
}

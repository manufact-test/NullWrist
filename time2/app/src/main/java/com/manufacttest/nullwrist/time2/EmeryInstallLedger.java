package com.manufacttest.nullwrist.time2;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

/**
 * Durable content fingerprints of binaries successfully sent to PebbleOS.
 * This is only evidence of a prior transfer, not proof of a currently running
 * app: the active app must still be checked with guest AppRunState.
 */
public final class EmeryInstallLedger {
    private final SharedPreferences prefs;

    public EmeryInstallLedger(Context context){
        prefs=context.getApplicationContext()
                .getSharedPreferences("time2-guest-installed-ledger",Context.MODE_PRIVATE);
    }

    static String sha256(File pbw)throws IOException{
        if(!pbw.isFile()||pbw.length()<1||pbw.length()>EmeryPackageIndex.MAX_PBW_BYTES)
            throw new IOException("Missing or oversized PBW");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(FileInputStream in=new FileInputStream(pbw)){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);
            }
            StringBuilder hex=new StringBuilder(64);
            for(byte value:digest.digest())hex.append(
                    String.format(Locale.ROOT,"%02x",value&255));
            return hex.toString();
        }catch(NoSuchAlgorithmException error){
            throw new IOException("SHA-256 unavailable",error);
        }
    }

    public synchronized boolean matches(String uuid,File pbw)throws IOException{
        String expected=prefs.getString("pbw_"+UUID.fromString(uuid),null);
        return expected!=null && expected.equals(sha256(pbw));
    }

    public synchronized void record(String uuid,File pbw)throws IOException{
        String digest=sha256(pbw);
        if(!prefs.edit().putString("pbw_"+UUID.fromString(uuid),digest).commit())
            throw new IOException("Unable to record PebbleOS transfer");
    }

    public synchronized void forget(String uuid){
        prefs.edit().remove("pbw_"+UUID.fromString(uuid)).commit();
    }

    public synchronized void invalidate(){
        prefs.edit().clear().commit();
    }
}

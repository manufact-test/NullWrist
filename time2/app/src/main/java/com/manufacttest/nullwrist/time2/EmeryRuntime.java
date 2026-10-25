package com.manufacttest.nullwrist.time2;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Diagnostic-only live QEMU session. No background service or fake watch state.
 * Assumes CI already bundled hashed Emery images and native ARM64 QEMU.
 * A single instance is scoped to its Activity; commands always off main thread.
 */
public final class EmeryRuntime {
    public interface Listener {
        void status(String text);
        void frame(Bitmap bitmap);
    }

    private final Context context;
    private final Listener listener;
    private final ExecutorService commands=Executors.newSingleThreadExecutor(r->{
        Thread thread=new Thread(r,"EmeryQmpWorker");thread.setDaemon(true);return thread;
    });
    private final ExecutorService frames=Executors.newSingleThreadExecutor(r->{
        Thread thread=new Thread(r,"EmeryFrameReader");thread.setDaemon(true);return thread;
    });
    private static final AtomicBoolean SESSION_ACTIVE=new AtomicBoolean(false);
    private volatile boolean ownsSession;
    private final AtomicBoolean displayRefreshQueued=new AtomicBoolean(false);
    private volatile boolean closing;
    private volatile Runnable gracefulStopCompletion;
    private volatile boolean connected;
    private volatile Process process;
    private final ExecutorService appInstallWorker=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"EmeryPebbleInstaller");t.setDaemon(true);return t;
    });
    private volatile EmeryPebbleLink appLink;
    private EmeryQmpClient qmp;
    private EmeryAssets.Staged assets;

    public EmeryRuntime(Context context,Listener listener) {
        this.context=context.getApplicationContext();
        this.listener=listener;
    }
    public boolean isConnected(){return connected&&!closing;}
    public static boolean isDeviceLabActive(){return SESSION_ACTIVE.get();}

    public void start(){
        if(!SESSION_ACTIVE.compareAndSet(false,true)){
            emit("Another Emery session is still running; close it first");
            return;
        }
        ownsSession=true;
        commands.execute(()->{
            try {
                emit("Validating Emery firmware and QEMU...");
                assets=EmeryAssets.stage(context);
                if(closing) return;
                // Only one QEMU can own our private QMP socket. We refuse to
                // spawn another if a previous emulator remains reachable.
                if(assets.qmp.exists()){
                    try(EmeryQmpClient existing=new EmeryQmpClient(
                            assets.qmp.getAbsolutePath())){
                        throw new IOException("Previous Emery instance is still active; "
                                + "cannot start another emulator safely");
                    } catch(IOException candidate){
                        if(candidate.getMessage()!=null
                                && candidate.getMessage().startsWith("Previous Emery"))
                            throw candidate;
                        // Socket path was stale; QMP handshake was impossible.
                        if(!assets.qmp.delete()) throw new IOException("Stale QMP socket cannot be cleared");
                    }
                }
                // A previous output is never proof of the current session.
                if(assets.frame.exists() && !assets.frame.delete())
                    throw new IOException("Cannot clear stale frame");
                File ppm=new File(assets.root,"headless-frame.ppm");
                if(ppm.exists() && !ppm.delete())
                    throw new IOException("Cannot clear stale screenshot");
                File hostLog=new File(assets.root,"emery-host.log");
                if(hostLog.exists() && !hostLog.delete())
                    throw new IOException("Cannot reset emulator diagnostics");
                File guestLog=new File(assets.root,"emery-debug-uart2.bin");
                if(guestLog.exists() && !guestLog.delete())
                    throw new IOException("Cannot reset guest UART2 diagnostics");
                List<String> argv=new ArrayList<>();
                argv.add(assets.qemu.getAbsolutePath());
                argv.add("-rtc"); argv.add("base=localtime");
                argv.add("-display"); argv.add("none");
                argv.add("-monitor"); argv.add("none");
                argv.add("-qmp"); argv.add("unix:"+assets.qmp.getAbsolutePath()+",server=on,wait=off");
                argv.add("-serial");argv.add("null");
                argv.add("-serial");argv.add("unix:"+new File(assets.root,"pebble.sock").getAbsolutePath()+",server=on,wait=off");
                // Firmware debug serial is UART2, NOT QMP/UART1. Preserve raw Pulse
                // frames for diagnosing real PebbleOS watchface crashes.
                argv.add("-serial");argv.add("file:"+new File(assets.root,"emery-debug-uart2.bin").getAbsolutePath());
                argv.add("-machine");argv.add("pebble-emery,audiodev=snd0");
                argv.add("-audiodev");argv.add("none,id=snd0");
                argv.add("-kernel");argv.add(assets.elf.getAbsolutePath());
                argv.add("-drive");argv.add("if=mtd,format=raw,file="+assets.spi.getAbsolutePath());
                ProcessBuilder builder=new ProcessBuilder(argv);
                builder.directory(assets.root);
                builder.redirectErrorStream(true);
                builder.redirectOutput(assets.log);
                builder.environment().put("HOME",assets.root.getAbsolutePath());
                builder.environment().put("TMPDIR",assets.root.getAbsolutePath());
                builder.environment().put("PEBBLE_EMERY_FB_PATH",assets.frame.getAbsolutePath());
                // Do not expose QMP or serial on 0.0.0.0.
                if(closing) return;
                emit("Booting genuine PebbleOS / Emery...");
                process=builder.start();

                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(18);
                while(!closing && System.nanoTime()<deadline){
                    if(!process.isAlive())throw new IOException(
                            "QEMU exited early (exit "+process.exitValue()
                                    +"). Open emulator diagnostics.");
                    try {
                        qmp=new EmeryQmpClient(assets.qmp.getAbsolutePath());
                        connected=true;
                        break;
                    } catch(IOException pending) {
                        Thread.sleep(150);
                    }
                }
                if(!connected)throw new IOException("Emery QMP did not come online");
                emit("Emery QMP connected; requesting explicit headless LCD refresh");
                frames.execute(this::pumpFrames);
                queueRequestedPbwInstall();
            } catch(InterruptedException e){
                Thread.currentThread().interrupt();
                if(!closing)emit("Emery startup interrupted");
                shutdownProcess();
            } catch(Exception error){
                if(!closing)emit("Emery start failed: "+error.getMessage());
                shutdownProcess();
            }
        });
    }

    private void pumpFrames(){
        int lastSeq=0;
        boolean rendered=false;
        boolean refreshWorked=false;
        long lastRefresh=0;
        int refreshes=0;
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);
        while(!closing){
            Process p=process;
            if(p==null || !p.isAlive()){
                emit("Emery QEMU stopped. Check private runtime logs.");
                return;
            }
            try {
                // QEMU -display none does not install a periodic GUI refresh
                // listener. Trigger graphic_hw_update through the official
                // QMP screendump command, not through a fake app overlay.
                long now=System.nanoTime();
                if(now-lastRefresh>=TimeUnit.MILLISECONDS.toNanos(rendered?4500:350) &&
                        displayRefreshQueued.compareAndSet(false,true)){
                    lastRefresh=now;
                    try {
                        commands.execute(()->{
                            try {
                                if(!closing && qmp!=null && connected){
                                    File ppm=new File(assets.root,"headless-frame.ppm");
                                    qmp.refreshHeadlessDisplay(ppm.getAbsolutePath());
                                }
                            } catch(IOException error) {
                                emit("QMP screendump failed: "+error.getMessage());
                            } finally {
                                displayRefreshQueued.set(false);
                            }
                        });
                        refreshes++;
                    } catch(java.util.concurrent.RejectedExecutionException stop) {
                        displayRefreshQueued.set(false);
                        return;
                    }
                }
                File ppm=new File(assets.root,"headless-frame.ppm");
                if(!refreshWorked && ppm.isFile() && ppm.length()>100){
                    refreshWorked=true;
                    emit("QEMU headless screenshot generated; checking ARGB bridge");
                }
                EmeryFrameProtocol.Frame image=EmeryFrameProtocol.read(assets.frame);
                if(image!=null && image.sequence!=lastSeq){
                    lastSeq=image.sequence;
                    Bitmap bitmap=Bitmap.createBitmap(EmeryFrameProtocol.WIDTH,
                            EmeryFrameProtocol.HEIGHT,Bitmap.Config.ARGB_8888);
                    bitmap.setPixels(image.argbPixels,0,EmeryFrameProtocol.WIDTH,
                            0,0,EmeryFrameProtocol.WIDTH,EmeryFrameProtocol.HEIGHT);
                    if(!closing)listener.frame(bitmap);
                    if(!rendered){
                        rendered=true;
                        emit("Real Emery framebuffer detected — interactive test mode");
                    }
                }
                if(!rendered && System.nanoTime()>deadline){
                    emit("No Emery LCD after 40s; QMP screenshot "+(refreshWorked?"OK":"MISSING")+
                            "; refresh requests="+refreshes+"; review host diagnostics");
                    return;
                }
                Thread.sleep(75);
            } catch(InterruptedException stop){
                Thread.currentThread().interrupt();
                return;
            } catch(IOException problem){
                emit("Frame read error: "+problem.getMessage());
                return;
            }
        }
    }

    /**
     * One serial owner for the lifetime of the emulator. This queue is polled
     * while QEMU stays alive; UI navigation does not restart the guest.
     */
    private void queueRequestedPbwInstall(){
        appInstallWorker.execute(()->{
            android.content.SharedPreferences pref=context.getSharedPreferences(
                    "time2-pbw-install",Context.MODE_PRIVATE);
            try{
                File socket=new File(assets.root,"pebble.sock");
                IOException last=null;
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
                while(!closing && System.nanoTime()<until && appLink==null){
                    try { appLink=new EmeryPebbleLink(socket.getAbsolutePath()); }
                    catch(IOException pending){ last=pending;Thread.sleep(200); }
                }
                if(closing)return;
                if(appLink==null)throw new IOException("Pebble UART unavailable: "+last);
                EmeryPbwInstaller installer=new EmeryPbwInstaller(appLink,this::emit);
                EmeryInstallLedger ledger=new EmeryInstallLedger(context);
                String oldError=pref.getString("last_operation_error",null);
                if(oldError!=null && oldError.contains("QEMU serial body was not received"))
                    pref.edit().remove("last_operation_error").apply();
                emit("Pebble serial companion connected");
                while(!closing && process!=null && process.isAlive() && appLink!=null){
                    Runnable completion=gracefulStopCompletion;
                    if(completion!=null){
                        gracefulStopCompletion=null;
                        try{
                            emit("PebbleOS: graceful stop requested for save-data flush");
                            // Ask the real guest which app runs now. Do not stop a
                            // stale UUID remembered by the Android companion.
                            byte[] query=new byte[17];query[0]=3;
                            appLink.send(0x34,query);
                            EmeryPebbleWire.Packet state=appLink.waitFor(0x34,3);
                            if(state.payload.length>=17 && state.payload[0]==1){
                                byte[] stop=new byte[17];stop[0]=2;
                                System.arraycopy(state.payload,1,stop,1,16);
                                boolean nonzero=false;
                                for(int i=1;i<17;i++)if(stop[i]!=0)nonzero=true;
                                if(nonzero){
                                    appLink.send(0x34,stop);
                                    // Let the guest execute deinit()/persist_write_*
                                    // on its own FreeRTOS app task before QEMU exits.
                                    Thread.sleep(1100);
                                }
                            }
                        }catch(Exception saveError){
                            if(!closing)emit("PebbleOS graceful stop not confirmed: "+
                                    saveError.getMessage());
                        }finally{
                            completion.run();
                        }
                        return;
                    }
                    String deleting=pref.getString("pending_delete_uuid",null);
                    String chosen=pref.getString("pending_uuid",null);
                    if(deleting==null && chosen==null){
                        appLink.serviceOnce(1);
                        continue;
                    }
                    try{
                        if(deleting!=null){
                            installer.uninstall(java.util.UUID.fromString(deleting));
                            // Tombstone is consumed only after guest deletion ACK.
                            ledger.forget(deleting);
                            if(deleting.equals(pref.getString("pending_delete_uuid",null)))
                                pref.edit().remove("pending_delete_uuid").commit();
                            if(deleting.equals(pref.getString("last_installed_uuid",null)))
                                pref.edit().remove("last_installed_uuid").apply();
                        }
                        if(chosen!=null){
                            java.util.UUID id=java.util.UUID.fromString(chosen);
                            File pbw=new File(new File(context.getFilesDir(),"time2/pbws"),
                                    id+".pbw");
                            if(!pbw.isFile())throw new IOException("Requested PBW missing: "+chosen);
                            String previous=pref.getString("last_installed_uuid",null);
                            String force=pref.getString("pending_force_reinstall_uuid",null);
                            boolean reinstall=chosen.equals(force);
                            if(reinstall)
                                pref.edit().remove("pending_force_reinstall_uuid").commit();
                            boolean stable;
                            if(!reinstall && ledger.matches(chosen,pbw)){
                                stable=installer.launchInstalled(id,previous);
                            }else{
                                stable=installer.install(pbw,previous);
                                // A guest transfer was completed. Record immutable PBW hash
                                // even when later RUNNING_STABLE is not confirmed.
                                ledger.record(chosen,pbw);
                                pref.edit().putString("last_transferred_uuid",chosen).apply();
                            }
                            if(stable)pref.edit().putString("last_installed_uuid",chosen).apply();
                            else emit("PBW installed but guest launch NOT confirmed: "+chosen);
                            if(chosen.equals(pref.getString("pending_uuid",null)))
                                pref.edit().remove("pending_uuid").commit();
                        }
                    }catch(Exception error){
                        if(chosen!=null && chosen.equals(pref.getString("pending_uuid",null)))
                            pref.edit().remove("pending_uuid").commit();
                        // On a failed deletion retain pending_delete_uuid for retry
                        // once the guest/connection recovers.
                        if(!closing)emit("PEBBLE APP TASK FAILED: "+error.getMessage());
                        // Don't hammer a broken guest with repeated failed deletes.
                        if(deleting!=null)Thread.sleep(1000);
                    }
                }
            }catch(InterruptedException stopped){
                Thread.currentThread().interrupt();
            }catch(Exception failure){
                if(!closing)emit("PEBBLE APP TASK FAILED: "+failure.getMessage());
            }finally{
                EmeryPebbleLink channel=appLink;
                appLink=null;
                if(channel!=null)try{channel.close();}catch(IOException ignored){}
            }
        });
    }

    /**
     * Send app STOP through the live Pebble protocol before shutting QEMU
     * down. Completion is called by the one serial owner after guest DEINIT
     * had time to persist data. No extra writer races with AppFetch/PutBytes.
     */
    public void requestGracefulStop(Runnable onFinished){
        if(closing || process==null || !process.isAlive() || appLink==null){
            onFinished.run();
            return;
        }
        gracefulStopCompletion=onFinished;
    }

    public void touch(EmeryQmpCommands.TouchPhase phase,int x,int y){
        if(closing||!connected)return;
        commands.execute(()->{
            if(!connected||closing||qmp==null)return;
            try {qmp.touch(phase,x,y);}
            catch(IOException e){emit("Touch transport error: "+e.getMessage());}
        });
    }

    public void press(EmeryQmpCommands.Button button){
        if(closing||!connected)return;
        commands.execute(()->{
            if(!connected||closing||qmp==null)return;
            try {qmp.press(button);}
            catch(IOException e){emit("Button transport error: "+e.getMessage());}
        });
    }

    public void close(){
        closing=true;
        connected=false;
        frames.shutdownNow();
        appInstallWorker.shutdownNow();
        EmeryPebbleLink serial=appLink;
        if(serial!=null)try{serial.close();}catch(IOException ignored){}
        // If the worker is blocked in a QMP transaction, allow that bounded
        // operation to finish and then tear down QEMU in its own worker.
        commands.execute(this::shutdownProcess);
        commands.shutdown();
    }

    private void shutdownProcess(){
        connected=false;
        if(qmp!=null){
            try{qmp.close();}catch(IOException ignored){}
            qmp=null;
        }
        Process p=process;
        if(p!=null){
            p.destroy();
            try{
                if(!p.waitFor(3,TimeUnit.SECONDS)){
                    p.destroyForcibly();
                    p.waitFor(2,TimeUnit.SECONDS);
                }
            } catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            }
            process=null;
        }
        // Only unlink after the process is gone, never while still live.
        if(assets!=null && (p==null||!p.isAlive())){
            if(assets.qmp.exists())assets.qmp.delete();
        }
        if(ownsSession){
            ownsSession=false;
            SESSION_ACTIVE.set(false);
        }
    }

    private void emit(String text){
        if(closing)return;
        // Report the true result back to the main-screen companion. This does
        // not cover the genuine guest LCD with an Android spinner or dialog.
        if(text.startsWith("PebbleOS:") || text.startsWith("PBW ") ||
                text.startsWith("PEBBLE APP TASK")) {
            android.content.SharedPreferences prefs=context.getSharedPreferences(
                    "time2-pbw-install",Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor update=prefs.edit()
                    .putString("last_operation_status",text);
            if(text.startsWith("PEBBLE APP TASK FAILED") ||
                    text.contains("NOT confirmed"))
                update.putString("last_operation_error",text);
            else if(text.startsWith("PebbleOS confirmed RUNNING_STABLE") ||
                    text.startsWith("PebbleOS deleted watchface"))
                update.remove("last_operation_error");
            update.apply();
        }
        if(assets!=null){
            File file=new File(assets.root,"emery-host.log");
            byte[] record=(System.currentTimeMillis()+" "+text+"\n")
                    .getBytes(StandardCharsets.UTF_8);
            synchronized(this){
                try(FileOutputStream out=new FileOutputStream(file,true)){
                    out.write(record);
                }catch(IOException ignored){
                    // A failed debug write cannot stop the emulator.
                }
            }
        }
        listener.status(text);
    }
}

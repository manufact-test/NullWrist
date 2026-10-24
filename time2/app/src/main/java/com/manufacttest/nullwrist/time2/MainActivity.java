package com.manufacttest.nullwrist.time2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal phone-side companion to a genuine PebbleOS rear watch.
 * Preserve existing PBW import/install, flash recovery and diagnostics.
 */
public final class MainActivity extends Activity {
    private static final int IMPORT_PBW=26;
    private static final int EXPORT_EMERY_LOG=27;
    private static final int EXPORT_SPI=28;
    private static final int IMPORT_SPI=29;
    private static final int BG=Color.rgb(8,9,13);
    private static final int ORANGE=Color.rgb(255,109,0);
    private static final int AMBER=Color.rgb(255,156,31);
    private static final int WHITE=Color.rgb(235,236,232);
    private static final int MUTED=Color.rgb(155,158,158);
    private static final int CARD=Color.rgb(22,23,28);
    private static final int BORDER=Color.rgb(46,47,53);
    private LinearLayout layout;
    private LocalPbwLibrary library;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Display display=getDisplay();
        int width=getResources().getDisplayMetrics().widthPixels;
        int height=getResources().getDisplayMetrics().heightPixels;
        boolean secondary=display!=null && display.getDisplayId()!=Display.DEFAULT_DISPLAY;
        if(secondary || (width<=700 && height<=820)){
            startActivity(new Intent(this,RearActivity.class));
            finish();
            return;
        }
        library=new LocalPbwLibrary(this);
        render();
    }

    @Override protected void onRestart(){
        super.onRestart();
        if(library!=null)render();
    }

    private void render(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18),dp(18),dp(18),dp(32));
        scroll.addView(layout,new ScrollView.LayoutParams(-1,-2));
        setContentView(scroll);

        buildHeader();
        buildHero();

        List<EmeryPackageIndex.PackageInfo> packages=new ArrayList<>(library.list());
        packages.sort(Comparator.comparing(item->item.name.toLowerCase(java.util.Locale.ROOT)));
        List<EmeryPackageIndex.PackageInfo> faces=new ArrayList<>();
        List<EmeryPackageIndex.PackageInfo> apps=new ArrayList<>();
        for(EmeryPackageIndex.PackageInfo item:packages){
            if(item.watchface)faces.add(item);
            else apps.add(item);
        }
        buildLibrarySection("WATCHFACES",faces,true);
        buildLibrarySection("WATCHAPPS",apps,false);
        text(layout,"Genuine PebbleOS / Emery   •   Titan 2",11,MUTED,Gravity.CENTER,20);
    }

    private void buildHeader(){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        PixelWordmarkView logo=new PixelWordmarkView(this);
        row.addView(logo,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView settings=action("⚙",false,()->showSettings());
        settings.setContentDescription("Settings and diagnostics");
        settings.setTextSize(23);
        row.addView(settings,new LinearLayout.LayoutParams(dp(56),dp(48)));
        layout.addView(row);
        text(layout,"TIME 2   /   PEBBLEOS",11,AMBER,Gravity.CENTER,2);

        boolean bundled=EmeryAssets.isBundled(this);
        boolean session=EmeryRuntime.isDeviceLabActive();
        LinearLayout status=new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(13),dp(10),dp(13),dp(10));
        status.setBackground(panel(CARD,BORDER));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=dp(14);
        layout.addView(status,params);
        TextView dot=label("●",14,session?ORANGE:MUTED,true);
        status.addView(dot);
        TextView line=label(session?" PebbleOS session open":
                bundled?" Emulator ready to start":" Emulator assets unavailable",
                12,WHITE,false);
        line.setPadding(dp(8),0,0,0);
        status.addView(line);
    }

    private void buildHero(){
        LinearLayout hero=new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(14),dp(14),dp(14),dp(14));
        hero.setBackground(panel(CARD,BORDER));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=dp(14);
        layout.addView(hero,params);

        LinearLayout content=new LinearLayout(this);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setOrientation(LinearLayout.HORIZONTAL);
        hero.addView(content);

        EmulatorSurfaceView preview=new EmulatorSurfaceView(this);
        preview.setEmptyLabel("NO FRAME YET");
        // Use a real, validated *saved* Emery framebuffer, never fake a
        // watchface preview or claim that this snapshot is a live feed.
        try{
            File frame=new File(new File(getFilesDir(),"time2-emery"),"emery-fb-v2.bin");
            EmeryFrameProtocol.Frame captured=EmeryFrameProtocol.read(frame);
            if(captured!=null){
                Bitmap image=Bitmap.createBitmap(EmeryFrameProtocol.WIDTH,
                        EmeryFrameProtocol.HEIGHT,Bitmap.Config.ARGB_8888);
                image.setPixels(captured.argbPixels,0,EmeryFrameProtocol.WIDTH,
                        0,0,EmeryFrameProtocol.WIDTH,EmeryFrameProtocol.HEIGHT);
                preview.setFrame(image);
            }
        }catch(IOException ignored){
            // An incomplete snapshot is not displayed.
        }
        preview.setContentDescription("Last captured Emery display, not live");
        preview.setFocusable(false);
        LinearLayout.LayoutParams thumb=new LinearLayout.LayoutParams(dp(116),dp(132));
        content.addView(preview,thumb);

        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14),0,0,0);
        content.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        text(info,"YOUR PEBBLE TIME 2",10,AMBER,Gravity.START,0);
        String lastId=getSharedPreferences("time2-pbw-install",MODE_PRIVATE)
                .getString("last_installed_uuid","");
        String name="Ready for a watchface";
        for(EmeryPackageIndex.PackageInfo item:library.list()) {
            if(item.uuid.equals(lastId)) {
                name=item.name;break;
            }
        }
        text(info,name,18,WHITE,Gravity.START,8).setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        text(info,"Last framebuffer snapshot",11,MUTED,Gravity.START,8);
        text(info,"Installation is not proof of a running watchface",
                10,MUTED,Gravity.START,8);

        TextView launch=action("OPEN PEBBLEOS",true,()->openWatch(false));
        LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(-1,dp(51));
        actionParams.topMargin=dp(14);
        hero.addView(launch,actionParams);
    }

    private void buildLibrarySection(String title,
            List<EmeryPackageIndex.PackageInfo> entries,boolean watchface){
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams heading=new LinearLayout.LayoutParams(-1,-2);
        heading.topMargin=dp(24);
        heading.bottomMargin=dp(9);
        layout.addView(header,heading);
        TextView label=label(title+"  /  "+entries.size(),14,WHITE,true);
        header.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        if(watchface){
            TextView importAction=action("+ IMPORT .PBW",false,this::importPbw);
            importAction.setTextSize(11);
            header.addView(importAction,new LinearLayout.LayoutParams(dp(126),dp(48)));
        }
        if(entries.isEmpty()){
            LinearLayout empty=new LinearLayout(this);
            empty.setPadding(dp(14),dp(14),dp(14),dp(14));
            empty.setBackground(panel(CARD,BORDER));
            layout.addView(empty,new LinearLayout.LayoutParams(-1,-2));
            text(empty,watchface?"No watchfaces imported yet.":"No watchapps imported yet.",
                    12,MUTED,Gravity.START,0);
            if(!watchface){
                TextView importAnother=action("+ IMPORT .PBW",false,this::importPbw);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));
                p.topMargin=dp(8);
                layout.addView(importAnother,p);
            }
            return;
        }
        for(EmeryPackageIndex.PackageInfo item:entries){
            LinearLayout line=new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(14),dp(8),dp(8),dp(8));
            line.setBackground(panel(CARD,BORDER));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.bottomMargin=dp(8);
            layout.addView(line,p);

            LinearLayout copy=new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(0,dp(4),dp(8),dp(4));
            line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            text(copy,item.name,14,WHITE,Gravity.START,0);
            String lastId=getSharedPreferences("time2-pbw-install",MODE_PRIVATE)
                    .getString("last_installed_uuid","");
            text(copy,item.uuid.equals(lastId)?"Transferred · not verified running":
                    "Imported · not installed",10,MUTED,Gravity.START,4);
            TextView install=action("INSTALL",false,()->install(item));
            install.setTextSize(11);
            line.addView(install,new LinearLayout.LayoutParams(dp(96),dp(48)));
        }
    }

    private void importPbw(){
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.setType("*/*");
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(picker,IMPORT_PBW);
    }

    private void install(EmeryPackageIndex.PackageInfo item){
        if(EmeryRuntime.isDeviceLabActive()){
            showMessage("PebbleOS already running",
                    "Return from the current watch session before installing another PBW.");
            return;
        }
        if(!EmeryAssets.isBundled(this)){
            showMessage("Emulator unavailable",
                    "This APK does not contain the experimental Emery emulator.");
            return;
        }
        getSharedPreferences("time2-pbw-install",MODE_PRIVATE).edit()
                .putString("pending_uuid",item.uuid).apply();
        openWatch(false);
    }

    private void openWatch(boolean diagnostics){
        Intent intent=new Intent(this,RearActivity.class);
        intent.putExtra(RearActivity.EXTRA_DIAGNOSTICS,diagnostics);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void showSettings(){
        String[] options={
                "Watch controls (diagnostic mode)",
                "Back up PebbleOS memory",
                "Restore PebbleOS memory",
                "Export emulator diagnostics",
                "Build status / About"
        };
        new AlertDialog.Builder(this)
                .setTitle("TIME 2   /   SETTINGS")
                .setItems(options,(dialog,index)->{
                    switch(index){
                        case 0:openWatch(true);break;
                        case 1:backupMemory();break;
                        case 2:restoreMemory();break;
                        case 3:exportDiagnostics();break;
                        case 4:showMessage("NullWrist Time 2 / 0.5.0-lab",
                                "Real Emery firmware display was verified on Titan 2. "
                                +"Third-party watchface execution is still experimental. "
                                +"Triple-tap sends Pebble SELECT. "
                                +"All previous NullWrist releases and their data are separate.");
                                break;
                        default:break;
                    }
                }).setNegativeButton("CLOSE",null).show();
    }

    private void backupMemory(){
        if(EmeryRuntime.isDeviceLabActive()){
            showMessage("Emery is running","Close the watch session first.");return;
        }
        Intent choose=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        choose.addCategory(Intent.CATEGORY_OPENABLE);
        choose.setType("application/octet-stream");
        choose.putExtra(Intent.EXTRA_TITLE,"nullwrist-time2-emery-flash.bin");
        startActivityForResult(choose,EXPORT_SPI);
    }

    private void restoreMemory(){
        if(EmeryRuntime.isDeviceLabActive()){
            showMessage("Emery is running","Close the watch session first.");return;
        }
        new AlertDialog.Builder(this).setTitle("Restore watch memory?")
                .setMessage("Replaces the Time 2 settings, apps and watchfaces only. "
                        +"Does not touch legacy NullWrist.")
                .setNegativeButton("CANCEL",null)
                .setPositiveButton("CHOOSE BACKUP",(dialog,which)->{
                    Intent choose=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    choose.setType("*/*");
                    choose.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(choose,IMPORT_SPI);
                }).show();
    }

    private void exportDiagnostics(){
        File dir=new File(getFilesDir(),"time2-emery");
        if(!new File(dir,"emery-qemu.log").isFile() &&
                !new File(dir,"emery-host.log").isFile()){
            showMessage("No logs yet","Start PebbleOS once to generate diagnostics.");
            return;
        }
        Intent export=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        export.addCategory(Intent.CATEGORY_OPENABLE);
        export.setType("text/plain");
        export.putExtra(Intent.EXTRA_TITLE,"nullwrist-time2-emery-qemu.log");
        startActivityForResult(export,EXPORT_EMERY_LOG);
    }

    private GradientDrawable panel(int fill,int stroke){
        GradientDrawable background=new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(7));
        if(stroke!=0)background.setStroke(dp(1),stroke);
        return background;
    }

    private TextView action(String value,boolean primary,Runnable callback){
        TextView button=label(value,13,primary?BG:ORANGE,true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(panel(primary?ORANGE:CARD,primary?0:BORDER));
        button.setOnClickListener(v->callback.run());
        button.setClickable(true);
        button.setFocusable(true);
        button.setMinHeight(dp(48));
        return button;
    }
    private TextView text(LinearLayout parent,String value,int size,int color,
                          int gravity,int topMarginDp){
        TextView view=label(value,size,color,false);
        view.setGravity(gravity);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(topMarginDp);
        parent.addView(view,p);
        return view;
    }
    private TextView label(String value,int size,int color,boolean strong){
        TextView view=new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(Typeface.MONOSPACE,strong?Typeface.BOLD:Typeface.NORMAL);
        return view;
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null) return;
        if(request==EXPORT_SPI||request==IMPORT_SPI){
            final boolean backup=request==EXPORT_SPI;
            android.net.Uri selected=data.getData();
            new Thread(()->{
                try{
                    if(backup)EmeryFlashBackup.exportImage(this,selected);
                    else EmeryFlashBackup.restoreImage(this,selected);
                    runOnUiThread(()->showMessage("Emery memory",
                            backup?"Backup saved successfully. Store it safely.":
                                    "Memory restored. Launch the rear display to check the watch."));
                }catch(IOException problem){
                    runOnUiThread(()->showMessage(backup?"Backup failed":"Restore failed",
                            problem.getMessage()));
                }
            },"EmeryFlashTransfer").start();
            return;
        }
        if(request==EXPORT_EMERY_LOG){
            File log=new File(new File(getFilesDir(),"time2-emery"),"emery-qemu.log");
            File host=new File(new File(getFilesDir(),"time2-emery"),"emery-host.log");
            if(!log.isFile() && !host.isFile()){
                showMessage("No logs","Run the rear display first.");return;
            }
            try(OutputStream output=getContentResolver().openOutputStream(data.getData())){
                if(output==null)throw new IOException("Cannot write destination");
                appendLog(output,"ANDROID EMERY HOST",host);
                appendLog(output,"NATIVE QEMU",log);
                appendGuestRaw(output,new File(new File(getFilesDir(),"time2-emery"),
                        "emery-debug-uart2.bin"));
                showMessage("Diagnostic exported",
                        "Saved Android status, QEMU output and encoded guest UART2 logs.");
            }catch(IOException error){
                showMessage("Export failed",error.getMessage());
            }
            return;
        }
        if(request!=IMPORT_PBW) return;
        try {
            EmeryPackageIndex.PackageInfo info=library.importPbw(data.getData());
            new AlertDialog.Builder(this).setTitle("Imported")
                    .setMessage(info.name+"\n"+(info.watchface?"Watchface":"Watchapp")
                            +"\n\nStored in private library. Choose INSTALL / LAUNCH next to the imported app to try installation in real PebbleOS.")
                    .setPositiveButton("OK",null).show();
            render();
        } catch(IOException error) {
            new AlertDialog.Builder(this).setTitle("Import rejected")
                    .setMessage(error.getMessage()).setPositiveButton("OK",null).show();
        }
    }

    private void appendLog(OutputStream out,String label,File file) throws IOException {
        out.write(("\n=== "+label+" ===\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(!file.isFile()){
            out.write("(not created)\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return;
        }
        try(InputStream input=new FileInputStream(file)){
            long skip=Math.max(0,file.length()-256L*1024L);
            while(skip>0){
                long n=input.skip(skip);
                if(n<=0)throw new IOException("Cannot seek to log tail");
                skip-=n;
            }
            byte[] buffer=new byte[8192];
            int n;
            while((n=input.read(buffer))!=-1)out.write(buffer,0,n);
        }
    }

    private void appendGuestRaw(OutputStream out,File guest)throws IOException{
        out.write("\n=== PEBBLEOS DEBUG UART2 (base64, last 48 KiB) ===\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(!guest.isFile()){
            out.write("(not created)\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return;
        }
        byte[] tail;
        try(InputStream in=new FileInputStream(guest);
            java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream()){
            long skipped=Math.max(0,guest.length()-48L*1024L);
            while(skipped>0){
                long n=in.skip(skipped);
                if(n<=0)throw new IOException("Cannot seek to guest log tail");
                skipped-=n;
            }
            byte[] chunk=new byte[8192];int n;
            while((n=in.read(chunk))!=-1){
                if(b.size()+n>48*1024)throw new IOException("Debug UART expanded unexpectedly");
                b.write(chunk,0,n);
            }
            tail=b.toByteArray();
        }
        out.write((android.util.Base64.encodeToString(tail,android.util.Base64.NO_WRAP)
                +"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private void showMessage(String title,String content){
        new AlertDialog.Builder(this).setTitle(title).setMessage(content)
                .setPositiveButton("OK",null).show();
    }


    private int dp(float value){
        return (int)(value*getResources().getDisplayMetrics().density+.5f);
    }
}

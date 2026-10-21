package com.manufacttest.nullwrist.time2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/** Fresh app shell. Never claims an emulator was started before native bootstrap exists. */
public final class MainActivity extends Activity {
    private static final int IMPORT_PBW = 26;
    private static final int EXPORT_EMERY_LOG = 27;
    private static final int BG = Color.rgb(8,9,13);
    private static final int ORANGE = Color.rgb(255,109,0);
    private static final int WHITE = Color.rgb(235,236,232);
    private LinearLayout layout;
    private LocalPbwLibrary library;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Display display=getDisplay();
        int width=getResources().getDisplayMetrics().widthPixels;
        int height=getResources().getDisplayMetrics().heightPixels;
        boolean secondary=display!=null && display.getDisplayId()!=Display.DEFAULT_DISPLAY;
        if (secondary || (width<=700 && height<=820)) {
            Intent rear=new Intent(this,RearActivity.class);
            startActivity(rear);
            finish();
            return;
        }
        library=new LocalPbwLibrary(this);
        render();
    }

    private void render() {
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(20);
        layout.setPadding(pad,dp(24),pad,dp(40));
        scroll.addView(layout);
        setContentView(scroll);

        PixelWordmarkView logo=new PixelWordmarkView(this);
        layout.addView(logo,new LinearLayout.LayoutParams(-1,dp(54)));
        addText("TIME 2  //  EMERY EDITION",14,ORANGE,Gravity.CENTER,dp(2));
        addText("VIRTUAL PEBBLEOS ON TITAN 2",11,WHITE,Gravity.CENTER,dp(10));

        section("01 / CORE STATUS");
        boolean lab=EmeryAssets.isBundled(this);
        addText(lab?"EMERY ENGINE: DEVICE LAB ASSETS PRESENT":
                    "EMERY ENGINE: NOT BUNDLED",15,ORANGE,Gravity.START,dp(8));
        addText(lab?"Experimental native firmware/QEMU bundle present. Boots in foreground and needs physical Titan 2 validation before claiming runtime support.":
                    "The ordinary source build contains no native QEMU or Emery firmware. The rear screen is an input-only preview.",12,WHITE,Gravity.START,dp(10));
        button(lab?"BOOT EMERY (DEVICE LAB)":"OPEN REAR INPUT PREVIEW",
                    ()->startActivity(new Intent(this,RearActivity.class)));

        section("02 / WATCH APPS & FACES");
        button("IMPORT EMERY .PBW",()->{
            Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.setType("*/*");
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(picker,IMPORT_PBW);
        });
        List<EmeryPackageIndex.PackageInfo> entries=library.list();
        addText("LOCAL LIBRARY: "+entries.size()+" PACKAGE(S)",12,ORANGE,Gravity.START,dp(6));
        for (EmeryPackageIndex.PackageInfo entry:entries) {
            addText((entry.watchface?"[FACE] ":"[APP] ")+entry.name
                    +"\n"+entry.author+" / "+entry.uuid,11,WHITE,Gravity.START,dp(8));
        }
        if(entries.isEmpty())
            addText("No Emery PBWs imported. Imported files are only inspected and stored; they cannot run until the runtime is integrated.",11,WHITE,Gravity.START,dp(6));

        section("03 / SAFETY");
        addText("Independent application ID / private storage. Does not alter or migrate the existing NullWrist 0.8.12. No network or notification access requested.",11,WHITE,Gravity.START,dp(8));
        button("EXPORT EMERY DIAGNOSTICS",()->{
            File log = new File(new File(getFilesDir(),"time2-emery"),"emery-qemu.log");
            if(!log.isFile()){
                showMessage("No log yet","Open the rear display once in the device-lab APK to generate a QEMU log.");
                return;
            }
            Intent export = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            export.addCategory(Intent.CATEGORY_OPENABLE);
            export.setType("text/plain");
            export.putExtra(Intent.EXTRA_TITLE,"nullwrist-time2-emery-qemu.log");
            startActivityForResult(export,EXPORT_EMERY_LOG);
        });
        button("VIEW BUILD STATUS",()->new AlertDialog.Builder(this)
                .setTitle("NullWrist Time 2 / 0.1.0-dev")
                .setMessage("Completed: project isolation, raster branding, Emery display mapping, touch diagnostics and PBW library.\n\nNot completed: on-device Emery boot verification, app execution, phone services, hardware stability tests. Device-lab APK bundles an experimental runtime; ordinary APK remains preview-only.")
                .setPositiveButton("OK",null).show());
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null) return;
        if(request==EXPORT_EMERY_LOG){
            File log=new File(new File(getFilesDir(),"time2-emery"),"emery-qemu.log");
            if(!log.isFile()){showMessage("No log","QEMU log not available.");return;}
            try(InputStream input=new FileInputStream(log);
                OutputStream output=getContentResolver().openOutputStream(data.getData())){
                if(output==null)throw new IOException("Cannot write destination");
                // Export only the most recent 512 KiB to avoid huge diagnostic files.
                long skip=Math.max(0,log.length()-512L*1024L);
                while(skip>0){
                    long jumped=input.skip(skip);
                    if(jumped<=0)throw new IOException("Cannot seek to log tail");
                    skip-=jumped;
                }
                byte[] buffer=new byte[8192];
                int n;
                while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
                showMessage("Diagnostic exported","Saved the latest QEMU log to your selected location.");
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
                            +"\n\nStored safely. Execution requires the future Emery runtime.")
                    .setPositiveButton("OK",null).show();
            render();
        } catch(IOException error) {
            new AlertDialog.Builder(this).setTitle("Import rejected")
                    .setMessage(error.getMessage()).setPositiveButton("OK",null).show();
        }
    }

    private void showMessage(String title,String content){
        new AlertDialog.Builder(this).setTitle(title).setMessage(content)
                .setPositiveButton("OK",null).show();
    }

    private void section(String title) {
        TextView label=text(title,13,ORANGE,Gravity.START);
        label.setPadding(0,dp(20),0,dp(12));
        layout.addView(label);
        View line=new View(this);
        line.setBackgroundColor(ORANGE);
        layout.addView(line,new LinearLayout.LayoutParams(-1,dp(2)));
    }
    private void addText(String value,int size,int color,int gravity,int topMargin) {
        TextView view=text(value,size,color,gravity);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=topMargin; layout.addView(view,params);
    }
    private TextView text(String text,int size,int color,int gravity) {
        TextView view=new TextView(this);
        view.setText(text); view.setTextColor(color); view.setTextSize(size);
        view.setGravity(gravity); view.setTypeface(Typeface.MONOSPACE);
        view.setLineSpacing(dp(4),1f);
        return view;
    }
    private void button(String text,Runnable callback) {
        Button button=new Button(this);
        button.setAllCaps(false);
        button.setText(text); button.setTextColor(BG);
        button.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        button.setTextSize(12);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(ORANGE); bg.setCornerRadius(0);
        button.setBackground(bg);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(55));
        params.topMargin=dp(12);
        layout.addView(button,params);
        button.setOnClickListener(v->callback.run());
    }
    private int dp(float x) { return (int)(x*getResources().getDisplayMetrics().density+.5f); }
}

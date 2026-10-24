package com.manufacttest.nullwrist.time2;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Separate private PBW library. Does not share Basalt data or paths. */
public final class LocalPbwLibrary {
    private final Context context;
    private final File directory;
    public LocalPbwLibrary(Context context) {
        this.context=context.getApplicationContext();
        this.directory=new File(context.getFilesDir(),"time2/pbws");
    }

    public EmeryPackageIndex.PackageInfo importPbw(Uri uri) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Cannot create PBW library");
        File temp=File.createTempFile("import-", ".tmp", directory);
        try {
            try (InputStream input=context.getContentResolver().openInputStream(uri);
                 FileOutputStream output=new FileOutputStream(temp)) {
                if (input == null) throw new IOException("Unable to open selected PBW");
                byte[] buffer=new byte[8192];
                long written=0;
                int n;
                while ((n=input.read(buffer))!=-1) {
                    written+=n;
                    if (written>EmeryPackageIndex.MAX_PBW_BYTES)
                        throw new IOException("PBW exceeds 20 MB");
                    output.write(buffer,0,n);
                }
                output.getFD().sync();
            }
            EmeryPackageIndex.PackageInfo info=EmeryPackageIndex.inspect(temp);
            File destination=new File(directory,info.uuid+".pbw");
            if (destination.exists()) throw new IOException("Already imported: "+info.name);
            if (!temp.renameTo(destination)) throw new IOException("Unable to store PBW");
            return info;
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    public List<EmeryPackageIndex.PackageInfo> list() {
        List<EmeryPackageIndex.PackageInfo> entries=new ArrayList<>();
        File[] files=directory.listFiles((dir,name)->name.endsWith(".pbw"));
        if (files==null) return entries;
        for (File file:files) {
            try { entries.add(EmeryPackageIndex.inspect(file)); }
            catch (IOException ignored) { /* invalid entries are not rendered as installed */ }
        }
        return entries;
    }
}

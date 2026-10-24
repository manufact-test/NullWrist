package com.manufacttest.nullwrist.time2;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Private editable PBW library. A starter pack is optional, bundled only in
 * device-local packages with permission to distribute the original PBWs.
 * The public-source lab build still works with no starter asset folder.
 */
public final class LocalPbwLibrary {
    private static final String STARTER_FOLDER="starter-pbws/";
    private final Context context;
    private final File directory;
    private final SharedPreferences catalog;

    public LocalPbwLibrary(Context context) {
        this.context=context.getApplicationContext();
        directory=new File(context.getFilesDir(),"time2/pbws");
        catalog=this.context.getSharedPreferences("time2-starter-catalog",Context.MODE_PRIVATE);
        try{seedStarterPack();}catch(IOException ignored){
            // Failed/absent optional starter never blocks user PBW imports.
        }
    }

    /** Seed once, preserve user imports, and honour deletion tombstones. */
    public synchronized void seedStarterPack() throws IOException {
        if(!directory.isDirectory()&&!directory.mkdirs())
            throw new IOException("Cannot create PBW library");
        try(InputStream stream=context.getAssets().open(STARTER_FOLDER+"index.txt");
            Scanner scanner=new Scanner(stream,StandardCharsets.UTF_8.name())){
            while(scanner.hasNextLine()){
                String line=scanner.nextLine().trim();
                if(line.isEmpty()||line.startsWith("#"))continue;
                String[] fields=line.split("\\s+");
                if(fields.length!=2||!fields[0].matches("[0-9a-fA-F-]{36}")||
                        !fields[1].matches("[0-9a-fA-F]{64}"))continue;
                String id;
                try{id=java.util.UUID.fromString(fields[0]).toString();}
                catch(IllegalArgumentException malformed){continue;}
                if(catalog.getBoolean("deleted_"+id,false))continue;
                File target=new File(directory,id+".pbw");
                if(target.isFile())continue;
                File temp=File.createTempFile("starter-", ".tmp", directory);
                try{
                    MessageDigest sha=MessageDigest.getInstance("SHA-256");
                    long size=0;
                    try(InputStream source=context.getAssets().open(STARTER_FOLDER+id+".pbw");
                        FileOutputStream out=new FileOutputStream(temp)){
                        byte[] buffer=new byte[8192];int n;
                        while((n=source.read(buffer))!=-1){
                            size+=n;
                            if(size>EmeryPackageIndex.MAX_PBW_BYTES)
                                throw new IOException("Oversized bundled PBW");
                            out.write(buffer,0,n);
                            sha.update(buffer,0,n);
                        }
                        out.getFD().sync();
                    }
                    if(!toHex(sha.digest()).equalsIgnoreCase(fields[1]))
                        throw new IOException("Bundled PBW SHA mismatch: "+id);
                    EmeryPackageIndex.PackageInfo info=EmeryPackageIndex.inspect(temp);
                    if(!info.uuid.equals(id))throw new IOException("Bundled PBW UUID mismatch");
                    if(!target.exists()&&!temp.renameTo(target))
                        throw new IOException("Unable to stage starter PBW");
                }catch(NoSuchAlgorithmException missing){
                    throw new IOException("SHA256 unavailable",missing);
                }finally{
                    if(temp.exists())temp.delete();
                }
            }
        }catch(java.io.FileNotFoundException optional){
            // GitHub/source-only debug APK may intentionally contain no starter.
        }
    }

    private static String toHex(byte[] digest){
        StringBuilder out=new StringBuilder(64);
        for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }

    public synchronized EmeryPackageIndex.PackageInfo importPbw(Uri uri)throws IOException {
        if(!directory.isDirectory()&&!directory.mkdirs())
            throw new IOException("Cannot create PBW library");
        File temp=File.createTempFile("import-", ".tmp", directory);
        try {
            try(InputStream input=context.getContentResolver().openInputStream(uri);
                FileOutputStream output=new FileOutputStream(temp)){
                if(input==null)throw new IOException("Unable to open selected PBW");
                byte[] buffer=new byte[8192];long written=0;int n;
                while((n=input.read(buffer))!=-1){
                    written+=n;
                    if(written>EmeryPackageIndex.MAX_PBW_BYTES)
                        throw new IOException("PBW exceeds 20 MB");
                    output.write(buffer,0,n);
                }
                output.getFD().sync();
            }
            EmeryPackageIndex.PackageInfo info=EmeryPackageIndex.inspect(temp);
            File destination=new File(directory,info.uuid+".pbw");
            if(destination.exists())throw new IOException("Already in your library: "+info.name);
            if(!temp.renameTo(destination))throw new IOException("Unable to store PBW");
            catalog.edit().remove("deleted_"+info.uuid).apply();
            return info;
        }finally{if(temp.exists())temp.delete();}
    }

    public synchronized void removePbw(String uuid)throws IOException {
        final String id;
        try{id=java.util.UUID.fromString(uuid).toString();}
        catch(IllegalArgumentException e){throw new IOException("Invalid PBW UUID",e);}
        File file=new File(directory,id+".pbw");
        if(file.exists()&&!file.delete())throw new IOException("Cannot delete PBW file");
        // Don't let the included starter pack silently reinstall deleted faces.
        catalog.edit().putBoolean("deleted_"+id,true).apply();
    }

    public synchronized List<EmeryPackageIndex.PackageInfo> list(){
        List<EmeryPackageIndex.PackageInfo> entries=new ArrayList<>();
        File[] files=directory.listFiles((dir,name)->name.endsWith(".pbw"));
        if(files==null)return entries;
        for(File file:files){
            try{entries.add(EmeryPackageIndex.inspect(file));}
            catch(IOException ignored){ /* invalid imports are not displayed as installed */ }
        }
        return entries;
    }
}

package fr.mediclogger.app;

import android.content.Context;
import android.database.Cursor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import net.zetetic.database.DatabaseErrorHandler;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

/** Converts legacy storage before any activity or notification receiver opens it. */
final class StorageMigration {
    static final DatabaseErrorHandler KEEP_DATA=(db,error)->{throw new SQLiteException("Database unavailable. Existing data was kept.");};
    private static boolean loaded;
    private StorageMigration(){}

    static synchronized void initialize(Context c){
        keyForDatabase(c,"medic-logger.db");
        AppPrefs.get(c);AppPrefs.exports(c);
        // Pending exports from older versions were plaintext in the private files directory.
        File[] files=c.getFilesDir().listFiles((directory,name)->name.startsWith("pending-export-")&&!name.endsWith(".encrypting"));
        if(files!=null)for(File file:files){
            File replacement=new File(file.getPath()+".encrypting");
            try{
                if(CryptoBox.encrypted(file))continue;
                try(InputStream in=new FileInputStream(file);OutputStream out=CryptoBox.exportOutput(replacement)){
                    byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                sync(replacement);replace(replacement,file);
            }catch(IOException failure){throw new IllegalStateException("Could not encrypt an unfinished export. Existing data was kept.",failure);}
            finally{replacement.delete();}
        }
    }
    static synchronized byte[] keyForDatabase(Context c,String name){
        if(!loaded){System.loadLibrary("sqlcipher");loaded=true;}
        byte[] password=CryptoBox.databasePassword(c);File database=c.getDatabasePath(name);
        try{if(plaintext(database))encryptDatabase(database,password);return password;}
        catch(IOException|RuntimeException failure){Arrays.fill(password,(byte)0);throw new IllegalStateException("Could not encrypt the database. Existing data was kept.",failure);}
    }
    private static boolean plaintext(File database) throws IOException {
        if(!database.exists()||database.length()==0)return false;
        byte[] expected="SQLite format 3\0".getBytes(StandardCharsets.US_ASCII),header=new byte[expected.length];
        try(FileInputStream in=new FileInputStream(database)){return in.read(header)==header.length&&Arrays.equals(expected,header);}
    }
    private static void encryptDatabase(File database,byte[] password) throws IOException {
        File encrypted=new File(database.getPath()+".encrypted-new");deleteSidecars(encrypted);Files.deleteIfExists(encrypted.toPath());
        try{
            // The plaintext source stays in place until the entire encrypted copy has been closed.
            // SQLite propagates the CREATE flag to ATTACH. The source already exists (its
            // plaintext header was checked), but the encrypted destination is a new file.
            try(SQLiteDatabase source=SQLiteDatabase.openDatabase(database.getPath(),new byte[0],null,SQLiteDatabase.OPEN_READWRITE|SQLiteDatabase.CREATE_IF_NECESSARY,KEEP_DATA,null)){
                // Fold in any legacy WAL first; close all source handles before replacing it.
                try(Cursor checkpoint=source.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null)){
                    if(checkpoint.moveToFirst()&&checkpoint.getInt(0)!=0)throw new IOException("Database is busy.");
                }
                int version=source.getVersion();
                // execSQL tracks attached databases and disables the multi-connection WAL pool.
                // rawExecSQL for ATTACH bypasses that bookkeeping: a later read may use another
                // connection which has never attached the encrypted destination.
                source.execSQL("ATTACH DATABASE ? AS encrypted KEY ?",new Object[]{encrypted.getPath(),new String(password,StandardCharsets.US_ASCII)});
                try{
                    // sqlcipher_export mutates the destination; run it on the write connection.
                    source.rawExecSQL("SELECT sqlcipher_export('encrypted')");
                    source.execSQL("PRAGMA encrypted.user_version="+version);
                }finally{source.execSQL("DETACH DATABASE encrypted");}
            }
            // Refuse the replacement if the copied pages cannot be authenticated/read.
            try(SQLiteDatabase copy=SQLiteDatabase.openDatabase(encrypted.getPath(),password,null,SQLiteDatabase.OPEN_READONLY,KEEP_DATA,null);
                Cursor integrity=copy.rawQuery("PRAGMA integrity_check",null)){
                if(!integrity.moveToFirst()||!"ok".equals(integrity.getString(0)))throw new IOException("Encrypted copy is incomplete.");
            }
            sync(encrypted);deleteSidecars(database);replace(encrypted,database);
        }finally{deleteSidecars(encrypted);Files.deleteIfExists(encrypted.toPath());}
    }
    private static void deleteSidecars(File database) throws IOException {for(String suffix:new String[]{"-wal","-shm","-journal"})Files.deleteIfExists(new File(database.getPath()+suffix).toPath());}
    private static void sync(File file) throws IOException {try(FileOutputStream out=new FileOutputStream(file,true)){out.getFD().sync();}}
    private static void replace(File from,File to) throws IOException {
        Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
}

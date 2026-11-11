package fr.mediclogger.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Private app files use authenticated AES-256-GCM; the master key never leaves Android Keystore. */
final class CryptoBox {
    private static final String ALIAS="medic-logger.storage.v1";
    private static final byte[] MAGIC={'M','L','A','E','A','D',1,0};
    private static final int IV_SIZE=12;
    private static SecretKey master;
    private CryptoBox(){}

    private static synchronized SecretKey master() throws GeneralSecurityException,IOException {
        if(master!=null)return master;
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS)){
            master=(SecretKey)store.getKey(ALIAS,null);
        }else{
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
            // No biometric/unlocked-device requirement: scheduled reminders must also work while locked.
            master=generator.generateKey();
        }
        return master;
    }
    private static Cipher cipher(int mode,byte[] iv,String purpose) throws GeneralSecurityException,IOException {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        if(mode==Cipher.ENCRYPT_MODE)cipher.init(mode,master());
        else cipher.init(mode,master(),new GCMParameterSpec(128,iv));
        cipher.updateAAD(MAGIC);cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));return cipher;
    }
    static byte[] seal(byte[] plain,String purpose) throws IOException {
        try{
            Cipher cipher=cipher(Cipher.ENCRYPT_MODE,null,purpose);
            ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(MAGIC);out.write(cipher.getIV());out.write(cipher.doFinal(plain));return out.toByteArray();
        }catch(GeneralSecurityException e){throw new IOException("Could not encrypt app data.",e);}
    }
    static byte[] open(byte[] encoded,String purpose) throws IOException {
        try{
            if(encoded.length<MAGIC.length+IV_SIZE+16)throw new IOException("Incomplete encrypted app data.");
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(encoded));byte[] iv=readHeader(in);
            return cipher(Cipher.DECRYPT_MODE,iv,purpose).doFinal(encoded,MAGIC.length+IV_SIZE,encoded.length-MAGIC.length-IV_SIZE);
        }catch(GeneralSecurityException e){throw new IOException("Could not decrypt app data.",e);}
    }
    private static byte[] readHeader(DataInputStream in) throws IOException {
        byte[] magic=new byte[MAGIC.length];in.readFully(magic);
        if(!Arrays.equals(magic,MAGIC))throw new IOException("Unknown encrypted app data format.");
        byte[] iv=new byte[IV_SIZE];in.readFully(iv);return iv;
    }
    static void writeAtomic(AtomicFile file,byte[] plain,String purpose) throws IOException {
        byte[] encrypted=seal(plain,purpose);FileOutputStream out=null;
        try{out=file.startWrite();out.write(encrypted);file.finishWrite(out);}
        catch(IOException failure){if(out!=null)file.failWrite(out);throw failure;}
    }
    static byte[] readAtomic(AtomicFile file,String purpose) throws IOException {return open(file.readFully(),purpose);}
    static boolean exists(AtomicFile file){return file.getBaseFile().exists()||new File(file.getBaseFile().getPath()+".bak").exists();}
    static OutputStream exportOutput(File file) throws IOException {
        FileOutputStream out=new FileOutputStream(file);
        try{Cipher cipher=cipher(Cipher.ENCRYPT_MODE,null,"pending-export");out.write(MAGIC);out.write(cipher.getIV());return new CipherOutputStream(out,cipher);}
        catch(GeneralSecurityException|IOException failure){out.close();throw new IOException("Could not encrypt export preparation.",failure);}
    }
    static InputStream exportInput(File file) throws IOException {
        FileInputStream in=new FileInputStream(file);
        try{byte[] iv=readHeader(new DataInputStream(in));return new CipherInputStream(in,cipher(Cipher.DECRYPT_MODE,iv,"pending-export"));}
        catch(GeneralSecurityException|IOException failure){in.close();throw new IOException("Could not open export preparation.",failure);}
    }
    static boolean encrypted(File file) throws IOException {
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))){byte[] prefix=new byte[MAGIC.length];return in.read(prefix)==MAGIC.length&&Arrays.equals(prefix,MAGIC);}
    }
    static synchronized byte[] databasePassword(Context context) {
        AtomicFile file=new AtomicFile(new File(context.getNoBackupFilesDir(),"database-key.enc"));
        try{
            if(exists(file))return readAtomic(file,"database-key");
            // A uniformly random 256-bit SQLCipher raw key avoids a human-password KDF on each open.
            byte[] random=new byte[32];new SecureRandom().nextBytes(random);
            StringBuilder raw=new StringBuilder("x'");for(byte b:random)raw.append(String.format(java.util.Locale.ROOT,"%02x",b&255));raw.append('\'');
            Arrays.fill(random,(byte)0);byte[] password=raw.toString().getBytes(StandardCharsets.US_ASCII);
            writeAtomic(file,password,"database-key");return password;
        }catch(IOException failure){throw new IllegalStateException("Encrypted storage is unavailable. Existing data was kept.",failure);}
    }
}

package fr.mediclogger.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Small, atomic, authenticated settings files. Keys and values are both encrypted. */
final class SecurePreferences implements SharedPreferences {
    private static final Map<String,SecurePreferences> INSTANCES=new HashMap<>();
    private final AtomicFile file;
    private final String purpose;
    private Map<String,Object> values;
    private final Set<OnSharedPreferenceChangeListener> listeners=Collections.newSetFromMap(new WeakHashMap<>());

    static synchronized SharedPreferences get(Context context,String name,String legacyName){
        String id=context.getPackageName()+":"+name;
        SecurePreferences prefs=INSTANCES.get(id);
        if(prefs==null){prefs=new SecurePreferences(context.getApplicationContext(),name,legacyName);INSTANCES.put(id,prefs);}
        return prefs;
    }
    private SecurePreferences(Context c,String name,String legacyName){
        file=new AtomicFile(new File(c.getNoBackupFilesDir(),name+".enc"));purpose="preferences:"+name;
        try{
            if(CryptoBox.exists(file))values=decode(CryptoBox.readAtomic(file,purpose));
            else{
                values=copy(c.getSharedPreferences(legacyName,Context.MODE_PRIVATE).getAll());
                CryptoBox.writeAtomic(file,encode(values),purpose);
            }
            // Remove plaintext XML and its backup only after the encrypted replacement is durable.
            if(!c.deleteSharedPreferences(legacyName))throw new IOException("Could not remove legacy settings.");
        }catch(Exception failure){throw new IllegalStateException("Encrypted settings are unavailable. Existing data was kept.",failure);}
    }
    private static Object cloneValue(Object value){return value instanceof Set<?>?new HashSet<>((Set<?>)value):value;}
    private static Map<String,Object> copy(Map<String,?> source){Map<String,Object> out=new HashMap<>();for(Map.Entry<String,?> item:source.entrySet())out.put(item.getKey(),cloneValue(item.getValue()));return out;}
    private static byte[] encode(Map<String,Object> source) throws IOException {
        try{
            JSONObject object=new JSONObject();
            for(Map.Entry<String,Object> entry:source.entrySet()){
                Object value=entry.getValue();String type;
                if(value instanceof String)type="string";
                else if(value instanceof Boolean)type="boolean";
                else if(value instanceof Integer)type="int";
                else if(value instanceof Long)type="long";
                else if(value instanceof Float)type="float";
                else if(value instanceof Set<?>)type="set";
                else throw new IOException("Unsupported setting type.");
                object.put(entry.getKey(),new JSONArray().put(type).put(value instanceof Set<?>?new JSONArray((Set<?>)value):value));
            }
            return object.toString().getBytes(StandardCharsets.UTF_8);
        }catch(org.json.JSONException failure){throw new IOException("Could not encode settings.",failure);}
    }
    private static Map<String,Object> decode(byte[] plain) throws Exception {
        JSONObject object=new JSONObject(new String(plain,StandardCharsets.UTF_8));Map<String,Object> out=new HashMap<>();
        java.util.Iterator<String> keys=object.keys();
        while(keys.hasNext()){
            String key=keys.next();JSONArray item=object.getJSONArray(key);Object value;
            switch(item.getString(0)){
                case "string":value=item.getString(1);break;
                case "boolean":value=item.getBoolean(1);break;
                case "int":value=item.getInt(1);break;
                case "long":value=item.getLong(1);break;
                case "float":value=(float)item.getDouble(1);break;
                case "set":Set<String> set=new HashSet<>();JSONArray array=item.getJSONArray(1);for(int i=0;i<array.length();i++)set.add(array.getString(i));value=set;break;
                default:throw new IOException("Unknown setting type.");
            }
            out.put(key,value);
        }
        return out;
    }
    @Override public synchronized Map<String,?> getAll(){return copy(values);}
    @Override public synchronized String getString(String key,String fallback){return values.containsKey(key)?(String)values.get(key):fallback;}
    @SuppressWarnings("unchecked") @Override public synchronized Set<String> getStringSet(String key,Set<String> fallback){return values.containsKey(key)?new HashSet<>((Set<String>)values.get(key)):fallback;}
    @Override public synchronized int getInt(String key,int fallback){return values.containsKey(key)?(Integer)values.get(key):fallback;}
    @Override public synchronized long getLong(String key,long fallback){return values.containsKey(key)?(Long)values.get(key):fallback;}
    @Override public synchronized float getFloat(String key,float fallback){return values.containsKey(key)?(Float)values.get(key):fallback;}
    @Override public synchronized boolean getBoolean(String key,boolean fallback){return values.containsKey(key)?(Boolean)values.get(key):fallback;}
    @Override public synchronized boolean contains(String key){return values.containsKey(key);}
    @Override public Editor edit(){return new Changes();}
    @Override public synchronized void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){listeners.add(listener);}
    @Override public synchronized void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){listeners.remove(listener);}

    private final class Changes implements Editor {
        private final Map<String,Object> changes=new LinkedHashMap<>();private boolean clear;
        private Editor put(String key,Object value){changes.put(key,cloneValue(value));return this;}
        @Override public Editor putString(String key,String value){return put(key,value);}
        @Override public Editor putStringSet(String key,Set<String> value){return put(key,value);}
        @Override public Editor putInt(String key,int value){return put(key,value);}
        @Override public Editor putLong(String key,long value){return put(key,value);}
        @Override public Editor putFloat(String key,float value){return put(key,value);}
        @Override public Editor putBoolean(String key,boolean value){return put(key,value);}
        @Override public Editor remove(String key){return put(key,null);}
        @Override public Editor clear(){clear=true;return this;}
        @Override public void apply(){if(!commit())throw new IllegalStateException("Could not save encrypted settings.");}
        @Override public boolean commit(){
            List<String> changed=new ArrayList<>();List<OnSharedPreferenceChangeListener> observers;
            synchronized(SecurePreferences.this){
                Map<String,Object> next=clear?new HashMap<>():copy(values);
                if(clear)changed.addAll(values.keySet());
                for(Map.Entry<String,Object> change:changes.entrySet()){
                    String key=change.getKey();Object value=change.getValue();
                    if(!java.util.Objects.equals(next.get(key),value))changed.add(key);
                    if(value==null)next.remove(key);else next.put(key,cloneValue(value));
                }
                if(changed.isEmpty())return true;
                try{CryptoBox.writeAtomic(file,encode(next),purpose);}catch(IOException failure){return false;}
                values=next;observers=new ArrayList<>(listeners);
            }
            Runnable notify=()->{for(String key:changed)for(OnSharedPreferenceChangeListener observer:observers)observer.onSharedPreferenceChanged(SecurePreferences.this,key);};
            if(Looper.myLooper()==Looper.getMainLooper())notify.run();else new Handler(Looper.getMainLooper()).post(notify);
            return true;
        }
    }
}

package fr.mediclogger.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AppPrefs {
    private AppPrefs() {}
    public static SharedPreferences get(Context c){return SecurePreferences.get(c,"settings","medic-settings");}
    public static SharedPreferences exports(Context c){return SecurePreferences.get(c,"exports","MainActivity");}
    public static String language(Context c){return get(c).getString("language","en");}
    public static Locale locale(Context c){return Locale.forLanguageTag(switch(language(c)){case "fr"->"fr-FR";case "pt"->"pt-PT";case "zh"->"zh-CN";default->"en-GB";});}
    public static Context localized(Context c){Configuration config=new Configuration(c.getResources().getConfiguration());config.setLocale(locale(c));config.setLayoutDirection(locale(c));return c.createConfigurationContext(config);}
    public static boolean notifications(Context c){return get(c).getBoolean("notifications",false);}
    public static List<Integer> colors(Context c){
        List<Integer> out=new ArrayList<>();
        try{JSONArray array=new JSONArray(get(c).getString("custom_colors","[]"));for(int i=0;i<array.length();i++)out.add(array.getInt(i));}catch(Exception ignored){}
        return out;
    }
    public static void addColor(Context c,int color){List<Integer> list=colors(c);if(!list.contains(color))list.add(color);JSONArray array=new JSONArray();for(int item:list)array.put(item);get(c).edit().putString("custom_colors",array.toString()).apply();}
}

package fr.mediclogger.app;

import android.content.Context;
import java.io.File;
import org.json.JSONArray;

/** Called only by the explicitly selected cleanup_fixtures instrumentation mode. */
final class FixtureCleanup {
    static int clean(Context c){
        int removed=0;
        try(LogStore db=new LogStore(c)){
            String[] expected={"TEST Alpha","TEST Beta"};
            for(int i=0;i<expected.length;i++){
                Models.Medicine m=db.medicine(i+1);
                if(m==null||!m.name.equals(expected[i]))continue;
                for(Models.Session s:db.sessions())if(s.medicineId==m.id)db.deleteSession(s.id);
                db.deleteMedicine(m.id);removed++;
            }
            JSONArray palette=new JSONArray();for(int color:AppPrefs.colors(c))if(color!=0xffc93048)palette.put(color);
            AppPrefs.get(c).edit().putString("custom_colors",palette.toString()).commit();
            android.content.pm.ShortcutManager shortcuts=c.getSystemService(android.content.pm.ShortcutManager.class);
            if(shortcuts!=null){shortcuts.removeDynamicShortcuts(java.util.Arrays.asList("medicine-1","medicine-2"));shortcuts.disableShortcuts(java.util.Arrays.asList("medicine-1","medicine-2"),"Test fixture removed");}
            File folder=new File(c.getFilesDir(),"instrumentation-exports");for(String ext:new String[]{"json","csv","xlsx","zip"})new File(folder,"device-test."+ext).delete();folder.delete();
            StatusNotifications.update(c);
        }
        return removed;
    }
}

package fr.mediclogger.app;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import fr.mediclogger.app.Models.Medicine;
import fr.mediclogger.app.Models.Session;
import fr.mediclogger.app.Models.ExportData;

/** Tests a separate database on the physical Android device. Never clears the real app database. */
public class LocalTestRunner extends Instrumentation {
    private int checks;
    private boolean cleanupOnly;
    @Override public void onCreate(Bundle args){super.onCreate(args);cleanupOnly=args!=null&&"true".equals(args.getString("cleanup_fixtures"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Context context=getTargetContext();String name="instrumentation-only.db";
        if(!context.getPackageName().equals("fr.mediclogger.app.qa")){result.putString("stream","FAIL: device tests may only run in the isolated QA application.\n");finish(Activity.RESULT_CANCELED,result);return;}
        if(cleanupOnly){try{int count=FixtureCleanup.clean(context);result.putString("stream","Removed "+count+" owned UI fixtures; other records preserved.\n");finish(Activity.RESULT_OK,result);}catch(Throwable e){result.putString("stream","Cleanup failed: "+e);finish(Activity.RESULT_CANCELED,result);}return;}
        context.deleteDatabase(name);int resultCode=Activity.RESULT_CANCELED;
        try(LogStore db=new LogStore(context,name)){
            Medicine first=new Medicine();first.name="Test medicine α";first.alias="AM";first.dose="10 mg";first.color=0xff187b68;first.monitorEffects=true;db.saveMedicine(first);
            Medicine second=new Medicine();second.name="Test medicine β";second.alias="PM";second.dose="1 tablet";second.color=0xff5377ae;second.monitorEffects=true;db.saveMedicine(second);
            Session one=db.take(first.id);Session two=db.take(second.id);Session three=db.take(first.id);
            check(db.sessions().size()==3,"Multiple simultaneous doses including the same medicine");
            db.mark(one.id,true);check(db.session(one.id).onsetAt!=null,"Effect onset saved");
            db.mark(one.id,false);check(db.session(one.id).status().equals("completed"),"Effect end saved");
            check(db.session(two.id).active() && db.session(three.id).active(),"Other doses remain active");
            boolean rejected=false;try{db.mark(one.id,true);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Completed doses reject extra events");
            db.mark(two.id,false);check(db.session(two.id).status().equals("closed_without_effect"),"No-effect closure");
            first.name="Renamed medicine";first.alias="NEW";db.saveMedicine(first);check(db.session(one.id).medicineName.equals("Test medicine α"),"Historical names are snapshots");
            Session edited=db.session(one.id);edited.notes="Test note, \"quotes\"\nSecond line";db.saveSession(edited);check(db.session(one.id).notes.equals(edited.notes),"Notes survive database round trip");
            edited.onsetAt=edited.takenAt-1;rejected=false;try{db.saveSession(edited);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Invalid times rejected without writing");
            check(db.session(one.id).onsetAt>=db.session(one.id).takenAt,"Failed edit preserves stored dose");
            second.archived=true;db.saveMedicine(second);check(db.medicines(false).size()==1 && db.medicines(true).size()==2,"Archive keeps history");
            rejected=false;try{db.take(second.id);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Archived medicines cannot be logged");
            Medicine duplicate=new Medicine();duplicate.name=first.name;duplicate.dose=first.dose;rejected=false;try{db.saveMedicine(duplicate);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Duplicate medicine rejected");
            ExportData export=new ExportData(db.sessions(),LocalDate.now().minusDays(1),LocalDate.now().plusDays(1),ZoneId.systemDefault(),null,System.currentTimeMillis());
            check(export.events.size()==6 && export.sessions.size()==3,"All recorded event types exported");
            File folder=new File(context.getFilesDir(),"instrumentation-exports");folder.mkdirs();
            for(ExportWriter.Format f:ExportWriter.Format.values())try(FileOutputStream out=new FileOutputStream(new File(folder,"device-test."+f.extension))){ExportWriter.write(out,export,f,false);}
            try(LogStore reopened=new LogStore(context,name)){check(reopened.sessions().size()==3,"Records persist after reopening database");}
            db.deleteSession(three.id);check(db.sessions().size()==2,"Delete affects only the selected dose");
            rejected=false;try{db.deleteMedicine(first.id);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Medicine deletion protects existing dose records");
            db.deleteSession(one.id);db.deleteMedicine(first.id);check(db.medicine(first.id)==null,"Medicine without records can be deleted");
            String[] languages={"en","fr","pt","zh"};String[] translated={"Settings","Réglages","Definições","设置"};
            for(int i=0;i<languages.length;i++){Configuration config=new Configuration(context.getResources().getConfiguration());config.setLocale(java.util.Locale.forLanguageTag(languages[i]));Context localized=context.createConfigurationContext(config);check(I18n.tr(localized,"Settings").equals(translated[i]),"Compiled language resource: "+languages[i]);}
            Configuration french=new Configuration(context.getResources().getConfiguration());french.setLocale(java.util.Locale.FRENCH);Context fr=context.createConfigurationContext(french);
            check(I18n.tr(fr,"%d events selected",1).equals("1 événement sélectionné"),"French singular resource");
            check(I18n.tr(fr,"%d events selected",2).equals("2 événements sélectionnés"),"French plural resource");
            Context isolated=context;
            AppPrefs.addColor(isolated,0xffc93048);AppPrefs.addColor(isolated,0xffc93048);check(AppPrefs.colors(isolated).size()==1&&AppPrefs.colors(isolated).get(0)==0xffc93048,"Custom colors persist and deduplicate");
            AppPrefs.get(isolated).edit().putString("language","pt").commit();check(AppPrefs.locale(isolated).toLanguageTag().equals("pt-PT"),"Language preference persists");context.deleteSharedPreferences("instrumentation-medic-settings");
            String[] permissions=context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
            check(permissions==null||!java.util.Arrays.asList(permissions).contains("android.permission.INTERNET"),"Installed app has no Internet permission");
            checks+=NotificationChecks.run(context);
            checks+=ReviewChecks.run(context);
            result.putString("stream","PASS: "+checks+" Android database and export checks\n");resultCode=Activity.RESULT_OK;
        }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n"+android.util.Log.getStackTraceString(e));}
        finally{context.deleteDatabase(name);AppPrefs.get(context).edit().clear().commit();StatusNotifications.update(context);}
        finish(resultCode,result);
    }
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}

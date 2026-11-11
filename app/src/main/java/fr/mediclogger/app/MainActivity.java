package fr.mediclogger.app;

import android.app.Activity;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar;
import android.widget.Switch;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import fr.mediclogger.app.Models.ExportData;
import fr.mediclogger.app.Models.Medicine;
import fr.mediclogger.app.Models.Session;

@SuppressLint("SetTextI18n") // UI templates are resolved through I18n resources; user text is never translated.
public class MainActivity extends Activity {
    private int BG, INK, MUTED, ACCENT, PALE, LINE, SURFACE, ON_ACCENT, AMBER, AMBER_BG, RED;
    private boolean night;
    private static final int WHITE=Color.WHITE;
    private void initPalette() {
        night=(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if(night){BG=SURFACE=0xff161213;INK=0xfff3eaea;MUTED=0xffb9abad;ACCENT=RED=0xffff9aa0;PALE=0xff3a2326;LINE=0xff3a3032;ON_ACCENT=0xff2a0d10;AMBER=0xffffc27a;AMBER_BG=0xff3d2e14;}
        else{BG=SURFACE=0xffffffff;INK=0xff302326;MUTED=0xff6d6063;ACCENT=RED=0xffb63840;PALE=0xfffbe7e7;LINE=0xffeadfe0;ON_ACCENT=0xffffffff;AMBER=0xff8a5318;AMBER_BG=0xfffff2dd;}
    }
    private static final int[] COLORS={0xffb63840,0xff5377ae,0xffa37442,0xff8e6ba9,0xffbd6b75,0xff64748b};
    private static final String[] COLOR_NAMES={"Red","Blue","Amber","Purple","Rose","Slate"};
    private static final int CREATE_EXPORT=401,CHOOSE_REMINDER_SOUND=402;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LogStore store;
    private LinearLayout root, page, nav;
    private ScrollView scroll;
    private int screen;
    private LocalDate exportFrom=LocalDate.now().minusDays(29), exportTo=LocalDate.now();
    private ExportWriter.Format exportFormat=ExportWriter.Format.EXCEL;
    private boolean exportZip, exporting, showArchived;
    private Set<Long> exportMedicineIds;
    private long reminderMedicineId=-1;
    private String journalQuery="";
    private int journalStatus;
    private int journalLimit=60;
    private LocalDate calendarDay=LocalDate.now();
    private YearMonth calendarMonth=YearMonth.now();
    private boolean resumed;
    private final Runnable clockRefresh=new Runnable() {
        @Override public void run() {
            if((screen==0||screen==6) && !isFinishing()) {
                int y=scroll==null?0:scroll.getScrollY(); render(); scroll.post(()->scroll.scrollTo(0,y));
            }
            handler.postDelayed(this,60000);
        }
    };

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppPrefs.localized(base));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        initPalette();
        if(state!=null&&state.containsKey("medic-state")){
            Parcel parcel=Parcel.obtain();
            try{byte[] plain=CryptoBox.open(state.getByteArray("medic-state"),"activity-state");parcel.unmarshall(plain,0,plain.length);parcel.setDataPosition(0);state=parcel.readBundle(getClassLoader());}
            catch(Exception unavailable){state=null;}finally{parcel.recycle();}
        }
        store=new LogStore(this);
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::navigateBack);
        if(state!=null) {
            screen=state.getInt("screen"); exportFrom=LocalDate.parse(state.getString("from",exportFrom.toString()));
            exportTo=LocalDate.parse(state.getString("to",exportTo.toString()));
            exportFormat=ExportWriter.Format.valueOf(state.getString("format",exportFormat.name()));
            exportZip=state.getBoolean("zip"); showArchived=state.getBoolean("archived");
            if(!state.getBoolean("export_all",true)){
                exportMedicineIds=new LinkedHashSet<>();long[] ids=state.getLongArray("export_medicines");if(ids!=null)for(long id:ids)exportMedicineIds.add(id);
            }else {long previous=state.getLong("medicine",-1);if(previous>=0){exportMedicineIds=new LinkedHashSet<>();exportMedicineIds.add(previous);}}
            reminderMedicineId=state.getLong("reminder_medicine",-1);
            journalQuery=state.getString("query",""); journalStatus=state.getInt("status");
            calendarDay=LocalDate.parse(state.getString("calendar_day",calendarDay.toString()));
            calendarMonth=YearMonth.parse(state.getString("calendar_month",calendarMonth.toString()));
        }
        if(Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if(!night) flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        render();applySystemBarAppearance();if(state!=null&&screen==3)restoreScroll(state.getInt("export_scroll",0));refreshShortcuts();handler.postDelayed(clockRefresh,60000);
        if(state==null) handleShortcut(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleShortcut(intent); }
    @Override protected void onResume(){super.onResume();if(resumed&&store!=null)renderKeepingScroll();resumed=true;}
    private void navigateBack(){if(screen!=0){screen=screen==6?2:0;render();}else moveTaskToBack(true);}
    @Override public void onBackPressed(){navigateBack();}
    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);Bundle b=new Bundle();b.putInt("screen",screen); b.putString("from",exportFrom.toString());
        b.putString("to",exportTo.toString()); b.putString("format",exportFormat.name()); b.putBoolean("zip",exportZip);
        b.putBoolean("archived",showArchived);b.putBoolean("export_all",exportMedicineIds==null);
        if(exportMedicineIds!=null){long[] ids=new long[exportMedicineIds.size()];int index=0;for(long id:exportMedicineIds)ids[index++]=id;b.putLongArray("export_medicines",ids);}
        b.putLong("reminder_medicine",reminderMedicineId);b.putInt("export_scroll",screen==3&&scroll!=null?scroll.getScrollY():0);
        b.putString("query",journalQuery); b.putInt("status",journalStatus);
        b.putString("calendar_day",calendarDay.toString());b.putString("calendar_month",calendarMonth.toString());
        Parcel parcel=Parcel.obtain();
        try{parcel.writeBundle(b);outState.putByteArray("medic-state",CryptoBox.seal(parcel.marshall(),"activity-state"));}
        catch(java.io.IOException unavailable){/* UI selection can be reset; medication data remains in encrypted storage. */}
        finally{parcel.recycle();}
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null); worker.shutdown(); store.close(); super.onDestroy();
    }
    private void handleShortcut(Intent intent) {
        long id=intent.getLongExtra("medicine_id",-1);
        if("fr.mediclogger.FINISH_DOSE".equals(intent.getAction())){
            long sessionId=intent.getLongExtra("session_id",-1);Session session=store.session(sessionId);
            try{if(session!=null&&session.monitorEffects){if(session.active())store.mark(sessionId,false);screen=0;render();ratingDialog(sessionId);}}catch(IllegalArgumentException stale){toast(stale.getMessage());StatusNotifications.update(this);}return;
        }
        if("fr.mediclogger.SHOW_STATUS".equals(intent.getAction())){screen=0;render();long session=intent.getLongExtra("session_id",-1);if(store.session(session)!=null)sessionDialog(session);return;}
        if("fr.mediclogger.REMINDER_DETAILS".equals(intent.getAction())){reminderMedicineId=id;screen=6;render();ReminderScheduler.refreshSummary(this);return;}
        if(id<0) return;
        intent.removeExtra("medicine_id");
        Medicine m=store.medicine(id);
        if(m==null || m.archived) { toast("This medicine is archived or no longer available."); return; }
        ShortcutManager shortcuts=getSystemService(ShortcutManager.class);if(shortcuts!=null)shortcuts.reportShortcutUsed("medicine-"+id);
        screen=0; render();
        new AlertDialog.Builder(this).setTitle(tr("Log a dose of %s?",m.label()))
            .setMessage(m.name+(m.dose.isEmpty()?"":" · "+m.dose))
            .setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Log dose"),(d,w)->take(m)).show();
    }

    private final Map<Long,Medicine> medicineCache=new LinkedHashMap<>();
    /** One lookup per medicine per screen build instead of one database query per dose card. */
    private Medicine cachedMedicine(long id){
        if(medicineCache.isEmpty()) for(Medicine m:store.medicines(true)) medicineCache.put(m.id,m);
        return medicineCache.get(id);
    }
    /** Needs a content view first: the window's insets controller does not exist before setContentView. */
    private void applySystemBarAppearance(){
        if(Build.VERSION.SDK_INT<30) return;
        android.view.WindowInsetsController bars=getWindow().getInsetsController();
        if(bars==null) return;
        int light=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        bars.setSystemBarsAppearance(night?0:light,light);
    }
    private void renderKeepingScroll(){int position=scroll==null?0:scroll.getScrollY();render();restoreScroll(position);}
    private void restoreScroll(int position){
        ScrollView target=scroll;target.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw(){if(target.getViewTreeObserver().isAlive())target.getViewTreeObserver().removeOnPreDrawListener(this);target.scrollTo(0,position);return true;}
        });
    }
    private void render() {
        medicineCache.clear();
        StatusNotifications.update(this);ReminderScheduler.reschedule(this,false);
        root=column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
                return WindowInsets.CONSUMED;
            }
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root); root.requestApplyInsets();
        LinearLayout header=row(); header.setPadding(dp(20),dp(8),dp(20),dp(8));
        ImageView mark=new ImageView(this); mark.setImageResource(R.mipmap.ic_launcher); mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(mark,lp(28,28)); gap(header,10,true);
        TextView brand=text(tr("Medic Logger"),18,INK,true); header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        ImageView settings=icon("settings",MUTED,24);settings.setPadding(dp(12),dp(12),dp(12),dp(12));settings.setContentDescription(tr("Settings"));settings.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);settings.setBackground(ripple(Color.TRANSPARENT,24));settings.setOnClickListener(v->{screen=4;render();});header.addView(settings,lp(48,48));
        header.setMinimumHeight(dp(56));root.addView(header);
        scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        page=column(); page.setPadding(dp(20),dp(12),dp(20),dp(24));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(screen) { case 1 -> journal(); case 2 -> medicines(); case 3 -> exportPage(); case 4 -> settingsPage(); case 5 -> calendarPage(); case 6 -> remindersPage(); default -> dashboard(); }
        if(screen==3)exportFooter();
        View divider=new View(this);divider.setBackgroundColor(LINE);root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        nav=row(); nav.setBackgroundColor(SURFACE); nav.setPadding(dp(4),dp(8),dp(4),dp(8));
        String[] labels={"Today","Journal","Calendar","Meds","Export"};String[] icons={"home","clock","calendar","pill","export"};int[] targets={0,1,5,2,3};
        for(int i=0;i<5;i++) {
            final int target=targets[i];
            LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setMinimumHeight(dp(64));item.setBackground(ripple(Color.TRANSPARENT,16));
            FrameLayout indicator=new FrameLayout(this);indicator.setBackground(shape(target==screen?PALE:SURFACE,20,0));
            ImageView img=icon(icons[i],target==screen?ACCENT:MUTED,24);indicator.addView(img,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
            LinearLayout.LayoutParams ip=lp(64,32);ip.gravity=Gravity.CENTER_HORIZONTAL;item.addView(indicator,ip);gap(item,4,false);
            TextView label=text(tr(labels[i]),12,target==screen?INK:MUTED,target==screen);label.setGravity(Gravity.CENTER);item.addView(label,new LinearLayout.LayoutParams(-1,-2));
            item.setContentDescription(tr(labels[i]));item.setSelected(target==screen);item.setOnClickListener(v->{screen=target;render();});
            nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(nav);
        exposeClickableRoles(root);
    }
    /** Custom-drawn controls are plain views; announce them as buttons (and the bottom bar items as selectable tabs) to TalkBack. */
    private void exposeClickableRoles(View view) {
        if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) exposeClickableRoles(group.getChildAt(i));
        if(!view.isClickable()||view instanceof Button||view instanceof CheckBox||view instanceof Switch||view instanceof EditText) return;
        boolean tab=view.getParent()==nav;
        view.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(host,info);
                info.setClassName(Button.class.getName());
                if(tab) info.setSelected(host.isSelected());
            }
        });
    }
    private void heading(String eyebrow,String title,String subtitle) {
        TextView h=text(tr(title),28,INK,false);h.setLetterSpacing(-.015f);page.addView(h);
        if(!eyebrow.isEmpty()){gap(page,6,false);page.addView(text(eyebrow,14,MUTED,false));}
        if(!subtitle.isEmpty()){gap(page,6,false);page.addView(text(subtitle,14,MUTED,false));}
        gap(page,20,false);
    }
    private void dashboard() {
        List<Session> sessions=store.sessions(); List<Medicine> meds=store.medicines(false);
        long active=sessions.stream().filter(Session::active).count();
        boolean trackingEffects=active>0||meds.stream().anyMatch(medicine->medicine.monitorEffects);
        long today=sessions.stream().filter(s->date(s.takenAt).equals(LocalDate.now())).count();
        heading(LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(AppPrefs.locale(this))),"Today","");
        LinearLayout summary=row();summary.addView(stat("Doses today",today,"pill"),new LinearLayout.LayoutParams(0,-2,1));gap(summary,12,true);
        summary.addView(stat(trackingEffects?"Ongoing":"Medicines",trackingEffects?active:meds.size(),trackingEffects?"clock":"pill"),new LinearLayout.LayoutParams(0,-2,1));page.addView(summary);gap(page,20,false);
        section("Quick log","Manage",()->{screen=2;render();});
        List<Medicine> quick=new ArrayList<>(); for(Medicine m:meds) if(m.favorite) quick.add(m);
        if(quick.isEmpty()) {
            LinearLayout empty=card();empty.addView(button(meds.isEmpty()?"+  Add medicine":"Choose shortcuts",true,()->{if(meds.isEmpty()) medicineDialog(null);else {screen=2;render();}}));page.addView(empty);
        } else {
            HorizontalScrollView horizontal=new HorizontalScrollView(this); horizontal.setHorizontalScrollBarEnabled(false); horizontal.setClipToPadding(false);
            LinearLayout rail=row(); rail.setGravity(Gravity.TOP);
            for(Medicine m:quick) {
                LinearLayout tile=column();tile.setPadding(dp(16),dp(14),dp(16),dp(14));tile.setBackground(ripple(tint(m.color),18));
                LinearLayout tr=row();tr.addView(icon("pill",m.color,20),lp(20,20));
                TextView plus=text(tr("+"),24,m.color,false);plus.setGravity(Gravity.END);tr.addView(plus,new LinearLayout.LayoutParams(0,-2,1));tile.addView(tr);gap(tile,10,false);
                TextView name=text(m.label(),16,INK,true); name.setMaxLines(2); tile.addView(name);
                gap(tile,5,false);tile.addView(text(m.dose.trim().isEmpty()?tr("Log dose"):m.dose,12,MUTED,false));if(m.stockRemaining!=null){gap(tile,6,false);tile.addView(text(tr("Stock: %d",m.stockRemaining),12,m.stockRemaining==0?RED:MUTED,false));}
                tile.setContentDescription(tr("Log dose: %s",m.name)+(m.dose.isEmpty()?"":", "+m.dose));
                tile.setOnClickListener(v->take(m));int width=Math.max(144,(int)((getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density-52)/2));LinearLayout.LayoutParams p=lp(width,-2);p.setMargins(0,0,dp(12),0);rail.addView(tile,p);
            }
            horizontal.addView(rail,new HorizontalScrollView.LayoutParams(-2,-2));page.addView(horizontal);
        }
        if(trackingEffects){
        gap(page,20,false);section("Active doses",active==0?null:""+active,null);
        if(active==0) {
            LinearLayout empty=card();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(28),dp(20),dp(28));empty.addView(icon("check",MUTED,28),lp(28,28));gap(empty,12,false);
            TextView label=text(tr("No active doses"),14,MUTED,false);label.setGravity(Gravity.CENTER);empty.addView(label);page.addView(empty);
        } else for(Session s:sessions) if(s.active()) doseCard(page,s,true);
        }
        List<Session> takenOnly=new ArrayList<>();for(Session s:sessions)if(!s.monitorEffects&&date(s.takenAt).equals(LocalDate.now()))takenOnly.add(s);
        if(!takenOnly.isEmpty()){gap(page,20,false);section("Taken today",null,null);for(Session s:takenOnly)doseCard(page,s,false);}
    }
    private LinearLayout stat(String label,long value,String kind) {
        LinearLayout tile=row();tile.setPadding(dp(16),dp(16),dp(12),dp(16));tile.setBackground(shape(SURFACE,18,LINE));
        LinearLayout values=column();values.addView(text(Long.toString(value),28,INK,false));gap(values,4,false);values.addView(text(tr(label),12,MUTED,false));tile.addView(values,new LinearLayout.LayoutParams(0,-2,1));
        tile.addView(icon(kind,ACCENT,24),lp(24,24));return tile;
    }
    private void doseCard(LinearLayout parent,Session s,boolean actions) {
        Medicine current=cachedMedicine(s.medicineId);int color=current==null?s.color:current.color;
        LinearLayout c=card();
        LinearLayout top=row();FrameLayout well=new FrameLayout(this);well.setBackground(shape(tint(color),12,0));well.addView(icon("pill",color,22),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));top.addView(well,lp(40,40));gap(top,12,true);
        LinearLayout names=column(); names.addView(text(s.label(),16,INK,true));
        String desc=(s.alias.isEmpty()?"":s.medicineName)+(s.dose.isEmpty()?"":(s.alias.isEmpty()?"":" · ")+s.dose);
        if(!desc.isEmpty()) {gap(names,3,false); names.addView(text(desc,12,MUTED,false));}
        top.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=text(tr("⋮"),25,MUTED,false);more.setGravity(Gravity.CENTER);more.setContentDescription(tr("Dose details")+": "+s.medicineName);more.setBackground(ripple(SURFACE,24));more.setOnClickListener(v->sessionDialog(s.id));
        top.addView(more,lp(48,48));c.addView(top);gap(c,12,false);
        if(!s.monitorEffects){
            c.addView(badge("Taken",MUTED,BG));gap(c,12,false);c.addView(text(fullTime(s.takenAt),14,INK,false));
            if(!s.notes.trim().isEmpty()){gap(c,10,false);c.addView(text(s.notes,13,MUTED,false));}
            c.setOnClickListener(v->sessionDialog(s.id));parent.addView(c);gap(parent,12,false);return;
        }
        boolean effect=s.onsetAt!=null;
        LinearLayout state=row();state.addView(badge(s.active()?(effect?"Effects active":"Waiting"):effect?"Completed":"No effect",s.active()?(effect?ACCENT:AMBER):MUTED,s.active()?(effect?PALE:AMBER_BG):BG));
        if(s.active()){gap(state,10,true);state.addView(text(elapsed(System.currentTimeMillis()-(effect?s.onsetAt:s.takenAt)),12,MUTED,false));}
        else if(effect){gap(state,10,true);state.addView(text(tr("%s of effects",elapsed(s.endedAt-s.onsetAt)),12,MUTED,false));}
        c.addView(state);gap(c,16,false);
        LinearLayout timeline=row();timeline.setPadding(dp(12),dp(12),dp(12),dp(12));timeline.setBackground(shape(BG,12,0));
        timeline.addView(stage("Taken",s.takenAt),new LinearLayout.LayoutParams(0,-2,1));
        timeline.addView(stage("Onset",s.onsetAt),new LinearLayout.LayoutParams(0,-2,1));
        timeline.addView(stage(s.active()?"End":effect?"Ended":"Closed",s.endedAt),new LinearLayout.LayoutParams(0,-2,1));c.addView(timeline);
        if(s.effectiveness!=null){gap(c,12,false);c.addView(text(tr("Effectiveness: %d/10",s.effectiveness),14,ACCENT,true));}
        if(!s.notes.trim().isEmpty()) { gap(c,10,false); c.addView(text(s.notes,13,MUTED,false)); }
        if(actions && s.active()) {
            gap(c,16,false);LinearLayout actionsRow=row();Button log=button(effect?"Effects ended":"Effects started",true,()->mark(s,!effect));actionsRow.addView(log,new LinearLayout.LayoutParams(0,-2,effect?1:2));
            if(!effect){gap(actionsRow,8,true);Button close=button("No effect",false,()->new AlertDialog.Builder(this).setTitle(tr("Close without effect?")).setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Close dose"),(d,w)->mark(s,false)).show());actionsRow.addView(close,new LinearLayout.LayoutParams(0,-2,1));}c.addView(actionsRow);
        } else c.setOnClickListener(v->sessionDialog(s.id));
        parent.addView(c); gap(parent,12,false);
    }
    private LinearLayout stage(String label,Long at){LinearLayout s=column();s.addView(text(tr(label),12,MUTED,false));gap(s,6,false);s.addView(text(at==null?"—":time(at),14,at==null?MUTED:INK,at!=null));return s;}
    private void journal() {
        heading("","Journal","");
        EditText search=edit("Search medicines or notes",journalQuery,100,false); search.setSingleLine(true); page.addView(search); gap(page,14,false);
        LinearLayout filters=row(); String[] labels={"All","Ongoing","Completed"};
        for(int i=0;i<3;i++) { final int target=i; TextView chip=chip(labels[i],journalStatus==i); chip.setOnClickListener(v->{journalStatus=target; journalLimit=60; render();}); LinearLayout.LayoutParams p=lp(-2,-2); p.setMargins(0,0,dp(8),0); filters.addView(chip,p); }
        page.addView(filters); gap(page,20,false);
        LinearLayout results=column(); page.addView(results);
        renderJournalResults(results);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int count,int after) {}
            public void onTextChanged(CharSequence s,int st,int before,int count) {journalQuery=s.toString(); journalLimit=60; renderJournalResults(results);}
            public void afterTextChanged(Editable s) {}
        });
    }
    private void renderJournalResults(LinearLayout results) {
        results.removeAllViews(); String q=journalQuery.toLowerCase(AppPrefs.locale(this)); LocalDate previous=null; int count=0;
        for(Session s:store.sessions()) {
            if(journalStatus==1&&!s.active() || journalStatus==2&&s.active()) continue;
            if(!(s.medicineName+" "+s.alias+" "+s.dose+" "+s.notes).toLowerCase(AppPrefs.locale(this)).contains(q)) continue;
            count++; if(count>journalLimit) continue;
            LocalDate day=date(s.takenAt);
            if(!day.equals(previous)) { if(previous!=null) gap(results,12,false); TextView date=text(day.equals(LocalDate.now())?tr("Today"):day.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy",AppPrefs.locale(this))).toUpperCase(AppPrefs.locale(this)),11,MUTED,true); date.setLetterSpacing(.08f); results.addView(date); gap(results,12,false); previous=day; }
            doseCard(results,s,false);
        }
        if(count==0){LinearLayout empty=card();empty.addView(text(tr("No doses found"),14,MUTED,false));results.addView(empty);}
        if(count>journalLimit) results.addView(button(tr("Show more (%d)",count-journalLimit),false,()->{journalLimit+=60;renderJournalResults(results);}));
    }
    private void medicines() {
        heading("","Medicines","");
        page.addView(button("+  Add medicine",true,()->medicineDialog(null)));gap(page,16,false);
        LinearLayout toggle=row(); TextView count=text(tr("%d saved medicines",store.medicines(false).size()),13,MUTED,false); toggle.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        TextView archived=text(tr(showArchived?"Hide archived":"Show archived"),12,ACCENT,true);archived.setMinHeight(dp(48));archived.setPadding(dp(8),0,0,0);archived.setGravity(Gravity.CENTER);archived.setOnClickListener(v->{showArchived=!showArchived;render();});toggle.addView(archived);page.addView(toggle);gap(page,8,false);
        List<Medicine> meds=store.medicines(showArchived);
        if(meds.isEmpty()){LinearLayout empty=card();empty.addView(text(tr("No medicines saved"),14,MUTED,false));page.addView(empty);}
        for(Medicine m:meds) {
            LinearLayout c=card();LinearLayout top=row();FrameLayout well=new FrameLayout(this);well.setBackground(shape(tint(m.color),12,0));well.addView(icon("pill",m.color,24),new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));top.addView(well,lp(44,44));gap(top,12,true);
            LinearLayout names=column();names.addView(text(m.name,16,INK,true));String detail=m.alias+(m.dose.isEmpty()?"":(m.alias.isEmpty()?"":" · ")+m.dose);if(!detail.isEmpty()){gap(names,4,false);names.addView(text(detail,13,MUTED,false));}top.addView(names,new LinearLayout.LayoutParams(0,-2,1));
            TextView star=text(m.favorite?"★":"☆",24,m.archived?MUTED:ACCENT,false);star.setGravity(Gravity.CENTER);star.setContentDescription(tr(m.favorite?"Remove quick shortcut: %s":"Add quick shortcut: %s",m.name));star.setOnClickListener(v->{m.favorite=!m.favorite;store.saveMedicine(m);refreshShortcuts();render();});top.addView(star,lp(48,48));c.addView(top);gap(c,16,false);
            LinearLayout buttons=row(); Button edit=button(m.archived?"Restore":"Edit medicine",false,()->{if(m.archived){m.archived=false;try{store.saveMedicine(m);refreshShortcuts();render();}catch(Exception e){toast(e.getMessage());}}else medicineDialog(m);}); buttons.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
            if(!m.archived) {gap(buttons,9,true); Button pin=button("Home shortcut",false,()->pinShortcut(m)); buttons.addView(pin,new LinearLayout.LayoutParams(0,-2,1));}
            else {gap(buttons,9,true);Button delete=button("Delete",false,()->new AlertDialog.Builder(this).setTitle(tr("Delete %s?",m.name)).setMessage(tr("Only medicines without recorded doses can be permanently deleted.")).setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Delete medicine"),(d,w)->{try{List<ReminderRule> rules=store.reminders(m.id);store.deleteMedicine(m.id);for(ReminderRule rule:rules)ReminderScheduler.dismiss(this,rule.id);removeShortcut(m.id);render();}catch(Exception e){toast(e.getMessage());}}).show());delete.setTextColor(RED);buttons.addView(delete,new LinearLayout.LayoutParams(0,-2,1));}
            if(!m.monitorEffects){c.addView(badge("Intake only",MUTED,BG));gap(c,12,false);}
            c.addView(buttons);
            if(!m.archived){
                if(m.stockRemaining!=null){gap(c,12,false);c.addView(text(tr("Current pack: %d",m.stockRemaining),13,m.stockRemaining==0?RED:MUTED,false));}
                gap(c,12,false);LinearLayout tools=row();tools.addView(button(m.stockRemaining==null?"Set stock":"Refill",false,()->refillDialog(m)),new LinearLayout.LayoutParams(0,-2,1));gap(tools,8,true);
                tools.addView(button("Reminders",false,()->{reminderMedicineId=m.id;screen=6;render();}),new LinearLayout.LayoutParams(0,-2,1));c.addView(tools);
            }else {gap(c,10,false);c.addView(badge("Archived",MUTED,BG));}
            page.addView(c);gap(page,12,false);
        }
    }

    private void exportPage() {
        heading("","Export","");
        LinearLayout range=card();range.addView(text(tr("Date range"),16,INK,true));gap(range,16,false);
        LinearLayout dates=row();
        LinearLayout left=column();left.addView(text(tr("From"),12,MUTED,false));gap(left,8,false);
        left.addView(button(shortDate(exportFrom),false,()->pickDate(exportFrom,d->{exportFrom=d;renderKeepingScroll();})));
        LinearLayout right=column();right.addView(text(tr("To"),12,MUTED,false));gap(right,8,false);
        right.addView(button(shortDate(exportTo),false,()->pickDate(exportTo,d->{exportTo=d;renderKeepingScroll();})));
        dates.addView(left,new LinearLayout.LayoutParams(0,-2,1));gap(dates,12,true);dates.addView(right,new LinearLayout.LayoutParams(0,-2,1));range.addView(dates);gap(range,12,false);
        LinearLayout presets=row();String[] labels={"Today","7 days","30 days","All time"};
        for(int i=0;i<4;i++) {
            final int preset=i;TextView v=chip(labels[i],false);v.setPadding(dp(4),dp(12),dp(4),dp(12));
            v.setOnClickListener(w->{
                exportTo=LocalDate.now();
                if(preset==0)exportFrom=exportTo;
                else if(preset==1)exportFrom=exportTo.minusDays(6);
                else if(preset==2)exportFrom=exportTo.minusDays(29);
                else {exportFrom=exportTo;for(Session dose:store.sessions())if(date(dose.takenAt).isBefore(exportFrom))exportFrom=date(dose.takenAt);}
                renderKeepingScroll();
            });
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(i<3)params.setMargins(0,0,dp(6),0);presets.addView(v,params);
        }
        range.addView(presets);gap(range,12,false);
        String medicineLabel=exportMedicineIds==null?tr("All medicines"):exportMedicineIds.isEmpty()?tr("Choose medicines"):tr("Medicines: %d",exportMedicineIds.size());
        if(exportMedicineIds!=null&&exportMedicineIds.size()==1){Medicine selected=store.medicine(exportMedicineIds.iterator().next());if(selected!=null)medicineLabel=selected.name;}
        range.addView(button(medicineLabel+"  ▾",false,this::chooseExportMedicine));gap(range,12,false);
        range.addView(text(ZoneId.systemDefault().getId()+" · "+tr("inclusive dates"),12,MUTED,false));page.addView(range);gap(page,16,false);
        LinearLayout formats=card();formats.addView(text(tr("Format"),16,INK,true));gap(formats,16,false);
        String[] names={"JSON","CSV","Excel","All formats"};String[] extensions={".json",".csv",".xlsx","ZIP · all three"};
        ExportWriter.Format[] values=ExportWriter.Format.values();
        for(int rowIndex=0;rowIndex<2;rowIndex++) {
            LinearLayout choices=row();
            for(int col=0;col<2;col++) {
                int i=rowIndex*2+col;ExportWriter.Format format=values[i];boolean chosen=format==exportFormat;
                LinearLayout option=row();option.setMinimumHeight(dp(64));option.setPadding(dp(12),dp(12),dp(8),dp(12));option.setBackground(ripple(chosen?PALE:BG,14));
                option.addView(icon(chosen?"radio-on":"radio-off",chosen?ACCENT:MUTED,20),lp(20,20));gap(option,10,true);
                LinearLayout title=column();title.addView(text(tr(names[i]),14,INK,true));gap(title,4,false);title.addView(text(tr(extensions[i]),12,MUTED,false));option.addView(title,new LinearLayout.LayoutParams(0,-2,1));
                option.setContentDescription(tr(names[i])+" · "+tr("Format"));option.setOnClickListener(v->{exportFormat=format;renderKeepingScroll();});
                choices.addView(option,new LinearLayout.LayoutParams(0,-2,1));if(col==0)gap(choices,8,true);
            }
            formats.addView(choices);if(rowIndex==0)gap(formats,8,false);
        }
        gap(formats,8,false);CheckBox zipped=new CheckBox(this);zipped.setText(tr("ZIP archive"));zipped.setTextSize(14);zipped.setTextColor(INK);zipped.setMinHeight(dp(48));zipped.setButtonTintList(ColorStateList.valueOf(ACCENT));
        zipped.setChecked(exportZip||exportFormat==ExportWriter.Format.ALL);zipped.setEnabled(exportFormat!=ExportWriter.Format.ALL);zipped.setOnCheckedChangeListener((v,checked)->exportZip=checked);formats.addView(zipped);page.addView(formats);gap(page,16,false);
    }
    private void exportFooter() {
        boolean valid=!exportTo.isBefore(exportFrom);int count=valid?exportData().events.size():0;
        LinearLayout footer=column();footer.setPadding(dp(20),dp(12),dp(20),dp(12));footer.setBackgroundColor(SURFACE);
        footer.addView(text(valid?tr("%d events selected",count):tr("End date must be on or after start date."),12,valid?MUTED:RED,false));gap(footer,8,false);
        Button save=button(exporting?"Preparing…":"Save export",true,this::startExport);save.setEnabled(!exporting&&valid&&count>0);save.setAlpha(save.isEnabled()?1:.45f);footer.addView(save);root.addView(footer);
    }
    private ExportData exportData() { return ExportData.forMedicines(store.sessions(),exportFrom,exportTo,ZoneId.systemDefault(),exportMedicineIds,System.currentTimeMillis()); }
    private void chooseExportMedicine() {
        List<Medicine> medicines=store.medicines(true);if(medicines.isEmpty()){toast("No medicines saved");return;}
        String[] labels=new String[medicines.size()];boolean[] checked=new boolean[medicines.size()];
        for(int i=0;i<medicines.size();i++){Medicine m=medicines.get(i);labels[i]=m.name+(m.dose.isEmpty()?"":" · "+m.dose)+(m.archived?" · "+tr("Archived"):"");checked[i]=exportMedicineIds==null||exportMedicineIds.contains(m.id);}
        new AlertDialog.Builder(this).setTitle(tr("Choose medicines")).setMultiChoiceItems(labels,checked,(dialog,index,value)->checked[index]=value)
            .setNegativeButton(tr("Cancel"),null).setNeutralButton(tr("All medicines"),(dialog,which)->{exportMedicineIds=null;renderKeepingScroll();})
            .setPositiveButton(tr("Apply"),(dialog,which)->{Set<Long> selected=new LinkedHashSet<>();for(int i=0;i<checked.length;i++)if(checked[i])selected.add(medicines.get(i).id);exportMedicineIds=selected;renderKeepingScroll();}).show();
    }

    private void take(Medicine m) {
        try {
            long active=store.sessions().stream().filter(s->s.medicineId==m.id&&s.active()).count();
            if(active>0) {
                // Show explicitly so an accidental repeat tap never silently duplicates a dose.
                new AlertDialog.Builder(this).setTitle(tr("Another dose of %s?",m.label()))
                    .setMessage(tr("%d doses are already active for this medicine. Log a separate dose?",active))
                    .setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Log another dose"),(d,w)->recordTake(m)).show();
            } else recordTake(m);
        } catch(Exception e) { toast(e.getMessage()); }
    }
    private void recordTake(Medicine m) {
        try {Session s=store.take(m.id);ReminderScheduler.dismissMedicine(this,m.id);screen=0;render();toast(tr("Dose logged"));}
        catch(Exception e){toast(e.getMessage());}
    }
    private void mark(Session s,boolean onset) {
        try {
            int position=scroll.getScrollY();store.mark(s.id,onset);render();scroll.post(()->scroll.scrollTo(0,position));toast(tr(onset?"Effect onset recorded":"Dose closed"));
            if(!onset)ratingDialog(s.id);
        } catch(Exception e){toast(e.getMessage());}
    }
    private LinearLayout ratingChoices(Integer[] rating){
        LinearLayout container=column();Runnable[] refresh=new Runnable[1];
        refresh[0]=()->{
            container.removeAllViews();container.addView(text(tr("Effectiveness (optional)"),14,INK,true));gap(container,8,false);
            for(int rowIndex=0;rowIndex<2;rowIndex++){
                LinearLayout line=row();int first=rowIndex==0?0:6,last=rowIndex==0?5:10;
                for(int number=first;number<=last;number++){
                    int value=number;boolean selected=rating[0]!=null&&rating[0]==number;TextView choice=text(""+number,16,selected?ACCENT:INK,true);choice.setGravity(Gravity.CENTER);choice.setMinHeight(dp(48));choice.setSelected(selected);choice.setContentDescription(tr("Effectiveness: %d/10",number));choice.setBackground(shape(selected?PALE:BG,12,selected?ACCENT:LINE));choice.setOnClickListener(v->{rating[0]=value;refresh[0].run();});LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1);if(number<last)params.setMargins(0,0,dp(4),0);line.addView(choice,params);
                }container.addView(line);gap(container,6,false);
            }
            gap(container,4,false);container.addView(text(tr("0: Not effective · 10: Very effective"),12,MUTED,false));gap(container,8,false);
            container.addView(button(rating[0]==null?tr("Not rated"):tr("Clear rating"),false,()->{rating[0]=null;refresh[0].run();}));
        };refresh[0].run();return container;
    }
    private void ratingDialog(long id){
        Session s=store.session(id);if(s==null||!s.monitorEffects||s.endedAt==null)return;Integer[] rating={s.effectiveness};
        LinearLayout content=form();content.addView(text(s.label(),16,INK,true));gap(content,16,false);content.addView(ratingChoices(rating));
        new AlertDialog.Builder(this).setTitle(tr("Effectiveness")).setView(formScroll(content)).setNegativeButton(tr("Skip"),null).setPositiveButton(tr("Save rating"),(dialog,which)->{
            try{store.rate(id,rating[0]);renderKeepingScroll();toast("Rating saved");}catch(IllegalArgumentException failure){toast(failure.getMessage());}
        }).show();
    }
    private void medicineDialog(Medicine existing) {
        Medicine m=existing==null?new Medicine():existing; if(existing==null)m.color=COLORS[store.medicines(true).size()%COLORS.length];
        LinearLayout content=form();
        EditText name=field(content,"Medicine name *","e.g. My medicine",m.name,80,false);
        EditText alias=field(content,"Short name","e.g. Morning",m.alias,20,false);
        EditText dose=field(content,"Dose label (optional)","e.g. 10 mg · 1 tablet",m.dose,80,false);
        EditText stock=existing==null?field(content,"Pack quantity (optional)","e.g. 30","",10,false):null;
        if(stock!=null)stock.setInputType(InputType.TYPE_CLASS_NUMBER);
        if(existing!=null&&m.stockRemaining!=null){content.addView(text(tr("Current pack: %d",m.stockRemaining),13,MUTED,false));gap(content,12,false);}
        Switch monitor=new Switch(this);monitor.setText(tr("Monitor effects"));monitor.setTextSize(14);monitor.setTextColor(INK);monitor.setMinHeight(dp(56));monitor.setChecked(m.monitorEffects);monitor.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,MUTED}));monitor.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{PALE,LINE}));content.addView(monitor,new LinearLayout.LayoutParams(-1,-2));
        if(existing!=null)content.addView(text(tr("Applies to new doses."),12,MUTED,false));gap(content,16,false);
        
        content.addView(text(tr("Color"),12,INK,true));gap(content,10,false);
        int[] chosen={m.color};LinearLayout colors=row();fillColors(colors,chosen);
        HorizontalScrollView colorRail=new HorizontalScrollView(this);colorRail.setHorizontalScrollBarEnabled(false);colorRail.addView(colors,new HorizontalScrollView.LayoutParams(-2,-2));content.addView(colorRail);gap(content,16,false);
        CheckBox star=new CheckBox(this);star.setText(tr("Show in Quick log"));star.setTextSize(14);star.setMinHeight(dp(48));star.setTextColor(INK);star.setButtonTintList(ColorStateList.valueOf(ACCENT));star.setChecked(m.favorite);content.addView(star);
        TextView error=text(tr(""),12,RED,false);content.addView(error);
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(tr(existing==null?"Add a medicine":"Edit medicine")).setView(formScroll(content)).setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Save medicine"),null);
        if(existing!=null)b.setNeutralButton(tr("Archive"),null);
        AlertDialog dialog=b.create();dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
                try{m.name=name.getText().toString();m.alias=alias.getText().toString();m.dose=dose.getText().toString();m.color=chosen[0];m.favorite=star.isChecked();m.monitorEffects=monitor.isChecked();if(stock!=null)m.stockRemaining=stock.getText().toString().trim().isEmpty()?null:quantity(stock.getText().toString());store.saveMedicine(m);refreshShortcuts();dialog.dismiss();render();toast("Medicine saved");}
                catch(Exception e){error.setText(tr(e.getMessage()));}
            });
            if(existing!=null)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(w->new AlertDialog.Builder(this).setTitle(tr("Archive %s?",m.name)).setMessage(tr("Your dose history will be kept.")).setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Archive"),(d,which)->{m.archived=true;store.saveMedicine(m);refreshShortcuts();dialog.dismiss();render();}).show());
        });dialog.show();
    }
    private void fillColors(LinearLayout rail,int[] chosen){
        rail.removeAllViews();LinkedHashSet<Integer> palette=new LinkedHashSet<>();for(int color:COLORS)palette.add(color);palette.addAll(AppPrefs.colors(this));palette.add(chosen[0]);
        int index=0;
        for(int color:palette){
            TextView circle=text(chosen[0]==color?"✓":"",20,Color.luminance(color)>.45?0xff302326:Color.WHITE,true);circle.setGravity(Gravity.CENTER);circle.setTag(color);circle.setBackground(shape(color,24,LINE));
            circle.setContentDescription(index<COLOR_NAMES.length?tr(COLOR_NAMES[index])+" · "+tr("Color"):tr("Custom color")+" "+hex(color));
            circle.setOnClickListener(v->{chosen[0]=color;fillColors(rail,chosen);});LinearLayout.LayoutParams params=lp(48,48);params.setMargins(0,0,dp(8),0);rail.addView(circle,params);index++;
        }
        TextView add=text("+",28,ACCENT,false);add.setGravity(Gravity.CENTER);add.setBackground(ripple(SURFACE,24));add.setContentDescription(tr("Create color"));
        add.setOnClickListener(v->colorDialog(chosen[0],color->{AppPrefs.addColor(this,color);chosen[0]=color;fillColors(rail,chosen);rail.post(()->((HorizontalScrollView)rail.getParent()).fullScroll(View.FOCUS_RIGHT));}));rail.addView(add,lp(48,48));
    }
    private long quantity(String input){
        try{long value=Long.parseLong(input.trim());if(value<0||value>1000000000L)throw new NumberFormatException();return value;}
        catch(NumberFormatException invalid){throw new IllegalArgumentException("Enter a quantity from 0 to 1,000,000,000.");}
    }
    private void refillDialog(Medicine medicine){
        LinearLayout content=form();
        if(medicine.stockRemaining!=null){content.addView(text(tr("Current pack: %d",medicine.stockRemaining),14,MUTED,false));gap(content,16,false);}
        EditText amount=field(content,"New pack quantity","e.g. 30","",10,false);amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(text(tr("Starts a new pack. Older doses do not change this pack."),12,MUTED,false));gap(content,12,false);TextView error=text("",12,RED,false);content.addView(error);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(tr("Refill")).setView(formScroll(content)).setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Save"),null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{store.refill(medicine.id,quantity(amount.getText().toString()));dialog.dismiss();renderKeepingScroll();toast("Stock updated");}catch(IllegalArgumentException invalid){error.setText(tr(invalid.getMessage()));}}));dialog.show();
    }
    private String hex(int color){return String.format(Locale.ROOT,"#%06X",color&0xffffff);}
    private void colorDialog(int initial,Consumer<Integer> result){
        LinearLayout content=form();View preview=new View(this);preview.setBackground(shape(initial,16,LINE));content.addView(preview,new LinearLayout.LayoutParams(-1,dp(64)));gap(content,16,false);
        EditText value=field(content,"Hex color","#RRGGBB",hex(initial),7,false);value.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        SeekBar[] channels=new SeekBar[3];String[] names={"Red channel","Green channel","Blue channel"};int[] values={Color.red(initial),Color.green(initial),Color.blue(initial)};boolean[] updating={false};
        for(int i=0;i<3;i++){content.addView(text(tr(names[i]),12,MUTED,false));SeekBar seek=new SeekBar(this);seek.setMax(255);seek.setProgress(values[i]);seek.setContentDescription(tr(names[i]));seek.setMinimumHeight(dp(48));seek.setProgressTintList(ColorStateList.valueOf(ACCENT));seek.setThumbTintList(ColorStateList.valueOf(ACCENT));content.addView(seek,new LinearLayout.LayoutParams(-1,dp(48)));channels[i]=seek;}
        for(SeekBar seek:channels)seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
            public void onProgressChanged(SeekBar s,int progress,boolean fromUser){if(updating[0])return;int color=Color.rgb(channels[0].getProgress(),channels[1].getProgress(),channels[2].getProgress());updating[0]=true;value.setText(hex(color));updating[0]=false;preview.setBackground(shape(color,16,LINE));}
        });
        value.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(Editable s){}
            public void onTextChanged(CharSequence s,int start,int before,int count){String input=s.toString();if(updating[0]||!input.matches("#?[0-9a-fA-F]{6}"))return;int color=Color.parseColor(input.startsWith("#")?input:"#"+input);updating[0]=true;channels[0].setProgress(Color.red(color));channels[1].setProgress(Color.green(color));channels[2].setProgress(Color.blue(color));updating[0]=false;preview.setBackground(shape(color,16,LINE));}
        });
        TextView error=text("",12,RED,false);content.addView(error);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(tr("Create color")).setView(formScroll(content)).setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Use color"),null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{String input=value.getText().toString().trim();if(!input.matches("#?[0-9a-fA-F]{6}")){error.setText(tr("Enter a color as #RRGGBB."));return;}result.accept(Color.parseColor(input.startsWith("#")?input:"#"+input));dialog.dismiss();}));dialog.show();
    }
    private void sessionDialog(long id) {
        Session s=store.session(id);if(s==null)return;
        LinearLayout content=form();content.addView(text(s.medicineName+(s.dose.isEmpty()?"":" · "+s.dose),14,MUTED,false));gap(content,17,false);
        content.addView(text(ZoneId.systemDefault().getId(),12,MUTED,false));gap(content,16,false);
        long[] taken={s.takenAt};Long[] onset={s.onsetAt},end={s.endedAt};
        content.addView(text(tr("Dose taken"),12,INK,true));gap(content,5,false);Button takenButton=button(fullTime(taken[0]),false,()->{});takenButton.setOnClickListener(v->pickTimestamp(taken[0],t->{taken[0]=t;takenButton.setText(fullTime(t));}));content.addView(takenButton);gap(content,12,false);
        CheckBox hasOnset=new CheckBox(this);hasOnset.setText(tr("Effect onset recorded"));hasOnset.setTextSize(14);hasOnset.setMinHeight(dp(48));hasOnset.setTextColor(INK);hasOnset.setChecked(onset[0]!=null);hasOnset.setButtonTintList(ColorStateList.valueOf(ACCENT));hasOnset.setVisibility(s.monitorEffects?View.VISIBLE:View.GONE);content.addView(hasOnset);
        Button onsetButton=button(onset[0]==null?"Set onset time":fullTime(onset[0]),false,()->{});onsetButton.setVisibility(onset[0]==null?View.GONE:View.VISIBLE);onsetButton.setOnClickListener(v->pickTimestamp(onset[0]==null?System.currentTimeMillis():onset[0],t->{onset[0]=t;onsetButton.setText(fullTime(t));}));content.addView(onsetButton);
        hasOnset.setOnCheckedChangeListener((v,checked)->{if(checked&&onset[0]==null)onset[0]=System.currentTimeMillis();onsetButton.setVisibility(checked?View.VISIBLE:View.GONE);if(checked)onsetButton.setText(fullTime(onset[0]));});gap(content,10,false);
        CheckBox hasEnd=new CheckBox(this);hasEnd.setText(tr("Dose closed / effects ended"));hasEnd.setTextSize(14);hasEnd.setMinHeight(dp(48));hasEnd.setTextColor(INK);hasEnd.setButtonTintList(ColorStateList.valueOf(ACCENT));hasEnd.setChecked(end[0]!=null);hasEnd.setVisibility(s.monitorEffects?View.VISIBLE:View.GONE);content.addView(hasEnd);
        Button endButton=button(end[0]==null?"Set end time":fullTime(end[0]),false,()->{});endButton.setVisibility(end[0]==null?View.GONE:View.VISIBLE);endButton.setOnClickListener(v->pickTimestamp(end[0]==null?System.currentTimeMillis():end[0],t->{end[0]=t;endButton.setText(fullTime(t));}));content.addView(endButton);
        hasEnd.setOnCheckedChangeListener((v,checked)->{if(checked&&end[0]==null)end[0]=System.currentTimeMillis();endButton.setVisibility(checked?View.VISIBLE:View.GONE);if(checked)endButton.setText(fullTime(end[0]));});gap(content,15,false);
        Integer[] rating={s.effectiveness};LinearLayout ratingView=ratingChoices(rating);ratingView.setVisibility(s.monitorEffects&&hasEnd.isChecked()?View.VISIBLE:View.GONE);content.addView(ratingView);
        hasEnd.setOnCheckedChangeListener((v,checked)->{if(checked&&end[0]==null)end[0]=System.currentTimeMillis();endButton.setVisibility(checked?View.VISIBLE:View.GONE);ratingView.setVisibility(s.monitorEffects&&checked?View.VISIBLE:View.GONE);if(checked)endButton.setText(fullTime(end[0]));});
        EditText notes=field(content,"Notes",s.monitorEffects?"How did you feel? (optional)":"Notes (optional)",s.notes,2000,true);
        TextView error=text(tr(""),12,RED,false);content.addView(error);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(tr("Dose details")).setView(formScroll(content)).setNegativeButton(tr("Cancel"),null).setNeutralButton(tr("Delete dose"),null).setPositiveButton(tr("Save changes"),null).create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{s.takenAt=taken[0];s.onsetAt=hasOnset.isChecked()?onset[0]:null;s.endedAt=hasEnd.isChecked()?end[0]:null;s.effectiveness=hasEnd.isChecked()?rating[0]:null;s.notes=notes.getText().toString();store.saveSession(s);dialog.dismiss();render();toast("Dose updated");}catch(Exception e){error.setText(tr(e.getMessage()));}});
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(RED);dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(w->new AlertDialog.Builder(this).setTitle(tr("Delete this dose?")).setMessage(tr("This removes the dose and its events.")).setNegativeButton(tr("Keep dose"),null).setPositiveButton(tr("Delete"),(d,which)->{store.deleteSession(id);dialog.dismiss();render();toast("Dose deleted");}).show());
        });dialog.show();
    }

    private void startExport() {
        if(exporting)return;ExportData data;
        try{data=exportData();if(data.events.isEmpty()){toast("There are no events in this period.");return;}}catch(Exception e){toast(e.getMessage());return;}
        ExportWriter.Format format=exportFormat;boolean zip=exportZip||format==ExportWriter.Format.ALL;
        String previous=AppPrefs.exports(this).getString("pending_export",null);
        if(previous!=null){File old=new File(previous);if(getFilesDir().equals(old.getParentFile())||getNoBackupFilesDir().equals(old.getParentFile()))old.delete();AppPrefs.exports(this).edit().remove("pending_export").apply();}
        String filename=data.baseName()+"."+(zip?"zip":format.extension);exporting=true;renderKeepingScroll();
        worker.execute(()->{
            File pending=new File(getNoBackupFilesDir(),"pending-export-"+java.util.UUID.randomUUID()+".enc");
            try{
                try(OutputStream out=CryptoBox.exportOutput(pending)){ExportWriter.write(out,data,format,zip);}
                AppPrefs.exports(this).edit().putString("pending_export",pending.getAbsolutePath()).apply();
                runOnUiThread(()->{exporting=false;if(isDestroyed())return;renderKeepingScroll();Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(zip?"application/zip":format.mime).putExtra(Intent.EXTRA_TITLE,filename).putExtra(Intent.EXTRA_LOCAL_ONLY,true);try{startActivityForResult(intent,CREATE_EXPORT);}catch(Exception e){pending.delete();AppPrefs.exports(this).edit().remove("pending_export").apply();toast("No file picker is available on this device.");}});
            }catch(Exception e){pending.delete();runOnUiThread(()->{exporting=false;if(!isDestroyed()){renderKeepingScroll();toast(tr("Export failed: %s",e.getMessage()));}});}
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==CHOOSE_REMINDER_SOUND){
            if(result==RESULT_OK&&data!=null){
                Uri sound=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if(sound!=null&&("content".equals(sound.getScheme())||"android.resource".equals(sound.getScheme()))){
                    if((data.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0)try{getContentResolver().takePersistableUriPermission(sound,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
                    AppPrefs.get(this).edit().putString("reminder_sound",sound.toString()).apply();ReminderAlerts.ensureChannel(this);
                }
            }
            renderKeepingScroll();return;
        }
        if(request!=CREATE_EXPORT)return;
        String path=AppPrefs.exports(this).getString("pending_export",null);if(path==null){toast("Please prepare the export again.");return;}
        File source=new File(path);
        if(result!=RESULT_OK||data==null||data.getData()==null){source.delete();AppPrefs.exports(this).edit().remove("pending_export").apply();toast(tr("Export cancelled"));return;}
        Uri destination=data.getData();exporting=true;renderKeepingScroll();
        worker.execute(()->{
            try {
                try(InputStream in=CryptoBox.exportInput(source);OutputStream out=getContentResolver().openOutputStream(destination,"w")){
                    if(out==null)throw new java.io.IOException("The destination could not be opened.");byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.flush();
                }
                source.delete();AppPrefs.exports(this).edit().remove("pending_export").apply();
                runOnUiThread(()->{exporting=false;if(!isDestroyed()){renderKeepingScroll();toast("Export saved");}});
            }catch(Exception e){runOnUiThread(()->{exporting=false;if(!isDestroyed()){renderKeepingScroll();toast("Could not save the file. Please try exporting again.");}});}
        });
    }
    private ShortcutInfo shortcut(Medicine m) {
        Intent intent=new Intent(this,MainActivity.class).setAction("fr.mediclogger.LOG_DOSE").putExtra("medicine_id",m.id);
        return new ShortcutInfo.Builder(this,"medicine-"+m.id).setShortLabel(m.label()).setLongLabel(tr("Log dose: %s",m.name)).setIcon(Icon.createWithResource(this,R.mipmap.ic_launcher)).setIntent(intent).build();
    }
    private void refreshShortcuts() {
        try{ShortcutManager manager=getSystemService(ShortcutManager.class);if(manager==null)return;List<ShortcutInfo> list=new ArrayList<>();List<String> archived=new ArrayList<>();for(Medicine m:store.medicines(true)){if(m.archived){archived.add("medicine-"+m.id);continue;}if(m.favorite&&list.size()<Math.min(4,manager.getMaxShortcutCountPerActivity()))list.add(shortcut(m));}manager.setDynamicShortcuts(list);if(!archived.isEmpty())manager.disableShortcuts(archived,tr("This medicine is archived."));for(ShortcutInfo info:manager.getPinnedShortcuts()){Medicine m=store.medicine(Long.parseLong(info.getId().replace("medicine-","")));if(m!=null&&!m.archived){manager.enableShortcuts(java.util.Collections.singletonList(info.getId()));manager.updateShortcuts(java.util.Collections.singletonList(shortcut(m)));}}}catch(Exception ignored){/* Launcher shortcuts are optional; in-app shortcuts always work. */}
    }
    private void pinShortcut(Medicine m) {
        ShortcutManager manager=getSystemService(ShortcutManager.class);
        if(manager!=null&&manager.isRequestPinShortcutSupported()){try{manager.requestPinShortcut(shortcut(m),null);}catch(Exception e){toast("This launcher could not add the shortcut.");}}
        else toast("This launcher does not support home shortcuts. Use Quick log instead.");
    }
    private void removeShortcut(long id) {
        try{ShortcutManager manager=getSystemService(ShortcutManager.class);if(manager!=null){List<String> ids=java.util.Collections.singletonList("medicine-"+id);manager.removeDynamicShortcuts(ids);manager.disableShortcuts(ids,tr("This medicine has been deleted."));}}catch(Exception ignored){}
        refreshShortcuts();
    }
    private void settingsPage(){
        heading("","Settings","");
        LinearLayout language=card();language.addView(text(tr("Language"),16,INK,true));gap(language,16,false);
        String[] languageNames={"English","Français","Português","中文"};String[] codes={"en","fr","pt","zh"};int selected=0;for(int i=0;i<codes.length;i++)if(codes[i].equals(AppPrefs.language(this)))selected=i;
        language.addView(button(languageNames[selected]+"  ▾",false,()->new AlertDialog.Builder(this).setTitle(tr("Language")).setSingleChoiceItems(languageNames,java.util.Arrays.asList(codes).indexOf(AppPrefs.language(this)),(dialog,index)->{
            AppPrefs.get(this).edit().putString("language",codes[index]).apply();dialog.dismiss();refreshShortcuts();StatusNotifications.update(this);recreate();
        }).setNegativeButton(tr("Cancel"),null).show()));page.addView(language);gap(page,16,false);
        LinearLayout notifications=card();notifications.addView(text(tr("Notifications"),16,INK,true));gap(notifications,8,false);
        Switch enabled=new Switch(this);enabled.setText(tr("Show medication status"));enabled.setTextSize(14);enabled.setTextColor(INK);enabled.setMinHeight(dp(56));enabled.setChecked(AppPrefs.notifications(this));enabled.setSwitchMinWidth(dp(48));enabled.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,MUTED}));enabled.setTrackTintList(ColorStateList.valueOf(PALE));
        enabled.setOnCheckedChangeListener((view,checked)->{AppPrefs.get(this).edit().putBoolean("notifications",checked).apply();if(checked&&Build.VERSION.SDK_INT>=33&&!StatusNotifications.allowed(this))requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},510);else StatusNotifications.update(this);});notifications.addView(enabled,new LinearLayout.LayoutParams(-1,-2));
        gap(notifications,8,false);notifications.addView(text(tr("Use the arrows to switch doses."),12,MUTED,false));gap(notifications,16,false);
        if(AppPrefs.notifications(this)){notifications.addView(button("Show notification again",false,()->{if(store.sessions().stream().noneMatch(Session::active)){toast("No active doses");return;}StatusNotifications.update(this);toast("Notification shown");}));gap(notifications,8,false);}
        notifications.addView(button("Android notification settings",false,()->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()))));
        page.addView(notifications);gap(page,16,false);
        LinearLayout reminders=card();reminders.addView(text(tr("Medication reminders"),16,INK,true));gap(reminders,8,false);
        Switch reminderEnabled=new Switch(this);reminderEnabled.setText(tr("Remind me to take medication"));reminderEnabled.setTextSize(14);reminderEnabled.setTextColor(INK);reminderEnabled.setMinHeight(dp(56));reminderEnabled.setChecked(ReminderAlerts.enabled(this));reminderEnabled.setSwitchMinWidth(dp(48));reminderEnabled.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,MUTED}));reminderEnabled.setTrackTintList(ColorStateList.valueOf(PALE));
        reminderEnabled.setOnCheckedChangeListener((view,checked)->{AppPrefs.get(this).edit().putBoolean("reminders_enabled",checked).apply();ReminderScheduler.reschedule(this,false);renderKeepingScroll();if(checked&&Build.VERSION.SDK_INT>=33&&!StatusNotifications.allowed(this))requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},511);});reminders.addView(reminderEnabled,new LinearLayout.LayoutParams(-1,-2));
        if(ReminderAlerts.enabled(this)){
            gap(reminders,12,false);reminders.addView(button(tr("Alert: %s",tr(ReminderAlerts.LABELS[ReminderAlerts.mode(this)]))+"  ▾",false,()->{
                String[] labels=java.util.Arrays.stream(ReminderAlerts.LABELS).map(this::tr).toArray(String[]::new);
                new AlertDialog.Builder(this).setTitle(tr("Reminder alert")).setSingleChoiceItems(labels,ReminderAlerts.mode(this),(dialog,which)->{AppPrefs.get(this).edit().putInt("reminder_alert_mode",which).apply();ReminderAlerts.ensureChannel(this);dialog.dismiss();renderKeepingScroll();}).setNegativeButton(tr("Cancel"),null).show();
            }));
            if(ReminderAlerts.hasSound(this)){gap(reminders,8,false);reminders.addView(button(tr("Sound: %s",ReminderAlerts.soundName(this))+"  ▾",false,this::chooseReminderSound));}
            gap(reminders,8,false);reminders.addView(button("Reminder sound and vibration",false,()->startActivity(new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,ReminderAlerts.ensureChannel(this)))));
        }
        if(Build.VERSION.SDK_INT>=31&&!ReminderScheduler.precise(this)){gap(reminders,12,false);reminders.addView(button("Allow precise reminders",false,this::openPreciseReminderSettings));}
        page.addView(reminders);gap(page,16,false);
        LinearLayout support=card();support.addView(text(tr("Support Medic Logger"),16,INK,true));gap(support,12,false);support.addView(button("Offer me a coffee",true,()->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://ko-fi.com/zerrino")).addCategory(Intent.CATEGORY_BROWSABLE));}
            catch(android.content.ActivityNotFoundException unavailable){toast("No browser is available on this device.");}
        }));gap(support,8,false);support.addView(text("Ko-fi · zerrino",12,MUTED,false));page.addView(support);gap(page,16,false);
        LinearLayout legal=card();legal.addView(text(tr("Legal"),16,INK,true));gap(legal,8,false);
        legal.addView(button("Privacy policy",false,()->legalDocument(false)));gap(legal,8,false);
        legal.addView(button("Medical notice",false,()->legalDocument(true)));gap(legal,8,false);
        legal.addView(button("Open-source licenses",false,this::licensesDialog));page.addView(legal);gap(page,16,false);
        try{page.addView(text(tr("Version %s",getPackageManager().getPackageInfo(getPackageName(),0).versionName),12,MUTED,false));}catch(PackageManager.NameNotFoundException impossible){throw new AssertionError(impossible);}
    }
    private void legalDocument(boolean medical){
        LinearLayout content=form();
        for(String[] section:medical?LegalContent.MEDICAL:LegalContent.PRIVACY){
            TextView title=text(tr(section[0]),16,INK,true);if(Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);content.addView(title);gap(content,6,false);
            TextView paragraph=text(tr(section[1]),15,INK,false);paragraph.setTextIsSelectable(true);paragraph.setLineSpacing(dp(3),1);content.addView(paragraph);gap(content,20,false);
        }
        if(!medical){
            content.addView(text(tr("Publisher and contact"),16,INK,true));gap(content,6,false);content.addView(text(LegalContent.PUBLISHER,15,INK,false));gap(content,8,false);
            content.addView(button(LegalContent.EMAIL,false,()->{
                try{startActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+LegalContent.EMAIL)));}
                catch(android.content.ActivityNotFoundException unavailable){android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);clipboard.setPrimaryClip(android.content.ClipData.newPlainText(tr("Contact"),LegalContent.EMAIL));toast("Email address copied");}
            }));
            if(LegalContent.PRIVACY_URL!=null){gap(content,8,false);content.addView(button("View online",false,()->{
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(LegalContent.PRIVACY_URL)).addCategory(Intent.CATEGORY_BROWSABLE));}
                catch(android.content.ActivityNotFoundException unavailable){toast("No browser is available on this device.");}
            }));}
            gap(content,12,false);content.addView(text(tr("Last updated")+": "+LegalContent.UPDATED,12,MUTED,false));
        }
        new AlertDialog.Builder(this).setTitle(tr(medical?"Medical notice":"Privacy policy")).setView(formScroll(content)).setPositiveButton(tr("Done"),null).show();
    }
    private void licensesDialog(){
        try(java.io.InputStream input=getResources().openRawResource(R.raw.third_party_notices)){
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
            TextView notices=text(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8),13,INK,false);notices.setTextIsSelectable(true);notices.setLineSpacing(dp(3),1);
            LinearLayout content=form();content.addView(notices);
            new AlertDialog.Builder(this).setTitle(tr("Open-source licenses")).setView(formScroll(content)).setPositiveButton(tr("Done"),null).show();
        }catch(java.io.IOException unavailable){toast("Please try again.");}
    }
    private void chooseReminderSound(){
        Intent picker=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_NOTIFICATION).putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,tr("Notification sound")).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false).putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,ReminderAlerts.sound(this));
        try{startActivityForResult(picker,CHOOSE_REMINDER_SOUND);}catch(android.content.ActivityNotFoundException unavailable){startActivity(new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,ReminderAlerts.ensureChannel(this)));}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);if(code!=510&&code!=511)return;boolean granted=results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED;
        if(code==510)AppPrefs.get(this).edit().putBoolean("notifications",granted).apply();StatusNotifications.update(this);renderKeepingScroll();if(!granted)toast("Notification permission is off. You can enable it in Android settings.");
    }
    private void openPreciseReminderSettings(){if(Build.VERSION.SDK_INT>=31)startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}
    private void remindersPage(){
        Medicine medicine=store.medicine(reminderMedicineId);if(medicine==null){heading("","Reminders","");page.addView(text(tr("This medicine is archived or no longer available."),14,MUTED,false));return;}
        heading(medicine.name,"Reminders","");
        if(!ReminderAlerts.enabled(this)){page.addView(button("Medication reminders are off in Settings",false,()->{screen=4;render();}));gap(page,12,false);}
        page.addView(button("+ Add reminder",true,()->reminderDialog(medicine,null)));gap(page,16,false);
        if(!StatusNotifications.allowed(this)){page.addView(button("Allow notifications",false,()->{if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},511);else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));}));gap(page,12,false);}
        if(Build.VERSION.SDK_INT>=31&&!ReminderScheduler.precise(this)){
            page.addView(button("Allow precise reminders",false,this::openPreciseReminderSettings));gap(page,8,false);page.addView(text(tr("Without this access, Android may delay reminders."),12,MUTED,false));gap(page,16,false);
        }
        List<ReminderRule> rules=store.reminders(medicine.id);
        if(rules.isEmpty()){LinearLayout empty=card();empty.addView(text(tr("No reminders yet"),14,MUTED,false));page.addView(empty);}
        for(ReminderRule rule:rules){
            LinearLayout card=card();LinearLayout top=row();top.addView(text(tr(reminderFrequency(rule)),16,INK,true),new LinearLayout.LayoutParams(0,-2,1));
            Switch enabled=new Switch(this);enabled.setContentDescription(tr("Enabled"));enabled.setMinHeight(dp(48));enabled.setChecked(rule.enabled);enabled.setOnCheckedChangeListener((button,checked)->{rule.enabled=checked;store.saveReminder(rule);renderKeepingScroll();});top.addView(enabled);card.addView(top);gap(card,8,false);
            card.addView(text(String.join(" · ",rule.times().stream().map(time->time.format(DateTimeFormatter.ofPattern("HH:mm"))).collect(java.util.stream.Collectors.toList())),14,INK,false));
            String dates=reminderDates(rule);if(!dates.isEmpty()){gap(card,8,false);card.addView(text(dates,13,MUTED,false));}
            if(rule.frequency.equals("cycle")){gap(card,8,false);card.addView(badge(cyclePhase(rule),rule.isOnDay(LocalDate.now())?ACCENT:MUTED,rule.isOnDay(LocalDate.now())?PALE:BG));}
            if(rule.enabled&&rule.nextAt!=null){gap(card,8,false);card.addView(text(tr("Next: %s",fullTime(rule.nextAt)),12,MUTED,false));}
            gap(card,16,false);LinearLayout actions=row();actions.addView(button("Edit",false,()->reminderDialog(medicine,rule)),new LinearLayout.LayoutParams(0,-2,1));gap(actions,8,true);
            actions.addView(button("Delete",false,()->new AlertDialog.Builder(this).setTitle(tr("Delete reminder?")).setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Delete"),(dialog,which)->{store.deleteReminder(rule.id);ReminderScheduler.dismiss(this,rule.id);renderKeepingScroll();}).show()),new LinearLayout.LayoutParams(0,-2,1));card.addView(actions);page.addView(card);gap(page,12,false);
        }
    }
    private String reminderFrequency(ReminderRule rule){return switch(rule.frequency){case "daily"->"Every day";case "weekly"->rule.weekDays==31?"Weekdays":rule.weekDays==96?"Weekends":"Weekly";case "monthly"->"Monthly";case "yearly"->"Yearly";case "cycle"->"ON/OFF cycle";default->"Reminders";};}
    private String reminderDates(ReminderRule rule){
        if(rule.frequency.equals("weekly")){List<String> days=new ArrayList<>();for(int day=1;day<=7;day++)if((rule.weekDays&(1<<(day-1)))!=0)days.add(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT,AppPrefs.locale(this)));return String.join(", ",days);}
        if(rule.frequency.equals("monthly"))return rule.monthDays().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
        if(rule.frequency.equals("yearly"))return rule.yearDays().stream().map(day->day.atYear(2000).format(DateTimeFormatter.ofPattern("d MMM",AppPrefs.locale(this)))).collect(java.util.stream.Collectors.joining(", "));
        if(rule.frequency.equals("cycle"))return tr("%d days ON · %d days OFF",rule.cycleOnDays,rule.cycleOffDays)+"\n"+tr("First ON day: %s",shortDate(rule.cycleStartDate()));
        return "";
    }
    private String cyclePhase(ReminderRule rule){int position=rule.cyclePosition(LocalDate.now());if(position<0)return tr("Starts %s",shortDate(rule.cycleStartDate()));return position<rule.cycleOnDays?tr("ON · Day %d of %d",position+1,rule.cycleOnDays):tr("OFF · Day %d of %d",position-rule.cycleOnDays+1,rule.cycleOffDays);}
    private int cycleDays(String value){try{return Integer.parseInt(value.trim());}catch(NumberFormatException invalid){throw new IllegalArgumentException("Use 1–3650 ON days and 0–3650 OFF days.");}}
    private void reminderDialog(Medicine medicine,ReminderRule existing){
        ReminderRule rule=existing==null?new ReminderRule():store.reminder(existing.id);if(rule==null){toast("This reminder no longer exists.");return;}
        if(existing==null){rule.medicineId=medicine.id;rule.monthDays=""+LocalDate.now().getDayOfMonth();rule.yearDays=MonthDay.now().toString();}
        LinearLayout content=form();String[] names={"Every day","Weekdays","Weekends","Weekly","Monthly","Yearly","ON/OFF cycle"};int[] mode={switch(rule.frequency){case "weekly"->rule.weekDays==31?1:rule.weekDays==96?2:3;case "monthly"->4;case "yearly"->5;case "cycle"->6;default->0;}};
        int[] weekDays={rule.weekDays};TreeSet<LocalTime> hours=new TreeSet<>(rule.times());TreeSet<Integer> monthDays=new TreeSet<>(rule.monthDays());TreeSet<MonthDay> yearDays=new TreeSet<>(rule.yearDays());
        String[] cycleOn={""+rule.cycleOnDays},cycleOff={""+rule.cycleOffDays},cycleStart={rule.cycleStart.isEmpty()?LocalDate.now().toString():rule.cycleStart};
        content.addView(text(tr("Frequency"),12,INK,true));gap(content,8,false);Button frequency=button(tr(names[mode[0]])+"  ▾",false,()->{});content.addView(frequency);gap(content,16,false);
        LinearLayout pattern=column();content.addView(pattern);Runnable[] refreshPattern=new Runnable[1];
        refreshPattern[0]=()->{
            pattern.removeAllViews();
            if(mode[0]==3){
                pattern.addView(text(tr("Days of the week"),12,INK,true));gap(pattern,8,false);
                for(int rowIndex=0;rowIndex<2;rowIndex++){LinearLayout days=row();int first=rowIndex==0?1:5,last=rowIndex==0?4:7;
                    for(int i=first;i<=last;i++){int day=i;TextView choice=chip(DayOfWeek.of(i).getDisplayName(TextStyle.SHORT,AppPrefs.locale(this)),(weekDays[0]&(1<<(day-1)))!=0);choice.setPadding(0,dp(12),0,dp(12));choice.setOnClickListener(v->{weekDays[0]^=1<<(day-1);refreshPattern[0].run();});LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(i<last)params.setMargins(0,0,dp(6),0);days.addView(choice,params);}pattern.addView(days);gap(pattern,6,false);
                }gap(pattern,10,false);
            }else if(mode[0]==4){
                pattern.addView(text(tr("Days of the month"),12,INK,true));gap(pattern,8,false);
                String label=monthDays.isEmpty()?tr("Choose dates"):monthDays.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
                pattern.addView(button(label+"  ▾",false,()->{String[] labels=new String[31];boolean[] checked=new boolean[31];for(int i=0;i<31;i++){labels[i]=""+(i+1);checked[i]=monthDays.contains(i+1);}new AlertDialog.Builder(this).setTitle(tr("Days of the month")).setMultiChoiceItems(labels,checked,(dialog,index,value)->checked[index]=value).setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Apply"),(dialog,which)->{monthDays.clear();for(int i=0;i<31;i++)if(checked[i])monthDays.add(i+1);refreshPattern[0].run();}).show();}));gap(pattern,16,false);
            }else if(mode[0]==5){
                pattern.addView(text(tr("Annual dates"),12,INK,true));gap(pattern,8,false);
                for(MonthDay date:yearDays){LinearLayout line=row();line.addView(text(date.atYear(2000).format(DateTimeFormatter.ofPattern("d MMM",AppPrefs.locale(this))),14,INK,false),new LinearLayout.LayoutParams(0,-2,1));TextView remove=text("×",24,RED,false);remove.setGravity(Gravity.CENTER);remove.setContentDescription(tr("Delete"));remove.setOnClickListener(v->{yearDays.remove(date);refreshPattern[0].run();});line.addView(remove,lp(48,48));pattern.addView(line);}
                pattern.addView(button("+ Add date",false,()->{DatePickerDialog picker=new DatePickerDialog(this,(view,year,month,day)->{yearDays.add(MonthDay.of(month+1,day));refreshPattern[0].run();},2028,LocalDate.now().getMonthValue()-1,LocalDate.now().getDayOfMonth());picker.setTitle(tr("Day and month"));picker.show();}));gap(pattern,16,false);
            }else if(mode[0]==6){
                LinearLayout counts=row(),onColumn=column(),offColumn=column();
                EditText on=field(onColumn,"Days ON","21",cycleOn[0],4,false),off=field(offColumn,"Days OFF","7",cycleOff[0],4,false);on.setInputType(InputType.TYPE_CLASS_NUMBER);off.setInputType(InputType.TYPE_CLASS_NUMBER);
                counts.addView(onColumn,new LinearLayout.LayoutParams(0,-2,1));gap(counts,12,true);counts.addView(offColumn,new LinearLayout.LayoutParams(0,-2,1));pattern.addView(counts);
                pattern.addView(text(tr("First ON day"),12,INK,true));gap(pattern,8,false);
                pattern.addView(button(shortDate(LocalDate.parse(cycleStart[0]))+"  ▾",false,()->{LocalDate date=LocalDate.parse(cycleStart[0]);new DatePickerDialog(this,(view,year,month,day)->{cycleStart[0]=LocalDate.of(year,month+1,day).toString();refreshPattern[0].run();},date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show();}));gap(pattern,8,false);
                TextView preview=text("",13,ACCENT,true);pattern.addView(preview);gap(pattern,16,false);
                Runnable update=()->{try{ReminderRule value=new ReminderRule();value.cycleOnDays=cycleDays(cycleOn[0]);value.cycleOffDays=cycleDays(cycleOff[0]);value.cycleStart=cycleStart[0];preview.setText(cyclePhase(value));}catch(IllegalArgumentException incomplete){preview.setText("");}};
                on.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(Editable s){}public void onTextChanged(CharSequence s,int start,int before,int count){cycleOn[0]=s.toString();update.run();}});
                off.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(Editable s){}public void onTextChanged(CharSequence s,int start,int before,int count){cycleOff[0]=s.toString();update.run();}});update.run();
            }
        };
        frequency.setOnClickListener(v->{String[] translated=new String[names.length];for(int i=0;i<names.length;i++)translated[i]=tr(names[i]);new AlertDialog.Builder(this).setTitle(tr("Frequency")).setSingleChoiceItems(translated,mode[0],(dialog,which)->{if(which==3&&mode[0]!=3)weekDays[0]=0;if(which==0)weekDays[0]=127;if(which==1)weekDays[0]=31;if(which==2)weekDays[0]=96;mode[0]=which;frequency.setText(tr(names[which])+"  ▾");refreshPattern[0].run();dialog.dismiss();}).setNegativeButton(tr("Cancel"),null).show();});
        refreshPattern[0].run();content.addView(text(tr("Times"),12,INK,true));gap(content,8,false);LinearLayout timeList=column();content.addView(timeList);Runnable[] refreshTimes=new Runnable[1];
        refreshTimes[0]=()->{timeList.removeAllViews();for(LocalTime time:hours){LinearLayout line=row();Button value=button(time.format(DateTimeFormatter.ofPattern("HH:mm")),false,()->new TimePickerDialog(this,(view,hour,minute)->{hours.remove(time);hours.add(LocalTime.of(hour,minute));refreshTimes[0].run();},time.getHour(),time.getMinute(),true).show());line.addView(value,new LinearLayout.LayoutParams(0,-2,1));TextView remove=text("×",24,RED,false);remove.setGravity(Gravity.CENTER);remove.setContentDescription(tr("Delete"));remove.setOnClickListener(v->{hours.remove(time);refreshTimes[0].run();});line.addView(remove,lp(48,48));timeList.addView(line);gap(timeList,8,false);}};
        refreshTimes[0].run();content.addView(button("+ Add time",false,()->new TimePickerDialog(this,(view,hour,minute)->{hours.add(LocalTime.of(hour,minute));refreshTimes[0].run();},8,0,true).show()));gap(content,16,false);
        Switch enabled=new Switch(this);enabled.setText(tr("Enabled"));enabled.setTextSize(14);enabled.setTextColor(INK);enabled.setMinHeight(dp(56));enabled.setChecked(rule.enabled);content.addView(enabled);TextView error=text("",12,RED,false);content.addView(error);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(tr(existing==null?"Add reminder":"Edit reminder")).setView(formScroll(content)).setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Save"),null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            try{rule.frequency=mode[0]==6?"cycle":mode[0]==4?"monthly":mode[0]==5?"yearly":mode[0]==0?"daily":"weekly";if(mode[0]==6){rule.cycleOnDays=cycleDays(cycleOn[0]);rule.cycleOffDays=cycleDays(cycleOff[0]);rule.cycleStart=cycleStart[0];}rule.times=hours.stream().map(LocalTime::toString).collect(java.util.stream.Collectors.joining(","));rule.weekDays=weekDays[0];rule.monthDays=monthDays.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));rule.yearDays=yearDays.stream().map(MonthDay::toString).collect(java.util.stream.Collectors.joining(","));rule.enabled=enabled.isChecked();store.saveReminder(rule);dialog.dismiss();renderKeepingScroll();if(rule.enabled&&ReminderAlerts.enabled(this)&&Build.VERSION.SDK_INT>=33&&!StatusNotifications.allowed(this))requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},511);}
            catch(IllegalArgumentException invalid){error.setText(tr(invalid.getMessage()));}
        }));dialog.show();
    }
    private void calendarPage(){
        heading("","Calendar","");List<Session> all=store.sessions();Locale locale=AppPrefs.locale(this);
        Map<LocalDate,Integer> eventsByDay=new LinkedHashMap<>();long monthCount=0;
        for(Session s:all){eventsByDay.merge(date(s.takenAt),1,Integer::sum);if(s.onsetAt!=null)eventsByDay.merge(date(s.onsetAt),1,Integer::sum);if(s.endedAt!=null)eventsByDay.merge(date(s.endedAt),1,Integer::sum);if(YearMonth.from(date(s.takenAt)).equals(calendarMonth))monthCount++;}
        LinearLayout month=card();LinearLayout bar=row();
        TextView previous=text("‹",28,ACCENT,false);previous.setGravity(Gravity.CENTER);previous.setContentDescription(tr("Previous month"));previous.setOnClickListener(v->{calendarMonth=calendarMonth.minusMonths(1);calendarDay=calendarMonth.atDay(Math.min(calendarDay.getDayOfMonth(),calendarMonth.lengthOfMonth()));render();});bar.addView(previous,lp(48,48));
        TextView monthName=text(calendarMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy",locale)),16,INK,true);monthName.setGravity(Gravity.CENTER);bar.addView(monthName,new LinearLayout.LayoutParams(0,-2,1));
        TextView next=text("›",28,ACCENT,false);next.setGravity(Gravity.CENTER);next.setContentDescription(tr("Next month"));next.setOnClickListener(v->{calendarMonth=calendarMonth.plusMonths(1);calendarDay=calendarMonth.atDay(Math.min(calendarDay.getDayOfMonth(),calendarMonth.lengthOfMonth()));render();});bar.addView(next,lp(48,48));month.addView(bar);
        LinearLayout weekdays=row();for(int i=1;i<=7;i++){TextView day=text(DayOfWeek.of(i).getDisplayName(TextStyle.SHORT,locale),12,MUTED,false);day.setGravity(Gravity.CENTER);day.setPadding(0,dp(12),0,dp(8));weekdays.addView(day,new LinearLayout.LayoutParams(0,-2,1));}month.addView(weekdays);
        int offset=calendarMonth.atDay(1).getDayOfWeek().getValue()-1;int cells=((offset+calendarMonth.lengthOfMonth()+6)/7)*7;
        for(int rowIndex=0;rowIndex<cells/7;rowIndex++){
            LinearLayout week=row();
            for(int col=0;col<7;col++){
                int dayNumber=rowIndex*7+col-offset+1;LinearLayout cell=column();cell.setGravity(Gravity.CENTER);cell.setMinimumHeight(dp(52));
                if(dayNumber>0&&dayNumber<=calendarMonth.lengthOfMonth()){
                    LocalDate day=calendarMonth.atDay(dayNumber);boolean chosen=day.equals(calendarDay);TextView number=text(Integer.toString(dayNumber),14,chosen?ON_ACCENT:INK,chosen);number.setGravity(Gravity.CENTER);
                    number.setBackground(shape(chosen?ACCENT:SURFACE,18,day.equals(LocalDate.now())&&!chosen?ACCENT:0));cell.addView(number,lp(32,32));gap(cell,3,false);
                    View dot=new View(this);dot.setBackground(shape(eventsByDay.containsKey(day)?ACCENT:Color.TRANSPARENT,3,0));cell.addView(dot,lp(4,4));cell.setBackground(ripple(Color.TRANSPARENT,16));cell.setContentDescription(shortDate(day)+" · "+tr("%d events",eventsByDay.getOrDefault(day,0)));cell.setOnClickListener(v->{calendarDay=day;render();});
                }
                week.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
            }
            month.addView(week);
        }
        gap(month,12,false);LinearLayout summary=row();summary.addView(text(tr("%d doses this month",monthCount),12,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));TextView today=chip("Today",false);today.setOnClickListener(v->{calendarDay=LocalDate.now();calendarMonth=YearMonth.now();render();});summary.addView(today);month.addView(summary);page.addView(month);gap(page,24,false);
        section(shortDate(calendarDay),null,null);
        ExportData dayData=new ExportData(all,calendarDay,calendarDay,ZoneId.systemDefault(),null,System.currentTimeMillis());long doses=dayData.events.stream().filter(e->e.type.equals("dose_taken")).count();
        LinearLayout totals=row();totals.addView(stat("Doses",doses,"pill"),new LinearLayout.LayoutParams(0,-2,1));gap(totals,12,true);totals.addView(stat("Events",dayData.events.size(),"clock"),new LinearLayout.LayoutParams(0,-2,1));page.addView(totals);gap(page,20,false);
        if(dayData.sessions.isEmpty()){LinearLayout empty=card();empty.addView(text(tr("No records on this day"),14,MUTED,false));page.addView(empty);}
        else{
            Map<Long,List<Session>> groups=new LinkedHashMap<>();for(Session s:dayData.sessions)groups.computeIfAbsent(s.medicineId,key->new ArrayList<>()).add(s);
            for(List<Session> group:groups.values()){
                Session latest=group.get(0);long count=group.stream().filter(s->date(s.takenAt).equals(calendarDay)).count();
                LinearLayout detail=column();detail.setPadding(0,dp(8),0,dp(12));detail.addView(text(latest.medicineName,16,INK,true));gap(detail,6,false);detail.addView(text(tr(count==1?"1 dose":"%d doses",count),13,ACCENT,true));gap(detail,5,false);detail.addView(text(tr("Last dose %s ago",elapsed(System.currentTimeMillis()-latest.takenAt)),12,MUTED,false));page.addView(detail);
                for(Session s:group)doseCard(page,s,false);
            }
        }
        if(!all.isEmpty()){gap(page,12,false);page.addView(text(tr("First recorded on %s",shortDate(date(all.get(all.size()-1).takenAt))),12,MUTED,false));}
    }
    private String tr(String english,Object... args){return I18n.tr(this,english,args);}
    private String elapsed(long milliseconds){long minutes=Math.max(0,milliseconds/60000);if(minutes<1)return tr("< 1 min");if(minutes<60)return tr("%d min",minutes);if(minutes>=1440)return tr("%d d",minutes/1440);long hours=minutes/60;return minutes%60==0?tr("%d h",hours):tr("%d h %d min",hours,minutes%60);}
    private void pickDate(LocalDate initial,Consumer<LocalDate> result) {
        new DatePickerDialog(this,(picker,y,m,d)->result.accept(LocalDate.of(y,m+1,d)),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();
    }
    private void pickTimestamp(long initial,Consumer<Long> result) {
        ZoneId zone=ZoneId.systemDefault();ZonedDateTime t=Instant.ofEpochMilli(initial).atZone(zone);
        pickDate(t.toLocalDate(),d->new TimePickerDialog(this,(picker,h,m)->result.accept(d.atTime(h,m).atZone(zone).toInstant().toEpochMilli()),t.getHour(),t.getMinute(),true).show());
    }
    private LocalDate date(long at){return Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate();}
    private String time(long at){return DateTimeFormatter.ofPattern("HH:mm",AppPrefs.locale(this)).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()));}
    private String fullTime(long at){return shortDate(date(at))+" · "+time(at);}
    private String shortDate(LocalDate d){return d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(AppPrefs.locale(this)));}
    private void toast(String message){Toast.makeText(this,tr(message==null?"Please try again.":message),Toast.LENGTH_SHORT).show();}

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void gap(LinearLayout l,int size,boolean horizontal){l.addView(new View(this),lp(horizontal?size:1,horizontal?1:size));}
    private TextView text(String value,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(Math.max(12,size));t.setTextColor(color);t.setFontFeatureSettings("kern");t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setIncludeFontPadding(false);return t;}
    private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private Drawable ripple(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x22b63840),shape(color,radius,color==SURFACE?LINE:0),shape(SURFACE,radius,0));}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(shape(SURFACE,20,LINE));return c;}
    private TextView badge(String label,int color,int bg){TextView t=text(tr(label),12,color,true);t.setPadding(dp(10),dp(5),dp(10),dp(5));t.setBackground(shape(bg,8,0));return t;}
    private TextView chip(String label,boolean selected){TextView t=text(tr(label),12,selected?ON_ACCENT:ACCENT,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(16),dp(12),dp(16),dp(12));t.setMinHeight(dp(48));t.setBackground(ripple(selected?ACCENT:PALE,24));return t;}
    private Button button(String label,boolean primary,Runnable click){Button b=new Button(this);b.setText(tr(label));b.setTextSize(14);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(primary?ON_ACCENT:INK);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setBackground(ripple(primary?ACCENT:SURFACE,24));b.setStateListAnimator(null);b.setOnClickListener(v->click.run());b.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return b;}
    private void section(String title,String action,Runnable click){LinearLayout r=row();r.addView(text(tr(title),18,INK,true),new LinearLayout.LayoutParams(0,-2,1));if(action!=null){TextView link=text(tr(action),12,click==null?MUTED:ACCENT,click!=null);link.setGravity(Gravity.CENTER);link.setMinHeight(dp(48));link.setMinWidth(dp(48));if(click!=null)link.setOnClickListener(v->click.run());r.addView(link);}page.addView(r);gap(page,8,false);}
    private int tint(int color){double a=night?.2:.09;return Color.rgb((int)(Color.red(color)*a+Color.red(SURFACE)*(1-a)),(int)(Color.green(color)*a+Color.green(SURFACE)*(1-a)),(int)(Color.blue(color)*a+Color.blue(SURFACE)*(1-a)));}
    private LinearLayout form(){LinearLayout l=column();l.setPadding(dp(24),dp(12),dp(24),dp(16));return l;}
    private ScrollView formScroll(LinearLayout content){ScrollView s=new ScrollView(this);s.setFillViewport(false);s.addView(content,new ScrollView.LayoutParams(-1,-2));return s;}
    private EditText edit(String hint,String value,int max,boolean multiline){EditText e=new EditText(this);e.setTextSize(16);e.setMinHeight(dp(56));e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(tr(hint));e.setText(value);e.setPadding(dp(13),dp(14),dp(13),dp(14));e.setBackground(shape(SURFACE,12,LINE));e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));e.setSingleLine(!multiline);if(multiline){e.setMinLines(3);e.setMaxLines(6);e.setGravity(Gravity.TOP|Gravity.START);}return e;}
    private EditText field(LinearLayout l,String label,String hint,String value,int max,boolean multiline){l.addView(text(tr(label),12,INK,true));gap(l,7,false);EditText e=edit(hint,value,max,multiline);e.setContentDescription(tr(label));l.addView(e,new LinearLayout.LayoutParams(-1,-2));gap(l,15,false);return e;}
    private ImageView icon(String kind,int color,int size){ImageView i=new ImageView(this);i.setImageDrawable(new LineIcon(kind,color));i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return i;}

    private static final class LineIcon extends Drawable {
        private final String kind;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        LineIcon(String kind,int color){this.kind=kind;p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
        @Override public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);
            Path path=new Path();
            switch(kind){
                case "calendar" -> {c.drawRoundRect(new RectF(3,5,21,22),2,2,p);c.drawLine(3,10,21,10,p);c.drawLine(8,2,8,7,p);c.drawLine(16,2,16,7,p);c.drawLine(7,14,9,14,p);c.drawLine(15,14,17,14,p);c.drawLine(7,18,9,18,p);}
                case "settings" -> {c.drawCircle(12,12,6,p);c.drawCircle(12,12,2.3f,p);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);c.drawLine(12,2,12,5,p);c.restore();}}
                case "radio-off" -> c.drawCircle(12,12,8,p);
                case "radio-on" -> {c.drawCircle(12,12,8,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,12,4,p);p.setStyle(Paint.Style.STROKE);}
                case "pill" -> {c.save();c.rotate(-42,12,12);c.drawRoundRect(new RectF(7,2,17,22),5,5,p);c.drawLine(7,12,17,12,p);c.restore();}
                case "home" -> {path.moveTo(3,10);path.lineTo(12,3);path.lineTo(21,10);path.moveTo(5,9);path.lineTo(5,21);path.lineTo(19,21);path.lineTo(19,9);path.moveTo(10,21);path.lineTo(10,14);path.lineTo(14,14);path.lineTo(14,21);c.drawPath(path,p);}
                case "clock" -> {c.drawCircle(12,12,9,p);c.drawLine(12,7,12,12,p);c.drawLine(12,12,16,14,p);}
                case "export" -> {path.moveTo(4,14);path.lineTo(4,21);path.lineTo(20,21);path.lineTo(20,14);path.moveTo(12,3);path.lineTo(12,15);path.moveTo(7,10);path.lineTo(12,15);path.lineTo(17,10);c.drawPath(path,p);}
                case "check" -> {c.drawCircle(12,12,9,p);path.moveTo(7,12);path.lineTo(10.5f,15.5f);path.lineTo(17,8.5f);c.drawPath(path,p);}
                case "shield" -> {path.moveTo(12,2);path.lineTo(21,6);path.lineTo(20,14);path.quadTo(18,20,12,23);path.quadTo(6,20,4,14);path.lineTo(3,6);path.close();c.drawPath(path,p);path.reset();path.moveTo(8,12);path.lineTo(11,15);path.lineTo(16,9);c.drawPath(path,p);}
            }c.restore();}
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}

package fr.mediclogger.app;

import android.app.Application;

public final class MedicApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        StorageMigration.initialize(this);
    }
}

package com.nexhacks.tapmate;

import android.app.Application;
import com.nexhacks.tapmate.utils.Config;

public class TapMateApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Config.loadEnv(this);
    }
}

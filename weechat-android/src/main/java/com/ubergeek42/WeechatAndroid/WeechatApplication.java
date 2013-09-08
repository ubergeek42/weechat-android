package com.ubergeek42.WeechatAndroid;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;

@ReportsCrashes(formKey = "",
formUri = "http://ubergeek42.com/android/submit.php",

excludeMatchingSharedPreferencesKeys={"(ssh_pass|ssh_user|password|stunnel_pass)"},

mode = ReportingInteractionMode.NOTIFICATION,
resNotifTickerText = R.string.crash_notif_ticker_text,
resNotifTitle = R.string.crash_notif_title,
resNotifText = R.string.crash_notif_text,
resDialogText = R.string.crash_dialog_text,
resDialogCommentPrompt = R.string.crash_dialog_comment_prompt
)
public class WeechatApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // The following line triggers the initialization of ACRA
        ACRA.init(this);
    }
}

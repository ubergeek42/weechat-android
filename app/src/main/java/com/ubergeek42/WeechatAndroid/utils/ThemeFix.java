package com.ubergeek42.WeechatAndroid.utils;

import android.content.res.TypedArray;
import android.os.Build;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

public class ThemeFix {

    // fix windowLightStatusBar/windowLightNavigationBar not changed after changing theme on
    // Android O. the bug only affects oreo, not previous versions or pie
    // this must be called in onCreate() of the activity

    // https://stackoverflow.com/questions/46109569/issue-with-toggling-status-bar-color-on-android-o
    // https://issuetracker.google.com/issues/65883460

    public static void fixLightStatusAndNavigationBar(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (Build.VERSION.SDK_INT >= 28) return;

        TypedArray a = activity.obtainStyledAttributes(new int[]{
                android.R.attr.windowLightStatusBar,
                android.R.attr.windowLightNavigationBar});
        final boolean windowLightStatusBar = a.getBoolean(0, false);
        final boolean windowLightNavigationBar = a.getBoolean(0, false);
        a.recycle();

        int flag = activity.getWindow().getDecorView().getSystemUiVisibility();

        flag = addRemoveConstant(flag, View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR, windowLightStatusBar);
        flag = addRemoveConstant(flag, View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR, windowLightNavigationBar);

        activity.getWindow().getDecorView().setSystemUiVisibility(flag);
    }

    private static int addRemoveConstant(int flag, int constant, boolean add) {
        return add ? flag | constant : flag & ~constant;
    }
}

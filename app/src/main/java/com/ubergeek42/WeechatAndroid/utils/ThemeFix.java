package com.ubergeek42.WeechatAndroid.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Build;

import androidx.annotation.DrawableRes;
import androidx.annotation.MainThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import android.view.View;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.P;

public class ThemeFix {

    // fix windowLightStatusBar/windowLightNavigationBar not changed after changing theme on
    // Android O. the bug only affects oreo, not previous versions or pie
    // this must be called in onCreate() of the activity

    // https://stackoverflow.com/questions/46109569/issue-with-toggling-status-bar-color-on-android-o
    // https://issuetracker.google.com/issues/65883460

    // AS complains that android.R.attr.windowLightNavigationBar needs api 27, and we aren't
    // setting android:windowLightNavigationBar before api 27, but the resulting boolean
    // windowLightNavigationBar is correct and changes depending on the theme

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

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // set the icon that appears in recent application list;
    // also set the color of the title bar in recent app list, as changing the theme doesn't
    // immediately change the color—the old color is used unless you kill the activity.
    // also explicitly set application name, since calling setTaskDescription screws it up
    // on android m. note that ActivityManager$TaskDescription(String, int, ...) doesn't
    // exist on android < p, so create the bitmap manually

    @SuppressWarnings("deprecation") @MainThread
    public static void fixIconAndColor(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        String appName = activity.getString(R.string.app_name);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.setTaskDescription(new ActivityManager.TaskDescription(null,
                    0, 0xff000000 | P.colorPrimary));
        } else {
            activity.setTaskDescription(new ActivityManager.TaskDescription(appName,
                    null, 0xff000000 | P.colorPrimary));
        }
    }

    private static Bitmap getBitmapFromDrawable(Context context, @DrawableRes int drawableId) {
        Drawable drawable = AppCompatResources.getDrawable(context, drawableId);

        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        } else if (drawable instanceof VectorDrawableCompat ||
                (Build.VERSION.SDK_INT >= 26 && drawable instanceof AdaptiveIconDrawable) ||
                (Build.VERSION.SDK_INT >= 21 && drawable instanceof VectorDrawable)) {
            Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);

            return bitmap;
        } else {
            throw new IllegalArgumentException("unsupported drawable type: " + drawable);
        }
    }

    public static boolean isColorLight(int color) {
        int avg = (((color >> 16) & 0xff) +
                ((color >> 8) & 0xff) +
                (color & 0xff)) / 3;
        return avg > (0xff / 2);
    }

    // returns whether or not the context is using night mode. this mode is set by either calling
    // AppCompatDelegate.setDefaultNightMode() with a specific mode or is set by the system. the
    // system will recreate the activity when the actual mode changes. treat undefined flag as night mode
    public static boolean isNightModeEnabledForActivity(AppCompatActivity activity) {
        int flag = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return flag != Configuration.UI_MODE_NIGHT_NO;
    }
}

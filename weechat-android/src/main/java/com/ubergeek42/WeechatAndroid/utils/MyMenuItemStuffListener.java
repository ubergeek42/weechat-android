package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

/**
 * a listener that provides toast creation on long click
 * useful for action bar menu items with custom layouts
 * see http://stackoverflow.com/questions/17696486/actionbar-notification-count-icon-like-google-have/25453979#25453979
 */
public abstract class MyMenuItemStuffListener implements View.OnClickListener, View.OnLongClickListener {
    private String hint;
    private View view;

    public MyMenuItemStuffListener(View view, String hint) {
        this.view = view;
        this.hint = hint;
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
    }

    @Override abstract public void onClick(View v);

    @Override public boolean onLongClick(View v) {
        final int[] screenPos = new int[2];
        final Rect displayFrame = new Rect();
        view.getLocationOnScreen(screenPos);
        view.getWindowVisibleDisplayFrame(displayFrame);
        final Context context = view.getContext();
        final int width = view.getWidth();
        final int height = view.getHeight();
        final int midy = screenPos[1] + height / 2;
        final int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        Toast cheatSheet = Toast.makeText(context, hint, Toast.LENGTH_SHORT);
        if (midy < displayFrame.height()) {
            cheatSheet.setGravity(Gravity.TOP | Gravity.RIGHT,
                    screenWidth - screenPos[0] - width / 2, height);
        } else {
            cheatSheet.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, height);
        }
        cheatSheet.show();
        return true;
    }
}

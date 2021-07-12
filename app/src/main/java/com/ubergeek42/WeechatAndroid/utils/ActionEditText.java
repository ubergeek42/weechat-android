package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import androidx.appcompat.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.service.P;

public class ActionEditText extends AppCompatEditText {
    public void setFont() {
        ((TextView) this).setTypeface(P.typeface);
    }
    public ActionEditText(Context context) {
        super(context);
        setFont();
    }

    public ActionEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFont();
    }

    public ActionEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setFont();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection conn = super.onCreateInputConnection(outAttrs);
        outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        return conn;
    }
}

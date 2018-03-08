package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class ActionEditText extends AppCompatEditText {
    public ActionEditText(Context context) {
        super(context);
    }

    public ActionEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ActionEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection conn = super.onCreateInputConnection(outAttrs);
        outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        outAttrs.inputType &= ~InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        return conn;
    }
}

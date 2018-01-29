package com.ubergeek42.cats;

import android.util.Log;

class Printer {
    void println(int priority, String tag, String msg) {
        Log.println(priority, tag, msg);
    }
}

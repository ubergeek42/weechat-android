package com.ubergeek42;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TabWidget;

/**
 * Fix for broken TabWidget on android 2.?
 * See the following URLs for more information/where the code came from:
 * http://code.google.com/p/android/issues/detail?id=2772
 * http://groups.google.com/group/android-developers/browse_thread/thread/8a24314b853bccb5
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */

public class TabWidgetFix extends TabWidget {
	private static final Logger logger = LoggerFactory.getLogger(TabWidgetFix.class);
	
	private final View dummy;
	public TabWidgetFix(Context context) {
		super(context);
		dummy = new View(context);
	}
	

	public TabWidgetFix(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		dummy = new View(context);
	}


	public TabWidgetFix(Context context, AttributeSet attrs) {
		super(context, attrs);
		dummy = new View(context);
	}


	public View getChildAt(int i) {
		if (i<0 || i>= getChildCount()) {
			return dummy;
		}
		return super.getChildAt(i);
	}
	
}

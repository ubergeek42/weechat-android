package com.ubergeek42.WeechatAndroid;

import android.preference.PreferenceActivity;
import android.os.Bundle;

public class WeechatPreferencesActivity extends PreferenceActivity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    addPreferencesFromResource(R.xml.preferences);
	    
	}

}

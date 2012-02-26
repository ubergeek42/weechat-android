package com.ubergeek42;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class WeechatPreferencesActivity extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
	}

}

package com.ubergeek42.WeechatAndroid;

import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;

public class WeechatPreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	private SharedPreferences sharedPreferences;
	private EditTextPreference hostPref;
	private EditTextPreference portPref;
	private EditTextPreference textSizePref;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    addPreferencesFromResource(R.xml.preferences);
	    sharedPreferences = getPreferenceScreen().getSharedPreferences();
	    	    
	    hostPref = (EditTextPreference) getPreferenceScreen().findPreference("host");
	    portPref = (EditTextPreference) getPreferenceScreen().findPreference("port");
	    textSizePref = (EditTextPreference) getPreferenceScreen().findPreference("text_size");
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);
		
	    hostPref.setSummary(sharedPreferences.getString("host", ""));
	    portPref.setSummary(sharedPreferences.getString("port", "8001"));
	    textSizePref.setSummary(sharedPreferences.getString("text_size", "10"));
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("host")) {
			hostPref.setSummary(sharedPreferences.getString(key, ""));
		} else if(key.equals("port")) {
			portPref.setSummary(sharedPreferences.getString("port", "8001"));
		} else if(key.equals("text_size")) {
			textSizePref.setSummary(sharedPreferences.getString("text_size", "10"));
		}
	}

}

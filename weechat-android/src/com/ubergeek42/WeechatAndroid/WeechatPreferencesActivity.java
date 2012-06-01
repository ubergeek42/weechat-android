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
	private EditTextPreference passPref;
	private EditTextPreference stunnelCert;
	private EditTextPreference stunnelPass;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    addPreferencesFromResource(R.xml.preferences);
	    sharedPreferences = getPreferenceScreen().getSharedPreferences();
	    	    
	    hostPref = (EditTextPreference) getPreferenceScreen().findPreference("host");
	    portPref = (EditTextPreference) getPreferenceScreen().findPreference("port");
	    passPref = (EditTextPreference) getPreferenceScreen().findPreference("password");
	    textSizePref = (EditTextPreference) getPreferenceScreen().findPreference("text_size");
	    stunnelCert = (EditTextPreference) getPreferenceScreen().findPreference("stunnel_cert");
	    stunnelPass = (EditTextPreference) getPreferenceScreen().findPreference("stunnel_pass");
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
	    stunnelCert.setSummary(sharedPreferences.getString("stunnel_cert", "Not Set"));
	    
	    if (sharedPreferences.getString("password", null) == null){
	    	passPref.setSummary("None Set");
	    }else{
	    	passPref.setSummary("******");
	    }
	    
	    if (sharedPreferences.getString("stunnel_pass", null) == null){
	    	stunnelPass.setSummary("None Set");
	    }else{
	    	stunnelPass.setSummary("******");
	    }
    }

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("host")) {
			hostPref.setSummary(sharedPreferences.getString(key, ""));
		} else if(key.equals("port")) {
			portPref.setSummary(sharedPreferences.getString("port", "8001"));
		} else if(key.equals("password")) {
			if (sharedPreferences.getString("password", null) == null){
		    	passPref.setSummary("None Set");
		    }else{
		    	passPref.setSummary("******");
		    }
		} else if(key.equals("text_size")) {
			textSizePref.setSummary(sharedPreferences.getString("text_size", "10"));
		} else if(key.equals("stunnel_cert")) {
			stunnelCert.setSummary(sharedPreferences.getString("stunnel_cert", "/sdcard/weechat/client.p12"));
		} else if(key.equals("stunnel_pass")) {
			if (sharedPreferences.getString("stunnel_pass", null) == null){
		    	stunnelPass.setSummary("None Set");
		    }else{
		    	stunnelPass.setSummary("******");
		    }
		}
	}

}

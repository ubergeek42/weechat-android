/*******************************************************************************
 * Copyright 2012 Keith Johnson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
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
	    
	    String tmp;
	    tmp = sharedPreferences.getString("password", null);
	    if ( tmp == null || tmp.equals("")){
	    	passPref.setSummary("None Set");
	    }else{
	    	passPref.setSummary("******");
	    }
	    tmp = sharedPreferences.getString("stunnel_pass", null);
	    if ( tmp == null || tmp.equals("")){
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
			String tmp = sharedPreferences.getString("password", null);
			if (tmp == null || tmp.equals("")){
		    	passPref.setSummary("None Set");
		    }else{
		    	passPref.setSummary("******");
		    }
		} else if(key.equals("text_size")) {
			textSizePref.setSummary(sharedPreferences.getString("text_size", "10"));
		} else if(key.equals("stunnel_cert")) {
			stunnelCert.setSummary(sharedPreferences.getString("stunnel_cert", "/sdcard/weechat/client.p12"));
		} else if(key.equals("stunnel_pass")) {
			String tmp = sharedPreferences.getString("stunnel_pass", null);
			if (tmp == null || tmp.equals("")){
		    	stunnelPass.setSummary("None Set");
		    }else{
		    	stunnelPass.setSummary("******");
		    }
		}
	}

}

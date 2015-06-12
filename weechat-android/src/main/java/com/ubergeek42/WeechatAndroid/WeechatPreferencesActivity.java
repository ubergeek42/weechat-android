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

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.*;
import android.widget.BaseAdapter;
import android.widget.Toast;

import com.ubergeek42.WeechatAndroid.utils.FontManager;
import com.ubergeek42.WeechatAndroid.utils.FontPreference;

import java.text.SimpleDateFormat;
import java.util.Arrays;

public class WeechatPreferencesActivity extends PreferenceActivity implements
        OnSharedPreferenceChangeListener {

    private SharedPreferences sharedPreferences;
    private PreferenceScreen connectionSettings;
    private EditTextPreference hostPref;
    private EditTextPreference portPref;
    private EditTextPreference textSizePref;
    private EditTextPreference timestampformatPref;
    private EditTextPreference passPref;
    private EditTextPreference stunnelCert;
    private EditTextPreference stunnelPass;
    private EditTextPreference sshHostPref;
    private EditTextPreference sshPortPref;
    private EditTextPreference sshPassPref;
    private EditTextPreference sshUserPref;
    private EditTextPreference sshKeyFilePref;
    private ListPreference prefixPref;
    private ListPreference connectionTypePref;
    private PreferenceScreen pingPreferences;
    private CheckBoxPreference pingEnabledPref;
    private FontPreference bufferFontPref;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        sharedPreferences = getPreferenceScreen().getSharedPreferences();

        connectionSettings = (PreferenceScreen) getPreferenceScreen().findPreference("connection_group");
        hostPref = (EditTextPreference) getPreferenceScreen().findPreference("host");
        portPref = (EditTextPreference) getPreferenceScreen().findPreference("port");
        passPref = (EditTextPreference) getPreferenceScreen().findPreference("password");
        textSizePref = (EditTextPreference) getPreferenceScreen().findPreference("text_size");
        bufferFontPref = (FontPreference) getPreferenceScreen().findPreference("buffer_font");
        timestampformatPref = (EditTextPreference) getPreferenceScreen().findPreference(
                "timestamp_format");
        timestampformatPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object pattern) {
                try {
                    new SimpleDateFormat((String)pattern);
                } catch (IllegalArgumentException e) {
                    Toast.makeText(WeechatPreferencesActivity.this, R.string.pref_timestamp_invalid, Toast.LENGTH_SHORT).show();
                    return false;
                }

                return true;
            }
        });
        stunnelCert = (EditTextPreference) getPreferenceScreen().findPreference("stunnel_cert");
        stunnelPass = (EditTextPreference) getPreferenceScreen().findPreference("stunnel_pass");

        sshHostPref = (EditTextPreference) getPreferenceScreen().findPreference("ssh_host");
        sshUserPref = (EditTextPreference) getPreferenceScreen().findPreference("ssh_user");
        sshPortPref = (EditTextPreference) getPreferenceScreen().findPreference("ssh_port");
        sshPassPref = (EditTextPreference) getPreferenceScreen().findPreference("ssh_pass");
        sshKeyFilePref = (EditTextPreference) getPreferenceScreen().findPreference("ssh_keyfile");

        prefixPref = (ListPreference) getPreferenceScreen().findPreference("prefix_align");
        Preference prefixWidthPref = getPreferenceScreen().findPreference("prefix_max_width");
        prefixWidthPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                try {
                    Integer.parseInt((String)newValue);
                } catch (IllegalArgumentException e) {
                    Toast.makeText(WeechatPreferencesActivity.this, R.string.pref_prefix_width_invalid, Toast.LENGTH_SHORT).show();
                    return false;
                }

                return true;
            }
        });
        connectionTypePref = (ListPreference) getPreferenceScreen().findPreference("connection_type");
        pingPreferences = (PreferenceScreen) getPreferenceScreen().findPreference("ping_group");
        pingEnabledPref = (CheckBoxPreference) getPreferenceScreen().findPreference("ping_enabled");
        setTitle(R.string.preferences);
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
        updateBufferFontSummary();

        timestampformatPref.setSummary(sharedPreferences.getString("timestamp_format", "HH:mm:ss"));
        stunnelCert.setSummary(sharedPreferences.getString("stunnel_cert", "Not Set"));

        sshHostPref.setSummary(sharedPreferences.getString("ssh_host", ""));
        sshUserPref.setSummary(sharedPreferences.getString("ssh_user", ""));
        sshPortPref.setSummary(sharedPreferences.getString("ssh_port", "22"));
        sshKeyFilePref.setSummary(sharedPreferences.getString("ssh_keyfile", "Not Set"));

        prefixPref.setSummary(prefixPref.getEntry());
        connectionTypePref.setSummary(connectionTypePref.getEntry());

        if (pingEnabledPref.isChecked()) {
            pingPreferences.setSummary("Enabled");
        } else {
            pingPreferences.setSummary("Disabled");
        }

        String tmp;
        tmp = sharedPreferences.getString("password", null);
        if (tmp == null || tmp.equals("")) {
            passPref.setSummary("None Set");
        } else {
            passPref.setSummary("******");
        }
        tmp = sharedPreferences.getString("stunnel_pass", null);
        if (tmp == null || tmp.equals("")) {
            stunnelPass.setSummary("None Set");
        } else {
            stunnelPass.setSummary("******");
        }
        tmp = sharedPreferences.getString("ssh_pass", null);
        if (tmp == null || tmp.equals("")) {
            sshPassPref.setSummary("None Set");
        } else {
            sshPassPref.setSummary("******");
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "host":
                hostPref.setSummary(sharedPreferences.getString(key, ""));
                break;
            case "port":
                portPref.setSummary(sharedPreferences.getString("port", "8001"));
                break;
            case "password": {
                String tmp = sharedPreferences.getString("password", null);
                if (tmp == null || tmp.equals("")) {
                    passPref.setSummary("None Set");
                } else {
                    passPref.setSummary("******");
                }
                break;
            }
            case "text_size":
                textSizePref.setSummary(sharedPreferences.getString("text_size", "10"));
                break;
            case "timestamp_format":
                timestampformatPref.setSummary(sharedPreferences.getString("timestamp_format",
                        "HH:mm:ss"));
                break;
            case "stunnel_cert":
                stunnelCert.setSummary(sharedPreferences.getString("stunnel_cert", "/sdcard/weechat/client.p12"));
                break;
            case "stunnel_pass": {
                String tmp = sharedPreferences.getString("stunnel_pass", null);
                if (tmp == null || tmp.equals("")) {
                    stunnelPass.setSummary("None Set");
                } else {
                    stunnelPass.setSummary("******");
                }
                break;
            }
            case "ssh_host":
                sshHostPref.setSummary(sharedPreferences.getString(key, ""));
                break;
            case "ssh_user":
                sshUserPref.setSummary(sharedPreferences.getString(key, ""));
                break;
            case "ssh_port":
                sshPortPref.setSummary(sharedPreferences.getString(key, "22"));
                break;
            case "ssh_pass": {
                String tmp = sharedPreferences.getString("ssh_pass", null);
                if (tmp == null || tmp.equals("")) {
                    sshPassPref.setSummary("None Set");
                } else {
                    sshPassPref.setSummary("******");
                }
                break;
            }
            case "ssh_keyfile":
                sshKeyFilePref.setSummary(sharedPreferences.getString(key, "/sdcard/weechat/sshkey.id_rsa"));
                break;
            case "prefix_align":
                prefixPref.setSummary(prefixPref.getEntry());
                break;
            case "connection_type":
                connectionTypePref.setSummary(connectionTypePref.getEntry());
                break;
            case "ping_enabled":
                boolean pingEnabled = sharedPreferences.getBoolean("ping_enabled", true);
                if (pingEnabled) {
                    pingPreferences.setSummary("Enabled");
                } else {
                    pingPreferences.setSummary("Disabled");
                }
                ((BaseAdapter) connectionSettings.getRootAdapter()).notifyDataSetChanged();
                break;
            case "buffer_font":
                updateBufferFontSummary();
                break;
        }
    }

    private void updateBufferFontSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.pref_font_summary));
        sb.append("\n\nSearch Path:\n");
        for (String p: FontManager.fontdirs)
            sb.append("   " + p + "\n");
        sb.append("\nCurrent Value:\n" + sharedPreferences.getString("buffer_font", "Not Set"));
        bufferFontPref.setSummary(sb.toString());

    }
}

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

import java.security.cert.CertPath;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.service.Buffer;
import com.ubergeek42.WeechatAndroid.service.BufferList;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.HotlistItem;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;

import android.support.v4.view.PagerTitleStrip;

public class WeechatActivity extends SherlockFragmentActivity implements RelayConnectionHandler, OnPageChangeListener {
    
    private static Logger logger = LoggerFactory.getLogger("WA");
    final private static boolean DEBUG = BuildConfig.DEBUG && true;

    public RelayServiceBinder relay;
    private SocketToggleConnection connection_state_toggler;
    private Menu actionBarMenu;
    private ViewPager viewPager;
    private MainPagerAdapter mainPagerAdapter;
    private PagerTitleStrip titleIndicator;
    private InputMethodManager imm;
    
    private boolean phone_mode;

    /////////////////////////
    ///////////////////////// lifecycle
    /////////////////////////

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) logger.debug("onCreate({})", savedInstanceState);
        super.onCreate(savedInstanceState);

        // Start the background service (if necessary)
        startService(new Intent(this, RelayService.class));

        // Load the layout
        setContentView(R.layout.main_screen);

        FragmentManager manager = getSupportFragmentManager();
        viewPager = (ViewPager) findViewById(R.id.main_viewpager);
        mainPagerAdapter = new MainPagerAdapter(this, manager, viewPager);

        // run adapter.firstTimeInit() only the first time application is started
        // adapter.restoreState() will take care of setting phone_mode other times
        phone_mode = manager.findFragmentById(R.id.bufferlist_fragment) == null;
        if (savedInstanceState == null)
            mainPagerAdapter.firstTimeInit(phone_mode);

        viewPager.setAdapter(mainPagerAdapter);

        // pages to each side of the on-screen page that should stay in memory
        // the other pages are NOT destroyed, merely their views are
        viewPager.setOffscreenPageLimit(0);

        ActionBar ab = getSupportActionBar();
        ab.setHomeButtonEnabled(true);
        ab.setDisplayShowCustomEnabled(true);
        ab.setDisplayShowTitleEnabled(false);
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        CutePagerTitleStrip strip = (CutePagerTitleStrip) inflater.inflate(R.layout.cute_pager_title_strip_layout, null);
        strip.setViewPager(viewPager);
        strip.setOnPageChangeListener(this);
        ab.setCustomView(strip);

        // TODO Read preferences from background, its IO, 31ms strict mode!
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        setTitle(getString(R.string.app_version));

        // TODO: make notification load the right ui_buffer
        // TODO: add preference to hide the TitlePageIndicator
    }

    /** bind to relay service, which results in:
     **   onServiceConnected, which
     **     adds relay connection handler */
    // TODO: android.app.ServiceConnectionLeaked: Activity com.ubergeek42.WeechatAndroid.WeechatActivity
    // TODO: has leaked ServiceConnection com.ubergeek42.WeechatAndroid.WeechatActivity$1@424fdbe8 that was originally bound here
    // TODO: apparently onStop() sometimes doesn't get to unbind the service as onServiceConnected is called too late
    // TODO: then onStart() is trying to bind again and boom! anyways, this doesn't do any visible harm...
    @Override
    protected void onStart() {
        if (DEBUG) logger.debug("onStart()");
        super.onStart();
        if (DEBUG) logger.debug("...calling bindService()");
        bindService(new Intent(this, RelayService.class), service_connection, Context.BIND_AUTO_CREATE);
    }

    /** if connection toggler is active, cancel it,
     ** remove relay connection handler and
     ** unbind from service */
    @Override
    protected void onStop() {
        if (DEBUG) logger.debug("onStop()");
        super.onStop();

        if (connection_state_toggler != null
                && connection_state_toggler.getStatus() != AsyncTask.Status.FINISHED) {
            connection_state_toggler.cancel(true);
            connection_state_toggler = null;
        }

        if (relay != null) {
            if (DEBUG) logger.debug("...calling unbindService()");
            relay.removeRelayConnectionHandler(WeechatActivity.this);
            unbindService(service_connection);
            relay = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (DEBUG) logger.debug("onDestroy()");
        super.onDestroy();
    }

    /////////////////////////
    ///////////////////////// relay connection
    /////////////////////////

    ServiceConnection service_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) logger.debug("onServiceConnected(), main thread? {}", Looper.myLooper() == Looper.getMainLooper());
            relay = (RelayServiceBinder) service;
            relay.addRelayConnectionHandler(WeechatActivity.this);

            // Check if the service is already connected to the weechat relay, and if so load it up
            if (relay.isConnection(RelayService.CONNECTED))
                WeechatActivity.this.onConnect();

            // open buffer that MIGHT be open in the service
            if (relay.getBufferList() != null)
                for (String full_name : BufferList.synced_buffers_full_names)
                    mainPagerAdapter.openBuffer(full_name, false);

            // open the ui_buffer we want
            handleIntent();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) logger.debug("onServiceDisconnected()");
            relay = null;
        }
    };

    /////////////////////////
    ///////////////////////// RelayConnectionHandler
    /////////////////////////

    @Override
    public void onConnecting() {}

    /** creates and updates the hotlist
     ** makes sure we update action bar menu after a connection change */
    @Override
    public void onConnect() {
        makeMenuReflectConnectionStatus();
    }

    @Override
    public void onAuthenticated() {}

    @Override
    public void onBuffersListed() {}

    /** makes sure we update action bar menu after a connection change */
    @Override
    public void onDisconnect() {
        makeMenuReflectConnectionStatus();
    }

    @Override
    public void onError(final String errorMsg, Object extraData) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getBaseContext(), "Error: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        });        
        if (DEBUG) logger.error("onError({}, ...)", errorMsg);
        if (extraData instanceof SSLException) {
            if (DEBUG) logger.error("...cause: {}", ((SSLException) extraData).getCause());
            SSLException e1 = (SSLException) extraData;
            if (e1.getCause() instanceof CertificateException) {
                CertificateException e2 = (CertificateException) e1.getCause();
                
                if (e2.getCause() instanceof CertPathValidatorException) {
                    CertPathValidatorException e = (CertPathValidatorException) e2.getCause();
                    CertPath cp = e.getCertPath();                    
                    
                    // Set the cert error on the backend
                    relay.setCertificateError((X509Certificate) cp.getCertificates().get(0));
                    
                    // Start an activity to attempt establishing trust
                    Intent i = new Intent(this, SSLCertActivity.class);
                    startActivity(i);
                }
            }
        }
    }

    /////////////////////////
    ///////////////////////// connection toggler
    /////////////////////////

    private class SocketToggleConnection extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            if (relay.isConnection(RelayService.CONNECTED)) {
                relay.shutdown();
                return true;
            } else {
                return relay.connect();
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            supportInvalidateOptionsMenu();
        }
    }

    /////////////////////////
    ///////////////////////// OnPageChangeListener
    /////////////////////////

    @Override
    public void onPageScrollStateChanged(int state) {}

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

    @Override
    public void onPageSelected(int position) {
        invalidateOptionsMenu();
        hideSoftwareKeyboard();
    }

    /////////////////////////
    ///////////////////////// other fragment methods
    /////////////////////////

    @Override
    protected void onNewIntent(Intent intent) {
        if (DEBUG) logger.debug("onNewIntent({})", intent);
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent();
    }

    /** Can safely hold on to this according to docs
     ** http://developer.android.com/reference/android/app/Activity.html#onCreateOptionsMenu(android.view.Menu) **/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (false && DEBUG) logger.debug("onCreateOptionsMenu(...)");
        MenuInflater menuInflater = getSupportMenuInflater();
        menuInflater.inflate(R.menu.menu_actionbar, menu);
        actionBarMenu = menu;
        makeMenuReflectConnectionStatus();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (false && DEBUG) logger.debug("onPrepareOptionsMenu(...)");
        super.onPrepareOptionsMenu(menu);
        boolean buffer_visible = !(phone_mode && viewPager.getCurrentItem() == 0);
        menu.findItem(R.id.menu_nicklist).setVisible(buffer_visible);
        menu.findItem(R.id.menu_close).setVisible(buffer_visible);
        return true;
    }

    /** handle the options when the user presses the menu button */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                if (viewPager != null)
                    viewPager.setCurrentItem(0);
                break;
            }
            case R.id.menu_connection_state: {
                if (relay != null) {
                    connection_state_toggler = new SocketToggleConnection();
                    connection_state_toggler.execute();
                }
                break;
            }
            case R.id.menu_preferences: {
                Intent i = new Intent(this, WeechatPreferencesActivity.class);
                startActivity(i);
                break;
            }
            case R.id.menu_close: {
                BufferFragment currentBuffer = mainPagerAdapter.getCurrentBufferFragment();
                if (currentBuffer != null) {
                    currentBuffer.onBufferClosed();
                }
                break;
            }
            case R.id.menu_about: {
                Intent i = new Intent(this, WeechatAboutActivity.class);
                startActivity(i);
                break;
            }
            case R.id.menu_quit: {
                if (relay != null) {
                    relay.shutdown();
                }
                unbindService(service_connection);
                relay = null;
                stopService(new Intent(this, RelayService.class));
                finish();
                break;
            }
            case R.id.menu_hotlist: {
                if (relay != null && relay.isConnection(RelayService.CONNECTED)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(R.string.hotlist);
                    final HotlistListAdapter hla = new HotlistListAdapter(WeechatActivity.this, relay);
                    builder.setAdapter(hla, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int position) {
                            HotlistItem hotlistItem = hla.getItem(position);
//                            String name = hotlistItem.getFullName();
//                            openBuffer(name);
                        }
                    });
                    builder.create().show();
                }
                break;
            }
            case R.id.menu_nicklist: {
                BufferFragment currentBuffer = mainPagerAdapter.getCurrentBufferFragment();
                if (currentBuffer == null)
                    break;
                ArrayList<String> nicks = null; // currentBuffer.getNicklist();
                if (nicks == null)
                    break;

                NickListAdapter nicklistAdapter = new NickListAdapter(WeechatActivity.this, nicks);

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.nicklist_menu) + " (" + nicks.size() + ")");
                builder.setAdapter(nicklistAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int position) {
                        // TODO define something to happen here
                    }
                });
                builder.create().show();
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    /////////////////////////
    ///////////////////////// private stuff
    /////////////////////////

    /** when we get an intent, do something with it
     ** apparently on certain systems android passes an extra key "profile"
     ** containing an android.os.UserHandle object, so it might be non-null
     ** even if doesn't contain "buffers" */
    private void handleIntent() {
        if (DEBUG) logger.debug("handleIntent()");
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String full_name = extras.getString("full_name");
            if (full_name != null) {
                if (DEBUG) logger.debug("handleIntent(): opening ui_buffer '{}' from extras", full_name);
                openBuffer(full_name, true);
            }
        }
    }

    /** change first menu item from connect to disconnect or back depending on connection status */
    private void makeMenuReflectConnectionStatus() {
        if (false && DEBUG) logger.debug("makeMenuReflectConnectionStatus()");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (WeechatActivity.this.actionBarMenu != null) {
                    MenuItem connectionStatus = WeechatActivity.this.actionBarMenu.findItem(R.id.menu_connection_state);
                    if (relay != null && (relay.isConnection(RelayService.CONNECTED)))
                        connectionStatus.setTitle(R.string.disconnect);
                    else
                        connectionStatus.setTitle(R.string.connect);
                }
            }
        });
    }

    /////////////////////////
    ///////////////////////// misc
    /////////////////////////

    public void openBuffer(String full_name, boolean focus) {
        if (DEBUG) logger.debug("openBuffer({})", full_name);
        mainPagerAdapter.openBuffer(full_name, focus);
    }

    // In own thread to prevent things from breaking
    public void closeBuffer(String full_name) {
        if (DEBUG) logger.debug("closeBuffer({})", full_name);
        mainPagerAdapter.closeBuffer(full_name);
    }

    /** hides the software keyboard, if any */
    public void hideSoftwareKeyboard() {
        imm.hideSoftInputFromWindow(viewPager.getWindowToken(), 0);
    }

    @Override
    public void onBackPressed() {
        if (DEBUG) logger.debug("onBackPressed()");
        if (phone_mode && viewPager.getCurrentItem() != 0) viewPager.setCurrentItem(0);
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (DEBUG) logger.debug("onSaveInstanceState()");
        super.onSaveInstanceState(outState);
    }
}

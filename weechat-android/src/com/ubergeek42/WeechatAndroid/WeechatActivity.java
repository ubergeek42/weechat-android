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
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.util.Log;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.fragments.BufferListFragment;
import com.ubergeek42.WeechatAndroid.fragments.BufferListFragment.OnBufferSelectedListener;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.HotlistItem;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;
import com.viewpagerindicator.TitlePageIndicator;

public class WeechatActivity extends SherlockFragmentActivity implements RelayConnectionHandler, OnBufferSelectedListener {
    
    private static Logger logger = LoggerFactory.getLogger(WeechatActivity.class);
    private boolean mBound = false;
    private RelayServiceBinder rsb;
    
    private ViewPager viewPager;
    private MainPagerAdapter mainPagerAdapter;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start the background service(if necessary)
        startService(new Intent(this, RelayService.class));

        // Load the layout
        setContentView(R.layout.main_screen);
        
        viewPager = (ViewPager) findViewById(R.id.main_viewpager);
        
        // Restore state if we have it
        if (savedInstanceState != null) {
            // Load the previous BufferListFragment, and the Buffers that were open 
            int numpages = savedInstanceState.getInt("numpages");
            BufferListFragment blf = (BufferListFragment)getSupportFragmentManager().getFragment(savedInstanceState, "saved_frag_0");
            ArrayList<BufferFragment> frags = new ArrayList<BufferFragment>();
            for(int i=1;i<numpages;i++) {
                BufferFragment f = (BufferFragment)getSupportFragmentManager().getFragment(savedInstanceState, "saved_frag_"+i);
                if (f!=null)
                    frags.add(f);
            }
            mainPagerAdapter = new MainPagerAdapter(getSupportFragmentManager(), viewPager, blf, frags);
        } else {
            mainPagerAdapter = new MainPagerAdapter(getSupportFragmentManager(), viewPager);
        }
        viewPager.setAdapter(mainPagerAdapter);
        viewPager.setOffscreenPageLimit(10);// TODO: probably a crash if more than 10 buffers, and screen rotates
        // see: http://stackoverflow.com/questions/11296411/fragmentstatepageradapter-illegalstateexception-myfragment-is-not-currently

        TitlePageIndicator titleIndicator = (TitlePageIndicator) findViewById(R.id.main_titleviewpager);
        titleIndicator.setViewPager(viewPager);

        // TODO Read preferences from background, its IO, 31ms strict mode!
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        
        updateTitle();
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("numpages", mainPagerAdapter.getCount());
        for(int i=0;i<mainPagerAdapter.getCount(); i++) {
            if (mainPagerAdapter.getItem(i)!=null)
                getSupportFragmentManager().putFragment(outState,"saved_frag_"+i, mainPagerAdapter.getItem(i));
        }
    }
    
    @Override
    public void onBufferSelected(String buffer) {
       // Does the buffer actually exist?(If not, return)
        if (rsb.getBufferByName(buffer) == null) {
            return;
        }
        mainPagerAdapter.openBuffer(buffer);
    }

    private SocketToggleConnection taskToggleConnection;
    private HotlistListAdapter hotlistListAdapter;
    private Menu actionBarMenu;
    



    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        logger.debug("onNewIntent called");
        handleIntent();
    }
    // when we get an intent, do something with it
    private void handleIntent() {
        // Load a buffer if necessary
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            // Load the given buffer
            onBufferSelected(extras.getString("buffer"));
        } else {
            // Load the bufferlist
        }
    }


    @Override
    public void onConnect() {
        if (rsb != null && rsb.isConnected()) {
            // Create and update the hotlist
            hotlistListAdapter = new HotlistListAdapter(WeechatActivity.this, rsb);
        }

        // Make sure we update action bar menu after a connection change.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateMenuContext(actionBarMenu);
            }
        });
    }

    @Override
    public void onDisconnect() {
        // Make sure we update action bar menu after a connection change.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateMenuContext(actionBarMenu);
            }
        });
    }

    @Override
    public void onError(String errorMsg, Object extraData) {
        Log.d("WeechatActivity", "onError:" + errorMsg);
        Log.d("Cause: ", ((SSLException)extraData).getCause().toString());
        if (extraData instanceof SSLException) {
            SSLException e1 = (SSLException) extraData;
            if (e1.getCause() instanceof CertificateException) {
                CertificateException e2 = (CertificateException) e1.getCause();
                
                if (e2.getCause() instanceof CertPathValidatorException) {
                    CertPathValidatorException e = (CertPathValidatorException) e2.getCause();
                    CertPath cp = e.getCertPath();                    
                    
                    // Set the cert error on the backend
                    rsb.setCertificateError((X509Certificate) cp.getCertificates().get(0));
                    
                    // Start an activity to attempt establishing trust
                    Intent i = new Intent(this, SSLCertActivity.class);
                    startActivity(i);
                }
            }
        }
    }

    @Override
    // Handle the options when the user presses the Menu key
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_connection_state: {
            if (rsb != null) {
                taskToggleConnection = new SocketToggleConnection();
                taskToggleConnection.execute();
            }
            break;
        }
        case R.id.menu_preferences: {
            Intent i = new Intent(this, WeechatPreferencesActivity.class);
            startActivity(i);
            break;
        }
        case R.id.menu_close: {
            if (viewPager.getCurrentItem()>0)
                mainPagerAdapter.closeBuffer(mainPagerAdapter.getCurrentBuffer().getBufferName());
            break;
        }
        case R.id.menu_about: {
            Intent i = new Intent(this, WeechatAboutActivity.class);
            startActivity(i);
            break;
        }
        case R.id.menu_quit: {
            new SocketShutdownConnection().execute();
            break;
        }
        case R.id.menu_hotlist: {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.hotlist);
            builder.setAdapter(hotlistListAdapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int position) {
                    HotlistItem hotlistItem = hotlistListAdapter.getItem(position);
                    String name = hotlistItem.getFullName();
                    onBufferSelected(name);
                }
            });
            builder.create().show();
            break;
        }
        case R.id.menu_nicklist: {
            // No nicklist if they aren't looking at a buffer
            if (viewPager.getCurrentItem()>0) {
                break;
            }

            // TODO: check for null(should be covered by previous if statement
            String[] nicks = mainPagerAdapter.getCurrentBuffer().getNicklist();
            if (nicks == null) {
                break;
            }

            NickListAdapter nicklistAdapter = new NickListAdapter(WeechatActivity.this, nicks);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.nicklist_menu) + " (" + nicks.length + ")");
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



    /**
     * Replacement method for onPrepareOptionsMenu due to rsb might be null on the event of clicking
     * the menu button.
     * 
     * Hence our activity stores the menu references in onCreateOptionsMenu and we can update menu
     * items underway from events like onConnect.
     * 
     * @param menu
     *            actionBarMenu to update context on
     */
    public void updateMenuContext(Menu menu) {
        if (menu == null) {
            return;
        }

        // Swap the text from connect to disconnect depending on connection status
        MenuItem connectionStatus = menu.findItem(R.id.menu_connection_state);
        if (rsb != null && rsb.isConnected()) {
            connectionStatus.setTitle(R.string.disconnect);
        } else {
            connectionStatus.setTitle(R.string.connect);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getSupportMenuInflater();
        menuInflater.inflate(R.menu.menu_actionbar, menu);

        updateMenuContext(menu);

        // Can safely hold on to this according to docs
        // http://developer.android.com/reference/android/app/Activity.html#onCreateOptionsMenu(android.view.Menu)
        actionBarMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    public void closeBuffer(final String bufferName) {
        // In own thread to prevent things from breaking
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainPagerAdapter.closeBuffer(bufferName);                
            }
        });
        updateTitle();
    }

    private void updateTitle() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                BufferFragment bf = mainPagerAdapter.getCurrentBuffer();
                if (bf!=null) {
                    bf.updateTitle();
                } else {
                    setTitle(getString(R.string.app_version));
                }
            }
        });
    }

    private class SocketShutdownConnection extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            if (rsb != null) {
                rsb.shutdown();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            unbindService(mConnection);
            mBound = false;
            stopService(new Intent(WeechatActivity.this, RelayService.class));
            finish();
        }
    }

    /**
     * Used to toggle the connection
     */
    private class SocketToggleConnection extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            if (rsb.isConnected()) {
                rsb.shutdown();
            } else {
                return rsb.connect();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            supportInvalidateOptionsMenu();
        }
    }
    
    /**
     * Service connection management...
     */
    
    @Override
    protected void onStart() {
        super.onStart();
        // Bind to the Relay Service
        bindService(new Intent(this, RelayService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (taskToggleConnection != null
                && taskToggleConnection.getStatus() != AsyncTask.Status.FINISHED) {
            taskToggleConnection.cancel(true);
            taskToggleConnection = null;
        }

        if (mBound) {
            rsb.removeRelayConnectionHandler(WeechatActivity.this);
            unbindService(mConnection);
            mBound = false;
        }
    }

    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rsb = (RelayServiceBinder) service;
            rsb.addRelayConnectionHandler(WeechatActivity.this);

            mBound = true;
            // Check if the service is already connected to the weechat relay, and if so load it up
            if (rsb.isConnected()) {
                WeechatActivity.this.onConnect();
            }

            handleIntent();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            rsb = null;
        }
    };

    
}

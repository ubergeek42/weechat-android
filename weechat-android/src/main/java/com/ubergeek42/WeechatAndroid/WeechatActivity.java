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
import java.util.EnumSet;
import java.util.HashSet;

import javax.net.ssl.SSLException;

import android.support.v4.app.DialogFragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;

import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.ubergeek42.WeechatAndroid.adapters.MainPagerAdapter;
import com.ubergeek42.WeechatAndroid.adapters.NickListAdapter;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.service.Buffer;
import com.ubergeek42.WeechatAndroid.service.BufferList;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.utils.MyMenuItemStuffListener;
import com.ubergeek42.WeechatAndroid.utils.ToolbarController;
import com.ubergeek42.WeechatAndroid.utils.UntrustedCertificateDialog;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.weechat.relay.connection.Connection;

import static com.ubergeek42.WeechatAndroid.service.Events.*;
import static com.ubergeek42.weechat.relay.connection.Connection.STATE.*;

import de.greenrobot.event.EventBus;

public class WeechatActivity extends AppCompatActivity implements
        CutePagerTitleStrip.CutePageChangeListener {

    private static Logger logger = LoggerFactory.getLogger("WA");
    final private static boolean DEBUG = BuildConfig.DEBUG;
    final private static boolean DEBUG_OPTIONS_MENU = false;
    final private static boolean DEBUG_LIFECYCLE = false;
    final private static boolean DEBUG_CONNECION = false;
    final private static boolean DEBUG_INTENT = false;
    final private static boolean DEBUG_BUFFERS = false;
    final private static boolean DEBUG_DRAWER = false;

    private Menu uiMenu;
    private ViewPager uiPager;
    private MainPagerAdapter adapter;
    private InputMethodManager imm;
    private CutePagerTitleStrip uiStrip;
    
    private boolean slidy;
    private boolean drawerEnabled = true;
    private boolean drawerShowing = false;
    private DrawerLayout uiDrawerLayout = null;
    private View uiDrawer = null;
    private ActionBarDrawerToggle drawerToggle = null;
    private @NonNull ImageView uiInfo;

    public ToolbarController toolbarController;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// life cycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        if (DEBUG_LIFECYCLE) logger.debug("onCreate({})", savedInstanceState);
        super.onCreate(savedInstanceState);

        // start background service (if necessary)
        startService(new Intent(this, RelayService.class));

        // load layout
        setContentView(R.layout.main_screen);

        // remove window color so that we get low overdraw
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // prepare pager
        FragmentManager manager = getSupportFragmentManager();
        uiPager = (ViewPager) findViewById(R.id.main_viewpager);
        adapter = new MainPagerAdapter(this, manager, uiPager);
        uiPager.setAdapter(adapter);

        // prepare action bar
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        final ActionBar uiActionBar = getSupportActionBar();
        uiActionBar.setHomeButtonEnabled(true);
        uiActionBar.setDisplayShowCustomEnabled(true);
        uiActionBar.setDisplayShowTitleEnabled(false);
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        uiStrip = (CutePagerTitleStrip) inflater.inflate(R.layout.cute_pager_title_strip_layout, null);
        uiStrip.setViewPager(uiPager);
        uiStrip.setOnPageChangeListener(this);
        uiActionBar.setCustomView(uiStrip);

        // this is the text view behind the uiPager
        // it says stuff like 'connecting', 'disconnected' et al
        uiInfo = (ImageView) findViewById(R.id.kitty);
        uiInfo.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (state.contains(CONNECTING) || state.contains(CONNECTED)) disconnect();
                else connect();
            }
        });

        // if this is true, we've got notification drawer and have to deal with it
        // setup drawer toggle, which calls drawerVisibilityChanged()
        slidy = getResources().getBoolean(R.bool.slidy);
        if (slidy) {
            uiDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
            uiDrawer = findViewById(R.id.bufferlist_fragment);
            drawerToggle = new ActionBarDrawerToggle(this, uiDrawerLayout,
                    R.string.open_drawer, R.string.close_drawer) {

                @SuppressWarnings("SimplifiableConditionalExpression")
                @Override
                public void onDrawerStateChanged(int newState) {
                    super.onDrawerStateChanged(newState);
                    boolean showing = (newState == DrawerLayout.STATE_IDLE) ?
                            uiDrawerLayout.isDrawerVisible(uiDrawer) : true;
                    if (drawerShowing != showing)
                        drawerVisibilityChanged(showing);
                }
            };
            drawerShowing = uiDrawerLayout.isDrawerVisible(uiDrawer);
            uiDrawerLayout.setDrawerListener(drawerToggle);
            uiActionBar.setDisplayHomeAsUpEnabled(true);
        }

        toolbarController = new ToolbarController((Toolbar) findViewById(R.id.toolbar), uiActionBar);

        final View layout = slidy ? findViewById(R.id.drawer_layout) : findViewById(R.id.not_drawer_layout);
        final View root = ((ViewGroup) layout).getChildAt(0);
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                // if more than 300 pixels, its probably a keyboard...
                int heightDiff = root.getRootView().getHeight() - root.getHeight();
                if (heightDiff > 300)
                    toolbarController.onSoftwareKeyboardStateChanged(true);
                else if (heightDiff < 300)
                    toolbarController.onSoftwareKeyboardStateChanged(false);
            }
        });

        // TODO Read preferences from background, its IO, 31ms strict mode!
        //PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        String title = "WA v" + BuildConfig.VERSION_NAME;
        setTitle(title);
        uiStrip.setEmptyText(title);
        updateCutePagerTitleStrip();
        adjustUI(); // TODO use elsewhere?
    }

    public void connect() {
        Intent i = new Intent(this, RelayService.class);
        i.setAction(RelayService.ACTION_START);
        startService(i);
    }

    public void disconnect() {
        Intent i = new Intent(this, RelayService.class);
        i.setAction(RelayService.ACTION_STOP);
        startService(i);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override protected void onStart() {
        if (DEBUG_LIFECYCLE) logger.debug("onStart()");
        super.onStart();
        EventBus.getDefault().registerSticky(this);
    }

    @Override protected void onStop() {
        if (DEBUG_LIFECYCLE) logger.debug("onStop()");
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onDestroy() {
        if (DEBUG_LIFECYCLE) logger.debug("onDestroy()");
        super.onDestroy();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// these two are necessary for the drawer

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (slidy) drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (slidy) drawerToggle.onConfigurationChanged(newConfig);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// S E R V I C E

    synchronized public void onServiceConnected(ComponentName name, IBinder service) {
        if (DEBUG_LIFECYCLE) logger.debug("onServiceConnected()");

        // open buffer that MIGHT be open in the service
        for (String fullName : BufferList.syncedBuffersFullNames)
            openBufferSilently(fullName);

        //  adjustUI();

        // if we have intent, handle it
        if (getIntent().hasExtra(EXTRA_NAME))
            openBufferFromIntent();

        updateHotCount(BufferList.getHotCount());
    }

    private void adjustUI() {
        int image = R.drawable.ic_big_connecting;
        if (state.contains(UNKNOWN) || state.contains(DISCONNECTED)) image = R.drawable.ic_big_disconnected;
        else if (state.contains(AUTHENTICATED)) image = R.drawable.ic_big_connected;

        setInfoImage(image);

        // enable/disable drawer
        if (slidy) {
            if (state.contains(BUFFERS_LISTED)) enableDrawer();
            else disableDrawer();
        }

        // adjust menu
        makeMenuReflectConnectionStatus();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// RelayConnectionHandler

    private EnumSet<Connection.STATE> state = EnumSet.of(Connection.STATE.UNKNOWN);

    @SuppressWarnings("unused")
    public void onEvent(StateChangedEvent event) {
        logger.debug("onEvent({})", event);
        this.state = event.state;
        adjustUI();
        if (event.state.contains(Connection.STATE.BUFFERS_LISTED)) {
            if (slidy) showDrawerIfPagerIsEmpty();
        }
    }

    @SuppressWarnings("unused")
    public void onEvent(final ExceptionEvent event) {
        if (DEBUG_CONNECION) logger.debug("onEvent({})", event);
        final Exception e = event.e;
        if (e instanceof SSLException) {
            SSLException e1 = (SSLException) e;
            if (e1.getCause() instanceof CertificateException) {
                CertificateException e2 = (CertificateException) e1.getCause();
                if (e2.getCause() instanceof CertPathValidatorException) {
                    CertPathValidatorException e3 = (CertPathValidatorException) e2.getCause();
                    CertPath cp = e3.getCertPath();

                    final X509Certificate certificate = (X509Certificate) cp.getCertificates().get(0);
                    DialogFragment f = UntrustedCertificateDialog.newInstance(certificate);
                    f.show(getSupportFragmentManager(), "boo");
                    disconnect();
                    return;
                }
            }
        }
        final String msg = "Error: " + (TextUtils.isEmpty(e.getMessage()) ? e.getClass().getSimpleName() : e.getMessage());
        runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// OnPageChangeListener
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public void onPageScrollStateChanged(int state) {}
    @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

    @Override public void onPageSelected(int position) {
        hideSoftwareKeyboard();
        toolbarController.onPageChangedOrSelected();
    }

    @Override public void onChange() {
        updateMenuItems();
        hideSoftwareKeyboard();
        toolbarController.onPageChangedOrSelected();
        findViewById(R.id.kitty).setVisibility(
                (uiPager.getAdapter().getCount() == 0) ? View.VISIBLE : View.GONE);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// MENU
    ////////////////////////////////////////////////////////////////////////////////////////////////

    volatile private int hotNumber = 0;
    private @Nullable TextView uiHot = null;

    /** update hot count (that red square over the bell icon) at any time
     ** also sets "hotNumber" in case menu has to be recreated
     ** can be called off the main thread */
    public void updateHotCount(final int newHotNumber) {
        if (DEBUG_OPTIONS_MENU) logger.debug("updateHotCount(), hot: {} -> {}", hotNumber, newHotNumber);
        hotNumber = newHotNumber;
        if (uiHot != null)
            uiHot.post(new Runnable() {
                @Override public void run() {
                    if (newHotNumber == 0)
                        uiHot.setVisibility(View.INVISIBLE);
                    else {
                        uiHot.setVisibility(View.VISIBLE);
                        uiHot.setText(Integer.toString(newHotNumber));
                    }
                }
            });
    }

    /** hide or show nicklist/close menu item according to buffer
     ** MUST be called on main thread */
    private void updateMenuItems() {
        if (uiMenu == null) return;
        boolean bufferVisible = adapter.getCount() > 0;
        uiMenu.findItem(R.id.menu_nicklist).setVisible(bufferVisible);
        uiMenu.findItem(R.id.menu_close).setVisible(bufferVisible);
    }

    /** Can safely hold on to this according to docs
     ** http://developer.android.com/reference/android/app/Activity.html#onCreateOptionsMenu(android.view.Menu) **/
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (DEBUG_OPTIONS_MENU) logger.debug("onCreateOptionsMenu(...)");
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_actionbar, menu);
        final View menuHotlist = MenuItemCompat.getActionView(menu.findItem(R.id.menu_hotlist));
        uiHot = (TextView) menuHotlist.findViewById(R.id.hotlist_hot);
        updateHotCount(hotNumber);
        new MyMenuItemStuffListener(menuHotlist, "Show hot message") {
            @Override
            public void onClick(View v) {
                onHotlistSelected();
            }
        };
        this.uiMenu = menu;
        updateMenuItems();
        makeMenuReflectConnectionStatus();
        return super.onCreateOptionsMenu(menu);
    }

    /** handle the options when the user presses the menu button */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (DEBUG_OPTIONS_MENU) logger.debug("onOptionsItemSelected({})", item);
        switch (item.getItemId()) {
            case android.R.id.home: {
                if (slidy && drawerEnabled) {
                    if (drawerShowing) hideDrawer();
                    else showDrawer();
                }
                break;
            }
            case R.id.menu_connection_state: {
                //if (relay != null) {
                    if (state.contains(CONNECTING) || state.contains(CONNECTED)) disconnect();
                    else connect();
                //}
                break;
            }
            case R.id.menu_preferences: {
                Intent intent = new Intent(this, PreferencesActivity.class);
                startActivity(intent);
                break;
            }
            case R.id.menu_close: {
                BufferFragment current = adapter.getCurrentBufferFragment();
                if (current != null)
                    current.onBufferClosed();
                break;
            }
            case R.id.menu_quit: {
                disconnect();
                stopService(new Intent(this, RelayService.class));
                finish();
                break;
            }
            case R.id.menu_hotlist:
                break;
            case R.id.menu_nicklist:
                Buffer buffer = BufferList.findByFullName(adapter.getCurrentBufferFullName());
                if (buffer == null) break;

                NickListAdapter nicklistAdapter = new NickListAdapter(WeechatActivity.this, buffer);

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setAdapter(nicklistAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int position) {
                        // TODO define something to happen here
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.setTitle("squirrels are awesome");
                dialog.setOnShowListener(nicklistAdapter);
                dialog.setOnDismissListener(nicklistAdapter);
                dialog.show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onHotlistSelected() {
        if (DEBUG_OPTIONS_MENU) logger.debug("onHotlistSelected()");
        Buffer buffer = BufferList.getHotBuffer();
        if (buffer != null)
            openBuffer(buffer.fullName);
        else
            Toast.makeText(this, getString(R.string.no_hot_buffers), Toast.LENGTH_SHORT).show();
    }

    /** change first menu item from connect to disconnect or back depending on connection status */
    private void makeMenuReflectConnectionStatus() {
        if (DEBUG_OPTIONS_MENU) logger.debug("makeMenuReflectConnectionStatus()");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (WeechatActivity.this.uiMenu != null) {
                    MenuItem connectionStatus = WeechatActivity.this.uiMenu.findItem(R.id.menu_connection_state);
                    String msg;

                    if (state.contains(AUTHENTICATED)) msg = "Disconnect";
                    else if (state.contains(CONNECTING) || state.contains(CONNECTED)) msg = "Stop connecting";
                    else msg = "Connect";
                    connectionStatus.setTitle(msg);

                    final View menuHotlist = MenuItemCompat.getActionView(uiMenu.findItem(R.id.menu_hotlist));
                    ImageView bellImage = (ImageView) menuHotlist.findViewById(R.id.hotlist_bell);
                    bellImage.setImageResource(BufferList.OPTIMIZE_TRAFFIC ? R.drawable.ic_bell_cracked : R.drawable.ic_bell);
                }
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// MISC
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** open a buffer WITHOUT hiding the drawer and checking if we are connected */
    public void openBufferSilently(@NonNull String fullName) {
        if (DEBUG_BUFFERS) logger.debug("openBufferSilently({})", fullName);
        adapter.openBuffer(fullName, false);
    }

    public void openBuffer(@NonNull String fullName) {
        if (DEBUG_BUFFERS) logger.debug("openBuffer({})", fullName);
        if (adapter.isBufferOpen(fullName) || state.contains(AUTHENTICATED)) {
            adapter.openBuffer(fullName, true);
            if (slidy) hideDrawer();
        } else {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
        }
    }

    // In own thread to prevent things from breaking
    public void closeBuffer(String fullName) {
        if (DEBUG_BUFFERS) logger.debug("closeBuffer({})", fullName);
        adapter.closeBuffer(fullName);
        if (slidy) showDrawerIfPagerIsEmpty();
    }

    /** hides the software keyboard, if any */
    public void hideSoftwareKeyboard() {
        imm.hideSoftInputFromWindow(uiPager.getWindowToken(), 0);
    }

    @Override
    public void onBackPressed() {
        if (DEBUG_LIFECYCLE) logger.debug("onBackPressed()");
        if (slidy && drawerShowing) hideDrawer();
        else super.onBackPressed();
    }

    /** called if the text of one of the buffers has been changed
     ** and the uiStrip doesn't update itself because there's no scrolling */
    public void updateCutePagerTitleStrip() {
        uiStrip.updateText();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// drawer stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void drawerVisibilityChanged(boolean showing) {
        if (DEBUG_DRAWER) logger.debug("drawerVisibilityChanged({})", showing);
        drawerShowing = showing;
        hideSoftwareKeyboard();
        BufferFragment current = adapter.getCurrentBufferFragment();
        if (current != null)
            current.maybeChangeVisibilityState();
    }

    public boolean isPagerNoticeablyObscured() {
        return drawerShowing;                          //todo?
    }

    public void enableDrawer() {
        if (DEBUG_DRAWER) logger.debug("enableDrawer()");
        drawerEnabled = true;
        uiPager.post(new Runnable() {
            @Override
            public void run() {
                uiDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            }
        });
    }

    public void disableDrawer() {
        if (DEBUG_DRAWER) logger.debug("disableDrawer()");
        drawerEnabled = false;
        uiPager.post(new Runnable() {
            @Override
            public void run() {
                uiDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        });
    }

    public void showDrawer() {
        if (DEBUG_DRAWER) logger.debug("showDrawer()");
        if (!drawerShowing) drawerVisibilityChanged(true); // we need this so that drawerShowing is set immediately
        uiPager.post(new Runnable() {
            @Override
            public void run() {
                uiDrawerLayout.openDrawer(uiDrawer);
            }
        });
    }

    public void hideDrawer() {
        if (DEBUG_DRAWER) logger.debug("hideDrawer()");
        uiPager.post(new Runnable() {
            @Override
            public void run() {
                uiDrawerLayout.closeDrawer(uiDrawer);
            }
        });
    }

    /** pop up drawer if connected & no pages in the adapter **/
    public void showDrawerIfPagerIsEmpty() {
        if (DEBUG_DRAWER) logger.debug("showDrawerIfPagerIsEmpty()");
        if (!drawerShowing)
            uiPager.post(new Runnable() {
                @Override public void run() {
                    if (state.contains(BUFFERS_LISTED) && adapter.getCount() == 0)
                        showDrawer();
                }
            });
    }

    /** set image that appears in the pager when no pages are open */
    private void setInfoImage(final int id) {
        final Drawable drawable = getResources().getDrawable(id);
        uiInfo.post(new Runnable() {
            @Override
            public void run() {
                Utils.setImageDrawableWithFade(uiInfo, drawable, 350);
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// intent
    ////////////////////////////////////////////////////////////////////////////////////////////////

    //private String intent_buffer_full_name = null;
    private final String EXTRA_NAME = "full_name";

    /** we may get intent while we are connected to the service and when we are not.
     ** empty (but present) fullName means open the drawer (in case we have highlights
     ** on multiple buffers */
    @Override protected void onNewIntent(Intent intent) {
        if (DEBUG_INTENT) logger.debug("onNewIntent(...), fullName='{}'", intent.getStringExtra(EXTRA_NAME));
        super.onNewIntent(intent);
        if (intent.hasExtra(EXTRA_NAME)) {
            setIntent(intent);
            //if (relay != null) openBufferFromIntent();
            openBufferFromIntent();
        }
    }

    /** the extra must be non-null */
    private void openBufferFromIntent() {
        if (DEBUG_INTENT) logger.debug("openBufferFromIntent()");
        String name = getIntent().getStringExtra(EXTRA_NAME);
        if ("".equals(name)) {
            if (slidy) showDrawer();
        } else {
            openBuffer(name);
        }
        getIntent().removeExtra(EXTRA_NAME);
    }
}

// Copyright 2012 Keith Johnson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ubergeek42.WeechatAndroid;

import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.util.EnumSet;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;

import android.text.TextUtils;
import android.view.*;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.adapters.BufferListClickListener;
import com.ubergeek42.WeechatAndroid.adapters.MainPagerAdapter;
import com.ubergeek42.WeechatAndroid.adapters.NickListAdapter;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.relay.Hotlist;
import com.ubergeek42.WeechatAndroid.relay.Nick;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.SSLHandler;
import com.ubergeek42.WeechatAndroid.utils.Emojify;
import com.ubergeek42.WeechatAndroid.utils.InvalidHostnameDialog;
import com.ubergeek42.WeechatAndroid.utils.MyMenuItemStuffListener;
import com.ubergeek42.WeechatAndroid.utils.ToolbarController;
import com.ubergeek42.WeechatAndroid.utils.UntrustedCertificateDialog;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.WeechatAndroid.service.RelayService.STATE;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.CatD;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import static com.ubergeek42.WeechatAndroid.service.Events.*;
import static com.ubergeek42.WeechatAndroid.service.RelayService.STATE.*;
import static com.ubergeek42.WeechatAndroid.utils.Constants.*;

public class WeechatActivity extends AppCompatActivity implements
        CutePagerTitleStrip.CutePageChangeListener, BufferListClickListener {

    final private static @Root Kitty kitty = Kitty.make("WA");

    private Menu uiMenu;
    private ViewPager uiPager;
    private MainPagerAdapter adapter;
    private InputMethodManager imm;

    private boolean slidy;
    private boolean drawerEnabled = true;
    private boolean drawerShowing = false;
    private DrawerLayout uiDrawerLayout = null;
    private View uiDrawer = null;
    private ActionBarDrawerToggle drawerToggle = null;
    private ImageView uiInfo;

    public ToolbarController toolbarController;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// life cycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @Override @Cat public void onCreate(@Nullable Bundle savedInstanceState) {
        // after OOM kill and not going to restore anything? remove all fragments & open buffers
        if (!P.isServiceAlive() && !BufferList.hasData()) {
            P.openBuffers.clear();
            savedInstanceState = null;
        }

        super.onCreate(savedInstanceState);

        // load layout
        setContentView(R.layout.main_screen);

        // remove window color so that we get low overdraw
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // prepare pager
        FragmentManager manager = getSupportFragmentManager();
        uiPager = findViewById(R.id.main_viewpager);
        adapter = new MainPagerAdapter(manager, uiPager);
        uiPager.setAdapter(adapter);

        // prepare action bar
        setSupportActionBar(findViewById(R.id.toolbar));
        final ActionBar uiActionBar = getSupportActionBar();
        //noinspection ConstantConditions
        uiActionBar.setHomeButtonEnabled(true);
        uiActionBar.setDisplayShowCustomEnabled(true);
        uiActionBar.setDisplayShowTitleEnabled(false);

        CutePagerTitleStrip uiStrip = findViewById(R.id.cute_pager_title_strip);
        uiStrip.setViewPager(uiPager);
        uiStrip.setOnPageChangeListener(this);

        // this is the text view behind the uiPager
        // it says stuff like 'connecting', 'disconnected' et al
        uiInfo = findViewById(R.id.kitty);
        uiInfo.setOnClickListener(v -> {if (state.contains(STARTED)) disconnect(); else connect();});

        // if this is true, we've got notification drawer and have to deal with it
        // setup drawer toggle, which calls drawerVisibilityChanged()
        slidy = getResources().getBoolean(R.bool.slidy);
        uiDrawer = findViewById(R.id.bufferlist_fragment);
        if (slidy) {
            uiDrawerLayout = findViewById(R.id.drawer_layout);
            drawerToggle = new ActionBarDrawerToggle(this, uiDrawerLayout, R.string.open_drawer, R.string.close_drawer) {
                @Override public void onDrawerSlide(View drawerView, float slideOffset) {
                    drawerVisibilityChanged(slideOffset > 0);
                }
            };
            drawerShowing = uiDrawerLayout.isDrawerVisible(uiDrawer);
            uiDrawerLayout.addDrawerListener(drawerToggle);
            uiActionBar.setDisplayHomeAsUpEnabled(true);
        }

        toolbarController = new ToolbarController(this);

        imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        String title = "WA v" + BuildConfig.VERSION_NAME;
        setTitle(title);
        uiStrip.setEmptyText(title);

        if (P.isServiceAlive()) connect();

        P.calculateWeaselWidth();

        // restore buffers if we have data in the static
        // if no data and not going to connect, clear stuff
        // if no data and going to connect, let the LISTED event restore it all
        if (adapter.canRestoreBuffers()) adapter.restoreBuffers();
        Emojify.init(getApplicationContext());
    }

    @MainThread @CatD(linger=true) public void connect() {
        P.loadConnectionPreferences();
        int error = P.validateConnectionPreferences();
        if (error != 0) {
            Weechat.showLongToast(error);
            return;
        }

        kitty.debug("proceeding!");
        Intent i = new Intent(this, RelayService.class);
        i.setAction(RelayService.ACTION_START);
        startService(i);
    }

    @MainThread @CatD public void disconnect() {
        Intent i = new Intent(this, RelayService.class);
        i.setAction(RelayService.ACTION_STOP);
        startService(i);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean started = false;
    @MainThread @Override @CatD protected void onStart() {
        state = null;
        EventBus.getDefault().register(this);
        updateHotCount(Hotlist.getHotCount());
        if (getIntent().hasExtra(NOTIFICATION_EXTRA_BUFFER_FULL_NAME)) openBufferFromIntent();
        started = true;
        super.onStart();
    }

    @MainThread @Override @CatD protected void onStop() {
        started = false;
        EventBus.getDefault().unregister(this);
        P.saveStuff();
        super.onStop();
    }

    @MainThread @Override @CatD protected void onDestroy() {
        super.onDestroy();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// these two are necessary for the drawer

    @MainThread @Override protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (slidy) drawerToggle.syncState();
    }

    @MainThread @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (slidy) drawerToggle.onConfigurationChanged(newConfig);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// the joy
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @Cat private void adjustUI() {
        final int image;
        if (state.contains(STOPPED)) image = R.drawable.ic_big_disconnected;
        else if (state.contains(AUTHENTICATED)) image = R.drawable.ic_big_connected;
        else image = R.drawable.ic_big_connecting;
        setInfoImage(image);
        setDrawerEnabled(state.contains(LISTED));
        makeMenuReflectConnectionStatus();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// events?

    private EnumSet<STATE> state = null;

    @Subscribe(sticky=true, threadMode=ThreadMode.MAIN)
    @MainThread @Cat public void onEvent(StateChangedEvent event) {
        boolean init = state == null;
        state = event.state;
        adjustUI();
        if (state.contains(LISTED)) {
            if (adapter.canRestoreBuffers()) adapter.restoreBuffers();
            else if (!init && slidy) showDrawerIfPagerIsEmpty();
        }
    }

    // since api v 23, when certificate's hostname doesn't match the host, instead of `java.security
    // .cert.CertPathValidatorException: Trust anchor for certification path not found` a `javax.net
    // .ssl.SSLPeerUnverifiedException: Cannot verify hostname: wrong.host.badssl.com` is thrown.
    // as this method is doing network, it should be run on a worker thread. currently it's run on
    // the connection thread, which can get interrupted by doge, but this likely not a problem as
    // EventBus won't crash the application by default
    @Subscribe
    @WorkerThread @Cat public void onEvent(final ExceptionEvent event) {
        final Exception e = event.e;
        if ((e instanceof SSLPeerUnverifiedException) ||
                (e instanceof SSLException &&
                e.getCause() instanceof CertificateException &&
                e.getCause().getCause() instanceof CertPathValidatorException)) {

            SSLHandler.Result r = SSLHandler.checkHostname(P.host, P.port);
            if (r.certificate == null) return;
            DialogFragment fragment = r.verified ?
                    UntrustedCertificateDialog.newInstance(r.certificate) :
                    InvalidHostnameDialog.newInstance(r.certificate);

            fragment.show(getSupportFragmentManager(), "ssl-error");
            Weechat.runOnMainThread(this::disconnect);
            return;
        }
        String message = TextUtils.isEmpty(e.getMessage()) ? e.getClass().getSimpleName() : e.getMessage();
        Weechat.showLongToast(R.string.error, message);
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
        PagerAdapter pa = uiPager.getAdapter();
        int visible = pa != null && pa.getCount() == 0 ? View.VISIBLE : View.GONE;
        findViewById(R.id.kitty_background).setVisibility(visible);
        findViewById(R.id.kitty).setVisibility(visible);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// MENU
    ////////////////////////////////////////////////////////////////////////////////////////////////

    volatile private int hotNumber = 0;
    private @Nullable TextView uiHot = null;

    // update hot count (that red square over the bell icon) at any time
    // also sets "hotNumber" in case menu has to be recreated
    @MainThread @Cat("Menu") public void updateHotCount(final int newHotNumber) {
        if (hotNumber == newHotNumber) return;
        hotNumber = newHotNumber;
        if (uiHot == null) return;
        if (newHotNumber == 0) {
            uiHot.setVisibility(View.INVISIBLE);
        } else {
            uiHot.setVisibility(View.VISIBLE);
            uiHot.setText(String.valueOf(newHotNumber));
        }
    }

    // hide or show nicklist/close menu item according to buffer
    @MainThread private void updateMenuItems() {
        if (uiMenu == null) return;
        boolean bufferVisible = adapter.getCount() > 0;
        uiMenu.findItem(R.id.menu_nicklist).setVisible(bufferVisible);
        uiMenu.findItem(R.id.menu_close).setVisible(bufferVisible);
        uiMenu.findItem(R.id.menu_filter_lines).setChecked(P.filterLines);
    }

    @Override @MainThread @Cat("Menu") public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_actionbar, menu);
        final View menuHotlist = menu.findItem(R.id.menu_hotlist).getActionView();
        uiHot = menuHotlist.findViewById(R.id.hotlist_hot);
        new MyMenuItemStuffListener(menuHotlist, getString(R.string.hint_show_hot_message)) {
            @Override public void onClick(View v) {onHotlistSelected();}
        };
        uiMenu = menu;
        updateMenuItems();
        makeMenuReflectConnectionStatus();
        updateHotCount(hotNumber);
        return super.onCreateOptionsMenu(menu);
    }

    @MainThread @Override @Cat("Menu") public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (slidy && drawerEnabled) {
                    if (drawerShowing) hideDrawer();
                    else showDrawer();
                }
                break;
            case R.id.menu_connection_state:
                if (state.contains(STARTED)) disconnect();
                else connect();
                break;
            case R.id.menu_preferences:
                Intent intent = new Intent(this, PreferencesActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_close:
                BufferFragment current = adapter.getCurrentBufferFragment();
                if (current != null)
                    current.onBufferClosed();
                break;
            case R.id.menu_hotlist:
                break;
            case R.id.menu_nicklist:
                final Buffer buffer = BufferList.findByFullName(adapter.getCurrentBufferFullName());
                if (buffer == null) break;

                final NickListAdapter nicklistAdapter = new NickListAdapter(WeechatActivity.this, buffer);

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setAdapter(nicklistAdapter, (dialogInterface, position) -> {
                    Nick nick = nicklistAdapter.getItem(position);
                    SendMessageEvent.fire("input 0x%x /query %s", buffer.pointer, nick.name);
                });
                AlertDialog dialog = builder.create();
                dialog.setTitle("squirrels are awesome");
                dialog.setOnShowListener(nicklistAdapter);
                dialog.setOnDismissListener(nicklistAdapter);
                dialog.show();
                break;
            case R.id.menu_filter_lines:
                final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
                final boolean filter = !P.filterLines;
                item.setChecked(filter);
                p.edit().putBoolean(PREF_FILTER_LINES, filter).apply();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @MainThread @Cat("Menu") private void onHotlistSelected() {
        Buffer buffer = BufferList.getHotBuffer();
        if (buffer != null)
            openBuffer(buffer.fullName);
        else
            Weechat.showShortToast(R.string.no_hot_buffers);
    }

    @MainThread @Cat("Menu") private void makeMenuReflectConnectionStatus() {
        if (uiMenu == null) return;
        MenuItem connectionStatus = uiMenu.findItem(R.id.menu_connection_state);
        String msg;

        if (state.contains(AUTHENTICATED)) msg = getString(R.string.disconnect);
        else if (state.contains(STARTED)) msg = getString(R.string.stop_connecting);
        else msg = getString(R.string.connect);
        connectionStatus.setTitle(msg);

        final View menuHotlist = uiMenu.findItem(R.id.menu_hotlist).getActionView();
        ImageView bellImage = menuHotlist.findViewById(R.id.hotlist_bell);
        bellImage.setImageResource(P.optimizeTraffic ? R.drawable.ic_bell_cracked : R.drawable.ic_bell);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// MISC
    ////////////////////////////////////////////////////////////////////////////////////////////////


    @MainThread @Override public void onBufferClick(String fullName) {
        openBuffer(fullName);
    }

    @MainThread public void openBuffer(@NonNull String fullName) {
        openBuffer(fullName, null);
    }

    @MainThread @Cat("Buffers") public void openBuffer(@NonNull final String fullName, @Nullable final String text) {
        if (adapter.isBufferOpen(fullName) || state.contains(AUTHENTICATED)) {
            adapter.openBuffer(fullName);
            adapter.focusBuffer(fullName);
            // post so that the fragment is created first, if it's not ready
            if (text != null) Weechat.runOnMainThread(() -> adapter.setBufferInputText(fullName, text));
            if (slidy) hideDrawer();
        } else {
            Weechat.showShortToast(R.string.not_connected);
        }
    }

    @MainThread @Cat("Buffers") public void closeBuffer(String fullName) {
        adapter.closeBuffer(fullName);
        if (slidy) showDrawerIfPagerIsEmpty();
    }

    @MainThread public void hideSoftwareKeyboard() {
        imm.hideSoftInputFromWindow(uiPager.getWindowToken(), 0);
    }

    @MainThread @Override public void onBackPressed() {
        if (slidy && drawerShowing) hideDrawer();
        else super.onBackPressed();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// drawer stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread public void drawerVisibilityChanged(boolean showing) {
        if (drawerShowing == showing) return;
        drawerShowing = showing;
        hideSoftwareKeyboard();
        BufferFragment current = adapter.getCurrentBufferFragment();
        if (current != null)
            current.onVisibilityStateChanged(BufferFragment.State.FULL_VISIBILITY);
    }

    @MainThread public boolean isPagerNoticeablyObscured() {
        return drawerShowing;
    }

    @MainThread @Cat("Drawer") private void setDrawerEnabled(final boolean enabled) {
        drawerEnabled = enabled;
        if (slidy) uiDrawerLayout.setDrawerLockMode(enabled ?
                DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        else uiDrawer.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    @MainThread @Cat("Drawer") public void showDrawer() {
        if (!drawerEnabled) return;
        if (!drawerShowing) drawerVisibilityChanged(true); // we need this so that drawerShowing is set immediately
        uiDrawerLayout.openDrawer(uiDrawer, started);
    }

    @MainThread @Cat("Drawer") public void hideDrawer() {
        uiDrawerLayout.closeDrawer(uiDrawer, started);
    }

    @MainThread @Cat("Drawer") public void showDrawerIfPagerIsEmpty() {
        if (!drawerShowing && state.contains(LISTED) && adapter.getCount() == 0) showDrawer();
    }

    // set the kitty image that appears when no pages are open
    @MainThread private void setInfoImage(final int id) {
        final Drawable drawable = getResources().getDrawable(id);
        Utils.setImageDrawableWithFade(uiInfo, drawable, 350);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// intent
    ////////////////////////////////////////////////////////////////////////////////////////////////


    // we may get intent while we are connected to the service and when we are not
    @MainThread @Override @Cat("Intent") protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(NOTIFICATION_EXTRA_BUFFER_FULL_NAME)) {
            setIntent(intent);
            if (started) openBufferFromIntent();
        }
    }

    // todo make this sane
    // if buffer name is "" (any), open that buffer or show drawer
    // else open buffer and set text
    @MainThread @Cat("Intent") private void openBufferFromIntent() {
        String name = getIntent().getStringExtra(NOTIFICATION_EXTRA_BUFFER_FULL_NAME);
        if (NOTIFICATION_EXTRA_BUFFER_FULL_NAME_ANY.equals(name)) {
            if (BufferList.getHotBufferCount() > 1) {
                if (slidy) showDrawer();
            } else {
                Buffer buffer = BufferList.getHotBuffer();
                if (buffer != null) openBuffer(buffer.fullName);
            }
        } else {
            String text = getIntent().getStringExtra(NOTIFICATION_EXTRA_BUFFER_INPUT_TEXT);
            openBuffer(name, text);
        }
        getIntent().removeExtra(NOTIFICATION_EXTRA_BUFFER_INPUT_TEXT);
        getIntent().removeExtra(NOTIFICATION_EXTRA_BUFFER_FULL_NAME);
    }
}

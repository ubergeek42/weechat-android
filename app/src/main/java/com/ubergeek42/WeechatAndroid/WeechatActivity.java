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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.ubergeek42.WeechatAndroid.adapters.BufferListClickListener;
import com.ubergeek42.WeechatAndroid.adapters.MainPagerAdapter;
import com.ubergeek42.WeechatAndroid.adapters.NickListAdapter;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.media.CachePersist;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.relay.Hotlist;
import com.ubergeek42.WeechatAndroid.relay.Nick;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayService.STATE;
import com.ubergeek42.WeechatAndroid.service.SSLHandler;
import com.ubergeek42.WeechatAndroid.upload.ShareObject;
import com.ubergeek42.WeechatAndroid.upload.UrisShareObject;
import com.ubergeek42.WeechatAndroid.upload.TextShareObject;
import com.ubergeek42.WeechatAndroid.upload.UploadService;
import com.ubergeek42.WeechatAndroid.utils.CertificateDialog;
import com.ubergeek42.WeechatAndroid.utils.FancyAlertDialogBuilder;
import com.ubergeek42.WeechatAndroid.utils.FriendlyExceptions;
import com.ubergeek42.WeechatAndroid.utils.Network;
import com.ubergeek42.WeechatAndroid.utils.SimpleTransitionDrawable;
import com.ubergeek42.WeechatAndroid.utils.ThemeFix;
import com.ubergeek42.WeechatAndroid.utils.ToolbarController;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.CatD;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;

import static com.ubergeek42.WeechatAndroid.media.Cache.findException;
import static com.ubergeek42.WeechatAndroid.service.Events.*;
import static com.ubergeek42.WeechatAndroid.service.RelayService.STATE.*;
import static com.ubergeek42.WeechatAndroid.upload.UploadServiceKt.ACTION_UPLOAD;
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

    @SuppressLint("WrongThread") @MainThread @Override @CatD
    public void onCreate(@Nullable Bundle savedInstanceState) {
        // after OOM kill and not going to restore anything? remove all fragments & open buffers
        if (!P.isServiceAlive() && !BufferList.hasData() && !P.openBuffers.isEmpty()) {
            P.openBuffers.clear();
            savedInstanceState = null;
        }

        super.onCreate(savedInstanceState);

        // load layout
        setContentView(R.layout.main_screen);

        // remove window color so that we get low overdraw
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // fix status bar and navigation bar icon color on Oreo.
        // TODO remove this once the bug has been fixed
        ThemeFix.fixLightStatusAndNavigationBar(this);

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
        uiActionBar.setDisplayHomeAsUpEnabled(false);

        CutePagerTitleStrip uiStrip = findViewById(R.id.cute_pager_title_strip);
        uiStrip.setViewPager(uiPager);
        uiStrip.setOnPageChangeListener(this);

        // this is the text view behind the uiPager
        // it says stuff like 'connecting', 'disconnected' et al
        uiInfo = findViewById(R.id.kitty);
        uiInfo.setImageDrawable(new SimpleTransitionDrawable());
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

        if (P.isServiceAlive()) connect();

        // restore buffers if we have data in the static
        // if no data and not going to connect, clear stuff
        // if no data and going to connect, let the LISTED event restore it all
        if (adapter.canRestoreBuffers()) adapter.restoreBuffers();

        P.applyThemeAfterActivityCreation(this);
        P.storeThemeOrColorSchemeColors(this);  // required for ThemeFix.fixIconAndColor()
        ThemeFix.fixIconAndColor(this);
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

    // a dirty but quick & safe hack that sets background color of the popup menu
    @Override public View onCreateView(View parent, String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        if (name.equals("androidx.appcompat.view.menu.ListMenuItemView") &&
                parent.getParent() instanceof FrameLayout) {
            Drawable drawable = ContextCompat.getDrawable(context, R.drawable.bg_popup_menu);
            if (drawable != null) {
                drawable.setColorFilter(0xff000000 | P.colorPrimary, PorterDuff.Mode.MULTIPLY);
                ((View) parent.getParent()).setBackground(drawable);
            }
        }
        return super.onCreateView(parent, name, context, attrs);
    }

    @MainThread @Override @CatD protected void onStart() {
        Network.get().register(this, null);     // no callback, simply make sure that network info is correct while we are showing
        P.calculateWeaselWidth();
        EventBus.getDefault().register(this);
        state = EventBus.getDefault().getStickyEvent(StateChangedEvent.class).state;
        updateHotCount(Hotlist.getHotCount());
        started = true;
        P.storeThemeOrColorSchemeColors(this);
        applyColorSchemeToViews();
        super.onStart();
        if (uiMenu != null) uiMenu.findItem(R.id.menu_dark_theme).setVisible(P.themeSwitchEnabled);
        if (getIntent().hasExtra(EXTRA_BUFFER_POINTER)) openBufferFromIntent();
        enableDisableExclusionRects();
    }

    @MainThread @Override @CatD protected void onStop() {
        started = false;
        EventBus.getDefault().unregister(this);
        P.saveStuff();
        super.onStop();
        Network.get().unregister(this);
        CachePersist.save();
    }

    @MainThread @Override @CatD protected void onDestroy() {
        toolbarController.detach();
        super.onDestroy();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// these two are necessary for the drawer

    @MainThread @Override protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (slidy) drawerToggle.syncState();
    }

    @MainThread @Override public void onConfigurationChanged(@NonNull Configuration newConfig) {
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

    @Subscribe(sticky=true, threadMode=ThreadMode.MAIN_ORDERED)
    @MainThread @Cat public void onEvent(StateChangedEvent event) {
        boolean init = state == event.state;
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

    // SSL WebSockets will generate the same errors, except in the case of a certificate with an
    // invalid host and a missing endpoint (e.g. wrong.host.badssl.com). as we are verifying the
    // socket after connecting, in this case the user will see a toast with “The status ... is not
    // '101 Switching Protocols'”. this error is also valid so we consider this a non-issue
    @Subscribe
    @WorkerThread @Cat public void onEvent(final ExceptionEvent event) {
        kitty.error("onEvent(ExceptionEvent)", event.e);
        final Exception e = event.e;
        if (findException(e, SSLPeerUnverifiedException.class) != null ||
                findException(e, CertificateException.class) != null) {
            SSLHandler.Result r = SSLHandler.checkHostnameAndValidity(P.host, P.port);
            if (r.certificateChain != null && r.certificateChain.length > 0) {
                DialogFragment fragment = null;
                if (r.exception instanceof CertificateExpiredException) {
                    fragment = CertificateDialog.buildExpiredCertificateDialog(this, r.certificateChain);
                } else if (r.exception instanceof CertificateNotYetValidException) {
                    fragment = CertificateDialog.buildNotYetValidCertificateDialog(this, r.certificateChain);
                } else if (r.exception instanceof SSLPeerUnverifiedException) {
                    fragment = CertificateDialog.buildInvalidHostnameCertificateDialog(this, r.certificateChain);
                } else if (r.exception == null) {
                    fragment = CertificateDialog.buildUntrustedOrNotPinnedCertificateDialog(this, r.certificateChain);
                }
                if (fragment != null) {
                    fragment.show(getSupportFragmentManager(), "ssl-error");
                    Weechat.runOnMainThread(this::disconnect);
                    return;
                }
            }
        }

        FriendlyExceptions.Result result = new FriendlyExceptions(this).getFriendlyException(e);
        if (result.message != null) Weechat.showLongToast(R.string.error, result.message);
        if (result.shouldStopConnecting) Weechat.runOnMainThread(this::disconnect);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// OnPageChangeListener
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Cat("Page") @Override public void onPageScrollStateChanged(int state) {}
    @Cat("Page") @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
    @Cat("Page") @Override public void onPageSelected(int position) {onChange();}

    // this method gets called repeatedly on various pager changes; make sure to only do stuff
    // when an actual change takes place
    private long currentBufferPointer = -1;
    @Cat("Page") @Override public void onChange() {
        long pointer = adapter.getCurrentBufferPointer();
        if (currentBufferPointer == pointer) return;
        boolean needToChangeKittyVisibility = currentBufferPointer == -1 || currentBufferPointer == 0 || pointer == 0;
        currentBufferPointer = pointer;

        updateMenuItems();
        hideSoftwareKeyboard();
        toolbarController.onPageChangedOrSelected();
        if (needToChangeKittyVisibility) {
            int visible = adapter.getCount() == 0 ? View.VISIBLE : View.GONE;
            uiPager.post(() -> findViewById(R.id.kitty_background).setVisibility(visible));
            findViewById(R.id.kitty).setVisibility(visible);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// MENU
    ////////////////////////////////////////////////////////////////////////////////////////////////

    volatile private int hotNumber = 0;
    private @Nullable TextView uiHot = null;

    // update hot count (that red square over the bell icon) at any time
    // also sets "hotNumber" in case menu has to be recreated
    @MainThread @Cat("Menu") public void updateHotCount(final int newHotNumber) {
        //if (hotNumber == newHotNumber) return;
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
        uiMenu.findItem(R.id.menu_dark_theme).setVisible(P.themeSwitchEnabled);
        uiMenu.findItem(R.id.menu_dark_theme).setChecked(P.darkThemeActive);
    }

    @Override @MainThread @Cat("Menu") public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_actionbar, menu);
        final View menuHotlist = menu.findItem(R.id.menu_hotlist).getActionView();
        uiHot = menuHotlist.findViewById(R.id.hotlist_hot);

        // set color of the border around the [2] badge on the bell, as well as text color
        GradientDrawable drawable = (GradientDrawable) uiHot.getBackground();
        drawable.setStroke((int) (P.darkThemeActive ? P._4dp / 2 : P._4dp / 2 - 1), P.colorPrimary);
        uiHot.setTextColor(P.darkThemeActive ? 0xffffffff : P.colorPrimary);

        TooltipCompat.setTooltipText(menuHotlist, getString(R.string.hint_show_hot_message));
        menuHotlist.setOnClickListener((View v) -> onHotlistSelected());
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
                final Buffer buffer = BufferList.findByPointer(adapter.getCurrentBufferPointer());
                if (buffer == null) break;

                final NickListAdapter nicklistAdapter = new NickListAdapter(WeechatActivity.this, buffer);

                AlertDialog.Builder builder = new FancyAlertDialogBuilder(this);
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
            case R.id.menu_dark_theme:
                item.setChecked(!P.darkThemeActive);
                PreferenceManager.getDefaultSharedPreferences(this).edit().putString(
                        PREF_THEME, P.darkThemeActive ? PREF_THEME_LIGHT : PREF_THEME_DARK).apply();
                break;
            case R.id.sync_hotlist:
                BufferList.syncHotlist();
                break;
            case R.id.die:
                System.exit(0);
                break;
            case R.id.upload:
                Intent i = new Intent(this, UploadService.class);
                i.setAction(ACTION_UPLOAD);
                i.setDataAndType(Uri.EMPTY, "poop/haha");
                startService(i);
                kitty.warn("ok");
                break;
        }
        return true;
    }

    @MainThread @Cat("Menu") private void onHotlistSelected() {
        Buffer buffer = BufferList.getHotBuffer();
        if (buffer != null)
            openBuffer(buffer.pointer);
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
        bellImage.setImageResource(P.optimizeTraffic ? R.drawable.ic_toolbar_bell_cracked : R.drawable.ic_toolbar_bell);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// MISC
    ////////////////////////////////////////////////////////////////////////////////////////////////


    @MainThread @Override public void onBufferClick(long pointer) {
        openBuffer(pointer);
    }

    @MainThread public void openBuffer(long pointer) {
        openBuffer(pointer, null);
    }

    @MainThread @Cat("Buffers") public void openBuffer(long pointer, @Nullable ShareObject shareObject) {
        if (adapter.isBufferOpen(pointer) || state.contains(AUTHENTICATED)) {
            adapter.openBuffer(pointer);
            adapter.focusBuffer(pointer);
            // post so that the fragment is created first, if it's not ready
            if (shareObject != null) Weechat.runOnMainThread(() -> adapter.setBufferInputText(pointer, shareObject));
            if (slidy) hideDrawer();
        } else {
            Weechat.showShortToast(R.string.not_connected);
        }
    }

    @MainThread @Cat("Buffers") public void closeBuffer(long pointer) {
        adapter.closeBuffer(pointer);
        if (slidy) showDrawerIfPagerIsEmpty();
    }

    @MainThread public void hideSoftwareKeyboard() {
        imm.hideSoftInputFromWindow(uiPager.getWindowToken(), 0);
    }

    @MainThread @Override public void onBackPressed() {
        if (slidy && drawerShowing) hideDrawer();
        else moveTaskToBack(true);
    }

    @MainThread public boolean isChatInputFocused() {
        View view = getCurrentFocus();
        return view != null && view.getId() == R.id.chatview_input;
    }

    // this gets called on *every* change to the whole buffer list including hotlist changes
    // todo only sort open buffers on major buffer list changes
    @MainThread public void onBuffersChanged() {
        adapter.sortOpenBuffers();
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
    int infoImageId = -1;
    @Cat @MainThread private void setInfoImage(final int id) {
        if (infoImageId == id) return;
        infoImageId = id;
        SimpleTransitionDrawable trans = (SimpleTransitionDrawable) uiInfo.getDrawable();
        trans.setTarget(AppCompatResources.getDrawable(this, id));
        trans.startTransition(350);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// intent
    ////////////////////////////////////////////////////////////////////////////////////////////////


    // we may get intent while we are connected to the service and when we are not
    @MainThread @Override @Cat("Intent") protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(EXTRA_BUFFER_POINTER)) {
            setIntent(intent);
            if (started) openBufferFromIntent();
        }
    }

    // when this is called, EXTRA_BUFFER_POINTER must be set
    @MainThread @Cat("Intent") private void openBufferFromIntent() {
        Intent intent = getIntent();

        long pointer = intent.getLongExtra(EXTRA_BUFFER_POINTER, NOTIFICATION_EXTRA_BUFFER_ANY);
        intent.removeExtra(EXTRA_BUFFER_POINTER);

        if (pointer == NOTIFICATION_EXTRA_BUFFER_ANY) {
            if (BufferList.getHotBufferCount() > 1) {
                if (slidy) showDrawer();
            } else {
                Buffer buffer = BufferList.getHotBuffer();
                if (buffer != null) openBuffer(buffer.pointer);
            }
        } else {
            @Nullable String action = intent.getAction();
            @Nullable String type = intent.getType();

            ShareObject shareObject = null;

            boolean sendOne = Intent.ACTION_SEND.equals(action);
            boolean sendMultiple = Intent.ACTION_SEND_MULTIPLE.equals(action);

            if (sendOne && "text/plain".equals(type)) {
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (text != null) shareObject = new TextShareObject(text);
            } else {
                List<Uri> uris = null;

                if (sendOne) {
                    Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (uri != null) uris = Collections.singletonList(uri);
                } else if (sendMultiple) {
                    uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                }

                if (!Utils.isEmpty(uris)) {
                    try {
                        shareObject = UrisShareObject.fromUris(uris);
                    } catch (Exception e) {
                        kitty.warn("Error while accessing uri", e);
                        FriendlyExceptions.Result result = new FriendlyExceptions(this).getFriendlyException(e);
                        if (result.message != null) Weechat.showLongToast(R.string.error, result.message);
                    }
                }
            }

            if (shareObject != null) openBuffer(pointer, shareObject);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // status bad can be colored since api 21 and have dark icons since api 23
    // navigation bar can be colored since api 21 and can have dark icons since api 26 via
    // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR, which the theming engine seems to be setting
    // automatically, and since api 27 via android:navigationBarColor
    private void applyColorSchemeToViews() {
        findViewById(R.id.kitty_background).setBackgroundColor(P.colorPrimary);
        findViewById(R.id.toolbar).setBackgroundColor(P.colorPrimary);

        boolean isLight = ThemeFix.isColorLight(P.colorPrimaryDark);
        if (!isLight || Build.VERSION.SDK_INT >= 23) getWindow().setStatusBarColor(P.colorPrimaryDark);
        if (!isLight || Build.VERSION.SDK_INT >= 26) getWindow().setNavigationBarColor(P.colorPrimaryDark);
    }

    @MainThread void enableDisableExclusionRects() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !slidy) return;
        uiPager.post(() -> {
            int pagerHeight = uiPager.getHeight();
            uiPager.setSystemGestureExclusionRects(
                    PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_USE_GESTURE_EXCLUSION_ZONE, PREF_USE_GESTURE_EXCLUSION_ZONE_D) ?
                            Collections.singletonList(new Rect(0, pagerHeight / 2, 200, pagerHeight)) :
                            Collections.emptyList());
        });
    }
}

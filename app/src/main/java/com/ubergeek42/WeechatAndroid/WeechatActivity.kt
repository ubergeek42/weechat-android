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
package com.ubergeek42.WeechatAndroid

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import androidx.preference.PreferenceManager
import com.ubergeek42.WeechatAndroid.CutePagerTitleStrip.CutePageChangeListener
import com.ubergeek42.WeechatAndroid.adapters.BufferListClickListener
import com.ubergeek42.WeechatAndroid.adapters.MainPagerAdapter
import com.ubergeek42.WeechatAndroid.databinding.WeaselBinding
import com.ubergeek42.WeechatAndroid.dialogs.CertificateDialog.buildExpiredCertificateDialog
import com.ubergeek42.WeechatAndroid.dialogs.CertificateDialog.buildInvalidHostnameCertificateDialog
import com.ubergeek42.WeechatAndroid.dialogs.CertificateDialog.buildNotYetValidCertificateDialog
import com.ubergeek42.WeechatAndroid.dialogs.CertificateDialog.buildUntrustedOrNotPinnedCertificateDialog
import com.ubergeek42.WeechatAndroid.dialogs.NicklistDialog
import com.ubergeek42.WeechatAndroid.dialogs.ScrollableDialog
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment
import com.ubergeek42.WeechatAndroid.fragments.BufferFragmentContainer
import com.ubergeek42.WeechatAndroid.media.CachePersist
import com.ubergeek42.WeechatAndroid.notifications.NotificationPermissionChecker
import com.ubergeek42.WeechatAndroid.notifications.shortcuts
import com.ubergeek42.WeechatAndroid.notifications.shouldRequestNotificationPermission
import com.ubergeek42.WeechatAndroid.notifications.showNotificationPermissionRationaleDialog
import com.ubergeek42.WeechatAndroid.notifications.statistics
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.service.Events.ExceptionEvent
import com.ubergeek42.WeechatAndroid.service.Events.StateChangedEvent
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.service.RelayService
import com.ubergeek42.WeechatAndroid.service.getSystemTrustedCertificateChain
import com.ubergeek42.WeechatAndroid.service.shouldRequestExactAlarmPermission
import com.ubergeek42.WeechatAndroid.service.showAlarmPermissionRationaleDialog
import com.ubergeek42.WeechatAndroid.upload.Config
import com.ubergeek42.WeechatAndroid.upload.InsertAt
import com.ubergeek42.WeechatAndroid.upload.ShareObject
import com.ubergeek42.WeechatAndroid.upload.TextShareObject
import com.ubergeek42.WeechatAndroid.upload.UploadDatabase
import com.ubergeek42.WeechatAndroid.upload.UrisShareObject.Companion.fromUris
import com.ubergeek42.WeechatAndroid.upload.chooseFiles
import com.ubergeek42.WeechatAndroid.upload.main
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.FriendlyExceptions
import com.ubergeek42.WeechatAndroid.utils.Network
import com.ubergeek42.WeechatAndroid.utils.SimpleTransitionDrawable
import com.ubergeek42.WeechatAndroid.utils.ThemeFix
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.WeechatAndroid.utils.findCause
import com.ubergeek42.WeechatAndroid.utils.isAnyOf
import com.ubergeek42.WeechatAndroid.utils.let
import com.ubergeek42.WeechatAndroid.utils.u
import com.ubergeek42.WeechatAndroid.utils.ulet
import com.ubergeek42.WeechatAndroid.utils.wasCausedBy
import com.ubergeek42.WeechatAndroid.utils.wasCausedByEither
import com.ubergeek42.WeechatAndroid.views.DrawerToggleFix
import com.ubergeek42.WeechatAndroid.views.ToolbarController
import com.ubergeek42.WeechatAndroid.views.WeechatActivityFullScreenController
import com.ubergeek42.WeechatAndroid.views.hideSoftwareKeyboard
import com.ubergeek42.WeechatAndroid.views.solidColor
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.CatD
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.ColorScheme
import com.ubergeek42.weechat.SslAxolotl
import com.ubergeek42.weechat.relay.connection.SSHServerKeyVerifier
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.util.EnumSet
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.system.exitProcess


class WeechatActivity : AppCompatActivity(), CutePageChangeListener,
        BufferListClickListener, BufferFragmentContainer {
    private var uiMenu: Menu? = null

    private lateinit var pagerAdapter: MainPagerAdapter

    private var slidy = false

    lateinit var ui: WeaselBinding
    lateinit var uiWeasel: View
    private lateinit var uiDrawerLayout: DrawerLayout
    private lateinit var uiDrawer: View
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private lateinit var menuBackgroundDrawable: Drawable

    @get:MainThread override var isPagerNoticeablyObscured = false
        private set

    private val toolbarController = ToolbarController(this).apply { observeLifecycle() }

    val notificationPermissionChecker = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> NotificationPermissionChecker(this)
        else -> null
    }

    init { WeechatActivityFullScreenController(this).observeLifecycle() }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////// life cycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @CatD public override fun onCreate(savedInstanceState: Bundle?) {
        // after OOM kill and not going to restore anything? remove all fragments & open buffers
        if (!P.isServiceAlive() && !BufferList.hasData() && P.openBuffers.isNotEmpty()) {
            P.openBuffers.clear()
            super.onCreate(null)
        } else {
            super.onCreate(savedInstanceState)
        }

        setContentView(R.layout.main_screen)
        ui = WeaselBinding.bind(findViewById(android.R.id.content))
        uiDrawer = findViewById(R.id.bufferlist_fragment)
        uiWeasel = findViewById(R.id.weasel)    // ui.weasel for some reason returns a wrong view

        setSupportActionBar(ui.toolbar)

        // fix status bar and navigation bar icon color on Oreo.
        // TODO remove this once the bug has been fixed
        ThemeFix.fixLightStatusAndNavigationBar(this)

        pagerAdapter = MainPagerAdapter(supportFragmentManager, ui.pager)
        ui.pager.adapter = pagerAdapter

        ui.cutePagerTitleStrip.run {
            setViewPager(ui.pager)
            setOnPageChangeListener(this@WeechatActivity)
        }

        supportActionBar?.run {
            setHomeButtonEnabled(true)
            setDisplayShowCustomEnabled(true)
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(false)
        }

        // this is the text view behind the uiPager
        // it says stuff like 'connecting', 'disconnected' et al
        ui.kitty.setImageDrawable(SimpleTransitionDrawable())
        ui.kitty.setOnClickListener {
            if (connectionState.isStarted) disconnect() else connect()
        }

        // if this is true, we've got navigation drawer and have to deal with it
        // setup drawer toggle, which calls drawerVisibilityChanged()
        slidy = resources.getBoolean(R.bool.slidy)

        if (slidy) {
            uiDrawerLayout = findViewById(R.id.main_screen_drawer_layout)
            uiDrawerLayout.setScrimColor(0x30000000)
            val drawerWidth = resources.getDimensionPixelSize(R.dimen.drawer_width)
            drawerToggle = object : DrawerToggleFix(this, uiDrawerLayout,
                    R.string.ui__ActionBarDrawerToggle__open_drawer,
                    R.string.ui__ActionBarDrawerToggle__close_drawer) {
                override fun onDrawerSlide(offset: Float) {
                    drawerVisibilityChanged(offset > 0)
                    uiWeasel.translationX = drawerWidth * offset * 0.8f
                }
            }
            drawerToggle.isDrawerSlideAnimationEnabled = false
            isPagerNoticeablyObscured = uiDrawerLayout.isDrawerVisible(uiDrawer)
            uiDrawerLayout.addDrawerListener(drawerToggle)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        menuBackgroundDrawable = ContextCompat.getDrawable(this, R.drawable.bg_popup_menu)!!

        if (savedInstanceState == null && P.isServiceAlive()) connect()

        // restore buffers if we have data in the static
        // if no data and not going to connect, clear stuff
        // if no data and going to connect, let the LISTED event restore it all
        if (pagerAdapter.canRestoreBuffers()) pagerAdapter.restoreBuffers()

        P.applyThemeAfterActivityCreation(this)
        P.storeThemeOrColorSchemeColors(this)   // required for ThemeFix.fixIconAndColor()
        ThemeFix.fixIconAndColor(this)
    }

    @MainThread @CatD(linger = true) fun connect() {
        P.loadConnectionPreferences()

        val errorStringId = P.validateConnectionPreferences()
        if (errorStringId != 0) {
            Toaster.ErrorToast.show(errorStringId)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && shouldRequestExactAlarmPermission()) {
            showAlarmPermissionRationaleDialog()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shouldRequestNotificationPermission()) {
            showNotificationPermissionRationaleDialog()
            return
        }

        kitty.debug("proceeding!")
        RelayService.startWithAction(this, RelayService.ACTION_START)
    }

    @MainThread @CatD fun disconnect() {
        RelayService.startWithAction(this, RelayService.ACTION_STOP)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var started = false

    // a dirty but quick & safe hack that sets background color of the popup menu
    override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
        if (name.endsWith(".menu.ListMenuItemView") && parent?.parent is FrameLayout) {
            (parent.parent as View).background = menuBackgroundDrawable.apply {
                setColorFilter(P.colorPrimary.solidColor, PorterDuff.Mode.MULTIPLY)
            }
        }
        return super.onCreateView(parent, name, context, attrs)
    }

    @MainThread @CatD override fun onStart() {
        Network.get().register(this, null)  // no callback, simply make sure that network info is correct while we are showing
        EventBus.getDefault().register(this)
        connectionState = EventBus.getDefault().getStickyEvent(StateChangedEvent::class.java).state
        started = true
        P.storeThemeOrColorSchemeColors(this)
        applyColorSchemeToViews()
        super.onStart()
        uiMenu?.findItem(R.id.menu_dark_theme)?.isVisible = P.themeSwitchEnabled
        if (intent.hasExtra(Constants.EXTRA_BUFFER_POINTER) ||
                intent.hasExtra(Constants.EXTRA_BUFFER_FULL_NAME)) openBufferFromIntent()
        enableDisableExclusionRects()
    }

    @MainThread @CatD override fun onStop() {
        started = false
        EventBus.getDefault().unregister(this)
        P.saveStuff()
        super.onStop()
        Network.get().unregister(this)
        CachePersist.save()
        UploadDatabase.save()
        statistics.save()
    }

    ///////////////////////////////////////////////////////// these two are necessary for the drawer

    @MainThread override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (slidy) drawerToggle.syncState()
    }

    @MainThread override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (slidy) drawerToggle.onConfigurationChanged(newConfig)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////// the joy
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @Cat private fun adjustUI() {
        setKittyImage(when {
            connectionState.isStopped -> R.drawable.ic_big_disconnected
            connectionState.isAuthenticated -> R.drawable.ic_big_connected
            else -> R.drawable.ic_big_connecting
        })
        makeMenuReflectConnectionStatus()
    }

    //////////////////////////////////////////////////////////////////////////////////////// events?

    private var connectionState: EnumSet<RelayService.STATE>? = null

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN_ORDERED)
    @MainThread @Cat fun onEvent(event: StateChangedEvent) {
        val init = connectionState === event.state
        connectionState = event.state
        adjustUI()
        if (connectionState.isListed) {
            if (pagerAdapter.canRestoreBuffers()) {
                pagerAdapter.restoreBuffers()
            } else if (!init && slidy) {
                showDrawerIfPagerIsEmpty()
            }
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
    @WorkerThread fun onEvent(event: ExceptionEvent) {
        kitty.error("onEvent(ExceptionEvent)", event.e)
        var fragmentMaker: (() -> DialogFragment)? = null

        if (event.e.wasCausedByEither<SSLPeerUnverifiedException, CertificateException>()) {
            val exceptionWrapper = event.e.findCause<SslAxolotl.ExceptionWrapper>()
            val lastServerOfferedCertificateChain = exceptionWrapper?.lastServerOfferedCertificateChain
            val lastAuthType = exceptionWrapper?.lastAuthType

            val (certificateChainTrustedBySystem, certificateChainForDisplay) =
                if (lastServerOfferedCertificateChain != null && lastAuthType != null) {
                    try {
                        true to getSystemTrustedCertificateChain(
                                lastServerOfferedCertificateChain, lastAuthType, P.host)
                    } catch (e: Exception) {
                        false to lastServerOfferedCertificateChain
                    }
                } else {
                    false to null
                }

            if (!certificateChainForDisplay.isNullOrEmpty()) {
                fragmentMaker = when {
                    event.e.wasCausedBy<CertificateExpiredException>() -> {
                        { buildExpiredCertificateDialog(this,
                                certificateChainForDisplay) }
                    }
                    event.e.wasCausedBy<CertificateNotYetValidException>() -> {
                        { buildNotYetValidCertificateDialog(this,
                                certificateChainForDisplay) }
                    }
                    event.e.wasCausedBy<SSLPeerUnverifiedException>() -> {
                        { buildInvalidHostnameCertificateDialog(this,
                                certificateChainForDisplay) }
                    }
                    event.e.wasCausedBy<CertPathValidatorException>() -> {
                        { buildUntrustedOrNotPinnedCertificateDialog(this,
                                certificateChainForDisplay,
                                certificateChainTrustedBySystem) }
                    }
                    else -> null
                }
            }
        }

        event.e.findCause<SSHServerKeyVerifier.VerifyException>()?.let { e ->
            fragmentMaker = when (e) {
                is SSHServerKeyVerifier.ServerNotKnownException -> {{ ScrollableDialog
                        .buildServerNotKnownDialog(this, e.server, e.identity) }}
                is SSHServerKeyVerifier.ServerNotVerifiedException -> {{ ScrollableDialog
                        .buildServerNotVerifiedDialog(this, e.server, e.identity) }}
                else -> null
            }
        }

        if (fragmentMaker != null) {
            main {
                fragmentMaker!!.invoke().show(supportFragmentManager, "ssl-or-ssh-error")
                disconnect()
            }
        } else {
            val friendlyException = FriendlyExceptions(this).getFriendlyException(event.e)
            Toaster.ErrorToast.show(R.string.error__etc__prefix, friendlyException.message)
            if (friendlyException.shouldStopConnecting) main { disconnect() }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////// OnPageChangeListener
    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onPageScrollStateChanged(state: Int) {}
    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
    override fun onPageSelected(position: Int) { onChange() }

    // this method gets called repeatedly on various pager changes; make sure to only do stuff
    // when an actual change takes place
    private var currentBufferPointer: Long = -1
    override fun onChange() {
        val pointer = pagerAdapter.currentBufferPointer
        if (currentBufferPointer == pointer) return
        val needToChangeKittyVisibility = currentBufferPointer.isAnyOf(0, -1) || pointer == 0L
        currentBufferPointer = pointer

        updateMenuItems()
        ui.pager.hideSoftwareKeyboard()
        toolbarController.onPageChangedOrSelected()
        if (needToChangeKittyVisibility) {
            ui.kitty.visibility = if (pagerAdapter.count == 0) View.VISIBLE else View.GONE
            applyMainBackgroundColor()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////// MENU
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Volatile private var hotNumber = 0
    private var uiHot: TextView? = null

    // update hot count (that red square over the bell icon) at any time
    // also sets "hotNumber" in case menu has to be recreated
    @MainThread @Cat("Menu") fun updateHotCount(newHotNumber: Int) {
        //if (hotNumber == newHotNumber) return;
        hotNumber = newHotNumber
        uiHot?.apply {
            visibility = if (newHotNumber != 0) View.VISIBLE else View.INVISIBLE
            if (newHotNumber != 0) text = newHotNumber.toString()
        }
    }

    // hide or show nicklist/close menu item according to buffer
    @MainThread override fun updateMenuItems() = ulet(uiMenu) { menu ->
        val bufferVisible = pagerAdapter.count > 0

        menu.run {
            findItem(R.id.menu_search).isVisible = bufferVisible
            findItem(R.id.menu_nicklist).isVisible = bufferVisible
            findItem(R.id.menu_close).isVisible = bufferVisible
            findItem(R.id.menu_filter_lines).isChecked = P.filterLines
            findItem(R.id.menu_dark_theme).isVisible = P.themeSwitchEnabled
            findItem(R.id.menu_dark_theme).isChecked = P.darkThemeActive
        }

        val showUpload1 = bufferVisible &&
                pagerAdapter.currentBufferFragment?.shouldShowUploadMenus() == true
        val showUpload2 = showUpload1 && Config.paperclipAction2 != null

        menu.findItem(R.id.menu_upload_1).run {
            isVisible = showUpload1
            if (showUpload1) setTitle(Config.paperclipAction1.menuItemResId)
        }

        menu.findItem(R.id.menu_upload_2).run {
            isVisible = showUpload2
            if (showUpload2) setTitle(Config.paperclipAction2!!.menuItemResId)
        }
    }

    @MainThread @Cat("Menu") override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_actionbar, menu)

        val menuHotlist = menu.findItem(R.id.menu_hotlist).actionView!!
        uiHot = menuHotlist.findViewById(R.id.hotlist_hot)

        // set color of the border around the [2] badge on the bell, as well as text color
        //GradientDrawable drawable = (GradientDrawable) uiHot.getBackground();
        //drawable.setStroke((int) (P.darkThemeActive ? P._4dp / 2 : P._4dp / 2 - 1), P.colorPrimary);
        uiHot?.setTextColor(if (P.darkThemeActive) 0xffffffff.u else P.colorPrimary)

        TooltipCompat.setTooltipText(menuHotlist, getString(R.string.menu__hotlist_hint))
        menuHotlist.setOnClickListener { onHotlistSelected() }
        uiMenu = menu
        updateMenuItems()
        makeMenuReflectConnectionStatus()
        // `onCreateOptionsMenu` is called *after* onStart, when `updateHotCount` was already called
        updateHotCount(hotNumber)
        return super.onCreateOptionsMenu(menu)
    }

    @MainThread @Cat("Menu") override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> if (slidy) {
                if (isPagerNoticeablyObscured) hideDrawer() else showDrawer()
            }
            R.id.menu_search -> {
                pagerAdapter.currentBufferFragment?.searchEnableDisable(enable = true, newSearch = true)
            }
            R.id.menu_connection_state -> {
                if (connectionState.isStarted) disconnect() else connect()
            }
            R.id.menu_preferences -> {
                startActivity(Intent(this, PreferencesActivity::class.java))
            }
            R.id.menu_close -> {
                pagerAdapter.currentBufferFragment?.onBufferClosed()
            }
            R.id.menu_hotlist -> {
                // see method below
            }
            R.id.menu_nicklist -> {
                NicklistDialog.show(this, pagerAdapter.currentBufferPointer)
            }
            R.id.menu_filter_lines -> {
                item.isChecked = !P.filterLines
                PreferenceManager.getDefaultSharedPreferences(this)
                        .edit()
                        .putBoolean(Constants.PREF_FILTER_LINES, item.isChecked)
                        .apply()
            }
            R.id.menu_dark_theme -> {
                item.isChecked = !P.darkThemeActive
                val value = if (item.isChecked) Constants.PREF_THEME_DARK else Constants.PREF_THEME_LIGHT
                PreferenceManager.getDefaultSharedPreferences(this)
                        .edit()
                        .putString(Constants.PREF_THEME, value)
                        .apply()
            }
            R.id.menu_upload_1, R.id.menu_upload_2 -> {
                val paperclipTarget = if (item.itemId == R.id.menu_upload_1)
                        Config.paperclipAction1 else Config.paperclipAction2
                let(pagerAdapter.currentBufferFragment, paperclipTarget) { fragment, target ->
                    chooseFiles(fragment, target)
                }
            }
            R.id.sync_hotlist -> BufferList.syncHotlist()
            R.id.remove_shortcuts -> shortcuts.removeAllShortcuts()
            R.id.die -> exitProcess(0)
        }
        return true
    }

    @MainThread @Cat("Menu") private fun onHotlistSelected() {
        val buffer = BufferList.getNextHotBuffer()
        if (buffer != null) {
            openBuffer(buffer.pointer)
        } else {
            Toaster.ShortToast.show(R.string.error__etc__no_hot_buffers)
        }
    }

    @MainThread @Cat("Menu") private fun makeMenuReflectConnectionStatus() = ulet(uiMenu) { menu ->
        val connectionStateTitle = getString(when {
            connectionState.isAuthenticated -> R.string.menu__connection_state__disconnect
            connectionState.isStarted -> R.string.menu__connection_state__stop_connecting
            else -> R.string.menu__connection_state__connect
        })
        menu.findItem(R.id.menu_connection_state).title = connectionStateTitle

        val menuHotlist = menu.findItem(R.id.menu_hotlist).actionView!!
        val bellImage = menuHotlist.findViewById<ImageView>(R.id.hotlist_bell)
        bellImage.setImageResource(if (P.optimizeTraffic) R.drawable.ic_toolbar_bell_cracked else R.drawable.ic_toolbar_bell)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////// MISC
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread override fun onBufferClick(pointer: Long) {
        openBuffer(pointer)
        BufferList.findByPointer(pointer)?.let { buffer ->
            statistics.reportBufferWasManuallyFocused(buffer.fullName)
        }
    }

    @MainThread @Cat("Buffers") fun openBuffer(pointer: Long, shareObject: ShareObject? = null) {
        pagerAdapter.openBuffer(pointer)
        pagerAdapter.focusBuffer(pointer)

        if (slidy) hideDrawer()

        if (shareObject != null) {
            val fragment = pagerAdapter.currentBufferFragment
            if (fragment != null && fragment.view != null) {
                fragment.setShareObject(shareObject, InsertAt.END)
            } else {
                // let fragment be created first, if it's not ready
                supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentLifecycleCallbacks() {
                    override fun onFragmentResumed(manager: FragmentManager, fragment: Fragment) {
                        if (fragment is BufferFragment && fragment.pointer == pointer) {
                            fragment.setShareObject(shareObject, InsertAt.END)
                            manager.unregisterFragmentLifecycleCallbacks(this)
                        }
                    }
                }, false)
            }
        }
    }

    @MainThread @Cat("Buffers") override fun closeBuffer(pointer: Long) {
        pagerAdapter.closeBuffer(pointer)
        if (slidy) showDrawerIfPagerIsEmpty()
    }

    override fun onChatLinesScrolled(dy: Int, onTop: Boolean, onBottom: Boolean) {
        toolbarController.onChatLinesScrolled(dy, onTop, onBottom)
    }

    @MainThread override fun onBackPressed() {
        if (currentFocus?.id == R.id.search_input) {
            pagerAdapter.currentBufferFragment?.searchEnableDisable(enable = false, newSearch = false)
        } else if (slidy && isPagerNoticeablyObscured) {
            hideDrawer()
        } else {
            moveTaskToBack(true)
        }
    }

    val isChatInputOrSearchInputFocused: Boolean
        @MainThread get() = currentFocus?.id.isAnyOf(R.id.chat_input, R.id.search_input)

    // this gets called on *every* change to the whole buffer list including hotlist changes
    // todo only sort open buffers on major buffer list changes
    @MainThread fun onBuffersChanged() {
        pagerAdapter.sortOpenBuffers()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////// drawer stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread fun drawerVisibilityChanged(showing: Boolean) {
        if (isPagerNoticeablyObscured == showing) return
        isPagerNoticeablyObscured = showing
        ui.pager.hideSoftwareKeyboard()
        pagerAdapter.currentBufferFragment?.onVisibilityStateChanged(BufferFragment.ChangedState.FullVisibility)
    }

    // call drawerVisibilityChanged() right away
    // as we need for isPagerNoticeablyObscured to be set immediately
    @MainThread @Cat("Drawer") fun showDrawer() {
        if (!isPagerNoticeablyObscured) drawerVisibilityChanged(true)
        uiDrawerLayout.openDrawer(uiDrawer, started)
    }

    @MainThread @Cat("Drawer") fun hideDrawer() {
        uiDrawerLayout.closeDrawer(uiDrawer, started)
    }

    @MainThread @Cat("Drawer") fun showDrawerIfPagerIsEmpty() {
        if (!isPagerNoticeablyObscured && pagerAdapter.count == 0) {
            showDrawer()
        }
    }

    fun isBufferListVisible() = !slidy || isPagerNoticeablyObscured

    // set the kitty image that appears when no pages are open
    private var kittyImageResourceId = -1
    @MainThread @Cat private fun setKittyImage(resourceId: Int) {
        if (kittyImageResourceId == resourceId) return
        kittyImageResourceId = resourceId
        val drawable = ui.kitty.drawable as SimpleTransitionDrawable
        drawable.setTarget(AppCompatResources.getDrawable(this, resourceId))
        drawable.startTransition(350)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////// intent
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // we may get intent while we are connected to the service and when we are not
    @MainThread @Cat("Intent") override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(Constants.EXTRA_BUFFER_POINTER) ||
                intent.hasExtra(Constants.EXTRA_BUFFER_FULL_NAME)) {
            setIntent(intent)
            if (started) openBufferFromIntent()
        }
    }

    // when this is called, EXTRA_BUFFER_POINTER must be set
    @MainThread @Cat("Intent") private fun openBufferFromIntent() {
        val intent = intent
        var pointer = intent.getLongExtra(Constants.EXTRA_BUFFER_POINTER,
                                          Constants.EXTRA_BUFFER_POINTER_ANY)

        intent.removeExtra(Constants.EXTRA_BUFFER_POINTER)

        if (pointer == Constants.EXTRA_BUFFER_POINTER_ANY) {
            val fullName = intent.getStringExtra(Constants.EXTRA_BUFFER_FULL_NAME)
            if (fullName != null && fullName != Constants.EXTRA_BUFFER_FULL_NAME_ANY) {
                val buffer = BufferList.findByFullName(fullName)
                if (buffer == null) {
                    Toaster.ErrorToast.show("Couldn’t find buffer $fullName")
                    intent.removeExtra(Constants.EXTRA_BUFFER_FULL_NAME)
                    return
                } else {
                    pointer = buffer.pointer
                }
            }
        }

        intent.removeExtra(Constants.EXTRA_BUFFER_FULL_NAME)

        if (pointer == Constants.EXTRA_BUFFER_POINTER_ANY) {
            if (BufferList.hotBufferCount > 1) {
                if (slidy) showDrawer()
            } else {
                BufferList.getNextHotBuffer()?.let {
                    openBuffer(it.pointer)
                    if (pointer != 0L) statistics.reportBufferWasManuallyFocused(it.fullName)
                }
            }
        } else {
            var shareObject: ShareObject? = null

            val sendOne = intent.action == Intent.ACTION_SEND
            val sendMultiple = intent.action == Intent.ACTION_SEND_MULTIPLE

            if (sendOne && "text/plain" == intent.type) {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { shareObject = TextShareObject(it) }
            } else {
                val uris = when {
                    sendOne -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { listOf(it) }
                    sendMultiple -> intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    else -> null
                }

                if (!uris.isNullOrEmpty()) {
                    try {
                        shareObject = fromUris(uris)
                    } catch (e: Exception) {
                        kitty.warn("Error while accessing uri", e)
                        Toaster.ErrorToast.show(e)
                    }
                }
            }

            openBuffer(pointer, shareObject)
            if (pointer != 0L) {
                BufferList.findByPointer(pointer)?.let { buffer ->
                    statistics.reportBufferWasManuallyFocused(buffer.fullName)
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // set window background color that is used by system when we can't draw
    // but don't draw it ourselves so that we get low overdraw
    private fun applyColorSchemeToViews() {
        window.setBackgroundDrawable(ColorDrawable(P.colorPrimary.solidColor))
        window.decorView.background = null
        applyMainBackgroundColor()
        ui.toolbarContainer.setBackgroundColor(P.colorPrimary)
    }

    // to reduce overdraw, change background color instead of drawing over it
    private fun applyMainBackgroundColor() {
        uiWeasel.setBackgroundColor(if (pagerAdapter.count == 0) {
            P.colorPrimary
        } else {
            ColorScheme.get().default_color[ColorScheme.OPT_BG].solidColor
        })
    }

    @MainThread fun enableDisableExclusionRects() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !slidy) return

        ui.pager.post {
            val pagerHeight = ui.pager.height
            ui.pager.systemGestureExclusionRects = if (P.useGestureExclusionZone) {
                listOf(Rect(0, pagerHeight / 2, 200, pagerHeight))
            } else {
                emptyList()
            }
        }
    }

    companion object {
        @Root private val kitty: Kitty = Kitty.make("WA")
    }
}


private inline val EnumSet<RelayService.STATE>?.isAuthenticated get () =
    this != null && contains(RelayService.STATE.AUTHENTICATED)
private inline val EnumSet<RelayService.STATE>?.isListed get () =
    this != null && contains(RelayService.STATE.LISTED)
private inline val EnumSet<RelayService.STATE>?.isStarted get () =
    this != null && contains(RelayService.STATE.STARTED)
private inline val EnumSet<RelayService.STATE>?.isStopped get () =
    this != null && size == 1 && contains(RelayService.STATE.STOPPED)

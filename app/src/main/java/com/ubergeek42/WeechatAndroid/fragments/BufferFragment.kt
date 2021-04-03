package com.ubergeek42.WeechatAndroid.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.transition.Fade
import android.transition.TransitionManager
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.view.MenuCompat
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.adapters.ChatLinesAdapter
import com.ubergeek42.WeechatAndroid.copypaste.Paste
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.BufferEye
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.Lines
import com.ubergeek42.WeechatAndroid.search.Search
import com.ubergeek42.WeechatAndroid.search.SearchConfig
import com.ubergeek42.WeechatAndroid.service.Events.SendMessageEvent
import com.ubergeek42.WeechatAndroid.service.Events.StateChangedEvent
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.service.RelayService
import com.ubergeek42.WeechatAndroid.tabcomplete.TabCompleter
import com.ubergeek42.WeechatAndroid.upload.Config
import com.ubergeek42.WeechatAndroid.upload.InsertAt
import com.ubergeek42.WeechatAndroid.upload.MediaAcceptingEditText
import com.ubergeek42.WeechatAndroid.upload.MediaAcceptingEditText.HasLayoutListener
import com.ubergeek42.WeechatAndroid.upload.ShareObject
import com.ubergeek42.WeechatAndroid.upload.Suri
import com.ubergeek42.WeechatAndroid.upload.Target
import com.ubergeek42.WeechatAndroid.upload.Upload.CancelledException
import com.ubergeek42.WeechatAndroid.upload.UploadManager
import com.ubergeek42.WeechatAndroid.upload.UploadObserver
import com.ubergeek42.WeechatAndroid.upload.WRITE_PERMISSION_REQUEST_FOR_CAMERA
import com.ubergeek42.WeechatAndroid.upload.chooseFiles
import com.ubergeek42.WeechatAndroid.upload.getShareObjectFromIntent
import com.ubergeek42.WeechatAndroid.upload.i
import com.ubergeek42.WeechatAndroid.upload.suppress
import com.ubergeek42.WeechatAndroid.upload.validateUploadConfig
import com.ubergeek42.WeechatAndroid.views.AnimatedRecyclerView
import com.ubergeek42.WeechatAndroid.utils.Assert.assertThat
import com.ubergeek42.WeechatAndroid.utils.FriendlyExceptions
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.WeechatAndroid.utils.Utils
import com.ubergeek42.WeechatAndroid.utils.afterTextChanged
import com.ubergeek42.WeechatAndroid.utils.indexOfOrElse
import com.ubergeek42.WeechatAndroid.utils.ulet
import com.ubergeek42.WeechatAndroid.views.BackGestureAwareEditText
import com.ubergeek42.WeechatAndroid.views.BufferFragmentFullScreenController
import com.ubergeek42.WeechatAndroid.views.CircleView
import com.ubergeek42.WeechatAndroid.views.CircularImageButton
import com.ubergeek42.WeechatAndroid.views.OnBackGestureListener
import com.ubergeek42.WeechatAndroid.views.OnJumpedUpWhileScrollingListener
import com.ubergeek42.WeechatAndroid.views.jumpThenSmoothScroll
import com.ubergeek42.WeechatAndroid.views.jumpThenSmoothScrollCentering
import com.ubergeek42.WeechatAndroid.views.scrollToPositionWithOffsetFix
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.CatD
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.ColorScheme
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import java.util.regex.PatternSyntaxException


private const val POINTER_KEY = "pointer"


class BufferFragment : Fragment(), BufferEye {
    @Root private val kitty: Kitty = Kitty.make("BF")

    private var pointer: Long = 0

    private var weechatActivity: WeechatActivity? = null
    private var buffer: Buffer? = null
    private var attachedToBuffer = false

    // todo make lateinit soon
    private var linesAdapter: ChatLinesAdapter? = null

    private var uiLines: AnimatedRecyclerView? = null
    private var uiInput: MediaAcceptingEditText? = null
    private var uiPaperclip: ImageButton? = null
    private var uiSend: ImageButton? = null
    private var uiTab: ImageButton? = null

    private var uploadLayout: ViewGroup? = null
    private var uploadProgressBar: ProgressBar? = null
    private var uploadButton: ImageButton? = null

    private var uiBottomBar: ViewGroup? = null
    private var connectivityIndicator: CircleView? = null
    private var fabScrollToBottom: CircularImageButton? = null

    init { BufferFragmentFullScreenController(this).observeLifecycle() }

    companion object {
        @JvmStatic fun newInstance(pointer: Long) =
                BufferFragment().apply {
                    arguments = Bundle().apply {
                        putLong(POINTER_KEY, pointer)
                    }
                }
    }

    @MainThread override fun setArguments(args: Bundle?) {
        super.setArguments(args)
        pointer = requireArguments().getLong(POINTER_KEY)
        BufferList.findByPointer(pointer)?.let {
            buffer = it
            kitty.setPrefix(it.shortName)
        } ?: kitty.setPrefix(Utils.pointerToString(pointer))
        uploadManager = UploadManager.forBuffer(pointer)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////// life cycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @Cat override fun onAttach(context: Context) {
        super.onAttach(context)
        weechatActivity = context as WeechatActivity
    }

    @MainThread @Cat override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedInstanceState?.let {
            restoreRecyclerViewState(it)
            restoreSearchState(it)
        }
    }

    @MainThread @Cat override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View? {
        val v = inflater.inflate(R.layout.chatview_main, container, false)

        uiLines = v.findViewById(R.id.chatview_lines)
        uiInput = v.findViewById(R.id.chatview_input)
        uiPaperclip = v.findViewById(R.id.chatview_paperclip)
        uiSend = v.findViewById(R.id.chatview_send)
        uiTab = v.findViewById(R.id.chatview_tab)

        uploadLayout = v.findViewById(R.id.upload_layout)
        uploadProgressBar = v.findViewById(R.id.upload_progress_bar)
        uploadButton = v.findViewById(R.id.upload_button)

        uiBottomBar = v.findViewById(R.id.bottom_bar)
        connectivityIndicator = v.findViewById(R.id.connectivity_indicator)
        fabScrollToBottom = v.findViewById(R.id.fab_scroll_to_bottom)

        uploadButton?.setOnClickListener {
            if (lastUploadStatus == UploadStatus.UPLOADING) {
                uploadManager?.filterUploads(emptyList())
            } else {
                startUploads(uiInput?.getNotReadySuris())
            }
        }

        linesAdapter = ChatLinesAdapter(uiLines!!).apply {
            this@BufferFragment.buffer?.let {
                buffer = it
                loadLinesSilently()
            }
        }

        uiLines?.run {
            adapter = linesAdapter
            isFocusable = false
            isFocusableInTouchMode = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy != 0) {
                        if (focusedInViewPager) {
                            weechatActivity?.toolbarController?.onChatLinesScrolled(dy, onTop, onBottom)
                        }
                        showHideFabWhenScrolled(dy, onBottom)
                    }
                }
            })
            onJumpedUpWhileScrollingListener = OnJumpedUpWhileScrollingListener {
                showHideFabWhenScrolled(-100000, onBottom = false)
            }
        }

        uiPaperclip?.run {
            setOnClickListener { chooseFiles(this@BufferFragment, Config.paperclipAction1) }
            setOnLongClickListener {
                Config.paperclipAction2?.let { action2 ->
                    chooseFiles(this@BufferFragment, action2)
                    true
                } ?: false
            }
        }

        uiSend?.setOnClickListener { sendMessageOrStartUpload() }
        uiTab?.setOnClickListener { tryTabComplete() }

        uiInput?.run {
            setOnKeyListener(uiInputHardwareKeyPressListener)
            setOnLongClickListener { Paste.showPasteDialog(uiInput) }
            afterTextChanged {
                cancelTabCompletionOnInputTextChange()
                fixupUploadsOnInputTextChange()
                showHidePaperclip()
            }
            setOnEditorActionListener { _: TextView, actionId: Int, _: KeyEvent? ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessageOrStartUpload()
                    true
                } else {
                    false
                }
            }
        }

        fabScrollToBottom?.setOnClickListener {
            uiLines?.jumpThenSmoothScroll(linesAdapter!!.itemCount - 1)
            focusedMatch = 0
            enableDisableSearchButtons()
            adjustSearchNumbers()
        }

        fabScrollToBottom?.setOnLongClickListener {
            uiLines?.jumpThenSmoothScroll(0)
            true
        }

        initSearchViews(v)
        uiLines?.post { applyRecyclerViewState() }

        connectedToRelay = true     // assume true, this will get corrected later
        return v
    }

    @MainThread @Cat override fun onDestroyView() {
        super.onDestroyView()
        uiLines = null
        uiInput = null
        uiSend = null
        uiTab = null
        uiPaperclip = null
        uploadLayout = null
        uploadButton = null
        uploadProgressBar = null
        linesAdapter = null
        uiBottomBar = null
        connectivityIndicator = null
        fabScrollToBottom = null

        destroySearchViews()
    }

    @MainThread @Cat override fun onResume() {
        super.onResume()
        uiTab?.visibility = if (P.showTab) View.VISIBLE else View.GONE
        EventBus.getDefault().register(this)
        applyColorSchemeToViews()
        adjustConnectivityIndications(false)
        uiInput?.textifyReadySuris()   // this will fix any uploads that were finished while we were absent
        fixupUploadsOnInputTextChange()             // this will set appropriate upload ui state
        showHidePaperclip()
        showHideFabAfterRecyclerViewRestored()
        uploadManager?.observer = uploadObserver   // this will resume ui for any uploads that are still running
    }

    @MainThread @Cat override fun onPause() {
        super.onPause()
        uploadManager?.observer = null
        lastUploadStatus = null         // setObserver & afterTextChanged2 will fix this
        detachFromBuffer()
        recordRecyclerViewState()
        EventBus.getDefault().unregister(this)
    }

    @MainThread @Cat override fun onDetach() {
        weechatActivity = null
        super.onDetach()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveRecyclerViewState(outState)
        saveSearchState(outState)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////// visibility state
    ////////////////////////////////////////////////////////////////////////////////////////////////

    enum class ChangedState { BufferAttachment, PagerFocus, FullVisibility, LinesListed }
    @MainThread @Cat(linger = true) fun onVisibilityStateChanged(changedState: ChangedState)
            = ulet(weechatActivity, buffer) { activity, buffer ->
        if (!buffer.linesAreReady()) return
        kitty.trace("proceeding!")

        val watched = attachedToBuffer && focusedInViewPager && !activity.isPagerNoticeablyObscured
        if (buffer.isWatched != watched) {
            if (watched) linesAdapter?.scrollToHotLineIfNeeded()
            buffer.setWatched(watched)
        }

        if (changedState == ChangedState.PagerFocus && !focusedInViewPager ||                                   // swiping left/right or
                changedState == ChangedState.BufferAttachment && !attachedToBuffer && focusedInViewPager) {     // minimizing app, closing buffer, disconnecting
            buffer.moveReadMarkerToEnd()
            if (changedState == ChangedState.PagerFocus) linesAdapter?.onLineAdded()                            // trigger redraw after moving marker to end
        }
    }

    private var focusedInViewPager = false

    // called by MainPagerAdapter
    // if true the page is the main in the adapter; called when sideways scrolling is complete
    @MainThread override fun setUserVisibleHint(focused: Boolean) {
        super.setUserVisibleHint(focused)
        this.focusedInViewPager = focused
        onVisibilityStateChanged(ChangedState.PagerFocus)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////// events
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // this is used instead of `buffer == null` as we need buffer while detachFromBuffer is running
    private var connectedToRelay = true

    // this can be forced to always run in background, but then it would run after onStart()
    // if the fragment hasn't been initialized yet, that would lead to a bit of flicker
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    @MainThread @Cat fun onEvent(event: StateChangedEvent) {
        val connectedToRelay = event.state.contains(RelayService.STATE.LISTED)
        val oldBuffer = buffer

        if (oldBuffer == null || connectedToRelay) {
            buffer = BufferList.findByPointer(pointer)
            if (connectedToRelay && buffer == null) {
                onBufferClosed()
                return
            }
        }

        if (buffer != null && (oldBuffer != buffer || !attachedToBuffer)) attachToBuffer()

        val connectivityChanged = this.connectedToRelay != connectedToRelay
        this.connectedToRelay = connectedToRelay

        adjustConnectivityIndications(connectivityChanged)
    }

    ////////////////////////////////////////////////////////////////////////////////// attach detach

    // when attaching to the buffer, there's a period when buffers are listed,
    // but lines for the current buffer have not yet arrived.
    // so we only set adapter's buffer if lines are ready for the current buffer.
    // if they're not, the adapter will be using the old buffer, if any, until onLinesListed is run.
    // todo make sure that this behaves fine on slow connections
    @MainThread @Cat private fun attachToBuffer() = ulet(buffer) { buffer ->
        buffer.setBufferEye(this)
        if (buffer.linesAreReady()) linesAdapter?.buffer = buffer
        linesAdapter?.loadLinesWithoutAnimation()
        attachedToBuffer = true
        onVisibilityStateChanged(ChangedState.BufferAttachment)
    }

    // buffer might be null if we are closing fragment that is not connected
    @MainThread @Cat private fun detachFromBuffer() {
        attachedToBuffer = false
        onVisibilityStateChanged(ChangedState.BufferAttachment)
        buffer?.setBufferEye(null)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////// ui
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread private fun adjustConnectivityIndications(animate: Boolean)
            = ulet(connectivityIndicator) { indicator ->
        val state = when {
            connectedToRelayAndSynced -> ConnectivityState.OnlineAndSynced
            connectedToRelay -> ConnectivityState.OnlineAndSyncing
            else -> ConnectivityState.Offline
        }

        uiSend?.isEnabled = state.sendEnabled

        indicator.setColor(state.badgeColor)

        val indicatorVisibility = if (state.displayBadge) View.VISIBLE else View.GONE
        if (indicator.visibility != indicatorVisibility) {
            if (animate) TransitionManager.beginDelayedTransition(
                    indicator.parent as ViewGroup, connectivityIndicatorTransition)
            indicator.visibility = indicatorVisibility
        }
    }

    private fun applyColorSchemeToViews() {
        fabScrollToBottom?.setBackgroundColor(P.colorPrimary)
        uiBottomBar?.setBackgroundColor(P.colorPrimary)
        searchMatchDecorationPaint.color = ColorScheme.get().searchMatchBackground
    }

    //////////////////////////////////////////////////////////////////////////////////////////// fab

    private var fabShowing = false
        set(show) {
            if (field != show) {
                field = show
                fabScrollToBottom?.run { if (show) show() else hide() }
            }
        }

    private var uiLinesBottomOffset = 0
    private fun showHideFabWhenScrolled(dy: Int, onBottom: Boolean) {
        if (onBottom) {
            uiLinesBottomOffset = 0
            fabShowing = false
        } else {
            uiLinesBottomOffset += dy
            if (uiLinesBottomOffset < -FAB_SHOW_THRESHOLD) fabShowing = true
        }
    }

    private fun showHideFabAfterRecyclerViewRestored() {
        fabShowing = uiLines?.onBottom == false
    }

    //////////////////////////////////////////////////////////////////////////// recycler view state

    private data class RecyclerViewState(
        val lastChildPointer: Long,
        val invisiblePixels: Int,
    )

    private var recyclerViewState: RecyclerViewState? = null

    private fun saveRecyclerViewState(outState: Bundle) = ulet(recyclerViewState) {
        outState.putLong(KEY_LAST_CHILD_POINTER, it.lastChildPointer)
        outState.putInt(KEY_INVISIBLE_PIXELS, it.invisiblePixels)
    }

    private fun restoreRecyclerViewState(savedInstanceState: Bundle) {
        recyclerViewState = RecyclerViewState(
                savedInstanceState.getLong(KEY_LAST_CHILD_POINTER),
                savedInstanceState.getInt(KEY_INVISIBLE_PIXELS))
    }

    private fun recordRecyclerViewState() = ulet(uiLines) { lines ->
        recyclerViewState = if (lines.onBottom) {
            null
        } else {
            val lastChild: View = lines.getChildAt(lines.childCount - 1) ?: return
            val lastChildPointer = lines.getChildItemId(lastChild)
            val invisiblePixels = lastChild.bottom - lines.height
            RecyclerViewState(lastChildPointer, invisiblePixels)
        }
    }

    private fun applyRecyclerViewState() = ulet(recyclerViewState, uiLines, linesAdapter) {
            state, lines, adapter ->
        val position = adapter.findPositionByPointer(state.lastChildPointer)
        if (position == -1) return

        lines.scrollToPositionWithOffsetFix(position, state.invisiblePixels)
        lines.post {
            lines.recheckTopBottom()
            showHideFabAfterRecyclerViewRestored()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////// BufferEye stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread override fun onLineAdded() {
        linesAdapter?.onLineAdded()
    }

    @MainThread override fun onGlobalPreferencesChanged(numberChanged: Boolean) {
        linesAdapter?.onGlobalPreferencesChanged(numberChanged)
    }

    @WorkerThread override fun onLinesListed() {
        linesAdapter?.buffer = buffer
        linesAdapter?.onLinesListed()
        Weechat.runOnMainThread {
            adjustConnectivityIndications(true)
            adjustSearchNumbers()   // remove the "+", if needed
            onVisibilityStateChanged(ChangedState.LinesListed)
        }
    }

    @WorkerThread override fun onTitleChanged() {
        linesAdapter?.onTitleChanged()
    }

    @AnyThread override fun onBufferClosed() {
        Weechat.runOnMainThreadASAP { weechatActivity?.closeBuffer(pointer) }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////// keyboard
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private val uiInputHardwareKeyPressListener = View.OnKeyListener { _, keyCode, event ->
        if (event.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false

        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                sendMessageOrStartUpload()
                return@OnKeyListener true
            }
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SEARCH -> {
                tryTabComplete()
                return@OnKeyListener true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
                if (P.volumeBtnSize) {
                    val change = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1f else -1f
                    val textSize = (P.textSize + change).coerceIn(5f, 30f)
                    P.setTextSizeColorAndLetterWidth(textSize)
                    return@OnKeyListener true
                }
            }
        }

        return@OnKeyListener false
    }

    /////////////////////////////////////////////////////////////////////////////////// send message

    private val connectedToRelayAndSynced get() = connectedToRelay && buffer?.linesAreReady() == true

    @MainThread private fun sendMessageOrStartUpload() = ulet(buffer, uiInput) { buffer, input ->
        val suris = input.getNotReadySuris()
        if (suris.isNotEmpty()) {
            startUploads(suris)
        } else {
            assertThat(buffer).isEqualTo(linesAdapter?.buffer)
            if (connectedToRelayAndSynced) {
                SendMessageEvent.fireInput(buffer, input.text.toString())
                input.setText("")   // this will reset tab completion
            } else {
                Toaster.ErrorToast.show(R.string.error__etc__not_connected)
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////// tab completion
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var completer: TabCompleter? = null

    @MainThread private fun tryTabComplete() = ulet(buffer, uiInput) { buffer, input ->
        if (completer == null) completer = TabCompleter.obtain(lifecycle, buffer, input)
        completer?.next()
    }

    // check if this input change is caused by tab completion. if not, cancel tab completer
    @MainThread private fun cancelTabCompletionOnInputTextChange() {
        val cancelled = completer?.cancel()
        if (cancelled == true) completer = null
    }

    @MainThread fun setShareObject(shareObject: ShareObject) {
        shareObject.insert(uiInput!!, InsertAt.END)
        if (isSearchEnabled) searchInput?.post { searchEnableDisable(enable = false) }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////// upload
    ////////////////////////////////////////////////////////////////////////////////////////////////

    enum class UploadStatus {
        NOTHING_TO_UPLOAD, HAS_THINGS_TO_UPLOAD, UPLOADING
    }

    private var uploadManager: UploadManager? = null
    private var lastUploadStatus: UploadStatus? = null

    @CatD @MainThread fun setUploadStatus(uploadStatus: UploadStatus) {
        if (uploadStatus == lastUploadStatus) return
        lastUploadStatus = uploadStatus

        when (uploadStatus) {
            UploadStatus.NOTHING_TO_UPLOAD -> {
                if (P.showSend) uiSend?.visibility = View.VISIBLE
                uploadLayout?.visibility = View.GONE
            }
            UploadStatus.HAS_THINGS_TO_UPLOAD, UploadStatus.UPLOADING -> {
                uiSend?.visibility = View.GONE
                uploadLayout?.visibility = View.VISIBLE
                setUploadProgress(-1f)
                val uploadIcon = if (uploadStatus == UploadStatus.HAS_THINGS_TO_UPLOAD)
                    R.drawable.ic_toolbar_upload else R.drawable.ic_toolbar_upload_cancel
                uploadButton?.setImageResource(uploadIcon)
            }
        }
    }

    // show indeterminate progress in the end, when waiting for the server to produce a response
    @CatD @MainThread fun setUploadProgress(ratio: Float) {
        uploadProgressBar?.apply {
            if (ratio < 0) {
                visibility = View.INVISIBLE
                isIndeterminate = false     // this will reset the thing
                progress = 1                // but it has a bug so we need to
                progress = 0                // set it twice
            } else {
                visibility = View.VISIBLE
                if (ratio >= 1f) {
                    isIndeterminate = true
                } else {
                    isIndeterminate = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setProgress((100 * ratio).i, true)
                    } else {
                        progress = (100 * ratio).i
                    }
                }
            }
        }
    }

    private fun fixupUploadsOnInputTextChange() {
        val suris = uiInput!!.getNotReadySuris()
        if (lastUploadStatus == UploadStatus.UPLOADING) {
            uploadManager?.filterUploads(suris)
        }
        if (suris.isEmpty()) {
            setUploadStatus(UploadStatus.NOTHING_TO_UPLOAD)
        } else if (lastUploadStatus != UploadStatus.UPLOADING) {
            setUploadStatus(UploadStatus.HAS_THINGS_TO_UPLOAD)
        }
    }

    private val uploadObserver: UploadObserver = object : UploadObserver {
        override fun onUploadsStarted() {
            setUploadStatus(UploadStatus.UPLOADING)
        }

        override fun onProgress(ratio: Float) {
            setUploadProgress(ratio)
        }

        override fun onUploadDone(suri: Suri) {
            uiInput?.textifyReadySuris()
        }

        override fun onUploadFailure(suri: Suri, e: Exception) {
            if (e !is CancelledException) {
                val message = FriendlyExceptions(context).getFriendlyException(e).message
                Toaster.ErrorToast.show("Could not upload: %s\n\nError: %s", suri.uri, message)
            }
        }

        override fun onFinished() {
            setUploadStatus(if (uiInput!!.getNotReadySuris().isNotEmpty())
                UploadStatus.HAS_THINGS_TO_UPLOAD else UploadStatus.NOTHING_TO_UPLOAD)
        }
    }

    @Cat override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            suppress<Exception>(showToast = true) {
                getShareObjectFromIntent(requestCode, data)?.insert(uiInput!!, InsertAt.CURRENT_POSITION)
            }
        }
    }

    fun shouldShowUploadMenus() = P.showPaperclip && uiPaperclip?.visibility == View.GONE

    @MainThread fun showHidePaperclip(): Unit = ulet(weechatActivity, uiPaperclip, uiInput) {
            activity, paperclip, input ->
        if (!P.showPaperclip) {
            if (paperclip.visibility != View.GONE) paperclip.visibility = View.GONE
            return
        }

        val layout = paperclip.parent as ViewGroup

        // other buttons at this point might not have been measured, so we can't simply get width
        // of the input field; calculate through layout width instead. see R.layout.chatview_main
        val textWidth = input.getTextWidth()
        if (textWidth < 0) {
            // rerun when we can determine text width. we can post() but this ensures the code
            // is re-run earlier, and we can actually show-hide paperclip before the first draw
            input.hasLayoutListener = HasLayoutListener { showHidePaperclip() }
            return
        }

        // for the purpose of the subsequent calculation we pretend that paperclip is shown,
        // else ratio can jump backwards on character entry, revealing the button again.
        // if the send button is off, adding a ShareSpan can reveal it (well, upload button),
        // but it's not a problem as it can only appear on text addition and disappear on deletion*
        var widgetWidth = P.weaselWidth - P._4dp - actionButtonWidth
        if (uiTab?.visibility != View.GONE) widgetWidth -= actionButtonWidth
        if (uiSend?.visibility != View.GONE) widgetWidth -= actionButtonWidth

        val shouldShowPaperclip = textWidth / widgetWidth < 0.8f
        if (shouldShowPaperclip == (paperclip.visibility == View.VISIBLE)) return

        // not entirely correct, but good enough; don't animate if invisible
        val alreadyDrawn = layout.width > 0
        if (alreadyDrawn) TransitionManager.beginDelayedTransition(layout, paperclipTransition)

        paperclip.visibility = if (shouldShowPaperclip) View.VISIBLE else View.GONE
        activity.updateMenuItems()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == WRITE_PERMISSION_REQUEST_FOR_CAMERA &&
                grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            chooseFiles(this, Target.Camera)
        }
    }

    private fun startUploads(suris: List<Suri>?) {
        suppress<Exception>(showToast = true) {
            validateUploadConfig()
            uploadManager!!.startUploads(suris!!)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////// search
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var searchBar: View? = null
    private var inputBar: View? = null

    private var searchInput: BackGestureAwareEditText? = null
    private var searchResultNo: TextView? = null
    private var searchResultCount: TextView? = null
    private var searchUp: ImageButton? = null
    private var searchDown: ImageButton? = null
    private var searchOverflow: ImageButton? = null

    private fun saveSearchState(outState: Bundle) {
        outState.putBoolean(KEY_SEARCHING, isSearchEnabled)
        outState.putLong(KEY_LAST_FOCUSED_MATCH, focusedMatch)
        outState.putBoolean(KEY_CASE_SENSITIVE, searchConfig.caseSensitive)
        outState.putBoolean(KEY_REGEX, searchConfig.regex)
        outState.putString(KEY_SOURCE, searchConfig.source.name)
    }

    private fun restoreSearchState(savedInstanceState: Bundle) {
        matches = if (savedInstanceState.getBoolean(KEY_SEARCHING))
                pendingMatches else emptyMatches
        focusedMatch = savedInstanceState.getLong(KEY_LAST_FOCUSED_MATCH)
        searchConfig = SearchConfig(
                caseSensitive = savedInstanceState.getBoolean(KEY_CASE_SENSITIVE),
                regex = savedInstanceState.getBoolean(KEY_REGEX),
                SearchConfig.Source.valueOf(savedInstanceState.getString(KEY_SOURCE)!!)
        )
    }

    private fun initSearchViews(root: View) {
        searchBar = root.findViewById(R.id.search_bar)
        inputBar = root.findViewById(R.id.input_bar)

        val searchCancel = root.findViewById<ImageButton>(R.id.search_cancel)
        searchInput = root.findViewById(R.id.search_input)
        searchResultNo = root.findViewById(R.id.search_result_no)
        searchResultCount = root.findViewById(R.id.search_result_count)
        searchUp = root.findViewById(R.id.search_up)
        searchDown = root.findViewById(R.id.search_down)
        searchOverflow = root.findViewById(R.id.search_overflow)
        searchCancel.setOnClickListener { searchEnableDisable(enable = false) }

        searchInput?.run {
            // check lifecycle, so that this is not triggered by restoring state
            afterTextChanged {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) triggerNewSearch()
            }

            // not consuming event — letting the keyboard close
            setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) searchUp?.performClick()
                false
            }

            onBackGestureListener = OnBackGestureListener {
                return@OnBackGestureListener if (searchBar?.visibility == View.VISIBLE) {
                    searchEnableDisable(enable = false)
                    true
                } else {
                    false
                }
            }
        }

        searchUp?.setOnClickListener(searchButtonClickListener)
        searchUp?.setOnLongClickListener(searchButtonLongClickListener)
        searchDown?.setOnClickListener(searchButtonClickListener)
        searchDown?.setOnLongClickListener(searchButtonLongClickListener)
        searchOverflow?.setOnClickListener { createPopupMenu().show() }

        // todo figure out why views are recreated while the instance is retained
        // post to searchInput so that this is run after search input has been restored
        if (isSearchEnabled) {
            searchInput?.post { searchEnableDisable(enable = true, newSearch = false) }
        }
    }

    private fun destroySearchViews() {
        searchBar = null
        inputBar = null
        searchInput = null
        searchResultNo = null
        searchResultCount = null
        searchUp = null
        searchDown = null
        searchOverflow = null
    }

    @MainThread @Cat fun searchEnableDisable(enable: Boolean, newSearch: Boolean = false) {
        searchBar?.visibility = if (enable) View.VISIBLE else View.GONE
        inputBar?.visibility = if (enable) View.GONE else View.VISIBLE

        if (enable) {
            searchInput?.requestFocus()
            uiLines?.addItemDecoration(searchMatchDecoration)
            triggerNewSearch()
            if (newSearch) {
                weechatActivity?.imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
                searchInput?.selectAll()
            }
        } else {
            uiInput?.requestFocus()
            weechatActivity?.hideSoftwareKeyboard()
            matches = emptyMatches
            focusedMatch = 0
            uiLines?.removeItemDecoration(searchMatchDecoration)
            linesAdapter?.search = null
        }
    }

    private fun triggerNewSearch() {
        val text = searchInput?.text.toString()
        try {
            buffer?.requestMoreLines(P.searchLineIncrement)
            val matcher = Search.Matcher.fromString(text, searchConfig)
            linesAdapter?.search = Search(matcher, searchListener)
        } catch (e: PatternSyntaxException) {
            linesAdapter?.search = null
            searchListener.onSearchResultsChanged(badRegexPatternMatches)
        }
    }

    private var matches = emptyMatches      // list of pointers
    private var focusedMatch: Long = 0      // pointer to the last focused match, or 0 if n/a

    private val isSearchEnabled get() = matches !== emptyMatches

    private var searchListener = Search.Listener { matches: List<Long> ->
        this.matches = matches
        enableDisableSearchButtons()
        adjustSearchNumbers()
    }

    private fun enableDisableSearchButtons() {
        val hasMatches = matches.isNotEmpty()
        val matchIndex = matches.indexOfOrElse(focusedMatch, matches::size)
        searchUp?.isEnabled = hasMatches && matchIndex > 0
        searchDown?.isEnabled = hasMatches && matchIndex < matches.lastIndex
    }

    // we could be detecting the way the + is shown in different ways.
    // but a + that depends on true availability of lines isn't very useful if we are not
    // requesting the entirety of lines available.
    // so we only show it to indicate that we are fetching lines.
    private fun adjustSearchNumbers() {
        if (!isSearchEnabled) return
        val matchIndex = matches.indexOf(focusedMatch)
        searchResultNo?.text = if (matchIndex == -1)
                "-" else (matches.size - matchIndex).toString()
        searchResultCount?.text = if (matches === badRegexPatternMatches)
                "err" else {
            val size = matches.size.toString()
            val fetching = buffer?.linesStatus == Lines.Status.Fetching
            if (fetching) "$size+" else size
        }
    }

    private var searchConfig = SearchConfig.default

    private fun createPopupMenu(): PopupMenu {
        val popupMenu = PopupMenu(context, searchOverflow)
        popupMenu.inflate(R.menu.menu_search)
        MenuCompat.setGroupDividerEnabled(popupMenu.menu, true)
        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            searchConfig = when (item.itemId) {
                R.id.menu_search_source_prefix -> searchConfig.copy(source = SearchConfig.Source.Prefix)
                R.id.menu_search_source_message -> searchConfig.copy(source = SearchConfig.Source.Message)
                R.id.menu_search_source_prefix_and_message -> searchConfig.copy(source = SearchConfig.Source.PrefixAndMessage)
                R.id.menu_search_regex -> searchConfig.copy(regex = !item.isChecked)
                R.id.menu_search_case_sensitive -> searchConfig.copy(caseSensitive = !item.isChecked)
                else -> return@setOnMenuItemClickListener false
            }
            adjustSearchMenu(popupMenu.menu)
            triggerNewSearch()
            true
        }
        adjustSearchMenu(popupMenu.menu)
        return popupMenu
    }

    private fun adjustSearchMenu(menu: Menu) {
        val sourceId = when (searchConfig.source) {
            SearchConfig.Source.Prefix -> R.id.menu_search_source_prefix
            SearchConfig.Source.Message -> R.id.menu_search_source_message
            SearchConfig.Source.PrefixAndMessage -> R.id.menu_search_source_prefix_and_message
        }
        menu.findItem(sourceId).isChecked = true
        menu.findItem(R.id.menu_search_regex).isChecked = searchConfig.regex
        menu.findItem(R.id.menu_search_case_sensitive).isChecked = searchConfig.caseSensitive
    }

    private var searchButtonClickListener = View.OnClickListener { view: View ->
        val index = matches.indexOfOrElse(focusedMatch, matches::size)
        val change = if (view.id == R.id.search_up) -1 else +1
        scrollToSearchIndex(index + change)
    }

    private var searchButtonLongClickListener = View.OnLongClickListener { view: View ->
        scrollToSearchIndex(if (view.id == R.id.search_up) 0 else matches.lastIndex)
        true
    }

    private fun scrollToSearchIndex(index: Int) {
        matches.getOrNull(index)?.let {
            focusedMatch = it
            linesAdapter?.findPositionByPointer(it)?.let { position ->
                if (position != -1) uiLines?.jumpThenSmoothScrollCentering(position)
            }
            uiLines?.invalidate()   // trigger redecoration
            enableDisableSearchButtons()
            adjustSearchNumbers()
        }
    }

    private var searchMatchDecorationPaint = Paint()
    private var searchMatchDecoration = object : ItemDecoration() {
        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            if (!matches.contains(focusedMatch)) return

            val rect = Rect()
            val paint = searchMatchDecorationPaint

            parent.forEach { child ->
                if (parent.getChildItemId(child) == focusedMatch) {
                    parent.getDecoratedBoundsWithMargins(child, rect)
                    rect.top += child.translationY.i
                    rect.bottom += child.translationY.i
                    canvas.drawRect(rect, paint)
                }
            }
        }
    }
}


private val actionButtonWidth = 48 * P._1dp         // as set in ActionButton style

private val paperclipTransition = Fade().apply {
    duration = 200
    addTarget(R.id.chatview_paperclip)
}

private val connectivityIndicatorTransition = Fade().apply {
    duration = 500
    addTarget(R.id.connectivity_indicator)
}

private val emptyMatches: List<Long> = ArrayList()
private val pendingMatches: List<Long> = ArrayList()
private val badRegexPatternMatches: List<Long> = ArrayList()

private const val KEY_LAST_CHILD_POINTER = "lastChildPointer"
private const val KEY_INVISIBLE_PIXELS = "invisiblePixels"
private const val KEY_SEARCHING = "searching"
private const val KEY_LAST_FOCUSED_MATCH = "lastFocusedMatch"
private const val KEY_REGEX = "regex"
private const val KEY_CASE_SENSITIVE = "caseSensitive"
private const val KEY_SOURCE = "source"


private enum class ConnectivityState(
    val displayBadge: Boolean,
    colorString: String
) {
    Offline(true, "#ff0000"),
    OnlineAndSyncing(true, "#ffaa00"),
    OnlineAndSynced(false,"#00bb33");

    val sendEnabled = !displayBadge
    val badgeColor = Color.parseColor(colorString)
}


private val FAB_SHOW_THRESHOLD = P._200dp * 7
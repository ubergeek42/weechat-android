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
import android.view.DragEvent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.view.MenuCompat
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.adapters.ChatLinesAdapter
import com.ubergeek42.WeechatAndroid.copypaste.Paste
import com.ubergeek42.WeechatAndroid.databinding.ChatviewMainBinding
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.BufferEye
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.Line
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
import com.ubergeek42.WeechatAndroid.upload.MediaAcceptingEditText.HasLayoutListener
import com.ubergeek42.WeechatAndroid.upload.ShareObject
import com.ubergeek42.WeechatAndroid.upload.Suri
import com.ubergeek42.WeechatAndroid.upload.Target
import com.ubergeek42.WeechatAndroid.upload.TextShareObject
import com.ubergeek42.WeechatAndroid.upload.Upload.CancelledException
import com.ubergeek42.WeechatAndroid.upload.UploadManager
import com.ubergeek42.WeechatAndroid.upload.UploadObserver
import com.ubergeek42.WeechatAndroid.upload.UrisShareObject
import com.ubergeek42.WeechatAndroid.upload.WRITE_PERMISSION_REQUEST_FOR_CAMERA
import com.ubergeek42.WeechatAndroid.upload.chooseFiles
import com.ubergeek42.WeechatAndroid.upload.getShareObjectFromIntent
import com.ubergeek42.WeechatAndroid.upload.i
import com.ubergeek42.WeechatAndroid.upload.insertAddingSpacesAsNeeded
import com.ubergeek42.WeechatAndroid.upload.suppress
import com.ubergeek42.WeechatAndroid.upload.validateUploadConfig
import com.ubergeek42.WeechatAndroid.utils.*
import com.ubergeek42.WeechatAndroid.utils.Assert.assertThat
import com.ubergeek42.WeechatAndroid.views.BufferFragmentFullScreenController
import com.ubergeek42.WeechatAndroid.views.OnBackGestureListener
import com.ubergeek42.WeechatAndroid.views.OnJumpedUpWhileScrollingListener
import com.ubergeek42.WeechatAndroid.views.calculateApproximateWeaselWidth
import com.ubergeek42.WeechatAndroid.views.hideSoftwareKeyboard
import com.ubergeek42.WeechatAndroid.views.jumpThenSmoothScroll
import com.ubergeek42.WeechatAndroid.views.jumpThenSmoothScrollCentering
import com.ubergeek42.WeechatAndroid.views.scrollToPositionWithOffsetFix
import com.ubergeek42.WeechatAndroid.views.showSoftwareKeyboard
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.CatD
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.ColorScheme
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.regex.PatternSyntaxException


private const val POINTER_KEY = "pointer"


interface BufferFragmentContainer {
    fun closeBuffer(pointer: Long)
    fun onChatLinesScrolled(dy: Int, onTop: Boolean, onBottom: Boolean)
    val isPagerNoticeablyObscured: Boolean
    fun updateMenuItems()
}


class BufferFragment : Fragment(), BufferEye {
    @Root private val kitty: Kitty = Kitty.make("BF")

    var pointer: Long = 0

    private var container: BufferFragmentContainer? = null
    private var buffer: Buffer? = null
    private var attachedToBuffer = false

    // todo make lateinit soon
    private var linesAdapter: ChatLinesAdapter? = null

    var ui: ChatviewMainBinding? = null

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
        container = context as BufferFragmentContainer
    }

    @MainThread @Cat override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedInstanceState?.let {
            restoreRecyclerViewState(it)
            restoreSearchState(it)
        }
    }

    @MainThread @Cat override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                               savedInstanceState: Bundle?): View {
        val ui = ChatviewMainBinding.inflate(inflater).also { this.ui = it }

        ui.uploadButton.setOnClickListener {
            if (lastUploadStatus == UploadStatus.UPLOADING) {
                uploadManager?.filterUploads(emptyList())
            } else {
                startUploads(ui.chatInput.getNotReadySuris())
            }
        }

        linesAdapter = ChatLinesAdapter(ui.chatLines).apply {
            this@BufferFragment.buffer?.let {
                buffer = it
                loadLinesSilently()
            }

            onLineDoubleTappedListener = { line -> insertNickFromLine(line) }
        }

        ui.chatLines.run {
            adapter = linesAdapter
            isFocusable = false
            isFocusableInTouchMode = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy != 0) {
                        if (focusedInViewPager) {
                            this@BufferFragment.container?.onChatLinesScrolled(dy, onTop, onBottom)
                        }
                        showHideFabWhenScrolled(dy, onBottom)
                    }
                }
            })
            onJumpedUpWhileScrollingListener = OnJumpedUpWhileScrollingListener {
                showHideFabWhenScrolled(-100000, onBottom = false)
            }
        }

        ui.paperclipButton.run {
            setOnClickListener { chooseFiles(this@BufferFragment, Config.paperclipAction1) }
            setOnLongClickListener {
                Config.paperclipAction2?.let { action2 ->
                    chooseFiles(this@BufferFragment, action2)
                    true
                } ?: false
            }
        }

        ui.sendButton.setOnClickListener { sendMessageOrStartUpload() }
        ui.tabButton.setOnClickListener { tryTabComplete() }

        ui.chatInput.run {
            setOnKeyListener(uiInputHardwareKeyPressListener)
            setOnLongClickListener { Paste.showPasteDialog(ui.chatInput) }
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

        ui.chatInput.setOnDragListener(onDragListener)
        ui.root.setOnDragListener(onDragListener)

        ui.scrollToBottomFab.setOnClickListener {
            ui.chatLines.jumpThenSmoothScroll(linesAdapter!!.itemCount - 1)
            focusedMatch = 0
            enableDisableSearchButtons()
            adjustSearchNumbers()
        }

        ui.scrollToBottomFab.setOnLongClickListener {
            ui.chatLines.jumpThenSmoothScroll(0)
            true
        }

        initSearchViews()
        ui.chatLines.post { applyRecyclerViewState() }

        connectedToRelay = true     // assume true, this will get corrected later
        return ui.root
    }

    @MainThread @Cat override fun onDestroyView() {
        super.onDestroyView()
        ui = null
        linesAdapter = null
    }

    @MainThread @Cat override fun onResume() = ulet(ui) { ui ->
        super.onResume()
        ui.tabButton.visibility = if (P.showTab) View.VISIBLE else View.GONE
        EventBus.getDefault().register(this)
        applyColorSchemeToViews()
        adjustConnectivityIndications(false)
        restorePendingInputFromParallelFragment()
        ui.chatInput.textifyReadySuris()   // this will fix any uploads that were finished while we were absent
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
        setPendingInputForParallelFragments()
    }

    @MainThread @Cat override fun onDetach() {
        container = null
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
            = ulet(container, buffer) { container, buffer ->
        if (!buffer.linesAreReady()) return
        kitty.trace("proceeding!")

        val watchedKey = if (container is WeechatActivity) "main-activity" else "bubble-activity"
        val watched = attachedToBuffer && focusedInViewPager && !container.isPagerNoticeablyObscured

        if (buffer.isWatchedByKey(watchedKey) != watched) {
            if (watched) linesAdapter?.scrollToHotLineIfNeeded()
            if (watched) buffer.addWatchedKey(watchedKey) else buffer.removeWatchedKey(watchedKey)
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
        buffer.addBufferEye(this)
        if (buffer.linesAreReady()) linesAdapter?.buffer = buffer
        linesAdapter?.loadLinesWithoutAnimation()
        attachedToBuffer = true
        onVisibilityStateChanged(ChangedState.BufferAttachment)
    }

    // buffer might be null if we are closing fragment that is not connected
    @MainThread @Cat private fun detachFromBuffer() {
        attachedToBuffer = false
        onVisibilityStateChanged(ChangedState.BufferAttachment)
        buffer?.removeBufferEye(this)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////// ui
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread private fun adjustConnectivityIndications(animate: Boolean) = ulet(ui) { ui ->
        val state = when {
            connectedToRelayAndSynced -> ConnectivityState.OnlineAndSynced
            connectedToRelay -> ConnectivityState.OnlineAndSyncing
            else -> ConnectivityState.Offline
        }

        ui.sendButton.isEnabled = state.sendEnabled

        val indicator = ui.connectivityIndicator
        indicator.setColor(state.badgeColor)

        val indicatorVisibility = if (state.displayBadge) View.VISIBLE else View.GONE
        if (indicator.visibility != indicatorVisibility) {
            if (animate) TransitionManager.beginDelayedTransition(
                    indicator.parent as ViewGroup, connectivityIndicatorTransition)
            indicator.visibility = indicatorVisibility
        }
    }

    private fun applyColorSchemeToViews() = ulet(ui) { ui ->
        ui.scrollToBottomFab.setBackgroundColor(P.colorPrimary)
        ui.bottomBar.setBackgroundColor(P.colorPrimary)
        searchMatchDecorationPaint.color = ColorScheme.get().searchMatchBackground
    }

    //////////////////////////////////////////////////////////////////////////////////////////// fab

    private var fabShowing = false
        set(show) {
            if (field != show) {
                field = show
                ui?.scrollToBottomFab?.run { if (show) show() else hide() }
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
        fabShowing = ui?.chatLines?.onBottom == false
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

    private fun recordRecyclerViewState() = ulet(ui?.chatLines) { lines ->
        recyclerViewState = if (lines.onBottom) {
            null
        } else {
            val lastChild: View = lines.getChildAt(lines.childCount - 1) ?: return
            val lastChildPointer = lines.getChildItemId(lastChild)
            val invisiblePixels = lastChild.bottom - lines.height
            RecyclerViewState(lastChildPointer, invisiblePixels)
        }
    }

    private fun applyRecyclerViewState() = ulet(recyclerViewState, ui?.chatLines, linesAdapter) {
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
        Weechat.runOnMainThreadASAP { container?.closeBuffer(pointer) }
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
                val up = keyCode == KeyEvent.KEYCODE_VOLUME_UP
                when (P.volumeRole) {
                    P.VolumeRole.ChangeTextSize -> {
                        val change = if (up) 1f else -1f
                        val textSize = (P.textSize + change).coerceIn(5f, 30f)
                        P.setTextSizeColorAndLetterWidth(textSize)
                        return@OnKeyListener true
                    }
                    P.VolumeRole.NavigateInputHistory -> {
                        ui?.chatInput?.let {
                            P.history.navigate(it, if (up) History.Direction.Older else History.Direction.Newer)
                        }
                        return@OnKeyListener true
                    }
                    else -> {}
                }
            }
        }

        return@OnKeyListener false
    }

    /////////////////////////////////////////////////////////////////////////////////// send message

    private val connectedToRelayAndSynced get() = connectedToRelay && buffer?.linesAreReady() == true

    @MainThread private fun sendMessageOrStartUpload() = ulet(buffer, ui?.chatInput) { buffer, input ->
        val suris = input.getNotReadySuris()
        if (suris.isNotEmpty()) {
            startUploads(suris)
        } else {
            assertThat(buffer).isEqualTo(linesAdapter?.buffer)
            if (connectedToRelayAndSynced) {
                SendMessageEvent.fireInput(buffer, input.text.toString())
                P.history.reset(input)
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

    @MainThread private fun tryTabComplete() = ulet(buffer, ui?.chatInput) { buffer, input ->
        if (completer == null) completer = TabCompleter.obtain(lifecycle, buffer, input)
        completer?.next()
    }

    // check if this input change is caused by tab completion. if not, cancel tab completer
    @MainThread private fun cancelTabCompletionOnInputTextChange() {
        val cancelled = completer?.cancel()
        if (cancelled == true) completer = null
    }

    private fun insertNickFromLine(line: Line) = ulet(ui, line.nick) { ui, nick ->
        if (nick.isNotBlank()) {
            val textToInsert = if (ui.chatInput.selectionStart == 0) "$nick: " else nick
            ui.chatInput.insertAddingSpacesAsNeeded(InsertAt.CURRENT_POSITION, textToInsert)
        }
    }

    @MainThread fun setShareObject(shareObject: ShareObject, insertAt: InsertAt) = ulet(ui) { ui ->
        restorePendingInputFromParallelFragment()
        shareObject.insert(ui.chatInput, insertAt)
        if (isSearchEnabled) ui.searchInput.post { searchEnableDisable(enable = false) }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////// upload
    ////////////////////////////////////////////////////////////////////////////////////////////////

    enum class UploadStatus {
        NOTHING_TO_UPLOAD, HAS_THINGS_TO_UPLOAD, UPLOADING
    }

    private var uploadManager: UploadManager? = null
    private var lastUploadStatus: UploadStatus? = null

    @CatD @MainThread fun setUploadStatus(uploadStatus: UploadStatus) = ulet(ui) { ui ->
        if (uploadStatus == lastUploadStatus) return
        lastUploadStatus = uploadStatus

        when (uploadStatus) {
            UploadStatus.NOTHING_TO_UPLOAD -> {
                ui.sendButton.visibility = if (P.showSend) View.VISIBLE else View.GONE
                ui.uploadLayout.visibility = View.GONE
            }
            UploadStatus.HAS_THINGS_TO_UPLOAD, UploadStatus.UPLOADING -> {
                ui.sendButton.visibility = View.GONE
                ui.uploadLayout.visibility = View.VISIBLE
                setUploadProgress(-1f)
                val uploadIcon = if (uploadStatus == UploadStatus.HAS_THINGS_TO_UPLOAD)
                    R.drawable.ic_toolbar_upload else R.drawable.ic_toolbar_upload_cancel
                ui.uploadButton.setImageResource(uploadIcon)
            }
        }
    }

    // show indeterminate progress in the end, when waiting for the server to produce a response
    @CatD @MainThread fun setUploadProgress(ratio: Float) {
        ui?.uploadProgressBar?.apply {
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
        val suris = ui?.chatInput?.getNotReadySuris() ?: return
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
            ui?.chatInput?.textifyReadySuris()
        }

        override fun onUploadFailure(suri: Suri, e: Exception) {
            if (e !is CancelledException) {
                val message = FriendlyExceptions(context).getFriendlyException(e).message
                Toaster.ErrorToast.show("Could not upload: %s\n\nError: %s", suri.uri, message)
            }
        }

        override fun onFinished() = ulet(ui) { ui ->
            setUploadStatus(if (ui.chatInput.getNotReadySuris().isNotEmpty())
                UploadStatus.HAS_THINGS_TO_UPLOAD else UploadStatus.NOTHING_TO_UPLOAD)
        }
    }

    @Cat override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            suppress<Exception>(showToast = true) {
                getShareObjectFromIntent(requestCode, data)?.let {
                    setShareObject(it, InsertAt.CURRENT_POSITION)
                }
            }
        }
    }

    fun shouldShowUploadMenus() = P.showPaperclip && ui?.paperclipButton?.visibility == View.GONE

    @MainThread fun showHidePaperclip(): Unit = ulet(container, ui) { container, ui ->
        val paperclip = ui.paperclipButton
        val input = ui.chatInput

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

        val weaselWidth = ui.chatLines.width.let {
            if (it > 0) it else activity?.calculateApproximateWeaselWidth() ?: 1000
        }

        // for the purpose of the subsequent calculation we pretend that paperclip is shown,
        // else ratio can jump backwards on character entry, revealing the button again.
        // if the send button is off, adding a ShareSpan can reveal it (well, upload button),
        // but it's not a problem as it can only appear on text addition and disappear on deletion*
        var widgetWidth = weaselWidth - P._4dp - actionButtonWidth
        if (ui.tabButton.visibility != View.GONE) widgetWidth -= actionButtonWidth
        if (ui.sendButton.visibility != View.GONE) widgetWidth -= actionButtonWidth

        val shouldShowPaperclip = textWidth / widgetWidth < 0.8f
        if (shouldShowPaperclip == (paperclip.visibility == View.VISIBLE)) return

        // not entirely correct, but good enough; don't animate if invisible
        val alreadyDrawn = layout.width > 0
        if (alreadyDrawn) TransitionManager.beginDelayedTransition(layout, paperclipTransition)

        paperclip.visibility = if (shouldShowPaperclip) View.VISIBLE else View.GONE
        container.updateMenuItems()
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

    private fun initSearchViews() = ulet(ui) { ui ->
        ui.searchCancelButton.setOnClickListener { searchEnableDisable(enable = false) }

        ui.searchInput.run {
            // check lifecycle, so that this is not triggered by restoring state
            afterTextChanged {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) triggerNewSearch()
            }

            // not consuming event — letting the keyboard close
            setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) ui.searchUpButton.performClick()
                false
            }

            onBackGestureListener = OnBackGestureListener {
                return@OnBackGestureListener if (ui.searchBar.visibility == View.VISIBLE) {
                    searchEnableDisable(enable = false)
                    true
                } else {
                    false
                }
            }
        }

        ui.searchUpButton.setOnClickListener(searchButtonClickListener)
        ui.searchUpButton.setOnLongClickListener(searchButtonLongClickListener)
        ui.searchDownButton.setOnClickListener(searchButtonClickListener)
        ui.searchDownButton.setOnLongClickListener(searchButtonLongClickListener)
        ui.searchMenuButton.setOnClickListener { createPopupMenu().show() }

        // todo figure out why views are recreated while the instance is retained
        // post to searchInput so that this is run after search input has been restored
        if (isSearchEnabled) {
            ui.searchInput.post { searchEnableDisable(enable = true, newSearch = false) }
        }
    }

    @MainThread @Cat fun searchEnableDisable(enable: Boolean, newSearch: Boolean = false) = ulet(ui) { ui ->
        ui.searchBar.visibility = if (enable) View.VISIBLE else View.GONE
        ui.inputBar.visibility = if (enable) View.GONE else View.VISIBLE

        if (enable) {
            ui.searchInput.requestFocus()
            ui.chatLines.addItemDecoration(searchMatchDecoration)
            triggerNewSearch()
            if (newSearch) {
                ui.searchInput.showSoftwareKeyboard()
                ui.searchInput.selectAll()
            }
        } else {
            ui.chatInput.requestFocus()
            ui.chatInput.hideSoftwareKeyboard()
            matches = emptyMatches
            focusedMatch = 0
            ui.chatLines.removeItemDecoration(searchMatchDecoration)
            linesAdapter?.search = null
        }
    }

    private fun triggerNewSearch() = ulet(ui) { ui ->
        val text = ui.searchInput.text.toString()
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

    private fun enableDisableSearchButtons() = ulet(ui) { ui ->
        val hasMatches = matches.isNotEmpty()
        val matchIndex = matches.indexOfOrElse(focusedMatch, matches::size)
        ui.searchUpButton.isEnabled = hasMatches && matchIndex > 0
        ui.searchDownButton.isEnabled = hasMatches && matchIndex < matches.lastIndex
    }

    // we could be detecting the way the + is shown in different ways.
    // but a + that depends on true availability of lines isn't very useful if we are not
    // requesting the entirety of lines available.
    // so we only show it to indicate that we are fetching lines.
    private fun adjustSearchNumbers() = ulet(ui) { ui ->
        if (!isSearchEnabled) return
        val matchIndex = matches.indexOf(focusedMatch)
        ui.searchResultNo.text = if (matchIndex == -1)
                "-" else (matches.size - matchIndex).toString()
        ui.searchResultCount.text = if (matches === badRegexPatternMatches)
                "err" else {
            val size = matches.size.toString()
            val fetching = buffer?.linesStatus == Lines.Status.Fetching
            if (fetching) "$size+" else size
        }
    }

    private var searchConfig = SearchConfig.default

    private fun createPopupMenu(): PopupMenu {
        val popupMenu = PopupMenu(context, ui!!.searchMenuButton)
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
        val change = if (view.id == R.id.search_up_button) -1 else +1
        scrollToSearchIndex(index + change)
    }

    private var searchButtonLongClickListener = View.OnLongClickListener { view: View ->
        scrollToSearchIndex(if (view.id == R.id.search_up_button) 0 else matches.lastIndex)
        true
    }

    private fun scrollToSearchIndex(index: Int) = ulet(ui) { ui ->
        matches.getOrNull(index)?.let {
            focusedMatch = it
            linesAdapter?.findPositionByPointer(it)?.let { position ->
                if (position != -1) ui.chatLines.jumpThenSmoothScrollCentering(position)
            }
            ui.chatLines.invalidate()   // trigger redecoration
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

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun setPendingInputForParallelFragments() = ulet(buffer, ui) { buffer, ui ->
        ui.chatInput.text?.let {
            pendingInputs[buffer.fullName] = it.makeCopyWithoutUselessSpans()
        }
    }

    private fun restorePendingInputFromParallelFragment() = ulet(buffer, ui?.chatInput) { buffer, chatInput ->
        pendingInputs[buffer.fullName]?.let { pendingInput ->
            if (chatInput.text?.equalsIgnoringUselessSpans(pendingInput) == false) {
                chatInput.setText(pendingInput)
                chatInput.setSelection(pendingInput.length)
            }
            pendingInputs.remove(buffer.fullName)
        }
    }

    // It is possible to use OnReceiveContentListener instead of this.
    // This allows showing some drag and drop indications,
    // as well as more explicit and possibly simpler permission handling.
    // We indicate that we handle `ACTION_DRAG_STARTED`, else `ACTION_DROP` is not received;
    // We indicate that we don't handle other events
    // in order to allow cursor movement in the input field while dragging.
    //
    // We are saving input for parallel fragments (i.e. bubbles) on pause and restoring it on resume.
    // On some systems, particularly on API 27, it's possible that, when dragging from another app,
    // the target activity isn't actually resumed, hence this input change may fail to be recorded.
    // To prevent this, explicitly record the change after loading the thumbnails.
    private val onDragListener = View.OnDragListener { _, event ->
        if (event.action == DragEvent.ACTION_DRAG_STARTED) return@OnDragListener true
        if (event.action != DragEvent.ACTION_DROP) return@OnDragListener false

        ulet(activity, ui, event.clipData) { activity, ui, clipData ->
            val uris = (0..<clipData.itemCount).mapNotNull { clipData.getItemAt(it).uri }

            if (uris.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    activity.requestDragAndDropPermissions(event)
                }

                lifecycleScope.launch {
                    suppress<Exception>(showToast = true) {
                        UrisShareObject.fromUris(uris).insertAsync(ui.chatInput, InsertAt.CURRENT_POSITION)
                        setPendingInputForParallelFragments()
                    }
                }
            } else if (clipData.itemCount > 0) {
                clipData.getItemAt(0).text?.let { text ->
                    TextShareObject(text).insert(ui.chatInput, InsertAt.CURRENT_POSITION)
                    setPendingInputForParallelFragments()
                }
            }
        }

        true
    }
}


private val actionButtonWidth = 48 * P._1dp         // as set in ActionButton style

private val paperclipTransition = Fade().apply {
    duration = 200
    addTarget(R.id.paperclip_button)
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
private const val KEY_HISTORY = "history"

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


private val pendingInputs = mutableMapOf<String, CharSequence>()

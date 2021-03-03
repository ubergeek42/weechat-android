package com.ubergeek42.WeechatAndroid.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import android.widget.TextView.OnEditorActionListener
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.view.MenuCompat
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
import com.ubergeek42.WeechatAndroid.search.Search
import com.ubergeek42.WeechatAndroid.search.Search.Matcher.Companion.fromString
import com.ubergeek42.WeechatAndroid.search.SearchConfig
import com.ubergeek42.WeechatAndroid.service.Events.SendMessageEvent
import com.ubergeek42.WeechatAndroid.service.Events.StateChangedEvent
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.service.RelayService
import com.ubergeek42.WeechatAndroid.tabcomplete.TabCompleter
import com.ubergeek42.WeechatAndroid.tabcomplete.TabCompleter.Companion.obtain
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
import com.ubergeek42.WeechatAndroid.upload.validateUploadConfig
import com.ubergeek42.WeechatAndroid.utils.AnimatedRecyclerView
import com.ubergeek42.WeechatAndroid.utils.FriendlyExceptions
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.WeechatAndroid.utils.Utils
import com.ubergeek42.WeechatAndroid.utils.Utils.SimpleTextWatcher
import com.ubergeek42.WeechatAndroid.views.BackGestureAwareEditText
import com.ubergeek42.WeechatAndroid.views.OnBackGestureListener
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.CatD
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import java.util.regex.PatternSyntaxException


private const val POINTER_KEY = "pointer"


class BufferFragment : Fragment(), BufferEye,
        View.OnKeyListener, View.OnClickListener, TextWatcher, OnEditorActionListener {

    @Root private val kitty: Kitty = Kitty.make("BF")

    private var pointer: Long = 0

    private var weechatActivity: WeechatActivity? = null
    private var buffer: Buffer? = null
    private var attached = false
    private var linesAdapter: ChatLinesAdapter? = null

    private var uiLines: AnimatedRecyclerView? = null
    private var uiInput: MediaAcceptingEditText? = null
    private var uiPaperclip: ImageButton? = null
    private var uiSend: ImageButton? = null
    private var uiTab: ImageButton? = null
    private var uploadLayout: ViewGroup? = null
    private var uploadProgressBar: ProgressBar? = null
    private var uploadButton: ImageButton? = null

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
        kitty.setPrefix(Utils.pointerToString(pointer))
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
    }

    @MainThread @Cat override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?,
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

        uploadButton!!.setOnClickListener {
            if (lastUploadStatus == UploadStatus.UPLOADING) {
                uploadManager!!.filterUploads(emptyList())
            } else {
                startUploads(uiInput!!.getNotReadySuris())
            }
        }

        linesAdapter = ChatLinesAdapter(uiLines)
        uiLines!!.adapter = linesAdapter
        uiLines!!.isFocusable = false
        uiLines!!.isFocusableInTouchMode = false
        uiLines!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (focused && dy != 0) weechatActivity!!.toolbarController.onScroll(dy, uiLines!!.onTop, uiLines!!.onBottom)
            }
        })

        uiPaperclip!!.setOnClickListener { chooseFiles(this, Config.paperclipAction1) }
        uiPaperclip!!.setOnLongClickListener {
            return@setOnLongClickListener if (Config.paperclipAction2 != null) {
                chooseFiles(this, Config.paperclipAction2!!)
                true
            } else {
                false
            }
        }

        uiSend!!.setOnClickListener(this)
        uiTab!!.setOnClickListener(this)

        uiInput!!.setOnKeyListener(this)          // listen for hardware keyboard
        uiInput!!.addTextChangedListener(this)    // listen for software keyboard through watching input box text
        uiInput!!.setOnEditorActionListener(this) // listen for software keyboard's “send” click. see onEditorAction()
        uiInput!!.setOnLongClickListener { Paste.showPasteDialog(uiInput) }

        initSearchViews(v, savedInstanceState)
        online = true
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
    }

    @MainThread @Cat override fun onResume() {
        super.onResume()
        uiTab!!.visibility = if (P.showTab) View.VISIBLE else View.GONE
        EventBus.getDefault().register(this)
        applyColorSchemeToViews()
        uiInput!!.textifyReadySuris()   // this will fix any uploads that were finished while we were absent
        afterTextChanged2()             // this will set appropriate upload ui state
        showHidePaperclip()
        uploadManager!!.observer = uploadObserver   // this will resume ui for any uploads that are still running
    }

    @MainThread @Cat override fun onPause() {
        super.onPause()
        uploadManager!!.observer = null
        lastUploadStatus = null         // setObserver & afterTextChanged2 will fix this
        detachFromBuffer()
        EventBus.getDefault().unregister(this)
    }

    @MainThread @Cat override fun onDetach() {
        weechatActivity = null
        super.onDetach()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    enum class State { ATTACHMENT, PAGER_FOCUS, FULL_VISIBILITY, LINES }
    @MainThread @Cat(linger = true) fun onVisibilityStateChanged(state: State) {
        if (weechatActivity == null || buffer == null || !buffer!!.linesAreReady()) return
        kitty.trace("proceeding!")

        val watched = attached && focused && !weechatActivity!!.isPagerNoticeablyObscured
        if (buffer!!.isWatched != watched) {
            if (watched) linesAdapter!!.scrollToHotLineIfNeeded()
            buffer!!.setWatched(watched)
        }

        if (state == State.PAGER_FOCUS && !focused ||                   // swiping left/right or
                state == State.ATTACHMENT && !attached && focused) {    // minimizing app, closing buffer, disconnecting
            buffer!!.moveReadMarkerToEnd()
            if (state == State.PAGER_FOCUS) linesAdapter!!.onLineAdded()
        }
    }

    private var focused = false

    // called by MainPagerAdapter
    // if true the page is the main in the adapter; called when sideways scrolling is complete
    @MainThread override fun setUserVisibleHint(focused: Boolean) {
        super.setUserVisibleHint(focused)
        this.focused = focused
        onVisibilityStateChanged(State.PAGER_FOCUS)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////// events
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var online = true

    // this can be forced to always run in background, but then it would run after onStart()
    // if the fragment hasn't been initialized yet, that would lead to a bit of flicker
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    @MainThread @Cat fun onEvent(event: StateChangedEvent) {
        val online = event.state.contains(RelayService.STATE.LISTED)
        if (buffer == null || online) {
            buffer = BufferList.findByPointer(pointer)
            if (online && buffer == null) {
                onBufferClosed()
                return
            }
            if (buffer != null) attachToBuffer() else kitty.warn("...buffer is null")   // this should only happen after OOM kill
        }
        if (this.online != online.also { this.online = it }) initUI()
    }

    ////////////////////////////////////////////////////////////////////////////////// attach detach

    @MainThread @Cat private fun attachToBuffer() {
        buffer!!.setBufferEye(this)
        linesAdapter!!.setBuffer(buffer)
        linesAdapter!!.loadLinesWithoutAnimation()
        attached = true
        onVisibilityStateChanged(State.ATTACHMENT)
    }

    // buffer might be null if we are closing fragment that is not connected
    @MainThread @Cat private fun detachFromBuffer() {
        attached = false
        onVisibilityStateChanged(State.ATTACHMENT)
        linesAdapter!!.setBuffer(null)
        if (buffer != null) buffer!!.setBufferEye(null)
        buffer = null
    }

    ///////////////////////////////////////////////////////////////////////////////////////////// ui

    @MainThread @Cat private fun initUI() {
        uiInput!!.isEnabled = online
        uiSend!!.isEnabled = online
        uiTab!!.isEnabled = online
        uiPaperclip!!.isEnabled = online
        if (!online) weechatActivity!!.hideSoftwareKeyboard()
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
        uiLines?.requestAnimation()
        linesAdapter?.onLinesListed()
        Weechat.runOnMainThread { onVisibilityStateChanged(State.LINES) }
    }

    @WorkerThread override fun onPropertiesChanged() {
        linesAdapter?.onPropertiesChanged()
    }

    @AnyThread override fun onBufferClosed() {
        Weechat.runOnMainThreadASAP { weechatActivity?.closeBuffer(pointer) }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////// keyboard / buttons
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // the only OnKeyListener's method, only applies to hardware buttons
    // User pressed some key in the input box, check for what it was
    @MainThread override fun onKey(v: View, keycode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        when (keycode) {
            KeyEvent.KEYCODE_ENTER -> {
                sendMessage()
                return true
            }
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SEARCH -> {
                tryTabComplete()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
                if (P.volumeBtnSize) {
                    val change = if (keycode == KeyEvent.KEYCODE_VOLUME_UP) 1f else -1f
                    val textSize = (P.textSize + change).coerceIn(5f, 30f)
                    P.setTextSizeColorAndLetterWidth(textSize)
                    return true
                }
            }
        }

        return false
    }

    // the only OnClickListener's method. send button or tab button pressed
    @MainThread override fun onClick(view: View) {
        when (view.id) {
            R.id.chatview_send -> sendMessage()
            R.id.chatview_tab -> tryTabComplete()
        }
    }

    // the only OnEditorActionListener's method
    // listens to keyboard's “send” press (not our button)
    @MainThread override fun onEditorAction(textView: TextView, actionId: Int, keyEvent: KeyEvent?): Boolean {
        return if (actionId == EditorInfo.IME_ACTION_SEND) {
            sendMessage()
            return true
        } else {
            false
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////send message

    @MainThread private fun sendMessage() {
        if (buffer == null || uiInput == null) return
        val suris = uiInput!!.getNotReadySuris()
        if (!Utils.isEmpty(suris)) {
            startUploads(suris)
        } else {
            SendMessageEvent.fireInput(buffer!!, uiInput!!.text.toString())
            uiInput!!.setText("")   // this will reset tab completion
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// tab completion
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var completer: TabCompleter? = null
    @MainThread @SuppressLint("SetTextI18n")
    private fun tryTabComplete() {
        if (buffer == null) return
        if (completer == null) completer = obtain(lifecycle, buffer!!, uiInput!!)
        completer!!.next()
    }

    @MainThread @SuppressLint("SetTextI18n")
    fun setShareObject(shareObject: ShareObject) {
        shareObject.insert(uiInput!!, InsertAt.END)
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// text watcher

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

    // invalidate tab completion progress on input box text change
    // tryTabComplete() will set it back if it modified the text causing this function to run
    @MainThread override fun afterTextChanged(s: Editable) {
        if (completer != null) {
            val cancelled = completer!!.cancel()
            if (cancelled) completer = null
        }
        afterTextChanged2()
        showHidePaperclip()
    }

    private fun applyColorSchemeToViews() {
        requireView().findViewById<View>(R.id.bottom_bar).setBackgroundColor(P.colorPrimary)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    enum class UploadStatus {
        NOTHING_TO_UPLOAD, HAS_THINGS_TO_UPLOAD, UPLOADING
    }

    private var lastUploadStatus: UploadStatus? = null

    @CatD @MainThread fun setUploadStatus(uploadStatus: UploadStatus) {
        if (uploadStatus == lastUploadStatus) return
        lastUploadStatus = uploadStatus

        when (uploadStatus) {
            UploadStatus.NOTHING_TO_UPLOAD -> {
                if (P.showSend) uiSend!!.visibility = View.VISIBLE
                uploadLayout!!.visibility = View.GONE
                return
            }
            UploadStatus.HAS_THINGS_TO_UPLOAD -> {
                uiSend!!.visibility = View.GONE
                uploadLayout!!.visibility = View.VISIBLE
                uploadButton!!.setImageResource(R.drawable.ic_toolbar_upload)
                setUploadProgress(-1f)
            }
            UploadStatus.UPLOADING -> {
                uiSend!!.visibility = View.GONE
                uploadLayout!!.visibility = View.VISIBLE
                uploadButton!!.setImageResource(R.drawable.ic_toolbar_upload_cancel)
                setUploadProgress(-1f)
            }
        }
    }

    // show indeterminate progress in the end, when waiting for the server to produce a response
    @CatD @MainThread fun setUploadProgress(ratio: Float) {
        if (ratio < 0) {
            uploadProgressBar!!.visibility = View.INVISIBLE
            uploadProgressBar!!.isIndeterminate = false     // this will reset the thing
            uploadProgressBar!!.progress = 1                // but it has a bug so we need to
            uploadProgressBar!!.progress = 0                // set it twice
        } else {
            uploadProgressBar!!.visibility = View.VISIBLE
            if (ratio >= 1f) {
                uploadProgressBar!!.isIndeterminate = true
            } else {
                uploadProgressBar!!.isIndeterminate = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    uploadProgressBar!!.setProgress((100 * ratio).toInt(), true)
                } else {
                    uploadProgressBar!!.progress = (100 * ratio).toInt()
                }
            }
        }
    }

    private var uploadManager: UploadManager? = null

    private fun afterTextChanged2() {
        val suris = uiInput!!.getNotReadySuris()
        if (lastUploadStatus == UploadStatus.UPLOADING) {
            uploadManager!!.filterUploads(suris)
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
            uiInput!!.textifyReadySuris()
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
            try {
                getShareObjectFromIntent(requestCode, data)
                        ?.insert(uiInput!!, InsertAt.CURRENT_POSITION)
            } catch (e: Exception) {
                Toaster.ErrorToast.show(e)
            }
        }
    }

    fun shouldShowUploadMenus(): Boolean {
        return P.showPaperclip && uiPaperclip != null && uiPaperclip!!.visibility == View.GONE
    }

    @MainThread fun showHidePaperclip() {
        if (weechatActivity == null || uiPaperclip == null) return

        if (!P.showPaperclip) {
            if (uiPaperclip!!.visibility != View.GONE) uiPaperclip!!.visibility = View.GONE
            return
        }

        val layout = uiPaperclip!!.parent as ViewGroup

        // other buttons at this point might not have been measured, so we can't simply get width
        // of the input field; calculate through layout width instead. see R.layout.chatview_main
        val textWidth = uiInput!!.getTextWidth()
        if (textWidth < 0) {
            // rerun when we can determine text width. we can post() but this ensures the code
            // is re-run earlier, and we can actually show-hide paperclip before the first draw
            uiInput!!.hasLayoutListener = HasLayoutListener { showHidePaperclip() }
            return
        }

        // for the purpose of the subsequent calculation we pretend that paperclip is shown,
        // else ratio can jump backwards on character entry, revealing the button again.
        // if the send button is off, adding a ShareSpan can reveal it (well, upload button),
        // but it's not a problem as it can only appear on text addition and disappear on deletion*
        var widgetWidth = P.weaselWidth - P._4dp - actionButtonWidth
        if (uiTab!!.visibility != View.GONE) widgetWidth -= actionButtonWidth
        if (uiSend!!.visibility != View.GONE) widgetWidth -= actionButtonWidth

        val shouldShowPaperclip = textWidth / widgetWidth < 0.8f
        if (shouldShowPaperclip == (uiPaperclip!!.visibility == View.VISIBLE)) return

        // not entirely correct, but good enough; don't animate if invisible
        val alreadyDrawn = layout.width > 0
        if (alreadyDrawn) TransitionManager.beginDelayedTransition(layout, paperclipTransition)

        uiPaperclip!!.visibility = if (shouldShowPaperclip) View.VISIBLE else View.GONE
        weechatActivity!!.updateMenuItems()
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == WRITE_PERMISSION_REQUEST_FOR_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                chooseFiles(this, Target.Camera)
            }
        }
    }

    private fun startUploads(suris: List<Suri>?) {
        try {
            validateUploadConfig()
            uploadManager!!.startUploads(suris!!)
        } catch (e: Exception) {
            Toaster.ErrorToast.show(e)
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

    private fun initSearchViews(root: View, savedInstanceState: Bundle?) {
        searchBar = root.findViewById(R.id.search_bar)
        inputBar = root.findViewById(R.id.input_bar)

        val searchCancel = root.findViewById<ImageButton>(R.id.search_cancel)
        searchInput = root.findViewById(R.id.search_input)
        searchResultNo = root.findViewById(R.id.search_result_no)
        searchResultCount = root.findViewById(R.id.search_result_count)
        searchUp = root.findViewById(R.id.search_up)
        searchDown = root.findViewById(R.id.search_down)
        searchOverflow = root.findViewById(R.id.search_overflow)
        searchCancel.setOnClickListener { searchEnableDisable(enable = false, newSearch = false) }

        // check lifecycle, so that this is not triggered by restoring state
        searchInput!!.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    triggerNewSearch()
                }
            }
        })

        // not consuming event — letting the keyboard close
        searchInput!!.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchUp!!.performClick()
            }
            false
        }

        searchInput!!.onBackGestureListener = OnBackGestureListener {
            return@OnBackGestureListener if (searchBar!!.visibility == View.VISIBLE) {
                searchEnableDisable(enable = false, newSearch = false)
                true
            } else {
                false
            }
        }

        searchUp!!.setOnClickListener(searchButtonClickListener)
        searchDown!!.setOnClickListener(searchButtonClickListener)

        searchOverflow!!.setOnClickListener { createPopupMenu().show() }

        // todo figure out why views are recreated while the instance is retained
        // post to searchInput so that this is run after search input has been restored
        if (matches !== emptyMatches) {
            searchInput!!.post { searchEnableDisable(enable = true, newSearch = false) }
        } else if (savedInstanceState != null && savedInstanceState.getBoolean("searching")) {
            lastFocusedMatch = savedInstanceState.getLong("lastFocusedMatch")
            searchConfig = SearchConfig(
                    savedInstanceState.getBoolean("caseSensitive"),
                    savedInstanceState.getBoolean("regex"),
                    SearchConfig.Source.valueOf(savedInstanceState.getString("source")!!)
            )
            searchInput!!.post { searchEnableDisable(enable = true, newSearch = false) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("searching", matches !== emptyMatches)
        outState.putLong("lastFocusedMatch", lastFocusedMatch)
        outState.putBoolean("regex", searchConfig.regex)
        outState.putBoolean("caseSensitive", searchConfig.caseSensitive)
        outState.putString("source", searchConfig.source.name)
    }

    @MainThread @Cat fun searchEnableDisable(enable: Boolean, newSearch: Boolean) {
        searchBar!!.visibility = if (enable) View.VISIBLE else View.GONE
        inputBar!!.visibility = if (enable) View.GONE else View.VISIBLE

        if (enable) {
            searchInput!!.requestFocus()
            uiLines!!.addItemDecoration(decoration)
            triggerNewSearch()
            if (newSearch) {
                weechatActivity!!.imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
                searchInput!!.selectAll()
            }
        } else {
            uiInput!!.requestFocus()
            weechatActivity!!.hideSoftwareKeyboard()
            matches = emptyMatches
            lastFocusedMatch = 0
            uiLines!!.removeItemDecoration(decoration)
            linesAdapter!!.setSearch(null)
        }
    }

    private fun triggerNewSearch() {
        val text = searchInput!!.text.toString()
        try {
            linesAdapter!!.setSearch(Search(fromString(text, searchConfig), searchListener))
        } catch (e: PatternSyntaxException) {
            linesAdapter!!.setSearch(null)
            searchListener.onSearchResultsChanged(badRegexPatternMatches)
        }
    }

    private var matches = emptyMatches
    private var lastFocusedMatch: Long = 0

    // todo clear lastFocusedMatch here?
    private var searchListener = Search.Listener { matches: List<Long> ->
        kitty.info("matches: %s", matches)
        this.matches = matches
        enableDisableSearchButtons()
        adjustSearchNumbers()
    }

    private fun enableDisableSearchButtons() {
        val hasMatches = matches.isNotEmpty()
        var lastMatchIndex = matches.indexOf(lastFocusedMatch)
        if (lastMatchIndex == -1) lastMatchIndex = matches.size
        searchUp!!.isEnabled = hasMatches && lastMatchIndex > 0
        searchDown!!.isEnabled = hasMatches && lastMatchIndex < matches.size - 1
    }

    private fun adjustSearchNumbers() {
        val lastMatchIndex = matches.indexOf(lastFocusedMatch)
        searchResultNo!!.text = if (lastMatchIndex == -1)
            "-" else (matches.size - lastMatchIndex).toString()
        searchResultCount!!.text = if (matches === badRegexPatternMatches)
            "err" else matches.size.toString()
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
                R.id.menu_search_source_message_and_prefix -> searchConfig.copy(source = SearchConfig.Source.PrefixAndMessage)
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
            SearchConfig.Source.PrefixAndMessage -> R.id.menu_search_source_message_and_prefix
        }
        menu.findItem(sourceId).isChecked = true
        menu.findItem(R.id.menu_search_regex).isChecked = searchConfig.regex
        menu.findItem(R.id.menu_search_case_sensitive).isChecked = searchConfig.caseSensitive
    }

    private var searchButtonClickListener = View.OnClickListener { view: View ->
        var lastMatchIndex = matches.indexOf(lastFocusedMatch)
        if (lastMatchIndex == -1) lastMatchIndex = matches.size

        val delta = if (view.id == R.id.search_up) -1 else +1
        val index = lastMatchIndex + delta
        if (index < 0 || index >= matches.size) return@OnClickListener

        kitty.info("scrolling to %s/%s", index, matches.size)
        lastFocusedMatch = matches[index]
        linesAdapter!!.scrollToPointer(lastFocusedMatch)
        uiLines!!.invalidate()  // trigger redecoration
        enableDisableSearchButtons()
        adjustSearchNumbers()
    }

    private var decoration: ItemDecoration = object : ItemDecoration() {
        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            if (!matches.contains(lastFocusedMatch)) return

            val childCount = parent.childCount
            val rect = Rect()
            val paint = Paint()
            paint.color = 0x22588ab8

            for (i in 0 until childCount) {
                val child = parent.getChildAt(i)
                val pointer = parent.getChildItemId(child)

                if (pointer == lastFocusedMatch) {
                    parent.getDecoratedBoundsWithMargins(child, rect)
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

private val emptyMatches: List<Long> = ArrayList()
private val badRegexPatternMatches: List<Long> = ArrayList()
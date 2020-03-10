package com.ubergeek42.WeechatAndroid.fragments;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import android.content.Context;
import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.copypaste.Paste;
import com.ubergeek42.WeechatAndroid.utils.AnimatedRecyclerView;
import com.ubergeek42.WeechatAndroid.adapters.ChatLinesAdapter;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferEye;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.CopyPaste;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.ColorScheme;

import static com.ubergeek42.WeechatAndroid.service.Events.*;
import static com.ubergeek42.WeechatAndroid.service.RelayService.STATE.*;


public class BufferFragment extends Fragment implements BufferEye, OnKeyListener,
        OnClickListener, TextWatcher, TextView.OnEditorActionListener {

    final private @Root Kitty kitty = Kitty.make("BF");

    private final static String POINTER = "pointer";

    private WeechatActivity activity = null;
    private boolean attached = false;

    private AnimatedRecyclerView uiLines;
    private EditText uiInput;
    private ImageButton uiSend;
    private ImageButton uiTab;

    private long pointer = 0;
    private Buffer buffer;

    private ChatLinesAdapter linesAdapter;

    public static BufferFragment newInstance(long pointer) {
        BufferFragment fragment = new BufferFragment();
        Bundle args = new Bundle();
        args.putLong(POINTER, pointer);
        fragment.setArguments(args);
        return fragment;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// life cycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("ConstantConditions")
    @MainThread @Override public void setArguments(@Nullable Bundle args) {
        super.setArguments(args);
        pointer = getArguments().getLong(POINTER);
        kitty.setPrefix(Utils.pointerToString(pointer));
    }

    @MainThread @Override @Cat public void onAttach(Context context) {
        super.onAttach(context);
        this.activity = (WeechatActivity) context;
    }

    @MainThread @Override @Cat public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @MainThread @Override @Cat
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.chatview_main, container, false);

        uiLines = v.findViewById(R.id.chatview_lines);
        uiInput = v.findViewById(R.id.chatview_input);
        uiSend = v.findViewById(R.id.chatview_send);
        uiTab = v.findViewById(R.id.chatview_tab);

        linesAdapter = new ChatLinesAdapter(uiLines);
        uiLines.setAdapter(linesAdapter);
        uiLines.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (focused && dy != 0) activity.toolbarController.onScroll(dy, uiLines.getOnTop(), uiLines.getOnBottom());
            }
        });

        uiSend.setOnClickListener(this);
        uiTab.setOnClickListener(this);
        uiInput.setOnKeyListener(this);            // listen for hardware keyboard
        uiInput.addTextChangedListener(this);      // listen for software keyboard through watching input box text
        uiInput.setOnEditorActionListener(this);   // listen for software keyboard's “send” click. see onEditorAction()

        uiLines.setFocusable(false);
        uiLines.setFocusableInTouchMode(false);

        uiInput.setOnLongClickListener((View view) -> Paste.showPasteDialog(uiInput));

        online = true;
        return v;
    }

    @MainThread @Override @Cat public void onDestroyView() {
        super.onDestroyView();
        uiLines = null;
        uiInput = null;
        uiSend = null;
        uiTab = null;
        linesAdapter = null;
    }

    @MainThread @Override @Cat public void onResume() {
        super.onResume();
        uiSend.setVisibility(P.showSend ? View.VISIBLE : View.GONE);
        uiTab.setVisibility(P.showTab ? View.VISIBLE : View.GONE);
        uiLines.setBackgroundColor(0xFF000000 | ColorScheme.get().default_color[ColorScheme.OPT_BG]);
        EventBus.getDefault().register(this);
        applyColorSchemeToViews();
    }

    @MainThread @Override @Cat public void onPause() {
        super.onPause();
        detachFromBuffer();
        EventBus.getDefault().unregister(this);
    }

    @MainThread @Override @Cat public void onDetach() {
        activity = null;
        super.onDetach();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public enum State {ATTACHMENT, PAGER_FOCUS, FULL_VISIBILITY, LINES}
    @MainThread @Cat(linger=true) public void onVisibilityStateChanged(State state) {
        if (activity == null || buffer == null || !buffer.linesAreReady()) return;
        kitty.trace("proceeding!");

        boolean watched = attached && focused && !activity.isPagerNoticeablyObscured();
        if (buffer.isWatched != watched) {
            if (watched) linesAdapter.scrollToHotLineIfNeeded();
            buffer.setWatched(watched);
        }

        if ((state == State.PAGER_FOCUS && !focused) ||                 // swiping left/right or
            (state == State.ATTACHMENT && !attached && focused)) {      // minimizing app, closing buffer, disconnecting
            buffer.moveReadMarkerToEnd();
            if (state == State.PAGER_FOCUS) linesAdapter.onLineAdded();
        }
    }

    private boolean focused = false;

    // called by MainPagerAdapter
    // if true the page is the main in the adapter; called when sideways scrolling is complete
    @MainThread @Override public void setUserVisibleHint(boolean focused) {
        super.setUserVisibleHint(focused);
        this.focused = focused;
        onVisibilityStateChanged(State.PAGER_FOCUS);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// events
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean online = true;

    // this can be forced to always run in background, but then it would run after onStart()
    // if the fragment hasn't been initialized yet, that would lead to a bit of flicker
    @Subscribe(sticky=true, threadMode=ThreadMode.MAIN)
    @MainThread @Cat public void onEvent(@NonNull StateChangedEvent event) {
        boolean online = event.state.contains(LISTED);
        if (buffer == null || online) {
            buffer = BufferList.findByPointer(pointer);
            if (online && buffer == null) {
                onBufferClosed();
                return;
            }
            if (buffer != null) attachToBuffer();
            else kitty.warn("...buffer is null");    // this should only happen after OOM kill
        }
        if (this.online != (this.online = online)) initUI();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// attach detach

    @MainThread @Cat private void attachToBuffer() {
        buffer.setBufferEye(this);
        linesAdapter.setBuffer(buffer);
        linesAdapter.loadLinesWithoutAnimation();
        attached = true;
        onVisibilityStateChanged(State.ATTACHMENT);
    }

    // buffer might be null if we are closing fragment that is not connected
    @MainThread @Cat private void detachFromBuffer() {
        attached = false;
        onVisibilityStateChanged(State.ATTACHMENT);
        linesAdapter.setBuffer(null);
        if (buffer != null) buffer.setBufferEye(null);
        buffer = null;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// ui

    @MainThread @Cat private void initUI() {
        uiInput.setEnabled(online);
        uiSend.setEnabled(online);
        uiTab.setEnabled(online);
        if (!online) activity.hideSoftwareKeyboard();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferEye stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread @Override public void onLineAdded() {
        ChatLinesAdapter a = linesAdapter; if (a != null) a.onLineAdded();
    }

    @MainThread @Override public void onGlobalPreferencesChanged(boolean numberChanged) {
        if (linesAdapter == null) return;
        linesAdapter.onGlobalPreferencesChanged(numberChanged);
    }

    @WorkerThread @Override public void onLinesListed() {
        if (uiLines != null) uiLines.requestAnimation();
        ChatLinesAdapter a = linesAdapter; if (a != null) a.onLinesListed();
        Weechat.runOnMainThread(() -> onVisibilityStateChanged(State.LINES));
    }

    @WorkerThread @Override public void onPropertiesChanged() {
        ChatLinesAdapter a = linesAdapter; if (a != null) a.onPropertiesChanged();
    }

    @AnyThread @Override public void onBufferClosed() {
        Weechat.runOnMainThreadASAP(() -> {if (activity != null) activity.closeBuffer(pointer);});
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// keyboard / buttons
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // the only OnKeyListener's method, only applies to hardware buttons
    // User pressed some key in the input box, check for what it was
    @MainThread @Override public boolean onKey(View v, int keycode, KeyEvent event) {
        int action = event.getAction();
        return checkSendMessage(keycode, action) ||
                checkVolumeButtonResize(keycode, action) ||
                checkForTabCompletion(keycode, action);

    }

    @MainThread private boolean checkSendMessage(int keycode, int action) {
        if (keycode == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_UP) sendMessage();
            return true;
        }
        return false;
    }

    @MainThread private boolean checkForTabCompletion(int keycode, int action) {
        if ((keycode == KeyEvent.KEYCODE_TAB || keycode == KeyEvent.KEYCODE_SEARCH) &&
                action == KeyEvent.ACTION_DOWN) {
            tryTabComplete();
            return true;
        }
        return false;
    }

    @MainThread private boolean checkVolumeButtonResize(int keycode, int action) {
        if (keycode == KeyEvent.KEYCODE_VOLUME_DOWN || keycode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (P.volumeBtnSize) {
                if (action == KeyEvent.ACTION_UP) {
                    float textSize = P.textSize;
                    switch (keycode) {
                        case KeyEvent.KEYCODE_VOLUME_UP:
                            if (textSize < 30) textSize += 1;
                            break;
                        case KeyEvent.KEYCODE_VOLUME_DOWN:
                            if (textSize > 5) textSize -= 1;
                            break;
                    }
                    P.setTextSizeColorAndLetterWidth(textSize);
                }
                return true;
            }
        }
        return false;
    }

    // the only OnClickListener's method. send button or tab button pressed
    @MainThread @Override public void onClick(View v) {
        switch (v.getId()) {
            case R.id.chatview_send: sendMessage(); break;
            case R.id.chatview_tab: tryTabComplete(); break;
        }
    }

    // the only OnEditorActionListener's method
    // listens to keyboard's “send” press (not our button)
    @MainThread @Override public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            sendMessage();
            return true;
        }
        return false;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// send message

    @MainThread private void sendMessage() {
        SendMessageEvent.fireInput(buffer.fullName, uiInput.getText().toString());
        uiInput.setText("");   // this will reset tab completion
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// tab completion
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean tcInProgress;
    private ArrayList<String> tcMatches;
    private int tcIndex;
    private int tcWordStart;
    private int tcWordEnd;

    @MainThread @SuppressLint("SetTextI18n") private void tryTabComplete() {
        if (buffer == null) return;

        String txt = uiInput.getText().toString();
        if (!tcInProgress) {
            // find the end of the word to be completed
            // blabla nick|
            tcWordEnd = uiInput.getSelectionStart();
            if (tcWordEnd <= 0)
                return;

            // find the beginning of the word to be completed
            // blabla |nick
            tcWordStart = tcWordEnd;
            while (tcWordStart > 0 && txt.charAt(tcWordStart - 1) != ' ')
                tcWordStart--;

            // get the word to be completed, lowercase
            if (tcWordStart == tcWordEnd)
                return;
            String prefix = txt.substring(tcWordStart, tcWordEnd).toLowerCase();

            // compute a list of possible matches
            // nicks is ordered in last used comes first way, so we just pick whatever comes first
            // if computed list is empty, abort
            tcMatches = buffer.getMostRecentNicksMatching(prefix);
            if (tcMatches.size() == 0)
                return;

            tcIndex = 0;
        } else {
            tcIndex = (tcIndex + 1) % tcMatches.size();
        }

        // get new nickname, adjust the end of the word marker
        // and finally set the text and place the cursor on the end of completed word
        String nick = tcMatches.get(tcIndex);
        if (tcWordStart == 0)
            nick += ": ";
        uiInput.setText(txt.substring(0, tcWordStart) + nick + txt.substring(tcWordEnd));
        tcWordEnd = tcWordStart + nick.length();
        uiInput.setSelection(tcWordEnd);
        // altering text in the input box sets tcInProgress to false,
        // so this is the last thing we do in this function:
        tcInProgress = true;
    }

    @MainThread @SuppressLint("SetTextI18n") public void setText(String text) {
        String oldText = uiInput.getText().toString();
        if (oldText.length() > 0 && oldText.charAt(oldText.length() - 1) != ' ') oldText += ' ';
        uiInput.setText(oldText + text);
        uiInput.setSelection(uiInput.getText().length());
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// text watcher

    @MainThread @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @MainThread @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

    // invalidate tab completion progress on input box text change
    // tryTabComplete() will set it back if it modified the text causing this function to run
    @MainThread @Override public void afterTextChanged(Editable s) {
        tcInProgress = false;
    }

    @SuppressWarnings("ConstantConditions") private void applyColorSchemeToViews() {
        getView().findViewById(R.id.chatview_bottombar).setBackgroundColor(P.colorPrimary);
    }
}

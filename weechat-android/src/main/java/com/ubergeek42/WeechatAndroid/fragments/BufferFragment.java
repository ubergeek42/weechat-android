package com.ubergeek42.WeechatAndroid.fragments;

import java.util.Vector;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ubergeek42.WeechatAndroid.adapters.ChatLinesAdapter;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.Buffer.LINES;
import com.ubergeek42.WeechatAndroid.relay.BufferEye;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.CopyPaste;
import com.ubergeek42.weechat.ColorScheme;

import static com.ubergeek42.WeechatAndroid.service.Events.*;
import static com.ubergeek42.WeechatAndroid.service.RelayService.STATE.*;

import de.greenrobot.event.EventBus;

public class BufferFragment extends Fragment implements BufferEye, OnKeyListener,
        OnClickListener, TextWatcher, TextView.OnEditorActionListener {

    final private static boolean DEBUG_TAB_COMPLETE = false;
    final private static boolean DEBUG_LIFECYCLE = true;
    final private static boolean DEBUG_VISIBILITY = false;
    final private static boolean DEBUG_AUTOSCROLLING = false;

    private final static String TAG = "tag";

    private WeechatActivity activity = null;
    private boolean started = false;

    private ListView uiLines;
    private EditText uiInput;
    private ImageButton uiSend;
    private ImageButton uiTab;

    private ViewGroup uiMore;
    private Button uiMoreButton;

    private String fullName = "…";
    private Buffer buffer;

    private ChatLinesAdapter linesAdapter;
    private int visiblePosition = -1;

    private Logger logger = LoggerFactory.getLogger(toString());

    public static BufferFragment newInstance(String tag) {
        BufferFragment fragment = new BufferFragment();
        Bundle args = new Bundle();
        args.putString(TAG, tag);
        fragment.setArguments(args);
        return fragment;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// life cycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAttach(Context context) {
        if (DEBUG_LIFECYCLE) logger.debug("onAttach(...)");
        super.onAttach(context);
        this.activity = (WeechatActivity) context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG_LIFECYCLE) logger.debug("onCreate()");
        super.onCreate(savedInstanceState);
        fullName = getArguments().getString(TAG);
        logger = LoggerFactory.getLogger(toString());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG_LIFECYCLE) logger.debug("onCreateView()");
        View v = inflater.inflate(R.layout.chatview_main, container, false);

        uiLines = (ListView) v.findViewById(R.id.chatview_lines);
        uiInput = (EditText) v.findViewById(R.id.chatview_input);
        uiSend = (ImageButton) v.findViewById(R.id.chatview_send);
        uiTab = (ImageButton) v.findViewById(R.id.chatview_tab);

        uiSend.setOnClickListener(this);
        uiTab.setOnClickListener(this);
        uiInput.setOnKeyListener(this);            // listen for hardware keyboard
        uiInput.addTextChangedListener(this);      // listen for software keyboard through watching input box text
        uiInput.setOnEditorActionListener(this);   // listen for software keyboard's “send” click. see onEditorAction()

        uiLines.setFocusable(false);
        uiLines.setFocusableInTouchMode(false);

        CopyPaste cp = new CopyPaste(activity, uiInput);
        uiInput.setOnLongClickListener(cp);
        uiLines.setOnItemLongClickListener(cp);

        uiMore = (ViewGroup) inflater.inflate(R.layout.more_button, null);
        uiMoreButton = (Button) uiMore.findViewById(R.id.button_more);
        uiMoreButton.setOnClickListener(this);
        uiLines.addHeaderView(uiMore);
        status = Buffer.LINES.CAN_FETCH_MORE;

        online = true;
        return v;
    }

    @Override
    public void onStart() {
        if (DEBUG_LIFECYCLE) logger.debug("onStart()");
        super.onStart();
        started = true;
        uiSend.setVisibility(P.showSend ? View.VISIBLE : View.GONE);
        uiTab.setVisibility(P.showTab ? View.VISIBLE : View.GONE);
        uiLines.setBackgroundColor(0xFF000000 | ColorScheme.get().defaul[ColorScheme.OPT_BG]);
        EventBus.getDefault().registerSticky(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Backup top visible item for later restore
        if (uiLines != null)
            visiblePosition = uiLines.getFirstVisiblePosition();
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {
        if (DEBUG_LIFECYCLE) logger.debug("onStop()");
        super.onStop();
        started = false;
        detachFromBuffer();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onDetach() {
        if (DEBUG_LIFECYCLE) logger.debug("onDetach()");
        activity = null;
        super.onDetach();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// visibility (set by pager adapter)
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean pagerVisible = false;

    /** these are the highlight and private counts that we are supposed to scroll
     ** they are reset after the scroll has been completed */
    private int highlights = 0;

    // this method is using the following:
    // lastVisibleLine      last line that exists in the buffer. NOTE: "visible" here means line is not filtered in weechat
    // readMarkerLine       used for display. it is:
    //     * saved on app shutdown and restored on start
    //     * altered if a buffer has been read in weechat (see BufferList.saveLastReadLine)
    //     * set to the last displayed line when user navigates away from a buffer
    //     * shifted from invisible line to last visible line if buffer is filtered
    private void maybeMoveReadMarker() {
        if (DEBUG_VISIBILITY) logger.debug("maybeMoveReadMarker()");
        if (buffer != null && buffer.readMarkerLine != buffer.lastVisibleLine) {
            buffer.readMarkerLine = buffer.lastVisibleLine;
            linesAdapter.needMoveLastReadMarker = true;
            onLinesChanged();
        }
    }

    private int privates = 0;

    /** called when visibility of current fragment is (potentially) altered by
     **   * drawer being shown/hidden
     **   * whether buffer is shown in the pager (see MainPagerAdapter)
     **   * availability of buffer & activity
     **   * lifecycle */
    public void maybeChangeVisibilityState() {
        if (DEBUG_VISIBILITY) logger.debug("maybeChangeVisibilityState()");
        if (activity == null || buffer == null)
            return;

        // see if visibility has changed. if it hasn't, do nothing
        boolean obscured = activity.isPagerNoticeablyObscured();
        boolean watched = started && pagerVisible && !obscured;

        if (buffer.isWatched == watched) return;

        // visibility has changed.
        if (watched) {
            highlights = buffer.highlights;
            privates = (buffer.type == Buffer.PRIVATE) ? buffer.unreads : 0;
        }
        buffer.setWatched(watched);
        scrollToHotLineIfNeeded();

        // move the read marker in weechat (if preferences dictate)
        if (!watched && P.hotlistSync) {
            EventBus.getDefault().post(new SendMessageEvent(String.format("input %s /buffer set hotlist -1", buffer.hexPointer())));
            EventBus.getDefault().post(new SendMessageEvent(String.format("input %s /input set_unread_current_buffer", buffer.hexPointer())));
        }
    }

    /** called by MainPagerAdapter
     ** tells us that this page is visible, also used to lifecycle calls (must call super) */
    @Override
    public void setUserVisibleHint(boolean visible) {
        if (DEBUG_VISIBILITY) logger.debug("setUserVisibleHint({})", visible);
        super.setUserVisibleHint(visible);
        this.pagerVisible = visible;
        if (!visible) maybeMoveReadMarker();
        maybeChangeVisibilityState();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// events
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean online = true;

    // this can be forced to always run in background, but then it would run after onStart()
    // if the fragment hasn't been initialized yet, that would lead to a bit of flicker
    @SuppressWarnings("unused")
    public void onEvent(@NonNull StateChangedEvent event) {
        logger.debug("onEvent({})", event);
        boolean online = event.state.contains(LISTED);
        if (buffer == null || online) {
            buffer = BufferList.findByFullName(fullName);
            if (online && buffer == null) {
                onBufferClosed();
                return;
            }
            if (buffer != null) attachToBuffer();
            else logger.warn("...buffer is null");    // this should only happen after OOM kill
        }
        if (this.online != online) initUI(this.online = online);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// attach detach

    private void attachToBuffer() {
        logger.debug("attachToBuffer()");

        buffer.setBufferEye(this);

        linesAdapter = new ChatLinesAdapter(activity, buffer, uiLines);
        linesAdapter.setFont(P.bufferFont);
        linesAdapter.readLinesFromBuffer();

        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                activity.updateCutePagerTitleStrip();
                uiLines.setAdapter(linesAdapter);
                maybeChangeHeader();
                // once everything is settled, restore position if it was saved
                if (visiblePosition != -1)
                    // without the -1 offset hack, the item above the last one gets selected
                    // instead of the one we want
                    uiLines.setSelectionFromTop(visiblePosition, -1);
                // clear the save
                visiblePosition = -1;
            }
        });
        maybeChangeVisibilityState();
    }

    // buffer might be null if we are closing fragment that is not connected
    private void detachFromBuffer() {
        if (DEBUG_LIFECYCLE) logger.debug("detachFromBuffer()");
        maybeChangeVisibilityState();
        if (buffer != null) buffer.setBufferEye(null);
        buffer = null;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// ui

    public void initUI(final boolean online) {
        if (DEBUG_LIFECYCLE) logger.debug("initUI({})", online);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                uiInput.setEnabled(online);
                uiSend.setEnabled(online);
                uiTab.setEnabled(online);
                if (!online)
                    activity.hideSoftwareKeyboard();
            }
        });
    }

    private LINES status = LINES.CAN_FETCH_MORE;
    private void maybeChangeHeader() {
        final LINES s = buffer.getLineStatus();
        if (status == s) return;
        status = s;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                logger.debug("maybeChangeHeader(); {}", s);
                if (s == LINES.EVERYTHING_FETCHED) {
                    uiMore.removeAllViews();
                } else {
                    if (uiMore.getChildCount() == 0) uiMore.addView(uiMoreButton);
                    boolean more = s == LINES.CAN_FETCH_MORE;
                    uiMoreButton.setEnabled(more);
                    uiMoreButton.setTextColor(more ? 0xff80cbc4 : 0xff777777);
                    uiMoreButton.setText(getString(more ? R.string.more_button : R.string.more_button_fetching));
                }
            }
        });
    }

    private void requestMoreLines() {
        if (buffer.getLineStatus() == LINES.CAN_FETCH_MORE) {
            buffer.requestMoreLines();
            maybeChangeHeader();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferEye stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onLinesChanged() {
        if (linesAdapter != null) {
            linesAdapter.onLinesChanged();
        }
        maybeChangeHeader();
    }

    @Override
    public void onLinesListed() {
        if (linesAdapter != null) {
            linesAdapter.onLinesChanged();
        }
        maybeChangeHeader();
        scrollToHotLineIfNeeded();
    }

    @Override
    public void onPropertiesChanged() {
        linesAdapter.onPropertiesChanged();
    }

    @Override
    public void onBufferClosed() {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                activity.closeBuffer(fullName);
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// scrolling
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** scroll to the first hot line, if possible (that is, first unread line in a private buffer
     **     or the first unread highlight)
     ** can be called multiple times, will only run once
     ** posts to the listview to make sure it's fully completed loading the items
     **     after setting the adapter or updating lines */
    public void scrollToHotLineIfNeeded() {
        if (DEBUG_AUTOSCROLLING) logger.debug("scrollToHotLineIfNeeded()");
        if (buffer != null && buffer.isWatched && buffer.holdsAllLines && (highlights > 0 || privates > 0)) {
            uiLines.post(new Runnable() {
                @Override
                public void run() {
                    int count = linesAdapter.getCount(), idx = -2;

                    if (privates > 0) {
                        int p = 0;
                        for (idx = count - 1; idx >= 0; idx--) {
                            Line line = (Line) linesAdapter.getItem(idx);
                            if (line.type == Line.LINE_MESSAGE && ++p == privates) break;
                        }
                    } else if (highlights > 0) {
                        int h = 0;
                        for (idx = count - 1; idx >= 0; idx--) {
                            Line line = (Line) linesAdapter.getItem(idx);
                            if (line.highlighted && ++h == highlights) break;
                        }
                    }

                    if (idx == -1)
                        Toast.makeText(getActivity(), activity.getString(R.string.autoscroll_no_line), Toast.LENGTH_SHORT).show();
                    else if (idx > 0)
                        uiLines.smoothScrollToPosition(idx);

                    highlights = privates = 0;
                }
            });
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// keyboard / buttons
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** the only OnKeyListener's method
     ** User pressed some key in the input box, check for what it was
     ** NOTE: this only applies to HARDWARE buttons */
    @Override
    public boolean onKey(View v, int keycode, KeyEvent event) {
        if (DEBUG_TAB_COMPLETE) logger.debug("onKey(..., {}, ...)", keycode);
        int action = event.getAction();
        return checkSendMessage(keycode, action) ||
                checkVolumeButtonResize(keycode, action) ||
                checkForTabCompletion(keycode, action);

    }

    private boolean checkSendMessage(int keycode, int action) {
        if (keycode == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_UP) sendMessage();
            return true;
        }
        return false;
    }

    private boolean checkForTabCompletion(int keycode, int action) {
        if ((keycode == KeyEvent.KEYCODE_TAB || keycode == KeyEvent.KEYCODE_SEARCH) &&
                action == KeyEvent.ACTION_DOWN) {
            tryTabComplete();
            return true;
        }
        return false;
    }

    private boolean checkVolumeButtonResize(int keycode, int action) {
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
                    P.setTextSizeAndLetterWidth(textSize);
                }
                return true;
            }
        }
        return false;
    }

    /** the only OnClickListener's method
     ** our own send button or tab button pressed */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.chatview_send: sendMessage(); break;
            case R.id.chatview_tab: tryTabComplete(); break;
            case R.id.button_more: requestMoreLines(); break;
        }
    }

    /** the only OnEditorActionListener's method
     ** listens to keyboard's “send” press (NOT our button) */
    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            sendMessage();
            return true;
        }
        return false;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// send message

    /** sends the message if there's anything to send */
    private void sendMessage() {
        String input = uiInput.getText().toString();
        if (input.length() != 0)
            P.addSentMessage(input);
        String[] lines = input.split("\n");
        for (String line : lines) {
            if (line.length() != 0)
                EventBus.getDefault().post(new SendMessageEvent(String.format("input %s %s", buffer.hexPointer(), line)));
        }
        uiInput.setText("");   // this will reset tab completion
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// tab completion
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean tcInProgress;
    private Vector<String> tcMatches;
    private int tcIndex;
    private int tcWordStart;
    private int tcWordEnd;

    /** attempts to perform tab completion on the current input */
    @SuppressLint("SetTextI18n") private void tryTabComplete() {
        if (DEBUG_TAB_COMPLETE) logger.debug("tryTabComplete()");
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
            tcMatches = new Vector<>();

            for (String nick : buffer.getLastUsedNicksCopy())
                if (nick.toLowerCase().startsWith(prefix))
                    tcMatches.add(nick.trim());
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

    public void setText(String text) {
        String oldText = uiInput.getText().toString();
        if (oldText.length() > 0 && oldText.charAt(oldText.length() - 1) != ' ') oldText += ' ';
        uiInput.setText(oldText + text);
        uiInput.setSelection(uiInput.getText().length());
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// text watcher

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

    /** invalidate tab completion progress on input box text change
     ** tryTabComplete() will set it back if it modified the text causing this function to run */
    @Override
    public void afterTextChanged(Editable s) {
        if (DEBUG_TAB_COMPLETE) logger.debug("afterTextChanged(...)");
        tcInProgress = false;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public String toString() {
        return "BF [" + fullName + "]";
    }
}

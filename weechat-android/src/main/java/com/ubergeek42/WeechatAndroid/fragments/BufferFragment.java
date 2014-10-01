package com.ubergeek42.WeechatAndroid.fragments;

import java.util.ArrayList;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.URLSpan;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.ubergeek42.WeechatAndroid.adapters.ChatLinesAdapter;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.service.Buffer;
import com.ubergeek42.WeechatAndroid.service.BufferEye;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;

public class BufferFragment extends SherlockFragment implements BufferEye, OnKeyListener,
        OnSharedPreferenceChangeListener, OnClickListener, TextWatcher, RelayConnectionHandler,
        TextView.OnEditorActionListener {

    private static Logger logger = LoggerFactory.getLogger("BufferFragment");
    final private static boolean DEBUG_TAB_COMPLETE = false;
    final private static boolean DEBUG_LIFECYCLE = true;
    final private static boolean DEBUG_VISIBILITY = false;
    final private static boolean DEBUG_MESSAGES = false;
    final private static boolean DEBUG_CONNECTION = false;
    final private static boolean DEBUG_AUTOSCROLLING = true;

    private static final String PREFS_TEXT_SIZE = "text_size";
    private final static String PREF_SHOW_SEND = "sendbtn_show";
    private final static String PREF_SHOW_TAB = "tabbtn_show";

    public final static String LOCAL_PREF_FULL_NAME = "full_name";

    private WeechatActivity activity = null;
    private boolean started = false;

    private ListView ui_lines;
    private EditText ui_input;
    private ImageButton ui_send;
    private ImageButton ui_tab;

    private String full_name = "…";
    private String short_name = full_name;
    private Buffer buffer;

    private RelayServiceBinder relay;
    private ChatLinesAdapter lines_adapter;
    private SharedPreferences prefs;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// life cycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAttach(Activity activity) {
        if (DEBUG_LIFECYCLE) logger.warn("{} onAttach(...)", full_name);
        super.onAttach(activity);
        this.activity = (WeechatActivity) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG_LIFECYCLE) logger.warn("{} onCreate()", full_name);
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        short_name = full_name = getArguments().getString(LOCAL_PREF_FULL_NAME);
        prefs = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG_LIFECYCLE) logger.warn("{} onCreateView()", full_name);
        View v = inflater.inflate(R.layout.chatview_main, container, false);

        ui_lines = (ListView) v.findViewById(R.id.chatview_lines);
        ui_input = (EditText) v.findViewById(R.id.chatview_input);
        ui_send = (ImageButton) v.findViewById(R.id.chatview_send);
        ui_tab = (ImageButton) v.findViewById(R.id.chatview_tab);

        registerForContextMenu(ui_lines);

        ui_send.setOnClickListener(this);
        ui_tab.setOnClickListener(this);
        ui_input.setOnKeyListener(this);            // listen for hardware keyboard
        ui_input.addTextChangedListener(this);      // listen for software keyboard through watching input box text
        ui_input.setOnEditorActionListener(this);   // listen for software keyboard's “send” click. see onEditorAction()

        return v;
    }

    @Override
    public void onStart() {
        if (DEBUG_LIFECYCLE) logger.warn("{} onStart()", full_name);
        super.onStart();
        started = true;
        prefs.registerOnSharedPreferenceChangeListener(this);
        activity.bind(this);
    }

    @Override
    public void onStop() {
        if (DEBUG_LIFECYCLE) logger.warn("{} onStop()", full_name);
        super.onStop();
        started = false;
        detachFromBuffer();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        relay = null;
        activity.unbind(this);
    }

    @Override
    public void onDetach() {
        if (DEBUG_LIFECYCLE) logger.warn("{} onDetach()", full_name);
        activity = null;
        super.onDetach();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// visibility (set by pager adapter)
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean pager_visible = false;
    private boolean visible = false;

    /** called when visibility of current fragment is (potentially) altered by
     **   * drawer being shown/hidden
     **   * whether buffer is shown in the pager (see MainPagerAdapter)
     **   * availability of buffer & activity
     **   * lifecycle (todo) */
    public void maybeChangeVisibilityState() {
        if (DEBUG_VISIBILITY) logger.warn("{} maybeChangeVisibilityState()", full_name);
        if (activity == null || buffer == null)
            return;
        boolean obscured = activity.isPagerNoticeablyObscured();
        visible = started && pager_visible && !obscured;
        buffer.setWatched(visible);
        scrollToHotLineIfNeeded();
    }

    /** called by MainPagerAdapter
     ** tells us that this page is visible, also used to lifecycle calls (must call super) */
    @Override
    public void setUserVisibleHint(boolean visible) {
        if (DEBUG_VISIBILITY) logger.warn("{} setUserVisibleHint({})", full_name, visible);
        super.setUserVisibleHint(visible);
        this.pager_visible = visible;
        maybeChangeVisibilityState();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// fake service connection
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void onServiceConnected(RelayServiceBinder relay) {
        if (DEBUG_LIFECYCLE) logger.warn("{} onServiceConnected()", full_name);
        this.relay = relay;
        boolean online = relay.isConnection(RelayService.BUFFERS_LISTED);
        initUI(online);
        attachToBufferOrClose();
    }

    // should never ever happen
    public void onServiceDisconnected() {
        if (buffer != null) buffer.setBufferEye(null);
        buffer = null;
        relay = null;
    }


    //////////////////////////////////////////////////////////////////////////////////////////////// RelayConnectionHandler stuff

    @Override public void onConnecting() {}
    @Override public void onConnect() {}
    @Override public void onAuthenticated() {}
    @Override public void onError(String err, Object extraInfo) {}

    public void onBuffersListed() {
        if (DEBUG_CONNECTION) logger.warn("{} onBuffersListed()", full_name);
        initUI(true);
        attachToBufferOrClose();
    }

    @Override
    public void onDisconnect() {
        if (DEBUG_CONNECTION) logger.warn("{} onDisconnect()", full_name);
        initUI(false);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// the juice
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // there's relay now
    private void attachToBufferOrClose() {
        if (DEBUG_LIFECYCLE) logger.warn("{} attachToBufferOrClose()", full_name);
        buffer = relay.getBufferByFullName(full_name);
        if (buffer == null) {
            // no buffer? it might happen if:
            //  * the buffer was closed in weechat. if so, close here as well
            //    (post so that closing doesn't get executed on current loop to avoid issues)
            //  * we are not yet connected, e.g., after service shutdown. if so,
            //    wait for onBuffersListed event
            if (relay.isConnection(RelayService.BUFFERS_LISTED)) onBufferClosed();
            return;
        }
        short_name = buffer.short_name;
        buffer.setBufferEye(this);                      // buffer watcher TODO: java.lang.NullPointerException if run in thread ?!?!

        lines_adapter = new ChatLinesAdapter(activity, buffer, ui_lines);
        lines_adapter.readLinesFromBuffer();

        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                activity.updateCutePagerTitleStrip();
                ui_lines.setAdapter(lines_adapter);
            }
        });

        relay.addRelayConnectionHandler(this);          // connect/disconnect watcher
        maybeChangeVisibilityState();
    }

    // no relay after dis :<
    // buffer might be null if we are closing fragment that is not connected
    private void detachFromBuffer() {
        if (DEBUG_LIFECYCLE) logger.warn("{} detachFromBuffer()", full_name);
        maybeChangeVisibilityState();
        if (relay != null) relay.removeRelayConnectionHandler(this);        // remove connect / disconnect watcher
        if (buffer != null) buffer.setBufferEye(null);                      // remove buffer watcher
        buffer = null;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// ui

    public void initUI(final boolean online) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                ui_input.setFocusable(online);
                ui_input.setFocusableInTouchMode(online);
                ui_send.setEnabled(online);
                ui_tab.setEnabled(online);
                if (!online)
                    activity.hideSoftwareKeyboard();
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PREF_SHOW_SEND) && ui_send != null)
            ui_send.setVisibility(prefs.getBoolean(key, true) ? View.VISIBLE : View.GONE);
        else if (key.equals(PREF_SHOW_TAB) && ui_tab != null)
            ui_tab.setVisibility(prefs.getBoolean(key, true) ? View.VISIBLE : View.GONE);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferEye stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onLinesChanged() {
        lines_adapter.onLinesChanged();
    }

    @Override
    public void onLinesListed() {
        if (DEBUG_MESSAGES) logger.warn("{} onLinesListed()", full_name);
        scrollToHotLineIfNeeded();
    }

    @Override
    public void onPropertiesChanged() {
        lines_adapter.onPropertiesChanged();
    }

    // post so that closing buffer never happens in the same loop as opening it
    // must prevent concurrent modification errors & possibly recursive transaction execution?
    @Override
    public void onBufferClosed() {
        if (DEBUG_CONNECTION) logger.warn("{} onBufferClosed()", full_name);
        ui_lines.post(new Runnable() {
            @Override public void run() {
                activity.closeBuffer(full_name);
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// scrolling
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean must_scroll = false;

    public void scrollToHotLine() {
        must_scroll = true;
        scrollToHotLineIfNeeded();
    }

    /** scroll to the first hot line, if possible (that is, first unread line in a private buffer
     **     or the first unread highlight)
     ** can be called multiple times, will only run once
     ** posts to the listview to make sure it's fully completed loading the items
     **     after setting the adapter or updating lines */
    public void scrollToHotLineIfNeeded() {
        if (DEBUG_AUTOSCROLLING) logger.error("{} scrollToHotLineIfNeeded()", short_name);
        if (must_scroll && buffer != null && visible && buffer.holds_all_lines) {
            if (DEBUG_AUTOSCROLLING) logger.error("...proceeding");
            must_scroll = false;
            ui_lines.post(new Runnable() {
                @Override public void run() {
                    if (DEBUG_AUTOSCROLLING) logger.error("...u/h: {}/{}", buffer.old_unreads, buffer.old_highlights);
                    int count = lines_adapter.getCount();
                    Integer idx = null;

                    if (buffer.type == Buffer.PRIVATE && buffer.old_unreads > 0) {
                        int privates = 0;
                        for (idx = count - 1; idx >= 0; idx--) {
                            Buffer.Line line = (Buffer.Line) lines_adapter.getItem(idx);
                            if (line.type == Buffer.Line.LINE_MESSAGE && ++privates == buffer.old_unreads)
                                break;
                        }
                    } else if (buffer.old_highlights > 0) {
                        int highlights = 0;
                        for (idx = count - 1; idx >= 0; idx--) {
                            Buffer.Line line = (Buffer.Line) lines_adapter.getItem(idx);
                            if (line.highlighted && ++highlights == buffer.old_highlights) break;
                        }
                    }

                    if (idx == null) {
                        Toast.makeText(getActivity(), "The buffer must have been read in weechat", Toast.LENGTH_SHORT).show();
                    } else if (idx < 0) {
                        Toast.makeText(getActivity(), "Can't find the line to scroll to", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getActivity(), "Scrolling to: " + idx + "/" + count + "(ohl:" + buffer.old_highlights + " our:" + buffer.old_unreads + ")", Toast.LENGTH_LONG).show();
                        ui_lines.smoothScrollToPosition(idx);
                    }
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
        if (DEBUG_TAB_COMPLETE) logger.warn("{} onKey(..., {}, ...)", full_name, keycode);
        int action = event.getAction();
        // Enter key sends the message
        if (keycode == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_UP) activity.runOnUiThread(message_sender);
            return true;
        }
        // Check for text resizing keys (volume buttons)
        if (keycode == KeyEvent.KEYCODE_VOLUME_DOWN || keycode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (action == KeyEvent.ACTION_UP) {
                float text_size = Float.parseFloat(prefs.getString(PREFS_TEXT_SIZE, "10"));
                if (keycode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (text_size < 30) text_size += 1;
                } else {
                    if (text_size > 5) text_size -= 1;
                }
                prefs.edit().putString(PREFS_TEXT_SIZE, Float.toString(text_size)).commit();
            }
            return true;
        }
        // try tab completion if we press tab or search
        if ((keycode == KeyEvent.KEYCODE_TAB || keycode == KeyEvent.KEYCODE_SEARCH) &&
                action == KeyEvent.ACTION_DOWN) {
            tryTabComplete();
            return true;
        }
        return false;
    }

    /** the only OnClickListener's method
     ** our own send button or tab button pressed */
    @Override
    public void onClick(View v) {
        if (v.getId() == ui_send.getId())
            activity.runOnUiThread(message_sender);
        else if (v.getId() == ui_tab.getId())
            tryTabComplete();
    }

    /** the only OnEditorActionListener's method
     ** listens to keyboard's “send” press (NOT our button) */
    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            activity.runOnUiThread(message_sender);
            return true;
        }
        return false;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// send todo why runnable?

    /** ends the message if there's anything to send
     ** if user entered /buffer clear or /CL command, then clear the lines */
    private Runnable message_sender = new Runnable() {
        @Override
        public void run() {
            String input = ui_input.getText().toString();
            if (input.length() > 0) {
                relay.sendMessage("input " + buffer.full_name + " " + input + "\n");
                ui_input.setText("");   // this will reset tab completion
            }
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// tab completion
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean tc_inprogress;
    private Vector<String> tc_matches;
    private int tc_index;
    private int tc_wordstart;
    private int tc_wordend;
    
    /** attempts to perform tab completion on the current input */
    private void tryTabComplete() {
        if (DEBUG_TAB_COMPLETE) logger.warn("tryTabComplete()");
        if (buffer == null) return;

        String txt = ui_input.getText().toString();
        if (!tc_inprogress) {
            // find the end of the word to be completed
            // blabla nick|
            tc_wordend = ui_input.getSelectionStart();
            if (tc_wordend <= 0)
                return;

            // find the beginning of the word to be completed
            // blabla |nick
            tc_wordstart = tc_wordend;
            while (tc_wordstart > 0 && txt.charAt(tc_wordstart - 1) != ' ')
                tc_wordstart--;

            // get the word to be completed, lowercase
            if (tc_wordstart == tc_wordend)
                return;
            String prefix = txt.substring(tc_wordstart, tc_wordend).toLowerCase();

            // compute a list of possible matches
            // nicks is ordered in last used comes first way, so we just pick whatever comes first
            // if computed list is empty, abort
            tc_matches = new Vector<String>();

            for (String nick : buffer.getLastUsedNicksCopy())
                if (nick.toLowerCase().startsWith(prefix))
                    tc_matches.add(nick.trim());
            if (tc_matches.size() == 0)
                return;

            tc_index = 0;
        } else {
            tc_index = (tc_index + 1) % tc_matches.size();
        }

        // get new nickname, adjust the end of the word marker
        // and finally set the text and place the cursor on the end of completed word
        String nick = tc_matches.get(tc_index);
        if (tc_wordstart == 0)
            nick += ": ";
        ui_input.setText(txt.substring(0, tc_wordstart) + nick + txt.substring(tc_wordend));
        tc_wordend = tc_wordstart + nick.length();
        ui_input.setSelection(tc_wordend);
        // altering text in the input box sets tc_inprogress to false,
        // so this is the last thing we do in this function:
        tc_inprogress = true;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// text watcher

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

    /** invalidate tab completion progress on input box text change
     ** tryTabComplete() will set it back if it modified the text causing this function to run */
    @Override
    public void afterTextChanged(Editable s) {
        if (DEBUG_TAB_COMPLETE) logger.warn("{} afterTextChanged(...)", full_name);
        tc_inprogress = false;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// context menu
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private ArrayList<String> copy_list = null;

    /** This is related to the tap and hold menu that appears when clicking on a message
     ** check for visibility is required because this is called for ALL fragments at once
     ** see http://stackoverflow.com/questions/5297842/how-to-handle-oncontextitemselected-in-a-multi-fragment-activity */
    @Override public boolean onContextItemSelected(MenuItem item) {
        if (pager_visible && copy_list != null) {
            @SuppressWarnings("deprecation")
            ClipboardManager cm = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setText(copy_list.get(item.getItemId() - Menu.FIRST));
            return true;
        }
        return false;
    }

    @Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (!(v instanceof ListView)) return;

        TextView ui_textview = (TextView) ((AdapterView.AdapterContextMenuInfo) menuInfo).targetView;
        if (ui_textview == null) return;

        menu.setHeaderTitle("Copy");
        copy_list = new ArrayList<String>();

        // add message
        String message = ((Buffer.Line) ui_textview.getTag()).getNotificationString();
        menu.add(0, Menu.FIRST, 0, message);
        copy_list.add(message);
        
        // add urls
        int i = 1;
        for (URLSpan url: ui_textview.getUrls()) {
            menu.add(0, Menu.FIRST + i++, 1, url.getURL());
            copy_list.add(url.getURL());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public String getShortBufferName() {
        return short_name;
    }
}

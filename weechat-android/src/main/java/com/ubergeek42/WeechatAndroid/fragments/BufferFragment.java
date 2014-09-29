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
import android.os.IBinder;
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
import com.ubergeek42.WeechatAndroid.BuildConfig;
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
    final private static boolean DEBUG = BuildConfig.DEBUG;
    final private static boolean DEBUG_TAB_COMPLETE = false;
    final private static boolean DEBUG_LIFECYCLE = true;
    final private static boolean DEBUG_VISIBILITY = false;
    final private static boolean DEBUG_MESSAGES = false;
    final private static boolean DEBUG_CONNECTION = false;
    final private static boolean DEBUG_AUTOSCROLLING = true;

    private final static String PREF_SHOW_SEND = "sendbtn_show";
    private final static String PREF_SHOW_TAB = "tabbtn_show";

    public final static String LOCAL_PREF_FULL_NAME = "full_name";
    public final static String LOCAL_PREF_FOCUS_HOT = "must_focus_hot";

    private WeechatActivity activity = null;
    private boolean started = false;

    private ListView ui_listview;
    private EditText ui_input;
    private ImageButton ui_send;
    private ImageButton ui_tab;

    private RelayServiceBinder relay;

    private String full_name = "…";
    private String short_name = full_name;
    private Buffer buffer;

    private ChatLinesAdapter chatlines_adapter;

    // Settings for keeping track of the current tab completion stuff
    private boolean tc_inprogress;
    private Vector<String> tc_matches;
    private int tc_index;
    private int tc_wordstart;
    private int tc_wordend;

    // Preference things
    private SharedPreferences prefs;

    /////////////////////////
    ///////////////////////// lifecycle
    /////////////////////////

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
        if (savedInstanceState != null) short_name = savedInstanceState.getString("short_name");
        setRetainInstance(true);
        short_name = full_name = getArguments().getString("full_name");
        getArguments().remove("must_focus_hot");
        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG_LIFECYCLE) logger.warn("{} onCreateView()", full_name);
        View v = inflater.inflate(R.layout.chatview_main, container, false);

        ui_listview = (ListView) v.findViewById(R.id.chatview_lines);
        ui_input = (EditText) v.findViewById(R.id.chatview_input);
        ui_send = (ImageButton) v.findViewById(R.id.chatview_send);
        ui_tab = (ImageButton) v.findViewById(R.id.chatview_tab);

        registerForContextMenu(ui_listview);

        ui_send.setOnClickListener(this);
        ui_tab.setOnClickListener(this);
        ui_input.setOnKeyListener(this);            // listen for hardware keyboard
        ui_input.addTextChangedListener(this);      // listen for software keyboard through watching input box text
        ui_input.setOnEditorActionListener(this);   // listen for software keyboard's “send” click. see onEditorAction()

        return v;
    }

    /** this method is run on “activation” of the buffer, although it may be invisible
     ** we want to be disconnected from all things before the method runs
     ** we connect to the service, which results in:
     **   service_connection.onServiceConnected() which:
     **     binds to RelayConnectionHandler, and,
     **     if connected,
     **       calls onBuffersListed()
     **     else
     **       calls onDisconnect(), which sets the “please connect” message */
    @Override
    public void onStart() {
        if (DEBUG_LIFECYCLE) logger.warn("{} onStart()", full_name);
        super.onStart();
        started = true;
        prefs.registerOnSharedPreferenceChangeListener(this);
        activity.bind(this);
    }

    @Override
    public void onResume() {
        if (DEBUG_LIFECYCLE) logger.warn("{} onResume()", full_name);
        super.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("short_name", full_name);
    }

    @Override
    public void onPause() {
        if (DEBUG_LIFECYCLE) logger.warn("{} onPause()", full_name);
        super.onPause();
    }

    /** this is the “deactivation” of the buffer, not necessarily called when it gets destroyed
     ** we want to disconnect from all things here */
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
    public void onDestroyView() {
        if (DEBUG_LIFECYCLE) logger.warn("{} onDestroyView()", full_name);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (DEBUG_LIFECYCLE) logger.warn("{} onDestroy()", full_name);
        super.onDestroy();
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

    public void maybeChangeVisibilityState() {
        if (DEBUG_VISIBILITY) logger.warn("{} maybeChangeVisibilityState()", full_name);
        if (activity == null || buffer == null)
            return;
        boolean obscured = activity.isPagerNoticeablyObscured();
        visible = started && pager_visible && !obscured;
        if (DEBUG_VISIBILITY) logger.warn("...started={}, pager_visible={}, obscured={} (pager_visible=t&t&f)", new Object[]{started, pager_visible, obscured});
        buffer.setWatched(visible);
        scrollToHotLineIfNeeded();
    }

    @Override
    public void setUserVisibleHint(boolean visible) {
        if (DEBUG_VISIBILITY) logger.warn("{} setUserVisibleHint({})", full_name, visible);
        super.setUserVisibleHint(visible);
        this.pager_visible = visible;
        maybeChangeVisibilityState();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// service connection
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void onServiceConnected(IBinder service) {
        if (DEBUG_LIFECYCLE) logger.warn("{} onServiceConnected()", full_name);
        relay = (RelayServiceBinder) service;
        boolean online = relay.isConnection(RelayService.BUFFERS_LISTED);
        initUI(online);
        attachToBufferOrClose();
    }

    // should never ever happen
    public void onServiceDisconnected() {
        if (buffer != null) {
            buffer.setBufferEye(null);
            buffer = null;
        }
        relay = null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// RelayConnectionHandler stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // there's relay now
    private void attachToBufferOrClose() {
        if (DEBUG_LIFECYCLE) logger.warn("{} attachToBufferOrClose()", full_name);
        buffer = relay.getBufferByFullName(full_name);
        if (buffer == null) {
            onBufferClosed();
            return;
        }
        short_name = buffer.short_name;
        buffer.setBufferEye(this);                      // buffer watcher TODO: java.lang.NullPointerException if run in thread ?!?!

        chatlines_adapter = new ChatLinesAdapter(activity, buffer, ui_listview);
        chatlines_adapter.readLinesFromBuffer();

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.updateCutePagerTitleStrip();
                ui_listview.setAdapter(chatlines_adapter);
            }
        });

        relay.addRelayConnectionHandler(this);          // connect/disconnect watcher
        maybeChangeVisibilityState();
        scrollToHotLineIfNeeded();
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


    public void initUI(final boolean online) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ui_input.setFocusable(online);
                ui_input.setFocusableInTouchMode(online);
                ui_send.setEnabled(online);
                ui_tab.setEnabled(online);
                if (!online)
                    activity.hideSoftwareKeyboard();
            }
        });
    }

    @Override public void onConnecting() {}
    @Override public void onConnect() {}
    @Override public void onAuthenticated() {}
    @Override public void onError(String err, Object extraInfo) {}

    public void onBuffersListed() {
        if (DEBUG_CONNECTION) logger.warn("{} onBuffersListed() <{}>", full_name, this);
        initUI(true);
        attachToBufferOrClose();
    }

    @Override
    public void onDisconnect() {
        if (DEBUG_CONNECTION) logger.warn("{} onDisconnect()", full_name);
        initUI(false);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferObserver stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onLinesChanged() {
        chatlines_adapter.onLinesChanged();
    }

    @Override
    public void onLinesListed() {
        if (DEBUG_MESSAGES) logger.warn("{} onLinesListed()", full_name);
        scrollToHotLineIfNeeded();
    }

    @Override
    public void onPropertiesChanged() {
        chatlines_adapter.onPropertiesChanged();
    }

    @Override
    public void onBufferClosed() {
        if (DEBUG_CONNECTION) logger.warn("{} onBufferClosed()", full_name);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
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
     ** can be called multiple times, resets option when done
     ** posts to the listview to make sure it's fully completed loading the items
     ** after setting the adapter or updating lines */
    public void scrollToHotLineIfNeeded() {
        if (DEBUG_AUTOSCROLLING) logger.error("{} scrollToHotLineIfNeeded()", short_name);
        if (must_scroll && buffer != null && visible && buffer.holds_all_lines) {
            if (DEBUG_AUTOSCROLLING) logger.error("...proceeding");
            ui_listview.post(new Runnable() {
                @Override
                public void run() {
                    if (DEBUG_AUTOSCROLLING) logger.error("...u/h: {}/{}", buffer.old_unreads, buffer.old_highlights);
                    int count = chatlines_adapter.getCount();
                    Integer idx = null;

                    if (buffer.type == Buffer.PRIVATE && buffer.old_unreads > 0) {
                        int privates = 0;
                        for (idx = count - 1; idx >= 0; idx--) {
                            Buffer.Line line = (Buffer.Line) chatlines_adapter.getItem(idx);
                            if (line.type == Buffer.Line.LINE_MESSAGE && ++privates == buffer.old_unreads) break;
                        }
                    } else if (buffer.old_highlights > 0) {
                        int highlights = 0;
                        for (idx = count - 1; idx >= 0; idx--) {
                            Buffer.Line line = (Buffer.Line) chatlines_adapter.getItem(idx);
                            if (line.highlighted && ++highlights == buffer.old_highlights) break;
                        }
                    }


                    if (idx == null) {
                        Toast.makeText(getActivity(), "The buffer must have been read in weechat", Toast.LENGTH_SHORT).show();
                    } else if (idx < 0) {
                        Toast.makeText(getActivity(), "Can't find the line to scroll to", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getActivity(), "Scrolling to: "+ idx + "/" + count + "(ohl:"+buffer.old_highlights+" our:"+buffer.old_unreads+")", Toast.LENGTH_SHORT).show();
                        ui_listview.smoothScrollToPosition(idx);
                    }

                    must_scroll = false;
                }
            });
        }
    }

    /** the only OnKeyListener's method
     ** User pressed some key in the input box, check for what it was
     ** NOTE: this only applies to HARDWARE buttons */
    @Override
    public boolean onKey(View v, int keycode, KeyEvent event) {
        if (DEBUG_TAB_COMPLETE) logger.warn("{} onKey(..., {}, ...)", full_name, keycode);
        int action = event.getAction();
        // Enter key sends the message
        if (keycode == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_UP) getActivity().runOnUiThread(message_sender);
            return true;
        }
        // Check for text resizing keys (volume buttons)
        if (keycode == KeyEvent.KEYCODE_VOLUME_DOWN || keycode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (action == KeyEvent.ACTION_UP) {
                float text_size = Float.parseFloat(prefs.getString("text_size", "10"));
                if (keycode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (text_size < 30) text_size += 1;
                } else {
                    if (text_size > 5) text_size -= 1;
                }
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("text_size", Float.toString(text_size));
                editor.commit();
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
            getActivity().runOnUiThread(message_sender);
        else if (v.getId() == ui_tab.getId())
            tryTabComplete();
    }

    /** the only OnEditorActionListener's method
     ** listens to keyboard's “send” press (NOT our button) */
    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            getActivity().runOnUiThread(message_sender);
            return true;
        }
        return false;
    }

    /** the only OnSharedPreferenceChangeListener's method */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PREF_SHOW_SEND) && ui_send != null)
            ui_send.setVisibility(prefs.getBoolean(key, true) ? View.VISIBLE : View.GONE);
        else if (key.equals(PREF_SHOW_TAB) && ui_tab != null)
            ui_tab.setVisibility(prefs.getBoolean(key, true) ? View.VISIBLE : View.GONE);
    }

    /////////////////////////
    ///////////////////////// send
    /////////////////////////

    /** ends the message if there's anything to send
     ** if user entered /buffer clear or /CL command, then clear the lines */
    private Runnable message_sender = new Runnable() {
        @Override
        public void run() {
            String input = ui_input.getText().toString();
            if (input.length() > 0) {
                if (input.equals("/CL") || input.equals("/buffer clear"))
                    chatlines_adapter.clearLines();
                relay.sendMessage("input " + buffer.full_name + " " + input + "\n");
                ui_input.setText("");   // this will reset tab completion
            }
        }
    };

    /////////////////////////
    ///////////////////////// tab complete
    /////////////////////////
    
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

    public String getShortBufferName() {
        return short_name;
    }

    public Buffer getBuffer() {
        return buffer;
    }

    /////////////////////////
    ///////////////////////// context menu
    /////////////////////////

    private static final int CONTEXT_MENU_COPY_FIRST = Menu.FIRST;
    private ArrayList<String> copy_list = null;

    /** This is related to the tap and hold menu that appears when clicking on a message
     ** check for visibility is required because this is called for ALL fragments at once
     ** see http://stackoverflow.com/questions/5297842/how-to-handle-oncontextitemselected-in-a-multi-fragment-activity */
    @Override public boolean onContextItemSelected(MenuItem item) {
        if (pager_visible && copy_list != null) {
            @SuppressWarnings("deprecation")
            ClipboardManager cm = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setText(copy_list.get(item.getItemId() - CONTEXT_MENU_COPY_FIRST));
            return true;
        }
        return false;
    }

    @Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (!(v instanceof ListView)) return;
        
        View selected = ((AdapterView.AdapterContextMenuInfo) menuInfo).targetView;
        if (selected == null) return;
        TextView textview = (TextView) selected;

        menu.setHeaderTitle("Copy");
        copy_list = new ArrayList<String>();

        // add message
        String message = ((Buffer.Line) textview.getTag()).getNotificationString();
        menu.add(0, CONTEXT_MENU_COPY_FIRST, 0, message);
        copy_list.add(message);
        
        // add urls
        int i = 1;
        for (URLSpan url: textview.getUrls()) {
            menu.add(0, CONTEXT_MENU_COPY_FIRST + i++, 1, url.getURL());
            copy_list.add(url.getURL());
        }
    }

    /////////////////////////
    ///////////////////////// TextWatcher stuff
    /////////////////////////

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

    /** invalidate tab completion progress on input box text change
     ** tryTabComplete() will set it back if it modified the text causing this function to run */
    @Override
    public void afterTextChanged(Editable s) {
        if (DEBUG_TAB_COMPLETE) logger.warn("{} afterTextChanged(...)", full_name);
        tc_inprogress = false;
    }
}

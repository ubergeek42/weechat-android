package com.ubergeek42.WeechatAndroid.fragments;

import java.util.ArrayList;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.WeechatAndroid.ChatLinesAdapter;
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
    final private static boolean DEBUG_LIFECYCLE = false;
    final private static boolean DEBUG_MESSAGES = false;
    final private static boolean DEBUG_CONNECTION = false;
    final private static boolean DEBUG_AUTOSCROLLING = false;

    private final static String PREF_SHOW_SEND = "sendbtn_show";
    private final static String PREF_SHOW_TAB = "tabbtn_show";

    private ListView chatLines;
    private EditText inputBox;
    private Button sendButton;
    private Button tabButton;

    private RelayServiceBinder relay;

    private String full_name = "…";
    private String short_name = full_name;
    private Buffer buffer;

    private boolean must_focus_hot = false;

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
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG_LIFECYCLE) logger.warn("{} onCreate()", full_name);
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) short_name = savedInstanceState.getString("short_name");
        setRetainInstance(true);
        short_name = full_name = getArguments().getString("full_name");
        must_focus_hot = getArguments().getBoolean("must_focus_hot", false);
        getArguments().remove("must_focus_hot");
        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG_LIFECYCLE) logger.warn("{} onCreateView()", full_name);
        View v = inflater.inflate(R.layout.chatview_main, container, false);

        chatLines = (ListView) v.findViewById(R.id.chatview_lines);
        inputBox = (EditText) v.findViewById(R.id.chatview_input);
        sendButton = (Button) v.findViewById(R.id.chatview_send);
        tabButton = (Button) v.findViewById(R.id.chatview_tab);

        sendButton.setOnClickListener(this);
        tabButton.setOnClickListener(this);
        inputBox.setOnKeyListener(this);            // listen for hardware keyboard
        inputBox.addTextChangedListener(this);      // listen for software keyboard through watching input box text
        inputBox.setOnEditorActionListener(this);   // listen for software keyboard's “send” click. see onEditorAction()

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

        if (BuildConfig.DEBUG && (short_name.equals("") || relay != null || buffer != null)) // sanity check
            throw new AssertionError("shit shouldn't be empty");

        prefs.registerOnSharedPreferenceChangeListener(this);
        if (DEBUG_LIFECYCLE) logger.warn("...calling bindService()");
        getActivity().bindService(new Intent(getActivity(), RelayService.class), service_connection,
                Context.BIND_AUTO_CREATE);
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
        if (relay != null) {
            relay.removeRelayConnectionHandler(BufferFragment.this);                // remove connect / disconnect watcher
            relay = null;
        }
        if (buffer != null) {
            buffer.setBufferEye(null);                                              // remove buffer watcher
            buffer.setWatched(false);       // 123
            buffer = null;
        }
        if (DEBUG_LIFECYCLE) logger.warn("...calling unbindService()");
        getActivity().unbindService(service_connection);
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
        super.onDetach();
    }

    /////////////////////////
    ///////////////////////// visibility (set by pager adapter)
    /////////////////////////

    private boolean visible = false;

    @Override
    public void setUserVisibleHint(boolean visible) {
        if (DEBUG_LIFECYCLE) logger.warn("{} setUserVisibleHint({})", full_name, visible);
        super.setUserVisibleHint(visible);
        this.visible = visible;
        if (buffer != null) buffer.setWatched(visible);
        maybeScrollToLine();
    }

    /////////////////////////
    ///////////////////////// service connection
    /////////////////////////

    private ServiceConnection service_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder
                service) {
            if (DEBUG_LIFECYCLE) logger.warn("{} onServiceConnected()", BufferFragment.this.full_name);
            relay = (RelayServiceBinder) service;
            if (relay.isConnection(RelayService.BUFFERS_LISTED))
                BufferFragment.this.onBuffersListed();                                  // TODO: run this in a thread
            else
                BufferFragment.this.onDisconnect();
            relay.addRelayConnectionHandler(BufferFragment.this);                       // connect/disconnect watcher
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {                         // TODO: wut
            if (DEBUG) logger.warn("{} onServiceDisconnected() <- should not happen!", BufferFragment.this.full_name);
            if (buffer != null) {
                buffer.setBufferEye(null);                             // buffer watcher
                buffer = null;
            }
            relay = null;
        }
    };

    /////////////////////////
    ///////////////////////// RelayConnectionHandler stuff
    /////////////////////////

    @Override
    public void onConnecting() {}

    @Override
    public void onConnect() {if (DEBUG_CONNECTION) logger.warn("{} onConnect()", full_name);}

    @Override
    public void onAuthenticated() {}

    /** this function is called when the buffers have been listed, i.e. when we can TRY
     ** attaching to the buffer and fetching lines and sending messages and whatnot
     ** it's not necessary that the buffers have been listed just now, though */
    public void onBuffersListed() {
        if (DEBUG_CONNECTION) logger.warn("{} onBuffersListed() <{}>", full_name, this);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // check if the buffer is still there
                // it should be there at ALL times EXCEPT when we RE-connect to the service and find it missing
                buffer = relay.getBufferByFullName(full_name);
                if (buffer == null) {
                    // TODO: replace with a notification that the buffer's been closed (?) in weechat?
                    ViewGroup vg = (ViewGroup) getView().findViewById(R.id.chatview_layout);
                    vg.removeAllViews();
                    vg.addView(getActivity().getLayoutInflater().inflate(R.layout.buffer_not_loaded, vg, false));
                }
                else {
                    // set short name. we set it here because it's the buffer won't change
                    // and the name should be accessible between calls to this function
                    short_name = buffer.short_name;
                    ((WeechatActivity) getActivity()).updateCutePagerTitleStrip();
                    chatlines_adapter = new ChatLinesAdapter(getActivity(), buffer, chatLines);
                    buffer.setBufferEye(BufferFragment.this);   // buffer watcher TODO: java.lang.NullPointerException if run in thread ?!?!
                    chatlines_adapter.readLinesFromBuffer();
                    registerForContextMenu(chatLines);
                    inputBox.setFocusable(true);
                    inputBox.setFocusableInTouchMode(true);
                    sendButton.setEnabled(true);
                    tabButton.setEnabled(true);
                    sendButton.setVisibility(prefs.getBoolean(PREF_SHOW_SEND, true) ? View.VISIBLE : View.GONE);
                    tabButton.setVisibility(prefs.getBoolean(PREF_SHOW_TAB, false) ? View.VISIBLE : View.GONE);
                    chatLines.setAdapter(chatlines_adapter);
                    buffer.setWatched(visible);
                    maybeScrollToLine();
                }
            }
        });
    }

    /** on disconnect, restore chat lines if any
     ** remove the bottom bar to indicate that we are offline
     ** also remove the keyboard, if any */
    @Override
    public void onDisconnect() {
        if (DEBUG_CONNECTION) logger.warn("{} onDisconnect()", full_name);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (chatlines_adapter != null) chatLines.setAdapter(chatlines_adapter);
                inputBox.setFocusable(false);
                sendButton.setEnabled(false);
                tabButton.setEnabled(false);
                ((WeechatActivity) getActivity()).hideSoftwareKeyboard();
            }
        });
    }

    @Override
    public void onError(String err, Object extraInfo) {}

    /////////////////////////
    ///////////////////////// BufferObserver stuff
    /////////////////////////

    @Override
    public void onLinesChanged() {
        chatlines_adapter.onLinesChanged();
    }

    @Override
    public void onLinesListed() {
        if (DEBUG_MESSAGES) logger.warn("{} onLinesListed()", full_name);
        maybeScrollToLine();
    }

    @Override
    public void onPropertiesChanged() {
        chatlines_adapter.onPropertiesChanged();
    }

    @Override
    public void onBufferClosed() {
        if (DEBUG_CONNECTION) logger.warn("{} onBufferClosed()", full_name);
        ((WeechatActivity) getActivity()).closeBuffer(full_name);
    }

    /////////////////////////
    ///////////////////////// misc
    /////////////////////////

    public void maybeScrollToLine(boolean must_focus_hot) {
        this.must_focus_hot = must_focus_hot;
        maybeScrollToLine();
    }

    /** scroll to the first hot line, if possible  (that is, first unread line in a private buffer
     **     or the first unread highlight)
     ** can be called multiple times, resets {@link #must_focus_hot} when done
     ** posts to the listview to make sure it's fully completed loading the items
     ** after setting the adapter or updating lines */
    public void maybeScrollToLine() {
        if (DEBUG_AUTOSCROLLING) logger.error("{} maybeScrollToLine(), must_focus_hot = {}", full_name, must_focus_hot);
        if (!must_focus_hot || buffer == null || (!visible) || (!buffer.holds_all_lines))
            return;
        chatLines.post(new Runnable() {
            @Override
            public void run() {
                int count = chatlines_adapter.getCount(), idx = -1, highlights = 0;
                if (buffer.type == Buffer.PRIVATE && buffer.old_unreads > 0)
                    idx = count - buffer.old_unreads;
                else if (buffer.old_highlights > 0)
                    for (idx = count - 1; idx >= 0; idx--) {
                        Buffer.Line line = (Buffer.Line) chatlines_adapter.getItem(idx);
                        if (line.highlighted) highlights++;
                        if (highlights == buffer.old_highlights) break;
                    }
                if (idx > 0) chatLines.smoothScrollToPosition(idx);
                else Toast.makeText(getActivity(), "Can't find the line to scroll to", Toast.LENGTH_SHORT).show();
                must_focus_hot = false;
            }
        });
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
        if (v.getId() == sendButton.getId())
            getActivity().runOnUiThread(message_sender);
        else if (v.getId() == tabButton.getId())
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
        if (key.equals(PREF_SHOW_SEND) && sendButton != null)
            sendButton.setVisibility(prefs.getBoolean(key, true) ? View.VISIBLE : View.GONE);
        else if (key.equals(PREF_SHOW_TAB) && tabButton != null)
            tabButton.setVisibility(prefs.getBoolean(key, true) ? View.VISIBLE : View.GONE);
    }

    /////////////////////////
    ///////////////////////// send
    /////////////////////////

    /** ends the message if there's anything to send
     ** if user entered /buffer clear or /CL command, then clear the lines */
    private Runnable message_sender = new Runnable() {
        @Override
        public void run() {
            String input = inputBox.getText().toString();
            if (input.length() > 0) {
                if (input.equals("/CL") || input.equals("/buffer clear"))
                    chatlines_adapter.clearLines();
                relay.sendMessage("input " + buffer.full_name + " " + input + "\n");
                inputBox.setText("");   // this will reset tab completion
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

        String txt = inputBox.getText().toString();
        if (!tc_inprogress) {
            // find the end of the word to be completed
            // blabla nick|
            tc_wordend = inputBox.getSelectionStart();
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
        inputBox.setText(txt.substring(0, tc_wordstart) + nick + txt.substring(tc_wordend));
        tc_wordend = tc_wordstart + nick.length();
        inputBox.setSelection(tc_wordend);
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
        if (visible && copy_list != null) {
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

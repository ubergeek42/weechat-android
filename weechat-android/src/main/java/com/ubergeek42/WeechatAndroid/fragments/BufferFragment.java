package com.ubergeek42.WeechatAndroid.fragments;

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
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
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

    private ListView chatLines;
    private EditText inputBox;
    private Button sendButton;
    private Button tabButton;

    private RelayServiceBinder relay;

    private String full_name = "???";
    private String short_name = "Unknown";
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
        if (DEBUG) logger.warn("{} onAttach(...)", full_name);
        super.onAttach(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) logger.warn("{} onCreate()", full_name);
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) short_name = savedInstanceState.getString("short_name");
        setRetainInstance(true);
        short_name = full_name = getArguments().getString("full_name");
        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) logger.warn("{} onCreateView()", full_name);
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
        if (DEBUG) logger.warn("{} onStart()", full_name);
        super.onStart();

        if (BuildConfig.DEBUG && (short_name.equals("") || relay != null || buffer != null)) // sanity check
            throw new AssertionError("shit shouldn't be empty");

        prefs.registerOnSharedPreferenceChangeListener(this);
        if (DEBUG) logger.warn("...calling bindService()");
        getActivity().bindService(new Intent(getActivity(), RelayService.class), service_connection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        if (DEBUG) logger.warn("{} onResume()", full_name);
        super.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("short_name", full_name);
    }

    @Override
    public void onPause() {
        if (DEBUG) logger.warn("{} onPause()", full_name);
        super.onPause();
    }

    /** this is the “deactivation” of the buffer, not necessarily called when it gets destroyed
     ** we want to disconnect from all things here */
    @Override
    public void onStop() {
        if (DEBUG) logger.warn("{} onStop()", full_name);
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
        if (DEBUG) logger.warn("...calling unbindService()");
        getActivity().unbindService(service_connection);
    }

    @Override
    public void onDestroyView() {
        if (DEBUG) logger.warn("{} onDestroyView()", full_name);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) logger.warn("{} onDestroy()", full_name);
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        if (DEBUG) logger.warn("{} onDetach()", full_name);
        super.onDetach();
    }

    /////////////////////////
    ///////////////////////// visibility (set by pager adapter)
    /////////////////////////

    private boolean visible = false;

    @Override
    public void setUserVisibleHint(boolean visible) {
        if (DEBUG) logger.warn("{} setUserVisibleHint({})", full_name, visible);
        super.setUserVisibleHint(visible);
        this.visible = visible;
        if (buffer != null) buffer.setWatched(visible);
    }

    /////////////////////////
    ///////////////////////// service connection
    /////////////////////////

    private ServiceConnection service_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder
                service) {
            if (DEBUG) logger.warn("{} onServiceConnected(): main thread? {}", BufferFragment.this.full_name, Looper.myLooper() == Looper.getMainLooper());
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
    public void onConnect() {if (DEBUG) logger.warn("{} onConnect()", full_name);}

    @Override
    public void onAuthenticated() {}

    /** this function is called when the buffers have been listed, i.e. when we can TRY
     ** attaching to the buffer and fetching lines and sending messages and whatnot
     ** it's not necessary that the buffers have been listed just now, though */
    public void onBuffersListed() {
        if (DEBUG) logger.warn("{} onBuffersListed() <{}>", full_name, this);

        (new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                // check if the buffer is still there
                // it should be there at ALL times EXCEPT when we RE-connect to the service and find it missing
                buffer = relay.getBufferByFullName(full_name);
                if (buffer == null) return false;

                // set short name. we set it here because it's the buffer won't change
                // and the name should be accessible between calls to this function
                short_name = buffer.short_name;
                chatlines_adapter = new ChatLinesAdapter(getActivity(), buffer);
                buffer.setBufferEye(BufferFragment.this);   // buffer watcher TODO: java.lang.NullPointerException ?!?!
                buffer.setWatched(visible);                 // 123
                chatlines_adapter.onLinesChanged();
                registerForContextMenu(chatLines);
                return true;
            }

            @Override
            protected void onPostExecute(Boolean we_have_buffer) {
                if (we_have_buffer) {
                    inputBox.setFocusable(true);
                    inputBox.setFocusableInTouchMode(true);
                    sendButton.setEnabled(true);
                    tabButton.setEnabled(true);
                    sendButton.setVisibility(prefs.getBoolean("sendbtn_show", true) ? View.VISIBLE : View.GONE);
                    tabButton.setVisibility(prefs.getBoolean("tabbtn_show", false) ? View.VISIBLE : View.GONE);
                    chatLines.setAdapter(chatlines_adapter);
                } else {
                    // TODO: replace with a notification that the buffer's been closed (?) in weechat?
                    ViewGroup vg = (ViewGroup) getView().findViewById(R.id.chatview_layout);
                    vg.removeAllViews();
                    vg.addView(getActivity().getLayoutInflater().inflate(R.layout.buffer_not_loaded, vg, false));

                }
            }
        }).execute();
    }

    /** on disconnect, restore chat lines if any
     ** remove the bottom bar to indicate that we are offline
     ** also remove the keyboard, if any */
    @Override
    public void onDisconnect() {
        if (DEBUG) logger.warn("{} onDisconnect()", full_name);
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
    public void onPropertiesChanged() {
        chatlines_adapter.onPropertiesChanged();
    }

    @Override
    public void onBufferClosed() {
        if (DEBUG) logger.warn("{} onBufferClosed()", full_name);
        ((WeechatActivity) getActivity()).closeBuffer(full_name);
    }

//    @Override
//    public void onNicklistChanged() {
//        //nicks = buffer.getNicks();
//    }

    /////////////////////////
    ///////////////////////// misc
    /////////////////////////

    /** the only OnKeyListener's method
     ** User pressed some key in the input box, check for what it was
     ** NOTE: this only applies to HARDWARE buttons */
    @Override
    public boolean onKey(View v, int keycode, KeyEvent event) {
        if (DEBUG) logger.warn("{} onKey(..., {}, ...)", full_name, keycode);
        int action = event.getAction();
        // Enter key sends the message
        if (keycode == KeyEvent.KEYCODE_ENTER && action == KeyEvent.ACTION_UP) {
            getActivity().runOnUiThread(message_sender);
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
        if (key.equals("sendbtn_show") && sendButton != null)
            sendButton.setVisibility(prefs.getBoolean("sendbtn_show", true) ? View.VISIBLE : View.GONE);
        else if (key.equals("tabbtn_show") && tabButton != null)
            tabButton.setVisibility(prefs.getBoolean("tabbtn_show", true) ? View.VISIBLE : View.GONE);
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

    /** This is related to the tap and hold menu that appears when clicking on a message */
    private static final int CONTEXT_MENU_COPY_TXT = Menu.FIRST;
    private static final int CONTEXT_MENU_COPY_URL = CONTEXT_MENU_COPY_TXT+1;
    private TextView contextMenuView = null;
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (contextMenuView != null) {
            ClipboardManager cm = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            if (item.getItemId() == CONTEXT_MENU_COPY_TXT) {
                CharSequence txt = contextMenuView.getText();
                cm.setText(txt.toString());
            } else if (item.getItemId() >= CONTEXT_MENU_COPY_URL) {                     // TODO: don't follow the url immediately
                URLSpan[] urls = contextMenuView.getUrls();
                cm.setText(urls[item.getItemId() - CONTEXT_MENU_COPY_URL].getURL());
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (!(v instanceof ListView)) return;
        
        View selected = ((AdapterView.AdapterContextMenuInfo) menuInfo).targetView;
        if (selected == null) return;
        
        TextView msg = (TextView) selected.findViewById(R.id.chatline_message);
        if (msg == null) return;

        contextMenuView = msg;
        
        menu.setHeaderTitle("Copy?");
        menu.add(0, CONTEXT_MENU_COPY_TXT, 0, "Copy message text");
        
        URLSpan[] urls = contextMenuView.getUrls();
        int i = 0;
        for (URLSpan url: urls) {
            menu.add(0, CONTEXT_MENU_COPY_URL + i, 1, "URL: " + url.getURL());
            i++;
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

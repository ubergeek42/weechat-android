package com.ubergeek42.WeechatAndroid.fragments;

import java.util.Arrays;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import android.text.style.URLSpan;
import android.util.Log;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.ubergeek42.WeechatAndroid.ChatLinesAdapter;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.BufferObserver;

public class BufferFragment extends SherlockFragment implements BufferObserver, OnKeyListener,
        OnSharedPreferenceChangeListener, OnClickListener {
    private static final String TAG = "BufferFragment";

    private static Logger logger = LoggerFactory.getLogger(BufferFragment.class);

    private ListView chatlines;
    private EditText inputBox;
    private Button sendButton;
    private Button tabButton;

    private boolean mBound;
    private RelayServiceBinder rsb;

    private String fragmentTitle = "";
    private String bufferName = "";
    private Buffer buffer;

    private ChatLinesAdapter chatlineAdapter;

    private String[] nickCache;
    private final String[] message = { "Please wait, loading content." };

    // Settings for keeping track of the current tab completion stuff
    private boolean tabCompletingInProgress;
    private Vector<String> tabCompleteMatches;
    private int tabCompleteCurrentIndex;
    private int tabCompleteWordStart;
    private int tabCompleteWordEnd;

    // Preference things
    private SharedPreferences prefs;
    private boolean enableTabComplete = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.chatview_main, container, false);
    }

    /** Called when the activity is first created. */
    @Override
    public void onStart() {
        super.onStart();

        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        enableTabComplete = prefs.getBoolean("tab_completion", true);

        // During startup, check if there are arguments passed to the fragment.
        // onStart is a good place to do this because the layout has already been
        // applied to the fragment at this point so we can safely call the method
        // below that sets the Buffer text.
        Bundle args = getArguments();
        if (args != null) {
            // Set Buffer based on argument passed in
            this.bufferName = args.getString("buffer");
            // might need a refreshView() here?
        }

        // Bind to the Relay Service
        if (mBound == false) {
            getActivity().bindService(new Intent(getActivity(), RelayService.class), mConnection,
                    Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mBound) {
            getActivity().unbindService(mConnection);
            mBound = false;
        }
    }

    public String getBufferName() {
        return bufferName;
    }
    public String getShortBufferName() {
        if (buffer!=null)
            return buffer.getShortName();
        else
            return "Unknown";
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.debug("Bufferfragment onserviceconnected");
            rsb = (RelayServiceBinder) service;
            mBound = true;

            initView();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            rsb = null;
        }
    };

    private void loadEmptyView() {
        ViewGroup vg = (ViewGroup) getView().findViewById(R.id.chatview_layout);
        vg.removeAllViews();
        View empty = getActivity().getLayoutInflater().inflate(R.layout.buffer_not_loaded, vg,
                false);
        vg.addView(empty);
    }

    public void updateTitle() {
        if (fragmentTitle != null) getActivity().setTitle(fragmentTitle);
    }

    private void initView() {
        // Called without bufferName, can't do anything.
        if (bufferName.equals("")) {
            loadEmptyView();
            return;
        }

        // Remove buffer from hotlist
        if (rsb.getHotlistManager()!= null) {
            rsb.getHotlistManager().removeHotlistItem(bufferName);
        }

        chatlines = (ListView) getView().findViewById(R.id.chatview_lines);
        inputBox = (EditText) getView().findViewById(R.id.chatview_input);
        sendButton = (Button) getView().findViewById(R.id.chatview_send);
        tabButton = (Button) getView().findViewById(R.id.chatview_tab);

        if (prefs.getBoolean("sendbtn_show", true)) {
            sendButton.setVisibility(View.VISIBLE);
        } else {
            sendButton.setVisibility(View.GONE);
        }
        if (prefs.getBoolean("tabbtn_show", false)) {
            tabButton.setVisibility(View.VISIBLE);
        } else {
            tabButton.setVisibility(View.GONE);
        }

        chatlines.setAdapter(new ArrayAdapter<String>(getActivity(), R.layout.tips_list_item, message));
        // chatlines.setEmptyView(getView().findViewById(android.R.id.empty));

        buffer = rsb.getBufferByName(bufferName);

        // The buffer is no longer open...
        if (buffer == null) {
            bufferName = "";
            loadEmptyView();
            return;
        }
        // TODO this could be settings defined by user
        StringBuilder tsb = new StringBuilder();
        String buffername = buffer.getShortName();
        String title = buffer.getTitle();
        if (buffername != null) {
            tsb.append(buffername);
            tsb.append(" ");
        }
        if (title != null) {
            tsb.append(title);
        }
        fragmentTitle = tsb.toString();
        updateTitle();

        buffer.addObserver(this);

        // Subscribe to the buffer(gets the lines for it, and gets nicklist)
        rsb.subscribeBuffer(buffer.getPointer());

        chatlineAdapter = new ChatLinesAdapter(getActivity(), buffer);
        chatlines.setAdapter(chatlineAdapter);
        registerForContextMenu(chatlines);
        onLineAdded();

        sendButton.setOnClickListener(this);
        tabButton.setOnClickListener(this);
        inputBox.setOnKeyListener(this);
    }

    @Override
    public void onLineAdded() {
        rsb.resetNotifications();

        buffer.resetHighlight();
        buffer.resetUnread();

        chatlineAdapter.notifyChanged();
    }

    @Override
    public void onBufferClosed() {
        WeechatActivity act = (WeechatActivity) getActivity();
        act.closeBuffer(bufferName);
        if (buffer != null) {
            rsb.unsubscribeBuffer(buffer.getPointer());
        }
    }

    public String[] getNicklist() {
        return nickCache;
    }

    @Override
    public void onNicklistChanged() {
        nickCache = buffer.getNicks();
        Arrays.sort(nickCache);
    }

    // User pressed some key in the input box, check for what it was
    @Override
    public boolean onKey(View v, int keycode, KeyEvent event) {

        // Enter key sends the message
        if (keycode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
            getActivity().runOnUiThread(messageSender);
            return true;
        }
        // Check for text resizing keys(volume buttons)
        else if (keycode == KeyEvent.KEYCODE_VOLUME_UP && event.getAction() == KeyEvent.ACTION_DOWN) {
            float text_size = Float.parseFloat(prefs.getString("text_size", "10")) + 1;
            // Max text_size of 30
            if (text_size > 30) {
                text_size = 30;
            }
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("text_size", Float.toString(text_size));
            editor.commit();
            return true;
        } else if (keycode == KeyEvent.KEYCODE_VOLUME_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
            float text_size = Float.parseFloat(prefs.getString("text_size", "10")) - 1;
            // Enforce a minimum text size of 5
            if (text_size < 5) {
                text_size = 5;
            }
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("text_size", Float.toString(text_size));
            editor.commit();
            return true;
        } else if (keycode == KeyEvent.KEYCODE_VOLUME_DOWN || keycode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true;// Eat these keys
        } // Try tab completion(using tab key, or search key)
        else if ((keycode == KeyEvent.KEYCODE_TAB || keycode == KeyEvent.KEYCODE_SEARCH)
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            tryTabComplete();
            return true;
        } else if (KeyEvent.isModifierKey(keycode) || keycode == KeyEvent.KEYCODE_TAB
                || keycode == KeyEvent.KEYCODE_SEARCH) {
            // If it was a modifier key(or tab/search), don't kill tabCompletingInProgress
            return false;
        }
        tabCompletingInProgress = false;
        return false;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("tab_completion")) {
            enableTabComplete = prefs.getBoolean("tab_completion", true);
        } else if(key.equals("sendbtn_show") && sendButton != null) {
            if (prefs.getBoolean("sendbtn_show", true)) {
                sendButton.setVisibility(View.VISIBLE);
            } else {
                sendButton.setVisibility(View.GONE);
            }
        } else if(key.equals("tabbtn_show") && tabButton != null) {
            if (prefs.getBoolean("tabbtn_show", false)) {
                tabButton.setVisibility(View.VISIBLE);
            } else {
                tabButton.setVisibility(View.GONE);
            }
        }
    }

    // Send button pressed
    @Override
    public void onClick(View v) {
        if (sendButton.getId() == v.getId()) {
            getActivity().runOnUiThread(messageSender);
        } else if (tabButton.getId() == v.getId()) {
            // do tab completion
            tryTabComplete();
        }
    }

    // Attempts to perform tab completion on the current input
    private void tryTabComplete() {
        if (!enableTabComplete || nickCache == null) {
            return;
        }

        // Get the current input text
        String txt = inputBox.getText().toString();
        if (tabCompletingInProgress == false) {
            int currentPos = inputBox.getSelectionStart() - 1;
            int start = currentPos;
            if (currentPos < 0) {
                return;
            }

            // Search backwards to find the beginning of the word
            while (start > 0 && txt.charAt(start) != ' ') {
                start--;
            }
            if (start > 0) {
                start++;
            }
            String prefix = txt.substring(start, currentPos + 1).toLowerCase();
            if (prefix.length() < 1) {
                // No tab completion
                return;
            }

            Vector<String> matches = new Vector<String>();
            for (String possible : nickCache) {

                String temp = possible.toLowerCase().trim();
                if (temp.startsWith(prefix)) {
                    matches.add(possible.trim());
                }
            }
            if (matches.size() == 0) {
                return;
            }

            tabCompletingInProgress = true;
            tabCompleteMatches = matches;
            tabCompleteCurrentIndex = 0;
            tabCompleteWordStart = start;
            tabCompleteWordEnd = currentPos;
        } else {
            tabCompleteWordEnd = tabCompleteWordStart
                    + tabCompleteMatches.get(tabCompleteCurrentIndex).length() - 1; // end of current tab complete word
            tabCompleteCurrentIndex = (tabCompleteCurrentIndex + 1) % tabCompleteMatches.size(); // next match
        }

        try {
            String newtext = txt.substring(0, tabCompleteWordStart)
                    + tabCompleteMatches.get(tabCompleteCurrentIndex)
                    + txt.substring(tabCompleteWordEnd + 1);

            tabCompleteWordEnd = tabCompleteWordStart
                    + tabCompleteMatches.get(tabCompleteCurrentIndex).length(); // end of new tabcomplete word

            inputBox.setText(newtext);
            inputBox.setSelection(tabCompleteWordEnd);
        } catch (final StringIndexOutOfBoundsException e) {
            Log.d(TAG, "tryTabComplete(): " + e.toString());
            Toast.makeText(getActivity().getBaseContext(), R.string.could_not_complete_nick, Toast.LENGTH_SHORT).show();
        } catch (final IndexOutOfBoundsException e) {
            Log.d(TAG, "tryTabComplete(): " + e.toString());
            Toast.makeText(getActivity().getBaseContext(), R.string.could_not_complete_nick, Toast.LENGTH_SHORT).show();
        }
    }

    // Sends the message if necessary
    private Runnable messageSender = new Runnable() {
        @Override
        public void run() {
            tabCompletingInProgress = false;

            String input = inputBox.getText().toString();
            if (input.length() == 0) {
                return; // Ignore empty input box
            }

            // Check if it was a /buffer clear, /CL command, then clear the lines
            if (input.equals("/CL") || input.equals("/buffer clear")) {
                chatlineAdapter.clearLines();
            }

            String message = "input " + bufferName + " " + input;
            inputBox.setText("");
            rsb.sendMessage(message + "\n");
        }
    };

    /*
     * This is related to the tap and hold menu that appears when clicking on a message
     */
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
            } else if (item.getItemId() >= CONTEXT_MENU_COPY_URL) {
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

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        View selected = info.targetView;
        if (selected == null) return;


        TextView msg = (TextView)selected.findViewById(R.id.chatline_message);
        if (msg==null) return;

        contextMenuView = msg;

        menu.setHeaderTitle("Copy?");
        menu.add(0, CONTEXT_MENU_COPY_TXT, 0, "Copy message text");

        URLSpan[] urls = contextMenuView.getUrls();
        int i=0;
        for(URLSpan url: urls) {
            menu.add(0, CONTEXT_MENU_COPY_URL+i, 1, "URL: " + url.getURL());
            i++;
        }
    }
}

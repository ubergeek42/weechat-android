package com.ubergeek42.WeechatAndroid;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.ubergeek42.WeechatAndroid.adapters.BufferListAdapter;
import com.ubergeek42.WeechatAndroid.adapters.BufferListClickListener;
import com.ubergeek42.WeechatAndroid.service.Events;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.utils.ThemeFix;
import com.ubergeek42.WeechatAndroid.utils.Utils;

import org.greenrobot.eventbus.EventBus;

import static com.ubergeek42.WeechatAndroid.utils.Constants.*;

public class ShareTextActivity extends AppCompatActivity implements
        DialogInterface.OnDismissListener, BufferListClickListener {
    private Dialog dialog;

    private RecyclerView uiRecycler;
    private BufferListAdapter adapter;
    private RelativeLayout uiFilterBar;
    private EditText uiFilter;
    private ImageButton uiFilterClear;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        P.applyThemeAfterActivityCreation(this);
        P.storeThemeOrColorSchemeColors(this);  // required for ThemeFix.fixIconAndColor()
        ThemeFix.fixIconAndColor(this);
    }

    @Override protected void onStart() {
        super.onStart();

        if (!EventBus.getDefault().getStickyEvent(Events.StateChangedEvent.class).state.contains(RelayService.STATE.LISTED)) {
            Weechat.showShortToast(R.string.not_connected);
            finish();
            return;
        }

        Intent intent = getIntent();
        if (Utils.isAnyOf(intent.getAction(), Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            dialog = new Dialog(this, R.style.AlertDialogTheme);
            dialog.setContentView(R.layout.bufferlist_share);

            uiRecycler = dialog.findViewById(R.id.recycler);
            uiFilterBar = dialog.findViewById(R.id.filter_bar);
            uiFilter = dialog.findViewById(R.id.bufferlist_filter);
            uiFilterClear = dialog.findViewById(R.id.bufferlist_filter_clear);

            adapter = new BufferListAdapter();
            uiRecycler.setAdapter(adapter);
            uiFilterClear.setOnClickListener((v) -> uiFilter.setText(null));
            uiFilter.addTextChangedListener(filterTextWatcher);
            uiFilter.setText(BufferListAdapter.filterGlobal);

            adapter.onBuffersChanged();
            dialog.setCanceledOnTouchOutside(true);
            dialog.setCancelable(true);
            dialog.setOnDismissListener(this);

            if (!P.showBufferFilter) {
                uiFilterBar.setVisibility(View.GONE);
                uiRecycler.setPadding(0, 0, 0, 0);
            }
            applyColorSchemeToViews();

            dialog.show();
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (dialog == null) return;
        dialog.setOnDismissListener(null);  // prevent dismiss() from finish()ing the activity
        dialog.dismiss();                   // must be called in order to not cause leaks
    }

    @Override public void onBufferClick(long pointer) {
        Intent intent = new Intent(getApplicationContext(), WeechatActivity.class);
        intent.putExtra(EXTRA_BUFFER_POINTER, pointer);
        intent.putExtra(Intent.EXTRA_INTENT, getIntent());
        startActivity(intent);
        finish();
    }

    @Override public void onDismiss(DialogInterface dialog) {
        finish();
    }

    private TextWatcher filterTextWatcher = new TextWatcher() {
        @MainThread @Override public void afterTextChanged(Editable a) {}
        @MainThread @Override public void beforeTextChanged(CharSequence arg0, int a, int b, int c) {}
        @MainThread @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            uiFilterClear.setVisibility((s.length() == 0) ? View.INVISIBLE : View.VISIBLE);
            adapter.setFilter(s.toString(), false);
            adapter.onBuffersChanged();
        }
    };

    private void applyColorSchemeToViews() {
        uiFilterBar.setBackgroundColor(P.colorPrimary);
        uiRecycler.setBackgroundColor(P.colorPrimary);
    }
}

package com.ubergeek42.WeechatAndroid;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.ubergeek42.WeechatAndroid.adapters.BufferListAdapter;
import com.ubergeek42.WeechatAndroid.adapters.BufferListClickListener;
import com.ubergeek42.WeechatAndroid.service.Events;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.utils.ThemeFix;

import org.greenrobot.eventbus.EventBus;

import static com.ubergeek42.WeechatAndroid.utils.Constants.*;

public class ShareTextActivity extends AppCompatActivity implements
        DialogInterface.OnDismissListener, BufferListClickListener {
    private Dialog dialog;

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
        if ((Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType()))) {
            dialog = new Dialog(this, R.style.AlertDialogTheme);
            dialog.setContentView(R.layout.bufferlist_share);

            BufferListAdapter adapter = new BufferListAdapter();
            ((RecyclerView) dialog.findViewById(R.id.recycler)).setAdapter(adapter);
            dialog.findViewById(R.id.recycler).setBackgroundColor(P.colorPrimary);
            adapter.onBuffersChanged();
            dialog.setCanceledOnTouchOutside(true);
            dialog.setCancelable(true);
            dialog.setOnDismissListener(this);
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
        final String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        Intent intent = new Intent(getApplicationContext(), WeechatActivity.class);
        intent.putExtra(NOTIFICATION_EXTRA_BUFFER_POINTER, pointer);
        intent.putExtra(NOTIFICATION_EXTRA_BUFFER_INPUT_TEXT, text);
        startActivity(intent);
        finish();
    }

    @Override public void onDismiss(DialogInterface dialog) {
        finish();
    }
}

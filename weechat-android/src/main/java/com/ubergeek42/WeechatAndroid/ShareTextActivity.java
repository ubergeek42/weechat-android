package com.ubergeek42.WeechatAndroid;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.widget.Toast;

import com.ubergeek42.WeechatAndroid.adapters.BufferListAdapter;
import com.ubergeek42.WeechatAndroid.adapters.BufferListClickListener;
import com.ubergeek42.WeechatAndroid.service.Events;
import com.ubergeek42.WeechatAndroid.service.RelayService;

import org.greenrobot.eventbus.EventBus;

import static com.ubergeek42.WeechatAndroid.utils.Constants.*;

public class ShareTextActivity extends AppCompatActivity implements
        DialogInterface.OnDismissListener, BufferListClickListener {

    @Override
    protected void onStart() {
        super.onStart();

        if (!EventBus.getDefault().getStickyEvent(Events.StateChangedEvent.class).state.contains(RelayService.STATE.LISTED)) {
            Toast.makeText(getApplicationContext(), getString(R.string.not_connected), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent intent = getIntent();
        if ((Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType()))) {
            Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.bufferlist_share);

            BufferListAdapter adapter = new BufferListAdapter();
            adapter.attach(this);
            ((RecyclerView) dialog.findViewById(R.id.recycler)).setAdapter(adapter);
            adapter.onBuffersChanged();
            dialog.setCanceledOnTouchOutside(true);
            dialog.setCancelable(true);
            dialog.setOnDismissListener(this);
            dialog.show();
        }
    }

    @Override public void onBufferClick(String fullName) {
        final String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        Intent intent = new Intent(getApplicationContext(), WeechatActivity.class);
        intent.putExtra(NOTIFICATION_EXTRA_BUFFER_FULL_NAME, fullName);
        intent.putExtra(NOTIFICATION_EXTRA_BUFFER_INPUT_TEXT, text);
        startActivity(intent);
    }

    @Override public void onDismiss(DialogInterface dialog) {
        finish();
    }
}

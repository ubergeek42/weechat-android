package com.ubergeek42.WeechatAndroid;

import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.ubergeek42.WeechatAndroid.adapters.BufferListAdapter;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.service.Events;
import com.ubergeek42.WeechatAndroid.service.RelayService;

import de.greenrobot.event.EventBus;

public class ShareTextActivity extends AppCompatActivity implements DialogInterface.OnClickListener,
        DialogInterface.OnDismissListener, DialogInterface.OnShowListener {

    BufferListAdapter bufferlistAdapter;
    AlertDialog dialog;

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
            bufferlistAdapter = new BufferListAdapter(this).preventFilter();
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setAdapter(bufferlistAdapter, this)
                    .setTitle(getString(R.string.share_text_title));
            dialog = builder.create();
            dialog.setOnShowListener(this);
            dialog.setOnDismissListener(this);
            dialog.show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (dialog != null) {
            dialog.setOnDismissListener(null);      // prevents closing the activity on rotate
            dialog.dismiss();                       // prevents window leaks
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        Buffer buffer = bufferlistAdapter.getItem(which);
        if (buffer != null) {
            final String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            Intent intent = new Intent(getApplicationContext(), WeechatActivity.class);
            intent.putExtra(WeechatActivity.EXTRA_NAME, buffer.fullName);
            intent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(intent);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        finish();
    }

    @Override
    public void onShow(DialogInterface dialog) {
        bufferlistAdapter.onBuffersChanged();
    }
}

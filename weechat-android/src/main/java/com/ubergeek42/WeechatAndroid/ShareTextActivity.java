package com.ubergeek42.WeechatAndroid;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import com.ubergeek42.WeechatAndroid.adapters.BufferListAdapter;
import com.ubergeek42.WeechatAndroid.relay.Buffer;

public class ShareTextActivity extends AppCompatActivity {

    String text;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (!"text/plain".equals(type)) {
                return;
            } else {

                text = intent.getStringExtra(intent.EXTRA_TEXT);
                final BufferListAdapter bufferlistAdapter = new BufferListAdapter(this);

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setAdapter(bufferlistAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int position) {
                        Buffer buffer = bufferlistAdapter.getItem(position);
                        if (buffer == null) return;

                        ShareTextActivity.this.openBufferWithText(buffer);
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.setTitle("Share with");
                dialog.setOnShowListener(bufferlistAdapter);
                dialog.show();

            }
        }
    }

    protected void openBufferWithText(Buffer buffer) {
        Intent intent = new Intent(getApplicationContext(), WeechatActivity.class);
        intent.putExtra("full_name", buffer.fullName);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(intent);
    }
}

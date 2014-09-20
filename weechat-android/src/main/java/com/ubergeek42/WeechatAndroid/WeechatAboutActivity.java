/*******************************************************************************
 * Copyright 2012 Keith Johnson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.WeechatAndroid;

import java.io.File;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class WeechatAboutActivity extends Activity implements OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);
        TextView tv = (TextView) this.findViewById(R.id.versionID);
        tv.setText(com.ubergeek42.WeechatAndroid.BuildConfig.VERSION_BANNER);

        tv = (TextView) this.findViewById(R.id.versionStr);
        tv.setText("WeechatAndroid v" + com.ubergeek42.WeechatAndroid.BuildConfig.VERSION_NAME);
        
        Button clearCerts = (Button)findViewById(R.id.btn_clear_certs);
        clearCerts.setOnClickListener(this);
    }

    @Override
    public void onClick(View arg0) {
        File keystoreFile = new File(getDir("sslDir", Context.MODE_PRIVATE), "keystore.jks");
        keystoreFile.delete();
        Toast.makeText(this, "SSL Certs removed, please restart Weechat-Android", Toast.LENGTH_LONG).show();
    }

}

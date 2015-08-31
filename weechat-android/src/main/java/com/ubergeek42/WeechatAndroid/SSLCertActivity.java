package com.ubergeek42.WeechatAndroid;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Html;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;

public class SSLCertActivity extends Activity {
    private static Logger logger = LoggerFactory.getLogger("SSLCertActivity");
    final private static boolean DEBUG = true;


    private TextView certDetails;
    private boolean mBound = false;
    private RelayServiceBinder rsb;
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the layout
        setContentView(R.layout.sslcertviewer);
        
        certDetails = (TextView) findViewById(R.id.cert_details);
        accept = (Button) findViewById(R.id.accept_cert);
        reject = (Button) findViewById(R.id.reject_cert);
        accept.setEnabled(false);
        reject.setEnabled(false);
        accept.setOnClickListener(acceptListener);
        reject.setOnClickListener(rejectListener);
    }
    @Override
    protected void onStart() {
        super.onStart();

        // Bind to the Relay Service
        if (DEBUG) logger.debug("...calling bindService()");
        bindService(new Intent(this, RelayService.class), mConnection, Context.BIND_AUTO_CREATE);
    }
    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            if (DEBUG) logger.debug("...calling unbindService()");
            unbindService(mConnection);
            mBound = false;
        }
    }
    
    private OnClickListener acceptListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            rsb.acceptCertificate();
            rsb.connect();
            finish();
        }
    };
    private OnClickListener rejectListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            rsb.rejectCertificate();
            finish();
        }
    };
    
    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rsb = (RelayServiceBinder) service;
            mBound = true;
            
            showDetails(rsb.getCertificateError());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            rsb = null;
        }
    };
    private Button reject;
    private Button accept;
    
    public void showDetails(X509Certificate cert) {
        if (cert==null) {
            certDetails.setText("Error loading cert");            
            return;
        }
        StringBuilder details = new StringBuilder();
        details.append("<b>Issued to:</b><br>");
        details.append(cert.getSubjectDN().getName() + "<br><br>");
        
        details.append("<b>Issued by:</b><br>");
        details.append(cert.getIssuerDN().getName() + "<br><br>");
        
        details.append("<b>Validity Period:</b><br>");
        details.append("Issued On:  " + cert.getNotBefore() + "<br>");
        details.append("Expires On: " + cert.getNotAfter() + "<br><br>");
        
        details.append("<b>SHA-1 Fingerprint:</b><br>");
        try {
            String fingerprint = new String(Hex.encodeHex(DigestUtils.sha1(cert.getEncoded())));
            details.append(fingerprint);
        }catch (CertificateEncodingException e) {
            details.append("Unknown");
        }
        
        certDetails.setText(Html.fromHtml(details.toString()),TextView.BufferType.SPANNABLE);
        accept.setEnabled(true);
        reject.setEnabled(true);
    }
}

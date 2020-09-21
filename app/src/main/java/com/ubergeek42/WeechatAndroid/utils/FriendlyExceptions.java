package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.weechat.relay.connection.SSHConnection;
import com.ubergeek42.weechat.relay.connection.SSHServerKeyVerifier;

import java.util.ArrayList;
import java.util.Arrays;

import static com.ubergeek42.WeechatAndroid.media.Cache.findException;
import static com.ubergeek42.WeechatAndroid.utils.ThrowingKeyManagerWrapper.ClientCertificateMismatchException;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSL_CLIENT_CERTIFICATE;
import static com.ubergeek42.WeechatAndroid.utils.Utils.join;
import static com.ubergeek42.weechat.relay.connection.SSHServerKeyVerifier.HostKeyNotVerifiedException.makeSha2Fingerprint;

public class FriendlyExceptions {
    final Context context;

    public FriendlyExceptions(Context context) {
        this.context = context;
    }

    public static class Result {
        final public String message;
        final public boolean shouldStopConnecting;

        public Result(String message, boolean shouldStopConnecting) {
            this.message = message;
            this.shouldStopConnecting = shouldStopConnecting;
        }
    }

    public Result getFriendlyException(Exception e) {
        if (e instanceof ClientCertificateMismatchException)
            return getFriendlyException((ClientCertificateMismatchException) e);

        if (e instanceof SSHConnection.FailedToAuthenticateWithPasswordException)
            return getFriendlyException((SSHConnection.FailedToAuthenticateWithPasswordException) e);
        if (e instanceof SSHConnection.FailedToAuthenticateWithKeyException)
            return getFriendlyException((SSHConnection.FailedToAuthenticateWithKeyException) e);

        Exception ee = findException(e, SSHServerKeyVerifier.VerifyException.class);
        if (ee != null) {
            if (ee instanceof SSHServerKeyVerifier.UnknownHostKeyException)
                return getFriendlyException((SSHServerKeyVerifier.UnknownHostKeyException) ee);
            if (ee instanceof SSHServerKeyVerifier.HostKeyNotVerifiedException)
                return getFriendlyException((SSHServerKeyVerifier.HostKeyNotVerifiedException) ee);
        }

        return new Result(getJoinedExceptionString(e).toString(), false);
    }

    public Result getFriendlyException(ClientCertificateMismatchException e) {
        boolean certificateIsSet = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_SSL_CLIENT_CERTIFICATE, null) != null;
        String message = context.getString(certificateIsSet ?
                        R.string.error_client_certificate_mismatch :
                        R.string.error_client_certificate_not_set,
                join(", ", Arrays.asList(e.keyType)),
                join(", ", Arrays.asList(e.issuers)));
        return new Result(message, true);
    }

    public Result getFriendlyException(SSHConnection.FailedToAuthenticateWithPasswordException e) {
        String message = context.getString(R.string.exceptions_ssh_failed_to_authenticate_with_password);
        return new Result(message, true);
    }

    public Result getFriendlyException(SSHConnection.FailedToAuthenticateWithKeyException e) {
        String message = context.getString(R.string.exceptions_ssh_failed_to_authenticate_with_key);
        return new Result(message, true);
    }

    public Result getFriendlyException(SSHServerKeyVerifier.UnknownHostKeyException e) {
        String message = context.getString(
                R.string.exceptions_ssh_hostname_not_present_in_known_hosts, e.hostname);
        return new Result(message, true);
    }

    public Result getFriendlyException(SSHServerKeyVerifier.HostKeyNotVerifiedException e) {
        String fingerprint;
        try {
            fingerprint = makeSha2Fingerprint(e.key);
        } catch (Exception ee) {
            ee.printStackTrace();
            fingerprint = "n/a";
        }

        String message = context.getString(R.string.exceptions_ssh_hostkey_not_verified,
                e.hostname, e.algorithm, fingerprint);
        return new Result(message, true);
    }

    @SuppressWarnings("ConstantConditions")
    public CharSequence getJoinedExceptionString(Throwable t) {
        ArrayList<CharSequence> messages = new ArrayList<>();
        while (t != null) {
            String message = t.getMessage();
            if (TextUtils.isEmpty(message)) message = t.getClass().getSimpleName();
            if (!message.endsWith(".")) message += ".";
            messages.add(message);
            t = t.getCause();
        }
        return join(" ", messages);
    }
}

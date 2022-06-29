package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.weechat.relay.connection.SSHConnection;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

import static com.ubergeek42.WeechatAndroid.media.Cache.findException;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSL_CLIENT_CERTIFICATE;
import static com.ubergeek42.WeechatAndroid.utils.ThrowingKeyManagerWrapper.ClientCertificateMismatchException;
import static com.ubergeek42.WeechatAndroid.utils.Utils.join;

public class FriendlyExceptions {
    final Context context;

    public FriendlyExceptions(Context context) {
        this.context = context;
    }

    public static class Result {
        final public @NonNull String message;
        final public boolean shouldStopConnecting;

        public Result(@NonNull String message, boolean shouldStopConnecting) {
            this.message = message;
            this.shouldStopConnecting = shouldStopConnecting;
        }
    }

    public Result getFriendlyException(Throwable e) {
        ClientCertificateMismatchException clientCertificateMismatchException =
                findException(e, ClientCertificateMismatchException.class);
        if (clientCertificateMismatchException != null)
            return getFriendlyException(clientCertificateMismatchException);

        if (e instanceof UnknownHostException)
            return getFriendlyException((UnknownHostException) e);

        if (e instanceof SSHConnection.FailedToAuthenticateWithPasswordException)
            return getFriendlyException((SSHConnection.FailedToAuthenticateWithPasswordException) e);
        if (e instanceof SSHConnection.FailedToAuthenticateWithKeyException)
            return getFriendlyException((SSHConnection.FailedToAuthenticateWithKeyException) e);

        return new Result(getJoinedExceptionString(e).toString(), false);
    }

    public Result getFriendlyException(ClientCertificateMismatchException e) {
        boolean certificateIsSet = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_SSL_CLIENT_CERTIFICATE, null) != null;
        String message = context.getString(certificateIsSet ?
                        R.string.error__connection__ssl__client_certificate__certificate_mismatch :
                        R.string.error__connection__ssl__client_certificate__not_set,
                join(", ", Arrays.asList(e.keyType)),
                e.issuers == null? "*" : join(", ", Arrays.asList(e.issuers)));
        return new Result(message, true);
    }

    public Result getFriendlyException(SSHConnection.FailedToAuthenticateWithPasswordException e) {
        String message = context.getString(R.string.error__connection__ssh__failed_to_authenticate_with_password);
        return new Result(message, true);
    }

    public Result getFriendlyException(SSHConnection.FailedToAuthenticateWithKeyException e) {
        String message = context.getString(R.string.error__connection__ssh__failed_to_authenticate_with_key);
        return new Result(message, true);
    }

    // todo extract string
    public Result getFriendlyException(UnknownHostException e) {
        String message = "Unknown host: " + e.getMessage();
        return new Result(message, false);
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

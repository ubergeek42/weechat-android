package androidx.preference;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.service.SSLHandler;
import com.ubergeek42.WeechatAndroid.utils.SecurityUtils;

public class CertPickerPreference extends PasswordedFilePickerPreference implements DialogFragmentGetter {
    public CertPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected String saveData(@Nullable byte[] bytes, @NonNull String password) throws Exception {
        SSLHandler.getInstance(getContext()).setClientCertificate(bytes, password);
        persistString(bytes != null ? "ok" : null);
        notifyChanged();
        if (bytes == null) {
            return "Certificate forgotten";
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return SecurityUtils.areAllInsideSecurityHardware(SSLHandler.KEYSTORE_ALIAS_PREFIX) ?
                    "Certificate imported into security hardware" :
                    "Certificate imported into a software key store";
        } else {
            return "Certificate imported";
        }
    }
}

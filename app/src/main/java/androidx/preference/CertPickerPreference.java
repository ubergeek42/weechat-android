package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.SSLHandler;
import com.ubergeek42.WeechatAndroid.service.SSLHandlerKt;
import com.ubergeek42.WeechatAndroid.utils.TinyMap;

import static com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.InsideSecurityHardware;
import static com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.areAllInsideSecurityHardware;

public class CertPickerPreference extends PasswordedFilePickerPreference implements DialogFragmentGetter {
    public CertPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected String saveData(@Nullable byte[] bytes, @NonNull String password) throws Exception {
        Context context = getContext();
        SSLHandlerKt.setClientCertificate(bytes, password);
        persistString(bytes != null ? "ok" : null);
        notifyChanged();
        if (bytes == null) {
            return context.getString(R.string.pref__CertPickerPreference__success_certificate_forgotten);
        }

        return context.getString(TinyMap.of(
                InsideSecurityHardware.YES, R.string.pref__CertPickerPreference__success_stored_inside_security_hardware_yes,
                InsideSecurityHardware.NO, R.string.pref__CertPickerPreference__success_stored_inside_security_hardware_no,
                InsideSecurityHardware.CANT_TELL, R.string.pref__CertPickerPreference__success_stored_inside_security_hardware_yes_cant_tell
        ).get(areAllInsideSecurityHardware(SSLHandlerKt.KEYSTORE_ALIAS_PREFIX)));
    }
}

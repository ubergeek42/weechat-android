package android.support.v7.preference;

import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.utils.Constants;

public class RingtonePreferenceFix extends DialogPreference {

    public RingtonePreferenceFix(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public @NonNull String getRingtonePath() {
        return getSharedPreferences().getString(getKey(), Constants.PREF_NOTIFICATION_SOUND_D);
    }

    public void setRingtonePath(String path) {
        getSharedPreferences().edit().putString(getKey(), path).commit();
        notifyChanged();
    }

    // this and the following methods are only here to keep activity's code cleaner
    // since we cannot act as a receiver of startActivityForResult(),
    // we at least prepare the intent here..
    public @NonNull Intent makeRingtoneRequestIntent() {
        String path = getRingtonePath();
        Uri uri = !TextUtils.isEmpty(path) ? Uri.parse(path) : null;
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        return intent;
    }

    // ..and deal with the result here
    public void onActivityResult(@NonNull Intent intent) {
        Uri uri = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        setRingtonePath(uri == null ? "": uri.toString());
    }

    @Override public CharSequence getSummary() {
        String tmp = getRingtonePath();
        if ("".equals(tmp)) return getContext().getString(R.string.pref_ringtone_none);
        else {
            Ringtone ringtone = RingtoneManager.getRingtone(getContext(), Uri.parse(tmp));
            return (ringtone == null) ? getContext().getString(R.string.pref_ringtone_unknown) : ringtone.getTitle(getContext());
        }
    }
}
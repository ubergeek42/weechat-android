// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package androidx.preference;

import android.content.Context;
import android.content.Intent;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

public class FilePreference extends DialogPreference implements DialogFragmentGetter {
    final private static @Root Kitty kitty = Kitty.make();

    final static String DEFAULT_SUCCESSFULLY_SET = "File imported. You can delete it now";
    final static String DEFAULT_SUCCESSFULLY_CLEARED = "Cleared";

    public FilePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public CharSequence getSummary() {
        final String set_not_set = getContext().getString(Utils.isEmpty(getData(getPersistedString(null))) ? R.string.pref_file_not_set : R.string.pref_file_set);
        return getContext().getString(R.string.pref_file_summary,
                super.getSummary(), set_not_set);
    }

    protected String saveData(@Nullable byte[] bytes) throws Exception {
        if (callChangeListener(bytes)) {
            persistString(bytes == null ? null : Base64.encodeToString(bytes, Base64.NO_WRAP));
            notifyChanged();
        }
        return bytes == null ? DEFAULT_SUCCESSFULLY_CLEARED : DEFAULT_SUCCESSFULLY_SET;
    }

    public static @Nullable byte[] getData(String data) {
        try {return Base64.decode(data.getBytes(), Base64.NO_WRAP);}
        catch (IllegalArgumentException | NullPointerException ignored) {return null;}
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // this gets called when a file has been picked
    public void onActivityResult(@NonNull Intent intent) {
        runAndShowToast(() -> saveData(Utils.readFromUri(getContext(), intent.getData())));
    }

    @NonNull @Override public DialogFragment getDialogFragment() {
        return new FilePreferenceFragment();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class FilePreferenceFragment extends PreferenceDialogFragmentCompat {
        @Override protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            FilePreference preference = (FilePreference) getPreference();
            builder.setNeutralButton(getString(R.string.pref_file_clear_button), (dialog, which) ->
                    runAndShowToast(() -> preference.saveData(null)))
                .setNegativeButton(getString(R.string.pref_file_paste_button), (dialog, which) -> {
                    // noinspection deprecation
                    ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    CharSequence clip = cm.getText();
                    if (TextUtils.isEmpty(clip))
                        Weechat.showShortToast(R.string.pref_file_empty_clipboard);
                    else {
                        runAndShowToast(() -> preference.saveData(clip.toString().getBytes()));
                    }
                })
                .setPositiveButton(getString(R.string.pref_file_choose_button), (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    //noinspection ConstantConditions   -- both target fragment and arguments are set
                    getTargetFragment().startActivityForResult(intent, getArguments().getInt("code"));
                });
        }

        @Override public void onDialogClosed(boolean b) {}
    }

    interface ThrowingRunnable {String run() throws Exception;}
    public static void runAndShowToast(ThrowingRunnable runnable) {
        try {
            String s = runnable.run();
            Weechat.showLongToast(s);
        } catch (Exception e) {
            Weechat.showLongToast(R.string.pref_file_error, e.getMessage());
            kitty.error("error", e);
        }
    }
}

package androidx.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiSelectListPreferenceWithSummary extends MultiSelectListPreference {
    final private @Nullable String emptySummary;

    final private static @Root Kitty kitty = Kitty.make();

    @Cat
    public MultiSelectListPreferenceWithSummary(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MultiSelectListPreferenceWithSummary, 0, 0);
        emptySummary = a.getString(R.styleable.MultiSelectListPreferenceWithSummary_emptySummary);
        a.recycle();
    }


    @Override public CharSequence getSummary() {
        String summary = super.getSummary().toString();
        Set<String> values = getSharedPreferences().getStringSet(getKey(), Collections.emptySet());

        Set<Integer> valueIndices = new HashSet<>();
        for (String value : values) valueIndices.add(findIndexOfValue(value));

        List<CharSequence> labels = new ArrayList<>();
        int i = 0;
        for (CharSequence entry: getEntries()) if (valueIndices.contains(i++)) labels.add(entry);

        CharSequence valueString = Utils.join(", ", labels);
        return TextUtils.isEmpty(valueString) ? emptySummary : String.format(summary, valueString);
    }
}

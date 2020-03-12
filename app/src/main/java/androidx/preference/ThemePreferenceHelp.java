package androidx.preference;

import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;

public class ThemePreferenceHelp extends Preference {

    public ThemePreferenceHelp(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public CharSequence getSummary() {
        StringBuilder sb = new StringBuilder();
        for (String p: ThemeManager.getThemeSearchDirectories(getContext()))
            sb.append("<br>&nbsp;&nbsp;&nbsp;&nbsp;").append(p);

        return Html.fromHtml(getContext().getString(R.string.pref_theme_help, sb));
    }

    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        summary.setMovementMethod(LinkMovementMethod.getInstance());
        summary.setMaxHeight(Integer.MAX_VALUE);
    }
}

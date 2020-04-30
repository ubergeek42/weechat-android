package androidx.preference;

import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.widget.TextView;

@SuppressWarnings({"unused", "WeakerAccess"})
public class HelpPreference extends Preference {
    public HelpPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public HelpPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public HelpPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HelpPreference(Context context) {
        super(context);
    }

    @Override public CharSequence getSummary() {
        return Html.fromHtml(super.getSummary().toString());
    }

    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        summary.setMovementMethod(LinkMovementMethod.getInstance());
        summary.setMaxHeight(Integer.MAX_VALUE);
    }
}

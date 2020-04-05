package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

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

    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        summary.setMaxHeight(Integer.MAX_VALUE);
    }
}

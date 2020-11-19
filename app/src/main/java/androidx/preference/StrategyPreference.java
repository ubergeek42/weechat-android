package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.media.Config;
import com.ubergeek42.WeechatAndroid.media.Strategy;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;

import java.util.ArrayList;
import java.util.List;

public class StrategyPreference extends FullScreenEditTextPreference {
    public StrategyPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Cat(exit = true) @Override public CharSequence getSummary() {
        Context context = getContext();

        Config.Info info = Config.parseConfigSafe(getText());
        if (info == null)
            return context.getString(R.string.pref__StrategyPreference__summary_error);

        String messageFilter = info.messageFilter != null ?
                context.getString(R.string.pref__StrategyPreference__message_filter_set) :
                context.getString(R.string.pref__StrategyPreference__message_filter_not_set);

        String lineFilters = info.lineFilters == null ?
                context.getString(R.string.pref__StrategyPreference__0_line_filters_set) :
                context.getResources().getQuantityString(R.plurals.pref__StrategyPreference__n_line_filters_set,
                        info.lineFilters.size(), info.lineFilters.size());

        String summaries;
        if (info.strategies == null) {
            summaries = context.getString(R.string.pref__StrategyPreference__strategies_not_loaded);
        } else {
            List<CharSequence> names = new ArrayList<>();
            for (Strategy s : info.strategies) names.add(s.getName());
            summaries = context.getString(R.string.pref__StrategyPreference__strategies_list, Utils.join(", ", names));
        }

        return context.getString(R.string.pref__StrategyPreference__summary, messageFilter, lineFilters, summaries);
    }

    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        summary.setMaxHeight(Integer.MAX_VALUE);
    }
}

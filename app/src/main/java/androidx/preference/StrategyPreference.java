package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

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
        String text = getText();
        Config.Info info;

        try {
            info = Config.parseConfig(text);
        } catch (Exception e) {
            return "Error";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Define the ways images are fetched from individual websites. You can use a " +
                "blacklist to skip certain websites or a whitelist to only access the websites you" +
                " trust.\n\n");

        sb.append(info.messageFilter != null ?
                "Message filter set" :
                "Message filter not set");

        sb.append("; ");
        sb.append(info.lineFilters == null ?
                "line filters not set" :
                info.lineFilters.size() + " line filter(s) set");

        sb.append(";\n\n");
        if (info.strategies == null) {
            sb.append("No strategies loaded");
        } else {
            sb.append("Strategies: ");
            List<CharSequence> names = new ArrayList<>();
            for (Strategy s : info.strategies) names.add(s.getName());
            sb.append(Utils.join(", ", names));
        }

        return sb.toString();
    }

    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        summary.setMaxHeight(Integer.MAX_VALUE);
    }
}

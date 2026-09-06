package androidx.preference

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.AttributeSet
import android.widget.TextView
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.media.Config
import com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_STRATEGIES
import com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_STRATEGIES_D
import com.ubergeek42.WeechatAndroid.views.snackbar.showSnackbar

class StrategyPreference(context: Context, attrs: AttributeSet?) :
        Preference(context, attrs) {

    override fun getSummary(): CharSequence {
        val info = Config.parseConfigSafe(getPersistedString(PREF_MEDIA_PREVIEW_STRATEGIES_D))
                ?: return context.getString(R.string.pref__StrategyPreference__summary_error)

        val messageFilter = if (info.messageFilter != null)
            context.getString(R.string.pref__StrategyPreference__message_filter_set) else
            context.getString(R.string.pref__StrategyPreference__message_filter_not_set)

        val lineFilters = if (info.lineFilters == null)
            context.getString(R.string.pref__StrategyPreference__0_line_filters_set) else
            context.resources.getQuantityString(
                R.plurals.pref__StrategyPreference__n_line_filters_set,
                info.lineFilters.size,
                info.lineFilters.size
            )

        val summaries = if (info.strategies == null) {
            context.getString(R.string.pref__StrategyPreference__strategies_not_loaded)
        } else {
            val commaSeparatedNames = info.strategies.joinToString { it.name }
            context.getString(R.string.pref__StrategyPreference__strategies_list, commaSeparatedNames)
        }

        return context.getString(R.string.pref__StrategyPreference__summary,
                                 messageFilter,
                                 lineFilters,
                                 summaries)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summary = holder.findViewById(android.R.id.summary) as TextView
        summary.setMaxHeight(Int.MAX_VALUE)
    }

    init {
        intent = Intent(context, StrategyPreferenceActivity::class.java)
    }

    // With dialogs, we can can call through to the preference to notify it of change.
    // With activities we can't, so simply watch for the change in shared preferences.
    val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == this.key) notifyChanged()
    }

    override fun onAttached() {
        super.onAttached()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(changeListener)
    }

    override fun onDetached() {
        super.onDetached()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(changeListener)
    }
}

class StrategyPreferenceActivity : FullScreenEditTextPreferenceActivity() {
    override val key = PREF_MEDIA_PREVIEW_STRATEGIES
    override val title get() = getString(R.string.pref__media_preview__strategies)
    override val defaultValue get() = getString(R.string.pref__media_preview__strategies_default)

    override fun validate(value: String) =
        try {
            Config.parseConfig(value) != null
            true
        } catch (e: Exception) {
            showSnackbar(e)
            false
        }
}

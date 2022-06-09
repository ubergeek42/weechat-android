package androidx.preference

import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog


@Suppress("LeakingThis")
abstract class ClearPreference(context: Context, attrs: AttributeSet?)
        : DialogPreference(context, attrs), DialogFragmentGetter {

    abstract val message: Int
    abstract val negativeButton: Int
    abstract val positiveButton: Int

    init { update() }

    abstract fun update()

    abstract fun clear()

    override fun getDialogFragment() = ClearCertPreferenceFragment()

    class ClearCertPreferenceFragment : PreferenceDialogFragmentCompat(), DialogInterface.OnClickListener {
        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)

            val preference = preference as ClearPreference

            builder.apply {
                setTitle(null)
                setMessage(getString(preference.message))
                setNegativeButton(getString(preference.negativeButton), null)
                setPositiveButton(getString(preference.positiveButton)) { _, _ -> preference.clear() }
            }
        }

        override fun onDialogClosed(b: Boolean) {
            (preference as ClearPreference).update()
        }
    }
}
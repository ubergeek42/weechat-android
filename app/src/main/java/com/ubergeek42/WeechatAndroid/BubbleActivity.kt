package com.ubergeek42.WeechatAndroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import com.ubergeek42.WeechatAndroid.databinding.BubbleActivityBinding
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment
import com.ubergeek42.WeechatAndroid.fragments.BufferFragmentContainer
import com.ubergeek42.WeechatAndroid.notifications.notifyBubbleActivityCreated
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.as0x
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.views.onSystemBarsAndImeInsetsChanged
import com.ubergeek42.WeechatAndroid.views.solidColor
import com.ubergeek42.WeechatAndroid.views.updateDimensions
import com.ubergeek42.cats.Cat
import com.ubergeek42.weechat.ColorScheme


class BubbleActivity : AppCompatActivity(), BufferFragmentContainer {
    private var bufferFragment: BufferFragment? = null

    lateinit var ui: BubbleActivityBinding

    @Cat override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.bubble_activity)
        ui = BubbleActivityBinding.bind(findViewById(android.R.id.content))

        ui.root.onSystemBarsAndImeInsetsChanged { insets ->
            ui.fragmentContainer.updatePadding(bottom = insets.bottom)
            ui.navigationPadding.updateDimensions(height = insets.bottom)
        }

        val pointer = intent?.getLongExtra(Constants.EXTRA_BUFFER_POINTER, -1) as Long

        BufferList.findByPointer(pointer)?.let { buffer ->
            val tag = "bubble:${pointer.as0x}"
            val alreadyAddedFragment = supportFragmentManager.findFragmentByTag(tag)

            bufferFragment = if (alreadyAddedFragment == null) {
                BufferFragment.newInstance(buffer.pointer).also {
                    supportFragmentManager.beginTransaction()
                            .add(R.id.fragment_container, it, tag)
                            .commit()
                }
            } else {
                alreadyAddedFragment as BufferFragment
            }

            notifyBubbleActivityCreated(pointer)
        }

        P.applyThemeAfterActivityCreation(this)
        P.storeThemeOrColorSchemeColors(this)
    }

    override fun onResume() {
        super.onResume()
        bufferFragment?.userVisibleHint = true
    }

    override fun onPause() {
        bufferFragment?.userVisibleHint = false
        super.onPause()
    }

    @Cat override fun onStart() {
        super.onStart()
        applyColorSchemeToViews()
    }

    @Cat override fun onStop() {
        super.onStop()
    }

    @Cat private fun applyColorSchemeToViews() {
        val chatBackgroundColor = ColorScheme.get().default_color[ColorScheme.OPT_BG].solidColor
        window.setBackgroundDrawable(chatBackgroundColor.toDrawable())
        ui.navigationPadding.setBackgroundColor(P.colorPrimaryDark)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onChatLinesScrolled(dy: Int, onTop: Boolean, onBottom: Boolean) {}
    override fun updateMenuItems() {}
    override val isPagerNoticeablyObscured = false

    override fun closeBuffer(pointer: Long) {
        finish()
    }
}

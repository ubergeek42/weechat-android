package com.ubergeek42.WeechatAndroid

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment
import com.ubergeek42.WeechatAndroid.fragments.BufferFragmentContainer
import com.ubergeek42.WeechatAndroid.notifications.notifyBubbleActivityCreated
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.views.solidColor
import com.ubergeek42.cats.Cat
import com.ubergeek42.weechat.ColorScheme


private val matchParentLayoutParams = FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.MATCH_PARENT
)


private val FRAME_LAYOUT_ID = View.generateViewId()


class BubbleActivity : AppCompatActivity(), BufferFragmentContainer {
    private var fullName = ""

    private var bufferFragment: BufferFragment? = null

    @Cat override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FrameLayout(this).apply {
            id = FRAME_LAYOUT_ID
            setContentView(this, matchParentLayoutParams)
        }

        fullName = intent?.getStringExtra(Constants.EXTRA_BUFFER_FULL_NAME) ?: ""

        BufferList.findByFullName(fullName)?.let { buffer ->
            val tag = "bubble:$fullName"
            val alreadyAddedFragment = supportFragmentManager.findFragmentByTag(tag)

            bufferFragment = if (alreadyAddedFragment == null) {
                BufferFragment.newInstance(buffer.pointer).also {
                    supportFragmentManager.beginTransaction()
                            .add(FRAME_LAYOUT_ID, it, tag)
                            .commit()
                }
            } else {
                alreadyAddedFragment as BufferFragment
            }

            notifyBubbleActivityCreated(fullName)
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
        window.setBackgroundDrawable(ColorDrawable(chatBackgroundColor))
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onChatLinesScrolled(dy: Int, onTop: Boolean, onBottom: Boolean) {}
    override fun updateMenuItems() {}
    override val isPagerNoticeablyObscured = false

    override fun closeBuffer(pointer: Long) {
        finish()
    }
}

package com.ubergeek42.WeechatAndroid

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment
import com.ubergeek42.WeechatAndroid.fragments.BufferFragmentContainer
import com.ubergeek42.WeechatAndroid.notifications.notifyBubbleActivityCreated
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.as0x
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.views.snackbar.BaseSnackbarBuilderProvider
import com.ubergeek42.WeechatAndroid.views.snackbar.SnackbarBuilder
import com.ubergeek42.WeechatAndroid.views.snackbar.SnackbarPositionController
import com.ubergeek42.WeechatAndroid.views.snackbar.setOrScheduleSettingAnchorAfterPagerChange
import com.ubergeek42.WeechatAndroid.views.solidColor
import com.ubergeek42.cats.Cat
import com.ubergeek42.weechat.ColorScheme


private val matchParentLayoutParams = FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.MATCH_PARENT
)


class BubbleActivity : AppCompatActivity(), BufferFragmentContainer, BaseSnackbarBuilderProvider {
    private var bufferFragment: BufferFragment? = null

    @Cat override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CoordinatorLayout(this).apply {
            id = R.id.coordinator_layout
            setContentView(this, matchParentLayoutParams)
        }

        val pointer = intent?.getLongExtra(Constants.EXTRA_BUFFER_POINTER, -1) as Long

        BufferList.findByPointer(pointer)?.let { buffer ->
            val tag = "bubble:${pointer.as0x}"
            val alreadyAddedFragment = supportFragmentManager.findFragmentByTag(tag)

            bufferFragment = if (alreadyAddedFragment == null) {
                BufferFragment.newInstance(buffer.pointer).also {
                    supportFragmentManager.beginTransaction()
                            .add(R.id.coordinator_layout, it, tag)
                            .commit()
                }
            } else {
                alreadyAddedFragment as BufferFragment
            }

            snackbarPositionController.setOrScheduleSettingAnchorAfterPagerChange(
                pointer, bufferFragment, supportFragmentManager
            )

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
        window.setBackgroundDrawable(ColorDrawable(chatBackgroundColor))
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onChatLinesScrolled(dy: Int, onTop: Boolean, onBottom: Boolean) {}
    override fun updateMenuItems() {}
    override val isPagerNoticeablyObscured = false

    override fun closeBuffer(pointer: Long) {
        finish()
    }

    private val snackbarPositionController = SnackbarPositionController()

    override val baseSnackbarBuilder: SnackbarBuilder = {
        snackbarPositionController.setSnackbar(this)
    }

}

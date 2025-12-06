@file:Suppress("PackageDirectoryMismatch")
package androidx.drawerlayout.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
import androidx.annotation.RequiresApi


@RequiresApi(34)
class OnBackAnimationAwareDrawerLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null)
        : DrawerLayout(context, attrs) {

    private fun getDrawer() = findDrawerWithGravity(Gravity.LEFT)

    private val onBackAnimationCallback = object : OnBackAnimationCallback {
        override fun onBackStarted(backEvent: BackEvent) {}

        override fun onBackProgressed(backEvent: BackEvent) {
            moveDrawerToOffset(getDrawer(), 1f - backEvent.progress)
            invalidate()
        }

        override fun onBackInvoked() {
            close()
        }

        override fun onBackCancelled() {
            open()
        }
    }

    init {
        addDrawerListener(object : DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {}
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

            // Not using `PRIORITY_OVERLAY`,
            // which is the default in `androidx.drawerlayout:drawerlayout:1.2.0`,
            // as IME uses `PRIORITY_DEFAULT` and having a higher priority
            // results in the drawer being closed before IME
            override fun onDrawerOpened(drawerView: View) {
                findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(PRIORITY_DEFAULT, onBackAnimationCallback)
            }

            override fun onDrawerClosed(drawerView: View) {
                findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(onBackAnimationCallback)
            }
        })
    }
}
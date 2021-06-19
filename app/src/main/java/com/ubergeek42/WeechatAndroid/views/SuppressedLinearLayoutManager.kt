@file:Suppress("PackageDirectoryMismatch")
package androidx.recyclerview.widget

import android.content.Context
import com.ubergeek42.WeechatAndroid.upload.suppress


// this is an attempt to dirty fix some very rare crashes in buffer list recycler view
// see https://github.com/ubergeek42/weechat-android/issues/512

// the overridden method suppresses:
//   * IndexOutOfBoundsException: Inconsistency detected. Invalid item position...
//   * IllegalArgumentException: view is not a child, cannot hide...
//   * RuntimeException: trying to unhide a view that was not hidden...
internal open class SuppressedLinearLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun layoutChunk(
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?,
        layoutState: LayoutState?,
        result: LayoutChunkResult?,
    ) {
        suppress<RuntimeException> {
            super.layoutChunk(recycler, state, layoutState, result)
        }
    }
}
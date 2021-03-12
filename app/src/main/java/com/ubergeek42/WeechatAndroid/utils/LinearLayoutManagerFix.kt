// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.utils

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager

internal open class LinearLayoutManagerFix(
    context: Context?,
    orientation: Int,
    reverseLayout: Boolean
) : LinearLayoutManager(context, orientation, reverseLayout) {

    // Disable predictive animations. There is a bug in RecyclerView which causes views that
    // are being reloaded to pull invalid ViewHolders from the internal recycler stack if the
    // adapter size has decreased since the ViewHolder was recycled.
    // http://stackoverflow.com/a/33985508/1449683
    override fun supportsPredictiveItemAnimations() = false
}
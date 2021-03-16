package com.ubergeek42.WeechatAndroid.utils

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback

fun DiffUtil.DiffResult.print(name: String) {
    println("diff result: $name")

    dispatchUpdatesTo(object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            println(":: inserting $count items at position $position")
        }

        override fun onRemoved(position: Int, count: Int) {
            println(":: removing $count items at position $position")
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            println(":: moving item from $fromPosition to $toPosition")
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            println(":: changing $count items starting from position $position")
        }
    })
}
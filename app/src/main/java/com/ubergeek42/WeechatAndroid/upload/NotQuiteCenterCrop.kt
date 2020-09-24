package com.ubergeek42.WeechatAndroid.upload

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.atan

private const val BEND = 0.8
private const val CEILING = 1.6

// this function slightly reduces a ratio that is >= 1
// the more ratio is, the more it is reduced

// `bend` determines how curvy is the curve
// it must be between 0 and 1; at 0 it's flat, at 1 it starts at a 45° angle

// ceiling determines the maximum theoretical value produced by the function
// it must be between 1 and inf; at 1 it's flat
@Suppress("SameParameterValue")
private fun reduce(ratio: Double, bend: Double, ceiling: Double): Double {
    val z = (ceiling - 1) * 2
    return 1 + atan(bend * PI / z * (ratio - 1)) * z / PI
}


// this is like center crop, but instead the longer side of the image is sli-i-ightly bigger
// than the target dimension. this would be something between center crop and the default behavior
class NotQuiteCenterCrop : BitmapTransformation() {
    override fun transform(pool: BitmapPool, bitmap: Bitmap, ow: Int, oh: Int): Bitmap {
        val dimensions = Dimensions(bitmap.width, bitmap.height)

        dimensions.whileRotatedToLandscape {
            var ratio = dimensions.width / dimensions.height.d
            ratio = reduce(ratio, BEND, CEILING)
            dimensions.width = (dimensions.height * ratio).i
        }

        return TransformationUtils.centerCrop(pool, bitmap, dimensions.width, dimensions.height)
    }

    override fun equals(other: Any?) = other is NotQuiteCenterCrop
    override fun hashCode() = ID.hashCode()
    override fun updateDiskCacheKey(messageDigest: MessageDigest) = messageDigest.update(ID_BYTES)

    companion object {
        private const val ID = "com.ubergeek42.WeechatAndroid.upload.NotQuiteCenterCrop"
        private val ID_BYTES = ID.toByteArray(CHARSET)
    }
}


private class Dimensions(var width: Int, var height: Int) {
    private fun rotate() {
        width = height.also { height = width }
    }

    fun whileRotatedToLandscape(f: () -> Unit) {
        if (width >= height) {
            f()
        } else {
            rotate()
            f()
            rotate()
        }
    }
}

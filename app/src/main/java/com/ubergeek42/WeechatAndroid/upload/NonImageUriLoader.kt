package com.ubergeek42.WeechatAndroid.upload

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey


// this a fallback loader for URIs that handles any URIs
class NonImageUriLoader : ModelLoader<Uri, Bitmap> {
    override fun buildLoadData(model: Uri, width: Int, height: Int, options: Options) =
            LoadData(ObjectKey(model), NonImageDataFetcher(model, width, height))

    override fun handles(model: Uri) = true

    class Factory : ModelLoaderFactory<Uri, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory) = NonImageUriLoader()
        override fun teardown() { /* no teardown */ }
    }
}


private const val BACKGROUND_COLOR = Color.WHITE
private const val TEXT_COLOR = Color.BLACK
private const val TEXT_SIZE_RATIO = 0.1625f
private val PADDING = 3.dp_to_px

class NonImageDataFetcher(
    val uri: Uri,
    private val thumbnailWidth: Int,
    private val thumbnailHeight: Int,
) : DataFetcher<Bitmap> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val layoutMaxWidth = thumbnailWidth - (PADDING * 2)
        val layoutMaxHeight = thumbnailHeight - (PADDING * 2)

        val text = Suri.getFileName(uri) ?: "file"

        val backgroundPaint = Paint()
        backgroundPaint.color = BACKGROUND_COLOR
        backgroundPaint.style = Paint.Style.FILL

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        textPaint.textSize = TEXT_SIZE_RATIO * thumbnailWidth
        textPaint.color = TEXT_COLOR

        val layout = StaticLayout(text, textPaint, layoutMaxWidth,
                Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
        val left = if (layout.maxLineWidth > layoutMaxWidth) PADDING else (thumbnailWidth - layout.maxLineWidth) / 2
        val top = if (layout.height > layoutMaxHeight) PADDING else (thumbnailHeight - layout.height) / 2

        val image = Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawPaint(backgroundPaint)
        canvas.translate(left.toFloat(), top.toFloat())
        layout.draw(canvas)

        callback.onDataReady(image)
    }

    override fun getDataClass() = Bitmap::class.java
    override fun getDataSource() = DataSource.LOCAL
    override fun cleanup() { /* no cleanup */ }
    override fun cancel() { /* no canceling */ }
}
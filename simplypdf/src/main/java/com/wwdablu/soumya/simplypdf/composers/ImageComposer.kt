package com.wwdablu.soumya.simplypdf.composers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import com.bumptech.glide.Glide
import com.wwdablu.soumya.simplypdf.SimplyPdfDocument
import com.wwdablu.soumya.simplypdf.composers.properties.ImageProperties
import com.wwdablu.soumya.simplypdf.composers.properties.cell.Cell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


class ImageComposer(simplyPdfDocument: SimplyPdfDocument) : UnitComposer(simplyPdfDocument) {

    private var bitmapPainter: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun getTypeHandler(): String = "image"

    /**
     * Draw the bitmap into the document
     *
     * @param bmp Bitmap to be drawn into the document
     * @param properties ImageProperties to be used
     * @param xMargin Margin to be provided on the X-axis
     * @param yMargin Margin to be provided on the Y-axis
     */
    @JvmOverloads
    fun drawBitmap(bmp: Bitmap, properties: ImageProperties, xMargin: Int = 0, yMargin: Int = 0) {
        drawBitmap(bmp, properties, xMargin, yMargin, 0, null)
    }

    /**
     * Load an image from an URL and then add it to the document
     *
     * @param url URL to the image
     * @param context Context of either application or activity
     * @param properties ImageProperties to be used
     * @param xMargin Margin to be provided on the X-axis
     * @param yMargin Margin to be provided on the Y-axis
     */
    @JvmOverloads
    fun drawFromUrl(
        url: String,
        context: Context,
        properties: ImageProperties,
        xMargin: Int = 0,
        yMargin: Int = 0
    ) {

        val bitmap = runBlocking {
            withContext(CoroutineScope(Dispatchers.IO).coroutineContext) {
                Glide.with(context)
                    .asBitmap()
                    .load(url)
                    .submit()
                    .get()
            }
        }

        drawBitmap(bitmap, properties, xMargin, yMargin, 0, null)
    }

    /**
     * Draws the provided bitmap into the document
     *
     * @param bmp Bitmap to be drawn into the document
     * @param properties ImageProperties to be used
     * @param xMargin Margin to be provided on the X-axis
     * @param yMargin Margin to be provided on the Y-axis
     * @param xShift Shift the Image on the X-Axis from the start/left
     * @param cell The table cell inside which the image is to be drawn. Can be null.
     */
    internal fun drawBitmap(
        bmp: Bitmap,
        properties: ImageProperties,
        xMargin: Int,
        yMargin: Int,
        xShift: Int,
        cell: Cell?
    ) {

        //If recycled, do nothing
        if (bmp.isRecycled) {
            return
        }

        var bitmap = bmp
        val bmpSpacing = getTopSpacing(DEFAULT_SPACING)
        val isCellContent: Boolean = cell != null

        bitmap = if (cell == null) {
            scaleIfNeeded(bitmap, simplyPdfDocument.usablePageWidth)
        } else {
            scaleIfNeeded(bitmap, cell.getCellWidth() - (xMargin * 2))
        }

        val xTranslate = if (cell == null) {
            alignmentTranslationX(properties.alignment, bitmap.width)
        } else {
            cellAlignmentTranslateX(properties.alignment, cell, xMargin)
        }

        if (!canFitContentInPage(bitmap.height + DEFAULT_SPACING) &&
            simplyPdfDocument.pageContentHeight != simplyPdfDocument.topMargin
        ) {
            simplyPdfDocument.newPage()
            simplyPdfDocument.insertEmptyLines(1)
        }

        val canvasDensity = 72
        val targetDensity = simplyPdfDocument.documentInfo.dpi

        val canvas = pageCanvas
        canvas.density = canvasDensity
        canvas.save()
        canvas.translate(
            (if (isCellContent) 0 else simplyPdfDocument.startMargin) +
                    (xTranslate + xShift + xMargin).toFloat(),
            (if (isCellContent) (cell!!.getCellHeight() - bitmap.height) / 2 - (yMargin) else 0) + (simplyPdfDocument.pageContentHeight + yMargin).toFloat()
        )

        bitmap.density = targetDensity

        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val dst = RectF(
            0f,
            0f,
            bitmap.width * canvasDensity.toFloat() / targetDensity,
            bitmap.height * canvasDensity.toFloat() / targetDensity
        )
        canvas.drawBitmap(bitmap, null, dst, paint)

        if (bitmap != bmp) {
            bitmap.recycle()
        }
        canvas.restore()

        if (!isCellContent) {
            simplyPdfDocument.addContentHeight(bitmap.height + bmpSpacing + (yMargin * 2))
        }
    }

    internal fun getScaledDimension(bitmap: Bitmap, toWidth: Int): Pair<Int, Int> {

        //Check whether the original bitmap is within the bounds needed
        if (bitmap.width <= toWidth) return Pair(bitmap.width, bitmap.height)

        val useFactor: Float = bitmap.width.toFloat() / toWidth

        return Pair((bitmap.width / useFactor).toInt(), (bitmap.height / useFactor).toInt())
    }

    private fun scaleIfNeeded(bitmap: Bitmap, width: Int): Bitmap {

        val dimension = getScaledDimension(bitmap, width)

        //Check whether scaling is needed or not
        if (bitmap.width <= width) return bitmap

        return Bitmap.createScaledBitmap(bitmap, dimension.first, dimension.second, true)
    }
}
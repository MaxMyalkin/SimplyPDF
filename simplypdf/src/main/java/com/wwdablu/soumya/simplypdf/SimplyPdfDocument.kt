package com.wwdablu.soumya.simplypdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import androidx.annotation.ColorInt
import com.wwdablu.soumya.simplypdf.composers.ImageComposer
import com.wwdablu.soumya.simplypdf.composers.TableComposer
import com.wwdablu.soumya.simplypdf.composers.TextComposer
import com.wwdablu.soumya.simplypdf.document.DocumentInfo
import com.wwdablu.soumya.simplypdf.document.PageModifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * Allows the developer to modify the PDF document. An instance of this can be obtained by using
 * the [SimplyPdf] class.
 */
class SimplyPdfDocument internal constructor(
    internal val context: Context,
    private val document: File
) {
    val documentInfo: DocumentInfo = DocumentInfo()

    var lineHeight = 10
        set(value) {
            field = if(value <= 0) 10 else value
        }

    val text: TextComposer by lazy { TextComposer(this) }
    val image: ImageComposer by lazy { ImageComposer(this) }
    val table: TableComposer by lazy { TableComposer(this) }

    internal val pageModifiers: LinkedList<PageModifier> = LinkedList()

    private lateinit var pdfDocument: PrintedPdfDocument
    private lateinit var printAttributes: PrintAttributes

    /**
     * Returns the current page being used
     * @return Current page object
     */
    lateinit var currentPage: PdfDocument.Page
        private set

    /**
     * Returns the current page number on which content is being drawn.
     * @return Current page number
     */
    var currentPageNumber = 0
        private set

    /**
     * Returns the height of the content written in the current page.
     * @return Height of content for the current page
     */
    var pageContentHeight = 0
        private set

    private var finished = false

    fun insertEmptySpace(height: Int) {
        addContentHeight(height)
    }

    fun insertEmptyLines(count: Int) {
        insertEmptySpace(lineHeight * if(count < 0) 0 else count)
    }

    /**
     * Set the background color of the current page. It will apply it to the entire page.
     * @param color Color to be applied
     */
    private fun setPageBackgroundColor(@ColorInt color: Int) {
        ensureNotFinished()
        val canvas = currentPage.canvas
        canvas.save()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        canvas.drawRect(RectF(0F, 0F, pdfDocument.pageWidth.toFloat(),
                pdfDocument.pageHeight.toFloat()), paint)
        canvas.restore()
    }

    /**
     * Complete all the tasks and write the PDF to the location provided.
     */
    suspend fun finish() : Boolean {
        return withContext(Dispatchers.IO) {
            val result = runCatching {
                ensureNotFinished()
                pdfDocument.finishPage(currentPage)
                pdfDocument.writeTo(FileOutputStream(document))
                pdfDocument.close()
                finished = true
            }
            result.isSuccess
        }
    }

    internal fun build(context: Context, pageBgColor: Int = Color.WHITE) {
        printAttributes = PrintAttributes.Builder()
            .setColorMode(documentInfo.resolveColorMode())
            .setMediaSize(documentInfo.paperSize)
            .setResolution(
                PrintAttributes.Resolution(
                    "resolutionId",
                    "label",
                    documentInfo.dpi,
                    documentInfo.dpi
                )
            )
            .setMinMargins(documentInfo.margins.getMargin())
            .build()
        pdfDocument = PrintedPdfDocument(context, printAttributes)
        currentPage = pdfDocument.startPage(currentPageNumber)
        pageContentHeight += topMargin
        setPageBackgroundColor(pageBgColor)
        applyPageModifiers()
    }

    val startMargin: Int
        get() = if (!this::printAttributes.isInitialized) 0 else printAttributes.minMargins?.leftMils ?: 0
    val topMargin: Int
        get() = if (!this::printAttributes.isInitialized) 0 else printAttributes.minMargins?.topMils ?: 0
    val endMargin: Int
        get() = if (!this::printAttributes.isInitialized) 0 else printAttributes.minMargins?.rightMils ?: 0
    val bottomMargin: Int
        get() = if (!this::printAttributes.isInitialized) 0 else printAttributes.minMargins?.bottomMils ?: 0

    /**
     * Creates a new page in the PDF document and resets the internal markers on the document.
     *
     * @param color Specify the color you want to set. Default is white.
     * @return The new page on which content will be drawn
     */
    fun newPage(@ColorInt color: Int = Color.WHITE) {
        ensureNotFinished()
        pdfDocument.finishPage(currentPage)
        currentPageNumber++
        currentPage = pdfDocument.startPage(currentPageNumber)
        pageContentHeight = topMargin
        setPageBackgroundColor(color)
        applyPageModifiers()
    }

    /**
     * Adds the height of the content drawn currently to the total height of content already
     * present in the current page. If the total content height crosses the usable limit, then a
     * new page will be inserted.
     * @param pageContentHeight Page Content Height
     */
    fun addContentHeight(pageContentHeight: Int) {
        this.pageContentHeight += pageContentHeight

        //If the page content height crosses the height limit, create new page
        if(this.pageContentHeight >= usablePageHeight) {
            newPage()
        }
    }

    /**
     * Returns the height of the page on which content can be displayed. It taken into account the
     * margins on the page.
     * @return Usable height of the page
     */
    val usablePageHeight: Int
        get() = currentPage.info.pageHeight - (topMargin + bottomMargin)

    /**
     * Returns the width of the page on which content can be displayed. It taken into account the
     * margins on the page.
     * @return Usable width of the page
     */
    val usablePageWidth: Int
        get() = currentPage.info.pageWidth - (startMargin + endMargin)

    private fun ensureNotFinished() {
        check(!finished) { "Cannot use as finish has been called." }
    }

    private fun applyPageModifiers() {
        pageModifiers.forEach {
            it.render(this)
        }
    }
}
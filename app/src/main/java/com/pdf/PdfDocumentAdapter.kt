package com.pdf

import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.*
import java.net.URL

class PdfDocumentAdapter(
    private val mType: TYPE,
    private val mData: String? = null,
    private val writeDocument: ((PdfDocument) -> Unit)? = null
) : PrintDocumentAdapter() {
    override fun onLayout(oldAttributes: PrintAttributes, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
        } else {
            val builder = PrintDocumentInfo.Builder("PdfApp.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(builder, newAttributes != oldAttributes)
        }
    }

    override fun onWrite(pages: Array<PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
        if (mType == TYPE.Document) {
            val pdfDocument = PdfDocument().apply { writeDocument?.invoke(this) }
            try {
                pdfDocument.writeTo(FileOutputStream(destination.fileDescriptor))
                pdfDocument.close()
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                } else {
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }
            } catch (e: IOException) {
                callback.onWriteFailed(e.message)
            }
        } else {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = if (mType == TYPE.URL) {
                        URL(mData).openStream()
                    } else {
                        FileInputStream(mData)
                    }
                    val outputStream = FileOutputStream(destination.fileDescriptor)
                    val bytes = ByteArray(16384)
                    var size = inputStream.read(bytes)
                    while (size >= 0 && !cancellationSignal.isCanceled) {
                        outputStream.write(bytes, 0, size)
                        size = inputStream.read(bytes)
                    }
                    if (cancellationSignal.isCanceled) {
                        callback.onWriteCancelled()
                    } else {
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    }
                    inputStream.close()
                    outputStream.close()
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }
    }

    enum class TYPE { Document, URL, File }
}
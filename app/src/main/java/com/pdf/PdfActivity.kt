package com.pdf

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.print.PrintManager
import android.view.View
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pdf.databinding.ActivityPdfBinding
import com.thinh.deptrai.PdfRendererView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/** for Google-url or SDK < 21, use WebView */
class PdfActivity : AppCompatActivity() {
    private lateinit var mBinding: ActivityPdfBinding
    private val mPdfUrl = "https://github.com/barteksc/AndroidPdfViewer/files/867321/testingcrashpdf.pdf"
    private var isUrl = true
    private val mPdfStatusListener = object : PdfRendererView.StatusCallBack {
        override fun onDisplay() {
            showLoading(false)
        }

        override fun onError(error: Throwable) {
            Toast.makeText(this@PdfActivity, error.message, Toast.LENGTH_SHORT).show()
            showLoading(false)
            mBinding.progressBarDownload.visibility = View.GONE
        }

        override fun onDownloadProgress(progress: Int) {
            mBinding.progressBarDownload.progress = progress
        }

        override fun onDownloadSuccess() {
            mBinding.progressBarDownload.visibility = View.GONE
            showLoading(true)
        }
    }
    private val startPdfFileForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        val data = result.data?.data ?: return@registerForActivityResult
        if (result.resultCode == -1) {
            if (data.scheme.equals("content", true)) {
                val file = File(cacheDir, data.path!!.split("/").last() + ".pdf") // or get MimeType
                file.createNewFile()
                FileOutputStream(file).let { outputStream ->
                    contentResolver.openInputStream(data)?.let { inputStream ->
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                    }
                    outputStream.flush()
                    outputStream.close()
                }
                if (file.exists()) {
                    showLoading(true)
                    isUrl = false
                    mBinding.pdfView.renderFile(file)
                    return@registerForActivityResult
                }
            } else {
                val cursor = contentResolver.query(data, arrayOf("_data"), null, null, null)
                if (cursor?.moveToFirst() == true) {
                    val index = cursor.getColumnIndex("_data")
                    val path = cursor.getString(index)
                    if (path != null) {
                        mBinding.pdfView.renderFile(File(path))
                        isUrl = false
                        showLoading(true)
                        return@registerForActivityResult
                    }
                }
                cursor?.close()
            }
            mPdfStatusListener.onError(Throwable("Pdf has been corrupted"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityPdfBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        mBinding.pdfView.setStatusListener(mPdfStatusListener).setRatio(2)

        mBinding.btnOpenUrl.setOnClickListener {
            mBinding.progressBarDownload.visibility = View.VISIBLE
            mBinding.pdfView.renderUrl(mPdfUrl)
            isUrl = true
        }
        mBinding.btnOpenFile.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            } else {
                openFile()
            }
        }
        mBinding.btnPrint.setOnClickListener {
            val documentAdapter = if (isUrl) {
                PdfDocumentAdapter(PdfDocumentAdapter.TYPE.URL, mPdfUrl)
//                PdfDocumentAdapter(PdfDocumentAdapter.TYPE.File, mBinding.pdfView.getFilePath())
            } else {
                PdfDocumentAdapter(PdfDocumentAdapter.TYPE.File, mBinding.pdfView.getFilePath())
            }
            (getSystemService(Context.PRINT_SERVICE) as PrintManager).print("MVVM PDF Print", documentAdapter, null)
        }
        mBinding.btnPrintScreen.setOnClickListener {
            print(mBinding.root)
        }

        intent.getParcelableExtra<Uri?>(Intent.EXTRA_STREAM)?.let {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
            } else {
                showLoading(true)
                isUrl = false
                mBinding.pdfView.renderFile(File(it.path!!))
            }
        }
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            GlobalScope.launch(Dispatchers.IO) {
                if ((URLUtil.isHttpUrl(it) || URLUtil.isHttpsUrl(it)) && URL(it).openConnection().getHeaderField("Content-Type") == "application/pdf") {
                    GlobalScope.launch(Dispatchers.Main) {
                        mBinding.progressBarDownload.visibility = View.VISIBLE
                        mBinding.pdfView.renderUrl(it)
                        isUrl = true
                    }
                }
            }
        }
    }

    private fun openFile() {
        startPdfFileForResult.launch(
            Intent.createChooser(
                Intent(Intent.ACTION_GET_CONTENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE), "Select Document"
            )
        )
    }

    private fun showLoading(show: Boolean) {
        mBinding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty()) {
            when (requestCode) {
                100 -> openFile()
                101 -> {
                    showLoading(true)
                    isUrl = false
                    mBinding.pdfView.renderFile(File(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!.path!!))
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mBinding.pdfView.closePdfRender()
    }

    // for multiple pages
    private fun print(view: View) {
        val viewWidth = view.width
        val viewHeight = view.height
        val pageHeight = (view.width * 1.4142).toInt()
        val pageCount = viewHeight / pageHeight
        val pageInfo = PdfDocument.PageInfo.Builder(viewWidth, pageHeight, 1).create()
        val bitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        val documentAdapter = PdfDocumentAdapter(PdfDocumentAdapter.TYPE.Document) {
            for (i in 0..pageCount) {
                val page = it.startPage(pageInfo)
                if (i < pageCount) {
                    page.canvas.drawBitmap(Bitmap.createBitmap(bitmap, 0, pageHeight * i, viewWidth, pageHeight), 0F, 0F, null)
                } else {
                    page.canvas.drawBitmap(Bitmap.createBitmap(bitmap, 0, pageHeight * i, viewWidth, viewHeight - pageHeight), 0F, 0F, null)
                }
                it.finishPage(page)
            }
        }
        (getSystemService(Context.PRINT_SERVICE) as PrintManager).print("PDF App Print", documentAdapter, null)
    }

    // for once page
//    private fun print(view: View) {
//        val pageInfo = PdfDocument.PageInfo.Builder(view.width, view.height, 1).create()
//        val documentAdapter = PdfDocumentAdapter(PdfDocumentAdapter.TYPE.Document) {
//            it.startPage(pageInfo).also { page ->
//                view.draw(page.canvas)
//                it.finishPage(page)
//            }
//        }
//        (getSystemService(Context.PRINT_SERVICE) as PrintManager).print("PDF App Print", documentAdapter, null)
//    }
}
package com.thinh.deptrai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.util.LruCache
import android.view.*
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL

class PdfRendererView(private val mContext: Context, attrs: AttributeSet?) : RecyclerView(mContext, attrs) {
    private val mAdapter = Adapter(mContext, null, 2F)
    private var mFilePath: String? = null

    init {
        setHasFixedSize(true)
        adapter = mAdapter
        layoutManager = LinearLayoutManager(mContext)
    }

    override fun setAdapter(adapter: RecyclerView.Adapter<*>?) {
        if (adapter is Adapter) {
            super.setAdapter(adapter)
        }
    }

    fun setStatusListener(listener: StatusListener?): PdfRendererView {
        mAdapter.listener = listener
        return this
    }

    fun setRatio(ratio: Float): PdfRendererView {
        mAdapter.ratio = if (ratio > 0) ratio else 1F
        return this
    }

    fun getFilePath() = mFilePath

    fun renderUrl(url: String) {
        mAdapter.listener?.onDownloadProgress(0)
        GlobalScope.launch(Dispatchers.IO) {
            val file = File(mContext.cacheDir, "temp.pdf")
            try {
                val bufferSize = 8192
                val connection = URL(url).openConnection().apply { connect() }
                val totalLength = connection.contentLength
                val bytesBuffer = ByteArray(bufferSize)
                var bytesCopied = 0
                var bytes: Int
                FileOutputStream(file).use { outputStream ->
                    BufferedInputStream(connection.getInputStream(), bufferSize).use { inputStream ->
                        while (inputStream.read(bytesBuffer).also { bytes = it } != -1) {
                            outputStream.write(bytesBuffer, 0, bytes)
                            bytesCopied += bytes
                            GlobalScope.launch(Dispatchers.Main) {
                                mAdapter.listener?.onDownloadProgress(bytesCopied * 100 / totalLength)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                GlobalScope.launch(Dispatchers.Main) { mAdapter.listener?.onError(e) }
                return@launch
            }
            GlobalScope.launch(Dispatchers.Main) {
                mAdapter.listener?.onDownloadSuccess()
                renderFile(file)
            }
        }
    }

    fun renderFile(file: File) {
        mFilePath = file.path
        mAdapter.renderFile(file)
    }

    fun closePdfRender() {
        mAdapter.closeRender()
    }

    private class Adapter(private val mContext: Context, var listener: StatusListener?, var ratio: Float) : RecyclerView.Adapter<Adapter.ViewHolder>() {
        private var mPdfRenderer: PdfRenderer? = null
        private var isDisplayed = false
        private val mSavedBitmap = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024).toInt() / 2) {
            override fun sizeOf(key: Int, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }

        fun renderFile(file: File) {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            if (descriptor.statSize > 0L) {
                try {
                    mPdfRenderer = PdfRenderer(descriptor)
                    isDisplayed = false
                } catch (e: IOException) {
                    mPdfRenderer = null
                    listener?.onError(Throwable("Pdf has been corrupted"))
                }
            } else {
                mPdfRenderer = null
                listener?.onError(Throwable("Pdf has been corrupted"))
            }
            mSavedBitmap.evictAll()
            notifyDataSetChanged()
        }

        fun closeRender() {
            mPdfRenderer?.close()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(mContext).inflate(R.layout.item_rcv_pdf_page, parent, false))
        }

        override fun getItemCount(): Int {
            return mPdfRenderer?.pageCount ?: 0
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            mSavedBitmap[position]?.let {
                holder.view.visibility = View.VISIBLE
                holder.view.findViewById<ImageView>(R.id.imvPage).setImageBitmap(it)
                return
            }
            holder.view.visibility = View.INVISIBLE
            GlobalScope.launch(Dispatchers.IO) {
                synchronized(mPdfRenderer!!) {
                    try {
                        mPdfRenderer!!.openPage(position)
                    } catch (e: IllegalStateException) {
                        return@launch
                    }.apply {
                        val bitmap = try {
                            Bitmap.createBitmap((width * ratio).toInt(), (height * ratio).toInt(), Bitmap.Config.ARGB_8888)
                        } catch (e: OutOfMemoryError) {
                            close()
                            return@launch
                        }
                        render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        close()
                        mSavedBitmap.put(position, bitmap)
                        GlobalScope.launch(Dispatchers.Main) {
                            holder.view.visibility = View.VISIBLE
                            holder.view.findViewById<ImageView>(R.id.imvPage).setImageBitmap(bitmap)
                            if (!isDisplayed) {
                                isDisplayed = true
                                listener?.onDisplay()
                            }
                        }
                    }
                }
            }
        }

        private class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)
    }

    private var mActivePointerId = -1
    private var mScaleFactor = 1f
    private var maxWidth = 0.0f
    private var maxHeight = 0.0f
    private var mLastTouchX = 0f
    private var mLastTouchY = 0f
    private var mPosX = 0f
    private var mPosY = 0f
    private var width = 0f
    private var height = 0f
    private val mScaleDetector = ScaleGestureDetector(mContext, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            mScaleFactor = 1.0f.coerceAtLeast((mScaleFactor * detector.scaleFactor).coerceAtMost(5.0f))
            maxWidth = width - width * mScaleFactor
            maxHeight = height - height * mScaleFactor
            invalidate()
            return true
        }
    })

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        width = MeasureSpec.getSize(widthMeasureSpec).toFloat()
        height = MeasureSpec.getSize(heightMeasureSpec).toFloat()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        super.onTouchEvent(ev)
        mScaleDetector.onTouchEvent(ev)
        val action = ev.action
        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mLastTouchX =  ev.x
                mLastTouchY = ev.y
                mActivePointerId = ev.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = action and MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                val x = ev.getX(pointerIndex)
                val y = ev.getY(pointerIndex)
                mPosX += x - mLastTouchX
                mPosY += y - mLastTouchY
                if (mPosX > 0.0f) mPosX = 0.0f
                else if (mPosX < maxWidth) mPosX = maxWidth
                if (mPosY > 0.0f) mPosY = 0.0f
                else if (mPosY < maxHeight) mPosY = maxHeight
                mLastTouchX = x
                mLastTouchY = y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                mActivePointerId = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                mActivePointerId = -1
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = action and MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                val pointerId = ev.getPointerId(pointerIndex)
                if (pointerId == mActivePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    mLastTouchX = ev.getX(newPointerIndex)
                    mLastTouchY = ev.getY(newPointerIndex)
                    mActivePointerId = ev.getPointerId(newPointerIndex)
                }
            }
        }
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        if (mScaleFactor == 1.0f) {
            mPosX = 0.0f
            mPosY = 0.0f
        }
        canvas.translate(mPosX, mPosY)
        canvas.scale(mScaleFactor, mScaleFactor)
        super.dispatchDraw(canvas)
        canvas.restore()
//        invalidate()
    }

    interface StatusListener {
        fun onDownloadProgress(progress: Int) {}
        fun onDownloadSuccess() {}
        fun onDisplay() {}
        fun onError(error: Throwable) {}
    }
}
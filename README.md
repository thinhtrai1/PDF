# PdfRenderer
A simple PDF viewer. Can use for url or local file. Extended from RecyclerView and PdfRenderer.

<img src="https://github.com/thinhtrai1/PDF/blob/master/warning.svg"/>

[![Release](https://jitpack.io/v/thinhtrai1/PDF.svg)](https://jitpack.io/#thinhtrai1/PDF)
<br>  
<br>
# Dependency
```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
and
```
implementation 'com.github.thinhtrai1:PDF:1.0'
```
### Or
download this class to customize again the UI your way
https://github.com/thinhtrai1/PDF/blob/master/PdfViewer/src/main/java/com/thinh/deptrai/PdfRendererView.kt  
and your pdf page in `R.layout.item_rcv_pdf_page`  
https://github.com/thinhtrai1/PDF/blob/master/PdfViewer/src/main/res/layout/item_rcv_pdf_page.xml
<br>  
<br>
# Use
```
<com.thinh.deptrai.PdfRendererView
    android:id="@+id/pdfView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```
and
```pdfView.renderFile(mFile)``` or ```pdfView.renderUrl(mUrl)```  
#### Options
```
pdfView.setStatusListener(mListener).setRatio(mRatio) // is the ratio to the screen, depending on the view size, reduce it to increase performance, default is 2
```
#### For horizontal views or gridviews, customize your layoutManager:  
```kotlin 
pdfView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
```
```
pdfView.layoutManager = GridLayoutManager(context, 2)
```
<p align="center"><img src="https://github.com/thinhtrai1/PDF/blob/master/device-2021-06-10-082645.png" width="360" height="640" /></p>
<br>  
<br>
<h1>Source</h1>
https://github.com/afreakyelf/Pdf-Viewer.git

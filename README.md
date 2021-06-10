# PdfRenderer
A simple PDF viewer. Can use for url or local file. Extended from RecyclerView and PdfRenderer.

<img src="https://github.com/thinhtrai1/PDF/blob/master/warning.svg"/>

[![Release](https://jitpack.io/v/thinhtrai1/PDF.svg)](https://jitpack.io/#thinhtrai1/PDF)
<br>  
<br>
# Dependency
```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
and
```gradle
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
```xml
<com.thinh.deptrai.PdfRendererView
    android:id="@+id/pdfView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```
and
```java
pdfView.renderFile(mFile)
```
or
```java
pdfView.renderUrl(mUrl)
```  
#### Options
```java
pdfView.setStatusListener(mListener).setRatio(mRatio)
// ratio is the ratio in the screen, depending on the view size, reduce it to increase performance, default is 2,
// recommended should only be 1 or 2, ratio 3 or higher can cause an OutOfMemoryError in case too many large images have to be processed at once.
```
#### For horizontal views or gridviews, customize your layoutManager:  
```kotlin 
pdfView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
```
```kotlin
pdfView.layoutManager = GridLayoutManager(context, 2)
```
<p align="center"><img src="https://github.com/thinhtrai1/PDF/blob/master/device-2021-06-10-082645.png" width="270" height="480" />
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<img src="https://github.com/thinhtrai1/PDF/blob/master/device-2021-06-10-093603.png" width="270" height="480" /></p>
<br>  
<br>
<h1>Source</h1>
https://github.com/afreakyelf/Pdf-Viewer.git

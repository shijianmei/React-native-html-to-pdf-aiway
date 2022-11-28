/*
 * Created on 11/15/17.
 * Written by Islam Salah with assistance from members of Blink22.com
 */

package android.print;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.webkit.WebSettings;

import java.io.File;

import android.util.Base64;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;

import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfWriter;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Converts HTML to PDF.
 * <p>
 * Can convert only one task at a time, any requests to do more conversions before
 * ending the current task are ignored.
 */
public class PdfConverter implements Runnable {

    private static final String TAG = "PdfConverter";
    private static PdfConverter sInstance;

    private Context mContext;
    private String mHtmlString;
    private File mPdfFile;
    private PrintAttributes mPdfPrintAttrs;
    private boolean mIsCurrentlyConverting;
    private WebView mWebView;
    private boolean mShouldEncode;
    private WritableMap mResultMap;
    private Promise mPromise;
    private String mBaseURL;

    private PdfConverter() {
    }

    public static synchronized PdfConverter getInstance() {
        if (sInstance == null)
            sInstance = new PdfConverter();

        return sInstance;
    }

    @Override
    public void run() {
//        mWebView = new WebView(mContext);
//        mWebView.setWebViewClient(new WebViewClient() {
//            @Override
//            public void onPageFinished(WebView view, String url) {
//                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
//                    throw new RuntimeException("call requires API level 19");
//                else {
//                    PrintDocumentAdapter documentAdapter = mWebView.createPrintDocumentAdapter();
//                    documentAdapter.onLayout(null, getPdfPrintAttrs(), null, new PrintDocumentAdapter.LayoutResultCallback() {
//                    }, null);
//                    documentAdapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, getOutputFileDescriptor(), null, new PrintDocumentAdapter.WriteResultCallback() {
//                        @Override
//                        public void onWriteFinished(PageRange[] pages) {
//                            try {
//                                String base64 = "";
//                                if (mShouldEncode) {
//                                    base64 = encodeFromFile(mPdfFile);
//                                }
//
//                                PDDocument myDocument = PDDocument.load(mPdfFile);
//                                int pagesToBePrinted = myDocument.getNumberOfPages();
//
//                                mResultMap.putString("filePath", mPdfFile.getAbsolutePath());
//                                mResultMap.putString("numberOfPages", String.valueOf(pagesToBePrinted));
//                                mResultMap.putString("base64", base64);
//                                mPromise.resolve(mResultMap);
//                            } catch (IOException e) {
//                                mPromise.reject(e.getMessage());
//                            } finally {
//                                destroy();
//                            }
//                        }
//                    });
//                }
//            }
//        });
//        WebSettings settings = mWebView.getSettings();
//        settings.setDefaultTextEncodingName("utf-8");
//        settings.setAllowUniversalAccessFromFileURLs(true);
//        settings.setAllowFileAccess(true);
//        settings.setAllowFileAccessFromFileURLs(true);
//        if (mHtmlString.indexOf("http://") != -1 || mHtmlString.indexOf("https://") != -1) {// 包含
//            mWebView.loadData(mHtmlString, "text/HTML; charset=utf-8", null);
//        }else {
//            mWebView.loadDataWithBaseURL("", mHtmlString, "text/html", "UTF-8", null);
//        }

        List<String> pics = new ArrayList<>();
        if (!TextUtils.isEmpty(mHtmlString)) {
            pics = Arrays.asList(mHtmlString.split(","));
        }
        Document document = new Document(PageSize.A4, 0, 0, 0, 0);
        try {

            String path = mPdfFile.getPath();
            int pagesToBePrinted = pics.size();
            PdfWriter.getInstance(document, new FileOutputStream(path));
            document.open();
            Image image;
            for (String url : pics) {
                image = Image.getInstance(new URL(url));
                image.scaleToFit(new Rectangle(PageSize.A4));
                document.add(image);
                document.newPage();
            }

            String base64 = "";
            if (mShouldEncode) {
                base64 = encodeFromFile(mPdfFile);
            }

            mResultMap.putString("filePath", mPdfFile.getAbsolutePath());
            mResultMap.putString("numberOfPages", String.valueOf(pagesToBePrinted));
            mResultMap.putString("base64", base64);
            mPromise.resolve(mResultMap);


        } catch (Exception e) {
            Log.e("PdfBox-Android-Sample", "Exception thrown while creating PDF", e);
            mPromise.reject(e.getMessage());
        } finally {
            document.close();
            destroy();
        }


    }

    public PrintAttributes getPdfPrintAttrs() {
        return mPdfPrintAttrs != null ? mPdfPrintAttrs : getDefaultPrintAttrs();
    }

    public void setPdfPrintAttrs(PrintAttributes printAttrs) {
        this.mPdfPrintAttrs = printAttrs;
    }

    public void convert(Context context, String htmlString, File file, boolean shouldEncode, WritableMap resultMap,
                        Promise promise, String baseURL) throws Exception {
        if (context == null)
            throw new Exception("context can't be null");
        if (htmlString == null)
            throw new Exception("htmlString can't be null");
        if (file == null)
            throw new Exception("file can't be null");

        if (mIsCurrentlyConverting)
            return;

        mContext = context;
        mHtmlString = htmlString;
        mPdfFile = file;
        mIsCurrentlyConverting = true;
        mShouldEncode = shouldEncode;
        mResultMap = resultMap;
        mPromise = promise;
        mBaseURL = baseURL;
        runOnUiThread(this);
    }

    private ParcelFileDescriptor getOutputFileDescriptor() {
        try {
            Log.e("pdfFile", mPdfFile.getPath());
            mPdfFile.createNewFile();
//            return ParcelFileDescriptor.open(mPdfFile, ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_READ_WRITE);
            return ParcelFileDescriptor.open(mPdfFile, ParcelFileDescriptor.MODE_READ_WRITE);
        } catch (Exception e) {
            Log.d(TAG, "Failed to open ParcelFileDescriptor", e);
        }
        return null;
    }

    private PrintAttributes getDefaultPrintAttrs() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return null;

        return new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                .setResolution(new PrintAttributes.Resolution("RESOLUTION_ID", "RESOLUTION_ID", 600, 600))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();

    }

    private void runOnUiThread(Runnable runnable) {
        Handler handler = new Handler(mContext.getMainLooper());
        handler.post(runnable);
    }

    private void destroy() {
        mContext = null;
        mHtmlString = null;
        mPdfFile = null;
        mPdfPrintAttrs = null;
        mIsCurrentlyConverting = false;
        mWebView = null;
        mShouldEncode = false;
        mResultMap = null;
        mPromise = null;
    }

    private String encodeFromFile(File file) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
        byte[] fileBytes = new byte[(int) randomAccessFile.length()];
        randomAccessFile.readFully(fileBytes);
        return Base64.encodeToString(fileBytes, Base64.DEFAULT);
    }
}

package icf.itncampaign;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;

/**
 * ITN Mass Campaign app: the campaign page (assets/index.html) in a WebView.
 *
 * - Served from https://appassets.androidplatform.net/assets/ so the page has a real
 *   origin and keeps what it saves on the phone (localStorage).
 * - Media may play without a tap, so the welcome video speaks as soon as the app opens.
 * - The camera works for QR scanning (getUserMedia) and for the waybill photo
 *   (the photo field opens the camera).
 * - Receipts that the page "prints" go to Android's print screen (print or save as PDF).
 */
public class MainActivity extends Activity {

    static final int REQ_CAMERA = 11, REQ_FILE = 12;
    static final String HOME = "https://appassets.androidplatform.net/assets/index.html";

    private WebView web;
    private PermissionRequest pendingCamera;
    private ValueCallback<Uri[]> pendingFile;
    private Uri photoUri;
    private WebView printView;            // kept until printing has started

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0B4B66);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);          // welcome plays with sound by itself
        s.setAllowFileAccess(false);
        s.setUserAgentString(s.getUserAgentString() + " ITNCampaignApp");
        s.setTextZoom(100);                                     // large phone font settings do not break the layout
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                // anything outside the app (Google Sheets, links) opens in the phone's browser
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            /* camera for the QR scanners */
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (hasCamera()) { request.grant(request.getResources()); return; }
                    pendingCamera = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                });
            }

            /* the waybill photo field: open the camera (or pick an existing picture) */
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFile != null) pendingFile.onReceiveValue(null);
                pendingFile = callback;
                Intent pick;
                try { pick = params.createIntent(); } catch (Exception e) { pick = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"); }
                Intent chooser = Intent.createChooser(pick, "Waybill photo");
                if (hasCamera()) {
                    try {
                        File dir = new File(getCacheDir(), "photos");
                        dir.mkdirs();
                        File f = File.createTempFile("waybill_", ".jpg", dir);
                        photoUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                        Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        cam.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                        cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        if (params.isCaptureEnabled()) chooser = cam;                    // capture="environment": straight to the camera
                        else chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                    } catch (Exception ignored) { photoUri = null; }
                }
                try { startActivityForResult(chooser, REQ_FILE); }
                catch (Exception e) { pendingFile.onReceiveValue(null); pendingFile = null; return false; }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(HOME);

        if (!hasCamera()) requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }

    boolean hasCamera() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && pendingCamera != null) {
            if (hasCamera()) pendingCamera.grant(pendingCamera.getResources());
            else pendingCamera.deny();
            pendingCamera = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE || pendingFile == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) result = new Uri[]{data.getData()};   // picked from the gallery
            else if (data != null && data.getClipData() != null) result = new Uri[]{data.getClipData().getItemAt(0).getUri()};
            else if (photoUri != null) result = new Uri[]{photoUri};                           // taken with the camera
        }
        pendingFile.onReceiveValue(result);
        pendingFile = null;
    }

    /** Called from the page as window.AndroidApp. */
    class Bridge {
        @JavascriptInterface
        public void printHtml(final String html) {
            runOnUiThread(() -> {
                printView = new WebView(MainActivity.this);
                printView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                        if (pm != null) pm.print("ITN receipt", view.createPrintDocumentAdapter("ITN receipt"),
                                new PrintAttributes.Builder().build());
                    }
                });
                printView.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "utf-8", null);
            });
        }

        @JavascriptInterface
        public boolean isApp() { return true; }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}

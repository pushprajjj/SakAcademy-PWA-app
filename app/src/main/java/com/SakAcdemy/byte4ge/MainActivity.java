package com.SakAcdemy.byte4ge;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message; // <-- Add this import
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings; // <-- Add this import
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private String websiteURL = "https://student.sakacademy.in/"; // sets web URL
    private WebView webview;
    private SwipeRefreshLayout mySwipeRefreshLayout;
    private static final int FILE_REQUEST_CODE = 123;  // Replace with your desired request code
    private ValueCallback<Uri[]> mFilePathCallback;

    // ActivityResultLauncher for handling the result
    private final ActivityResultLauncher<Intent> mGetContentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null && mFilePathCallback != null) {
                        Uri[] resultArray = new Uri[]{data.getData()};
                        mFilePathCallback.onReceiveValue(resultArray);
                        mFilePathCallback = null;
                    }
                } else {
                    if (mFilePathCallback != null) {
                        mFilePathCallback.onReceiveValue(null);
                        mFilePathCallback = null;
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!CheckNetwork.isInternetAvailable(this)) {
            // If there is no internet, show an alert and finish the activity
            new AlertDialog.Builder(this)
                    .setTitle("No internet connection available")
                    .setMessage("Please check your mobile data or Wi-Fi network.")
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
        } else {
            // Initialize WebView
            webview = findViewById(R.id.webView);
            webview.getSettings().setJavaScriptEnabled(true);
            webview.getSettings().setDomStorageEnabled(true);
            webview.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
            webview.getSettings().setSupportMultipleWindows(true);
            webview.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
            webview.getSettings().setAllowFileAccess(true); // Enable file access for file upload
            webview.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                    // Create an intent to open the file picker
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");

                    // Start the file picker activity
                    mFilePathCallback = filePathCallback;
                    mGetContentLauncher.launch(intent);  // Using the ActivityResultLauncher
                    return true;
                }

                @Override
                public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                    WebView newWebView = new WebView(view.getContext());
                    WebSettings webSettings = newWebView.getSettings();
                    webSettings.setJavaScriptEnabled(true);

                    final Dialog dialog = new Dialog(view.getContext());
                    dialog.setContentView(newWebView);
                    dialog.show();

                    newWebView.setWebViewClient(new WebViewClient());
                    newWebView.setWebChromeClient(this);

                    WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                    transport.setWebView(newWebView);
                    resultMsg.sendToTarget();

                    return true;
                }
            });
            webview.loadUrl(websiteURL);
            webview.setWebViewClient(new WebViewClientDemo());

            // Handle file upload permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                }
            }

            // Swipe to refresh functionality
            mySwipeRefreshLayout = findViewById(R.id.swipeContainer);
            mySwipeRefreshLayout.setOnRefreshListener(() -> webview.reload());

            // Handle downloading
            webview.setDownloadListener(new DownloadListener() {
                @Override
                public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimeType);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    request.addRequestHeader("cookie", cookies);
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setDescription("Downloading file....");
                    request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    dm.enqueue(request);
                    Toast.makeText(getApplicationContext(), "Downloading File", Toast.LENGTH_SHORT).show();
                }
            });

            // Enable debugging (optional)
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private class WebViewClientDemo extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("whatsapp:")) {
                // Open mailto, tel, and whatsapp links in their respective responsible apps
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    // Handle the exception if the app is not installed
                    // For example, open the link in the default browser
                    view.loadUrl(url);
                }
                return true;  // Return true to indicate that the URL has been handled
            } else {
                view.loadUrl(url);
                return true;
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            mySwipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (webview.isFocused() && webview.canGoBack()) {
            webview.goBack();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("EXIT")
                    .setMessage("Are you sure?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton("No", null)
                    .show();
        }
    }

    static class CheckNetwork {
        private static final String TAG = CheckNetwork.class.getSimpleName();

        public static boolean isInternetAvailable(Context context) {
            NetworkInfo info = ((ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();

            if (info == null) {
                Log.d(TAG, "no internet connection");
                return false;
            } else {
                if (info.isConnected()) {
                    Log.d(TAG, "internet connection available...");
                    return true;
                } else {
                    Log.d(TAG, "internet connection");
                    return true;
                }
            }
        }
    }
}

package ir.wisetutor.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.webkit.ConsoleMessage;
import androidx.activity.OnBackPressedCallback;
import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {
    private SwipeRefreshLayout swipeRefresh;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean pageLoadFailed = false;
    private boolean offlineDialogShowing = false;
    private boolean networkAvailable = false;

    private static final int MICROPHONE_PERMISSION_CODE = 100;

    private WebView webView;
    private PermissionRequest pendingPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        swipeRefresh.setOnRefreshListener(() -> {
            if (hasInternet()) {
                webView.reload();
            } else {
                swipeRefresh.setRefreshing(false);
                showOfflineDialog();
            }
        });
        setupWebView();

        checkMicrophonePermission();

        webView.loadUrl("https://wisetutor.ir");
        OnBackPressedDispatcher dispatcher = getOnBackPressedDispatcher();

        dispatcher.addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {

                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
        registerNetworkCallback();
    }
    private boolean hasInternet() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(
                        Context.CONNECTIVITY_SERVICE
                );

        if (cm == null) return false;

        Network network = cm.getActiveNetwork();

        if (network == null) return false;

        NetworkCapabilities capabilities =
                cm.getNetworkCapabilities(network);

        return capabilities != null
                && capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
                && capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
        );
    }
    private void showOfflineDialog() {
        if (isFinishing() || offlineDialogShowing) return;

        offlineDialogShowing = true;

        new AlertDialog.Builder(this)
                .setTitle("عدم اتصال به اینترنت")
                .setMessage(
                        "اتصال اینترنت برقرار نیست. " +
                                "لطفاً اینترنت خود را بررسی کنید."
                )
                .setPositiveButton("تلاش مجدد", (dialog, which) -> {
                    offlineDialogShowing = false;

                    if (hasInternet()) {
                        pageLoadFailed = false;
                        webView.reload();
                    } else {
                        showOfflineDialog();
                    }
                })
                .setOnDismissListener(dialog ->
                        offlineDialogShowing = false
                )
                .show();
    }
    private void registerNetworkCallback() {
        connectivityManager =
                (ConnectivityManager) getSystemService(
                        Context.CONNECTIVITY_SERVICE
                );

        if (connectivityManager == null) return;

        networkCallback =
                new ConnectivityManager.NetworkCallback() {

                    @Override
                    public void onAvailable(Network network) {
                        runOnUiThread(() -> {
                            networkAvailable = true;
                            checkConnectionRestored();
                        });
                    }

                    @Override
                    public void onLost(Network network) {
                        runOnUiThread(() -> {
                            networkAvailable = false;

                            if (!hasInternet()) {
                                showOfflineDialog();
                            }
                        });
                    }

                    @Override
                    public void onCapabilitiesChanged(
                            Network network,
                            NetworkCapabilities capabilities
                    ) {
                        runOnUiThread(() -> {
                            if (hasInternet()) {
                                networkAvailable = true;
                                checkConnectionRestored();
                            }
                        });
                    }
                };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                .build();

        connectivityManager.registerNetworkCallback(
                request,
                networkCallback
        );
    }

    private void checkConnectionRestored() {
        if (networkAvailable && pageLoadFailed && hasInternet()) {
            pageLoadFailed = false;

            if (swipeRefresh != null) {
                swipeRefresh.setRefreshing(true);
            }

            webView.reload();
        }
    }
    @Override
    protected void onDestroy() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(
                        networkCallback
                );
            } catch (IllegalArgumentException ignored) {
            }
        }

        super.onDestroy();
    }

    private void setupWebView() {

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }

                pageLoadFailed = false;
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    android.webkit.WebResourceError error
            ) {
                super.onReceivedError(view, request, error);

                if (request.isForMainFrame()) {
                    pageLoadFailed = true;

                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }

                    if (!hasInternet()) {
                        showOfflineDialog();
                    }
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(final PermissionRequest request) {

                Log.d(
                        "WISE_MIC",
                        "WebView permission request: " + request.getOrigin()
                );

                for (String resource : request.getResources()) {
                    Log.d(
                            "WISE_MIC",
                            "Requested resource: " + resource
                    );
                }

                runOnUiThread(() -> {

                    if (!request.getOrigin().toString()
                            .equals("https://wisetutor.ir/")) {

                        Log.d("WISE_MIC", "Origin denied");

                        request.deny();
                        return;
                    }

                    if (ContextCompat.checkSelfPermission(
                            MainActivity.this,
                            Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED) {

                        Log.d(
                                "WISE_MIC",
                                "Android microphone permission NOT granted"
                        );

                        pendingPermissionRequest = request;

                        ActivityCompat.requestPermissions(
                                MainActivity.this,
                                new String[]{
                                        Manifest.permission.RECORD_AUDIO
                                },
                                MICROPHONE_PERMISSION_CODE
                        );

                        return;
                    }

                    Log.d(
                            "WISE_MIC",
                            "Granting WebView AUDIO_CAPTURE"
                    );

                    request.grant(request.getResources());

                });
            }
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {

                Log.d(
                        "WISE_JS",
                        consoleMessage.message()
                                + " | line: "
                                + consoleMessage.lineNumber()
                                + " | source: "
                                + consoleMessage.sourceId()
                );

                return true;
            }
        });
    }

    private void checkMicrophonePermission() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    MICROPHONE_PERMISSION_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == MICROPHONE_PERMISSION_CODE) {

            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                if (pendingPermissionRequest != null) {

                    pendingPermissionRequest.grant(
                            new String[]{
                                    PermissionRequest.RESOURCE_AUDIO_CAPTURE
                            }
                    );

                    pendingPermissionRequest = null;
                }
            } else {

                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.deny();
                    pendingPermissionRequest = null;
                }
            }
        }
    }


}
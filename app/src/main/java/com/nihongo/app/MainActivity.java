package com.nihongo.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 4101;
    private static final int REQUEST_IMPORT = 4102;

    private WebView webView;
    private String pendingExportJson;
    private String pendingAuthCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        webView.addJavascriptInterface(new AndroidFiles(), "AndroidFiles");

        webView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                deliverAuthCallback();
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, android.webkit.WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        handleAuthIntent(getIntent());
    }

    private void handleAuthIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        if ("com.nihongo.app".equals(uri.getScheme()) && "auth".equals(uri.getHost())) {
            pendingAuthCallback = uri.toString();
            deliverAuthCallback();
        }
    }

    private void deliverAuthCallback() {
        if (webView == null || pendingAuthCallback == null) return;
        String encoded = Base64.encodeToString(pendingAuthCallback.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
        webView.evaluateJavascript("(function(){if(window.handleLingoraAuthCallback){window.handleLingoraAuthCallback(atob(\"" + encoded + "\"));}})();", null);
        pendingAuthCallback = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthIntent(intent);
    }

    private class AndroidFiles {
        @JavascriptInterface
        public void openExternal(String url) {
            runOnUiThread(() -> {
                try {
                    Uri uri = Uri.parse(url);
                    String host = uri.getHost() == null ? "" : uri.getHost();
                    if (!"https".equals(uri.getScheme()) || !(host.endsWith(".supabase.co") || "accounts.google.com".equals(host))) {
                        throw new IllegalArgumentException("Endereço externo não permitido.");
                    }
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception error) {
                    Toast.makeText(MainActivity.this, "Não foi possível abrir o login: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void exportJson(String json) {
            runOnUiThread(() -> {
                pendingExportJson = json;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "nihongo-memoria.json");
                startActivityForResult(intent, REQUEST_EXPORT);
            });
        }

        @JavascriptInterface
        public void importJson() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(intent, REQUEST_IMPORT);
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingExportJson = null;
            return;
        }

        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_EXPORT) {
                if (pendingExportJson == null) throw new IllegalStateException("Nenhum backup preparado.");
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("Não foi possível abrir o destino.");
                    output.write(pendingExportJson.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                pendingExportJson = null;
                Toast.makeText(this, "Backup exportado com sucesso!", Toast.LENGTH_LONG).show();
            } else if (requestCode == REQUEST_IMPORT) {
                byte[] bytes;
                try (InputStream input = getContentResolver().openInputStream(uri);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (input == null) throw new IllegalStateException("Não foi possível ler o arquivo.");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    bytes = output.toByteArray();
                }
                String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
                webView.evaluateJavascript(
                    "window.receiveImportedBackup('" + encoded + "')", null
                );
            }
        } catch (Exception error) {
            pendingExportJson = null;
            Toast.makeText(this, "Erro nos dados: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

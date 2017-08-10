package net.kwmt27.webviewinputample;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    private FileChooserWebChromeClient webChromeClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = (WebView) findViewById(R.id.webview);
        String url = "file:///android_asset/sample.html";

        webView.getSettings().setJavaScriptEnabled(true);

        webChromeClient = new FileChooserWebChromeClient();
        webView.setWebChromeClient(webChromeClient);
        webView.loadUrl(url);

        WebView.setWebContentsDebuggingEnabled(true);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case FileChooserWebChromeClient.REQUEST_CAMERA_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    webChromeClient.openCameraGalleryChooser(this);
                    return;
                }
                // パーミッション要求を拒否した場合
                webChromeClient.callOnReceiveValue(null);
                Toast.makeText(this, "エラー", Toast.LENGTH_LONG).show();

            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case FileChooserWebChromeClient.INPUT_FILE_REQUEST_CODE:
                webChromeClient.cleanUpOnBackFromFileChooser(this, resultCode, intent);
            default:
                Log.d("", "requestCode:" + requestCode + ", resultCode:" + resultCode);
        }
    }


}



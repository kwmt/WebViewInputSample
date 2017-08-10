package net.kwmt27.webviewinputample;


import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;


public class FileChooserWebChromeClient extends WebChromeClient {

    public static final int REQUEST_CAMERA_PERMISSION = 1;
    public static final int INPUT_FILE_REQUEST_CODE = 2;


    private Uri photoFileUri;
    private ValueCallback<Uri[]> filePathCallback;
    private FileChooserParams fileChooserParams;

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        this.filePathCallback = filePathCallback;
        this.fileChooserParams = fileChooserParams;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] PERMISSIONS = {android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.CAMERA};
            if (!hasPermissions(webView.getContext(), PERMISSIONS)) {
                ActivityCompat.requestPermissions((Activity) (webView.getContext()), PERMISSIONS, REQUEST_CAMERA_PERMISSION);

                // onShowFileChooser
                // https://developer.android.com/reference/android/webkit/WebChromeClient.html#onShowFileChooser(android.webkit.WebView, android.webkit.ValueCallback<android.net.Uri[]>, android.webkit.WebChromeClient.FileChooserParams)
                // パーミッション要求を許可した場合は
                // this.cleanUpOnBackFromFileChooserで filePathCallback.onReceiveValue を読んでいる
                // (流れ: Activity.onRequestPermissionsResult -> this.openCameraGalleryChooser -> Activity.onActivityResult -> this.cleanUpOnBackFromFileChooser)
                // パーミッション要求を拒否した場合
                // this.callOnReceiveValue で filePathCallback.onReceiveValueを読んでいる
                // (流れ: Activity.onRequestPermissionsResult で許可チェック -> webChromeClient.callOnReceiveValue)
                return true;
            }
        }
        openCameraGalleryChooser((AppCompatActivity) (webView.getContext()));
        return true;
    }

    private boolean hasPermissions(Context context, String... permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * カメラかギャラリーを選択する画面を表示
     */
    public void openCameraGalleryChooser(AppCompatActivity activity) {
        // カメラ
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
            photoFileUri = CameraUtil.createPhotoFileUri(activity);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoFileUri);
        }
        // ギャラリー
        Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
        // 複数mimetype設定
        // https://stackoverflow.com/a/42582490
        contentSelectionIntent.setType("*/*");
        contentSelectionIntent.putExtra(Intent.EXTRA_MIME_TYPES, fileChooserParams.getAcceptTypes());
        contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);

        Intent chooserIntent = Intent.createChooser(contentSelectionIntent, "pick image");
        Intent[] intentArray = new Intent[]{takePictureIntent};
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

        activity.startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE);
    }

    /**
     * カメラやギャラリーから戻ってきたときの処理 (onActivityResultで呼ぶ)
     */
    public void cleanUpOnBackFromFileChooser(Context context, int resultCode, Intent intent) {
        if (filePathCallback == null) {
            return;
        }
        if (resultCode != Activity.RESULT_OK) {
            if (photoFileUri != null) {
                context.getContentResolver().delete(photoFileUri, null, null);
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
                photoFileUri = null;
            }
            return;
        }

        if (intent != null) {
            // ギャラリーで選択した場合
            // 画像が1つでも複数でもclipDataは null にならない
            ClipData clipData = intent.getClipData();
            if (clipData != null) {
                final int selectedFilesCount = clipData.getItemCount();
                Uri[] results = new Uri[selectedFilesCount];
                for (int i = 0; i < selectedFilesCount; i++) {
                    results[i] = clipData.getItemAt(i).getUri();
                }
                filePathCallback.onReceiveValue(results);
            } else {
                // カメラで撮影した場合
                filePathCallback.onReceiveValue(new Uri[]{photoFileUri});
            }
        } else {
            filePathCallback.onReceiveValue(null);
        }
        filePathCallback = null;
        photoFileUri = null;
    }

    public void callOnReceiveValue(Uri[] uris) {
        filePathCallback.onReceiveValue(uris);
    }



    private static class CameraUtil {
        static Uri createPhotoFileUri(Context context) {
            // カメラで撮影
            String filename = System.currentTimeMillis() + ".jpg";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            return context.getContentResolver()
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        }
    }
}

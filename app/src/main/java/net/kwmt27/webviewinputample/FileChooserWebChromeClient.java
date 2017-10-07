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
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import java.io.File;
import java.io.IOException;


public class FileChooserWebChromeClient extends WebChromeClient {

    public static final int REQUEST_CAMERA_PERMISSION = 1;
    public static final int INPUT_FILE_REQUEST_CODE = 2;


    private File photoFile;
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
        if (context == null || permissions == null) {
            throw new IllegalArgumentException("パーミッションチェックには、Contextとチェックしたいpermissionが必要です");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
            // 参考
            // https://akira-watson.com/android/camera-intent.html
            // https://inthecheesefactory.com/blog/how-to-share-access-to-file-with-fileprovider-on-android-nougat
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            if (photoFile != null) {
                Uri photoUri = getPhotoUri(activity, photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            }
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
            // 画像選択をキャンセルした場合
            deletePhotoFile(context);
            filePathCallback.onReceiveValue(null);
            return;
        }
        if (intent != null) {
            // ギャラリーで選択した場合
            // 画像を1枚選択した場合、intent.getData()に選択した画像のURIが入ってくる
            Uri onlyOneSelectedImageUri = intent.getData();
            // 画像を複数枚選択した場合(複数枚選択モード時)、intent.getClipData()に複数枚選択した画像のURIが入ってくる
            ClipData multipleSelectedImageUriData = intent.getClipData();
            // 複数枚選択した場合、intent.getData()に画像URIが入ってくるので、先にintent.getClipData()を判定している
            if (multipleSelectedImageUriData != null) {
                final int selectedFilesCount = multipleSelectedImageUriData.getItemCount();
                Uri[] results = new Uri[selectedFilesCount];
                for (int i = 0; i < selectedFilesCount; i++) {
                    results[i] = multipleSelectedImageUriData.getItemAt(i).getUri();
                }
                filePathCallback.onReceiveValue(results);
            } else if (onlyOneSelectedImageUri != null) {
                filePathCallback.onReceiveValue(new Uri[]{onlyOneSelectedImageUri});
            } else {
                receivePhotoFileForCamera(context);
            }
        } else {
            // https://stackoverflow.com/questions/12564112/android-camera-onactivityresult-intent-is-null-if-it-had-extras
            receivePhotoFileForCamera(context);
        }
        filePathCallback = null;
    }

    /**
     * ファイルが作成されていたら、削除する
     * @param context
     */
    public void deletePhotoFile(Context context) {
        // createImageFileした後に、deleteされる場合があるので、ContentResolverに登録されているかどうかによって制御する
        if(photoFile != null) {
            context.getContentResolver().delete(getPhotoUri(context, photoFile), null, null);
            photoFile = null;
        }
    }

    public void callOnReceiveValue(Uri[] uris) {
        filePathCallback.onReceiveValue(uris);
    }

    private File createImageFile() throws IOException {
        long timeStamp = System.currentTimeMillis();
        String imageFileName = "JPEG_" + timeStamp;
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private Uri registerContentResolver(Context context, String filePath) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DATA, filePath);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        return context.getContentResolver()
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

    }

    private boolean receivePhotoFileForCamera(Context context) {
        if (photoFile != null) {
            // カメラで撮影した場合
            Uri uri = registerContentResolver(context, photoFile.getAbsolutePath());
            filePathCallback.onReceiveValue(new Uri[]{uri});
            photoFile = null;
            return true;
        }
        filePathCallback.onReceiveValue(null);
        return false;
    }


    private Uri getPhotoUri(Context context, File file) {
        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);
    }

}

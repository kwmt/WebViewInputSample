# はじめに 
 
WebViewで下記のような`input`タグを追加して、画像選択するのが結構大変だったので、その実装サンプルです。

```
<input type="file" id="files" name="files[]" accept="audio/*,video/*,image/*" multiple>
```

# ポイント的なとこ

## Android OS
 API Level 21以上です。別のAPIのようで、大変そう・・・。Runtime Permission対応も含めてます。
 
## inputタグをタップしたとき
`WebChromeClient#onShowFileChooser` が呼ばれます。

## inputタグのAttributeについて

* `accept` Attributeは `onShowFileChooser` の引数の `fileChooserParams#getAcceptTypes()` で取得できます。
* `multiple` Attributeは `onShowFileChooser` の引数の `fileChooserParams#getMode()`が`FileChooserParams#MODE_OPEN_MULTIPLE`かどうかで判断できます。

## acceptが複数だった場合、intentに複数acceptを設定するには

`Intent.EXTRA_MIME_TYPES` というのが使えるようです。

```
contentSelectionIntent.setType("*/*");
contentSelectionIntent.putExtra(Intent.EXTRA_MIME_TYPES, fileChooserParams.getAcceptTypes());
```

## カメラかギャラリー（ドキュメント）を選択するようにしています

`Intent.createChooser` で実現できます。


## ギャラリー（ドキュメント）で選択した画像を取得するには
ギャラリー（ドキュメント）で選択した画像Uriを`onActivityResult`で取得できますが、
複数の画像を取得するには、`intent.getClipData()`を使わなければならないようです。`intent.getData()` で1つしか返ってこなくてハマってた。。

`multiple` Attributeを外しても intent.getClipData()` に入ってきます。
 
## inputタグをタップして、再度inputタグをタップするには
`onShowFileChooser` の引数の` ValueCallback<Uri[]> filePathCallback`の`onReceiveValue`が呼ばれていないといけません。
また、`onReceiveValue`は連続して呼ぶことはできません。つまり
```
filePathCallback.onReceiveValue(new Uri[]{result});
filePathCallback.onReceiveValue(null);
```
とはできません。



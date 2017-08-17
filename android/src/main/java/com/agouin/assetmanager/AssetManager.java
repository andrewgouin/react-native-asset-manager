package com.agouin.assetmanager;

import android.content.ContentResolver;
import android.content.Context;

import com.facebook.react.bridge.GuardedAsyncTask;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;

class AssetManager extends ReactContextBaseJavaModule {

  private static final String TAG = "RNAssetManager";

  private static final String[] PROJECTION;
  static {
    PROJECTION = new String[] {
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.TITLE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT
    };
  }

  private static final String SELECTION = MediaStore.Files.FileColumns.MEDIA_TYPE + "="
          + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
          + " OR "
          + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
          + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

  public AssetManager(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  public String getName() {
    return "AssetManager";
  }

  @ReactMethod
  public void getAssets(int first, Promise promise) {
    new GetAssetsTask(getReactApplicationContext(), first, promise).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private static class GetAssetsTask extends GuardedAsyncTask<Void,Void> {
    private final Context mContext;
    private final Promise mPromise;
    private final int mFirst;

    protected GetAssetsTask(
            ReactContext context,
            int first,
            Promise promise) {
      super(context);
      this.mContext = context;
      this.mFirst = first;
      this.mPromise = promise;
    }

    @Override
    protected void doInBackgroundGuarded(Void... params) {
      WritableMap response = new WritableNativeMap();
      ContentResolver resolver = mContext.getContentResolver();
      Cursor assets = resolver.query(MediaStore.Files.getContentUri("external"), PROJECTION, SELECTION, null, MediaStore.Files.FileColumns.DATE_ADDED + " DESC, " + MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC LIMIT " +
              (mFirst + 1));
      if (assets == null) {
        mPromise.reject("AssetManager failure", "Unable to load assets");
        return;
      }
      try {
        putEdges(resolver, assets, response, mFirst);
        putPageInfo(assets, response, mFirst);
      } catch (Exception exception) {
        mPromise.reject("AssetManager failure", "Error placing assets into map");
      } finally {
        mPromise.resolve(response);
      }
    }
  }

  private static void putPageInfo(Cursor assets, WritableMap response, int limit) {
    WritableMap pageInfo = new WritableNativeMap();
    pageInfo.putBoolean("has_next_page", limit < assets.getCount());
    if (limit < assets.getCount()) {
      assets.moveToPosition(limit - 1);
      pageInfo.putString(
              "end_cursor",
              assets.getString(assets.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)));
    }
    response.putMap("page_info", pageInfo);
  }

  private static void putEdges(
          ContentResolver resolver,
          Cursor photos,
          WritableMap response,
          int limit) {
    WritableArray edges = new WritableNativeArray();
    photos.moveToFirst();
    int idIndex = photos.getColumnIndex(MediaStore.Files.FileColumns._ID);
    int mimeTypeIndex = photos.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE);
    int dateAddedIndex = photos.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED);
    int widthIndex = photos.getColumnIndex(MediaStore.Files.FileColumns.WIDTH);
    int heightIndex = photos.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT);

    for (int i = 0; i < limit && !photos.isAfterLast(); i++) {
      WritableMap edge = new WritableNativeMap();
      WritableMap node = new WritableNativeMap();
      boolean imageInfoSuccess = putImageInfo(resolver, photos, node, idIndex, widthIndex, heightIndex);
      if (imageInfoSuccess) {
        putBasicNodeInfo(photos, node, mimeTypeIndex, dateAddedIndex);
        edge.putMap("node", node);
        edges.pushMap(edge);
      } else {
        // we skipped an image because we couldn't get its details (e.g. width/height), so we
        // decrement i in order to correctly reach the limit, if the cursor has enough rows
        i--;
      }
      photos.moveToNext();
    }
    response.putArray("edges", edges);
  }

  private static void putBasicNodeInfo(
          Cursor photos,
          WritableMap node,
          int mimeTypeIndex,
          int dateTakenIndex) {
    node.putString("type", photos.getString(mimeTypeIndex));
    node.putDouble("timestamp", photos.getLong(dateTakenIndex) / 1000d);
  }

  private static boolean putImageInfo(
          ContentResolver resolver,
          Cursor photos,
          WritableMap node,
          int idIndex,
          int widthIndex,
          int heightIndex) {
    WritableMap image = new WritableNativeMap();
    Uri photoUri = Uri.withAppendedPath(
            MediaStore.Files.getContentUri("external"),
            photos.getString(idIndex));
    image.putString("uri", photoUri.toString());
    float width = -1;
    float height = -1;
    width = photos.getInt(widthIndex);
    height = photos.getInt(heightIndex);

    if (width <= 0 || height <= 0) {
      try {
        AssetFileDescriptor photoDescriptor = resolver.openAssetFileDescriptor(photoUri, "r");
        BitmapFactory.Options options = new BitmapFactory.Options();
        // Set inJustDecodeBounds to true so we don't actually load the Bitmap, but only get its
        // dimensions instead.
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(photoDescriptor.getFileDescriptor(), null, options);
        photoDescriptor.close();

        width = options.outWidth;
        height = options.outHeight;
      } catch (IOException e) {
        return false;
      }
    }
    image.putDouble("width", width);
    image.putDouble("height", height);
    node.putMap("image", image);
    return true;
  }

}

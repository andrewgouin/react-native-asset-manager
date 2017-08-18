package com.agouin.assetmanager;

import android.content.ContentResolver;
import android.content.ContentUris;
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

import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;

import android.provider.MediaStore;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
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
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Images.Thumbnails.DATA
    };
  }

  private static final String SELECTION = "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
          + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
          + " OR "
          + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
          + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO +
          ")";

  public AssetManager(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  public String getName() {
    return "AssetManager";
  }

  @ReactMethod
  public void getAssets(int first, @Nullable String after, Promise promise) {
    new GetAssetsTask(getReactApplicationContext(), first, after, promise).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private static class GetAssetsTask extends GuardedAsyncTask<Void,Void> {
    private final Context mContext;
    private final Promise mPromise;
    private final int mFirst;
    private final String mAfter;

    protected GetAssetsTask(
            ReactContext context,
            int first,
            String after,
            Promise promise) {
      super(context);
      this.mContext = context;
      this.mFirst = first;
      this.mAfter = after;
      this.mPromise = promise;
    }

    @Override
    protected void doInBackgroundGuarded(Void... params) {
      WritableMap response = new WritableNativeMap();
      ContentResolver resolver = mContext.getContentResolver();
      String selection;
      String[] selectionArgs = null;
      if (mAfter != null){
        selection = SELECTION + " AND "+  MediaStore.Files.FileColumns.DATE_ADDED + " < ?";
        selectionArgs = new String[] {mAfter};
      } else {
        selection = SELECTION;
      }
      Cursor assets = resolver.query(MediaStore.Files.getContentUri("external"), PROJECTION, selection, selectionArgs, MediaStore.Files.FileColumns.DATE_ADDED + " DESC, " + MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC LIMIT " +
              (mFirst + 1));
      if (assets == null) {
        mPromise.reject("AssetManager failure", "Unable to load assets");
        return;
      }
      try {
        putEdges(mContext, resolver, assets, response, mFirst);
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
          Context context,
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
      String assetInfoKey = putAssetInfo(context, resolver, photos, node, idIndex, widthIndex, heightIndex);
      if (assetInfoKey != null) {
        putBasicNodeInfo(photos, node, mimeTypeIndex, dateAddedIndex);
        edge.putMap("node", node);
        edge.putString("key", assetInfoKey);
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

  private static String writeBitmapToFile(Context context, Bitmap bitmap, String imageName) {
    FileOutputStream out = null;
    String filePath = context.getCacheDir() + "/" + imageName +".png";
    File file = new File(filePath);
    if(file.exists()) return filePath;
    try {
      out = new FileOutputStream(filePath);
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
      // PNG is a lossless format, the compression factor (100) is ignored
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (out != null) {
          out.close();
          return filePath;
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  private static String putAssetInfo(
          Context context,
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
    boolean isVideo = photos.getInt(photos.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
    String filePath = photos.getString(photos.getColumnIndex(MediaStore.Files.FileColumns.DATA));
    final String thumbpath;
    if (isVideo) {
      Bitmap thumb = ThumbnailUtils.createVideoThumbnail(filePath,
              MediaStore.Images.Thumbnails.MINI_KIND);
      thumbpath = writeBitmapToFile(context, thumb, photos.getString(idIndex));
      image.putString("path",filePath);
    } else {
      thumbpath = photos.getString(photos.getColumnIndex(MediaStore.Images.Thumbnails.DATA));
    }
    image.putString("thumb", Uri.fromFile(new File(thumbpath)).toString());
    float width = -1;
    float height = -1;
    width = photos.getInt(widthIndex);
    height = photos.getInt(heightIndex);

    image.putDouble("width", width);
    image.putDouble("height", height);
    node.putMap("image", image);
    return photos.getString(idIndex);
  }

}

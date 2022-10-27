package com.example.watermark;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by suli on 2020/9/9
 * <p>
 * 给图片添加文字水印
 **/
class BitmapImageWatermarkTask extends AsyncTask<Void, Void, BitmapImageWatermarkTask.Result> {
  final static String TAG = "WatermarkTask";

  final Context context;
  final Uri srcUri;
  final float ratio;
  final String location;
  final int index;
  final OnResultListener listener;

  final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

  public BitmapImageWatermarkTask(
      Context context,
      Uri srcUri,
      float ratio,
      String location,
      int index,
      OnResultListener listener
  ) {
    this.context = context;
    this.srcUri = srcUri;
    this.ratio = ratio;
    this.location = location;
    this.index = index;
    this.listener = listener;
  }

  @Override
  protected Result doInBackground(Void... voids) {
    if (isCancelled()) {
      return null;
    }

    try {
      // 1. read exif
      ExifInterface exif = new ExifInterface(context.getContentResolver().openInputStream(srcUri));
      final String dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
      final String dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
      final String dateTimeDigitized = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED);
      final int orientation =
          exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

      if (dateTime == null) {
        Cursor cursor = context.getContentResolver().query(srcUri, new String[] {
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE,
        }, null, null, null);
        cursor.moveToFirst();
        String time = cursor.getString(0);
        String addedTime = cursor.getString(1);
        long size = cursor.getLong(2);
        cursor.close();
        Log.d(TAG, srcUri + "> createTime:" + time + ",addedTime:" + addedTime + ",size:" + size);
      }

      Log.d("BitmapTask",
          srcUri
              + "> dateTime:"
              + dateTime
              + ", dateTimeOriginal:"
              + dateTimeOriginal
              + ", dateTimeDigitized:"
              + dateTimeDigitized
              + ", orientation:"
              + orientation);

      BitmapFactory.Options srcOptions =
          BitmapUtil.decodeImageForOption(context.getContentResolver(), srcUri);
      Bitmap srcBitmap = BitmapUtil.decodeImage(context.getContentResolver(), srcUri, srcOptions);

      Bitmap rotateBitmap = null;
      switch (orientation) {
        case ExifInterface.ORIENTATION_ROTATE_90:
          rotateBitmap = BitmapUtil.rotate(srcBitmap, 90);
          break;
        case ExifInterface.ORIENTATION_ROTATE_180:
          rotateBitmap = BitmapUtil.rotate(srcBitmap, 180);
          break;
        case ExifInterface.ORIENTATION_ROTATE_270:
          rotateBitmap = BitmapUtil.rotate(srcBitmap, 270);
          break;
      }
      if (rotateBitmap != null) {
        srcBitmap.recycle();
        srcBitmap = rotateBitmap;
      }

      String watermarkText = dateTime.split(" ")[0].replaceAll(":", "-");
      final int offset = (int) (srcBitmap.getWidth() * 0.045);
      final int textSize = (int) (srcBitmap.getHeight() * 0.035);

      Log.i("bitmap", "add watermark height:"
          + srcBitmap.getHeight()
          + ", text:"
          + watermarkText
          + ", textSize:"
          + textSize
          + ", offset:"
          + offset);

      Bitmap result =
          BitmapUtil.addWatermarkToBitmap(srcBitmap, watermarkText, ratio, location, offset,
              textSize, 0xFFFFFFFF);

      Uri output =
          Uri.fromFile(
              new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getPath(),
                  watermarkText + "_" + index + ".jpg"));
      Log.d("watermark", "output watermark file:" + output);
      BitmapUtil.writeBitmapToUri(context, result, output, Bitmap.CompressFormat.JPEG, 100);

      return new Result(output, null);
    } catch (Exception e) {
      e.printStackTrace();
      return new Result(null, e);
    }
  }

  @Override
  protected void onPostExecute(Result result) {
    super.onPostExecute(result);
    if (isCancelled()) {
      return;
    }

    if (result != null) {
      listener.onResult(result);
    }
  }

  static final class Result {
    Uri output;
    Exception error;

    public Result(Uri output, Exception error) {
      this.output = output;
      this.error = error;
    }
  }

  interface OnResultListener {

    void onResult(Result result);
  }
}

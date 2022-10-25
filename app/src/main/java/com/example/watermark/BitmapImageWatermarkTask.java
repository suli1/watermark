package com.example.watermark;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;

/**
 * Created by suli on 2020/9/9
 * <p>
 * 给图片添加文字水印
 **/
class BitmapImageWatermarkTask extends AsyncTask<Void, Void, BitmapImageWatermarkTask.Result> {
  final Context context;
  final Uri srcUri;
  final String watermarkText;
  final float ratio;
  final String location;
  final int offset;
  final Uri targetUri;
  final OnResultListener listener;

  public BitmapImageWatermarkTask(
      Context context,
      Uri srcUri,
      String watermarkText,
      float ratio,
      String location,
      int offset,
      Uri targetUri,
      OnResultListener listener
  ) {
    this.context = context;
    this.srcUri = srcUri;
    this.watermarkText = watermarkText;
    this.ratio = ratio;
    this.location = location;
    this.offset = offset;
    this.targetUri = targetUri;
    this.listener = listener;
  }

  @Override
  protected Result doInBackground(Void... voids) {
    if (isCancelled()) {
      return null;
    }

    try {
      BitmapFactory.Options srcOptions =
          BitmapUtil.decodeImageForOption(context.getContentResolver(), srcUri);
      Bitmap srcBitmap = BitmapUtil.decodeImage(context.getContentResolver(), srcUri, srcOptions);

      Bitmap result =
          BitmapUtil.addWatermarkToBitmap(srcBitmap, watermarkText, ratio, location, offset);
      BitmapUtil.writeBitmapToUri(context, result, targetUri, Bitmap.CompressFormat.JPEG, 90);

      return new Result(true, null);
    } catch (Exception e) {
      e.printStackTrace();
      return new Result(false, e);
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
    boolean success;
    Exception error;

    public Result(boolean success, Exception error) {
      this.success = success;
      this.error = error;
    }
  }

  interface OnResultListener {

    void onResult(Result result);
  }
}

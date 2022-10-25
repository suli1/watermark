package com.example.watermark;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
  private final static String TAG = "Watermark";

  private final static int REQUEST_PERMISSION_STORAGE = 101;
  private final static int REQUEST_PERMISSION_ALL_FILES = 102;
  private final static int REQUEST_PICK_IMAGES = 103;

  private List<Uri> imagesList;

  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    requestPermissions();

    findViewById(R.id.bntPickImages).setOnClickListener(view -> pickImages());
    findViewById(R.id.btnAddWatermark).setOnClickListener(view -> addWatermark());
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_PICK_IMAGES && data != null) {
      imagesList = new ArrayList<>();

      if (data.getData() != null) {
        Uri imageUri = data.getData();
        imagesList.add(imageUri);
        Log.d(TAG, "Selected image:" + imageUri);
      } else if (data.getClipData() != null) {
        ClipData mClipData = data.getClipData();

        for (int i = 0; i < mClipData.getItemCount(); i++) {
          ClipData.Item item = mClipData.getItemAt(i);
          Uri uri = item.getUri();
          imagesList.add(uri);
          Log.i(TAG, "Selected images:" + uri);
        }
      }
    }
  }

  private void requestPermissions() {
    requestPermissions(new String[] {
        Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
    }, REQUEST_PERMISSION_STORAGE);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        requestPermissions(new String[] { Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION },
            REQUEST_PERMISSION_ALL_FILES);
      }
    }
  }

  private void pickImages() {
    Intent intent = new Intent();
    intent.setType("image/*");
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    intent.setAction(Intent.ACTION_GET_CONTENT);
    startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_PICK_IMAGES);
  }

  private void addWatermark() {
    for (Uri src : imagesList) {
      Uri output =
          Uri.fromFile(new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getPath(),
              "watermark_" + System.currentTimeMillis() + ".jpg"));
      Log.d(TAG, "output watermark file:" + output);
      new BitmapImageWatermarkTask(
          this,
          src,
          "2022-10-23",
          0,
          "topLeft",
          16,
          output,
          response -> {
            if (response.success) {
              Log.i(TAG, "Add watermark to (" + src + ")");
            } else {
              Log.e(TAG, "Add watermark to (" + src + ") failed!");
            }
          }
      ).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }
}

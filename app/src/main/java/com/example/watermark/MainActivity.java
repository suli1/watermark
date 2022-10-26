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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
  private final static String TAG = "Watermark";

  private final static int REQUEST_PERMISSION_STORAGE = 101;
  private final static int REQUEST_PERMISSION_ALL_FILES = 102;
  private final static int REQUEST_PICK_IMAGES = 103;

  private final List<WatermarkImageBean> imagesList = new ArrayList<>();

  RecyclerView recyclerView;
  WatermarkImageAdapter adapter;

  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    requestPermissions();

    findViewById(R.id.bntPickImages).setOnClickListener(view -> pickImages());
    findViewById(R.id.btnAddWatermark).setOnClickListener(view -> addWatermark());
    recyclerView = findViewById(R.id.recyclerView);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    adapter = new WatermarkImageAdapter();
    recyclerView.setAdapter(adapter);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_PICK_IMAGES && data != null) {

      if (data.getData() != null) {
        Uri imageUri = data.getData();
        imagesList.add(new WatermarkImageBean(imageUri));
        adapter.notifyDataSetChanged();
        Log.d(TAG, "Selected image:" + imageUri);
      } else if (data.getClipData() != null) {
        ClipData mClipData = data.getClipData();
        for (int i = 0; i < mClipData.getItemCount(); i++) {
          ClipData.Item item = mClipData.getItemAt(i);
          Uri uri = item.getUri();
          imagesList.add(new WatermarkImageBean(uri));
          Log.i(TAG, "Selected images:" + uri);
        }
        adapter.notifyDataSetChanged();
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
    for (int i = 0; i < imagesList.size(); i++) {
      final WatermarkImageBean src = imagesList.get(i);
      if (src.watermarked != null) {
        continue;
      }

      final int index = i;
      Uri output =
          Uri.fromFile(new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getPath(),
              "watermark_" + index + ".jpg"));
      Log.d(TAG, "output watermark file:" + output);
      new BitmapImageWatermarkTask(
          this,
          src.original,
          0,
          "bottomRight",
          output,
          response -> {
            if (response.success) {
              src.watermarked = output;
              adapter.notifyItemChanged(index);
              Log.i(TAG, "Add watermark to (" + src.watermarked + ")");
            } else {
              Log.e(TAG, "Add watermark to (" + src.original + ") failed!");
            }
          }
      ).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  class WatermarkImageAdapter extends RecyclerView.Adapter<WatermarkImageViewHolder> {

    @NonNull @Override
    public WatermarkImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View itemView = LayoutInflater.from(MainActivity.this)
          .inflate(R.layout.watermark_image_item, parent, false);
      return new WatermarkImageViewHolder(itemView);
    }

    @Override public void onBindViewHolder(@NonNull WatermarkImageViewHolder holder, int position) {
      WatermarkImageBean data = imagesList.get(position);
      holder.tvIndex.setText(String.valueOf(position));
      final String path = data.original.toString();
      holder.tvImageName.setText(path.substring(path.lastIndexOf('/') + 1));
      holder.ivOriginal.setImageURI(data.original);
      holder.btnWatermarked.setEnabled(data.watermarked != null);
    }

    @Override public int getItemCount() {
      return imagesList.size();
    }
  }

  class WatermarkImageViewHolder extends RecyclerView.ViewHolder {
    final TextView tvIndex;
    final TextView tvImageName;
    final ImageView ivOriginal;
    final Button btnWatermarked;

    public WatermarkImageViewHolder(@NonNull View itemView) {
      super(itemView);
      tvIndex = itemView.findViewById(R.id.tvIndex);
      tvImageName = itemView.findViewById(R.id.tvImageName);
      ivOriginal = itemView.findViewById(R.id.ivOriginal);
      btnWatermarked = itemView.findViewById(R.id.btnWatermarked);
    }
  }
}



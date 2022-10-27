package com.example.watermark;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
  private final static String TAG = "Watermark";

  private final static int REQUEST_PERMISSION_STORAGE = 101;
  private final static int REQUEST_PERMISSION_ALL_FILES = 102;

  private final List<WatermarkImageBean> imagesList = new ArrayList<>();

  RecyclerView recyclerView;
  WatermarkImageAdapter adapter;

  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    requestPermissions();

    findViewById(R.id.bntPickImages).setOnClickListener(view -> pickImages());
    findViewById(R.id.bntPickDir).setOnClickListener(view -> pickDir());

    findViewById(R.id.btnAddWatermark).setOnClickListener(view -> addWatermark());

    recyclerView = findViewById(R.id.recyclerView);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    adapter = new WatermarkImageAdapter();
    recyclerView.setAdapter(adapter);
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

  final ActivityResultLauncher<Intent> pickImagesLauncher =
      registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
          Intent data = result.getData();
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
      });

  private void pickImages() {
    Intent intent = new Intent();
    intent.setType("image/*");
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    intent.setAction(Intent.ACTION_GET_CONTENT);
    pickImagesLauncher.launch(Intent.createChooser(intent, "Select Picture"));
  }

  final ActivityResultLauncher<Intent> pickDirLauncher =
      registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
          Log.d(TAG, "dir:" + result.getData());
          Uri uri = result.getData().getData();
          DocumentFile dir = DocumentFile.fromTreeUri(this, uri);
          if (dir != null) {
            new Thread(new Runnable() {
              @Override public void run() {
                int index = 0;
                DocumentFile[] files = dir.listFiles();
                for (DocumentFile file : files) {
                  Log.i(TAG, "dir >" + index++ + ":" + file.getUri() + ",type:" + file.getType());
                  String fileType = file.getType();
                  if (fileType != null && fileType.startsWith("image")) {
                    imagesList.add(new WatermarkImageBean(file.getUri()));
                  }
                }
                runOnUiThread(() -> adapter.notifyDataSetChanged());
              }
            }).start();
          }
        }
      });

  private void pickDir() {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    i.addCategory(Intent.CATEGORY_DEFAULT);
    pickDirLauncher.launch(Intent.createChooser(i, "Choose directory"));
  }

  private void addWatermark() {
    for (int i = 0; i < imagesList.size(); i++) {
      final WatermarkImageBean src = imagesList.get(i);
      if (src.watermarked != null) {
        continue;
      }

      final int index = i;

      new BitmapImageWatermarkTask(
          this,
          src.original,
          0,
          "bottomRight",
          index,
          response -> {
            if (response.output != null) {
              src.watermarked = response.output;
              adapter.notifyItemChanged(index);
              Log.i(TAG, "Add watermark to (" + src.watermarked + ")");
            } else {
              Log.e(TAG, "Add watermark to (" + src.original + ") failed!" + response.error);
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
      //holder.ivOriginal.setImageURI(data.original);
      Glide.with(MainActivity.this).load(data.original).centerCrop().into(holder.ivOriginal);
      holder.btnWatermarked.setEnabled(data.watermarked != null);
    }

    @Override public int getItemCount() {
      return imagesList.size();
    }
  }

  static class WatermarkImageViewHolder extends RecyclerView.ViewHolder {
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



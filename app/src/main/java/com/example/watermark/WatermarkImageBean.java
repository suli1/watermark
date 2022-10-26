package com.example.watermark;

import android.net.Uri;

/**
 * Created by suli on 2022/10/26
 **/
public class WatermarkImageBean {
  public final Uri original;
  public Uri watermarked;

  public WatermarkImageBean(Uri original) {
    this.original = original;
    this.watermarked = null;
  }
}

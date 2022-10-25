package com.example.watermark;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Utility class that deals with operations with an bitmap.
 */
public final class BitmapUtil {

    static final Rect EMPTY_RECT = new Rect();

    /**
     * Used to know the max texture size allowed to be rendered
     */
    private static int mMaxTextureSize;

    /**
     * Crop image bitmap from given bitmap using the given points in the original bitmap and the given
     * rotation.<br>
     * if the rotation is not 0,90,180 or 270 degrees then we must first crop a larger area of the
     * image that contains the requires rectangle, rotate and then crop again a sub rectangle.<br>
     * If crop fails due to OOM we scale the cropping image by 0.5 every time it fails until it is
     * small enough.
     */
    static BitmapSampled cropBitmapObjectHandleOOM(Bitmap bitmap, RectF fittedCropRect) {
        int scale = 1;
        while (true) {
            try {
                Bitmap cropBitmap = cropBitmapObjectWithScale(bitmap, fittedCropRect, 1 / (float) scale);
                return new BitmapSampled(cropBitmap, scale);
            } catch (OutOfMemoryError e) {
                scale *= 2;
                if (scale > 8) {
                    throw e;
                }
            }
        }
    }

    /**
     * Crop image bitmap from given bitmap using the given points in the original bitmap and the given
     * rotation.<br>
     * if the rotation is not 0,90,180 or 270 degrees then we must first crop a larger area of the
     * image that contains the requires rectangle, rotate and then crop again a sub rectangle.
     *
     * @param scale how much to scale the cropped image part, use 0.5 to lower the image by half (OOM
     *              handling)
     */
    private static Bitmap cropBitmapObjectWithScale(Bitmap bitmap, RectF fittedCrop, float scale) {

        // get the rectangle in original image that contains the required cropped area (larger for non
        // rectangular crop)
        Rect rect = getRectFromFittedCrop(fittedCrop, bitmap.getWidth(), bitmap.getHeight());

        // crop and rotate the cropped image in one operation
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        Bitmap result =
                Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height(), matrix, true);

        if (result == bitmap) {
            // corner case when all bitmap is selected, no worth optimizing for it
            result = bitmap.copy(bitmap.getConfig(), false);
        }

        return result;
    }

    /**
     * Crop image bitmap from URI by decoding it with specific width and height to down-sample if
     * required.<br>
     * Additionally if OOM is thrown try to increase the sampling (2,4,8).
     */
    public static BitmapSampled cropBitmap(
            Context context,
            Uri loadedImageUri,
            RectF fittedCrop,
            int orgWidth,
            int orgHeight
    ) {
        int sampleMulti = 1;
        while (true) {
            try {
                // if successful, just return the resulting bitmap
                return cropBitmap(
                        context,
                        loadedImageUri,
                        fittedCrop,
                        orgWidth,
                        orgHeight,
                        sampleMulti);
            } catch (OutOfMemoryError e) {
                // if OOM try to increase the sampling to lower the memory usage
                sampleMulti *= 2;
                if (sampleMulti > 16) {
                    throw new RuntimeException(
                            "Failed to handle OOM by sampling ("
                                    + sampleMulti
                                    + "): "
                                    + loadedImageUri
                                    + "\r\n"
                                    + e.getMessage(),
                            e);
                }
            }
        }
    }

    /**
     * Decode bitmap from stream using sampling to get bitmap with the requested limit.
     */
    static BitmapSampled decodeSampledBitmap(Context context, Uri uri, int reqWidth, int reqHeight) {

        try {
            ContentResolver resolver = context.getContentResolver();

            // First decode with inJustDecodeBounds=true to check dimensions
            BitmapFactory.Options options = decodeImageForOption(resolver, uri);

            if (options.outWidth == -1 && options.outHeight == -1)
                throw new RuntimeException("File is not a picture");

            // Calculate inSampleSize
            options.inSampleSize =
                    Math.max(
                            calculateInSampleSizeByReqestedSize(
                                    options.outWidth, options.outHeight, reqWidth, reqHeight),
                            calculateInSampleSizeByMaxTextureSize(options.outWidth, options.outHeight));

            // Decode bitmap with inSampleSize set
            Bitmap bitmap = decodeImage(resolver, uri, options);

            return new BitmapSampled(bitmap, options.inSampleSize);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load sampled bitmap: " + uri + "\r\n" + e.getMessage(), e);
        }
    }

    static Rect getRectFromFittedCrop(RectF fittedCropRect, int imageWidth, int imageHeight) {
        int left = Math.round(fittedCropRect.left * imageWidth);
        int top = Math.round(fittedCropRect.top * imageHeight);
        int right = Math.round(fittedCropRect.right * imageWidth);
        int bottom = Math.round(fittedCropRect.bottom * imageHeight);

        return new Rect(left, top, right, bottom);
    }


    /**
     * Write the given bitmap to the given uri using the given compression.
     */
    public static void writeBitmapToUri(
            Context context,
            Bitmap bitmap,
            Uri uri,
            Bitmap.CompressFormat compressFormat,
            int compressQuality)
            throws FileNotFoundException {
        OutputStream outputStream = null;
        try {
            outputStream = context.getContentResolver().openOutputStream(uri);
            bitmap.compress(compressFormat, compressQuality, outputStream);
        } finally {
            closeSafe(outputStream);
        }
    }

    public static Bitmap addWatermarkToBitmap(Bitmap src, Bitmap watermark, float ratio, String location, int offset) {
        int width = src.getWidth();
        int height = src.getHeight();

        Bitmap ret = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(ret);
        canvas.drawBitmap(src, 0, 0, null);

        float scale = (width * ratio) / watermark.getWidth();
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        watermark = Bitmap.createBitmap(watermark, 0, 0, watermark.getWidth(), watermark.getHeight(), matrix, true);

        switch (location) {
            case "topLeft":
                canvas.drawBitmap(watermark, offset, offset, null);
                break;
            case "topRight":
                canvas.drawBitmap(watermark, width - watermark.getWidth() - offset, offset, null);
                break;
            case "bottomLeft":
                canvas.drawBitmap(watermark, offset, height - watermark.getHeight() - offset, null);
                break;
            case "bottomRight":
                canvas.drawBitmap(watermark, width - watermark.getWidth() - offset, height - watermark.getHeight() - offset, null);
                break;
        }

        canvas.save();

        canvas.restore();

        return ret;
    }

    public static Bitmap addWatermarkToBitmap(Bitmap src, String text, float ratio, String location, int offset) {
        int width = src.getWidth();
        int height = src.getHeight();

        Bitmap ret = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(ret);
        canvas.drawBitmap(src, 0, 0, null);

        Paint paint = new Paint();
        paint.setTextSize(16);
        paint.setColor(0xFFFF0000);

        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);


        float scale = (width * ratio) / bounds.width();
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);


        switch (location) {
            case "topLeft":
                canvas.drawText(text, offset, offset, paint);
                break;
            case "topRight":
                canvas.drawText(text, width - bounds.width() - offset, offset, paint);
                break;
            case "bottomLeft":
                canvas.drawText(text, offset, height - bounds.height() - offset, paint);
                break;
            case "bottomRight":
                canvas.drawText(text, width - bounds.width() - offset, height - bounds.height() - offset, paint);
                break;
        }

        canvas.save();

        canvas.restore();

        return ret;
    }


    // region: Private methods

    /**
     * Crop image bitmap from URI by decoding it with specific width and height to down-sample if
     * required.
     *
     * @param orgWidth    used to get rectangle from points (handle edge cases to limit rectangle)
     * @param orgHeight   used to get rectangle from points (handle edge cases to limit rectangle)
     * @param sampleMulti used to increase the sampling of the image to handle memory issues.
     */
    private static BitmapSampled cropBitmap(
            Context context,
            Uri loadedImageUri,
            RectF fittedCropRect,
            int orgWidth,
            int orgHeight,
            int sampleMulti) {

        // get the rectangle in original image that contains the required cropped area (larger for non
        // rectangular crop)
        Rect rect = getRectFromFittedCrop(fittedCropRect, orgWidth, orgHeight);

        int width = rect.width();
        int height = rect.height();

        Bitmap result = null;
        int sampleSize = 1;
        try {
            // decode only the required image from URI, optionally sub-sampling if reqWidth/reqHeight is
            // given.
            BitmapSampled bitmapSampled =
                    decodeSampledBitmapRegion(context, loadedImageUri, rect, width, height, sampleMulti);
            result = bitmapSampled.bitmap;
            sampleSize = bitmapSampled.sampleSize;
        } catch (Exception ignored) {
        }

        if (result != null) {
            return new BitmapSampled(result, sampleSize);
        } else {
            // failed to decode region, may be skia issue, try full decode and then crop
            return cropBitmap(
                    context,
                    loadedImageUri,
                    fittedCropRect,
                    sampleMulti,
                    rect,
                    width,
                    height
            );
        }
    }

    /**
     * Crop bitmap by fully loading the original and then cropping it, fallback in case cropping
     * region failed.
     */
    private static BitmapSampled cropBitmap(
            Context context,
            Uri loadedImageUri,
            RectF fittedCropRect,
            int sampleMulti,
            Rect rect,
            int width,
            int height
    ) {
        Bitmap result = null;
        int sampleSize;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize =
                    sampleSize =
                            sampleMulti
                                    * calculateInSampleSizeByReqestedSize(rect.width(), rect.height(), width, height);

            Bitmap fullBitmap = decodeImage(context.getContentResolver(), loadedImageUri, options);
            if (fullBitmap != null) {
                try {
                    // adjust crop points by the sampling because the image is smaller
                    result = cropBitmapObjectWithScale(fullBitmap, fittedCropRect, 1);
                } finally {
                    if (result != fullBitmap) {
                        fullBitmap.recycle();
                    }
                }
            }
        } catch (OutOfMemoryError e) {
            if (result != null) {
                result.recycle();
            }
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load sampled bitmap: " + loadedImageUri + "\r\n" + e.getMessage(), e);
        }
        return new BitmapSampled(result, sampleSize);
    }


    /**
     * Decode image from uri using "inJustDecodeBounds" to get the image dimensions.
     */
    public static BitmapFactory.Options decodeImageForOption(ContentResolver resolver, Uri uri)
            throws FileNotFoundException {
        InputStream stream = null;
        try {
            stream = resolver.openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(stream, EMPTY_RECT, options);
            options.inJustDecodeBounds = false;
            return options;
        } finally {
            closeSafe(stream);
        }
    }

    /**
     * Decode image from uri using given "inSampleSize", but if failed due to out-of-memory then raise
     * the inSampleSize until success.
     */
    public static Bitmap decodeImage(
            ContentResolver resolver, Uri uri, BitmapFactory.Options options)
            throws FileNotFoundException {
        do {
            InputStream stream = null;
            try {
                stream = resolver.openInputStream(uri);
                return BitmapFactory.decodeStream(stream, EMPTY_RECT, options);
            } catch (OutOfMemoryError e) {
                options.inSampleSize *= 2;
            } finally {
                closeSafe(stream);
            }
        } while (options.inSampleSize <= 512);
        throw new RuntimeException("Failed to decode image: " + uri);
    }

    /**
     * Decode specific rectangle bitmap from stream using sampling to get bitmap with the requested
     * limit.
     *
     * @param sampleMulti used to increase the sampling of the image to handle memory issues.
     */
    private static BitmapSampled decodeSampledBitmapRegion(
            Context context, Uri uri, Rect rect, int reqWidth, int reqHeight, int sampleMulti) {
        InputStream stream = null;
        BitmapRegionDecoder decoder = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize =
                    sampleMulti
                            * calculateInSampleSizeByReqestedSize(
                            rect.width(), rect.height(), reqWidth, reqHeight);

            stream = context.getContentResolver().openInputStream(uri);
            decoder = BitmapRegionDecoder.newInstance(stream, false);
            do {
                try {
                    return new BitmapSampled(decoder.decodeRegion(rect, options), options.inSampleSize);
                } catch (OutOfMemoryError e) {
                    options.inSampleSize *= 2;
                }
            } while (options.inSampleSize <= 512);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load sampled bitmap: " + uri + "\r\n" + e.getMessage(), e);
        } finally {
            closeSafe(stream);
            if (decoder != null) {
                decoder.recycle();
            }
        }
        return new BitmapSampled(null, 1);
    }


    /**
     * Calculate the largest inSampleSize value that is a power of 2 and keeps both height and width
     * larger than the requested height and width.
     */
    private static int calculateInSampleSizeByReqestedSize(
            int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            while ((height / 2 / inSampleSize) > reqHeight && (width / 2 / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Calculate the largest inSampleSize value that is a power of 2 and keeps both height and width
     * smaller than max texture size allowed for the device.
     */
    private static int calculateInSampleSizeByMaxTextureSize(int width, int height) {
        int inSampleSize = 1;
        if (mMaxTextureSize == 0) {
            mMaxTextureSize = getMaxTextureSize();
        }
        if (mMaxTextureSize > 0) {
            while ((height / inSampleSize) > mMaxTextureSize
                    || (width / inSampleSize) > mMaxTextureSize) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Get the max size of bitmap allowed to be rendered on the device.<br>
     * http://stackoverflow.com/questions/7428996/hw-accelerated-activity-how-to-get-opengl-texture-size-limit.
     */
    private static int getMaxTextureSize() {
        // Safe minimum default size
        final int IMAGE_MAX_BITMAP_DIMENSION = 2048;

        try {
            // Get EGL Display
            EGL10 egl = (EGL10) EGLContext.getEGL();
            EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

            // Initialise
            int[] version = new int[2];
            egl.eglInitialize(display, version);

            // Query total number of configurations
            int[] totalConfigurations = new int[1];
            egl.eglGetConfigs(display, null, 0, totalConfigurations);

            // Query actual list configurations
            EGLConfig[] configurationsList = new EGLConfig[totalConfigurations[0]];
            egl.eglGetConfigs(display, configurationsList, totalConfigurations[0], totalConfigurations);

            int[] textureSize = new int[1];
            int maximumTextureSize = 0;

            // Iterate through all the configurations to located the maximum texture size
            for (int i = 0; i < totalConfigurations[0]; i++) {
                // Only need to check for width since opengl textures are always squared
                egl.eglGetConfigAttrib(
                        display, configurationsList[i], EGL10.EGL_MAX_PBUFFER_WIDTH, textureSize);

                // Keep track of the maximum texture size
                if (maximumTextureSize < textureSize[0]) {
                    maximumTextureSize = textureSize[0];
                }
            }

            // Release
            egl.eglTerminate(display);

            // Return largest texture size found, or default
            return Math.max(maximumTextureSize, IMAGE_MAX_BITMAP_DIMENSION);
        } catch (Exception e) {
            return IMAGE_MAX_BITMAP_DIMENSION;
        }
    }


    /**
     * Close the given closeable object (Stream) in a safe way: check if it is null and catch-log
     * exception thrown.
     *
     * @param closeable the closable object to close
     */
    private static void closeSafe(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
    // endregion

    // region: Inner class: BitmapSampled

    /**
     * Holds bitmap instance and the sample size that the bitmap was loaded/cropped with.
     */
    public static final class BitmapSampled {

        /**
         * The bitmap instance
         */
        public final Bitmap bitmap;

        /**
         * The sample size used to lower the size of the bitmap (1,2,4,8,...)
         */
        public final int sampleSize;

        BitmapSampled(Bitmap bitmap, int sampleSize) {
            this.bitmap = bitmap;
            this.sampleSize = sampleSize;
        }
    }
    // endregion
}

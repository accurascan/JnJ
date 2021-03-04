package com.accurascan.ocr.mrz.motiondetection;

import android.util.Log;

public class RgbMotionDetection implements IMotionDetection {

    // Specific settings
    private static final int mPixelThreshold = 40; // Difference in pixel (RGB)
    private static int mThreshold = 10000; // Number of different pixels
    // (RGB)

    private static int[] mPrevious = null;
    private static int mPreviousWidth = 0;
    private static int mPreviousHeight = 0;
    Runtime gfg = Runtime.getRuntime();

    /**
     * {@inheritDoc}
     */
    @Override
    public int[] getPrevious() {
        return ((mPrevious != null) ? mPrevious.clone() : null);
    }

    protected static boolean isDifferent(int[] first, int width, int height) {
        if (first == null) throw new NullPointerException();

        if (mPrevious == null) return false;
        if (first.length != mPrevious.length) return true;
        if (mPreviousWidth != width || mPreviousHeight != height) return true;

        int totDifferentPixels = 0;
        for (int i = 0, ij = 0; i < height; i++) {
            for (int j = 0; j < width; j++, ij++) {
                int pix = (0xff & (first[ij]));
                int otherPix = (0xff & (mPrevious[ij]));

                // Catch any pixels that are out of range
                if (pix < 0) pix = 0;
                if (pix > 255) pix = 255;
                if (otherPix < 0) otherPix = 0;
                if (otherPix > 255) otherPix = 255;

                if (Math.abs(pix - otherPix) >= mPixelThreshold) {
                    totDifferentPixels++;
                    // Paint different pixel red
//                    first[ij] = Color.RED;
                }
            }
        }
        if (totDifferentPixels <= 0) totDifferentPixels = 1;
        boolean different = totDifferentPixels > mThreshold;
//        Log.e(TAG, "isDifferent: " + totDifferentPixels);

       /* int size = height * width;
        int percent = (int)(100/(size/(float)totDifferentPixels));
        String output = "Number of different pixels: " + totDifferentPixels + "> " + percent + "%";
        if (different) { Log.e(TAG, output); }
        else { Log.d(TAG, output); }*/

        return different;
    }

    /**
     * Detect motion comparing RGB pixel values. {@inheritDoc}
     */
    @Override
    public boolean detect(int[] rgb, int width, int height) {
        if (rgb == null) throw new NullPointerException();

        int[] original = rgb.clone();

        // Create the "mPrevious" picture, the one that will be used to check
        // the next frame against.
        if (mPrevious == null) {
            mPrevious = new int[original.length];
            mPrevious = original;
            mPreviousWidth = width;
            mPreviousHeight = height;
            // Log.i(TAG, "Creating background image");
            return false;
        }
        mThreshold = (int)((width * height) * 7.7/100);
        boolean motionDetected = isDifferent(rgb, width, height);

        // Replace the current image with the previous.
        mPrevious = null;
        mPrevious = new int[original.length];
        mPrevious = original;
        mPreviousWidth = width;
        mPreviousHeight = height;
        original = null;
        rgb = null;

        return motionDetected;
    }

    public boolean detect(int[] rgb, int width, int height, float i, float resolution) {
        if (rgb == null) throw new NullPointerException();

        int[] original = rgb.clone();

        // Create the "mPrevious" picture, the one that will be used to check
        // the next frame against.
        if (mPrevious == null) {
            mPrevious = new int[original.length];
            mPrevious = original;
            mPreviousWidth = width;
            mPreviousHeight = height;
            // Log.i(TAG, "Creating background image");
            return false;
        }
        float ratio = resolution / i;
        if (ratio > 0.5) ratio = i - (ratio * 3);
        else ratio = i;
        if (ratio < 5) ratio = 5;
        mThreshold = (int)((width * height) * ratio/100);
        boolean motionDetected = isDifferent(rgb, width, height);

        // Replace the current image with the previous.
        mPrevious = null;
        mPrevious = new int[original.length];
        mPrevious = original;
        mPreviousWidth = width;
        mPreviousHeight = height;
        original = null;
        rgb = null;
        return motionDetected;
    }
}

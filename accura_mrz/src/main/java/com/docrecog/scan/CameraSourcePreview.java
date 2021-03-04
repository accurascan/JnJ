package com.docrecog.scan;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import com.accurascan.ocr.mrz.util.Util;

public class CameraSourcePreview extends FrameLayout {
    private static final String TAG = CameraSourcePreview.class.getSimpleName();
    private final Context context;
    private final OcrCameraPreview ocrCameraPreview;
    private final SurfaceView surfaceView;
    int childXOffset = 0;
    int childYOffset = 0;
    int childWidth = 0;
    int childHeight = 0;

    public CameraSourcePreview(OcrCameraPreview ocrCameraPreview, Context context) {
        super(context);
        this.context = context;

        surfaceView = new SurfaceView(context);
        surfaceView.getHolder().addCallback(new SurfaceCallback());
        setOnTouchListener(new OnSurfaceTouchListener());
        addView(surfaceView);
        this.ocrCameraPreview = ocrCameraPreview;
    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // To Set preview for camera ratio
        // The isPreviewSet will be false then update surface view to maintain camera ratio for all device

        int width = ocrCameraPreview.getPreviewWidth();
        int height = ocrCameraPreview.getPreviewHeight();
        if (ocrCameraPreview.mCameraDevice != null) {
//            Size size = ocrCameraPreview.getPreviewSize();
//            if (size != null) {
//                width = (int) size.getWidth();
//                height = (int) size.getHeight();
//            }
            Camera.Size size = ocrCameraPreview.getPreviewSize();;
            if (size != null) {
                width = size.width;
                height = size.height;
            }
        }


        // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
        if (isPortraitMode()) {
            int tmp = width;
            width = height;
            height = tmp;
        }

        final int layoutWidth = right - left;
        final int layoutHeight = bottom - top;

        // Computes height and width for potentially doing fit width.
        int childWidth;
        int childHeight;
        int childXOffset = 0;
        int childYOffset = 0;
        float widthRatio = (float) layoutWidth / (float) width;
        float heightRatio = (float) layoutHeight / (float) height;

        // To fill the view with the camera preview, while also preserving the correct aspect ratio,
        // it is usually necessary to slightly oversize the child and to crop off portions along one
        // of the dimensions.  We scale up based on the dimension requiring the most correction, and
        // compute a crop offset for the other dimension.
        if (widthRatio > heightRatio) {
            childWidth = layoutWidth;
            childHeight = (int) ((float) height * widthRatio);
            childYOffset = (childHeight - layoutHeight) / 2;
        } else {
            childWidth = (int) ((float) width * heightRatio);
            childHeight = layoutHeight;
            childXOffset = (childWidth - layoutWidth) / 2;
        }
        this.childXOffset = childXOffset;
        this.childYOffset = childYOffset;
        this.childWidth = childWidth;
        this.childHeight = childHeight;
        Util.logd(TAG, "onLayout: (" + childXOffset + "," + childYOffset + ")");
        for (int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).layout(
                    -1 * childXOffset, -1 * childYOffset,
                    childWidth - childXOffset, childHeight - childYOffset);
        }
    }

    public SurfaceHolder getHolder() {
        return surfaceView.getHolder();
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Util.logd(TAG, "surfaceCreated: ");
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Util.logd(TAG, "surfaceChanged: ");
// Make sure we have a surface in the holder before proceeding.
            if (holder.getSurface() == null) {
                Util.logd(TAG, "holder.getSurface() == null");
                return;
            }
            ocrCameraPreview.startIfReady(holder);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Util.logd(TAG, "surfaceDestroyed: ");
            ocrCameraPreview.stopPreview();
        }
    }

    private class OnSurfaceTouchListener implements OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return ocrCameraPreview.onTouchView(v, event);
        }
    }

    private boolean isPortraitMode() {
        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return false;
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return true;
        }
        return false;
    }
}
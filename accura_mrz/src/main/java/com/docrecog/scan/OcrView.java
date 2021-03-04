package com.docrecog.scan;

import android.app.Activity;
import android.view.ViewGroup;
import com.accurascan.ocr.mrz.interfaces.OcrCallback;
import com.accurascan.ocr.mrz.model.RecogResult;

public abstract class OcrView extends OcrCameraPreview {

    private final Activity context;
    private OcrCallback ocrCallBack;
    private ViewGroup cameraContainer;
    private int cameraFacing;
    private int statusBarHeight = 0;
    private RecogType recogType = RecogType.MRZ;

    public OcrView(Activity context) {
        super(context);
        this.context = context;
    }

    public abstract void onPlaySound();

    /**
     * add call back to get camera update and ocr
     *
     * @param ocrCallBack
     * @return
     */
    public OcrView setOcrCallBack(OcrCallback ocrCallBack) {
        this.ocrCallBack = ocrCallBack;
        return this;
    }

    /**
     * add camera on this view
     *
     * @param view add camera preview to this view
     * @return
     */
    public OcrView setView(ViewGroup view) {
        this.cameraContainer = view;
        return this;
    }

    public OcrView setCameraFacing(int cameraFacing){
        this.cameraFacing = cameraFacing;
        return this;
    }

    /**
     * Set Camera type to scan Mrz or Ocr+Mrz
     *
     * @param recogType
     * @return
     */
    public OcrView setRecogType(RecogType recogType) {
        this.recogType = recogType;
        return this;
    }

    public OcrView setStatusBarHeight(int statusBarHeight) {
        this.statusBarHeight = statusBarHeight;
        return this;
    }

    /**
     * call this method to initialized camera and ocr
     */
    public void init() {
//        if (this.recogType == null) {
//            throw new NullPointerException("Must have to set recogType");
//        }
        if (this.cameraContainer == null) {
            throw new NullPointerException("Must have to setView");
        }
        if (this.ocrCallBack == null) {
            throw new NullPointerException(context.getClass().getName() + " must have to implement " + OcrCallback.class.getName());
        }
        setLayout(cameraContainer)
        .setType(RecogType.MRZ)
        .setHeight(statusBarHeight)
        .setFacing(cameraFacing)
        .start();
    }

    public void flipCamera(int i){
        this.cameraFacing = i;
        setFacing(this.cameraFacing);
        restartPreview();
    }

    /**
     * To handle camera on window focus update
     *
     * @param hasFocus
     */
    public void onFocusUpdate(boolean hasFocus) {
        onWindowFocusChanged(hasFocus);
    }

    /**
     * Call this method from
     *
     * @see com.accurascan.ocr.mrz.interfaces.OcrCallback#onUpdateLayout(int, int)
     * to start your camera preview and ocr
     */
    public void startOcrScan() {
        startOcr();
    }

    /**
     * call on activity resume to restart preview
     */
    public void resume() {
        onResume();
    }

    /**
     * call on activity pause to stop preview
     */
    public void pause() {
        onPause();
    }

    /**
     * call destroy method to stop camera preview
     */
    public void destroy() {
        ocrCallBack = null;
        onDestroy();
    }


    @Override
    void onProcessUpdate(int s, String s1, boolean b) {
        if (ocrCallBack != null) {
//            context.runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
                    OcrView.this.ocrCallBack.onProcessUpdate(s, s1, b);
//                }
//            });
        }
    }

    @Override
    void onError(String s) {
        if (ocrCallBack != null) {
//            context.runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
                    OcrView.this.ocrCallBack.onError(s);
//                }
//            });
        }
    }

    @Override
    void onUpdateLayout(int width, int height) {
        if (ocrCallBack != null) {
//            context.runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
                    OcrView.this.ocrCallBack.onUpdateLayout(width, height);
//                }
//            });
        }
    }

    @Override
    void onScannedComplete(Object result) {
        onPlaySound();
        if (ocrCallBack != null) {
            OcrView.this.ocrCallBack.onScannedComplete((RecogResult) result);
//            context.runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    if (recogType == RecogType.OCR && result instanceof OcrData) {
////                        OcrView.this.ocrCallBack.onScannedComplete((OcrData) result, null, null);
//                        OcrView.this.ocrCallBack.onScannedComplete(result);
//                    } else if (recogType == RecogType.MRZ && result instanceof RecogResult) {
////                        OcrView.this.ocrCallBack.onScannedComplete(null, (RecogResult) result, null);
//                        OcrView.this.ocrCallBack.onScannedComplete(result);
//                    }
//                }
//            });
        }
    }
}

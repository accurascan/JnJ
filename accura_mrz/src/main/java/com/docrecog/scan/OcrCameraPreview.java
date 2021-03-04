package com.docrecog.scan;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.CameraProfile;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.accurascan.ocr.mrz.R;
import com.accurascan.ocr.mrz.camerautil.CameraHolder;
import com.accurascan.ocr.mrz.camerautil.FocusManager;
import com.accurascan.ocr.mrz.model.InitModel;
import com.accurascan.ocr.mrz.model.RecogResult;
import com.accurascan.ocr.mrz.motiondetection.ImageProcessing;
import com.accurascan.ocr.mrz.motiondetection.RgbMotionDetection;
import com.accurascan.ocr.mrz.motiondetection.data.GlobalData;
import com.accurascan.ocr.mrz.util.AccuraLog;
import com.accurascan.ocr.mrz.util.BitmapUtil;
import com.accurascan.ocr.mrz.util.Util;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.accurascan.ocr.mrz.camerautil.CameraSource.FOCUSING;
import static com.accurascan.ocr.mrz.camerautil.CameraSource.IDLE;
import static com.accurascan.ocr.mrz.camerautil.CameraSource.PREVIEW_STOPPED;
import static com.accurascan.ocr.mrz.camerautil.FocusManager.isSupported;

abstract class OcrCameraPreview extends RecogEngine.ScanListener implements Camera.PreviewCallback, FocusManager.Listener {

    private final RgbMotionDetection detection;
    private boolean isPreviewStarted = false;
    private InitModel i1 = null;

    abstract void onProcessUpdate(int s, String s1, boolean b);

    abstract void onError(String s);

    abstract void onUpdateLayout(int width, int height);

    abstract void onScannedComplete(Object result);

    private static final String TAG = OcrCameraPreview.class.getSimpleName();

    private CameraSourcePreview cameraSourcePreview;
    protected Camera mCameraDevice;
    Camera.Parameters mParameters;
    int mPreviewWidth = 1280;
    int mPreviewHeight = 720;
    SurfaceHolder mSurfaceHolder;
    private int mCameraId;
    private Camera.Size previewSize;
    private int mCameraState = PREVIEW_STOPPED;

    // The subset of parameters we need to update in setCameraParameters().
    private static final int UPDATE_PARAM_INITIALIZE = 1;
    private static final int UPDATE_PARAM_PREFERENCE = 4;
    private static final int UPDATE_PARAM_ALL = -1;

    private static final int FIRST_TIME_INIT = 0;
    private static final int CLEAR_SCREEN_DELAY = 1;
    private static final int TRIGER_RESTART_RECOG = 5;
    private static final int STOP_RECOG = 6;
    private static final int TRIGER_RESTART_RECOG_DELAY = 40; //30 ms

    private static final int SNAPSHOT_IN_PROGRESS = 3;
    private static final int SELFTIMER_COUNTING = 4;

    final Handler mHandler = new MainHandler();

    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();
    private final AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback();

    // This handles everything about focus.
    FocusManager mFocusManager;
    private boolean mOpenCameraFail = false;
    private boolean mCameraDisabled = false, isTouchCalled;
    private boolean mOnResumePending;
    private boolean mPausing;
    private boolean mFirstTimeInitialized;
    private boolean mFirstInitialized = false;

    // The display rotation in degrees. This is only valid when mCameraState is
    // not PREVIEW_STOPPED.
    private int mDisplayRotation;
    // The value for android.hardware.Camera.setDisplayOrientation.
    private int mDisplayOrientation;
    //
    private int rotation;
        private final Lock _mutex = new ReentrantLock(true);
    private Thread mCameraOpenThread = new Thread(new Runnable() {
        public void run() {
            try {
                mCameraDevice = Util.openCamera(mActivity, mCameraId);
            } catch (Exception e) {
                mOpenCameraFail = true;
                mCameraDisabled = true;
            }
        }
    });
    private Thread mCameraPreviewThread = null;

    private Activity mActivity;
    private int countryId = -1;
    private int cardId = -1;
    //    private OcrCallback ocrCallBack;
    private ViewGroup cameraContainer;
    private int facing = 0;
//    private boolean isSetPlayer = true;

    private RecogEngine recogEngine;
    private RecogResult g_recogResult;
    private int rectW, rectH;
    private DisplayMetrics dm;
    private boolean isbothavailable = false;
    private int checkmrz = 0;
    //    private MediaPlayer mediaPlayer = null;
//    private AudioManager audioManager = null;
    private boolean isValidate = false;
    private int titleBarHeight = 0;
    private RecogType recogType = null;
    private MRZDocumentType mrzDocumentType = MRZDocumentType.NONE;
    private int mRecCnt = 0; //counter for mrz detecting
    private int bRet = 0; //counter for mrz detecting
    private int fCount = 0;
    private int frameCount = 2;

    private boolean isBlurSet = false;
    private boolean isInitialized;
    private int scanSide = 0;
//    private ProgressBar progressBar;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AccuraLog.loge(TAG, "handleMessage: " + msg.what);
            if (msg.what == 1) {
                init();
            } else if (msg.what == 0) {
                setInitialized(false);
            }
        }
    };

    private static final class NativeThread extends Thread {

        private final WeakReference<OcrCameraPreview> reference;

        private NativeThread(OcrCameraPreview activity) {
            reference = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            OcrCameraPreview mReference = reference.get();
            Util.logd(TAG, "Worker started");
            if (BitmapUtil.isPortraitMode(mReference.mActivity)) {
                mReference.rectW = mReference.dm.widthPixels - 20;
                mReference.rectH = (mReference.dm.heightPixels - mReference.titleBarHeight) / 3;
                Log.e(TAG, "run: frame" + mReference.dm.toString() + mReference.rectW + "x" + mReference.rectH);
            } else {
                mReference.rectH = (mReference.dm.heightPixels - mReference.titleBarHeight) - (int) (100 * mReference.dm.density);
                mReference.rectW = (int) (mReference.rectH / 0.69);
                Log.e(TAG, "run: frame" + mReference.dm.toString() + mReference.rectW + "x" + mReference.rectH);
            }
            if (RecogType.MRZ == mReference.recogType) {
                AccuraLog.loge(TAG, "InitializeM");
                InitModel initModel = mReference.recogEngine.initCard(mReference.mActivity, mReference.recogType == RecogType.MRZ ? 0 : 1);
                if (initModel != null && initModel.getResponseCode() == 1) {
                    mReference.onProcessUpdate(RecogEngine.SCAN_TITLE_MRZ_FRONT, null, false);
                    mReference.handler.sendEmptyMessage(1);
                } else {
                    mReference.onError(initModel.getResponseMessage());
                    mReference.handler.sendEmptyMessage(0);
                }
            }
            super.run();
        }
    }

    private static volatile AtomicBoolean processing = new AtomicBoolean(false);

    private static final class RecogThread extends Thread {
        private final WeakReference<OcrCameraPreview> reference;
        private byte[] data;
        private Camera camera;
        private Bitmap bmCard;
        private int ret;
        private long end;

        public RecogThread(OcrCameraPreview activity, byte[] bytes, Camera camera) {
            reference = new WeakReference<>(activity);
            this.data = bytes;
            this.camera = camera;
        }

        @Override
        public void run() {
            if (!processing.compareAndSet(false, true)) return;

            try {
                OcrCameraPreview mReference = reference.get();
                if (mReference != null) {
                    if (bmCard != null && !bmCard.isRecycled()) bmCard.recycle();
                    final Camera.Size size = camera.getParameters().getPreviewSize();
                    final int format = camera.getParameters().getPreviewFormat();

                    int[] ints = ImageProcessing.decodeYUV420SPtoRGB(data, size.width, size.height);
                    if (!mReference.detection.detect(ints, size.width, size.height, RecogEngine.mT, RecogEngine.v)/*mReference.recogEngine.doCheckFrame(data, size.width, size.height) > 0*/) {
//                        if (mReference.newMessage.contains(RecogEngine.ACCURA_ERROR_CODE_MOTION))
//                            mReference.onProcessUpdate(-1, "", false);
//                        bmCard = BitmapUtil.getBitmapFromData(data, size, format, mReference.rotation, mReference.rectH, mReference.rectW, mReference.recogType);
                        bmCard = BitmapUtil.getBitmapFromData(data, size, format, mReference.rotation, mReference.rectH, mReference.rectW, /*mReference.recogType,*/ mReference.cameraSourcePreview.childXOffset, mReference.cameraSourcePreview.childYOffset, mReference.cameraSourcePreview.childWidth, mReference.cameraSourcePreview.childHeight);

                        mReference._mutex.lock();

                        if (bmCard != null && mReference.recogEngine.checkLight(bmCard)) {
                            mReference.refreshPreview();
                            bmCard.recycle();
                            mReference._mutex.unlock(); // to restart thread
                            return;
                        }
                        if (bmCard != null && !bmCard.isRecycled()) {
                            if (mReference.recogEngine.checkValid(bmCard)) {
                                if (mReference.recogType == RecogType.MRZ) {
                                    Bitmap docBmp = bmCard.copy(Bitmap.Config.ARGB_8888, false);
                                    if (mReference.mrzDocumentType == MRZDocumentType.PASSPORT_MRZ || mReference.mrzDocumentType == MRZDocumentType.VISA_MRZ) {
                                        mReference.g_recogResult.recType = RecogEngine.RecType.INIT;
                                        long start = System.currentTimeMillis();
                                        Runnable runnable = new Runnable() {
                                            @Override
                                            public void run() {
                                                if (end < start) {
                                                    // Concat _clear with message to remove message after few seconds.
                                                    AccuraLog.loge(TAG, "Clear");
                                                    mReference.onUpdateProcess(RecogEngine.ACCURA_ERROR_CODE_PROCESSING.concat("_clear")/*"Processing..."*/);
                                                }
                                            }
                                        };
                                        Runnable runnable1 = () -> new Handler().postDelayed(runnable, 1800);
                                        mReference.mActivity.runOnUiThread(runnable1);
                                        ret = mReference.recogEngine.doRunData(bmCard, 0, mReference.g_recogResult, mReference.mrzDocumentType);
                                        end = System.currentTimeMillis();
                                        if (ret == 1) {
                                            mReference.onUpdateProcess(RecogEngine.ACCURA_ERROR_CODE_PROCESSING/*"Processing..."*/);
                                            Util.logd("ocr_log", "detectFace: Call");
                                            mReference.recogEngine.doFaceDetect(mReference.mRecCnt, docBmp, mReference.g_recogResult, new RecogEngine.ScanListener() {

                                                @Override
                                                void onScannedSuccess(boolean isDone, boolean isMRZRequired) {
                                                    if (mReference.mrzDocumentType == MRZDocumentType.VISA_MRZ) {
                                                        if (!isDone && mReference.bRet > -1) {
                                                            mReference.bRet++;
                                                        }
                                                        if (mReference.recogType == RecogType.MRZ) {
                                                            if ((mReference.bRet > mReference.frameCount || mReference.bRet == -1) && mReference.g_recogResult.recType == RecogEngine.RecType.MRZ && !mReference.g_recogResult.lines.equalsIgnoreCase("")) {
                                                                Util.logd(TAG, "INIT");
                                                                AccuraLog.loge(TAG, "onVDone");
                                                                mReference.g_recogResult.docFrontBitmap = bmCard.copy(Bitmap.Config.ARGB_8888, false);
                                                                mReference.sendInformation();
                                                            } else {
                                                                mReference.onScannedSuccess(isDone, isMRZRequired);
                                                            }
                                                            bmCard.recycle();
                                                            docBmp.recycle();
                                                        }
                                                    } else {
                                                        mReference.onScannedSuccess(isDone, isMRZRequired);
                                                        bmCard.recycle();
                                                        docBmp.recycle();
                                                    }

                                                }

                                                @Override
                                                void onFaceScanned(Bitmap bitmap) {
                                                    Util.logd("ocr_log", "detectFace: Done " + (mReference.g_recogResult.faceBitmap != null));
                                                    AccuraLog.loge(TAG, "mvpDone" + ((bitmap == null)?1:0));
                                                    if (mReference.recogType == RecogType.MRZ) {
                                                        if (mReference.g_recogResult.recType == RecogEngine.RecType.MRZ && !mReference.g_recogResult.lines.equalsIgnoreCase("")) {
                                                            mReference.g_recogResult.faceBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                                                            Util.logd(TAG, "INIT");
                                                            AccuraLog.loge(TAG, "onDone");
                                                            mReference.g_recogResult.docFrontBitmap = bmCard.copy(Bitmap.Config.ARGB_8888, false);
                                                            mReference.sendInformation();
                                                        } else {
                                                            mReference.g_recogResult.recType = RecogEngine.RecType.INIT;
                                                            mReference.g_recogResult.faceBitmap = null;
                                                            AccuraLog.loge(TAG, "onDone");
                                                            mReference.refreshPreview();
                                                        }
                                                        bmCard.recycle();
                                                        docBmp.recycle();
                                                    }
                                                }
                                            });
                                        } else {
                                            /*if (ret > 0 || ret == -1) {
                                                mReference.onUpdateProcess(RecogEngine.ACCURA_ERROR_CODE_RETRYING);
                                            } else*/ if (ret == -10) {
                                                mReference.onUpdateProcess(RecogEngine.ACCURA_ERROR_CODE_PASSPORT_MRZ);
                                            } else if (ret == -11) {
                                                mReference.onUpdateProcess(RecogEngine.ACCURA_ERROR_CODE_ID_MRZ);
                                            } else if (ret == -12) {
                                                mReference.onUpdateProcess(RecogEngine.ACCURA_ERROR_CODE_VISA_MRZ);
                                            } /*else if (ret == -13) {
                                                mReference.onUpdateProcess("D MRZ not detected");
                                            }*/ else if (ret == -3) {
                                                mReference.onUpdateProcess(RecogEngine.ACCURA_ERROR_CODE_MRZ);
                                            }
                                            mReference.refreshPreview();
                                            bmCard.recycle();
                                        }
                                        mReference.mRecCnt++; //counter increases
                                    } else {
                                        if (mReference.g_recogResult.recType == RecogEngine.RecType.INIT) {
                                            ret = mReference.recogEngine.doRunData(bmCard, 0, mReference.g_recogResult, mReference.mrzDocumentType);
                                            //                            if (ret <= 0 && mRecCnt > 2) {

                                            //                                if (mRecCnt % 4 == 1)
                                            //                            faceret = recogEngine.doRunFaceDetect(bmCard, g_recogResult);
                                            if (mReference.g_recogResult.faceBitmap == null) {
                                                mReference.recogEngine.doFaceDetect(mReference.mRecCnt, docBmp, mReference.g_recogResult, new RecogEngine.ScanListener() {

                                                    @Override
                                                    void onScannedSuccess(boolean isDone, boolean isMRZRequired) {
                                                        mReference.onScannedSuccess(isDone, isMRZRequired);
                                                    }

                                                    @Override
                                                    void onFaceScanned(Bitmap bitmap) {
                                                        if (mReference.recogType == RecogType.MRZ) {
                                                            if (mReference.g_recogResult.recType == RecogEngine.RecType.MRZ && !mReference.g_recogResult.lines.equalsIgnoreCase("")) {
                                                                mReference.g_recogResult.docFrontBitmap = bmCard.copy(Bitmap.Config.ARGB_8888, false);
                                                                mReference.sendInformation();
                                                            } else {
                                                                mReference.g_recogResult.docFrontBitmap = bmCard.copy(Bitmap.Config.ARGB_8888, false);
                                                                mReference.g_recogResult.recType = RecogEngine.RecType.FACE;
                                                                mReference.refreshPreview();
                                                            }
                                                            mReference.g_recogResult.faceBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                                                            bmCard.recycle();
                                                            docBmp.recycle();
                                                        }
                                                    }
                                                });
                                            }
                                            //                            }

                                            mReference.mRecCnt++; //counter increases
                                        } else if (mReference.g_recogResult.recType == RecogEngine.RecType.FACE) { //have to do mrz
                                            ret = mReference.recogEngine.doRunData(docBmp, 0, mReference.g_recogResult, mReference.mrzDocumentType);
                                            //                                    ret = mReference.recogEngine.doRunData(data, size.width, size.height, 0, mReference.mDisplayRotation, mReference.g_recogResult);
                                            if (mReference.bRet > -1) {
                                                mReference.bRet++;
                                            }
                                            mReference.mActivity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (ret > 0) {
                                                        mReference.mRecCnt = 0; //counter sets 0
                                                        Bitmap docBmp = bmCard;

                                                        if ((mReference.g_recogResult.recType == RecogEngine.RecType.MRZ && !mReference.g_recogResult.bRecDone) ||
                                                                (mReference.g_recogResult.recType == RecogEngine.RecType.FACE && mReference.g_recogResult.bRecDone)) {
                                                            if (mReference.bRet > 3 || mReference.bRet == -1) {
                                                                mReference.g_recogResult.docBackBitmap = docBmp.copy(Bitmap.Config.ARGB_8888, false);
                                                            } else {
                                                                mReference.g_recogResult.docFrontBitmap = docBmp.copy(Bitmap.Config.ARGB_8888, false);
                                                            }
                                                        }

                                                        if (mReference.g_recogResult.recType == RecogEngine.RecType.MRZ) {
                                                            mReference.g_recogResult.docFrontBitmap = docBmp.copy(Bitmap.Config.ARGB_8888, false);
                                                        }

                                                        if (mReference.g_recogResult.recType == RecogEngine.RecType.BOTH ||
                                                                mReference.g_recogResult.recType == RecogEngine.RecType.MRZ && mReference.g_recogResult.bRecDone)
                                                            mReference.g_recogResult.docFrontBitmap = docBmp.copy(Bitmap.Config.ARGB_8888, false);

                                                        docBmp.recycle();
                                                        bmCard.recycle();

                                                        if (mReference.g_recogResult.bRecDone) {
                                                            mReference.sendInformation();
                                                        } else {
                                                            //                                    onProcessUpdate(mActivity.getResources().getString(R.string.scan_front), null, true);
                                                            mReference.refreshPreview();
                                                        }
                                                    } else {
                                                    /*if (mRecCnt > 3 && faceret > 0) //detected only face, so need to detect mrz
                                                    {
                                                        mRecCnt = 0; //counter sets 0
                                                        faceret = 0;
                                                        g_recogResult.recType = RecogEngine.RecType.FACE;

                                                        Bitmap docBmp = bmCard;
                                                        g_recogResult.docFrontBitmap = docBmp.copy(Bitmap.Config.ARGB_8888, false);
                    //                                    docBmp.recycle();
                                                    } else*/
                                                        bmCard.recycle();
                                                        if (mReference.bRet == -1) {
                                                            if (ret == -3) {
                                                                mReference.onUpdateProcess(RecogEngine.ACCURA_ERROR_CODE_MRZ);
                                                            } else if (mReference.mrzDocumentType == MRZDocumentType.ID_CARD_MRZ && ret == -11) {
                                                                mReference.onUpdateProcess(RecogEngine.ACCURA_ERROR_CODE_ID_MRZ);
                                                            }
                                                        }
                                                        if (mReference.bRet == 3) {
                                                            mReference.bRet = -1;
                                                            mReference.onProcessUpdate(RecogEngine.SCAN_TITLE_MRZ_BACK, null, true);
                                                        }

                                                        if (mReference.g_recogResult.recType == RecogEngine.RecType.FACE || mReference.g_recogResult.faceBitmap != null) {
                                                            mReference.refreshPreview();
                                                        }
                                                    }
                                                }

                                            });
                                        } else if (mReference.g_recogResult.recType == RecogEngine.RecType.MRZ) { //have to do face
                                            if (mReference.g_recogResult.faceBitmap == null) {
                                                mReference.recogEngine.doFaceDetect(mReference.mRecCnt, docBmp, mReference.g_recogResult, new RecogEngine.ScanListener() {

                                                    @Override
                                                    void onScannedSuccess(boolean isDone, boolean isMRZRequired) {
                                                        if (mReference.mrzDocumentType == MRZDocumentType.NONE) {
                                                            if (!isDone && mReference.bRet > -1) {
                                                                mReference.bRet++;
                                                            }
                                                            if (mReference.recogType == RecogType.MRZ) {
                                                                if ((mReference.bRet > mReference.frameCount || mReference.bRet == -1) && mReference.g_recogResult.recType == RecogEngine.RecType.MRZ && !mReference.g_recogResult.lines.equalsIgnoreCase("")) {
                                                                    Util.logd(TAG, "INIT");
                                                                    AccuraLog.loge(TAG, "onVDone");
                                                                    mReference.g_recogResult.docFrontBitmap = bmCard.copy(Bitmap.Config.ARGB_8888, false);
                                                                    mReference.sendInformation();
                                                                } else {
                                                                    mReference.onScannedSuccess(isDone, isMRZRequired);
                                                                }
                                                                bmCard.recycle();
                                                                docBmp.recycle();
                                                            }
                                                        } else {
                                                            mReference.onScannedSuccess(isDone, isMRZRequired);
                                                            bmCard.recycle();
                                                            docBmp.recycle();
                                                        }
                                                    }

                                                    @Override
                                                    void onFaceScanned(Bitmap bitmap) {
                                                        if (mReference.recogType == RecogType.MRZ) {
                                                            if (mReference.g_recogResult.recType == RecogEngine.RecType.MRZ && mReference.g_recogResult.lines.equalsIgnoreCase("")) {
                                                                mReference.g_recogResult.faceBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                                                                mReference.g_recogResult.docFrontBitmap = bmCard.copy(Bitmap.Config.ARGB_8888, false);
                                                                mReference.sendInformation();
                                                            }
                                                            else {
                                                                mReference.g_recogResult.docFrontBitmap = bmCard.copy(Bitmap.Config.ARGB_8888, false);
                                                                bmCard.recycle();
                                                                mReference.g_recogResult.recType = RecogEngine.RecType.FACE;
                                                                mReference.refreshPreview();
                                                            }
                                                            bmCard.recycle();
                                                        }
                                                    }
                                                });
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (mReference.recogType == RecogType.MRZ && mReference.g_recogResult.recType == RecogEngine.RecType.FACE && mReference.bRet > -1) {
                                    mReference.bRet++;
                                    if (mReference.bRet == 3) {
                                        mReference.bRet = -1;
                                        mReference.onProcessUpdate(RecogEngine.SCAN_TITLE_MRZ_BACK, null, true);
                                    }
                                }
//                                mReference.onUpdateProcess(RecogEngine.ACCURA_ERROR_CODE_BLUR_DOCUMENT);
                                mReference.refreshPreview();
                            }
                        } else {
                            AccuraLog.loge(TAG, "Retrieve Frame data");
                            mReference.refreshPreview();
                        }

                        mReference._mutex.unlock();

                    } else {
                        if (mReference.fCount % 4 == 0) {
                            mReference.onUpdateProcess(RecogEngine.ACCURA_ERROR_CODE_MOTION);
                        }
                        mReference.fCount++;
                        mReference.refreshPreview();
                    }
                } else {
                    AccuraLog.loge(TAG, "ReleaseR");
                }
            } catch (Exception e) {
                e.printStackTrace();
                AccuraLog.loge(TAG, "Thread - " + Log.getStackTraceString(e));
            } finally {
                processing.set(false);
            }

            processing.set(false);
        }
    }

    //    private Thread recogThread = null;
    private Thread nativeThread = null;

    public OcrCameraPreview(Activity context) {
        this.mActivity = context;
        this.detection = new RgbMotionDetection();
    }

    /**
     * set data for scan specific card of the country
     *
     * @param countryId
     * @param cardId
     * @return
     */
    OcrCameraPreview setData(int countryId, int cardId) {
        this.countryId = countryId;
        this.cardId = cardId;
        return this;
    }

    /**
     * add camera on this view
     *
     * @param view add camera preview to this view
     * @return
     */
    OcrCameraPreview setLayout(ViewGroup view) {
        this.cameraContainer = view;
        return this;
    }

    /**
     * Set Camera type to scan Mrz or Ocr+Mrz
     *
     * @param recogType
     * @return
     */
    OcrCameraPreview setType(RecogType recogType) {
        this.recogType = recogType;
        return this;
    }


    public void setMrzDocumentType(MRZDocumentType mrzDocumentType) {
        this.mrzDocumentType = mrzDocumentType;
    }

//    /**
//     * set false to disable sound after scanned success
//     * else true to enable sound
//     *
//     * @param isPlayMedia is default true
//     * @return
//     */
//    OcrCameraPreview isSetMediaPlayer(boolean isPlayMedia) {
//        this.isSetPlayer = isPlayMedia;
//        return this;
//    }
//
//    /**
//     * set your custom play sound
//     *
//     * @param mediaPlayer
//     * @return
//     */
//    OcrCameraPreview setCustomMPlayer(MediaPlayer mediaPlayer) {
//        this.mediaPlayer = mediaPlayer;
//        return this;
//    }

    OcrCameraPreview setHeight(int titleBarHeight) {
        this.titleBarHeight = titleBarHeight;
        return this;
    }

    OcrCameraPreview setFacing(int facing){
        this.facing = facing;
        return this;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void setInitialized(boolean initialized) {
        isInitialized = initialized;
    }

    /**
     * call this method to initialized camera and ocr
     */
    void start() {
        AccuraLog.loge(TAG, "Initialize");
        if (this.recogType == null) {
            throw new NullPointerException("Must have to set recogType");
        }
        if (this.cameraContainer == null) {
            throw new NullPointerException("Must have to setView");
        }
        if (recogEngine == null) {
            recogEngine = new RecogEngine();
        }
        isValidate = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Util.isPermissionsGranted(mActivity)) {
            throw new RuntimeException(mActivity.getPackageName() + " must have to granted Camera permission to access your hardware camera");
        } else {
            mCameraOpenThread.start();

//            // Make sure camera device is opened.
//            try {
//                mCameraOpenThread.join();
//                mCameraOpenThread = null;
//                if (mOpenCameraFail) {
//                    Util.showErrorAndFinish(mActivity, R.string.cannot_connect_camera);
//                    return;
//                } else if (mCameraDisabled) {
//                    Util.showErrorAndFinish(mActivity, R.string.camera_disabled);
//                    return;
//                }
//            } catch (InterruptedException ex) {
//                // ignore
//            }

        }
        String[] defaultFocusModes = {"continuous-video", "auto", "continuous-picture"};
        mFocusManager = new FocusManager(defaultFocusModes);
        mCameraId = CameraHolder.instance().getCameraId(facing);
        dm = mActivity.getResources().getDisplayMetrics();

        // Use FrameLayout to zoom camera according to device screen
        // Fit document with ratio in our frame
        cameraSourcePreview = new CameraSourcePreview(this, mActivity);
        this.cameraContainer.addView(cameraSourcePreview);
//        this.cameraContainer.addView(progressBar, lp);

        doWork();
    }

    /**
     * call this method from
     *
     * @see com.accurascan.ocr.mrz.interfaces.OcrCallback#onUpdateLayout(int, int)
     * to start your camera preview and ocr
     */
    void startOcr() {
        AccuraLog.loge(TAG, "Start Scan");
        if (!isValidate) return;

        mCameraPreviewThread = new Thread(new Runnable() {
            public void run() {
                initializeCapabilities();
                startPreview();
            }
        });
        mCameraPreviewThread.start();
        // Make sure preview is started.
        try {
            mCameraPreviewThread.join();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        mCameraPreviewThread = null;
        isPreviewStarted = true;
        g_recogResult = new RecogResult();
        g_recogResult.recType = RecogEngine.RecType.INIT;
        g_recogResult.bRecDone = false;

        recogEngine.setCallBack(this, recogType);
        if (recogType == RecogType.MRZ) {
            onProcessUpdate(RecogEngine.SCAN_TITLE_MRZ_FRONT, null, false);
        }
//        progressBar.setVisibility(View.GONE);
//        if (progressBar != null && progressBar.isShowing()) {
//            progressBar.dismiss();
//        }
    }

    private void doWork() {
        nativeThread = new NativeThread(this);
        nativeThread.start();
////        progressBar = new ProgressDialog(mActivity);
////        progressBar.setProgressStyle(ProgressDialog.STYLE_SPINNER);
////        progressBar.setMessage("Please wait...");
////        progressBar.show();
//
//        new Thread() {
//            public void run() {
////        Runnable runnable = new Runnable() {
////            public void run() {
//                Util.logd(TAG, "Worker started");
//                if (recogType == RecogType.OCR) {
//                    try {
//                        if (i1 == null) {
//                            Log.e(TAG, "run: initOcr");
//                            i1 = recogEngine.initOcr(OcrCameraPreview.this, mActivity, OcrCameraPreview.this.conutryId, OcrCameraPreview.this.cardId);
//                        }
//                        if (i1 != null && i1.getInitData() != null) {
//                            rectH = i1.getInitData().getCameraHeight();
//                            rectW = i1.getInitData().getCameraWidth();
//                            ocrData.setCardname(i1.getInitData().getCardName());
//                            isbothavailable = i1.getInitData().getIsbothavailable();
//                            if (isbothavailable) {
//                                onProcessUpdate(String.format("Scan %s of %s", i1.getInitData().getCardside(), ocrData.getCardname()), null, false);
//                            } else {
//                                onProcessUpdate(String.format("Scan %s", ocrData.getCardname()), null, false);
//                            }
//                            handler.sendEmptyMessage(1);
//                        } else {
//                            onError(i1.getResponseMessage());
//                            handler.sendEmptyMessage(0);
//                        }
//                    } catch (Exception e) {
//                        Log.e("threadmessage", e.getMessage());
//                    }
//                } else {
//
//                    rectW = dm.widthPixels - 20;
//                    rectH = (dm.heightPixels - titleBarHeight) / 3;
//                    onProcessUpdate(mActivity.getResources().getString(R.string.scan_front), null, false);
//                    handler.sendEmptyMessage(1);
//                }
////            }
////        };
////        new Handler().postDelayed(runnable, 100);
//            }
//        }.start();
    }

    private void init() {
        //initialize the result value
        g_recogResult = new RecogResult();
        g_recogResult.recType = RecogEngine.RecType.INIT;
        g_recogResult.bRecDone = false;

//        // initialize media to play sound after scanned.
//        if (this.isSetPlayer) {
//            if (this.mediaPlayer == null) {
//                this.mediaPlayer = MediaPlayer.create(mActivity, R.raw.beep);
//            }
//            this.audioManager = (AudioManager) mActivity.getSystemService(Context.AUDIO_SERVICE);
//        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Util.isPermissionsGranted(mActivity)) {
            throw new RuntimeException(mActivity.getPackageName() + " must have to granted Camera permission to access your hardware camera");
        } else {
//            mCameraOpenThread.start();

            // Make sure camera device is opened.
            try {
                mCameraOpenThread.join();
                mCameraOpenThread = null;
                if (mOpenCameraFail) {
                    Util.showErrorAndFinish(mActivity, R.string.cannot_connect_camera);
                    return;
                } else if (mCameraDisabled) {
                    Util.showErrorAndFinish(mActivity, R.string.camera_disabled);
                    return;
                }
            } catch (InterruptedException ex) {
                // ignore
            }

        }
        AccuraLog.loge(TAG, "Init Success");
        onUpdateLayout(rectW, rectH);

    }

    /**
     * to handle camera on window focus update
     *
     * @param hasFocus
     */
    void onWindowFocusChanged(boolean hasFocus) {
        Util.logd(TAG, "onWindowFocusChanged.hasFocus=" + hasFocus
                + ".mOnResumePending=" + mOnResumePending);
        if (hasFocus && mOnResumePending) {
            doOnResume();
            mOnResumePending = false;
        }
    }

    /**
     * call on activity resume to restart preview
     */
    void onResume() {
        Util.logd(TAG, "onResume. hasWindowFocus()=" + mActivity.hasWindowFocus());
        if (mCameraDevice == null) {
            Util.logd(TAG, "onResume. mOnResumePending=true");
//            mOnResumePending = true;
            if (mActivity.hasWindowFocus()) {
                doOnResume();
            } else
                mOnResumePending = true;
        } else {
            Util.logd(TAG, "onResume. mOnResumePending=false");
            doOnResume();

            mOnResumePending = false;
        }
    }

    private void doOnResume() {
        if (mOpenCameraFail || mCameraDisabled)
            return;

        // if (mRecogService != null && mRecogService.isProcessing())
        // showProgress(null);
        mPausing = false;

        // Start the preview if it is not started.
        if (mCameraState == PREVIEW_STOPPED) {
            try {
                mCameraId = CameraHolder.instance().getCameraId(facing);
                mCameraDevice = Util.openCamera(mActivity, mCameraId);
                initializeCapabilities();
                startPreview();
                cameraSourcePreview.requestLayout();
            } catch (Exception e) {
                Util.showErrorAndFinish(mActivity, R.string.cannot_connect_camera);
                return;
            }
        }

        if (cameraSourcePreview.getHolder() != null) {
            // If first time initialization is not finished, put it in the
            // message queue.
            if (!mFirstTimeInitialized) {
                mHandler.sendEmptyMessage(FIRST_TIME_INIT);
            }
        }

        keepScreenOnAwhile();
    }

    /**
     * call on activity pause to stop preview
     */
    void onPause() {

        mOnResumePending = false;
        mPausing = true;
        if (nativeThread != null) {
            nativeThread.interrupt();
            nativeThread = null;
        }
//        recogThread.interrupt();
//        recogThread = null;

        stopPreview();

        // Close the camera now because other activities may need to use it.
        closeCamera();
        resetScreenOn();

        // Remove the messages in the event queue.
        mHandler.removeMessages(FIRST_TIME_INIT);
        mHandler.removeMessages(TRIGER_RESTART_RECOG);
    }

    // Snapshots can only be taken after this is called. It should be called
    // once only. We could have done these things in onCreate() but we want to
    // make preview screen appear as soon as possible.
    private void initializeFirstTime() {
        if (mFirstTimeInitialized)
            return;

//		mOrientationListener = new MyOrientationEventListener(this);
//		mOrientationListener.enable();

        mCameraId = CameraHolder.instance().getCameraId(facing);

        Util.initializeScreenBrightness(mActivity.getWindow(), mActivity.getContentResolver());
        mFirstTimeInitialized = true;
    }

    /**
     * call destroy method to stop camera preview
     */
    void onDestroy() {
//        if (mediaPlayer != null)
//            mediaPlayer.release();
        stopPreview();
        recogEngine.closeEngine(1);
    }

    public void closeEngine(boolean b) {
        recogEngine.closeEngine(b ? 0 : 1);
    }

    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CLEAR_SCREEN_DELAY: {
                    mActivity.getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }
                case FIRST_TIME_INIT: {
                    initializeFirstTime();
                    break;
                }

//                case SET_CAMERA_PARAMETERS_WHEN_IDLE: {
//                    setCameraParametersWhenIdle(0);
//                    break;
//                }

                case TRIGER_RESTART_RECOG:
                    if (!mPausing && mCameraDevice != null)
                        mCameraDevice.setOneShotPreviewCallback(OcrCameraPreview.this);
                    // clearNumberAreaAndResult();
                    break;
                case STOP_RECOG:
                    if (!mPausing)
                        mCameraDevice.setOneShotPreviewCallback(null);
                    // clearNumberAreaAndResult();
                    break;
            }
        }
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (mPausing) {
            return;
        }

        if (mCameraState != IDLE) {
            mCameraDevice.setOneShotPreviewCallback(OcrCameraPreview.this);
            return;
        }

        if (!isPreviewStarted) {
//            refreshPreview();
            return;
        }

        if (!GlobalData.isPhoneInMotion()) {
            AccuraLog.loge(TAG, "Pro. Frame");
            RecogThread recogThread = new RecogThread(this, data, camera);

            recogThread.start();
//            fCount++;
        } else {
            onUpdateProcess(RecogEngine.ACCURA_ERROR_CODE_MOTION);
            refreshPreview();
//            fCount++;
        }
    }

    private void refreshPreview() {
        mHandler.sendMessageDelayed(
                mHandler.obtainMessage(TRIGER_RESTART_RECOG),
                TRIGER_RESTART_RECOG_DELAY);
//        if (!mPausing && mCameraDevice != null) {
//            mCameraDevice.setOneShotPreviewCallback(OcrCameraPreview.this);
//        }
//        if (recogThread != null) {
//            if (recogThread instanceof RecogThread) ((RecogThread) recogThread).clearData();
//            recogThread.interrupt();
//        }
    }

    private void stopPreviewCallBack(){
        mHandler.sendMessageDelayed(
                mHandler.obtainMessage(STOP_RECOG),
                TRIGER_RESTART_RECOG_DELAY);
    }

    public void autoFocus() {
        if (mCameraDevice != null) {
            Camera.Parameters p = mCameraDevice.getParameters();
            List<String> focusModes = p.getSupportedFocusModes();

            if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                if (FocusManager.isSupported(Camera.Parameters.FOCUS_MODE_AUTO, mParameters.getSupportedFocusModes())) {
                    mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    if (mCameraDevice != null) {
                        mCameraDevice.setParameters(mParameters);
                        mCameraDevice.autoFocus(mAutoFocusCallback);
                        setCameraState(FOCUSING);
                        isTouchCalled = false;
                    }
                }
            } else {
                isTouchCalled = false;
            }
        }
    }

    @Override
    public void cancelAutoFocus() {
        mCameraDevice.cancelAutoFocus();
        if (mCameraState != SELFTIMER_COUNTING
                && mCameraState != SNAPSHOT_IN_PROGRESS) {
            setCameraState(IDLE);
        }
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
        isTouchCalled = false;
    }

    @Override
    public boolean capture() {
        // If we are already in the middle of taking a snapshot then ignore.
        if (mCameraState == SNAPSHOT_IN_PROGRESS || mCameraDevice == null) {
            return false;
        }
        setCameraState(SNAPSHOT_IN_PROGRESS);

        return true;
    }

    @Override
    public void setFocusParameters() {
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    @Override
    public void playSound(int soundId) {

    }

    private final class AutoFocusCallback implements
            Camera.AutoFocusCallback {
        public void onAutoFocus(boolean focused, Camera camera) {
            if (mPausing)
                return;

            if (mCameraState == FOCUSING) {
                setCameraState(IDLE);
            }
            mFocusManager.onAutoFocus(focused);

            String focusMode = mFocusManager.getFocusMode();
            mParameters.setFocusMode(focusMode);
            mCameraDevice.setParameters(mParameters);
            isTouchCalled = false;
//            autoFocus();
        }
    }

    private class CameraErrorCallback implements Camera.ErrorCallback {
        private static final String TAG = "CameraErrorCallback";

        public void onError(int error, Camera camera) {
            Log.e(TAG, "Got camera error callback. error=" + error);
            if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                // We are not sure about the current state of the app (in preview or
                // snapshot or recording). Closing the app is better than creating a
                // new Camera object.
//                throw new RuntimeException("Media server died.");
            }
        }
    }

    class Preview extends SurfaceView implements SurfaceHolder.Callback, View.OnTouchListener {
        private final SurfaceHolder mHolder;

        public Preview(Context context) {
            super(context);
            mHolder = getHolder();
            mHolder.addCallback(this);
            setOnTouchListener(this);
        }

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
            Util.logd(TAG, "(w,h)" + width + "," + height);
            // We need to save the holder for later use, even when the mCameraDevice
            // is null. This could happen if onResume() is invoked after this
            // function.
            mSurfaceHolder = holder;

            // The mCameraDevice will be null if it fails to connect to the camera
            // hardware. In this case we will show a dialog and then finish the
            // activity, so it's OK to ignore it.
            if (mCameraDevice == null)
                return;

            // Sometimes surfaceChanged is called after onPause or before onResume.
            // Ignore it.
            if (mPausing/* || cameraActivity.isFinishing()*/)
                return;

            // Set preview display if the surface is being created. Preview was
            // already started. Also restart the preview if display rotation has
            // changed. Sometimes this happens when the device is held in portrait
            // and camera app is opened. Rotation animation takes some time and
            // display rotation in onCreate may not be what we want.
            if (mCameraState == PREVIEW_STOPPED) {
                if (mFirstInitialized) {
//                    initializeCapabilities();
                    startPreview();
                }
            } else {
                if (Util.getDisplayRotation(mActivity) != mDisplayRotation) {
                    setDisplayOrientation();
                }
                if (holder.isCreating()) {
                    // Set preview display if the surface is being created and
                    // preview
                    // was already started. That means preview display was set to
                    // null
                    // and we need to set it now.
                    setPreviewDisplay(holder);
                }
            }

            // If first time initialization is not finished, send a message to do
            // it later. We want to finish surfaceChanged as soon as possible to let
            // user see preview first.
            if (!mFirstTimeInitialized) {
                mHandler.sendEmptyMessage(FIRST_TIME_INIT);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Util.logd(TAG, "surfaceDestroyed: ");
            stopPreview();
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (!isTouchCalled && mParameters != null) {
                String focusMode = mParameters.getFocusMode();
                if (focusMode == null || Camera.Parameters.FOCUS_MODE_INFINITY.equals(focusMode)) {
                    return false;
                }

                if (event.getAction() == MotionEvent.ACTION_UP) {
                    isTouchCalled = true;
                    autoFocus();
                }

            }

            return true;
        }
    }

    public void startIfReady(SurfaceHolder holder){
        // We need to save the holder for later use, even when the mCameraDevice
        // is null. This could happen if onResume() is invoked after this
        // function.
        mSurfaceHolder = holder;

        // The mCameraDevice will be null if it fails to connect to the camera
        // hardware. In this case we will show a dialog and then finish the
        // activity, so it's OK to ignore it.
        if (mCameraDevice == null)
            return;

        // Sometimes surfaceChanged is called after onPause or before onResume.
        // Ignore it.
        if (mPausing/* || cameraActivity.isFinishing()*/)
            return;

        // Set preview display if the surface is being created. Preview was
        // already started. Also restart the preview if display rotation has
        // changed. Sometimes this happens when the device is held in portrait
        // and camera app is opened. Rotation animation takes some time and
        // display rotation in onCreate may not be what we want.
        if (mCameraState == PREVIEW_STOPPED) {
            if (mFirstInitialized) {
//                    initializeCapabilities();
                startPreview();
            }
        } else {
            if (Util.getDisplayRotation(mActivity) != mDisplayRotation) {
                setDisplayOrientation();
            }
            if (holder.isCreating()) {
                // Set preview display if the surface is being created and
                // preview
                // was already started. That means preview display was set to
                // null
                // and we need to set it now.
                setPreviewDisplay(holder);
            }
        }

        // If first time initialization is not finished, send a message to do
        // it later. We want to finish surfaceChanged as soon as possible to let
        // user see preview first.
        if (!mFirstTimeInitialized) {
            mHandler.sendEmptyMessage(FIRST_TIME_INIT);
        }
    }

    public boolean onTouchView(View v, MotionEvent event) {
        if (!isTouchCalled && mParameters != null) {
            String focusMode = mParameters.getFocusMode();
            if (focusMode == null || Camera.Parameters.FOCUS_MODE_INFINITY.equals(focusMode)) {
                return false;
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                isTouchCalled = true;
                autoFocus();
            }

        }

        return true;
    }

    private void startPreview() {

        if (mCameraDevice != null) {
            if (mPausing || mActivity.isFinishing())
                return;

            mCameraDevice.setErrorCallback(mErrorCallback);

            // If we're previewing already, stop the preview first (this will blank
            // the screen).
            if (mCameraState != PREVIEW_STOPPED)
                stopPreview();

            setPreviewDisplay(cameraSourcePreview.getHolder());
            setDisplayOrientation();

            mCameraDevice.setOneShotPreviewCallback(OcrCameraPreview.this);
            setCameraParameters(UPDATE_PARAM_ALL);

            // Inform the mainthread to go on the UI initialization.
            if (mCameraPreviewThread != null) {
                synchronized (mCameraPreviewThread) {
                    mCameraPreviewThread.notify();
                }
            }

            try {
                Util.logd(TAG, "startPreview");
                mCameraDevice.startPreview();
//                autoFocus();
            } catch (Throwable ex) {
                closeCamera();
//                throw new RuntimeException("startPreview failed", ex);
            }

            setCameraState(IDLE);

            // notify again to make sure main thread is wake-up.
            if (mCameraPreviewThread != null) {
                synchronized (mCameraPreviewThread) {
                    mCameraPreviewThread.notify();
                }
            }
        }
    }

    public void stopPreview() {
        if (mCameraDevice == null)
            return;
        mCameraDevice.stopPreview();
//        mCameraDevice.setPreviewCallback(null);
        setCameraState(PREVIEW_STOPPED);
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            CameraHolder.instance().release();
            mCameraDevice.setErrorCallback(null);
            mCameraDevice = null;
            setCameraState(PREVIEW_STOPPED);
            mFocusManager.onCameraReleased();
        }
    }

    public void restartPreview(){
//        stopPreview();
//        closeCamera();
//        startPreview();
        onPause();
        onResume();
    }

    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            if (mCameraDevice != null) {
                mCameraDevice.setPreviewDisplay(holder);
                AccuraLog.loge(TAG, "Started");
            }
        } catch (Throwable ex) {
            closeCamera();
//            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    private void setDisplayOrientation() {
        mDisplayRotation = Util.getDisplayRotation(mActivity);
        int[] rotationArray = Util.getDisplayOrientation(mDisplayRotation,
                mCameraId);
        this.rotation = rotationArray[0];
        mDisplayOrientation = rotationArray[1];
        mCameraDevice.setDisplayOrientation(mDisplayOrientation);
    }

    private void setCameraState(int state) {
        mCameraState = state;
    }

    private void initializeCapabilities() {

        mFirstInitialized = true;

        if (mCameraDevice != null)
            mParameters = mCameraDevice.getParameters();
//        mCameraDevice.autoFocus(new AutoFocusCallback());
        if (mParameters != null) {
            mParameters.getFocusMode();
            mFocusManager.initializeParameters(mParameters);
        }

        if (mCameraDevice != null) {
            mParameters = mCameraDevice.getParameters();
            GetCameraResolution();
        }
    }

    // We separate the parameters into several subsets, so we can update only
    // the subsets actually need updating. The PREFERENCE set needs extra
    // locking because the preference can be changed from GLThread as well.
    private void setCameraParameters(int updateSet) {
        if (mCameraDevice != null) {
            mParameters = mCameraDevice.getParameters();

            if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
                updateCameraParametersInitialize();
            }


            if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
                updateCameraParametersPreference();
            }

            if (mParameters != null) {
                mCameraDevice.setParameters(mParameters);
                GetCameraResolution();
            }
        }
    }

    private void updateCameraParametersInitialize() {
        // Reset preview frame rate to the maximum because it may be lowered by
        // video camera Application.
        List<Integer> frameRates = mParameters.getSupportedPreviewFrameRates();
        if (frameRates != null) {
            Integer max = Collections.max(frameRates);
            mParameters.setPreviewFrameRate(max);
        }

        //mParameters.setRecordingHint(false);

        // Disable video stabilization. Convenience methods not available in API
        // level <= 14
        String vstabSupported = mParameters
                .get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            mParameters.set("video-stabilization", "false");
        }
    }

    private void updateCameraParametersPreference() {

        // Since change scene mode may change supported values,

        //mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
//        mModeView.setText(R.string.preview_mode);

        int camOri = CameraHolder.instance().getCameraInfo()[mCameraId].orientation;
        // Set a preview size that is closest to the viewfinder height and has the right aspect ratio.
        List<Camera.Size> sizes = mParameters.getSupportedPreviewSizes();
        Camera.Size optimalSize;
        //if (mode == SettingsActivity.CAPTURE_MODE)
        //	optimalSize = Util.getOptimalPreviewSize(this, sizes, aspectWtoH);
        //else
        {
            int requiredArea = mPreviewWidth * mPreviewHeight;
//            optimalSize = Util.getOptimalPreviewSizeByArea(mActivity, sizes, requiredArea);
            optimalSize = Util.getPreviewSize(mPreviewWidth, mPreviewHeight, mParameters);

        }

        Camera.Size original = mParameters.getPreviewSize();

        if (!original.equals(optimalSize)) {
            if (camOri == 0 || camOri == 180) {
                mParameters.setPreviewSize(optimalSize.height, optimalSize.width);
            } else {
                mParameters.setPreviewSize(optimalSize.width, optimalSize.height);
            }

            // Zoom related settings will be changed for different preview
            // sizes, so set and read the parameters to get lastest values

            if (mCameraDevice != null) {
                mCameraDevice.setParameters(mParameters);
                mParameters = mCameraDevice.getParameters();
            }
        }

        previewSize = mParameters.getPreviewSize();// new Size(mParameters.getPreviewSize().width, mParameters.getPreviewSize().height);
//        mParameters.setPreviewFormat(ImageFormat.NV21);
        // Set JPEG quality.
        int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(
                mCameraId, CameraProfile.QUALITY_HIGH);
        mParameters.setJpegQuality(jpegQuality);

        // For the following settings, we need to check if the settings are
        // still supported by latest driver, if not, ignore the settings.

        //if (Parameters.SCENE_MODE_AUTO.equals(mSceneMode))
        {
            if (mParameters != null) {
                // Set white balance parameter.
                String whiteBalance = "auto";
                if (isSupported(whiteBalance,
                        mParameters.getSupportedWhiteBalance())) {
                    mParameters.setWhiteBalance(whiteBalance);
                }

                String focusMode = mFocusManager.getFocusMode();
                mParameters.setFocusMode(focusMode);

                // Set exposure compensation
                int value = 0;
                int max = mParameters.getMaxExposureCompensation();
                int min = mParameters.getMinExposureCompensation();
                if (value >= min && value <= max) {
                    mParameters.setExposureCompensation(value);
                } else {
                    Util.logw(TAG, "invalid exposure range: " + value);
                }
            }
        }

        if (mParameters != null) {
            // Set flash mode.
            String flashMode = "off";
            List<String> supportedFlash = mParameters.getSupportedFlashModes();
            if (isSupported(flashMode, supportedFlash)) {
                mParameters.setFlashMode(flashMode);
            }

            Util.logd(TAG, "focusMode=" + mParameters.getFocusMode());
        }

    }

    /**
     * Returns the preview size that is currently in use by the underlying camera.
     */
    public Camera.Size getPreviewSize() {
        return previewSize;
    }

    public int getPreviewWidth() {
        return mPreviewWidth;
    }

    public int getPreviewHeight() {
        return mPreviewHeight;
    }

    public void GetCameraResolution() {
        if (mParameters != null && !isBlurSet) {
            isBlurSet = true;
            List sizes = mParameters.getSupportedPictureSizes();
            Camera.Size result = null;

            ArrayList<Integer> arrayListForWidth = new ArrayList<>();
            ArrayList<Integer> arrayListForHeight = new ArrayList<>();

            for (int i = 0; i < sizes.size(); i++) {
                result = (Camera.Size) sizes.get(i);
                arrayListForWidth.add(result.width);
                arrayListForHeight.add(result.height);
            }
            if (arrayListForWidth.size() != 0 && arrayListForHeight.size() != 0) {
                recogEngine.v = ((Collections.max(arrayListForWidth)) * (Collections.max(arrayListForHeight))) / 1024000f;
            }

            arrayListForWidth.clear();
            arrayListForHeight.clear();
        }
    }

    private String newMessage = "";
    private int isContinue = 0;

    @Override
    public void onUpdateProcess(String s) {
        if (!s.isEmpty()) {
            AccuraLog.loge(TAG, "onProcessUpdate " + s);
            if (newMessage.equals(s))
                return;
            newMessage = s;
            // if s is equal to RecogEngine.ACCURA_ERROR_CODE_PROCESSING.concat("_clear") then remove "_clear" message on displaying
            onProcessUpdate(-1, s.equals(RecogEngine.ACCURA_ERROR_CODE_PROCESSING.concat("_clear")) ? RecogEngine.ACCURA_ERROR_CODE_PROCESSING : s, false);
            if (!s.equals(RecogEngine.ACCURA_ERROR_CODE_PROCESSING) && isContinue == 0) {
                isContinue = 1;
                final Runnable runnable = () -> {
                    try {
                        if (!s.equals(RecogEngine.ACCURA_ERROR_CODE_DARK_DOCUMENT) && !newMessage.equals(RecogEngine.ACCURA_ERROR_CODE_PROCESSING)) {
                            newMessage = "";
                            onProcessUpdate(-1, "", false);
                        }
                        isContinue = 0;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                };
                Runnable runnable1 = () -> new Handler().postDelayed(runnable, 1500);
                if (mActivity != null) {
                    mActivity.runOnUiThread(runnable1);
                }
            }
        }
    }

    @Override
    public void onScannedSuccess(boolean b, boolean isMRZRequired) {
        if (b) {
            if (recogType == RecogType.MRZ) {
                if (g_recogResult.recType == RecogEngine.RecType.MRZ && !g_recogResult.lines.equalsIgnoreCase("")) {
                    AccuraLog.loge(TAG, "MRZ Done");
                    sendInformation();
                }
                else {
                    g_recogResult.recType = RecogEngine.RecType.FACE;
                    refreshPreview();
                }
            }
        } else {
            refreshPreview();
        }
    }

    @Override
    public void onScannedFailed(String s) {
        Log.e("ocr_log", s);
    }

    private void sendInformation() {
        checkmrz = 0;
//        try {
//            playEffect();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        recogEngine.removeCallBack(this);
//        recogEngine.closeEngine(0);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mRecCnt = 0;
        bRet = 0;
        fCount = 0;
        if (recogType == RecogType.MRZ) {
//            g_recogResult.faceDoc = bitmapToBytes(g_recogResult.faceBitmap);
//            g_recogResult.frontDoc = bitmapToBytes(g_recogResult.docFrontBitmap);
//            g_recogResult.backDoc = bitmapToBytes(g_recogResult.docBackBitmap);
//            g_recogResult.faceBitmap = null;
//            g_recogResult.docFrontBitmap = null;
//            g_recogResult.docBackBitmap = null;
            RecogResult recogResult = g_recogResult;

            g_recogResult = new RecogResult();
            g_recogResult.recType = RecogEngine.RecType.INIT;
            g_recogResult.bRecDone = false;
            onScannedComplete(recogResult);
        }

    }

    private String bitmapToBytes(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            bitmap.recycle();
            return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);
        }
        return null;
    }

//    ObjectAnimator anim = null;
//
//    void playEffect() {
//        if (isSetPlayer && mediaPlayer != null) {
//            if (audioManager != null)
//                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
//            mediaPlayer.start();
//        }
//    }

}

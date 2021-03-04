package com.docrecog.scan;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.accurascan.ocr.mrz.model.InitModel;
import com.accurascan.ocr.mrz.model.RecogResult;
import com.accurascan.ocr.mrz.util.AccuraLog;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import java.io.IOException;
import java.io.InputStream;

public class RecogEngine {

    static {
        try { // for Ocr
            System.loadLibrary("accurasdk");
            Log.e(RecogEngine.class.getSimpleName(), "Load success");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    abstract static class ScanListener {
        /**
         * This is called to get scanned processed message.
         */
        void onUpdateProcess(String s) {
        }


        /**
         * This is called after scanned success.
         *
         * @param isDone
         * @param isMRZRequired
         */
        abstract void onScannedSuccess(boolean isDone, boolean isMRZRequired);

        void onFaceScanned(Bitmap bitmap){

        }

        /**
         * This is called on scanned failed.
         */
        void onScannedFailed(String s) {
        }

    }

    public class SDKModel {
        public int i;
        public boolean isMRZEnable = false;
        public String message = "Success";
    }


    public static final int SCAN_TITLE_MRZ_FRONT = 1;
    public static final int SCAN_TITLE_MRZ_BACK = 2;

    public static final String ACCURA_ERROR_CODE_MOTION = "0";
    public static final String ACCURA_ERROR_CODE_DOCUMENT_IN_FRAME = "1";
    public static final String ACCURA_ERROR_CODE_BRING_DOCUMENT_IN_FRAME = "2";
    public static final String ACCURA_ERROR_CODE_PROCESSING = "3";
    public static final String ACCURA_ERROR_CODE_BLUR_DOCUMENT = "4";
    public static final String ACCURA_ERROR_CODE_FACE_BLUR = "5";
    public static final String ACCURA_ERROR_CODE_GLARE_DOCUMENT = "6";
    public static final String ACCURA_ERROR_CODE_HOLOGRAM = "7";
    public static final String ACCURA_ERROR_CODE_DARK_DOCUMENT = "8";
    public static final String ACCURA_ERROR_CODE_PHOTO_COPY_DOCUMENT = "9";
    public static final String ACCURA_ERROR_CODE_FACE = "10";
    public static final String ACCURA_ERROR_CODE_MRZ = "11";
    public static final String ACCURA_ERROR_CODE_PASSPORT_MRZ = "12";
    public static final String ACCURA_ERROR_CODE_ID_MRZ = "13";
    public static final String ACCURA_ERROR_CODE_VISA_MRZ = "14";

    private static final String TAG = "PassportRecog";
    private byte[] pDic = null;
    private int pDicLen = 0;
    private byte[] pDic1 = null;
    private int pDicLen1 = 0;
    private static String[] assetNames = {"mMQDF_f_Passport_bottom_Gray.dic", "mMQDF_f_Passport_bottom.dic"};
//    private static FirebaseVisionFaceDetector faceDetector;
    private ScanListener callBack;
    static float mT = 15;
    static float v = 5f;

    private static int[] faced = new int[3]; //value for detected face or not
    private static float[] fConf = new float[3]; //face detection confidence

    private static int[] intData = new int[3000];
    private static int NOR_W = 400;//1200;//1006;
    private static int NOR_H = 400;//750;//1451;

    private Context con;
    private boolean displayDialog = true;

    public RecogEngine() {

    }

    void setCallBack(ScanListener scanListener, RecogType recogType) {
        this.callBack = scanListener;
    }

    void removeCallBack(ScanListener scanListener) {
        this.callBack = scanListener;
    }

    //This is SDK app calling JNI method
    private native int loadDictionary(Context activity, String s, byte[] img_Dic, int len_Dic, byte[] img_Dic1, int len_Dic1,/*, byte[] licenseKey*/AssetManager assets, int[] intData);

    public native String doCheckData(byte[] yuvdata, int width, int height);

    private native int doRecogBitmap(Bitmap bitmap, int facepick, int[] intData, Bitmap faceBitmap, int[] faced, boolean unknownVal, int documentType);

    private native int doFaceDetect(Bitmap bitmap, Bitmap faceBitmap, float[] fConf);

    private native String doFaceCheck(long l, float v);

    private native String doCheckDocument(long l, float v);

    private native String loadCard(Context context, int type);

    /**
     * Set Blur Percentage to allow blur on document
     *
     * @param context        Activity context
     * @param blurPercentage is 0 to 100, 0 - clean document and 100 - Blurry document
     * @return 1 if success else 0
     */
    private native int setBlurPercentage(Context context, int blurPercentage, String errorMessage);

    /**
     * Set Blur Percentage to allow blur on detected Face
     *
     * @param context            Activity context
     * @param faceBlurPercentage is 0 to 100, 0 - clean face and 100 - Blurry face
     * @return 1 if success else 0
     */
    private native int setFaceBlurPercentage(Context context, int faceBlurPercentage, String errorMessage);

    /**
     * @param context
     * @param minPercentage
     * @param maxPercentage
     * @return 1 if success else 0
     */
    private native int setGlarePercentage(Context context, int minPercentage, int maxPercentage, String errorMessage);

    /**
     * Set CheckPhotoCopy to allow photocopy document or not
     *
     * @param context
     * @param isCheckPhotoCopy if true then reject photo copy document else vice versa
     * @return 1 if success else 0
     */
    private native int isCheckPhotoCopy(Context context, boolean isCheckPhotoCopy, String errorMessage);

    /**
     * set Hologram detection to allow hologram on face or not
     *
     * @param context
     * @param isDetectHologram if true then reject hologram is on face else it is allow .
     * @return 1 if success else 0
     */
    private native int SetHologramDetection(Context context, boolean isDetectHologram, String errorMessage);

    /**
     * set light tolerance to detect light on document if low light
     *
     * @param context
     * @param tolerance is 0 to 100, 0 - allow full dark document and 100 - allow full bright document
     * @return 1 if success else 0
     */
    private native int setLowLightTolerance(Context context, int tolerance, String errorMessage);

    /**
     * set motion threshold to detect motion on camera document
     *
     * @param context
     * @param motionThreshold
     * @return
     */
    private native int setMotionThreshold(Context context, int motionThreshold, @NonNull String message);

    private native String doLightCheck(long srcMat);

    private native int closeOCR(int i);

    public int setBlurPercentage(Context context, int blurPercentage) {
        return setBlurPercentage(context, blurPercentage,"");
    }

    public int setFaceBlurPercentage(Context context, int faceBlurPercentage) {
        return setFaceBlurPercentage(context, faceBlurPercentage,"");
    }

    public int setGlarePercentage(Context context, int minValue, int maxValue) {
        return setGlarePercentage(context, minValue, maxValue,"");
    }

    public int isCheckPhotoCopy(Context context, boolean isCheckPhotoCopy) {
        return isCheckPhotoCopy(context, isCheckPhotoCopy,"");
    }

    public int SetHologramDetection(Context context, boolean isDetectHologram) {
        return SetHologramDetection(context, isDetectHologram,"");
    }

    public int setLowLightTolerance(Context context, int tolerance) {
        return setLowLightTolerance(context, tolerance,"");
    }

    public int setMotionData(Activity activity, int motionThreshold) {
        mT = motionThreshold;
//        nM = message;
        return setMotionThreshold(activity, motionThreshold, "");
    }


    public void setDialog(boolean displayDialog) {
        this.displayDialog = displayDialog;
    }

    /**
     * Must have to call initEngine on app open
     *
     * @param context
     * @return
     */
    public SDKModel initEngine(Context context) {
        /*
           initialized sdk by InitEngine from thread
          The return value by initEngine used the identify
          Return ret < 0 if license not valid
          -1 - No key found
          -2 - Invalid Key
          -3 - Invalid Platform
          -4 - Invalid License

         */
        this.con = context;
        SDKModel sdkModel = new SDKModel();
        getAssetFile(assetNames[0], assetNames[1]);
        int[] ints = new int[5];
//        File file = loadClassifierData(context);
        int ret = loadDictionary(context, /*file != null ? file.getAbsolutePath() : */"", pDic, pDicLen, pDic1, pDicLen1, context.getAssets(),ints);
        Log.i("recogPassport", "loadDictionary: " + ret);
//        nM = "Keep Document Steady";
        if (ret < 0) {
            String message = "";
            if (ret == -1) {
                message = "No Key Found";
            } else if (ret == -2) {
                message = "Invalid Key";
            } else if (ret == -3) {
                message = "Invalid Platform";
            } else if (ret == -4) {
                message = "Invalid License";
            }
            sdkModel.message = message;
            if (displayDialog) {
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> {
                        AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
                        builder1.setMessage(sdkModel.message);
                        builder1.setCancelable(true);
                        builder1.setPositiveButton(
                                "OK",
                                (dialog, id) -> dialog.cancel());
                        AlertDialog alert11 = builder1.create();
                        alert11.show();
                    });
                } else
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        } else {
            sdkModel.isMRZEnable = ints[0] == 1;//isMrzEnable;//ret == 1 || ret == 4 || ret == 6 || ret == 7;
        }
        sdkModel.i = ret;
        return sdkModel;
    }

    /**
     * Initialized MRZ
     *
     * @param context      is activity context
     * @param recogType    0 for MRZ
     * @return {@link InitModel}
     */
    protected InitModel initCard(Context context, int recogType){
        String s = loadCard(context, recogType);
        try {
            if (s != null && !s.equals("")) {
                JSONObject jsonObject = new JSONObject(s);
                InitModel initModel = new Gson().fromJson(jsonObject.toString(), InitModel.class);
                return initModel;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    boolean checkValid(Bitmap bitmap) {
        Mat src = new Mat();
        Utils.bitmapToMat(bitmap, src);
        String s = doCheckDocument(src.getNativeObjAddr(), v);
        if (s != null && !s.equals("")) {
            src.release();
            try {
                JSONObject jsonObject = new JSONObject(s);
                int ic = jsonObject.getInt("responseCode");
                if (ic == 1) {
                    return true;
                } else {
                    String message = jsonObject.getString("responseMessage");
                    if (!message.isEmpty() && this.callBack != null) {
                        this.callBack.onUpdateProcess(message);
                    }
                    return false;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        src.release();
        return false;
    }

    boolean checkLight(Bitmap bitmap) {
        int ret = 0;
        Mat src = new Mat();
        Utils.bitmapToMat(bitmap, src);
        String s = doLightCheck(src.getNativeObjAddr());
        try {
            if (s != null && !TextUtils.isEmpty(s)) {
                JSONObject jsonObject = new JSONObject(s);
                ret = jsonObject.getInt("responseCode");
                if (ret > 0) {
                    callBack.onUpdateProcess(jsonObject.getString("responseMessage"));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        src.release();
        return ret == 1;
    }

    /**
     * To get MRZ data from documnet
     *
     * @param bmCard document bitmap
     * @param result {@link RecogResult} to get data
     * @param documentType
     * @return 0 if failed and >0 if success
     */
    int doRunData(Bitmap bmCard, int facepick, RecogResult result, MRZDocumentType documentType) {
        int ret = 1;
        //If fail, empty string.
        // both => 0
        // only face => 1
        // only mrz => 2
        Bitmap faceBmp = null;
        if (documentType == null) {
           documentType = MRZDocumentType.NONE;
        }
        ret = doRecogBitmap(bmCard, 0, intData, faceBmp, faced, true, documentType.value);
        AccuraLog.loge(TAG, "GetM - " + documentType + "," + ret);
        if (ret > 0) {
            if (result.recType == RecType.INIT) {
                if (faced[0] == 0) {
                    result.faceBitmap = null; //face not detected
                    result.recType = RecType.MRZ;
                } else {
                    if (faceBmp != null) {
                        result.faceBitmap = faceBmp.copy(Config.ARGB_8888, false);
                        if (faced[1] < 400 || faced[2] < 400)
                            result.faceBitmap = Bitmap.createBitmap(result.faceBitmap, 0, 0, faced[1], faced[2]);
                        result.recType = RecType.BOTH;
                        result.bRecDone = true;
                    }
                }
            } else if (result.recType == RecType.FACE) {
                result.bRecDone = true;
            }

            result.ret = ret;
            result.SetResult(intData);
        }
//        }
        return ret;
    }

    /**
     * To detect face from your camera frame
     * @param i
     * @param bitmap         document bitmap
     * @param result         to save face image
     * @param scanListener   call back required to getting success or failed response
     */
    void doFaceDetect(int i, Bitmap bitmap, RecogResult result, ScanListener scanListener) {
        AccuraLog.loge(TAG, "MF Detect");
        Bitmap faceBitmap = Bitmap.createBitmap(NOR_W, NOR_H, Config.ARGB_8888);
        int ret = doFaceDetect(bitmap, faceBitmap, fConf);

        //ret > 0 => detect face ok
        if (ret <= 0) faceBitmap = null;
        else if (fConf[1] < NOR_W || fConf[2] < NOR_H)
            faceBitmap = Bitmap.createBitmap(faceBitmap, 0, 0, (int) fConf[1], (int) fConf[2]);

        if (ret > 0 && result.recType == RecType.MRZ)
            result.bRecDone = true;

        if (ret > 0) {
            Mat clone = new Mat();
            Utils.bitmapToMat(faceBitmap, clone);
            String s = doFaceCheck(clone.getNativeObjAddr(), v);
            clone.release();
            try {
                if (s != null && !s.equals("")) {
                    JSONObject jsonObject = new JSONObject(s);
                    int ic = jsonObject.getInt("responseCode");
                    AccuraLog.loge(TAG, "checkf" + ic);
                    if (ic == 1) {
                        scanListener.onFaceScanned(faceBitmap.copy(Config.ARGB_8888, false));
                    } else if (ic == 10) {
                        AccuraLog.loge(TAG, "failed check: "+ic );
                        String message = jsonObject.getString("responseMessage");
                        if (!message.isEmpty() && this.callBack != null) {
                            this.callBack.onUpdateProcess(message);
                            scanListener.onScannedSuccess(false, false);
                        }
                    }
                } else scanListener.onScannedSuccess(false, false);
                faceBitmap.recycle();
            } catch (JSONException e) {
                faceBitmap.recycle();
                e.printStackTrace();
            }
        } else {
            callBack.onUpdateProcess(ACCURA_ERROR_CODE_FACE);
            scanListener.onScannedSuccess(false, false);
        }
    }

    private Bitmap bitmapFromMat(Mat mat) {
        if (mat != null) {
            Bitmap bmp = Bitmap.createBitmap(mat.width(), mat.height(), Config.ARGB_8888);
            Utils.matToBitmap(mat, bmp);
            return bmp;
        }
        return null;
    }

    private int getAssetFile(String fileName, String fileName1) {

        int size = 0;
        try {
            InputStream is = this.con.getResources().getAssets().open(fileName);
            size = is.available();
            pDic = new byte[size];
            pDicLen = size;
            is.read(pDic);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            InputStream is = this.con.getResources().getAssets().open(fileName1);
            size = is.available();
            pDic1 = new byte[size];
            pDicLen1 = size;
            is.read(pDic1);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return size;
    }

    void closeEngine(int destroy) {
        closeOCR(destroy);
    }

    public enum RecType {
        INIT, BOTH, FACE, MRZ
    }
}

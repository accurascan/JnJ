package com.accurascan.accura.mrz.sdk;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.accurascan.ocr.mrz.CameraView;
import com.accurascan.ocr.mrz.interfaces.OcrCallback;
import com.accurascan.ocr.mrz.model.RecogResult;
import com.accurascan.ocr.mrz.motiondetection.SensorsActivity;
import com.accurascan.ocr.mrz.util.AccuraLog;
import com.docrecog.scan.MRZDocumentType;
import com.docrecog.scan.RecogEngine;

import java.lang.ref.WeakReference;

public class OcrActivity extends SensorsActivity implements OcrCallback {

    private static final String TAG = OcrActivity.class.getSimpleName();
    private CameraView cameraView;
    private View viewLeft, viewRight, borderFrame;
    private TextView tvTitle, tvScanMessage;
    private ImageView imageFlip;
    private MRZDocumentType mrzType;

    private static class MyHandler extends Handler {
        private final WeakReference<OcrActivity> mActivity;

        public MyHandler(OcrActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            OcrActivity activity = mActivity.get();
            if (activity != null) {
                String s = "";
                if (msg.obj instanceof String) s = (String) msg.obj;
                switch (msg.what) {
                    case 0: activity.tvTitle.setText(s);break;
                    case 1: activity.tvScanMessage.setText(s);break;
                    case 2: if (activity.cameraView != null) activity.cameraView.flipImage(activity.imageFlip);
                        break;
                    default: break;
                }
            }
            super.handleMessage(msg);
        }
    }

    private Handler handler = new MyHandler(this);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (getIntent().getIntExtra("app_orientation", 1) != 0) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppThemeNoActionBar);
        requestWindowFeature(Window.FEATURE_NO_TITLE); // Hide the window title.
        setContentView(R.layout.ocr_activity);
        AccuraLog.loge(TAG, "Start Camera Activity");
        init();

        if (getIntent().hasExtra(MRZDocumentType.class.getName())) {
            mrzType = MRZDocumentType.detachFrom(getIntent());
        } else {
            mrzType = MRZDocumentType.NONE;
        }

        initCamera();
    }

    private void initCamera() {
        AccuraLog.loge(TAG, "Initialized camera");
        //<editor-fold desc="To get status bar height">
        Rect rectangle = new Rect();
        Window window = getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
        int statusBarTop = rectangle.top;
        int contentViewTop = window.findViewById(Window.ID_ANDROID_CONTENT).getTop();
        int statusBarHeight = contentViewTop - statusBarTop;
        //</editor-fold>

        RelativeLayout linearLayout = findViewById(R.id.ocr_root); // layout width and height is match_parent

        cameraView = new CameraView(this);
        cameraView.setMRZDocumentType(mrzType)
                .setView(linearLayout) // To add camera view
                .setCameraFacing(0) // To set front or back camera.
                .setOcrCallback(this)  // To get Update and Success Call back
                .setStatusBarHeight(statusBarHeight)  // To remove Height from Camera View if status bar visible
//                optional field
                .setEnableMediaPlayer(true) // false to disable default sound and default it is true
                .setCustomMediaPlayer(MediaPlayer.create(this, com.accurascan.ocr.mrz.R.raw.beep)) // To add your custom sound and Must have to enable media player
                .init();  // initialized camera
    }

    private void init() {
        viewLeft = findViewById(R.id.view_left_frame);
        viewRight = findViewById(R.id.view_right_frame);
        borderFrame = findViewById(R.id.border_frame);
        tvTitle = findViewById(R.id.tv_title);
        tvScanMessage = findViewById(R.id.tv_scan_msg);
        imageFlip = findViewById(R.id.im_flip_image);
        View btn_flip = findViewById(R.id.btn_flip);
        btn_flip.setOnClickListener(v -> {
            if (cameraView!=null) {
                cameraView.flipCamera();
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (cameraView != null) cameraView.onWindowFocusUpdate(hasFocus);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraView != null) cameraView.onResume();
    }

    @Override
    protected void onPause() {
        if (cameraView != null) cameraView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        AccuraLog.loge(TAG, "onDestroy");
        if (cameraView != null) cameraView.onDestroy();
        super.onDestroy();
        Runtime.getRuntime().gc(); // to clear garbage
    }

    /**
     * Override method call after camera initialized successfully
     *
     * And update your border frame according to width and height
     * it's different for different card
     *
     * Call {@link CameraView#startOcrScan(boolean isReset)} To start Camera Preview
     *
     * @param width    border layout width
     * @param height   border layout height
     */
    @Override
    public void onUpdateLayout(int width, int height) {
        AccuraLog.loge(TAG, "Frame Size (wxh) : " + width + "x" +  height);
        if (cameraView != null) cameraView.startOcrScan(false);

        //<editor-fold desc="To set camera overlay Frame">
        ViewGroup.LayoutParams layoutParams = borderFrame.getLayoutParams();
        layoutParams.width = width;
        layoutParams.height = height;
        borderFrame.setLayoutParams(layoutParams);
        ViewGroup.LayoutParams lpRight = viewRight.getLayoutParams();
        lpRight.height = height;
        viewRight.setLayoutParams(lpRight);
        ViewGroup.LayoutParams lpLeft = viewLeft.getLayoutParams();
        lpLeft.height = height;
        viewLeft.setLayoutParams(lpLeft);

        findViewById(R.id.ocr_frame).setVisibility(View.VISIBLE);
        //</editor-fold>
    }

    /**
     * Override this method after scan complete to get data from document
     *
     * @param result is scanned card data
     *
     */
    @Override
    public void onScannedComplete(RecogResult result) {
        Runtime.getRuntime().gc(); // To clear garbage
        AccuraLog.loge(TAG, "onScannedComplete: ");
        if (result != null) {
            RecogResult.setRecogResult((RecogResult) result);
            sendDataToResultActivity();
        } else Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show();
    }

    private void sendDataToResultActivity() {
        if (cameraView != null) cameraView.release(true);
        Intent intent = new Intent(this, OcrResultActivity.class);
        intent.putExtra("app_orientation", getRequestedOrientation());
        startActivityForResult(intent, 101);
    }

    /**
     * @param titleCode to display scan card message on top of border Frame
     *
     * @param errorMessage To display process message.
     *                null if message is not available
     * @param isFlip  To set your customize animation after complete front scan
     */
    @Override
    public void onProcessUpdate(int titleCode, String errorMessage, boolean isFlip) {
        AccuraLog.loge(TAG, "onProcessUpdate :-> " + titleCode + "," + errorMessage + "," + isFlip);
        Message message;
        if (getTitleMessage(titleCode) != null) {

            message = new Message();
            message.what = 0;
            message.obj = getTitleMessage(titleCode);
            handler.sendMessage(message);
        }
        if (errorMessage != null) {
            message = new Message();
            message.what = 1;
            message.obj = getErrorMessage(errorMessage);
            handler.sendMessage(message);
        }
        if (isFlip) {
            message = new Message();
            message.what = 2;
            handler.sendMessage(message);//  to set default animation or remove this line to set your customize animation
        }

    }

    private String getTitleMessage(int titleCode) {
        if (titleCode < 0) return null;
        switch (titleCode){
            case RecogEngine.SCAN_TITLE_MRZ_FRONT:
                return "Scan Front Side of Document";
            case RecogEngine.SCAN_TITLE_MRZ_BACK:
                return "Now Scan Back Side of Document";
            default:return "";
        }
    }

    private String getErrorMessage(String s) {
        switch (s) {
            case RecogEngine.ACCURA_ERROR_CODE_MOTION:
                return "Keep Document Steady";
            case RecogEngine.ACCURA_ERROR_CODE_PROCESSING:
                return "Processing...";
            case RecogEngine.ACCURA_ERROR_CODE_BLUR_DOCUMENT:
                return "Blur detect in document";
            case RecogEngine.ACCURA_ERROR_CODE_FACE_BLUR:
                return "Blur detected over face";
            case RecogEngine.ACCURA_ERROR_CODE_GLARE_DOCUMENT:
                return "Glare detect in document";
            case RecogEngine.ACCURA_ERROR_CODE_HOLOGRAM:
                return "Hologram Detected";
            case RecogEngine.ACCURA_ERROR_CODE_DARK_DOCUMENT:
                return "Low lighting detected";
            case RecogEngine.ACCURA_ERROR_CODE_PHOTO_COPY_DOCUMENT:
                return "Can not accept Photo Copy Document";
            case RecogEngine.ACCURA_ERROR_CODE_FACE:
                return "Face not detected";
            case RecogEngine.ACCURA_ERROR_CODE_MRZ:
                return "MRZ not detected";
            case RecogEngine.ACCURA_ERROR_CODE_PASSPORT_MRZ:
                return "Passport MRZ not detected";
            case RecogEngine.ACCURA_ERROR_CODE_ID_MRZ:
                return "ID card MRZ not detected";
            case RecogEngine.ACCURA_ERROR_CODE_VISA_MRZ:
                return "Visa MRZ not detected";
            default:
                return s;
        }
    }

    @Override
    public void onError(final String errorMessage) {
        // stop ocr if failed
        tvScanMessage.setText(errorMessage);
        Runnable runnable = () -> Toast.makeText(OcrActivity.this, errorMessage, Toast.LENGTH_LONG).show();
        runOnUiThread(runnable);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == 101) {
                Runtime.getRuntime().gc(); // To clear garbage
                //<editor-fold desc="Call CameraView#startOcrScan(true) To start again Camera Preview
                //And CameraView#startOcrScan(false) To start first time">
                if (cameraView != null) {
                    cameraView.startOcrScan(true);
                }
                //</editor-fold>
            }
        }
    }

}
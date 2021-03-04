package com.accurascan.ocr.mrz;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.accurascan.ocr.mrz.interfaces.OcrCallback;
import com.accurascan.ocr.mrz.util.AccuraLog;
import com.docrecog.scan.MRZDocumentType;
import com.docrecog.scan.OcrView;

public class CameraView {

    private final Activity context;
    private ViewGroup cameraContainer;
    private int cameraFacing;
    private OcrCallback callback;
    private int statusBarHeight = 0;
    private boolean setPlayer = true;
    private MediaPlayer mediaPlayer = null;
    private AudioManager audioManager = null;
    private OcrView ocrView = null;
    private int documentSide = -1;
    private MRZDocumentType documentType = null;

    public CameraView(Activity context) {
        this.context = context;
    }

//    /**
//     * Set Camera type
//     *
//     * @param recogType {@link RecogType}
//     * @return
//     */
//    public CameraView setRecogType(RecogType recogType) {
//        this.type = recogType;
//        return this;
//    }

    /**
     * set document type for mrz document(as like Passport, id, visa or other)
     *
     * @param documentType {@link MRZDocumentType}
     * @return
     */
    public CameraView setMRZDocumentType(MRZDocumentType documentType){
        this.documentType = documentType;
        return this;
    }

    /**
     * add camera on this view
     *
     * @param cameraContainer add camera preview to this view
     * @return
     */
    public CameraView setView(ViewGroup cameraContainer) {
        this.cameraContainer = cameraContainer;
        return this;
    }

    public CameraView setCameraFacing(int cameraFacing) {
        this.cameraFacing = cameraFacing;
        return this;
    }

    public void flipCamera(){
        this.cameraFacing = (this.cameraFacing == 0/*FACING_BACK*/ ? 1/*FACING_FRONT*/ : 0/*FACING_BACK*/);
        if (this.ocrView != null) {
            this.ocrView.flipCamera(this.cameraFacing);
        }
    }

    /**
     * add call back
     *
     * @param callback
     * @return
     */
    public CameraView setOcrCallback(OcrCallback callback) {
        this.callback = callback;
        return this;
    }

    public CameraView setStatusBarHeight(int statusBarHeight) {
        this.statusBarHeight = statusBarHeight;
        return this;
    }


    /**
     * set false to disable sound
     * default true to enable sound
     *
     * @param isPlayMedia is default true
     * @return
     */
    public CameraView setEnableMediaPlayer(boolean isPlayMedia) {
        this.setPlayer = isPlayMedia;
        return this;
    }

    /**
     * set your custom play sound
     *
     * @param mediaPlayer
     * @return
     */
    public CameraView setCustomMediaPlayer(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
        return this;
    }
    /**
     * call this method to initialized camera and ocr
     */
    public void init() {
//        if (this.type == null) {
//            throw new NullPointerException(CameraView.class.getName() + " must have to set recogType");
//        }
        if (this.cameraContainer == null) {
            throw new NullPointerException(CameraView.class.getName() + " must have to setView");
        }
        if (this.callback == null) {
            throw new NullPointerException(context.getClass().getName() + " must have to implement " + OcrCallback.class.getName());
        }

        // initialize media to play sound after scanned.
        if (this.setPlayer) {
            if (this.mediaPlayer == null) {
                this.mediaPlayer = MediaPlayer.create(context, R.raw.beep);
            }
            this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }

//        if (type == RecogType.MRZ) {
            ocrView = new OcrView(context) {
                @Override
                public void onPlaySound() {
                    playEffect();
                }
            };
            ocrView/*.setRecogType(this.type)*/
                    .setView(this.cameraContainer)
                    .setCameraFacing(this.cameraFacing)
                    .setOcrCallBack(this.callback)
                    .setStatusBarHeight(this.statusBarHeight)
                    .setMrzDocumentType(documentType != null ? documentType : MRZDocumentType.NONE);

            ocrView.init();
//        }
    }

    /**
     * call this method from
     *
     * @see com.accurascan.ocr.mrz.interfaces.OcrCallback#onUpdateLayout(int, int)
     * to start your camera preview and ocr
     */
    public void startOcrScan(boolean isReset) {
        AccuraLog.loge("CameraView" , "startOcrScan" + isReset);
        if (ocrView != null) ocrView.startOcrScan();
    }

    /**
     * To handle camera on change window focus
     *
     * @param hasFocus
     */
    public void onWindowFocusUpdate(boolean hasFocus) {
        if (ocrView != null) {
            ocrView.onFocusUpdate(hasFocus);
        }
    }

    /**
     * Call on activity resume to restart preview
     */
    public void onResume() {
        AccuraLog.loge(CameraView.class.getSimpleName(), "onResume()");
        if (ocrView != null) {
            ocrView.resume();
        } /*else if (scannerView != null) scannerView.startScan();*/
    }

    /**
     * Call on activity pause to stop preview
     */
    public void onPause() {
        AccuraLog.loge(CameraView.class.getSimpleName(), "onPause()");
        if (ocrView != null) {
            ocrView.pause();
        }/*else if (scannerView != null) scannerView.stopCamera();*/
    }

    /**
     * Call destroy method to release camera
     */
    public void onDestroy() {
        AccuraLog.loge(CameraView.class.getSimpleName(), "onDestroy()");
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (ocrView != null) {
            ocrView.destroy();
        }
    }

    /**
     * Call this method to flip card
     *
     * @param mFlipImage to animate imageView
     */
    public void flipImage(ImageView mFlipImage) {
        try {
            mFlipImage.setVisibility(View.VISIBLE);
            ObjectAnimator anim = (ObjectAnimator) AnimatorInflater.loadAnimator(context, R.animator.flipping);
            anim.setTarget(mFlipImage);
            anim.setDuration(1000);

            Animator.AnimatorListener animatorListener
                    = new Animator.AnimatorListener() {

                public void onAnimationStart(Animator animation) {
                    playEffect();
                }

                public void onAnimationRepeat(Animator animation) {

                }

                public void onAnimationEnd(Animator animation) {
                    mFlipImage.setVisibility(View.INVISIBLE);
                }

                public void onAnimationCancel(Animator animation) {

                }
            };

            anim.addListener(animatorListener);
            anim.start();
        } catch (Exception e) {

        }
    }

    private void playEffect() {
        if (setPlayer && mediaPlayer != null) {
            if (audioManager != null)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), 0);
            mediaPlayer.start();
        }
    }

    public void release(boolean b) {
        if (ocrView != null) {
            ocrView.closeEngine(b);
        }
    }
}

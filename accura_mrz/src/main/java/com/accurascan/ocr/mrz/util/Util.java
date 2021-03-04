/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.accurascan.ocr.mrz.util;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;

import com.accurascan.ocr.mrz.BuildConfig;
import com.accurascan.ocr.mrz.R;
import com.accurascan.ocr.mrz.camerautil.CameraHolder;
//import com.accurascan.ocr.mrz.camerautil.CameraSizePair;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Collection of utility functions used in this package.
 */
public class Util {
    private static final String TAG = "Util";

    private static final int MIN_CAMERA_PREVIEW_WIDTH = 400;
    private static final int MAX_CAMERA_PREVIEW_WIDTH = 1300;
    private static final int DEFAULT_REQUESTED_CAMERA_PREVIEW_WIDTH = 640;
    private static final int DEFAULT_REQUESTED_CAMERA_PREVIEW_HEIGHT = 360;


    /**
     * If the absolute difference between aspect ratios is less than this tolerance, they are
     * considered to be the same aspect ratio.
     */
    public static final float ASPECT_RATIO_TOLERANCE = 0.01f;

    // The brightness setting used when it is set to automatic in the system.
    // The reason why it is set to 0.7 is just because 1.0 is too bright.
    // Use the same setting among the Camera, VideoCamera and Panorama modes.
    private static final float DEFAULT_CAMERA_BRIGHTNESS = 0.7f;
    
    private static final int OPEN_RETRY_COUNT = 2;

    // Orientation hysteresis amount used in rounding, in degrees
    public static final int ORIENTATION_HYSTERESIS = 5;
    private static final boolean DEBUG = BuildConfig.DEBUG;


    private Util() {
    	
    }

    public static void Assert(boolean cond) {
//        try {
//            if (!cond) {
//                throw new AssertionError();
//            }
//        }catch (Exception e){
//
//        }
    }

    public static File getFilesDirectory(Context context) {
        File file = new File(context.getFilesDir(),"Tempcard");
        if (!file.exists()){
            file.mkdirs();
        }
        return file;
    }
   
    public static Size getMaxPictureSize(List<Size> supported, int camOri) {
		Size maxSize = supported.get(0);

		if (camOri == 0 || camOri == 180) {
			for (Size size : supported) {
				if (size.height > maxSize.height)
					maxSize = size;
			}
		} else {
			for (Size size : supported) {
				if (size.width > maxSize.width)
					maxSize = size;
			}
		}
		return maxSize;
    }
    
    public static Camera openCamera(Activity activity, int cameraId)
            throws Exception {
        // Check if device policy has disabled the camera.
        DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        if (dpm.getCameraDisabled(null)==true) {
            Util.logw(TAG, "cameraDisable" );
            throw new Exception();
        }
        for (int i = 0; i < OPEN_RETRY_COUNT; i++) {
	        try {
	            return CameraHolder.instance().open(cameraId);
	        } 
	        catch (Exception e) 
	        {
	        	if (i == 0) {
	        		try {
	        			//wait some time, and try another time
	        			//Camera device may be using by VT or atv.
	        			Thread.sleep(1000);
	        		} 
	        		catch(InterruptedException ie) 
	        		{
	        		}
	        		continue;
	        	} else {
		            // In eng build, we throw the exception so that test tool
		            // can detect it and report it
		            if ("eng".equals(Build.TYPE)) {
		            	Log.i(TAG, "Open Camera fail", e);
		            	throw e;
		            	//QA will always consider JE as bug, so..
		                //throw new RuntimeException("openCamera failed", e);
		            } 
		            else {
		                throw e;
		            }
	        	}
	        }
        }
        //just for build pass
        throw new Exception(new RuntimeException("Should never get here"));
    }


    public static boolean equals(Object a, Object b) {
        return (a == b) || (a == null ? false : a.equals(b));
    }
    public static int clamp(int x, int min, int max) {
        if (x > max) return max;
        if (x < min) return min;
        return x;
    }
    
    public static double[] getPreviewRatio(View realView, Parameters paramaters) {
    	int viewW = realView.getWidth();
		int viewH = realView.getHeight();
		int previewW = paramaters.getPreviewSize().width;
		int previewH = paramaters.getPreviewSize().height;
		double scaleX = (double) viewW / (double) previewW;
		double scaleY = (double) viewH / (double) previewH;
		double[] ratio = new double[2];
		ratio[0] = scaleX;
		ratio[1] = scaleY;
		return ratio;
    }
    
	
	public static int getDisplayRotation(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        switch (rotation) {
            case Surface.ROTATION_0: return 0;
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
        }
        return 0;
    }

    public static int[] getDisplayOrientation(int degrees, int cameraId) {
        // See android.hardware.Camera.setDisplayOrientation for
        // documentation.
        CameraInfo info = new CameraInfo();
        Camera.getCameraInfo(cameraId, info);
//        int result;
//        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
//            result = (info.orientation + degrees) % 360;
//            result = (360 - result) % 360;  // compensate the mirror
//        } else {  // back-facing
//            result = (info.orientation - degrees + 360) % 360;
//        }
//        return result;

        int angle;
        int displayAngle;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            angle = (info.orientation + degrees) % 360;
            displayAngle = (360 - angle) % 360; // compensate for it being mirrored
        } else { // back-facing
            angle = (info.orientation - degrees + 360) % 360;
            displayAngle = angle;
        }
        return new int[]{angle / 90,displayAngle};
    }

    public static int getCameraOrientation(int cameraId) {
        CameraInfo info = new CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        return info.orientation;
    }

    public static Size getOptimalPreviewSize(Activity currentActivity,
            List<Size> sizes, double targetRatio) {
        // Use a very small tolerance because we want an exact match.
        final double ASPECT_TOLERANCE = 0.001;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        double maxDiff = 0;

        // Because of bugs of overlay and layout, we sometimes will try to
        // layout the viewfinder in the portrait orientation and thus get the
        // wrong size of mSurfaceView. When we change the preview size, the
        // new overlay will be created before the old one closed, which causes
        // an exception. For now, just get the screen size

        Display display = currentActivity.getWindowManager().getDefaultDisplay();
        
        int targetHeight = Math. min(display.getHeight(), display.getWidth());

        if (targetHeight <= 0) {
            // We don't know the size of SurfaceView, use screen height
            targetHeight = display.getHeight();
        }
        
        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
//            if (Math.abs(size.height - targetHeight) > maxDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
//                maxDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSize == null) {
            Log.w(TAG, "No preview size match the aspect ratio");
            minDiff = Double.MAX_VALUE;
            maxDiff = 0;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
//            	if (Math.abs(size.height - targetHeight) > maxDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
//                    maxDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
    public static Size getOptimalPreviewSizeByArea(Activity currentActivity,  List<Size> sizes, int requiredArea) {
        // Use a very small tolerance because we want an exact match.
        if (sizes == null) return null;
        Size optimalSize = null;
        int minDiff = Integer.MAX_VALUE;
        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            int area =  size.width * size.height;
            int diff = Math.abs(requiredArea - area);
            if (diff < minDiff) {
                optimalSize = size;
                minDiff = diff;
            }
        }
        return optimalSize;
    }
    
    public static Size getMaxPreviewSizeWithRatio(Activity currentActivity,
            List<Size> sizes, double targetRatio) {
        // Use a very small tolerance because we want an exact match.
        final double ASPECT_TOLERANCE = 0.001;
        if (sizes == null) return null;

        Size maxPreviewSize = null;
        double maxDiff = 0;

        // Because of bugs of overlay and layout, we sometimes will try to
        // layout the viewfinder in the portrait orientation and thus get the
        // wrong size of mSurfaceView. When we change the preview size, the
        // new overlay will be created before the old one closed, which causes
        // an exception. For now, just get the screen size

        Display display = currentActivity.getWindowManager().getDefaultDisplay();
        
        int targetHeight = Math. min(display.getHeight(), display.getWidth());

        if (targetHeight <= 0) {
            // We don't know the size of SurfaceView, use screen height
            targetHeight = display.getHeight();
        }
        
        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) > maxDiff) {
                maxPreviewSize = size;
                maxDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (maxPreviewSize == null) {
            Log.w(TAG, "No preview size match the aspect ratio");
            maxDiff = 0;
            for (Size size : sizes) {
            	if (Math.abs(size.height - targetHeight) > maxDiff) {
                    maxPreviewSize = size;
                    maxDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return maxPreviewSize;
    }

    public static Size getMaxPreviewSize(List<Size> supported, int camOri) {
		Size maxSize = supported.get(9);
		
//		if (camOri == 0 || camOri == 180) {
//			for (Camera.Size size : supported) {
//				if (size.height > maxSize.height)
//					maxSize = size;
//			}
//		} else {
//			for (Camera.Size size : supported) {
//				if (size.width > maxSize.width)
//					maxSize = size;
//			}
//		}
		return maxSize;
    }

    /**
     * Selects the most suitable preview size, given the desired width and height.
     *
     * <p>Even though we only need to find the preview size, it's necessary to find both the preview
     * size and the picture size of the camera together, because these need to have the same aspect
     * ratio. On some hardware, if you would only set the preview size, you will get a distorted
     * image.But in our case we only deal with preview.
     *
     * @param desiredWidth  the desired width of the camera preview frames
     * @param desiredHeight the desired height of the camera preview frames
     * @param parameters    the Camera.Parameters to select a preview size from
     * @return the selected preview and picture size pair
     */
    public static Camera.Size getPreviewSize(int desiredWidth, int desiredHeight,
                                             Camera.Parameters parameters) {
        Camera.Size result=null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (result == null) {
                result=size;
            }
            if (size.width >= desiredWidth && size.height >= desiredHeight) {
                {
                    int resultArea=result.width * result.height;
                    int newArea=size.width * size.height;

                    if (newArea > resultArea) {
                        result=size;
                    }
                }
            }
        }

        return(result);
    }


    public static void rectFToRect(RectF rectF, Rect rect) {
        rect.left = Math.round(rectF.left);
        rect.top = Math.round(rectF.top);
        rect.right = Math.round(rectF.right);
        rect.bottom = Math.round(rectF.bottom);
    }

    public static void prepareMatrix(Matrix matrix, boolean mirror, int displayOrientation,
            int viewWidth, int viewHeight) {
        // Need mirror for front camera.
        matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(displayOrientation);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
    }


    public static void initializeScreenBrightness(Window win, ContentResolver resolver) {
        // Overright the brightness settings if it is automatic
        int mode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            WindowManager.LayoutParams winParams = win.getAttributes();
            winParams.screenBrightness = DEFAULT_CAMERA_BRIGHTNESS;
            win.setAttributes(winParams);
        }
    }
 

    public static void showErrorAndFinish(final Activity activity, int msgId) {
        DialogInterface.OnClickListener buttonListener =
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                activity.finish();
            }
        };
        new AlertDialog.Builder(activity)
                .setCancelable(false)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                //.setTitle(R.string.camera_error_title)
                .setTitle("")
                .setMessage(msgId)
                .setNeutralButton(R.string.dialog_ok, buttonListener)
                .show();
    }

	public static String getCurTimeString()
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String currentDateandTime = sdf.format(new Date());
		
		return currentDateandTime;
	}


    @RequiresApi(api = Build.VERSION_CODES.M)
    static void requestRuntimePermissions(Activity activity) {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions(activity)) {
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                allNeededPermissions.add(permission);
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            activity.requestPermissions(
                    allNeededPermissions.toArray(new String[0]), /* requestCode= */ 0);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean isPermissionsGranted(Context context) {
        String permission = Manifest.permission.CAMERA;
//        for (String permission : getRequiredPermissions(context)) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
//        }
        return true;
    }

    private static String[] getRequiredPermissions(Context context) {
        try {
            PackageInfo info =
                    context
                            .getPackageManager()
                            .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            return (ps != null && ps.length > 0) ? ps : new String[0];
        } catch (Exception e) {
            return new String[0];
        }
    }

    public static boolean isPortraitMode(Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
    }

    /**
     * Generates a list of acceptable preview sizes. Preview sizes are not acceptable if there is not
     * a corresponding picture size of the same aspect ratio. If there is a corresponding picture size
     * of the same aspect ratio, the picture size is paired up with the preview size.
     *
     * <p>This is necessary because even if we don't use still pictures, the still picture size must
     * be set to a size that is the same aspect ratio as the preview size we choose. Otherwise, the
     * preview images may be distorted on some devices.
     */
//    public static List<CameraSizePair> generateValidPreviewSizeList(Camera camera) {
//        Camera.Parameters parameters = camera.getParameters();
//        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
//        List<Camera.Size> supportedPictureSizes = parameters.getSupportedPictureSizes();
//        List<CameraSizePair> validPreviewSizes = new ArrayList<>();
//        for (Camera.Size previewSize : supportedPreviewSizes) {
//            float previewAspectRatio = (float) previewSize.width / (float) previewSize.height;
//
//            // By looping through the picture sizes in order, we favor the higher resolutions.
//            // We choose the highest resolution in order to support taking the full resolution
//            // picture later.
//            for (Camera.Size pictureSize : supportedPictureSizes) {
//                float pictureAspectRatio = (float) pictureSize.width / (float) pictureSize.height;
//                if (Math.abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
//                    validPreviewSizes.add(new CameraSizePair(previewSize, pictureSize));
//                    break;
//                }
//            }
//        }
//
//        // If there are no picture sizes with the same aspect ratio as any preview sizes, allow all of
//        // the preview sizes and hope that the camera can handle it.  Probably unlikely, but we still
//        // account for it.
//        if (validPreviewSizes.size() == 0) {
//            Log.w(TAG, "No preview sizes have a corresponding same-aspect-ratio picture size.");
//            for (Camera.Size previewSize : supportedPreviewSizes) {
//                // The null picture size will let us know that we shouldn't set a picture size.
//                validPreviewSizes.add(new CameraSizePair(previewSize, null));
//            }
//        }
//
//        return validPreviewSizes;
//    }

    public static void logd(String tag, String s) {
        if (DEBUG) {
            Log.d(tag, s);
        }
    }
    public static void logw(String tag, String s) {
        if (DEBUG) {
            Log.w(tag, s);
        }
    }
}



package com.windyice.qwerty;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.SensorEventListener;
import android.hardware.SensorEventListener2;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;


public class Video2Activity extends AppCompatActivity
        implements TextureView.SurfaceTextureListener {

    private CameraManager mCameraManager;
    private TextureView mPreView;
    private HandlerThread mThreadHandler;
    private Handler mHandler;
    private String mCameraId;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCharacteristics mCharacteristics;
    private StreamConfigurationMap mStreamConfigurationMap;
    private Size largest;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video2);
        mThreadHandler = new HandlerThread("CAMERA2");
        mThreadHandler.start();
        mHandler = new Handler(mThreadHandler.getLooper());
        mPreView = (TextureView) findViewById(R.id.video2_textureview);
        mPreView.setSurfaceTextureListener(this);



    }



    // 为Size定义一个比较器Comparator
    static class CompareSizesByArea implements Comparator<Size>{

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public int compare(Size lhs, Size rhs) {
            // 强转为long保证不会发生溢出
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        mCameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
            assert mCameraManager != null;
            // 获取可用相机列表
            String[] CameraIdList = mCameraManager.getCameraIdList();
            // 在这里可以通过CameraCharacteristics设置相机的功能,当然必须检查是否支持
            mCharacteristics = mCameraManager.getCameraCharacteristics(CameraIdList[0]);
            mStreamConfigurationMap=mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert mStreamConfigurationMap != null;
            int length=mStreamConfigurationMap.getOutputSizes(ImageFormat.JPEG).length;
            largest=mStreamConfigurationMap.getOutputSizes(ImageFormat.JPEG)[0];
            for(int k=1;k<length;k++){
                Size temp=mStreamConfigurationMap.getOutputSizes(ImageFormat.JPEG)[k];
                if(temp.getHeight()>=largest.getHeight()&&temp.getWidth()>=largest.getWidth()){
                    largest=temp;
                }
            }


            mCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //Toast.makeText(getApplicationContext(),"Not support camera",Toast.LENGTH_SHORT).show();
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                ActivityCompat.requestPermissions((Activity) this,new String[]{Manifest.permission.CAMERA}, 321);
            }
            mCameraManager.openCamera(CameraIdList[0], mCameraDeviceStateCallback, mHandler);

        }
        catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    private CameraDevice.StateCallback mCameraDeviceStateCallback=new CameraDevice.StateCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            try{
                startPreview(cameraDevice);
                mImageReader=ImageReader.newInstance(largest.getWidth(),largest.getHeight(),ImageFormat.JPEG,2);
                mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader imageReader) {
                        // 获取捕获的照片数据
                        Image image=imageReader.acquireNextImage();



                        ByteBuffer buffer=image.getPlanes()[0].getBuffer();
                        byte[] bytes=new byte[buffer.remaining()];
                        image.getCropRect();
                        // TODO: 上传服务器
                        Toast.makeText(getApplicationContext(),"上传",Toast.LENGTH_SHORT).show();
                    }
                },null);
            }
            catch (CameraAccessException e){
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {

        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {

        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startPreview(CameraDevice camera) throws CameraAccessException{
        SurfaceTexture texture=mPreView.getSurfaceTexture();
        texture.setDefaultBufferSize(mPreView.getWidth(),mPreView.getHeight());
        Surface surface=new Surface(texture);
        try{
            mPreviewBuilder=camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        }
        catch (CameraAccessException e){
            e.printStackTrace();
        }
        mPreviewBuilder.addTarget(surface);
        camera.createCaptureSession(Collections.singletonList(surface),mSessionStateCallback,mHandler);
    }

    private CameraCaptureSession.StateCallback mSessionStateCallback=new CameraCaptureSession.StateCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            try{
                cameraCaptureSession.setRepeatingRequest(mPreviewBuilder.build(),mSessionCaptureCallback,mHandler);
            }
            catch (CameraAccessException e){
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

        }
    };

    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback=new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {

        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {

        }
    };


//    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//    private void initCameraAndPreview(){
//        Log.d("WindyIce","init camera and preview");
//        HandlerThread handlerThread=new HandlerThread("Camera2");
//        handlerThread.start();
//        mHandler=new Handler(handlerThread.getLooper());
//        try{
//            mCameraId=""+ CameraCharacteristics.LENS_FACING_FRONT;
//            mImageReader=ImageReader.newInstance(mSurfaceView.getWidth(),
//                    mSurfaceView.getHeight(),
//                    ImageFormat.JPEG,7);
//            mImageReader.setOnImageAvailableListener();
//        }
//    }
}

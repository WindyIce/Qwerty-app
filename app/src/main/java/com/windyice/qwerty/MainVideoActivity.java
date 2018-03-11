package com.windyice.qwerty;

import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.hardware.Sensor;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MainVideoActivity extends AppCompatActivity {
    private SenderThread.MyHandler handler;
    private SenderThread senderThread;
    private ByteArrayOutputStream byteArrayOutputStream;

    private Button start;
    private Button stop;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private android.hardware.Camera camera;
    private boolean isPreview=false;   // 是不是在看
    private int screenWidth=300;
    private int screenHeight=400;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main_video);



        handler=new SenderThread.MyHandler();
        senderThread=new SenderThread(handler);
        new Thread(senderThread).start();

        DisplayMetrics displayMetrics=new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenWidth=displayMetrics.widthPixels;// 获取屏幕分辨率
        screenHeight=displayMetrics.heightPixels;

        start=(Button)findViewById(R.id.start_button);
        stop=(Button)findViewById(R.id.stop_button);
        surfaceView=(SurfaceView)findViewById(R.id.surfaceView_video);
        surfaceHolder=surfaceView.getHolder();
        surfaceHolder.setFixedSize(screenWidth,screenHeight/4*3);

        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                initCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                if(camera!=null){
                    if(isPreview){
                        camera.stopPreview();
                    }
                    camera.release();
                    camera=null;
                }
            }
        });
        // 开启连接
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                start.setEnabled(false);
            }
        });

    }



    @SuppressWarnings("deprecation")
    private void initCamera(){
        if(!isPreview){
            camera= android.hardware.Camera.open();
            SenderThread.size=camera.getParameters().getPreviewSize();
        }
        if(camera!=null&&!isPreview){
            try{
                camera.setPreviewDisplay(surfaceHolder);  // Surface view显示画面
                android.hardware.Camera.Parameters parameters=camera.getParameters();
                parameters.setPreviewSize(screenWidth,screenHeight/4*3);
                parameters.setPreviewFrameRate(5);  // 每秒捕获5帧画面
                parameters.setPictureFormat(ImageFormat.NV21);  // 设置图片格式
                parameters.setPictureSize(screenWidth,screenHeight/4*3);  // 设置图片大小
                camera.setDisplayOrientation(90);
                camera.setPreviewCallback(new android.hardware.Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] bytes, android.hardware.Camera camera) {
                        android.hardware.Camera.Size size=camera.getParameters().getPreviewSize();
                        try{
                            // 调用image.compressToJpeg将yuv格式图像数据转成jpeg
                            YuvImage image=new YuvImage(bytes,ImageFormat.NV21,size.width,size.height,null);
                            if(image!=null){
                                Message message=senderThread.recHandler.obtainMessage();
                                message.what=0x111;
                                message.obj=image;
                                senderThread.recHandler.sendMessage(message);
                                /*
                                outstream = new ByteArrayOutputStream();
                                image.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, outstream);
                                outstream.flush();
                                new Thread(senderThread).start();
                                */
                            }
                        }
                        catch (Exception e){
                            Log.e("Sys","Error:"+e.getMessage());
                        }
                    }
                });
                camera.startPreview();
                camera.autoFocus(null);
            }
            catch (IOException e){
                e.printStackTrace();
            }
            isPreview=true;
        }
    }

    static class MyHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            if(msg.what==0x222){

            }
        }
    }
}

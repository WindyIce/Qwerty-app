package com.windyice.qwerty;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SecondVideoActivity extends AppCompatActivity
        implements SurfaceHolder.Callback,
        android.hardware.Camera.PreviewCallback{
    private SurfaceView mSurfaceview = null; // SurfaceView对象：(视图组件)视频显示
    private SurfaceHolder mSurfaceHolder = null; // SurfaceHolder对象：(抽象接口)SurfaceView支持类
    private TextView mTextView;
    private android.hardware.Camera mCamera = null; // Camera对象，相机预览

    private SensorManager mSensorManager;
    private float ax; // x轴上的加速度
    private float ay; // y轴上的加速度
    private float az; // z轴上的加速度
    private float orix; // Pitch
    private float oriy; // Yaw
    private float oriz; // Roll

    /**服务器地址*/
    private String pUsername="XZY";
    /**服务器地址*/
    private String serverUrl="192.168.1.100";
    /**服务器端口*/
    private int serverPort=8888;
    /**视频刷新间隔*/
    private int VideoPreRate=1;
    /**当前视频序号*/
    private int tempPreRate=0;
    /**视频质量*/
    private int VideoQuality=85;

    /**发送视频宽度比例*/
    private float VideoWidthRatio=1;
    /**发送视频高度比例*/
    private float VideoHeightRatio=1;

    /**发送视频宽度*/
    private int VideoWidth=320;
    /**发送视频高度*/
    private int VideoHeight=240;
    /**视频格式索引*/
    private int VideoFormatIndex=0;
    /**是否发送视频*/
    private boolean startSendVideo=false;
    /**是否连接主机*/
    private boolean connectedServer=false;

    private String[] showing=new String[2];

    private Button myBtn01, myBtn02;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second_video);

        mSensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);

        //禁止屏幕休眠        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSurfaceview = (SurfaceView) findViewById(R.id.camera_preview);
        myBtn01=(Button)findViewById(R.id.button1);
        myBtn02=(Button)findViewById(R.id.button2);
        mTextView=(TextView)findViewById(R.id.textview_second_video);

        //开始连接主机按钮
        myBtn01.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                //Common.SetGPSConnected(LoginActivity.this, false);
                if(connectedServer){//停止连接主机，同时断开传输
                    startSendVideo=false;
                    connectedServer=false;
                    myBtn02.setEnabled(false);
                    myBtn01.setText("开始连接");
                    myBtn02.setText("开始传输");
                    //断开连接
                    Thread th = new MySenderCommand("PHONEDISCONNECT|"+pUsername+"|",serverUrl,serverPort);
                    th.start();
                }
                else//连接主机
                {
                    //启用线程发送命令PHONECONNECT
                    Thread th = new MySenderCommand("PHONECONNECT|"+pUsername+"|",serverUrl,serverPort);
                    th.start();
                    connectedServer=true;
                    myBtn02.setEnabled(true);
                    myBtn01.setText("停止连接");
                }
            }});

        myBtn02.setEnabled(false);
        myBtn02.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                if(startSendVideo)//停止传输视频
                {
                    startSendVideo=false;
                    myBtn02.setText("开始传输");
                }
                else{ // 开始传输视频
                    startSendVideo=true;
                    myBtn02.setText("停止传输");
                }
            }});
    }

    private SensorListener mOriSensorListener= new SensorListener() {
        @Override
        public void onSensorChanged(int sensor, float[] values) {
            if(sensor==SensorManager.SENSOR_ORIENTATION){
                orix=values[0];
                oriy=values[1];
                oriz=values[2];

                String toShow="Ori: x:"+orix+" y:"+oriy+" z:"+oriz;
                showing[0]=toShow;
                showing[1]=(showing[1].equals("")||showing[1]==null)?"":showing[1];
                String toSet=showing[0]+"\n"+showing[1];
                mTextView.setText(toSet);
            }
        }

        @Override
        public void onAccuracyChanged(int i, int i1) {

        }
    };

    private SensorListener mAccSensorListener =new SensorListener() {
        @Override
        public void onSensorChanged(int sensor, float[] values) {
            if(sensor==SensorManager.SENSOR_ACCELEROMETER){
                ax=values[0];
                ay=values[1];
                az=values[2];
                String toShow="Acc: x:"+ax+" y:"+ay+" z:"+az;
                showing[0]=(showing[0]==""||showing[0]==null)?"":showing[0];
                showing[1]=toShow;
                String toSet=showing[0]+"\n"+showing[1];
                mTextView.setText(toSet);
            }
        }

        @Override
        public void onAccuracyChanged(int i, int i1) {}
    };




    @Override
    public void onStart()//重新启动的时候
    {
        mSurfaceHolder = mSurfaceview.getHolder(); // 绑定SurfaceView，取得SurfaceHolder对象
        mSurfaceHolder.addCallback(this); // SurfaceHolder加入回调接口
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);// 设置显示器类型，setType必须设置
        //读取配置文件
        SharedPreferences preParas = PreferenceManager.getDefaultSharedPreferences(SecondVideoActivity.this);
        pUsername=preParas.getString("Username", "XZY");
        serverUrl=preParas.getString("ServerUrl", "192.168.0.100");
        String tempStr=preParas.getString("ServerPort", "8888");
        serverPort=Integer.parseInt(tempStr);
        tempStr=preParas.getString("VideoPreRate", "1");
        VideoPreRate=Integer.parseInt(tempStr);
        tempStr=preParas.getString("VideoQuality", "85");
        VideoQuality=Integer.parseInt(tempStr);
        tempStr=preParas.getString("VideoWidthRatio", "100");
        VideoWidthRatio=Integer.parseInt(tempStr);
        tempStr=preParas.getString("VideoHeightRatio", "100");
        VideoHeightRatio=Integer.parseInt(tempStr);
        VideoWidthRatio=VideoWidthRatio/100f;
        VideoHeightRatio=VideoHeightRatio/100f;

        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(   // 注册监听器
                mAccSensorListener,           // 监听器对象
                SensorManager.SENSOR_ACCELEROMETER,   // 传感器类型
                SensorManager.SENSOR_DELAY_UI);       // 传感器事件传递的额度
        /*mSensorManager.registerListener(
                mOriSensorListener,
                SensorManager.SENSOR_ORIENTATION,
                SensorManager.SENSOR_DELAY_UI);*/
        InitCamera();
    }

    /**初始化摄像头*/
    private void InitCamera(){
        try{
            mCamera = android.hardware.Camera.open();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        mSensorManager.unregisterListener(mAccSensorListener);
        //mSensorManager.unregisterListener(mOriSensorListener);
        super.onPause();
        try{
            if (mCamera != null) {
                mCamera.setPreviewCallback(null); // ！！这个必须在前，不然退出出错
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
        // TODO Auto-generated method stub
        if (mCamera == null) {
            return;
        }
        mCamera.stopPreview();
        mCamera.setPreviewCallback(this);
        mCamera.setDisplayOrientation(90); //设置横行录制
        //获取摄像头参数
        android.hardware.Camera.Parameters parameters = mCamera.getParameters();
        android.hardware.Camera.Size size = parameters.getPreviewSize();
        VideoWidth=size.width;
        VideoHeight=size.height;
        VideoFormatIndex=parameters.getPreviewFormat();

        mCamera.startPreview();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // TODO Auto-generated method stub
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.startPreview();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // TODO Auto-generated method stub
        if (null != mCamera) {
            mCamera.setPreviewCallback(null); // ！！这个必须在前，不然退出出错
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, android.hardware.Camera camera) {
        // TODO Auto-generated method stub
        //如果没有指令传输视频，就先不传
        if(!startSendVideo)
            return;
        if(tempPreRate<VideoPreRate){
            tempPreRate++;
            return;
        }
        tempPreRate=0;
        try {
            if(data!=null)
            {
                YuvImage image = new YuvImage(data,VideoFormatIndex, VideoWidth, VideoHeight,null);
                if(image!=null)
                {
                    ByteArrayOutputStream outstream = new ByteArrayOutputStream();
                    //在此设置图片的尺寸和质量
                    image.compressToJpeg(new Rect(0, 0, (int)(VideoWidthRatio*VideoWidth),
                            (int)(VideoHeightRatio*VideoHeight)), VideoQuality, outstream);

                    outstream.flush();
                    //启用线程将图像数据发送出去
                    Thread th = new MySendFileThread(outstream,pUsername,serverUrl,serverPort);
                    th.start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**创建菜单*/
    public boolean onCreateOptionsMenu(Menu menu)
    {
        menu.add(0,0,0,"系统设置");
        menu.add(0,1,1,"关于程序");
        menu.add(0,2,2,"退出程序");
        return super.onCreateOptionsMenu(menu);
    }
    /**菜单选中时发生的相应事件*/
    public boolean onOptionsItemSelected(MenuItem item)
    {
        super.onOptionsItemSelected(item);//获取菜单
        switch(item.getItemId())//菜单序号
        {
            case 0:
                //系统设置
            {
                //Intent intent=new Intent(this,SettingActivity.class);
                //startActivity(intent);
            }
            break;
            case 1://关于程序
            {
                new AlertDialog.Builder(this)
                        .setTitle("关于本程序")
                        .setMessage("本程序由WindyIce设计、编写。\nEmail：[url=mailto:windyicec@gmail.com]windyicec@gmail.com[/url]")
                        .setPositiveButton
                                (
                                        "我知道了",
                                        new DialogInterface.OnClickListener()
                                        {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which)
                                            {
                                            }
                                        }
                                )
                        .show();
            }
            break;
            case 2://退出程序
            {
                //杀掉线程强制退出
                android.os.Process.killProcess(android.os.Process.myPid());
            }
            break;
        }
        return true;
    }
}

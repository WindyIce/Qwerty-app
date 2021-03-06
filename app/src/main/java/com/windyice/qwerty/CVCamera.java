package com.windyice.qwerty;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class CVCamera extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    private static final String TAG="WindyIce_Activity";
    private static final byte ACC_SENSOR_CHANGE =0x1;
    private static final byte ORI_SENSOR_CHANGE=0x2;
    private static final int TAKE_PHOTOS_REQUEST_CODE =123;
    private static final int CAMERA_AUTHORIZE_CODE=321;
    private static final int WRITE_EXTERNAL_STORAGE_AUTHORIZE_CODE=322;
    private static final int MOUNT_UNMOUNT_FILESYSTEMS_AUTHORIZE_CODE=323;
    private static final int MQTT_RECEIVE=8;
    private static final int MQTT_SEND=9;

    private static final String MQTT_MESSAGE_CONNECT="cnet";
    private static final String MQTT_MESSAGE_NORMAL_RENDER="q3mr";
    private static final String MQTT_MESSAGE_TEST_RENDER="cpdb";

    private static final String MQTT_MESSAGE_TOPIC ="QRFA";

    public static final double PI=3.1415926535897932384626434;

    private CameraBridgeViewBase mOpenCvCameraView;

    private boolean mIsJavaCamera=true;
    private MenuItem mItemSwitchCamera=null;
    private TextView mTextview; // 加速传感器
    private TextView mTextview1; // 姿态传感器
    private TextView mTextview2; // 照了几张
    private TextView mTextview3; // 位置显示
    private TextView mTextview4;
    private Camera camera;

    private List<Mat> chessboardList=new ArrayList<>();

    private Button mButton;
    private Button mButton_takephoto;
    private boolean takingPhotos=false;

    private boolean canUpdateR1=false;

    private Mat R3;
    private Mat R2;
    private Mat R1;

    private double factor=1;

    private List<Mat> R3TList=new ArrayList<>();

    private final double stopQuota=0.03;

    //private final String imageSaveDir="/Qwerty/com.windyice.qwerty/imgs/Chessboard";
    //private int imageIndex=1; // 随着图像文件不断保存，增加的一个索引

    private SensorManager mSensorManager; // 管理器对象
    private Sensor mOriSensor;  // 姿态传感器
    private float[] orientation=new float[3]; // 姿态值
    private Sensor mAccSensor;  // 加速度传感器
    private float[] linear_acceleration=new float[3]; // 线性加速度值
    private Mat rotationMatrix_phone2world;
    private boolean supportSensor=true; // 是否支持传感器
    private final double DELTA_TIME =0.02; // 单位：秒

    private Point3 startPoint; // 手机开始的位置
    private Point3 mPosWorld=new Point3(); // 离手机开始位置的位置

    private List<Point3> mPosWorldListBeforeRecognize=new ArrayList<>();// 拍照前用于计算refactor的list
    private Point3 mPosScreen; // 相对屏幕的坐标
    private Point3 mSpeed=new Point3(); // 实时速度
    private Point3 mAcceleration;


    private boolean isStartComputingPoint=false; // 是否开始计算现在的位置

    private class DisconnectThread implements Runnable{
        private MqttBaseOperation mqttBaseOperation;
        public DisconnectThread(MqttBaseOperation _mqttBaseOperation){
            mqttBaseOperation=_mqttBaseOperation;
        }

        @Override
        public void run() {
            try {
                mqttBaseOperation.disconnect();
            }
            catch (Exception e){
                Log.i(TAG,e.getMessage());
                Log.i(TAG,Utils.getStackTrackString(e));
            }
        }
    }

    private class PublishThread implements Runnable {
        private String topic;
        private byte[] message;
        public PublishThread(String _topic,byte[] _message){
            topic=_topic;
            message=_message;
        }
        @Override
        public void run() {
            try {
                MqttMessage mqttMessage=new MqttMessage(message);
                mqttBaseOperation.publish(topic, mqttMessage);
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void publish(String topic,String message){
        PublishThread publishThread=new PublishThread(topic,message.getBytes());
        new Thread(publishThread).start();
        mqttBaseOperation.startReconnect(3000,true);
    }

    private BaseLoaderCallback mLoaderCallback=new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface.SUCCESS:{
                    Log.i(TAG,"OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                }break;
                default:{
                    super.onManagerConnected(status);
                }break;
            }
        }
    };

    private MqttBaseOperation mqttBaseOperation=
            new MqttBaseOperation("tcp://"+Utils.hostIP+":"+Utils.hostPort,Utils.clientId);

    private void authorize(){
        // 获得权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) this,new String[]{Manifest.permission.CAMERA}, CAMERA_AUTHORIZE_CODE);
        }
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions((Activity)this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},WRITE_EXTERNAL_STORAGE_AUTHORIZE_CODE);
        }
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions((Activity)this,new String[]{Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS},MOUNT_UNMOUNT_FILESYSTEMS_AUTHORIZE_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode== TAKE_PHOTOS_REQUEST_CODE &&resultCode==RESULT_OK){
            Toast.makeText(this,"照片保存成功",Toast.LENGTH_SHORT).show();
        }
        //super.onActivityResult(requestCode, resultCode, data);
    }

    private void viewInit(){
        mOpenCvCameraView=(CameraBridgeViewBase)findViewById(R.id.activity_cvcamera_surfaceview);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mTextview=(TextView)findViewById(R.id.activity_cvcamera_textview);
        mTextview1=(TextView)findViewById(R.id.activity_cvcamera_textview1);
        mTextview2=(TextView)findViewById(R.id.activity_cvcamera_textview2);
        mTextview3=(TextView)findViewById(R.id.activity_cvcamera_textview3);
        mTextview4=(TextView)findViewById(R.id.activity_cvcamera_textview4);
        String stringBuilder = "Location: \n" +
                "x: " + 0 + "\n" +
                "y: " + 0 + "\n" +
                "z: " + 0;
        mTextview3.setText(stringBuilder);
        mButton=(Button)findViewById(R.id.activity_cvcamera_button);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CameraRec();
            }
        });
        mButton_takephoto=(Button)findViewById(R.id.activity_cvcamera_button_takephoto);

        mButton_takephoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mOpenCvCameraView!=null){
                    // 每帧的回调CvCameraListener2中会检测这个值
                    takingPhotos=true;
                    R3TList.add(rotationMatrix_phone2world);
                    mPosWorldListBeforeRecognize.add(new Point3(mPosWorld.x,mPosWorld.y,mPosWorld.z));
                }
                // 正常拍照
//                try {
//                    String path = Environment.getExternalStorageDirectory() + File.separator + "images";
//                    String filename = "Chessboard" + imageIndex + ".jpg";
//                    File file = new File(path, filename);
//
//                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//                    ContentValues contentValues = new ContentValues(1);
//                    contentValues.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
//                    Uri imageUri = getApplicationContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
//                    intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
//                    startActivityForResult(intent, TAKE_PHOTOS_REQUEST_CODE);
//                }
//                catch (Exception e){
//                    Log.i(TAG,e.getMessage());
//                    Log.i(TAG,Utils.getStackTrackString(e));
//                }
            }
        });
    }

    private void sensorInit(){
        try {
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

            if(mSensorManager==null) throw new Exception("WindyIce::Sensor Manager null or not found.");
            mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            if(mAccSensor==null){
                Toast.makeText(this,"当前设备不支持加速度传感器",Toast.LENGTH_SHORT).show();
            }
            mOriSensor=mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        }
        catch (Exception e){
            supportSensor=false;
            Log.i(TAG,e.getMessage());
            Log.i(TAG,Utils.getStackTrackString(e));
        }
    }

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_cvcamera);

        authorize(); // 获得权限
        viewInit(); // 获得xml对象
        sensorInit();


    }

    private boolean tag1=true;

    /*
* @param A 被乘矩阵
* @param B 乘矩阵
* @param C 结果矩阵
* */
    public static Mat matMul(Mat A,Mat B,Mat C){
        Core.gemm(A,B,1.0,Mat.zeros(A.size(),A.type()),0.0,C);
        return C;
    }

    private void CameraRec(){
        if(tag1){
            if(Utils.testModeOn) publish(MQTT_MESSAGE_TOPIC,MQTT_MESSAGE_TEST_RENDER);
            else publish(MQTT_MESSAGE_TOPIC,MQTT_MESSAGE_NORMAL_RENDER);
            tag1=false;
        }
        if(chessboardList.size()<=5){
            Toast.makeText(this,"还没有足够的照片!",Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if(camera==null) {
                camera=new Camera();

            }
            for(int i=0;i<chessboardList.size();i++){
                camera.RecognizeChessboard(chessboardList.get(i),false);
            }
            camera.Calibrate();
            List<Mat> R1List;
            R1List=camera.getmRotationMatrixList();
            List<Point3> translationPointList=camera.getmTranslationMatrixList();
            double factorSum=0;
            for(int i=0;i<translationPointList.size()-1;i++){
                Point3 cameraPoint=translationPointList.get(i);
                double x=Math.sqrt(cameraPoint.x*cameraPoint.x+cameraPoint.y*cameraPoint.y+cameraPoint.z*cameraPoint.z);
                Point3 sensorPoint=mPosWorldListBeforeRecognize.get(i);
                double y=Math.sqrt(sensorPoint.x*sensorPoint.x+sensorPoint.y*sensorPoint.y+sensorPoint.z*sensorPoint.z);
                factorSum+=(y/x);
            }
            factor=factorSum/(translationPointList.size()-1);
            R2=matMul(R3TList.get(chessboardList.size()-1),R1List.get(chessboardList.size()-1),R2);  // 直接就取第一张
            canUpdateR1=true;
            R1=new Mat();
            Log.i(TAG,camera.toString());
            Utils.globalCalibrateInformation=camera.toString();
            isStartComputingPoint=true;
            startPoint=new Point3(0,0,0); // TODO: 和屏幕坐标对应起来！
            mPosWorld =new Point3(0,0,0);
            mSpeed=new Point3(0,0,0);
            Toast.makeText(this,"识别成功!",Toast.LENGTH_LONG).show();
        }
        catch (Exception e){
            Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
            Log.i(TAG,e.getMessage());
            Log.i(TAG,Utils.getStackTrackString(e));
            Utils.globalCalibrateInformation=Utils.getStackTrackString(e);
        }
//        Bitmap bitmap1= BitmapFactory.decodeResource(getResources(),R.drawable.jg1);
//        Bitmap bitmap2= BitmapFactory.decodeResource(getResources(),R.drawable.jg2);
//        Bitmap bitmap3= BitmapFactory.decodeResource(getResources(),R.drawable.jg3);
//        Bitmap bitmap4= BitmapFactory.decodeResource(getResources(),R.drawable.jg4);
//        Bitmap bitmap5= BitmapFactory.decodeResource(getResources(),R.drawable.jg5);
//        Bitmap bitmap6= BitmapFactory.decodeResource(getResources(),R.drawable.jg6);
//        Bitmap bitmap7= BitmapFactory.decodeResource(getResources(),R.drawable.jg7);
//        Bitmap bitmap8= BitmapFactory.decodeResource(getResources(),R.drawable.jg8);
//        Bitmap bitmap9= BitmapFactory.decodeResource(getResources(),R.drawable.jg9);
//        Mat mat1=new Mat();
//        org.opencv.android.Utils.bitmapToMat(bitmap1,mat1);
//        Mat mat2=new Mat();
//        org.opencv.android.Utils.bitmapToMat(bitmap2,mat2);
//        Mat mat3=new Mat();
//        org.opencv.android.Utils.bitmapToMat(bitmap3,mat3);
//        Mat mat4=new Mat();
//        org.opencv.android.Utils.bitmapToMat(bitmap4,mat4);
//        Mat mat5=new Mat();
//        org.opencv.android.Utils.bitmapToMat(bitmap5,mat5);
//        Mat mat6=new Mat();
//        org.opencv.android.Utils.bitmapToMat(bitmap6,mat6);
//        Mat mat7=new Mat();
//        org.opencv.android.Utils.bitmapToMat(bitmap7,mat7);
//        Mat mat8=new Mat();
//        org.opencv.android.Utils.bitmapToMat(bitmap8,mat8);
//        Mat mat9=new Mat();
//        org.opencv.android.Utils.bitmapToMat(bitmap9,mat9);
//        try {
//            camera.RecognizeChessboard(mat1, false);
//            camera.RecognizeChessboard(mat2, false);
//            camera.RecognizeChessboard(mat3, false);
//            camera.RecognizeChessboard(mat4, false);
//            camera.RecognizeChessboard(mat5, false);
//            camera.RecognizeChessboard(mat6, false);
//            camera.RecognizeChessboard(mat7, false);
//            camera.RecognizeChessboard(mat8, false);
//            camera.RecognizeChessboard(mat9, false);
//            camera.Calibrate();
//
//            Log.i(TAG,camera.toString());
//        }
//        catch (Exception e){
//            Log.i(TAG,e.getMessage());
//            Log.i("WindyIce_Activity", Utils.getStackTrackString(e));
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mOpenCvCameraView!=null){
            mOpenCvCameraView.disableView();
        }
        if(supportSensor) {
            mSensorManager.unregisterListener(accSensorEventListener);
            mSensorManager.unregisterListener(oriSensorEventListener);
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            new Thread(new DisconnectThread(mqttBaseOperation)).start();
            wait();
        }
        catch (Exception e){
            Log.i(TAG,e.getMessage());
            Log.i(TAG,Utils.getStackTrackString(e));
        }
        if(mOpenCvCameraView!=null){
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mqttBaseOperation.Setting(true,10,20);
        mqttBaseOperation.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {

                Log.i(TAG,"Mqtt::ConnectionLost");

                mqttBaseOperation.startReconnect(2000);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.i(TAG,topic+": topic\nmessage: "+message.toString());
                if(topic=="QRFPC") {
                    Message message1 = new Message();
                    message1.what = MQTT_RECEIVE;
                    message1.obj = message.toString();
                    mqttBaseOperation.getHandler().sendMessage(message1);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
        mqttBaseOperation.subscribe("QRFPC");
        mqttBaseOperation.setHandler(new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what){
                    case MQTT_RECEIVE:{
                        try {
                            String[] msgSplit = msg.obj.toString().split("_");
                            if(msgSplit[0].toLowerCase().equals("wss")){
                                Utils.chessboardWorldSpaceSize= Double.parseDouble(msgSplit[1]);
                            }
                        }
                        catch (Exception e){
                            Log.i(TAG,e.getMessage());
                            Log.i(TAG,Utils.getStackTrackString(e));
                        }

                    }break;
                    case MQTT_SEND:{

                    }break;
                }
            }
        });
        mqttBaseOperation.connect();
        publish(MQTT_MESSAGE_TOPIC,MQTT_MESSAGE_CONNECT);

        if (!OpenCVLoader.initDebug()) {
            Log.i(TAG, "Internal OpenCv library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback); // VERSION_3_0_0
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mOpenCvCameraView.setMaxFrameSize(960,1280);
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        if (supportSensor) {
            // FAST 0ms?    GAME 20ms    UI 60ms    NORMAL 200ms
            mSensorManager.registerListener(accSensorEventListener, mAccSensor, SensorManager.SENSOR_DELAY_GAME);
            mSensorManager.registerListener(oriSensorEventListener,mOriSensor,SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        try {
            if (takingPhotos) {
                takingPhotos = false;
                //Imgcodecs.imwrite(imageSaveDir+imageIndex+".jpg",inputFrame.rgba());
                chessboardList.add(inputFrame.rgba());
                //camera.RecognizeChessboard(inputFrame.rgba(), false);
                Log.i(TAG, "照片保存成功");
                mTextview2.setText("当前拍了"+chessboardList.size()+"张相");
            }

        }
        catch (Exception e){
            Log.i(TAG,e.getMessage());
            Log.i(TAG,Utils.getStackTrackString(e));
        }
        Mat dst = new Mat();
        Mat gray = inputFrame.rgba();
        Mat rotateMat = Imgproc.getRotationMatrix2D(new Point(gray.rows()/2,gray.cols()/2), 270, 1);
        Imgproc.warpAffine(gray, dst, rotateMat, dst.size());
        return dst;
    }

    // 线性加速度传感器监听
    private SensorEventListener accSensorEventListener =new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            float[] values=sensorEvent.values;

            Message message=new Message();
            message.obj=values;
            message.what= ACC_SENSOR_CHANGE;
            mHandler.handleMessage(message);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {}
    };

    // 姿态传感器监听
    private SensorEventListener oriSensorEventListener=new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            float[] values=sensorEvent.values;
            Message message=new Message();
            message.obj=values;
            message.what= ORI_SENSOR_CHANGE;
            mHandler.handleMessage(message);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };



    private void publish(String topic,byte[] message){
        PublishThread publishThread=new PublishThread(topic,message);
        new Thread(publishThread).start();
        mqttBaseOperation.startReconnect(3000,true);
    }

    private boolean canbeStopValue(float[] values){
        if(values.length!=3) return false;
        return (values[0] * values[0] + values[1] * values[1] + values[2] * values[2]) < stopQuota * stopQuota;
    }
    private void stopValue(float[] values){
        for(int i=0;i<values.length;i++){
            values[i]=0;
        }
    }
    private boolean canbeStopValue(double[] values){
        if(values.length!=3) return false;
        return values[0] * values[0] + values[1] * values[1] + values[2] * values[2] < stopQuota * stopQuota;
    }
    private void stopValue(double[] values){
        for(int i=0;i<values.length;i++){
            values[i]=0;
        }
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            try {
                switch (msg.what) {
                    case ACC_SENSOR_CHANGE: {
                        float[] values = (float[]) msg.obj;

                        //if (isStartComputingPoint) {
                            linear_acceleration = values;
                            //cut-off the velocity and acc if the length of acc is lower than a threshold
                            if(canbeStopValue(linear_acceleration)) stopValue(linear_acceleration);
                            List<Float> vector_local_acceleration = new ArrayList<>();
                            for (float a : linear_acceleration) {
                                vector_local_acceleration.add(a);
                            }
                            Mat linear_acceleration_world = new Mat(3,3,CvType.CV_32FC1);
                            linear_acceleration_world = Camera.matMul(
                                    rotationMatrix_phone2world,
                                    Converters.vector_float_to_Mat(vector_local_acceleration),
                                    linear_acceleration_world   );
                            List<Float> accelerationList = new ArrayList<>();
                            Converters.Mat_to_vector_float(linear_acceleration_world, accelerationList);
                            float[] linear_accleration_array = new float[3];
                            linear_accleration_array[0] = accelerationList.get(0);
                            linear_accleration_array[1] = accelerationList.get(1);
                            linear_accleration_array[2] = accelerationList.get(2);

                            mSpeed.x+=linear_accleration_array[0]* DELTA_TIME;
                            mSpeed.y+=linear_accleration_array[1]* DELTA_TIME;
                            mSpeed.z+=linear_accleration_array[2]* DELTA_TIME;

                            double[] speed={mSpeed.x,mSpeed.y,mSpeed.z};
                            if(canbeStopValue(speed)||canbeStopValue(linear_accleration_array)){
                                stopValue(speed);
                                mSpeed.x=0;
                                mSpeed.y=0;
                                mSpeed.z=0;
                            }

                            mTextview4.setText("Speed\n" + "x: " + mSpeed.x + "\ny: " + mSpeed.y + "\nz:"  + mSpeed.z);

                            mPosWorld.x+=(mSpeed.x* DELTA_TIME);
                            mPosWorld.y+=(mSpeed.y* DELTA_TIME);
                            mPosWorld.z+=(mSpeed.z* DELTA_TIME);

                            String stringBuilder = "Location: \n" +
                                    "x: " + mPosWorld.x + "\n" +
                                    "y: " + mPosWorld.y + "\n" +
                                    "z: " + mPosWorld.z;
                            mTextview3.setText(stringBuilder);

                            if(canUpdateR1){
                            Mat mPosWorldMat=new Mat(3,1,CvType.CV_32FC1);
                            mPosWorldMat.put(0,0,mPosWorld.x);
                            mPosWorldMat.put(1,0,mPosWorld.y);
                            mPosWorldMat.put(2,0,mPosWorld.z);
                            Mat mPosScreenMat=new Mat(3,1,CvType.CV_32FC1);

                            // 这里的R2一定是拍完所有照片才初始化的！！！
                            mPosScreenMat=matMul(R2.t(),mPosWorldMat,mPosScreenMat);
                            mPosScreen.x=mPosScreenMat.get(0,0)[0];
                            mPosScreen.y=mPosScreenMat.get(1,0)[0];
                            mPosScreen.z=mPosScreenMat.get(2,0)[0];

                            // 这里和下面的position都一定要乘一个factor

                            publish(MQTT_MESSAGE_TOPIC,"cpos"+(float)mPosScreen.x*factor+" "+(float)mPosScreen.y*factor+" "+(float)mPosScreen.z*factor);
                            }
                            else{
                                publish(MQTT_MESSAGE_TOPIC,"cpos"+(float)mPosWorld.x*factor+" "+(float)mPosWorld.y*factor+" "+(float)mPosWorld.z*factor);
                            }
                        //}
//                        else{
//                            publish(MQTT_MESSAGE_TOPIC,"cpos"+values[0]+
//                                " "+values[1]+" "+values[2]);
//                        }
                        String string = "LINEAR_ACCELERATION:\nx: " + values[0] + "\n" + "y: " + values[1] + "\n" + "z: " + values[2] + "\n";
                        mTextview.setText(string);
                    }
                    break;
                    case ORI_SENSOR_CHANGE: {
                        if (rotationMatrix_phone2world == null)
                            rotationMatrix_phone2world = new Mat(3, 3, CvType.CV_32FC1);
                        float[] values = (float[]) msg.obj;
                        //if (isStartComputingPoint) {
                            orientation = values;
                            double y = Math.toRadians(values[0]); // y---Yaw的弧度值
                            double x = Math.toRadians(values[1]); // x---Pitch的弧度值
                            double z = Math.toRadians(values[2]); // z---Roll的弧度值
                            double c1 = Math.cos(y);
                            double c2 = Math.cos(x);
                            double c3 = Math.cos(z);
                            double s1 = Math.sin(y);
                            double s2 = Math.sin(x);
                            double s3 = Math.sin(z);
                            // TODO : 转换一波！
                            Mat tempMat = new Mat(3, 3, CvType.CV_32FC1);
                            tempMat.put(0, 0, c1 * c3 + s1 * s2 * s3);
                            tempMat.put(0, 1, c3 * s1 * s2 - c1 * s3);
                            tempMat.put(0, 2, c2 * s1);
                            tempMat.put(1, 0, c2 * s3);
                            tempMat.put(1, 1, c2 * c3);
                            tempMat.put(1, 2, -s2);
                            tempMat.put(2, 0, c1 * s2 * s3 - c3 * s1);
                            tempMat.put(2, 1, c1 * c3 * s2 + s1 * s3);
                            tempMat.put(2, 2, c1 * c2);
                            rotationMatrix_phone2world = tempMat.t(); // 会不会出错？是逆矩阵还是？这里直接优化成transpose(正交矩阵的逆等于转置)
                            R3=tempMat;
                            if(canUpdateR1){
                                R1=matMul(R3,R2,R1);
                            }

                        //}
                        String string = "ORIENTATION:\nYaw: " + values[0] + "\n" + "Pitch: " + values[1] + "\n" + "Roll: " + values[2] + "\n";
                        mTextview1.setText(string);
                    }
                    break;
                }
            }
            catch (Exception e){
                Log.i(TAG,e.getMessage());
                Log.i(TAG,Utils.getStackTrackString(e));
            }
            super.handleMessage(msg);
        }

    };
}

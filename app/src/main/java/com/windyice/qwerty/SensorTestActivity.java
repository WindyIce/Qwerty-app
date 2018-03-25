package com.windyice.qwerty;

import android.annotation.SuppressLint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point3;
import org.opencv.utils.Converters;

import java.util.ArrayList;
import java.util.List;

public class SensorTestActivity extends AppCompatActivity {

    private final static String TAG="WindyIce_SensorTest";
    private static final byte ACC_SENSOR_CHANGE =0x1;
    private static final byte ORI_SENSOR_CHANGE=0x2;

    private SensorManager mSensorManager; // 管理器对象
    private Sensor mOriSensor;  // 姿态传感器
    private float[] orientation=new float[3]; // 姿态值
    private Sensor mAccSensor;  // 加速度传感器
    private float[] linear_acceleration=new float[3]; // 线性加速度值
    private Mat rotationMatrix_phone2world;
    private boolean supportSensor=true; // 是否支持传感器
    private final double DELTA_TIME =0.02; // 单位：秒

    private Point3 startPoint=new Point3(); // 手机开始的位置
    private Point3 mPosWorld=new Point3(); // 离手机开始位置的位置
    private Point3 mSpeed=new Point3(); // 实时速度
    private Point3 mAcceleration=new Point3();
    private boolean isStartComputingPoint=true; // 是否开始计算现在的位置

    private double stopQuota=0.01;

    private TextView mTextView_linearAcceleration_Phone;
    private TextView mTextView_orientation;
    private TextView mTextView_linearAccleration_World;
    private TextView mTextView_speed;
    private TextView mTextView_location;
    private EditText mEditText;
    private Button mButton;

    private MqttBaseOperation mqttBaseOperation=
            new MqttBaseOperation("tcp://"+Utils.hostIP+":"+Utils.hostPort,Utils.clientId);

    private void makeToast(String text){
        Toast.makeText(this,text,Toast.LENGTH_LONG).show();
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

    private void viewInit(){
        mTextView_linearAcceleration_Phone=(TextView) findViewById(R.id.sensortest_activity_textview_linearAccPhone);
        mTextView_orientation=(TextView) findViewById(R.id.sensortest_activity_textview_orientation);
        mTextView_linearAccleration_World=(TextView)findViewById(R.id.sensortest_activity_textview_linearAccWorld);
        mTextView_speed=(TextView)findViewById(R.id.sensortest_activity_textview_speed);
        mTextView_location=(TextView)findViewById(R.id.sensortest_activity_textview_location);
        mEditText=(EditText)findViewById(R.id.sensortest_activity_edittext1);
        mButton=(Button)findViewById(R.id.sensortest_activity_button1);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String input=mEditText.getText().toString();
                try{
                    stopQuota= Double.parseDouble(input);
                }
                catch (Exception e){
                    makeToast("Invalid input!");
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(supportSensor) {
            mSensorManager.unregisterListener(accSensorEventListener);
            mSensorManager.unregisterListener(oriSensorEventListener);
        }
    }
    @Override
    protected void onResume() {
        super.onResume();

        if (supportSensor) {
            // FAST 0ms?    GAME 20ms    UI 60ms    NORMAL 200ms
            mSensorManager.registerListener(accSensorEventListener, mAccSensor, SensorManager.SENSOR_DELAY_GAME);
            mSensorManager.registerListener(oriSensorEventListener,mOriSensor,SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_test);

        sensorInit();
        viewInit();
        mqttBaseOperation.Setting(true,10,20);
        mqttBaseOperation.publish(Utils.topic,new MqttMessage("cpdb".getBytes()));
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

    private boolean canbeStopValue(float[] values){
        if(values.length!=3) return false;
        return values[0] * values[0] + values[1] * values[1] + values[2] * values[2] < stopQuota * stopQuota;
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
                        boolean accStop=canbeStopValue(values);
                        if(accStop) stopValue(values);

                        StringBuilder stringBuilder=new StringBuilder();
                        stringBuilder.append("Linear Acceleration Phone:\n").
                                append("x: ").append(values[0]).append("\n").
                                append("y: ").append(values[1]).append("\n").append("z: ").append(values[2]);
                        mTextView_linearAcceleration_Phone.setText(stringBuilder.toString());
                        linear_acceleration = values;
                        List<Float> vector_local_acceleration = new ArrayList<>();
                        for (float a : linear_acceleration) {
                            vector_local_acceleration.add(a);
                        }
                        Mat linear_acceleration_world = new Mat();
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

                        stringBuilder=new StringBuilder();
                        stringBuilder.append("Linear Acceleration World:\n").
                                append("x: ").append(linear_accleration_array[0]).append("\n").
                                append("y: ").append(linear_accleration_array[1]).append("\n").
                                append("z: ").append(linear_accleration_array[2]);

                        mTextView_linearAccleration_World.setText(stringBuilder.toString());

                        mSpeed.x+=linear_accleration_array[0]* DELTA_TIME;
                        mSpeed.y+=linear_accleration_array[1]* DELTA_TIME;
                        mSpeed.z+=linear_accleration_array[2]* DELTA_TIME;
                        double[] speed={mSpeed.x,mSpeed.y,mSpeed.z};
                        if(canbeStopValue(speed)||accStop) {
                            stopValue(speed);
                            mSpeed.x=0;
                            mSpeed.y=0;
                            mSpeed.z=0;
                        } // 如果速度太小，或者加速度为0，速度截止

                        stringBuilder=new StringBuilder();
                        stringBuilder.append("Speed:\n").
                                append("x: ").append(speed[0]).append("\n").
                                append("y: ").append(speed[1]).append("\n").
                                append("z: ").append(speed[2]);
                        mTextView_speed.setText(stringBuilder.toString());

                        mPosWorld.x+=(speed[0]* DELTA_TIME);
                        mPosWorld.y+=(speed[1]* DELTA_TIME);
                        mPosWorld.z+=(speed[2]* DELTA_TIME);

                        stringBuilder=new StringBuilder();
                        stringBuilder.append("Position: \n").
                                append("x: ").append(mPosWorld.x).append("\n").
                                append("y: ").append(mPosWorld.y).append("\n").
                                append("z: ").append(mPosWorld.z);

                        mTextView_location.setText(stringBuilder.toString());
                    }
                    break;
                    case ORI_SENSOR_CHANGE: {
                        if (rotationMatrix_phone2world == null)
                            rotationMatrix_phone2world = new Mat(3, 3, CvType.CV_32FC1);
                        float[] values = (float[]) msg.obj;
                        if (isStartComputingPoint) {
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
                            tempMat.put(0, 1, c3 * s1 * s2 - c1 * c3);
                            tempMat.put(0, 2, c2 * s1);
                            tempMat.put(1, 0, c2 * s3);
                            tempMat.put(1, 1, c2 * s3);
                            tempMat.put(1, 2, -s2);
                            tempMat.put(2, 0, c1 * s2 * s3 - c3 * s1);
                            tempMat.put(2, 1, c1 * c3 * s2 + s1 * s3);
                            tempMat.put(2, 2, c1 * c2);
                            rotationMatrix_phone2world = tempMat.t(); // 会不会出错？是逆矩阵还是？这里直接优化成transpose(正交矩阵的逆等于转置)
                        }
                        String string = "ORIENTATION:\nYaw: " + values[0] + "\n" + "Pitch: " + values[1] + "\n" + "Roll: " + values[2] + "\n";
                        mTextView_orientation.setText(string);
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

package com.windyice.qwerty;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by 32699 on 2017/9/17.
 */

public class MqttBaseOperation {
    private String HOST;
    private String clientId;
    private MqttClient mqttClient;
    private MqttConnectOptions mqttConnectOptions;
    private Handler handler;
    private static String TAG="WindyIce_MqttBase";

    public MqttBaseOperation(String _HOST, String _clientId){
        HOST=_HOST;
        clientId=_clientId;
        try {
            mqttClient = new MqttClient(HOST, clientId, new MemoryPersistence());
        }
        catch (Exception e){
            Log.i(TAG,e.getMessage());
            Log.i(TAG,Utils.getStackTrackString(e));
        }
        mqttConnectOptions=new MqttConnectOptions();
    }

    public void setCallback(MqttCallback mqttCallback){
        mqttClient.setCallback(mqttCallback);
    }

    public void setHandler(Handler handler){
        this.handler=handler;
    }

    public void connect(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mqttClient.connect(mqttConnectOptions);
                    Message message=new Message();
                    message.what=2;
                    handler.sendMessage(message);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    Message message=new Message();
                    message.what=3;
                    handler.sendMessage(message);
                }
            }
        }).start();
    }

    public void connect(boolean withoutHandler){
        if(withoutHandler){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mqttClient.connect(mqttConnectOptions);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
        }
        else  connect();
    }

    public void disconnect()throws MqttException{
        mqttClient.disconnect();
    }

    public void startReconnect(long reconnectRate) {
        //final long reconnectRate=1*3000;
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if(!mqttClient.isConnected()){
                    connect();
                }
            }
        },0,reconnectRate, TimeUnit.MILLISECONDS);
    }

    public void startReconnect(long reconnectRate,boolean withoutHandler){
        if(withoutHandler){
            ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    if(!mqttClient.isConnected()){
                        connect(true);
                    }
                }
            },0,reconnectRate, TimeUnit.MILLISECONDS);
        }
        else startReconnect(reconnectRate);
    }

    public void Setting(boolean willCleanSession,int connectionTimeout,int keepAliveInterval){
        try{
            mqttConnectOptions.setCleanSession(willCleanSession);
            mqttConnectOptions.setConnectionTimeout(connectionTimeout);
            mqttConnectOptions.setKeepAliveInterval(keepAliveInterval);

        }
        catch (Exception e){
            Log.i("WindyIce_MqttBase","mqtt options setting failed");
            Log.i("WindyIce_MqttBase",e.getMessage());
            Log.i("WindyIce_MqttBase",Utils.getStackTrackString(e));
        }
    }

    public Handler getHandler(){
        return handler;
    }

    public void subscribe(String a){
        try {
            mqttClient.subscribe(a);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void subscribe(List<String> a){
        try{
            for(String b:a){
                mqttClient.subscribe(b);
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void publish(String topic,MqttMessage message){
        try{
            mqttClient.publish(topic,message);
        }
        catch (Exception e){
            if(!e.getMessage().equals("Too many publishes in progress")){
                Log.i(TAG,e.getMessage());
                Log.i(TAG,Utils.getStackTrackString(e));
            }
        }
    }
}

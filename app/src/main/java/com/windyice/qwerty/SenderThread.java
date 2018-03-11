package com.windyice.qwerty;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera.Size;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.util.Log;

/**
 * Created by WindyIce on 2018/2/15.
 */

public class SenderThread implements Runnable {

    private static Socket socket;
    private static ByteArrayOutputStream byteArrayOutputStream;
    private static byte byteBuffer[]=new byte[1024];
    public static Size size;



    // 向UI线程发送消息
    private Handler handler;

    // 接受UI线程信息
    public MyHandler recHandler;

    BufferedReader bufferedReader=null;
    static OutputStream outputStream=null;

    public SenderThread(Handler handler){
        this.handler=handler;
    }

    @Override
    public void run() {
        Looper.prepare();
        // 接受UI信息
        recHandler=new MyHandler();
        Looper.loop();
    }

    public static class MyHandler extends Handler{
        private String targetIP;
        private int port;

        private void setTargetIP(){
            targetIP="127.0.0.1";
            port=3000;
        }
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void handleMessage(Message msg) {
            setTargetIP();
            if(msg.what==0x111){
                try{
                    socket=new Socket(targetIP,port);
                    outputStream=socket.getOutputStream();
                    YuvImage image=(YuvImage) msg.obj;
                    if(socket.isOutputShutdown()){
                        socket.getKeepAlive();
                    }
                    else{
                        outputStream=socket.getOutputStream();
                        byteArrayOutputStream=new ByteArrayOutputStream();
                        image.compressToJpeg(new Rect(0,0,size.width,size.height),80,outputStream);
                        ByteArrayInputStream inputStream=new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                        int amount;
                        while((amount=inputStream.read(byteBuffer))!=-1){
                            outputStream.write(byteBuffer,0,amount);
                        }
                        outputStream.write("\n".getBytes());
                        byteArrayOutputStream.flush();
                        byteArrayOutputStream.close();
                        outputStream.flush();
                        outputStream.close();
                        socket.close();
                    }
                }
                catch (UnsupportedEncodingException e){
                    e.printStackTrace();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }
}

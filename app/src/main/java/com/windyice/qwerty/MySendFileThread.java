package com.windyice.qwerty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLEncoder;

/**
 * Created by 32699 on 2018/2/26.
 */

public class MySendFileThread extends Thread {
    private String username;
    private String ipname;
    private int port;
    private byte byteBuffer[]=new byte[1024];
    private OutputStream outsocket;
    private ByteArrayOutputStream myoutputstream;

    public MySendFileThread(ByteArrayOutputStream myoutputstream,String username,String ipname,int port){
        this.myoutputstream=myoutputstream;
        this.username=username;
        this.ipname=ipname;
        this.port=port;
        try {
            myoutputstream.close();
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try{
            // 将图像通过socket发送出去
            Socket tempSocket=new Socket(ipname,port);
            outsocket=tempSocket.getOutputStream();
            // 写入数据头
            String msg= URLEncoder.encode("PHONEVIDEO|"+username+"|","utf-8");
            byte[] buffer=msg.getBytes();
            outsocket.write(buffer);

            ByteArrayInputStream inputStream=new ByteArrayInputStream(myoutputstream.toByteArray());
            int amount;
            while((amount=inputStream.read(byteBuffer))!=-1){
                outsocket.write(byteBuffer,0,amount);
            }
            myoutputstream.flush();
            myoutputstream.close();
            tempSocket.close();
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
}

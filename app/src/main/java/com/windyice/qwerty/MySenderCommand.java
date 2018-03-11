package com.windyice.qwerty;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Created by 32699 on 2018/2/26.
 */

public class MySenderCommand extends Thread {
    private String command;
    private String serverUrl;
    private int serverPort;
    public MySenderCommand(String command,String serverUrl,int serverPort){
        this.command=command;
        this.serverUrl=serverUrl;
        this.serverPort=serverPort;
    }

    @Override
    public void run() {
        try{
            Socket socket=new Socket(serverUrl,serverPort);
            PrintWriter out=new PrintWriter(socket.getOutputStream());
            out.println(command);
            out.flush();
        }
        catch (UnknownHostException e){
            e.printStackTrace();
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
}

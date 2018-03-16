package com.windyice.qwerty;

/**
 * Created by 32699 on 2018/3/13.
 */

public class Utils {
    public static String getStackTrackString(Exception e){
        StringBuilder stringBuilder=new StringBuilder();
        for(int i=0;i<e.getStackTrace().length;i++){
            stringBuilder.append(e.getStackTrace()[i]).append("\n");
        }
        return stringBuilder.toString();
    }

    public static String globalCalibrateInformation="";

    public static String hostIP="23.97.65.241";
    public static int hostPort=3389;


}

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

    public static String hostIP="39.108.118.166";
    public static int hostPort=1883;
    public static String clientId="WindyIce_android";

}

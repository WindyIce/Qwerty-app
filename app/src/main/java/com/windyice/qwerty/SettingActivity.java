package com.windyice.qwerty;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

public class SettingActivity extends AppCompatActivity {

    private EditText editText1;
    private EditText editText2;
    private Button button1;
    private Button button2;
    private CheckBox checkBox1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        editText1=(EditText)findViewById(R.id.setting_activity_edittext1);
        editText2=(EditText)findViewById(R.id.setting_activity_edittext2);
        SharedPreferences sharedPreferences=getSharedPreferences("data",MODE_PRIVATE);
        String aIP=sharedPreferences.getString("IP",Utils.hostIP);
        int aPort=sharedPreferences.getInt("port",Utils.hostPort);;
        editText1.setText(aIP);
        editText2.setText(""+aPort);
        button1=(Button)findViewById(R.id.setting_activity_button1);
        button2=(Button)findViewById(R.id.setting_activity_button2);
        checkBox1=(CheckBox)findViewById(R.id.setting_activity_checkbox1);
        checkBox1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b){
                    Utils.testModeOn=true;
                }else{
                    Utils.testModeOn=false;
                }
            }
        });

        // IP地址确认
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String IP=editText1.getText().toString();
                String[] eachIP=IP.split("\\.");
                if(eachIP.length!=4){
                    makeToast("IP地址位数不是四位！",Toast.LENGTH_LONG);
                    return;
                }
                for (String anEachIP : eachIP) {
                    try {
                        int eachInt = Integer.parseInt(anEachIP);
                        if (eachInt > 255 || eachInt < 0) {
                            makeToast("IP地址范围超出限制！", Toast.LENGTH_LONG);
                            return;
                        }
                    } catch (Exception e) {
                        makeToast("含有非法字符输入!", Toast.LENGTH_LONG);
                    }
                }
                Utils.hostIP=IP;
                SharedPreferences.Editor editor=getSharedPreferences("data",MODE_PRIVATE).edit();
                editor.putString("IP",Utils.hostIP);
                editor.apply();
            }
        });

        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    int port=Integer.parseInt(editText2.getText().toString());
                    if(port<0||port>65535){
                        makeToast("端口号超出限制！",Toast.LENGTH_LONG);
                        return;
                    }
                    Utils.hostPort=port;
                    SharedPreferences.Editor editor=getSharedPreferences("data",MODE_PRIVATE).edit();
                    editor.putInt("port",Utils.hostPort);
                    editor.apply();
                }
                catch (Exception e){
                    makeToast("端口号含非法字符!",Toast.LENGTH_LONG);
                }
            }
        });
    }

    private void makeToast(String text,int toastCode){
        Toast.makeText(getApplicationContext(),text,toastCode).show();
    }
}

package com.windyice.qwerty;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class InfoActivity extends AppCompatActivity {

    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        mTextView=(TextView)findViewById(R.id.info_activity_textview);

    }

    @Override
    protected void onResume() {
        if(!Utils.globalCalibrateInformation.equals("")&&(!(Utils.globalCalibrateInformation==null)))
            mTextView.setText(Utils.globalCalibrateInformation);
        else
            mTextView.setText("还没有信息");
        super.onResume();
    }
}

package com.runjigroup.robot.speechclient;

import android.app.Activity;
import android.content.Intent;

import android.os.Bundle;

import android.view.View;
import android.view.View.OnClickListener;

import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;



public class LoginActivity extends Activity {
    private EditText etUserName;
    private EditText etUserPassword;
    private Toast mToast;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);//???

        etUserName = (EditText) findViewById(R.id.user_name);

        etUserPassword = (EditText) findViewById(R.id.user_password);

        btnLogin = (Button) findViewById(R.id.user_login);

        btnLogin.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.user_login:
                        returnLoginInfo();
                        break;
                    default:
                        break;
                }
            }
        });

        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
    }

    private void returnLoginInfo() {
        String user_name = etUserName.getText().toString();
        Intent intent = getIntent();
        Bundle data = new Bundle();
        data.putString("user_name", user_name);
        intent.putExtras(data);
        // 设置该SelectCityActivity结果码，并设置结束之后退回的Activity
        setResult(CommonMsg.RES_CODE_GET_USER_INFO, intent);
        // 结束SelectCityActivity
        finish();
    }

    @Override
    public void finish() {
        super.finish();
    }
}


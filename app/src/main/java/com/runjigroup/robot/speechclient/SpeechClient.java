package com.runjigroup.robot.speechclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.MotionEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class SpeechClient extends Activity {
    private int mUserId = 0;
    private int mSenId = 0;
    private int mSenFin = 0;
    private int mSenTotal = 0;
    private TextView tvSenToRead;
    private TextView tvSenRecog;
    private TextView tvStatistics;
    private Toast mToast;
    private Button btnRecord;
    private Button btnLogout;
    private int mState = CommonMsg.INIT;
    private String mSenToRead;
    private static String TAG = SpeechClient.class.getSimpleName();
    private Handler mRecogHandler = null;
    private Handler mRecordHandler = null;
    private Handler mSenHandler = null;

    private final Handler mHandler = new Handler(){
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case CommonMsg.RECV_SEN: {
                    String sen_to_read = msg.obj.toString();
                    assert sen_to_read != null;
                    assert mUserId == msg.arg1;
                    mSenId = msg.arg2;
                    if (!sen_to_read.isEmpty()){
                        mSenToRead = sen_to_read;
                        tvSenToRead.setText(sen_to_read);
                        btnRecord.setEnabled(true);
                    }
                    break;
                }
                case CommonMsg.REC_FIN: {
                    btnRecord.setEnabled(false);//录音结束，禁用按钮
                    Message req_voice = mRecogHandler.obtainMessage(CommonMsg.RECOG_START);
                    mRecogHandler.sendMessage(req_voice);
                    mState = CommonMsg.RECOG_START;
                    break;
                }
                case CommonMsg.RECOG_FIN: {
                    String sen_recog = msg.obj.toString();
                    assert sen_recog != null;
                    tvSenRecog.setText(sen_recog);
                    //==比较是否为相同reference,不能用做字符串的比较
                    if (sen_recog.equals(mSenToRead))
                    {
                        uploadWav();
                    }else{
                        //录音失败，重新录音
                        if (sen_recog.isEmpty()) {
                            //用户录音时没说话或音量太小
                            showMsg("没听清，请再说一遍");
                            btnRecord.setEnabled(true);
                        }else {
                            String comb = sen_recog + CommonMsg.TWO_SEN_SEP + mSenToRead;
                            Message req_pinyin = mSenHandler.obtainMessage(CommonMsg.REQUEST_PINYIN, comb);
                            mSenHandler.sendMessage(req_pinyin);
                        }
                    }
                    break;
                }
                case CommonMsg.RECV_UPLOAD_FIN_SUCC: {
                    tvStatistics.setText(++mSenFin + "/" + mSenTotal);
                    Message req_sen = mSenHandler.obtainMessage(CommonMsg.REQUEST_SEN, mUserId, 0);//sentence id == 0 placeholde
                    mSenHandler.sendMessage(req_sen);
                    mState = CommonMsg.REQUEST_SEN;
                    break;
                }
                case CommonMsg.RECV_UPLOAD_FIN_FAIL: {
                    uploadWav();
                    break;
                }
                case CommonMsg.RECV_STATISTICS_SUCC: {
                    int fin = msg.arg1;
                    int total = msg.arg2;
                    mSenFin = fin;
                    mSenTotal = total;
                    tvStatistics.setText(fin + "/" + total);
                    break;
                }
                case CommonMsg.LOGIN_FIN: {
                    int uid = msg.arg1;

                    mUserId = uid;
                    //记录登陆id
                    SharedPreferences conf = getSharedPreferences("config", MODE_PRIVATE);
                    SharedPreferences.Editor ed = conf.edit();
                    ed.putInt("user_id", uid);
                    ed.commit();
                    //显示当前进度, uid@arg1, palce_holder@arg2
                    Message req_stat = mSenHandler.obtainMessage(CommonMsg.REQUEST_STATISTICS, mUserId, 0);
                    mSenHandler.sendMessage(req_stat);
                    //开始获取待读语句
                    kickStart();

                    break;
                }
                case CommonMsg.PINYIN_FIN_SAME: {
                    uploadWav();
                    btnRecord.setEnabled(true);
                    break;
                }
                case CommonMsg.PINYIN_FIN_DIFF: {
                    btnRecord.setEnabled(true);
                    break;
                }
                case CommonMsg.SENTENCE_INITED: {
                    if (null == mSenHandler)
                        mSenHandler = ((Handler) msg.obj);

                    logIn();
                    break;
                }
                case CommonMsg.RECORD_INITED: {
                    mRecordHandler = ((Handler) msg.obj);
                    break;
                }
                case CommonMsg.RECOG_INITED: {
                    mRecogHandler = ((Handler) msg.obj);
                    break;
                }
                case CommonMsg.ALL_COMPLETE: {
                    showAlert("获取语句", CommonMsg.COMPLETE, CommonMsg.ALL_COMPLETE);
                    btnRecord.setEnabled(false);
                    mState = CommonMsg.ALL_COMPLETE;
                    break;
                }
                case CommonMsg.GET_SEN_ERR: {
                    showAlert("获取语句", (String)msg.obj, CommonMsg.GET_SEN_ERR);
                    break;
                }
                case CommonMsg.PUT_SEN_ERR: {
                    showAlert("上传语句", (String) msg.obj, CommonMsg.PUT_SEN_ERR);
                    break;
                }
                case CommonMsg.RECORD_ERR: {
                    showAlert("录音", (String) msg.obj, CommonMsg.RECORD_ERR);
                    break;
                }
                case CommonMsg.LOGIN_ERR: {
                    showAlert("登陆", (String) msg.obj, CommonMsg.LOGIN_ERR);
                    break;
                }
                case CommonMsg.PINYIN_ERR: {
                    showAlert("获取拼音", (String) msg.obj, CommonMsg.PINYIN_ERR);
                    break;
                }
                case CommonMsg.RECV_STATISTICS_FAIL: {
                    showAlert("获取进度", (String) msg.obj, CommonMsg.RECV_STATISTICS_FAIL);
                    break;
                }
                default: {
                    assert false;
                    break;
                }
            }
        };
    };
    private void uploadWav(){
        Message req_upload = mSenHandler.obtainMessage(CommonMsg.REQUEST_UPLOAD, mUserId, mSenId);
        mSenHandler.sendMessage(req_upload);
        mState = CommonMsg.REQUEST_UPLOAD;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_voice);

        tvSenToRead = (TextView) findViewById(R.id.sen_to_read);
        tvSenRecog = (TextView) findViewById(R.id.sen_recog);
        tvStatistics = (TextView) findViewById(R.id.statistics);
        btnRecord = (Button) findViewById(R.id.record);
        btnLogout = (Button) findViewById(R.id.user_logout);

        btnRecord.setEnabled(false);
        btnRecord.setOnTouchListener(new OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (v.getId()) {
                    case R.id.record:
                        if(event.getAction() == MotionEvent.ACTION_DOWN){
                            Message msg = mRecordHandler.obtainMessage(CommonMsg.REC_START);
                            mRecordHandler.sendMessage(msg);
                            showMsg("开始录音");
                        }else if(event.getAction() == MotionEvent.ACTION_UP){
                            Message msg = mRecordHandler.obtainMessage(CommonMsg.REC_STOP);
                            mRecordHandler.sendMessage(msg);
                            showMsg("结束录音");
                        }
                        break;
                    default:
                        break;
                }
                return true;//返回true表示该事件被消耗，不会再向后传播；不返回true会导致DOWN后UP永不出现
            }
        });
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.user_logout:
                        logOut();
                        break;
                    default:
                        break;
                }
            }
        });
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        SentenceThread senThread = new SentenceThread(this, mHandler);
        senThread.start();

        RecordThread recordThread = new RecordThread(this, mHandler);
        recordThread.start();

        RecogThread recogThread = new RecogThread(this, mHandler);
        recogThread.start();
    }


    protected void onResume() {
        super.onResume();
    }

    protected void onPause() {
        super.onPause();
    }

    private void showAlert(String mod_name, String err_msg, final int err_num){
        new AlertDialog.Builder(this)
                .setTitle(mod_name)
                .setMessage(err_msg)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (err_num) {
                            case CommonMsg.LOGIN_ERR: {
                                //重新开始登陆
                                getUserInfo();
                                break;
                            }
                            default:{
                                break;
                            }
                        }
                    }
                })
                .show();
    }

    private void showMsg(String msg){
        mToast.setText(msg);
        mToast.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CommonMsg.REQ_CODE_GET_USER_INFO && resultCode == CommonMsg.RES_CODE_GET_USER_INFO) {
            // 取出Intent里的数据
            String user_name = data.getStringExtra("user_name");
            if (user_name == null || user_name.equals("")) {
                getUserInfo();
            }else{
                Message req_login = mSenHandler.obtainMessage(CommonMsg.REQUEST_LOGIN, user_name);
                mSenHandler.sendMessage(req_login);
            }
        }

    }
    private void getUserInfo(){
        Intent login = new Intent(this, LoginActivity.class);
        // 启动指定Activity并等待返回的结果，其中23是请求码，用于标识该请求
        startActivityForResult(login, CommonMsg.REQ_CODE_GET_USER_INFO);
    }
    private void logOut() {
        SharedPreferences conf = getSharedPreferences("config", MODE_PRIVATE);
        SharedPreferences.Editor ed = conf.edit();
        ed.clear();
        ed.commit();
        getUserInfo();
    }
    /*
    private void logIn() {
        String user_name = null;
        int uid = 0;
        SharedPreferences conf = this.getSharedPreferences("config", MODE_PRIVATE);

        uid = conf.getInt("user_id", 0);
        if (uid == 0) {
            getUserInfo();
        }else{
            mUserId = uid; //跳过登陆
            kickStart();
        }
    }
    */
    private void logIn() {
        //因为不确定是外网还是内网登陆，所以必须要通过登陆来确定合法的URL，不能跳过登陆
        getUserInfo();
    }

    //开始第一次获取待读语句
    private void kickStart(){
        Message req_sen = mSenHandler.obtainMessage(CommonMsg.REQUEST_SEN, mUserId, 0);//sentence id == 0 placeholde
        mSenHandler.sendMessage(req_sen);
        mState = CommonMsg.REQUEST_SEN;
    }

};

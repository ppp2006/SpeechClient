package com.runjigroup.robot.speechclient;

/**
 * Created by Administrator on 15-7-10.
 */
import java.lang.Thread;
import java.io.FileOutputStream;
import java.io.IOException;

import android.util.Log;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import android.os.Bundle;
import android.content.Context;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;

public class RecogThread extends Thread{
    private Context mUiCtx;
    private Handler mUiHandler;
    private Handler mHandler = null;
    private SpeechRecognizer mIat;
    private static String TAG = RecogThread.class.getSimpleName();
    private InitListener mInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败，错误码：" + code);
            }
        }
    };

    private RecognizerListener mRecogListener = new RecognizerListener() {
        @Override
        public void onBeginOfSpeech() {
            showTip("开始说话");
        }
        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            // 返回 空串 表示 用户 没说话
            Message reply = mUiHandler.obtainMessage(CommonMsg.RECOG_FIN, "");
            mUiHandler.sendMessage(reply);
        }
        @Override
        public void onEndOfSpeech() {
            showTip("结束说话");
        }
        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            mIat.stopListening();
            showTip("停止听写");

            String sen_recog = results.getResultString();
            assert sen_recog != null;
            if (!sen_recog.isEmpty()) {//为什么每次识别都会收到一个空串""  ???
                //无论识别对错都上报给UI线程，如果错了，UI线程会重新下发录音识别命令
                Message reply = mUiHandler.obtainMessage(CommonMsg.RECOG_FIN, sen_recog);
                mUiHandler.sendMessage(reply);
            }
            Log.d(TAG, sen_recog);
            if (isLast) {
                //TODO
            }
        }
        @Override
        public void onVolumeChanged(int volume) {
            //showTip("当前正在说话，音量大小：" + volume);
        }
        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }
    };

    public RecogThread(Context ctx, Handler handler){
        mUiCtx = ctx;
        mUiHandler = handler;
        //因为不是在main线程初始化，所以要Force Login
        SpeechUtility.createUtility(mUiCtx, "appid=" + mUiCtx.getString(R.string.app_id) + "," + SpeechConstant.FORCE_LOGIN + "=true");
        mIat = SpeechRecognizer.createRecognizer(mUiCtx, mInitListener);
        setIatParam();
    }

    public Handler getHandler(){
        return mHandler;
    }

    public void run() {
        Looper.prepare();//set looper and queue
        //set callback
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                switch (msg.what) {
                    case CommonMsg.RECOG_START: {
                        int ret = 0; // 函数调用返回值
                        ret = mIat.startListening(mRecogListener);
                        if (ret != ErrorCode.SUCCESS) {
                            showTip("听写失败,错误码：" + ret);
                        } else {
                            showTip("请开始说话");
                        }

                        break;
                    }
                    default:{
                        assert false;
                        break;
                    }
                }
            }
        };
        //notify main
        Message note = mUiHandler.obtainMessage(CommonMsg.RECOG_INITED, mHandler);
        mUiHandler.sendMessage(note);
        //start
        Looper.loop();
    }

    public void setIatParam() {
        // 清空参数
        mIat.setParameter(SpeechConstant.PARAMS, null);

        // 设置听写引擎
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        // 设置返回结果格式
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "plain");

        // 设置语言
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        // 设置语言区域
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");

        // 设置语音输入源：
        // 录音机的录音方式，默认为MIC(MediaRecorder.AudioSource.MIC)
        // 如果需要外部传送录音，设置为-1，通过WriteAudio接口送入音频
        // 读外部音频文件（仅听写支持），设置为-2，需要设置传入识别音频源路径 ASR_SOURCE_PATH
        mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-2");



        // 识别采样率：可选范围：16000，8000（单位Hz），默认值：16000
        mIat.setParameter(SpeechConstant.SAMPLE_RATE, "16000");

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIat.setParameter(SpeechConstant.VAD_BOS, "4000");

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIat.setParameter(SpeechConstant.VAD_EOS,  "1000");

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, "0");

        // 外部传入识别音频源。该参数传入的音频路径，仅在 AUDIO_SOURCE 设置为-2时有效
        mIat.setParameter(SpeechConstant.ASR_SOURCE_PATH, CommonMsg.SPEECH_PATH+".wav");

        // 设置听写结果是否结果动态修正，为“1”则在听写过程中动态递增地返回结果，否则只在听写结束之后返回最终结果
        // 注：该参数暂时只对在线听写有效
        mIat.setParameter(SpeechConstant.ASR_DWA, "0");
    }

    private void showTip(final String str) {
        Log.d(TAG, str);
    }
}

package com.runjigroup.robot.speechclient;

/**
 * Created by Administrator on 15-7-13.
 */

import android.os.Environment;

public class CommonMsg {
    public static final int INIT = 0;
    public static final int REQUEST_SEN = 1;
    public static final int RECV_SEN = 2;
    public static final int RECOG_START = 11;
    public static final int RECOG_FIN = 12;
    public static final int REQUEST_UPLOAD = 21;
    public static final int RECV_UPLOAD_FIN_SUCC = 22;
    public static final int RECV_UPLOAD_FIN_FAIL = 23;
    public static final int REQUEST_LOGIN = 31;
    public static final int LOGIN_FIN = 32;
    public static final int REQUEST_PINYIN = 41;
    public static final int PINYIN_FIN_SAME = 42;
    public static final int PINYIN_FIN_DIFF = 43;

    public static final int UNIT_REC_START = 51;
    public static final int UNIT_REC_FIN = 52;
    public static final int REC_START = 57;
    public static final int REC_STOP = 58;
    public static final int REC_FIN = 59;

    public static final int REQ_CODE_GET_USER_INFO = 61;

    public static final int RES_CODE_GET_USER_INFO = 62;

    public static final int REQUEST_STATISTICS = 70;
    public static final int RECV_STATISTICS_SUCC = 71;
    public static final int RECV_STATISTICS_FAIL = 72;

    public static final int SENTENCE_INITED = 90;
    public static final int RECORD_INITED = 91;
    public static final int RECOG_INITED = 92;

    public static final String SPEECH_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/RunJi/speech";
    public static final String TWO_SEN_SEP = "_";
    public static final String COMPLETE = "【恭喜，您已读完全部语句！】";
    public static final int GET_SEN_ERR = 100;
    public static final int PUT_SEN_ERR = 101;
    public static final int RECORD_ERR = 102;
    public static final int LOGIN_ERR = 103;
    public static final int PINYIN_ERR = 104;

    public static final int ALL_COMPLETE = 404;
}

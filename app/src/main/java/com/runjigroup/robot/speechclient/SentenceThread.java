package com.runjigroup.robot.speechclient;

/**
 * Created by Administrator on 15-7-13.
 */
import java.lang.Thread;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.Map;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.net.HttpURLConnection;
import java.net.URL;

import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;


public class SentenceThread extends Thread {
    private Context mUiCtx = null;
    private Handler mUiHandler = null;
    private Handler mHandler  = null;
    private static String TAG = SentenceThread.class.getSimpleName();
    private int mCurSenId = 0;
    private int mFinNum = 0;
    private int mTotalNum = 0;
    private static int mTimeOut= 5000;//5s超时
    private static String mCurSenTxt = null;
    private static String mServerURL = null;

    private boolean getSentence(int uid) {
        boolean success = false;
        try {
            String cloudURL = mServerURL + "index.py?service=get_sen&uid=" + uid;
            HttpURLConnection conn = (HttpURLConnection) new URL(cloudURL).openConnection();
            conn.setConnectTimeout(mTimeOut);//超时机制，避免死等
            conn.setReadTimeout(mTimeOut);
            String result = extractResponse(conn);

            Map<String, String> map = new HashMap<String, String>();
            parseParameters(map, result);

            mCurSenId = Integer.valueOf(map.get("sen_id"));
            if (mCurSenId > 0) {
                mCurSenTxt = (String) map.get("sen_txt");
                success = true;
            } else if (mCurSenId == 0){
                //mCurSenTxt = CommonMsg.COMPLETE;//
                mCurSenTxt = "";//
                Message reply = mUiHandler.obtainMessage(CommonMsg.ALL_COMPLETE);
                mUiHandler.sendMessage(reply);
                success = true;
            }else{
                success = false;//不可能走到这里
            }
        }catch (Exception e){
            mCurSenId = 0;
            mCurSenTxt = e.toString();
            success = false;
        }
        return success;
    }

    private int loginCloud(String name) {
        int uid = -1;//-1表示连不上服务器，0表示查无此人
        int retryCnt = 0;
        int maxRetryCnt = 2;
        Exception lastException = null;
        for (retryCnt = 0; retryCnt < maxRetryCnt; retryCnt++) {
            if (retryCnt == 0) { //外网登陆
                mServerURL = "http://cleverestrobot.com/";
            }else if (retryCnt == 1) { //内网登陆
                if (uid > 0) {
                    break;//外网登陆成功，不必尝试内网
                }else{
                    mServerURL = "http://192.168.5.144/";
                }
            }
            try {
                String encoded_name = URLEncoder.encode(name, "UTF-8");
                String cloudURL = mServerURL + "index.py?service=login&user_name=" + encoded_name;//必须URL编码，否则mod_python会将汉字解析成问号
                HttpURLConnection conn = (HttpURLConnection) new URL(cloudURL).openConnection();
                conn.setConnectTimeout(mTimeOut);//超时机制，避免死等
                conn.setReadTimeout(mTimeOut);
                String result = extractResponse(conn);

                uid = Integer.valueOf(result);

            } catch (Exception e) {
                lastException = e;
            }
        }
        if (uid == -1 && retryCnt == maxRetryCnt) {
            //外网内网都连不上服务器，记录异常信息到mCurSenTxt
            mCurSenTxt = lastException.toString();
        }else if (uid == 0) {
            mCurSenTxt = "该用户不存在，请联系管理员添加！";
        }
        return uid;
    }

    private boolean putSentence(int uid, int sid) {
        boolean success = false;
        try {
            String wavPath = CommonMsg.SPEECH_PATH + ".wav";
            File wavFile = new File(wavPath);
            String uploadUrl = mServerURL + "index.py?service=put_sen&uid=" + uid + "&sid=" + sid;
            HttpURLConnection conn = (HttpURLConnection) new URL(uploadUrl).openConnection();

            // add request header
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "audio/wav");
            conn.setRequestProperty("Content-Length", String.valueOf(wavFile.length()));
            conn.setConnectTimeout(mTimeOut);//超时机制，避免死等
            conn.setReadTimeout(mTimeOut);

            conn.setDoInput(true);
            conn.setDoOutput(true);

            // send request
            DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
            wr.write(loadFile(wavFile));
            wr.flush();
            wr.close();

            String result = extractResponse(conn);
            if (result.equals("上传成功！") )
                success = true;
            else
                success = false;
        }catch(Exception e){
            Message err = mUiHandler.obtainMessage(CommonMsg.PUT_SEN_ERR, e.toString());
            mUiHandler.sendMessage(err);
        }
        return success;
    }
    private static byte[] loadFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);

        long length = file.length();
        byte[] bytes = new byte[(int) length];

        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
                && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += numRead;
        }

        if (offset < bytes.length) {
            is.close();
            throw new IOException("Could not completely read file " + file.getName());
        }

        is.close();
        return bytes;
    }

    private static String extractResponse(HttpURLConnection conn) throws Exception {
        if (conn.getResponseCode() != 200) {
            // request error
            return "";
        }
        InputStream is = conn.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuffer response = new StringBuffer();
        while ((line = rd.readLine()) != null) {
            response.append(line);
            //response.append('\r'); //our response is one line only
        }
        rd.close();
        return response.toString();
    }
    public static void parseParameters(Map map, String data)
            throws UnsupportedEncodingException
    {
        if ((data == null) || (data.length() <= 0))
        {
            System.out.println("empty string!!");
            return;
        }
        String tmp[] = data.trim().split("&");
        for (int i = 0; i < tmp.length; i++){
            String kv[] = tmp[i].split("=");
            //System.out.println("key = "+kv[0]+", value = "+kv[1]);
            map.put(kv[0], kv[1]);
        }
    }

    public SentenceThread(Context ctx, Handler handler){
        mUiCtx = ctx;
        mUiHandler = handler;
    }

    public void run() {
        Looper.prepare();//set looper and queue
        //set callback
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                switch (msg.what) {
                    case CommonMsg.REQUEST_LOGIN: {
                        String name = (String)msg.obj;
                        int uid = loginCloud(name);
                        if (uid > 0) {
                            Message reply = mUiHandler.obtainMessage(CommonMsg.LOGIN_FIN, uid, 0);
                            mUiHandler.sendMessage(reply);
                        }else{
                            Message err = mUiHandler.obtainMessage(CommonMsg.LOGIN_ERR, mCurSenTxt);
                            mUiHandler.sendMessage(err);
                        }
                        break;
                    }
                    case CommonMsg.REQUEST_SEN: {
                        boolean ret = false;
                        int uid = msg.arg1;
                        ret = getSentence(uid);
                        if (ret) {
                            Message reply = mUiHandler.obtainMessage(CommonMsg.RECV_SEN, uid, mCurSenId, mCurSenTxt);
                            mUiHandler.sendMessage(reply);
                        }else{
                            Message err = mUiHandler.obtainMessage(CommonMsg.GET_SEN_ERR, mCurSenTxt);
                            mUiHandler.sendMessage(err);
                        }
                        break;
                    }
                    case CommonMsg.REQUEST_PINYIN: {
                        String comb = msg.obj.toString();
                        String [] a = comb.split(CommonMsg.TWO_SEN_SEP);
                        String p0 = getPinyin(a[0]);
                        String p1 = getPinyin(a[1]);
                        if (!p0.isEmpty() && !p1.isEmpty() && p0.equals(p1)){
                            Message reply = mUiHandler.obtainMessage(CommonMsg.PINYIN_FIN_SAME);
                            mUiHandler.sendMessage(reply);
                        }else{
                            Message reply = mUiHandler.obtainMessage(CommonMsg.PINYIN_FIN_DIFF);
                            mUiHandler.sendMessage(reply);
                        }
                        break;
                    }
                    case CommonMsg.REQUEST_UPLOAD: {
                        boolean ret = false;
                        Message reply;
                        ret = putSentence(msg.arg1, msg.arg2);//uid@arg1, sid@arg2
                        if (ret) {
                             reply = mUiHandler.obtainMessage(CommonMsg.RECV_UPLOAD_FIN_SUCC);
                        }else{
                            reply = mUiHandler.obtainMessage(CommonMsg.RECV_UPLOAD_FIN_FAIL);
                        }
                        mUiHandler.sendMessage(reply);
                        break;
                    }
                    case CommonMsg.REQUEST_STATISTICS: {
                        boolean ret = false;
                        Message reply;
                        ret = getStatistics(msg.arg1);//uid@arg1, palce_holder@arg2
                        if (ret) {
                            reply = mUiHandler.obtainMessage(CommonMsg.RECV_STATISTICS_SUCC, mFinNum, mTotalNum);
                        }else{
                            reply = mUiHandler.obtainMessage(CommonMsg.RECV_STATISTICS_FAIL);
                        }
                        mUiHandler.sendMessage(reply);
                        break;
                    }
                }
            }
        };
        //notify main
        Message note = mUiHandler.obtainMessage(CommonMsg.SENTENCE_INITED, mHandler);
        mUiHandler.sendMessage(note);
        //start loop
        Looper.loop();
    }

    String getPinyin(String sen) {
        String res = "";
        try {
            String encoded_sen = URLEncoder.encode(sen, "UTF-8");
            String cloudURL = "http://string2pinyin.sinaapp.com/?str=" + encoded_sen + "&accent=0";//必须URL编码，否则mod_python会将汉字解析成问号
            HttpURLConnection conn = (HttpURLConnection) new URL(cloudURL).openConnection();
            conn.setConnectTimeout(mTimeOut);//超时机制，避免死等
            conn.setReadTimeout(mTimeOut);
            String result = extractResponse(conn);
            JSONObject resultObj = new JSONObject(result);
            res = resultObj.getString("pinyin");
            return res;
        } catch (Exception e) {
            Message err = mUiHandler.obtainMessage(CommonMsg.PINYIN_ERR, e.toString());
            mUiHandler.sendMessage(err);
        }
        return res;
    }
    boolean getStatistics(int uid) {
        boolean success = false;
        try {
            String cloudURL = mServerURL + "index.py?service=get_sta&uid=" + uid;
            HttpURLConnection conn = (HttpURLConnection) new URL(cloudURL).openConnection();
            conn.setConnectTimeout(mTimeOut);//超时机制，避免死等
            conn.setReadTimeout(mTimeOut);
            String result = extractResponse(conn);

            Map<String, String> map = new HashMap<String, String>();
            parseParameters(map, result);

            mFinNum = Integer.valueOf(map.get("fin"));
            mTotalNum = Integer.valueOf(map.get("total"));

            success = true;
        }catch (Exception e){
            success = false;
        }
        return success;
    }

    public Handler getHandler() {
        return mHandler;
    }
}

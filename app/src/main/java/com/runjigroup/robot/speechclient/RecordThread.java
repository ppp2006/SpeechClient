package com.runjigroup.robot.speechclient;

/**
 * Created by Administrator on 15-7-16.
 */
import java.io.File;
import java.lang.Thread;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import android.util.Log;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import android.os.Bundle;
import android.os.Environment;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class RecordThread extends Thread {
    private Context mUiCtx;
    private Handler mUiHandler;
    private Handler mHandler = null;
    private boolean bRecording = false;
    FileOutputStream mSpeechStream = null;
    private AudioRecord mAudioRecord;
    private int mUnitBufSize;
    private static String TAG = RecordThread.class.getSimpleName();

    public RecordThread(Context ctx, Handler handler){
        mUiCtx = ctx;
        mUiHandler = handler;

        mUnitBufSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mUnitBufSize);
    }

    public void run() {
        Looper.prepare(); //set looper and queue
        //set call back
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                switch (msg.what) {
                    case CommonMsg.REC_STOP: {
                        bRecording = false;
                        break;
                    }
                    case CommonMsg.REC_START: {
                        bRecording = true;
                        startRecord();
                        break;
                    }
                    case CommonMsg.UNIT_REC_FIN: {
                        if (bRecording) {
                            Message reply = mHandler.obtainMessage(CommonMsg.UNIT_REC_START);
                            mHandler.sendMessage(reply);
                        }else{
                            stopRecord();
                            Message reply = mUiHandler.obtainMessage(CommonMsg.REC_FIN);
                            mUiHandler.sendMessage(reply);
                        }
                        break;
                    }
                    case CommonMsg.UNIT_REC_START: {
                        recordUnit();
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
        Message note = mUiHandler.obtainMessage(CommonMsg.RECORD_INITED, mHandler);
        mUiHandler.sendMessage(note);
        //start loop
        Looper.loop();
    }

    private void startRecord() {
        mAudioRecord.startRecording();
        try {
            File subDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/RunJi/");
            if (!subDir.exists()) {
                subDir.mkdir();
            }
            mSpeechStream = new FileOutputStream(CommonMsg.SPEECH_PATH + ".raw");// 建立一个可存取字节的文件
        } catch (Exception e) {
            Message err = mUiHandler.obtainMessage(CommonMsg.RECORD_ERR, e.getMessage());
            mUiHandler.sendMessage(err);
        }
        //开始一小段录音
        recordUnit();
    }

    private void stopRecord() {
        mAudioRecord.stop();

        try {
            mSpeechStream.close();// 关闭写入流
            mSpeechStream = null;
        } catch (IOException e) {
            Message err = mUiHandler.obtainMessage(CommonMsg.RECORD_ERR, e.getMessage());
            mUiHandler.sendMessage(err);
        }
        convertToWav(CommonMsg.SPEECH_PATH+".raw", CommonMsg.SPEECH_PATH+".wav");
    }

    private void recordUnit() {
        // new一个byte数组用来存一些字节数据，大小为缓冲区大小
        byte[] audiodata = new byte[mUnitBufSize];
        int readsize = 0;
        readsize = mAudioRecord.read(audiodata, 0, mUnitBufSize);
        if (AudioRecord.ERROR_INVALID_OPERATION != readsize) {
            try {
                mSpeechStream.write(audiodata);
            } catch (IOException e) {
                Message err = mUiHandler.obtainMessage(CommonMsg.RECORD_ERR, e.getMessage());
                mUiHandler.sendMessage(err);
            }
        }
        Message reply = mHandler.obtainMessage(CommonMsg.UNIT_REC_FIN);
        mHandler.sendMessage(reply);
    }

    protected void finalize() {
        try {
            super.finalize();
            mAudioRecord.release();//释放资源
            mAudioRecord = null;
        }catch (Throwable e) {
            Message err = mUiHandler.obtainMessage(CommonMsg.RECORD_ERR, e.getMessage());
            mUiHandler.sendMessage(err);
        }
    }

    public Handler getHandler(){
        return mHandler;
    }

    private void convertToWav(String inFilename, String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        int sampleRate = 16000;
        int channels = 1;
        byte bitDepth = 16;
        long byteRate = bitDepth * sampleRate * channels / 8;
        byte[] data = new byte[mUnitBufSize];
        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    sampleRate, channels, bitDepth);
            while (in.read(data) != -1) {
                out.write(data);
            }
            in.close();
            out.close();
        } catch (Exception e) {
            Message err = mUiHandler.obtainMessage(CommonMsg.RECORD_ERR, e.getMessage());
            mUiHandler.sendMessage(err);
        }
    }

    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, int sampleRate, int channels, byte bitDepth)
            throws IOException {
        int byteRate = bitDepth * sampleRate * channels / 8;
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * bitDepth / 8); // block align
        header[33] = 0;
        header[34] = 16; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }

}

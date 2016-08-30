
package com.android.server;

import android.app.IDeviceManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.content.Context;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.File;

import android.os.FileObserver;
import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;

import android.util.Slog;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;
import android.util.Xml;
import com.android.internal.util.FastXmlSerializer;
import java.io.FileInputStream;
/**
 * {@hide}
 */
public class DeviceManagerService extends IDeviceManager.Stub {
    private static final String TAG = "DeviceManagerService";
    private Context mContext;
    private static final String SCREEN_ON_OFF = "screen";
    private static final String VIDEO = "video";

    private static final String TAG_SCREEN_ON="screen_on";
    private static final String TAG_SCREEN_OFF="screen_off";
    private static final String TAG_VIDEO_PAUSE="video_pause";
    private static final String TAG_VIDEO_START="video_start";
    private static final String TAG_VIDEO_STOP="video_stop";
    private static final String TAG_VIDEO_2K="video_2k";
    private String videoStartMinFreq="600000000";
    private String videoStopMinFreq="400000000";
    private String videoPauseMinFreq="400000000";
    private String screenOnMinFreq="400000000";
    private String screenOffMinFreq="200000000";
    private String video2kMinFreq="600000000";
    private String performanceMinFreq="800000000";
    private int curMinFreq=400000000;
    private static final int SET_MIN_FREQ=1;
    private static final int SET_GOVERNOR=2;
    private static final int PERFORMANCE=3;
    private static final int SIMPLE_ONDEMAND=4;
    private Handler handler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
                case SET_MIN_FREQ:
                    int minFreq=msg.arg1;
                    curMinFreq=minFreq;
                    Slog.d(TAG,"set ddr min freq="+curMinFreq);
                    File minFreqFile = new File("sys/bus/platform/drivers/rk3399-dmc-freq/dmc/devfreq/dmc/min_freq");
                    setDmcValue(minFreqFile,String.valueOf(minFreq));
                    break;
                case SET_GOVERNOR:;
                    File governorFile = new File("sys/bus/platform/drivers/rk3399-dmc-freq/dmc/devfreq/dmc/governor");
                    String value=msg.arg1==PERFORMANCE?"performance":"simple_ondemand";
                    Slog.d(TAG,"set governor="+value);
                    setDmcValue(governorFile,value);
                    break;
            }

        }
    };

    public DeviceManagerService(Context context) {
        mContext = context;
    }

    private VideoFileObserver mObserver;

    public void systemRunning() {
        //VideoFileObserver mObserver = new VideoFileObserver("/data/system");
        parseConfig();
        IntentFilter filter=new IntentFilter();
        filter.addAction(PowerManager.ACTION_PERFORMANCE_MODE_CHANGED);
        mContext.registerReceiver(receiver, filter);
       // mObserver = new VideoFileObserver("/data/video");
       // mObserver.startWatching();
        Slog.d(TAG,"videoStartMinFreq="+videoStartMinFreq+" videoStopMinFreq="+videoStopMinFreq+" videoPauseMinFreq="+videoPauseMinFreq+" screenOnMinFreq"+screenOnMinFreq+" screenOffMinFreq="+screenOffMinFreq+" video2kMinFreq="+video2kMinFreq);
    }

    private BroadcastReceiver receiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            if(PowerManager.ACTION_PERFORMANCE_MODE_CHANGED.equals(action)){
                int mode = intent.getIntExtra(PowerManager.EXTRA_PERFORMANCE_MODE, PowerManager.PERFORMANCE_MODE_NORMAL);
                Log.d(TAG,"action="+action+" mode="+mode);
                if(mode==PowerManager.PERFORMANCE_MODE_NORMAL){
                    //handler.removeMessages(SET_MIN_FREQ);
                    handler.sendMessage(handler.obtainMessage(SET_GOVERNOR,SIMPLE_ONDEMAND,0));
                }else{
                    //handler.removeMessages(SET_MIN_FREQ);
                    handler.sendMessage(handler.obtainMessage(SET_GOVERNOR,PERFORMANCE,0));
                }
            }
        }
    };
    /**
     * @hide
     */
    public String getValue(String name) {
        Log.d(TAG, "[getValue] name : " + name);
        return name;
    }

    /**
     * @hide
     */
    public int update(String name, String value, int status) {
        Slog.d(TAG, "----[update] name : " + name + ", value : " + value + ", status : " + status);
        if (name.equals(SCREEN_ON_OFF)) {
            if (value.equals("on")) {
                setDDRMinFreq(screenOnMinFreq);
            } else if (value.equals("off")) {
                setDDRMinFreq(screenOffMinFreq);
            }
        }else if(name.equals("video")){
            String videoState=value.split(":")[0];
            int width=Integer.valueOf(value.split(":")[1]);
            int height=Integer.valueOf(value.split(":")[2]);
            if (videoState.equals("start")) {
                if(width>2000&&height>2000){
                    setDDRMinFreq(video2kMinFreq);
                }else{
                    setDDRMinFreq(videoStartMinFreq);
                }
            } else if (videoState.equals("pause")) {
                setDDRMinFreq(videoPauseMinFreq);
            } else if (videoState.equals("stop")) {
                setDDRMinFreq(videoStopMinFreq);
            }
        }
        return 1;
    }

    private void setDmcValue(File file,String value){
        OutputStream output = null;
        OutputStreamWriter outputWrite = null;
        PrintWriter print = null;
        try {
            output = new FileOutputStream(file);
            outputWrite = new OutputStreamWriter(output);
            print = new PrintWriter(outputWrite);
            print.print(value);
            print.flush();
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setDDRMinFreq(String freq) {
        handler.removeMessages(SET_MIN_FREQ);
        if(curMinFreq>Integer.valueOf(freq)){
             //handler.sendMessage(handler.obtainMessage(SET_MIN_FREQ,Integer.valueOf(freq),0));
            Slog.d(TAG, "delay set DDR min_freq " + freq);
            handler.sendMessageDelayed(handler.obtainMessage(SET_MIN_FREQ,Integer.valueOf(freq),0),2000);
        }else{
            handler.sendMessage(handler.obtainMessage(SET_MIN_FREQ,Integer.valueOf(freq),0));
        }

    }




    public class VideoFileObserver extends FileObserver {
        public VideoFileObserver(String path) {
            //super(path, FileObserver.CLOSE_WRITE | FileObserver.CREATE | FileObserver.DELETE
            //| FileObserver.DELETE_SELF );
            super(path, FileObserver.CLOSE_WRITE);
        }

        public void onEvent(int event, String path) {
            Slog.d(TAG, "event=" + event + " path=" + path);
            if (path.equals("video_status") && event == FileObserver.CLOSE_WRITE) {
                String[] status = getVideoStatus().split(",");//state=start,width=1280,height=720,videoFramerate=24
                String videoState = status[0].split("=")[1];
                if (videoState.equals("start")) {
                    int width=Integer.valueOf(status[1].split("=")[1]);
                    int height=Integer.valueOf(status[2].split("=")[1]);
                    if(width>2000&&height>2000){
                        setDDRMinFreq(video2kMinFreq);
                    }else{
                        setDDRMinFreq(videoStartMinFreq);
                    }
                } else if (videoState.equals("pause")) {
                    setDDRMinFreq(videoPauseMinFreq);
                } else if (videoState.equals("stop")) {
                    setDDRMinFreq(videoStopMinFreq);
                }

            }
        }
    }

    private String getVideoStatus() {
        StringBuilder builder = new StringBuilder();
        try {
            File file = new File("/data/video/video_status");
            FileReader fread = new FileReader(file);
            BufferedReader buffer = new BufferedReader(fread);
            String str = null;
            while ((str = buffer.readLine()) != null) {
                builder.append(str);
            }
        } catch (IOException e) {
            Slog.e(TAG, "IO Exception"+e);
        }
        return builder.toString();
    }

    private void parseConfig(){
        File file=new File("/system/etc/ddr_config.xml");
        if (!file.exists()) {
            Slog.e(TAG, " Failed while trying resolve ddr config file, not exists");
            return;
        }
        FileInputStream stream=null;
        try {
            stream = new FileInputStream(file);
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, null);

            int type;
            do {
                type = parser.next();
                if (type == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if(tag.equals(TAG_SCREEN_OFF)){
                        screenOffMinFreq=parser.getAttributeValue(null,"min_freq");
                    }else if(tag.equals(TAG_SCREEN_ON)){
                        screenOnMinFreq=parser.getAttributeValue(null,"min_freq");
                    }else if(tag.equals(TAG_VIDEO_PAUSE)){
                        videoPauseMinFreq=parser.getAttributeValue(null,"min_freq");
                    }else if(tag.equals(TAG_VIDEO_START)){
                        videoStartMinFreq=parser.getAttributeValue(null,"min_freq");
                    }else if(tag.equals(TAG_VIDEO_STOP)){
                        videoStopMinFreq=parser.getAttributeValue(null,"min_freq");
                    }else if(tag.equals(TAG_VIDEO_2K)){
                        video2kMinFreq=parser.getAttributeValue(null,"min_freq");
                    }

                }
            } while(type != XmlPullParser.END_DOCUMENT);

        } catch (NullPointerException e) {
            Slog.w(TAG, "Warning, failed parsing wake_lock_filter.xml: " + e);
        } catch (NumberFormatException e) {
            Slog.w(TAG, "Warning, failed parsing wake_lock_filter.xml: " + e);
        } catch (XmlPullParserException e) {
            Slog.w(TAG, "Warning, failed parsing wake_lock_filter.xml: " + e);
        } catch (IOException e) {
            Slog.w(TAG, "Warning, failed parsing wake_lock_filter.xml: " + e);
        } catch (IndexOutOfBoundsException e) {
            Slog.w(TAG, "Warning, failed parsing wake_lock_filter.xml: " + e);
        }finally {
            if(stream!=null){
                try{
                    stream.close();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    }
}  

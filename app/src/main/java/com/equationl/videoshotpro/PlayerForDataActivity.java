package com.equationl.videoshotpro;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.equationl.videoshotpro.Image.Tools;
import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

public class PlayerForDataActivity extends AppCompatActivity {

    VideoView videoview;
    View videoview_contianer;
    Button btn_left,btn_right,btn_bottom;
    TextView video_time;
    Uri uri;
    LinkedBlockingQueue<Long> mark_time = new LinkedBlockingQueue<Long>();
    int pic_num=0, isFirstPlay=1, shot_num=0;
    GestureDetector mGestureDetector;
    Thread thread = new Thread(new MyThread()), gif_thread = new Thread(new ThreadShotGif());
    Boolean isDone=false;
    SharedPreferences settings;
    Boolean isHideBtn = false;
    Boolean isORIENTATION_LANDSCAPE = false;
    Boolean isShotGif = false, isShotingGif = false;
    Tools tool = new Tools();
    RelativeLayout.LayoutParams params;
    FFmpeg ffmpeg;
    String path, duration_text;
    Boolean isShowingTime = false;
    Resources res;
    int gif_start_time=0, gif_end_time=0;
    boolean isShotFinish=false;
    int shotToGifMinTime;

    String do4Rasult;
    int markTime[] = {0,0};
    ProgressDialog dialog;

    public static PlayerForDataActivity instance = null;    //FIXME  暂时这样吧，实在找不到更好的办法了


    private static final int HandlerStatusHideTime = 10010;
    private static final int HandlerStatusShowTime = 10011;
    private static final int HandlerStatusUpdateTime = 10012;
    private static final int HandlerShotGifFail = 10013;
    private static final int HandlerShotGifSuccess = 10014;
    private static final int HandlerShotGifRunning = 10015;
    private static final int HandlerFBFonProgress = 20000;
    private static final int HandlerFBFonSuccess = 20001;
    private static final int HandlerFBFonFail = 20002;
    private static final int HandlerFBFRunningFail = 20003;
    private static final int HandlerFBFRunningFinish = 20004;


    private static final String TAG = "el,In PFDA";

    private final MyHandler handler = new MyHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player_for_data);

        instance = this;

        videoview = (VideoView) findViewById(R.id.data_videoView);
        btn_left = (Button) findViewById(R.id.data_button_left);
        btn_right   = (Button) findViewById(R.id.data_button_right);
        btn_bottom   = (Button) findViewById(R.id.data_button_bottom);
        videoview_contianer = findViewById(R.id.data_main_videoview_contianer);
        video_time = (TextView) findViewById(R.id.data_video_time);

        params = (RelativeLayout.LayoutParams) btn_bottom.getLayoutParams();
        ffmpeg = FFmpeg.getInstance(this);

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        isShotGif = settings.getBoolean("isShotGif", false);

        res = getResources();
        //text_count.setText(String.format(res.getString(R.string.player_text_shotStatus),0, 0));

        tool.cleanExternalCache(this);    //清除上次产生的缓存图片

        Bundle bundle = this.getIntent().getExtras();
        path = bundle.getString("path");

        Log.i(TAG, "path="+path);

        do4Rasult = bundle.getString("do");

        uri = getIntent().getData();
        //videoview.setMediaController(new MediaController(this));
        videoview.setVideoURI(uri);

        MediaMetadataRetriever rev = new MediaMetadataRetriever();
        rev.setDataSource(getApplicationContext(),uri);
        String meta_duration = rev.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
        long duration = Long.parseLong(meta_duration);
        Bitmap bitmap = rev.getFrameAtTime(((duration/2)*1000),
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        videoview.setBackground(new BitmapDrawable(bitmap));

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = new Date(duration);
        duration_text = simpleDateFormat.format(date);

        mGestureDetector = new GestureDetector(this, mGestureListener);
        videoview.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                return mGestureDetector.onTouchEvent(event);
            }
        });

        if (do4Rasult.equals("FrameByFrame")) {
            Log.i(TAG, "逐帧截取模式，隐藏按钮");
            btn_right.setVisibility(View.INVISIBLE);
            btn_bottom.setText(R.string.player_text_mark);
        }

        if (do4Rasult.equals("getTime")) {
            Log.i(TAG, "获取时间，隐藏按钮");
            btn_right.setVisibility(View.INVISIBLE);
            btn_bottom.setText(R.string.player_text_ok);
        }

        btn_right   .setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (pic_num < 1) {
                    Toast.makeText(getApplicationContext(),R.string.player_toast_needMoreShot, Toast.LENGTH_LONG).show();
                }
                else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    if (!thread.isAlive()) {
                        thread = new Thread(new MyThread());
                        thread.start();
                    }
                    isDone = true;
                    btn_bottom.setClickable(false);
                    btn_right.setClickable(false);
                }
            }
        });
        btn_left .setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isFirstPlay==1) {
                    videoview.setBackgroundResource(0);
                    videoview.start();
                    btn_left.setText(R.string.player_text_rotationScreen);
                    isFirstPlay = 0;
                }
                else {
                    if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        isORIENTATION_LANDSCAPE = false;
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        isORIENTATION_LANDSCAPE = true;
                    }
                }
            }
        });
        btn_bottom   .setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                /*btn_bottom.setBackground(res.getDrawable(R.drawable.button_radius));
                text_count.setText(String.format(res.getString(R.string.player_text_shotStatus),pic_num+1,shot_num));
                mark_time.offer((long)videoview.getCurrentPosition());
                pic_num++;
                if (!thread.isAlive()) {
                    thread = new Thread(new MyThread());
                    thread.start();
                }   */

                if (do4Rasult.equals("FrameByFrame")) {
                    Log.i(TAG, "逐帧截取模式，点击按钮");
                    shotFrameOnclickButton();
                }

                if (do4Rasult.equals("getTime")) {
                    Log.i(TAG, "获取时间，点击按钮");
                    addTimeOnClickButton();
                }
            }
        });

        /*btn_bottom.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                Log.i(TAG, "on btn_bottom.onTouch");
                if (isShotingGif) {
                    Log.i(TAG, "on btn_bottom.onTouch, and is shotting gif");
                    return false;
                }
                if(motionEvent.getAction() == MotionEvent.ACTION_UP && isShotGif){
                    Log.d(TAG, "shot button ---> up");
                    gif_end_time = videoview.getCurrentPosition();
                    shotToGifMinTime = Integer.valueOf(settings.getString("shotToGifMinTime", "3"));
                    Log.i(TAG, "shotToGifMinTime="+shotToGifMinTime);
                    if (gif_end_time-gif_start_time > shotToGifMinTime*1000) {
                        btn_bottom.setBackground(res.getDrawable(R.drawable.button_radius_up));
                        isShotingGif = true;
                        if (!gif_thread.isAlive()) {
                            gif_thread = new Thread(new ThreadShotGif());
                            gif_thread.start();
                        }
                        return true;
                    }
                }
                if(motionEvent.getAction() == MotionEvent.ACTION_DOWN && isShotGif){
                    Log.d(TAG, "shot button ---> down");
                    btn_bottom.setBackground(res.getDrawable(R.drawable.button_radius));
                    gif_start_time = videoview.getCurrentPosition();
                }
                return false;
            }
        });   */

        videoview.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                if(what== MediaPlayer.MEDIA_ERROR_SERVER_DIED){
                    Toast.makeText(getApplicationContext(),"Media Error,Server Died"+extra, Toast.LENGTH_LONG).show();
                }else if(what== MediaPlayer.MEDIA_ERROR_UNKNOWN){
                    Toast.makeText(getApplicationContext(),"Media Error,Error Unknown "+extra, Toast.LENGTH_LONG).show();
                }
                return false;
            }
        });

        videoview.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                btn_left.setText(R.string.player_text_replay);
                isFirstPlay = 1;
            }
        });
    }

    @Override
    protected void onRestart() {
        Log.i("el_test", "onRestart");
        super.onRestart();
        isDone = false;
        btn_bottom.setClickable(true);
        btn_right.setClickable(true);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (videoview == null) {
            return;
        }
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){//横屏
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().invalidate();
            float width = getWidthInPx(this);
            float height = getHeightInPx(this);
            videoview_contianer.getLayoutParams().height = (int) height;
            videoview_contianer.getLayoutParams().width = (int) width;
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            btn_bottom.setLayoutParams(params);
            Log.i("TEST","width="+width+" height="+height);
        } else {
            final WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().setAttributes(attrs);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            float width = getWidthInPx(this);
            float height = getHeightInPx(this);
            videoview_contianer.getLayoutParams().height = (int) height;
            videoview_contianer.getLayoutParams().width = (int) width;
            params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            btn_bottom.setLayoutParams(params);
        }
    }

    public static float getHeightInPx(Context context) {
        float height = context.getResources().getDisplayMetrics().heightPixels;
        return height;
    }
    public static float getWidthInPx(Context context) {
        float width = context.getResources().getDisplayMetrics().widthPixels;
        return width;
    }

    public boolean saveMyBitmap(Bitmap bmp, String bitName) throws IOException {

        boolean flag;
        try {
            tool.saveBitmap2png(bmp,bitName, getExternalCacheDir());
            flag = true;
        } catch (Exception e) {
            flag = false;
        }

        return flag;
    }

    /**private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    break;
                case 2:
                    //text_count.setText(msg.obj.toString());
                    Toast.makeText(getApplicationContext(), (String)msg.obj, Toast.LENGTH_LONG).show();
                    break;
                case 3:
                    Intent intent = new Intent(PlayerForDataActivity.this, MarkPictureActivity.class);
                    startActivity(intent);
                    break;
                case HandlerStatusHideTime:
                    isShowingTime = false;
                    video_time.setVisibility(View.GONE);
                    break;
                case HandlerStatusShowTime:
                    isShowingTime = true;
                    video_time.setVisibility(View.VISIBLE);
                    String res;
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
                    simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    long lt = new Long(videoview.getCurrentPosition());
                    Date date = new Date(lt);
                    res = simpleDateFormat.format(date);
                    res += "/"+duration_text;
                    video_time.setText(res);
                    autoHideTime();
                    if (videoview.isPlaying() && isShowingTime) {
                        handler.sendEmptyMessageDelayed(HandlerStatusUpdateTime, 200);
                    }
                    Log.i("test", "res="+res);
                    break;
                case HandlerStatusUpdateTime:
                    //video_time.setVisibility(View.VISIBLE);
                    simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
                    simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    lt = new Long(videoview.getCurrentPosition());
                    date = new Date(lt);
                    res = simpleDateFormat.format(date);
                    res += "/"+duration_text;
                    video_time.setText(res);
                    if (videoview.isPlaying() && isShowingTime) {
                        handler.sendEmptyMessageDelayed(HandlerStatusUpdateTime, 200);
                    }
                    break;
                case HandlerShotGifSuccess:
                    isShotingGif = false;
                    MediaScannerConnection.scanFile(PlayerForDataActivity.this, new String[]{msg.obj.toString()}, null, null);
                    Toast.makeText(PlayerForDataActivity.this, R.string.player_toast_shotGif_success, Toast.LENGTH_SHORT).show();
                    break;
                case HandlerShotGifFail:
                    Toast.makeText(PlayerForDataActivity.this, R.string.player_toast_shotGif_fail, Toast.LENGTH_SHORT).show();
                    break;
                case HandlerShotGifRunning:
                    Toast.makeText(PlayerForDataActivity.this, R.string.player_toast_shotGif_start, Toast.LENGTH_SHORT).show();
                    break;
                case HandlerFBFonFail:
                    dialog.setMessage("截取失败：\n"+msg.obj.toString());
                    dialog.setCancelable(true);
                    break;
                case HandlerFBFonSuccess:
                    dialog.setMessage("截取成功：\n"+msg.obj.toString());
                    dialog.dismiss();
                    Toast.makeText(PlayerForDataActivity.this, R.string.player_toast_FBF_done, Toast.LENGTH_SHORT).show();
                    markTime[0] = 0;
                    markTime[1] = 0;
                    break;
                case HandlerFBFonProgress:
                    dialog.setMessage(msg.obj.toString());
                    break;
                case HandlerFBFRunningFinish:
                    btn_bottom.setText(R.string.player_text_mark);
            }

        }
    };   **/

    private class MyThread implements Runnable {
        @Override
        public void run() {
            Long time;
            while ((time = mark_time.peek()) != null) {
                if (!ffmpeg.isFFmpegCommandRunning()) {
                    isShotFinish = false;
                    Log.i("el_test", "time="+time);
                    Log.i("el_test", "shot_num="+shot_num);
                    String outPathName;
                    if (settings.getBoolean("isShotToJpg",true)) {
                        outPathName = getExternalCacheDir().toString()+"/"+shot_num+".jpg";
                    }
                    else {
                        outPathName = getExternalCacheDir().toString()+"/"+shot_num+".png";
                    }

                    String cmd[] = {"-ss", ""+(time/1000.0), "-i", path, "-y", "-f", "image2", "-t", "0.001", outPathName};
                    try {
                        ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
                            @Override
                            public void onFailure(String message) {
                                Log.i("el_test: onFailure", message);
                                Message msg = Message.obtain();
                                msg.obj = R.string.player_text_savePicture_fail+message;
                                msg.what = 2;
                                handler.sendMessage(msg);
                            }
                            @Override
                            public void onSuccess(String message) {
                                shot_num++;
                                mark_time.poll();
                                Log.i("el_test:", "onSuccess");
                                Message msg = Message.obtain();
                                msg.obj = String.format(res.getString(R.string.player_text_shotStatus),pic_num,shot_num);
                                msg.what = 1;
                                handler.sendMessage(msg);
                            }
                            @Override
                            public void onFinish() {
                                isShotFinish = true;
                            }
                        });
                    } catch (FFmpegCommandAlreadyRunningException e) {
                        Message msg = Message.obtain();
                        msg.obj = res.getString(R.string.player_text_savePicture_fail)+e;
                        msg.what = 2;
                        handler.sendMessage(msg);
                    }
                    //阻塞等待截取结果
                    while (!isShotFinish) {
                        try {
                            Thread.sleep(100);
                        }
                        catch (InterruptedException e) {}
                    }
                }
                else {
                    Log.i("el_test", "running");
                }
            }
            if (isDone) {
                Message msg = Message.obtain();
                msg.obj = "";
                msg.what = 3;
                handler.sendMessage(msg);
            }
        }
    }

    private class ThreadShotGif implements Runnable {
        @Override
        public void run() {
            String gif_RP = settings.getString("gifRP_value", "320x240");
            String gif_frameRate = settings.getString("gifFrameRate_value", "14");
            Log.i(TAG, "RP="+gif_RP+" fraerate="+gif_frameRate);
            String video_path = tool.getImageAbsolutePath(PlayerForDataActivity.this,uri);
            SimpleDateFormat sDateFormat    =   new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
            String date    =    sDateFormat.format(new    java.util.Date());
            date += "-by_EL.gif";
            final String save_path =  Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES).getPath() + "/" + date;
            String cmd = "-ss "+(gif_start_time/1000.0)+" -t "+((gif_end_time-gif_start_time)/1000.0)+" -i "+video_path;
            Log.i(TAG, "gif start time(s)="+(gif_start_time/1000.0)+" time(ms)="+gif_start_time+" all="+((gif_end_time-gif_start_time)/1000.0));
            cmd += gif_RP.equals("-1")?"":" -s "+gif_RP;
            cmd += " -f gif";
            cmd += gif_frameRate.equals("-1")?"":" -r "+gif_frameRate;
            cmd += " "+save_path;
            Log.i(TAG, "cmd = "+cmd);
            String gif_cmd[] = cmd.split(" ");

            while (ffmpeg.isFFmpegCommandRunning()) {
                //阻塞等待执行结束
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e){}
            }
            try {
                ffmpeg.execute(gif_cmd, new ExecuteBinaryResponseHandler() {

                    @Override
                    public void onStart() {
                        handler.sendEmptyMessage(HandlerShotGifRunning);
                    }

                    @Override
                    public void onProgress(String message) {}

                    @Override
                    public void onFailure(String message) {
                        Log.e(TAG, "截取GIF失败："+message);
                        handler.sendEmptyMessage(HandlerShotGifFail);
                    }

                    @Override
                    public void onSuccess(String message) {
                        Message msg = Message.obtain();
                        msg.obj = save_path;
                        msg.what = HandlerShotGifSuccess;
                        handler.sendMessage(msg);
                    }

                    @Override
                    public void onFinish() {}
                });
            } catch (FFmpegCommandAlreadyRunningException e) {
                handler.sendEmptyMessage(HandlerShotGifFail);
            }
        }
    }

    private android.view.GestureDetector.OnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (videoview.isPlaying()) {
                videoview.pause();
            }
            else {
                videoview.setBackgroundResource(0);
                videoview.start();
                btn_left.setText(R.string.player_text_rotationScreen);
            }

            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            Log.i("test", "单击屏幕");
            handler.sendEmptyMessage(HandlerStatusShowTime);

            if (settings.getBoolean("isHideButton", false) && isORIENTATION_LANDSCAPE) {
                if (isHideBtn) {
                    btn_left.setVisibility(View.VISIBLE);
                    btn_right.setVisibility(View.  VISIBLE);
                    btn_bottom.setVisibility(View.  VISIBLE);
                    isHideBtn = false;
                }
                else {
                    btn_left.setVisibility(View.INVISIBLE);
                    btn_right.setVisibility(View.  INVISIBLE);
                    btn_bottom.setVisibility(View.  INVISIBLE);
                    isHideBtn = true;
                }
            }

            return false;
        }


        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            video_time.setVisibility(View.VISIBLE);
            int px2ime = 500;
            videoview.seekTo(videoview.getCurrentPosition()-(int)distanceX*px2ime);
            String res;
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            long lt = new Long(videoview.getCurrentPosition());
            Date date = new Date(lt);
            res = simpleDateFormat.format(date);
            res += "/"+duration_text;
            video_time.setText(res);
            autoHideTime();

            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            //video_time.setVisibility(View.GONE);
            return true;
        }
    };

    Timer tHide = null;
    private void autoHideTime() {
        if (tHide == null) {
            Log.i("test","call in autoHideTime with tHide is null");
            tHide = new Timer();
            tHide.schedule(new TimerTask() {
                @Override
                public void run() {
                    handler.sendEmptyMessage(HandlerStatusHideTime);
                    tHide = null;
                }
            }, 2000);
        }
    }


    private void startFrameByFrame() {
        String video_path = tool.getImageAbsolutePath(PlayerForDataActivity.this,uri);
        SimpleDateFormat sDateFormat    =   new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
        String date    =    sDateFormat.format(new    java.util.Date());
        final String save_path = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES)+"/"+date+"/";
        File dirFirstFolder = new File(save_path);
        if(!dirFirstFolder.exists())
        {
            dirFirstFolder.mkdirs();
        }
        String text_last = settings.getBoolean("isShotToJpg",true)?"jpg -vcodec mjpeg":"png";
        double time_start = markTime[0];
        time_start = time_start/1000.0;
        double time_end = markTime[1];
        time_end = time_end/1000.0;
        time_end = time_end - time_start;
        String text = "-ss "+time_start+" -t "+time_end+" -i "+video_path+" "+save_path+"%08d."+text_last;
        Log.i(TAG, "cmd="+text);
        FFmpeg ffmpeg = FFmpeg.getInstance(getApplicationContext());
        if (!ffmpeg.isFFmpegCommandRunning()) {
            String cmd[] = text.split(" ");
            try {
                ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
                    @Override
                    public void onStart() {}
                    @Override
                    public void onFailure(String message) {
                        Message msg = Message.obtain();
                        msg.obj = message;
                        msg.what = HandlerFBFonFail;
                        handler.sendMessage(msg);
                    }
                    @Override
                    public void onSuccess(String message) {
                        Message msg = Message.obtain();
                        msg.obj = save_path;
                        msg.what = HandlerFBFonSuccess;
                        handler.sendMessage(msg);
                    }
                    @Override
                    public void onProgress(String message) {
                        Message msg = Message.obtain();
                        msg.obj = message;
                        msg.what = HandlerFBFonProgress;
                        handler.sendMessage(msg);
                    }
                    @Override
                    public void onFinish() {
                        handler.sendEmptyMessage(HandlerFBFRunningFinish);
                    }
                });
            } catch (FFmpegCommandAlreadyRunningException e) {
                handler.sendEmptyMessage(HandlerFBFRunningFail);
            }
        }
    }

    private void shotFrameOnclickButton() {
        int time = 0;
        if (markTime[0] == 0 && markTime[1] == 0) {
            btn_bottom.setText(R.string.player_text_mark_end);
            time = videoview.getCurrentPosition();
            markTime[0] = time==0 ? 1:time;
        }
        else {
            if (time >= videoview.getCurrentPosition()) {
                Toast.makeText(PlayerForDataActivity.this, R.string.player_toast_mark_timeError, Toast.LENGTH_SHORT).show();
            }
            else {
                markTime[1] = videoview.getCurrentPosition();
                dialog = new ProgressDialog(PlayerForDataActivity.this);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setIndeterminate(false);
                dialog.setCancelable(false);
                dialog.setMessage(res.getString(R.string.player_dialog_FBF_content));
                dialog.setTitle(res.getString(R.string.player_dialog_FBF_title));
                dialog.show();
                startFrameByFrame();
            }
        }
    }

    private void addTimeOnClickButton() {
        int time = videoview.getCurrentPosition();
        Intent intent = new Intent();
        intent.putExtra("time", time);
        this.setResult(1, intent);
        finish();
    }



    private static class MyHandler extends Handler {
        private final WeakReference<PlayerForDataActivity> mActivity;

        public MyHandler(PlayerForDataActivity activity) {
            mActivity = new WeakReference<PlayerForDataActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            PlayerForDataActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case 1:
                        break;
                    case 2:
                        //text_count.setText(msg.obj.toString());
                        Toast.makeText(activity.getApplicationContext(), (String)msg.obj, Toast.LENGTH_LONG).show();
                        break;
                    case 3:
                        Intent intent = new Intent(activity, MarkPictureActivity.class);
                        activity.startActivity(intent);
                        break;
                    case HandlerStatusHideTime:
                        activity.isShowingTime = false;
                        activity.video_time.setVisibility(View.GONE);
                        break;
                    case HandlerStatusShowTime:
                        activity.isShowingTime = true;
                        activity.video_time.setVisibility(View.VISIBLE);
                        String res;
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
                        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                        long lt = new Long(activity.videoview.getCurrentPosition());
                        Date date = new Date(lt);
                        res = simpleDateFormat.format(date);
                        res += "/"+activity.duration_text;
                        activity.video_time.setText(res);
                        activity.autoHideTime();
                        if (activity.videoview.isPlaying() && activity.isShowingTime) {
                            activity.handler.sendEmptyMessageDelayed(HandlerStatusUpdateTime, 200);
                        }
                        Log.i("test", "res="+res);
                        break;
                    case HandlerStatusUpdateTime:
                        //video_time.setVisibility(View.VISIBLE);
                        simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
                        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                        lt = new Long(activity.videoview.getCurrentPosition());
                        date = new Date(lt);
                        res = simpleDateFormat.format(date);
                        res += "/"+activity.duration_text;
                        activity.video_time.setText(res);
                        if (activity.videoview.isPlaying() && activity.isShowingTime) {
                            activity.handler.sendEmptyMessageDelayed(HandlerStatusUpdateTime, 200);
                        }
                        break;
                    case HandlerShotGifSuccess:
                        activity.isShotingGif = false;
                        MediaScannerConnection.scanFile(activity, new String[]{msg.obj.toString()}, null, null);
                        Toast.makeText(activity, R.string.player_toast_shotGif_success, Toast.LENGTH_SHORT).show();
                        break;
                    case HandlerShotGifFail:
                        Toast.makeText(activity, R.string.player_toast_shotGif_fail, Toast.LENGTH_SHORT).show();
                        break;
                    case HandlerShotGifRunning:
                        Toast.makeText(activity, R.string.player_toast_shotGif_start, Toast.LENGTH_SHORT).show();
                        break;
                    case HandlerFBFonFail:
                        activity.dialog.setMessage("截取失败：\n"+msg.obj.toString());
                        activity.dialog.setCancelable(true);
                        break;
                    case HandlerFBFonSuccess:
                        activity.dialog.setMessage("截取成功！");
                        activity.dialog.dismiss();
                        Toast.makeText(activity, String.format(activity.res.getString(R.string.player_toast_FBF_done),msg.obj.toString()),
                                Toast.LENGTH_LONG).show();
                        activity.markTime[0] = 0;
                        activity.markTime[1] = 0;
                        break;
                    case HandlerFBFonProgress:
                        activity.dialog.setMessage(msg.obj.toString());
                        break;
                    case HandlerFBFRunningFinish:
                        activity.btn_bottom.setText(R.string.player_text_mark);
                }
            }
        }
    }

}
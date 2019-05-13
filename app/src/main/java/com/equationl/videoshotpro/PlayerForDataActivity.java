package com.equationl.videoshotpro;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.equationl.videoshotpro.Image.CheckPictureText;
import com.equationl.videoshotpro.Image.Tools;
import com.equationl.videoshotpro.utils.Utils;
import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.player.PlayerFactory;
import com.shuyu.gsyvideoplayer.player.SystemPlayerManager;
import com.shuyu.gsyvideoplayer.utils.OrientationUtils;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class PlayerForDataActivity extends AppCompatActivity {
    SharedPreferences settings;
    StandardGSYVideoPlayer videoPlayer;
    OrientationUtils orientationUtils;
    ProgressDialog dialog;
    Resources res;
    Tools tool;
    Utils utils = new Utils();
    FFmpeg ffmpeg;
    Uri video_uri;
    File externalCacheDir;
    CheckPictureText cpt;

    ImageView btn_shot, btn_done;

    String Do;
    String video_path;
    int markTime[] = {0,0};

    private final MyHandler handler = new MyHandler(this);

    private static final String TAG = "EL, in PFD2Activity";
    @SuppressLint("StaticFieldLeak")
    public static PlayerForDataActivity instance = null;    //FIXME  暂时这样吧，实在找不到更好的办法了

    private static final int HandlerFBFonProgress = 10000;
    private static final int HandlerFBFonSuccess = 10001;
    private static final int HandlerFBFonFail = 10002;
    private static final int HandlerFBFRunningFail = 10003;
    private static final int HandlerFBFRunningFinish = 10004;
    private static final int HandlerABonProgress= 20001;
    private static final int HandlerABonSuccess= 20002;
    private static final int HandlerABonFail= 20003;
    private static final int HandlerABonInitFail= 20004;


    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player_for_data);
        instance = this;
        init();
        checkUri();
        initVideoPlayer();
        adaptationDo();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init() {
        btn_shot = findViewById(R.id.playerForData_btn_shot);
        btn_done = findViewById(R.id.playerForData_btn_done);
        videoPlayer =  findViewById(R.id.video_playerForData);

        Bundle bundle = this.getIntent().getExtras();
        if (bundle == null) {
            Toast.makeText(this, R.string.player_toast_getWhatToDo_fail, Toast.LENGTH_SHORT).show();
            finish();
        }
        else {
            Do = bundle.getString("do");
        }

        tool = new Tools();
        ffmpeg = FFmpeg.getInstance(this);
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        res = getResources();

        video_uri = getIntent().getData();
        video_path = tool.getImageAbsolutePath(this, video_uri);
        if (video_path == null) {
            Toast.makeText(this, R.string.player_toast_getVideoPath_fail, Toast.LENGTH_SHORT).show();
            finish();
        }

        tool.cleanExternalCache(this);

        btn_done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                click_btn_done();
            }
        });

        btn_shot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                click_btn_shot();
            }
        });

    }

    private void initVideoPlayer() {
        PlayerFactory.setPlayManager(SystemPlayerManager.class);
        videoPlayer.setUp(video_path, true, "");
        /*ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setImageResource(R.mipmap.error_picture);
        videoPlayer.setThumbImageView(imageView);  */
        videoPlayer.setLockLand(true);
        videoPlayer.setShowFullAnimation(false);
        videoPlayer.getTitleTextView().setVisibility(View.GONE);
        orientationUtils = new OrientationUtils(this, videoPlayer);
        //设置全屏按键功能,这是使用的是选择屏幕，而不是全屏
        videoPlayer.getFullscreenButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                orientationUtils.resolveByClick();
                //videoPlayer.startWindowFullscreen(PlayerActivity.this, false, false);
            }
        });
        videoPlayer.setIsTouchWiget(true);
        //设置返回按键功能
        videoPlayer.getBackButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        videoPlayer.startPlayLogic();
    }

    private void adaptationDo() {
        switch (Do) {
            case "FrameByFrame":
                btn_done.setVisibility(View.INVISIBLE);
                btn_shot.setImageResource(R.drawable.marked);
                break;
            case "getTime":
                btn_done.setVisibility(View.INVISIBLE);
                btn_shot.setImageResource(R.drawable.add);
                break;
            case "AutoBuild":
                btn_done.setVisibility(View.INVISIBLE);
                btn_shot.setImageResource(R.drawable.marked);
                externalCacheDir = getExternalCacheDir();
                break;
        }
    }

    private void click_btn_done() {
        //TODO
    }

    private void click_btn_shot() {
        switch (Do) {
            case "FrameByFrame":
                shotFrameOnclickButton();
                break;
            case "getTime":
                insertTimeOnClickButton();
                break;
            case "AutoBuild":
                startAutoBuild();
                break;
        }
    }

    private void startAutoBuild() {
        File data = new File(getExternalFilesDir("tessdata"), "chi_sim.traineddata");
        if (!data.exists()) {
            //TODO 下载文件
            Log.i(TAG, "need download tessdata");
        }
        shotFrameOnclickButton();
    }

    private void autoBuildStartCheckText() {
        new Thread(new CheckTextThread()).start();
    }

    private void insertTimeOnClickButton() {
        int time = videoPlayer.getCurrentPositionWhenPlaying();
        Intent intent = new Intent();
        intent.putExtra("time", time);
        this.setResult(1, intent);
        finish();
    }

    private void shotFrameOnclickButton() {
        int time;
        if (markTime[0] == 0 && markTime[1] == 0) {
            btn_shot.setImageResource(R.drawable.arrow_right_thick);
            time = videoPlayer.getCurrentPositionWhenPlaying();
            markTime[0] = time==0 ? 1:time;
        }
        else {
            if (markTime[0] >= videoPlayer.getCurrentPositionWhenPlaying()) {
                Toast.makeText(this, R.string.player_toast_mark_timeError, Toast.LENGTH_SHORT).show();
            }
            else {
                markTime[1] = videoPlayer.getCurrentPositionWhenPlaying();
                dialog = new ProgressDialog(this);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setIndeterminate(false);
                dialog.setCancelable(false);
                dialog.setMessage(res.getString(R.string.player_dialog_FBF_content));
                dialog.setTitle(res.getString(R.string.player_dialog_FBF_title));
                dialog.show();
                videoPlayer.onVideoPause();
                startFrameByFrame();
            }
        }
    }

    // FIXME 文档中没找到检查URI的方法，这样曲线检查吧
    private void checkUri() {
        MediaMetadataRetriever rev = new MediaMetadataRetriever();
        try {
            rev.setDataSource(this, video_uri);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, R.string.player_toast_loadUriFail_invalid, Toast.LENGTH_LONG).show();
            finish();
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.player_toast_loadUriFail_lackPermission, Toast.LENGTH_LONG).show();
            finish();
        }   catch (Exception e) {
            Toast.makeText(this, R.string.player_toast_loadUriFail_other, Toast.LENGTH_LONG).show();
            finish();
        }
        rev.release();
    }

    private void startFrameByFrame() {
        SimpleDateFormat sDateFormat    =   new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.CHINA);
        String date    =    sDateFormat.format(new    java.util.Date());
        final String save_path = tool.getSaveRootPath()+"/"+date+"/";
        if (!Do.equals("AutoBuild")) {
            File dirFirstFolder = new File(save_path);
            if(!dirFirstFolder.exists())
            {
                //noinspection ResultOfMethodCallIgnored
                dirFirstFolder.mkdirs();
            }
        }
        boolean isVideoPathHaveSpace = false;
        if (video_path.contains(" ")) {  //避免因为视频路径中包含空格而导致按照空格分割命令时出错
            video_path = video_path.replaceAll(" ", "_");
            isVideoPathHaveSpace = true;
        }
        String text_last = settings.getBoolean("isShotToJpg",true)?"jpg -vcodec mjpeg":"png";
        double time_start = markTime[0];
        time_start = time_start/1000.0;
        double time_end = markTime[1];
        time_end = time_end/1000.0;
        time_end = time_end - time_start;

        String text;
        if (Do.equals("AutoBuild")) {
            if (externalCacheDir == null) {
                Message msg = Message.obtain();
                msg.obj = res.getString(R.string.player_text_getCachePath_fail);
                msg.what = HandlerFBFonFail;
                handler.sendMessage(msg);
                return;
            }
            text = "-ss "+time_start+" -t "+time_end+" -i "+video_path+" -r 1 "+externalCacheDir.toString()+"/%d."+text_last;
        }
        else {
            text = "-ss "+time_start+" -t "+time_end+" -i "+video_path+" "+save_path+"%08d."+text_last;
        }

        Log.i(TAG, "cmd="+text);
        FFmpeg ffmpeg = FFmpeg.getInstance(getApplicationContext());
        if (!ffmpeg.isFFmpegCommandRunning()) {
            String cmd[] = text.split(" ");
            if (isVideoPathHaveSpace) {   //FIXME 现在的索引确定是5，小心以后变化啊
                cmd[5] = cmd[5].replaceAll("_", " ");
            }
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

    private static class MyHandler extends Handler {
        private final WeakReference<PlayerForDataActivity> mActivity;

        private MyHandler(PlayerForDataActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            PlayerForDataActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case HandlerFBFonFail:
                        String message = activity.res.getString(R.string.player_dialog_FBF_content_shot_fail);
                        message = String.format(message, msg.obj.toString());
                        activity.dialog.setMessage(message);
                        activity.dialog.setCancelable(true);
                        Log.e(TAG, msg.obj.toString());
                        break;
                    case HandlerFBFonSuccess:
                        if (activity.Do.equals("AutoBuild")) {
                            activity.dialog.setMessage(activity.res.getString(R.string.player_dialog_FBF_content_shot_success));
                            activity.markTime[0] = 0;
                            activity.markTime[1] = 0;
                            activity.autoBuildStartCheckText();
                            activity.dialog.setTitle(R.string.player_dialog_AB_title);
                        }
                        else {
                            activity.dialog.setMessage(activity.res.getString(R.string.player_dialog_FBF_content_shot_success));
                            activity.dialog.dismiss();
                            activity.markTime[0] = 0;
                            activity.markTime[1] = 0;
                            Intent intent2 = new Intent(activity, ChooseBestPictureActivity.class);
                            intent2.putExtra("filePath", msg.obj.toString());
                            activity.startActivity(intent2);
                        }
                        break;
                    case HandlerFBFonProgress:
                        activity.dialog.setMessage(msg.obj.toString());
                        break;
                    case HandlerFBFRunningFinish:
                        if (!activity.Do.equals("AutoBuild")) {
                            activity.videoPlayer.onVideoResume();
                            activity.btn_shot.setImageResource(R.drawable.marked);
                        }
                        break;
                    case HandlerABonFail:
                        activity.dialog.setMessage(msg.obj.toString());
                        activity.dialog.setCancelable(true);
                        break;
                    case HandlerABonSuccess:
                        String fileList[] = (String[]) msg.obj;
                        Intent intent = new Intent(activity, BuildPictureActivity.class);
                        Bundle bundle = new Bundle();
                        bundle.putStringArray("fileList", fileList);
                        bundle.putBoolean("isFromExtra", true);
                        bundle.putBoolean("isAutoBuild", true);
                        bundle.putInt("SubtitleHeight", activity.cpt.getSubtitleHeight());
                        intent.putExtras(bundle);
                        activity.startActivity(intent);

                        Log.i(TAG, "AB done!");
                        activity.dialog.dismiss();
                        activity.finish();
                        break;
                    case HandlerABonProgress:
                        activity.dialog.setMessage(msg.obj.toString());
                        break;
                    case HandlerABonInitFail:
                        Toast.makeText(activity,
                                activity.res.getString(R.string.player_toast_AB_init_fail)+msg.obj.toString(),
                                Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }
    }

    private class CheckTextThread implements Runnable {
        @Override
        public void run(){
            boolean isInit = false;
            try {
                cpt = new CheckPictureText(PlayerForDataActivity.this);
                isInit = true;
            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
                Message msg = Message.obtain();
                msg.obj = e;
                msg.what = HandlerABonInitFail;
                handler.sendMessage(msg);
            }

            if (!isInit) {
                Log.i(TAG, "need init tess");
            }
            else if (externalCacheDir == null) {
                Message msg = Message.obtain();
                msg.obj = res.getString(R.string.player_text_getCachePath_fail);
                Log.i(TAG, "msg.obj= "+res.getString(R.string.player_text_getCachePath_fail));
                msg.what = HandlerABonFail;
                handler.sendMessage(msg);
            }
            else {
                String extension = settings.getBoolean("isShotToJpg",true)?"jpg":"png";
                try {
                    tool.copyFile(new File(externalCacheDir, "1."+extension), new File(externalCacheDir, "0."+extension));
                } catch (IOException e) {
                    Log.e(TAG, Log.getStackTraceString(e));
                }
                String[] fileList = tool.getFileOrderByName(externalCacheDir.toString(), 1);
                boolean isFirst = true;
                fileList[0] = "del";
                for (int i=1;i<fileList.length;i++) {
                    Message msg = Message.obtain();
                    msg.obj = String.format(res.getString(R.string.player_dialog_AB_content_shot_progress), i+1);
                    msg.what = HandlerABonProgress;
                    handler.sendMessage(msg);
                    Bitmap bitmap = tool.getBitmapFromFile(externalCacheDir.toString()+"/"+fileList[i]);
                    int pictureState = cpt.isSingleSubtitlePicture(bitmap);
                    if (pictureState == cpt.StateCutPicture) {
                        Log.i(TAG, fileList[i]+" is cut");
                        if (isFirst) {
                            fileList[i] = "all";
                            isFirst = false;
                        }
                        else {
                            fileList[i] = "cut";
                        }
                    }
                    else if (pictureState == cpt.StateDelPicture){
                        Log.i(TAG, fileList[i]+" is del");
                        fileList[i] = "del";
                    }
                    else {
                        fileList[i] = "del";
                    }
                }
                Message msg = Message.obtain();
                msg.obj = fileList;
                msg.what = HandlerABonSuccess;
                handler.sendMessage(msg);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            if (Do.equals("FrameByFrame")) {
                utils.finishActivity(MainActivity.instance);
                Intent intent = new Intent(PlayerForDataActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
            return super.onKeyDown(keyCode, event);
        }else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        videoPlayer.onVideoPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        btn_shot.setClickable(true);
        btn_done.setClickable(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        videoPlayer.onVideoResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        GSYVideoManager.releaseAllVideos();
        if (orientationUtils != null)
            orientationUtils.releaseListener();
        if (dialog != null)
            dialog.dismiss();
    }

    @Override
    public void onBackPressed() {
        if (orientationUtils.getScreenType() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            videoPlayer.getFullscreenButton().performClick();
            return;
        }
        videoPlayer.setVideoAllCallBack(null);
        super.onBackPressed();
    }

}

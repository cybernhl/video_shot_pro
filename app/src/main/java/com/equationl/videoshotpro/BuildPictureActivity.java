package com.equationl.videoshotpro;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.equationl.videoshotpro.Image.Tools;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;

public class BuildPictureActivity extends AppCompatActivity {
    Button btn_up, btn_down, btn_done;
    ImageView imageTest;
    String[] fileList;
    Canvas canvas;
    Paint paint;
    Bitmap bm_test;
    float startY, stopY;
    int bWidth,bHeight;
    ProgressDialog dialog;
    int isDone=0;
    File savePath=null;
    SharedPreferences settings;
    Tools tool = new Tools();
    Boolean isFromExtra;
    public static BuildPictureActivity instance = null;    //FIXME  暂时这样吧，实在找不到更好的办法了

    private static final String TAG = "EL,InBuildActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_build_picture);

        Log.i("cao", "In BuildPictureActivity onCreate");

        instance = this;

        btn_up    = (Button)   findViewById(R.id.button_up);
        btn_down  = (Button)    findViewById(R.id.button_down);
        btn_done  = (Button)    findViewById(R.id.button_final_done);
        imageTest = (ImageView) findViewById(R.id.imageTest);

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        Bundle bundle = this.getIntent().getExtras();
        fileList = bundle.getStringArray("fileList");
        isFromExtra = bundle.getBoolean("isFromExtra");

        //Log.i("filelist", fileList.toString());

        dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);// 设置样式
        dialog.setIndeterminate(false);
        dialog.setCancelable(false);
        dialog.setMessage("正在处理...");
        dialog.setTitle("请稍等");
        dialog.setMax(fileList.length+1);

        Toast.makeText(this,"请调整剪切字幕的位置", Toast.LENGTH_LONG).show();

        bm_test = getCutImg().copy(Bitmap.Config.ARGB_8888,true);

        canvas = new Canvas(bm_test);
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth((float) 5);
        bHeight = bm_test.getHeight();
        bWidth = bm_test.getWidth();

        Log.i(TAG, "TEST IMAGE WIDTH="+bWidth+" HEIGHT="+bHeight);

        startY = (float) (bHeight*0.8);
        stopY = startY;
        canvas.drawLine(0,startY,bWidth,stopY,paint);
        imageTest.setImageBitmap(bm_test);

        int test[] = tool.getImageRealSize(imageTest);
        Log.i(TAG, "imageview width="+imageTest.getHeight()+" height="+imageTest.getWidth());


        btn_up.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isDone == 0) {
                    bm_test = getCutImg().copy(Bitmap.Config.ARGB_8888,true);
                    canvas = new Canvas(bm_test);
                    startY = startY-8;
                    if (startY < 0) {
                        startY = 0;
                    }
                    stopY = startY;
                    canvas.drawLine(0,startY,bm_test.getWidth(),stopY,paint);
                    imageTest.setImageBitmap(bm_test);
                }
                else {
                    try {
                        PlayerActivity.instance.finish();
                        MarkPictureActivity.instance.finish();
                        MainActivity.instance.finish();
                    } catch (NullPointerException e) {Log.e("el", e.toString());}
                    Intent intent = new Intent(BuildPictureActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                }
            }
        });

        btn_down.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isDone == 0) {
                    bm_test = getCutImg().copy(Bitmap.Config.ARGB_8888,true);
                    canvas = new Canvas(bm_test);
                    startY = startY+8;
                    if (startY > bHeight) {
                        startY = bHeight;
                    }
                    stopY = startY;
                    canvas.drawLine(0,startY,bm_test.getWidth(),stopY,paint);
                    imageTest.setImageBitmap(bm_test);
                }
            }
        });

        btn_done.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int test[] = tool.getImageRealSize(imageTest);
                Log.i(TAG, "imageview width="+test[0]+" height="+test[1]);
                if (isDone==1) {
                    Uri imageUri = Uri.fromFile(savePath);
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                    shareIntent.setType("image/*");
                    startActivity(Intent.createChooser(shareIntent, "分享到"));
                }
                else {
                    new Thread(new MyThread()).start();
                    dialog.show();
                    dialog.setProgress(0);
                }
            }
        });

    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            if (isDone == 1) {
                try {
                    PlayerActivity.instance.finish();
                    MarkPictureActivity.instance.finish();
                    MainActivity.instance.finish();
                } catch (NullPointerException e){}
                Intent intent = new Intent(BuildPictureActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
            return false;
        }else {
            return super.onKeyDown(keyCode, event);
        }

    }

    private Bitmap getBitmap(String no) {
        Bitmap bm = null;
        String extension;
        if (settings.getBoolean("isShotToJpg", true)) {
            extension = "jpg";
        }
        else {
            extension = "png";
        }

        try {
            bm = tool.getBitmapFromFile(no, getExternalCacheDir(),extension);
        }  catch (Exception e) {
            //Toast.makeText(getApplicationContext(),"获取截图失败"+e, Toast.LENGTH_LONG).show();
            Log.e("EL", "获取截图失败："+e.toString());
        }

        return bm;
    }

    private Bitmap getCutImg() {
        for (int i=0;i<fileList.length;i++) {
            if (fileList[i].equals("cut")) {
                return getBitmap(i+"");
            }
        }
        return getBitmap(0+"");
    }

    private Handler handler = new Handler() {
        // 在Handler中获取消息，重写handleMessage()方法
        @Override
        public void handleMessage(Message msg) {
            // 判断消息码是否为1
            if (msg.what == 1) {
                dialog.setProgress(dialog.getProgress()+1);
                dialog.setMessage(msg.obj.toString());
            }
            else if (msg.what == 2) {
                dialog.dismiss();
                btn_up.setText("返回");
                btn_done.setText("分享");
                btn_down.setVisibility(View.INVISIBLE);
                isDone=1;
                String temp_path = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES)+"/"+msg.obj.toString();
                temp_path += settings.getBoolean("isReduce_switch", false) ? ".jpg":".png";
                MediaScannerConnection.scanFile(BuildPictureActivity.this, new String[]{temp_path}, null, null);
                Toast.makeText(getApplicationContext(),"处理完成！图片已保存至 "+ temp_path +" 请进入图库查看", Toast.LENGTH_LONG).show();
            }
            else if (msg.what == 3) {
                Boolean isShow = settings.getBoolean("isMonitoredShow", false);
                if (isShow) {
                    imageTest.setImageBitmap((Bitmap) msg.obj);
                }
                else {
                    DisplayMetrics dm = new DisplayMetrics();
                    dm = getResources().getDisplayMetrics();
                    int screenHeight = dm.heightPixels;

                    Bitmap bm = (Bitmap) msg.obj;
                    if (bm.getHeight() > screenHeight) {
                        Bitmap newbm = Bitmap.createBitmap(bm, 0, bm.getHeight()-screenHeight, bm.getWidth(), screenHeight);
                        imageTest.setImageBitmap(newbm);
                    }
                    else {
                        imageTest.setImageBitmap(bm);
                    }
                }
            }
            else if (msg.what == 4) {
                Toast.makeText(getApplicationContext(),msg.obj.toString(), Toast.LENGTH_LONG).show();
                dialog.dismiss();
                isDone = 1;
                btn_up.setText("退出");
                btn_down.setVisibility(View.GONE);
                btn_done.setVisibility(View.GONE);
            }
        }
    };

    private class MyThread implements Runnable {
        int delete_nums=0;
        @Override
        public void run() {
            Message msg;
            int len = fileList.length;
            Bitmap final_bitmap = Bitmap.createBitmap(bWidth,1, Bitmap.Config.ARGB_8888);
            for (int i=0;i<len;i++) {
                msg = Message.obtain();
                msg.obj = "处理第"+i+"张图片";
                msg.what = 1;
                handler.sendMessage(msg);
                if (fileList[i].equals("cut")) {
                    final_bitmap = addBitmap(final_bitmap,cutBimap(getBitmap(i+"")));
                    msg = Message.obtain();
                    msg.obj = final_bitmap;
                    msg.what = 3;
                    handler.sendMessage(msg);
                }
                else if (fileList[i].equals("all")) {
                    final_bitmap = addBitmap(final_bitmap,getBitmap(i+""));
                    msg = Message.obtain();
                    msg.obj = final_bitmap;
                    msg.what = 3;
                    handler.sendMessage(msg);
                }
                else if (fileList[i].equals("text")) {
                    final_bitmap = addBitmap(final_bitmap,getBitmap(i+"_t"));
                    msg = Message.obtain();
                    msg.obj = final_bitmap;
                    msg.what = 3;
                    handler.sendMessage(msg);
                }
                else {
                    delete_nums++;
                }
            }
            Boolean isAddWatermark = settings.getBoolean("isAddWatermark_switch",true);
            if (isAddWatermark) {
                Canvas canvas = new Canvas(final_bitmap);
                String watermark = settings.getString("watermark_text","Made by videoshot");
                TextPaint textPaint = new TextPaint();
                textPaint.setColor(Color.argb(80,150,150,150));
                textPaint.setTextSize(40);

                Paint.FontMetricsInt fmi = textPaint.getFontMetricsInt();
                int char_height = fmi.bottom-fmi.top;

                int watermarkPosition = Integer.parseInt(settings.getString("watermark_position_value", "1"));
                Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
                switch (watermarkPosition) {
                    case 1:
                        break;
                    case 2:
                        canvas.translate(0,final_bitmap.getHeight()/2);
                        align = Layout.Alignment.ALIGN_CENTER;
                        break;
                    case 3:
                        canvas.translate(0, final_bitmap.getHeight()-char_height);
                        break;
                }

                StaticLayout layout = new StaticLayout(watermark,textPaint,canvas.getWidth(), align,1.0F,0.0F,true);
                canvas.translate(5,0);
                layout.draw(canvas);
            }

            if (delete_nums >= len) {
                msg = Message.obtain();
                msg.obj = "你全部删除了我合成什么啊？？？";
                msg.what = 4;
                handler.sendMessage(msg);
            }

            else {
                msg = Message.obtain();
                msg.obj = "导出图片";
                msg.what = 1;
                handler.sendMessage(msg);
                final_bitmap = tool.addRight(final_bitmap);
                SimpleDateFormat sDateFormat    =   new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
                String date    =    sDateFormat.format(new    java.util.Date());
                try {
                    if(saveMyBitmap(final_bitmap,date+"-by_EL", settings.getBoolean("isReduce_switch", false))) {
                        msg = Message.obtain();
                        msg.obj = date+"-by_EL";
                        msg.what = 2;
                        handler.sendMessage(msg);
                    }
                } catch (IOException e) {
                    //Toast.makeText(getApplicationContext(),"保存截图失败"+e,Toast.LENGTH_LONG).show();
                    msg = Message.obtain();
                    msg.obj = "保存截图失败"+e;
                    msg.what = 4;
                    handler.sendMessage(msg);
                }
            }
        }
    }

    private Bitmap cutBimap(Bitmap bm) {
        //return Bitmap.createBitmap(bm, 0, (int)startY, bWidth, (int)(bm.getHeight()-startY));
        return tool.cutBimap(bm, (int)startY, bWidth);
    }

    private Bitmap addBitmap(Bitmap first, Bitmap second) {
        return tool.jointBitmap(first, second);
    }

    private boolean saveMyBitmap(Bitmap bmp, String bitName, boolean isReduce) throws IOException {
        boolean flag;
        try {
            if (isReduce) {
                int quality = Integer.parseInt(settings.getString("reduce_value","100"));
                savePath = tool.saveBitmap2png(bmp,bitName, Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), true, quality);
            }
            else {
                savePath = tool.saveBitmap2png(bmp,bitName, Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES));
            }
            flag = true;
        } catch (Exception e) {
            flag = false;
        }

        return flag;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i("cao", "In BuildPictureActivity onDestroy");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.i("cao", "In BuildPictureActivity onStop");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i("cao", "In BuildPictureActivity onPause");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i("cao", "In BuildPictureActivity onResume");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i("cao", "In BuildPictureActivity onStart");
    }

    @Override
    public void onRestart() {
        super.onRestart();
        Log.i("cao", "In BuildPictureActivity onRestart");
    }
}

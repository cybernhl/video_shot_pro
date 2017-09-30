package com.equationl.videoshotpro;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dingmouren.colorpicker.ColorPickerDialog;
import com.dingmouren.colorpicker.OnColorPickerListener;
import com.equationl.videoshotpro.Image.Tools;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class MarkPictureActivity extends AppCompatActivity {
    ImageView imagview, imageViewText;
    String[] fileList;
    Button btn_start;
    Button start_color_picker, start_color_picker_bg;
    int pic_num,pic_no=0,flag=0;
    AlertDialog.Builder builder;
    private LayoutInflater mLayoutInflater;
    private View view;
    SharedPreferences settings;
    TextView tip_text, nums_tip_text;
    boolean isLongPress=false;
    Boolean isFromExtra;
    ProgressDialog dialog;
    Resources res;
    int text_color=Color.BLACK, bg_color = Color.WHITE;
    boolean isMoveText=false;

    Tools tool = new Tools();

    private static final int HandlerStatusHideTipText = 10010;
    private static final int HandlerStatusLongIsWorking = 10011;
    private static final int HandlerStatusIsLongPress = 10012;
    private static final int HandlerCheckImgSaveFail = 10013;
    private static final int HandlerStatusProgressRunning = 10014;
    private static final int HandlerStatusProgressDone = 10015;
    private static final int HandlerStatusGetImgFail = 10016;


    public static MarkPictureActivity instance = null;   //FIXME  暂时这样吧，实在找不到更好的办法了

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        //4.4以上设置透明状态栏
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }

        setContentView(R.layout.activity_mark_picture);

        instance = this;

        imagview = (ImageView) findViewById(R.id.imageView);
        imageViewText = (ImageView) findViewById(R.id.imageViewText);
        btn_start = (Button) findViewById(R.id.button_start);
        tip_text = (TextView) findViewById(R.id.make_picture_tip);
        nums_tip_text  = (TextView) findViewById(R.id.make_picture_nums_tip);

        settings = PreferenceManager.getDefaultSharedPreferences(this);
        res = getResources();

        final File filepath = new File(getExternalCacheDir().toString());
        fileList = filepath.list();
        pic_num = fileList.length;

        isFromExtra = this.getIntent().getBooleanExtra("isFromExtra", false);
        if (isFromExtra) {
            Intent service = new Intent(MarkPictureActivity.this, FloatWindowsService.class);
            stopService(service);

            dialog = new ProgressDialog(this);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setIndeterminate(false);
            dialog.setCancelable(false);
            dialog.setMessage(res.getString(R.string.markPicture_ProgressDialog_msg));
            dialog.setTitle(res.getString(R.string.markPicture_ProgressDialog_title));
            dialog.setMax(fileList.length);
            dialog.show();
            dialog.setProgress(0);
            new Thread(new MyThread()).start();
        }

        if (pic_num < 1) {
            Toast.makeText(this, R.string.markPicture_toast_readFile_fail, Toast.LENGTH_SHORT).show();
            finish();
        }

        nums_tip_text.setText("0/"+pic_num);

        Log.i("cao",pic_num+"");

        for (int i=0;i<pic_num;i++) {
            fileList[i] = "del";
        }

        btn_start.setVisibility(View.GONE);



        flag=0;
        imagview.setOnTouchListener(new View.OnTouchListener() {
            double  mPosX,mPosY,mCurPosX,mCurPosY,offsetX,offsetY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (flag == 0) {
                    Log.i("el", "第一次调用：pic_no="+pic_no);
                    set_image(pic_no);
                    flag++;
                    btn_start.setVisibility(View.VISIBLE);
                    imagview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    return false;
                }
                else {
                    Log.i("el", "非第一次调用：pic_no="+pic_no);
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            mPosX = event.getX();
                            mPosY = event.getY();
                            /*checkIsLongPress(false);
                            Log.i("test", "in ACTION_DOWN");  */
                            withdrawStep(mPosX, mPosY);
                            break;
                        case MotionEvent.ACTION_MOVE:
                            mCurPosX = event.getX();
                            mCurPosY = event.getY();
                            offsetX = mPosX - mCurPosX;
                            offsetY = mPosY - mCurPosY;
                            /*if (Math.abs(offsetX)<400 && Math.abs(offsetY)<400) {
                                checkIsLongPress(true);
                            }*/
                            if (isMoveText) {
                                int moveX = //(int)(-offsetX+imageViewText.getPaddingLeft());
                                        (int)mCurPosX;
                                int moveY = //(int)(offsetY+imageViewText.getPaddingTop());
                                        (int)mCurPosY;
                                int bgRealSize[] = getImageRealSize(imagview);
                                int bgRealX =  bgRealSize[0];
                                int bgRealY =  bgRealSize[1];
                                int relativeX = moveX-(imagview.getWidth()-bgRealX)/2;
                                int relativeY = moveY-(imagview.getHeight()-bgRealY)/2;
                                Log.i("EL", relativeX+" "+relativeY);
                                Bitmap bmTemp = getBitmapFromFile(pic_no+"");
                                Log.i("EL", moveX+" "+bmTemp.getWidth()+" "+moveY+" "+bmTemp.getHeight());
                                if (relativeY<bgRealY && relativeY>0) {
                                    Log.i("EL", "call");
                                    imageViewText.setPadding(moveX
                                            ,moveY
                                            ,imageViewText.getPaddingRight()
                                            ,imageViewText.getPaddingBottom());
                                }
                                /*if (imageViewText.getPaddingTop()<imagview.getHeight()) {
                                    imageViewText.setPadding(imageViewText.getPaddingTop()
                                            ,moveY
                                            ,imageViewText.getPaddingRight()
                                            ,imageViewText.getPaddingBottom());
                                }
                                if (imageViewText.getPaddingLeft()<imagview.getWidth()) {
                                    imageViewText.setPadding(moveX
                                            ,imageViewText.getPaddingLeft()
                                            ,imageViewText.getPaddingRight()
                                            ,imageViewText.getPaddingBottom());
                                }*/

                                //Log.i("EL", imageViewText.getPaddingLeft()+" "+imageViewText.getPaddingTop() + " " + offsetX);
                                break;
                            }
                            break;
                        case MotionEvent.ACTION_SCROLL:

                        case MotionEvent.ACTION_UP:
                            if (pic_no >= pic_num) {
                                Toast.makeText(getApplicationContext(),"已是最后一张，请点击右上角“开始合成”", Toast.LENGTH_SHORT).show();
                                break;
                            }

                            if (isMoveText) {
                                break;
                            }

                            offsetX = mPosX - mCurPosX;
                            offsetY = mPosY - mCurPosY;
                            if (Math.abs(offsetY) >= Math.abs(offsetX) ) {
                                Log.i("pic num", pic_num+"");
                                Log.i("pic no", pic_no+"");
                                if (mCurPosY - mPosY > 0
                                        && (Math.abs(mCurPosY - mPosY) > 200)) {
                                    //向下滑动
                                    //Toast.makeText(getApplicationContext(),"向下滑动",Toast.LENGTH_SHORT).show();
                                    if (fileList[pic_no].equals("text")) {
                                        Toast.makeText(MarkPictureActivity.this, "添加文字后不允许裁切！", Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                    tip_text.setText("裁切");
                                    tip_text.setVisibility(View.VISIBLE);
                                    autoHideText();
                                    nums_tip_text.setText((pic_no+1+"/")+pic_num);
                                    set_image(pic_no+1);
                                    fileList[pic_no] = "cut";
                                    pic_no++;
                                }
                                else if (mCurPosY - mPosY < 0
                                        && (Math.abs(mCurPosY - mPosY) > 200)) {
                                    //向上滑动
                                    //Toast.makeText(getApplicationContext(),"向上滑动",Toast.LENGTH_SHORT).show();
                                    tip_text.setText("全图");
                                    tip_text.setVisibility(View.VISIBLE);
                                    autoHideText();
                                    nums_tip_text.setText((pic_no+1+"/")+pic_num);
                                    set_image(pic_no+1);
                                    if (!fileList[pic_no].equals("text")) {
                                        fileList[pic_no] = "all";
                                    }
                                    pic_no++;
                                }
                            }
                            else {
                                if (mCurPosX - mPosX < 0
                                        && (Math.abs(mCurPosX - mPosX) > 200)) {
                                    //向左滑动
                                    slideToLeft();
                                }
                                else if (mCurPosX - mPosX > 0
                                        && (Math.abs(mCurPosX - mPosX) > 200)) {
                                    //向右滑动
                                    tip_text.setText("删除");
                                    tip_text.setVisibility(View.VISIBLE);
                                    autoHideText();
                                    nums_tip_text.setText((pic_no+1+"/")+pic_num);
                                    set_image(pic_no+1);
                                    pic_no++;
                                }
                            }
                            break;
                    }
                }
                return true;
            }

        });

        btn_start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isMoveText) {
                    //TODO 文字移动完成后
                    return;
                }
                if (pic_no <= 0) {
                    Toast.makeText(getApplicationContext(),"至少需要选择一张图片", Toast.LENGTH_LONG).show();
                }
                else {
                    Intent intent = new Intent(MarkPictureActivity.this, BuildPictureActivity.class);
                    Bundle bundle = new Bundle();
                    bundle.putStringArray("fileList",fileList);
                    bundle.putBoolean("isFromExtra", isFromExtra);
                    intent.putExtras(bundle);
                    startActivity(intent);
                }
            }
        });
    }

    private void set_image(int  no) {
        set_image(no, "null");
    }

    private void set_image(int no, String flag) {
        if (no < pic_num) {
            if (!flag.equals("null")) {
                imagview.setImageBitmap(getBitmapFromFile(no+flag));
            }
            else {
                imagview.setImageBitmap(getBitmapFromFile(no+""));
            }
        }
    }

    private Bitmap getBitmapFromFile(String no) {
        Bitmap bm = null;
        String extension = settings.getBoolean("isShotToJpg", true) ? "jpg":"png";

        try {
            bm = tool.getBitmapFromFile(no, getExternalCacheDir(),extension);
        }  catch (Exception e) {
            Message msg;
            msg = Message.obtain();
            msg.obj = e;
            msg.what = HandlerStatusGetImgFail;
            handler.sendMessage(msg);
        }

        return bm;
    }

    private Bitmap addTextToImage(Bitmap bm, String text, int size) {
        if (size < 0) {
            size = 30;
        }
        int width = bm.getWidth();

        TextPaint textPaint = new TextPaint();
        textPaint.setColor(text_color);
        textPaint.setTextSize(size);

        Paint.FontMetricsInt fmi = textPaint.getFontMetricsInt();

        int char_height = fmi.bottom-fmi.top;

        Log.i("text", char_height+" "+fmi.bottom +" " +fmi.top);

        String[] len = text.split("\n");
        int t_height=0;
        for (int i=0;i<len.length;i++) {
            t_height+=char_height;
            int string_wdith = (int)textPaint.measureText(len[i]);
            if (string_wdith > width) {
                t_height+=char_height*(string_wdith/width);
            }
        }

        Bitmap result = Bitmap.createBitmap(width,t_height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setColor(bg_color);
        canvas.drawRect(0, 0, width, bm.getHeight(), paint);

        StaticLayout layout = new StaticLayout(text,textPaint,canvas.getWidth(), Layout.Alignment.ALIGN_NORMAL,1.0F,0.0F,true);
        canvas.translate(5,0);
        layout.draw(canvas);
        return result;
    }

    private Bitmap addBitmap(Bitmap first, Bitmap second) {
        return tool.jointBitmap(first, second);
    }

    public boolean saveMyBitmap(Bitmap bmp, String bitName) throws IOException {
        boolean flag;
        try {
            if (settings.getBoolean("isShotToJpg", true)) {
                tool.saveBitmap2png(bmp,bitName, getExternalCacheDir(), true, 100);
                flag = true;
            }
            else {
                tool.saveBitmap2png(bmp,bitName, getExternalCacheDir());
                flag = true;
            }
        } catch (Exception e) {
            flag = false;
        }

        return flag;
    }

    Timer tHide = null;
    private void autoHideText() {
        if (tHide == null) {
            Log.i("test","call in autoHideTime with tHide is null");
            tHide = new Timer();
            tHide.schedule(new TimerTask() {
                @Override
                public void run() {
                    handler.sendEmptyMessage(HandlerStatusHideTipText);
                    tHide = null;
                }
            }, 1000);
        }
    }

    long callWithdrawTime = 0;
    double x_l, y_l;
    private void withdrawStep(double x, double y) {
        long time_now = System.currentTimeMillis();
        long delay = time_now-callWithdrawTime;
        double x_d = Math.abs(x-x_l);
        double y_d = Math.abs(y-y_l);

        if (delay<500 && x_d<20 && y_d<20 && (pic_no>0
                || fileList[pic_no].equals("text"))) {
            handler.sendEmptyMessage(HandlerStatusLongIsWorking);
        }
        callWithdrawTime = time_now;
        x_l = x;
        y_l = y;
    }



    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HandlerStatusHideTipText:
                    tip_text.setVisibility(View.GONE);
                    break;
                case HandlerStatusLongIsWorking:
                    tip_text.setText("撤销");
                    tip_text.setVisibility(View.VISIBLE);
                    autoHideText();
                    if (pic_no<pic_num && fileList[pic_no].equals("text")) {
                        set_image(pic_no);
                        fileList[pic_no] = "del";
                    }
                    else {
                        nums_tip_text.setText((pic_no+"/")+pic_num);
                        set_image(pic_no-1);
                        fileList[pic_no-1] = "del";
                        pic_no--;
                    }
                    break;
                case HandlerStatusIsLongPress:
                    isLongPress = false;
                    break;
                case HandlerCheckImgSaveFail:
                    Toast.makeText(MarkPictureActivity.this, "保存图片失败："+msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case HandlerStatusProgressRunning:
                    dialog.setProgress(dialog.getProgress()+1);
                    dialog.setMessage(msg.obj.toString());
                    break;
                case HandlerStatusProgressDone:
                    tool.MakeCacheToStandard(MarkPictureActivity.this);
                    dialog.dismiss();
                    break;
                case HandlerStatusGetImgFail:
                    Toast.makeText(MarkPictureActivity.this, "获取图片失败："+msg.obj, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private class MyThread implements Runnable {
        @Override
        public void run() {
            Message msg;
            int i = 0;
            File path = new File(getExternalCacheDir().toString());
            String extension = settings.getBoolean("isShotToJpg", true) ? "jpg":"png";
            String [] files = path.list();
            String name;
            Bitmap bitmap;

            for (String file:files) {
                msg = Message.obtain();
                msg.obj = String.format(res.getString(R.string.markPicture_ProgressDialog_msgOnRunning),
                        ""+(i+1));
                msg.what = HandlerStatusProgressRunning;
                handler.sendMessage(msg);

                if (file.contains("_")) {
                    name = file.split("_")[0];
                }
                else {
                    name = file.split("\\.")[0];
                }
                Log.i("cao", "file= "+file);
                Log.i("cao", "name= "+name);
                bitmap = tool.removeImgBlackSide(getBitmapFromFile(file.split("\\.")[0]));
                try {
                    saveMyBitmap(bitmap, name);
                } catch (IOException e) {
                    msg = Message.obtain();
                    msg.obj = e;
                    msg.what = HandlerCheckImgSaveFail;
                    handler.sendMessage(msg);
                }
                i++;
            }
            handler.sendEmptyMessage(HandlerStatusProgressDone);
        }
    }

    private void clickAddTextOkBtn() {
        btn_start.setText("确定");
        isMoveText = true;

        EditText edit_text = (EditText) view.findViewById(R.id.input_text);
        EditText edit_size = (EditText) view.findViewById(R.id.input_size);
        String text = edit_text.getText().toString();
        if (text.equals("")) {
            return;
        }
        int text_size;
        if (edit_size.getText().toString().equals("")) {
            text_size = 30;
        }
        else {
            text_size = Integer.parseInt(edit_size.getText().toString());
        }

        //Log.i("ccccc",text);
        Bitmap bm;
        if (pic_no<pic_num && fileList[pic_no].equals("text")) {
            bm = addBitmap(getBitmapFromFile(pic_no+"_t"),addTextToImage(getBitmapFromFile(pic_no+"_t"),text,text_size));
        }
        else {
            bm = addBitmap(getBitmapFromFile(pic_no+""),addTextToImage(getBitmapFromFile(pic_no+""),text,text_size));
        }
        try {
            saveMyBitmap(bm,pic_no+"_t");
        }
        catch (IOException e) {
            Log.i("excuse me?",e.toString());
            Toast.makeText(getApplicationContext(),"写入缓存失败！"+e.toString(), Toast.LENGTH_LONG).show();
        }
        set_image(pic_no, "_t");
        fileList[pic_no] = "text";
    }

    private void slideToLeft() {
        nums_tip_text.setText((pic_no+1+"/")+pic_num);
        DialogInterface.OnClickListener dialogOnclicListener=new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch(which){
                    case Dialog.BUTTON_POSITIVE:
                        clickAddTextOkBtn();
                        break;
                    case Dialog.BUTTON_NEGATIVE:
                        break;
                }
            }
        };
        mLayoutInflater= LayoutInflater.from(MarkPictureActivity.this);
        view=mLayoutInflater.inflate(R.layout.dialog_mark_picture, null, false);
        builder = new AlertDialog.Builder(MarkPictureActivity.this);
        builder.setTitle("请输入要添加的文字")
                .setView(view)
                .setPositiveButton("确定",dialogOnclicListener)
                .setNegativeButton("取消", dialogOnclicListener)
                .setCancelable(false)
                .create();
        builder.show();
        start_color_picker = (Button) view.findViewById(R.id.mark_dialog_chooseColor_btn);
        start_color_picker_bg = (Button) view.findViewById(R.id.mark_dialog_chooseColorBg_btn);
        start_color_picker.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ColorPickerDialog mColorPickerDialog = new ColorPickerDialog(
                        MarkPictureActivity.this,
                        Color.BLACK,
                        false,
                        mOnColorPickerListener
                ).show();
            }
        });
        start_color_picker_bg.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ColorPickerDialog mColorPickerDialog = new ColorPickerDialog(
                        MarkPictureActivity.this,
                        Color.WHITE,
                        false,
                        mOnColorPickerBgListener
                ).show();
            }
        });
    }

    private OnColorPickerListener mOnColorPickerListener = new OnColorPickerListener() {
        @Override
        public void onColorCancel(ColorPickerDialog dialog) {//取消选择的颜色

        }

        @Override
        public void onColorChange(ColorPickerDialog dialog, int color) {//实时监听颜色变化

        }

        @Override
        public void onColorConfirm(ColorPickerDialog dialog, int color) {//确定的颜色
            text_color = color;
            start_color_picker.setBackgroundColor(color);
            start_color_picker.setTextColor(tool.getInverseColor(color));
        }
    };

    private OnColorPickerListener mOnColorPickerBgListener = new OnColorPickerListener() {
        @Override
        public void onColorCancel(ColorPickerDialog dialog) {//取消选择的颜色

        }

        @Override
        public void onColorChange(ColorPickerDialog dialog, int color) {//实时监听颜色变化

        }

        @Override
        public void onColorConfirm(ColorPickerDialog dialog, int color) {//确定的颜色
            bg_color = color;
            start_color_picker_bg.setBackgroundColor(color);
            start_color_picker_bg.setTextColor(tool.getInverseColor(color));
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            dialog.dismiss();
        } catch (NullPointerException e){}
    }

    private int[] getImageRealSize(ImageView imageview) {
        int realImgShowWidth=0;
        int realImgShowHeight=0;
        Drawable imgDrawable = imageview.getDrawable();
        if (imgDrawable != null) {
            //获得ImageView中Image的真实宽高，
            int dw = imageview.getDrawable().getBounds().width();
            int dh = imageview.getDrawable().getBounds().height();

            //获得ImageView中Image的变换矩阵
            Matrix m = imageview.getImageMatrix();
            float[] values = new float[10];
            m.getValues(values);

            //Image在绘制过程中的变换矩阵，从中获得x和y方向的缩放系数
            float sx = values[0];
            float sy = values[4];

            //计算Image在屏幕上实际绘制的宽高
            realImgShowWidth = (int) (dw * sx);
            realImgShowHeight = (int) (dh * sy);
        }
        int size[] = {realImgShowWidth, realImgShowHeight};
        return size;
    }
}

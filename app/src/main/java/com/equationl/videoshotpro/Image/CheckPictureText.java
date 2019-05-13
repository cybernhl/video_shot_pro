package com.equationl.videoshotpro.Image;


import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.util.ArrayList;

public class CheckPictureText {
    public final int StateCutPicture = 1000;
    public final int StateDelPicture = 1001;


    /**
     * 二值化阀值
    * */
    private final static int BinaryzationT = 200;

    /**
    * 对比两张截图是否在同一台词时的容差
    * */
    private final static float isSameSubtitleTolerance = 0.6f;

    /**
    * 字幕高度
    * */
    private int subtitleHeight = 0;

    private String lastText = null;

    private TessBaseAPI tessBaseAPI;

    private final static  String TAG = "el, in CPT";

    Tools tool = new Tools();
    int i = 0;
    Context context2;

    public CheckPictureText(Context context) throws Exception{
        initTess(context);
        context2 = context;
    }

    /**
    * 获取最终的字幕高度
    * */
    public int getSubtitleHeight() {
        return subtitleHeight;
    }

    /**
     * 是否是非重复的有字幕图片
     * @param bitmap 欲检测的图片(原图)
     * */
    public int isSingleSubtitlePicture(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (subtitleHeight == 0) {
            subtitleHeight = (int)(bitmap.getHeight() * 0.7);
        }

        Log.i(TAG, "字幕高度为"+subtitleHeight);

        //预裁剪，减少遍历数
        //bitmap = Bitmap.createBitmap(bitmap, (int)(width*0.35), subtitleHeight, width-(int)(width*0.35)*2, height-subtitleHeight);
        bitmap = Bitmap.createBitmap(bitmap, 0, subtitleHeight, width, height-subtitleHeight);
        bitmap = getBinaryzationPicture(bitmap);

        //**************DEBUG*******************************************************************
        try{
            tool.saveBitmap2File(bitmap, i+"", context2.getExternalFilesDir("test"), false, 100);
            i++;
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        //**************END DEBUG***************************************************************

        Log.i(TAG, "origin bitmap width="+bitmap.getWidth()+", height="+bitmap.getHeight());

        String text = getOCRString(bitmap);
        Log.i(TAG, "识别到文字："+text);
        int charNum = 0;
        if (text.contains("_")) {
            charNum = text.length() - text.replaceAll("_", "").length();
        }
        if (text.equals("") || charNum > 2) {
            Log.i(TAG, "不是字幕因为没有文字");
            lastText = text;
            return StateDelPicture;
        }
        else if (lastText != null) {
            float similarity = levenshtein(text, lastText);
            Log.i(TAG, "similarity="+similarity);
            if (similarity > isSameSubtitleTolerance) {
                Log.i(TAG, "不是字幕因为两张图片一样！");
                lastText = text;
                return StateDelPicture;
            }
        }

        Log.i(TAG, "是非重复的字幕");
        lastText = text;
        return StateCutPicture;
    }

    private String getOCRString(Bitmap bitmap) {
        tessBaseAPI.setImage(bitmap);
        String text_o = tessBaseAPI.getUTF8Text();
        ArrayList<Rect> wordRects = tessBaseAPI.getRegions().getBoxRects();
        for (Rect rect : wordRects) {
            Log.i(TAG, "word rect, top="+rect.top+", bottom="+rect.bottom+", right="+rect.right+", left="+rect.left);
        }
        Log.i(TAG, "origin text="+text_o);
        text_o = text_o.replaceAll(" ", "");   //FIXME 避免双语字幕时空格过多影响测试字幕高度
        if (text_o.contains("\n")) {
            String texts[] = text_o.split("\n");
            if (texts[1].length() -
                    texts[1].replaceAll("[^\u4e00-\u9fa5]", "").length()
                    > 2) {
                Log.i(TAG, "检测到双语字幕，剔除非中文");
                text_o = texts[0];
            }
        }
        String text = text_o.replaceAll("[^a-zA-Z_\u4e00-\u9fa5]", "");
        int charNum = text_o.length() - text.length();
        if (charNum < 3 && !text.equals("")) {
            try {
                subtitleHeight = subtitleHeight+wordRects.get(0).top-5;
                Log.i(TAG, "改变字幕高度为："+subtitleHeight);
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
        return text;
    }

    private void initTess(Context context) throws Exception {
        String PATH;
        //noinspection ConstantConditions
        PATH = context.getExternalFilesDir(null).getPath();
        String DEFAULT_LANGUAGE = "chi_sim";
        tessBaseAPI = new TessBaseAPI();
        if (!tessBaseAPI.init(PATH+ File.separator, DEFAULT_LANGUAGE)) {
            Log.e(TAG, "init tess fail");
            throw new Exception("init tess fail");
        }
    }

    /**
    * 二值化bitmap（固定阀值法）
     * source: https://blog.csdn.net/huangxin388/article/details/80229103
    * */
    private Bitmap getBinaryzationPicture(Bitmap bitmap) {
        //得到图形的宽度和长度
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        //创建二值化图像
        Bitmap binarymap;
        binarymap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        //依次循环，对图像的像素进行处理
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                //得到当前像素的值
                int col = binarymap.getPixel(i, j);
                //得到alpha通道的值
                int alpha = col & 0xFF000000;
                //得到图像的像素RGB的值
                int red = (col & 0x00FF0000) >> 16;
                int green = (col & 0x0000FF00) >> 8;
                int blue = (col & 0x000000FF);
                // 用公式X = 0.3×R+0.59×G+0.11×B计算出X代替原来的RGB
                int gray = (int) ((float) red * 0.3 + (float) green * 0.59 + (float) blue * 0.11);
                //对图像进行二值化处理
                if (gray <= BinaryzationT) {
                    gray = 0;
                } else {
                    gray = 255;
                }
                // 新的ARGB
                int newColor = alpha | (gray << 16) | (gray << 8) | gray;
                //设置新图像的当前像素值
                binarymap.setPixel(i, j, newColor);
            }
        }
        return binarymap;
    }


    /*
    * 计算字符串相似值
    * from: https://wdhdmx.iteye.com/blog/1343856
    * */
    private static float levenshtein(String str1,String str2) {
        int len1 = str1.length();
        int len2 = str2.length();
        int[][] dif = new int[len1 + 1][len2 + 1];
        for (int a = 0; a <= len1; a++) {
            dif[a][0] = a;
        }
        for (int a = 0; a <= len2; a++) {
            dif[0][a] = a;
        }
        int temp;
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (str1.charAt(i - 1) == str2.charAt(j - 1)) {
                    temp = 0;
                } else {
                    temp = 1;
                }
                dif[i][j] = min(dif[i - 1][j - 1] + temp, dif[i][j - 1] + 1,
                        dif[i - 1][j] + 1);
            }
        }
        return 1 - (float) dif[len1][len2] / Math.max(str1.length(), str2.length());
    }

    private static int min(int... is) {
        int min = Integer.MAX_VALUE;
        for (int i : is) {
            if (min > i) {
                min = i;
            }
        }
        return min;
    }

}

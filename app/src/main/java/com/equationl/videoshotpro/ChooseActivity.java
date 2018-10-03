package com.equationl.videoshotpro;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.dingmouren.colorpicker.ColorPickerDialog;
import com.equationl.videoshotpro.Image.Tools;
import com.equationl.videoshotpro.utils.Utils;
import com.github.ielse.imagewatcher.ImageWatcher;
import com.github.ielse.imagewatcher.ImageWatcherHelper;
import com.huxq17.handygridview.HandyGridView;
import com.huxq17.handygridview.listener.OnItemCapturedListener;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import me.toptas.fancyshowcase.FancyShowCaseQueue;
import me.toptas.fancyshowcase.FancyShowCaseView;

public class ChooseActivity extends AppCompatActivity {

    HandyGridView gridView;
    List<Bitmap> images = new ArrayList<>();
    List<String> imagePaths = new ArrayList<>();
    String[] files;
    ProgressDialog dialog;
    Resources res;
    Tools tool = new Tools();
    LayoutInflater mLayoutInflater;
    View view;
    AlertDialog.Builder builder;
    ChoosePictureAdapter pictureAdapter;
    SharedPreferences sp_init;
    Boolean isFromExtra;
    boolean isEditMode = false;

    ImageWatcher vImageWatcher;
    ImageWatcher.OnPictureLongPressListener mOnPictureLongPressListener;


    private final MyHandler handler = new MyHandler(this);

    private final static String TAG = "EL,In ChooseActivity";

    private final static int HandlerStatusLoadImageNext = 1000;
    private final static int HandlerStatusLoadImageDone = 1001;

    public static ChooseActivity instance = null;   //FIXME  暂时这样吧，实在找不到更好的办法了

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose);

        gridView=(HandyGridView) findViewById(R.id.choosePicture_handyGridView);

        res = getResources();

        instance = this;

        sp_init = getSharedPreferences("init", Context.MODE_PRIVATE);

        isFromExtra = this.getIntent().getBooleanExtra("isFromExtra", false);
        if (isFromExtra) {
            Intent service = new Intent(ChooseActivity.this, FloatWindowsService.class);
            stopService(service);
        }


        String filepath = getExternalCacheDir().toString();
        files = tool.getFileOrderByName(filepath);

        dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setIndeterminate(false);
        dialog.setCancelable(false);
        dialog.setMessage(res.getString(R.string.chooseActivity_ProgressDialog_loadImage_content));
        dialog.setTitle(res.getString(R.string.chooseActivity_ProgressDialog_loadImage_title));
        dialog.setMax(files.length);
        dialog.show();
        dialog.setProgress(0);
        new Thread(new LoadImageThread()).start();

        initPictureWathcher();

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //showPictureDialog(position);
                //ChoosePictureAdapter.ViewHolder viewHolder = (ChoosePictureAdapter.ViewHolder) pictureAdapter.getView(position, view, parent).getTag();
                ImageView imageview = (ImageView) pictureAdapter.getView(position, view, parent);
                SparseArray<ImageView> imageGroupList = new SparseArray<>();
                imageGroupList.put(0, imageview);
                imagePaths = pictureAdapter.getImagePaths();
                String path = getExternalCacheDir().toString();
                String file = path+"/"+imagePaths.get(position);
                new FancyShowCaseView.Builder(ChooseActivity.this)
                        .focusOn(imageview)
                        .title(res.getString(R.string.choosePicture_guideView_clickImage))
                        .showOnce("choose_clickImage")
                        .build()
                        .show();
                vImageWatcher.show(imageview, imageGroupList, Collections.singletonList(Uri.parse(file)));
            }
        });

        gridView.setOnItemCapturedListener(new OnItemCapturedListener() {
            @Override
            public void onItemCaptured(View v, int position) {
                v.setScaleX(1.2f);
                v.setScaleY(1.2f);
            }

            @Override
            public void onItemReleased(View v, int position) {
                v.setScaleX(1f);
                v.setScaleY(1f);
            }
        });


        //showGuideDialog();
    }


    private class LoadImageThread implements Runnable {
        @Override
        public void run() {
            String path = getExternalCacheDir().toString();
            for (int i = 0; i < files.length; i++) {
                Bitmap bitmap = tool.getBitmapThumbnailFromFile(path+"/"+files[i], 128, 160);
                if (bitmap == null) {
                    bitmap = tool.drawableToBitmap(R.drawable.load_image_fail, ChooseActivity.this);
                }
                images.add(bitmap);
                handler.sendEmptyMessage(HandlerStatusLoadImageNext);
            }
            handler.sendEmptyMessage(HandlerStatusLoadImageDone);
        }
    }

    private static class MyHandler extends Handler {
        private final WeakReference<ChooseActivity> mActivity;

        private MyHandler(ChooseActivity activity) {
            mActivity = new WeakReference<ChooseActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ChooseActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case HandlerStatusLoadImageNext:
                        activity.dialog.setProgress(activity.dialog.getProgress()+1);
                        break;
                    case HandlerStatusLoadImageDone:
                        activity.pictureAdapter = new ChoosePictureAdapter(activity.images, activity.files, activity);
                        activity.gridView.setAdapter(activity.pictureAdapter);
                        activity.gridView.setMode(HandyGridView.MODE.LONG_PRESS);
                        activity.gridView.setAutoOptimize(false);
                        activity.gridView.setScrollSpeed(750);
                        activity.dialog.dismiss();
                        if (activity.sp_init.getBoolean("isFirstUseSortPicture", true)) {
                            activity.showGuideDialog();
                            SharedPreferences.Editor editor = activity.sp_init.edit();
                            editor.putBoolean("isFirstUseSortPicture", false);
                            editor.apply();
                        }
                        break;
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_choose_picture, menu);
        new Handler().postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        final FancyShowCaseView fancyShowCaseView1 = new FancyShowCaseView.Builder(ChooseActivity.this)
                                .focusOn(findViewById(R.id.choosePicture_menu_edit))
                                .title(res.getString(R.string.choosePicture_guideView_edit))
                                .showOnce("choose_edit")
                                .build();
                        final FancyShowCaseView fancyShowCaseView2 = new FancyShowCaseView.Builder(ChooseActivity.this)
                                .title(res.getString(R.string.choosePicture_guideView_editSummary))
                                .showOnce("choose_editSummary")
                                .build();
                        final FancyShowCaseView fancyShowCaseView3 = new FancyShowCaseView.Builder(ChooseActivity.this)
                                .focusOn(findViewById(R.id.choosePicture_menu_done))
                                .title(res.getString(R.string.choosePicture_guideView_done))
                                .showOnce("choose_done")
                                .build();
                        FancyShowCaseQueue mQueue = new FancyShowCaseQueue()
                                .add(fancyShowCaseView1)
                                .add(fancyShowCaseView2)
                                .add(fancyShowCaseView3);

                        mQueue.show();
                    }
                }, 50
        );
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isEditMode) {
            menu.findItem(R.id.choosePicture_menu_edit).setIcon(
                    R.drawable.square_edit_outline_blank);
        } else {
            menu.findItem(R.id.choosePicture_menu_edit).setIcon(R.drawable.square_edit_outline);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()) {
            case R.id.choosePicture_menu_done:
                imagePaths = pictureAdapter.getImagePaths();
                tool.sortCachePicture(imagePaths, this);
                Intent intent = new Intent(ChooseActivity.this, MarkPictureActivity2.class);   //FIXME
                if (isFromExtra) {
                    intent.putExtra("isFromExtra", true);
                }
                startActivity(intent);
                break;
            case R.id.choosePicture_menu_edit:
                if (pictureAdapter.inEditMode) {
                    isEditMode = false;
                    pictureAdapter.setInEditMode(false);
                }
                else {
                    isEditMode = true;
                    pictureAdapter.setInEditMode(true);
                }
                invalidateOptionsMenu();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

   /* private void showPictureDialog(int position) {
        imagePaths = pictureAdapter.getImagePaths();
        //Log.i(TAG, imagePaths.toString());
        String path = getExternalCacheDir().toString();
        Bitmap bitmap = tool.getBitmapFromFile(path+"/"+imagePaths.get(position));
        if (bitmap == null) {
            bitmap = tool.drawableToBitmap(R.drawable.load_image_fail, ChooseActivity.this);
        }
        DialogInterface.OnClickListener dialogOnclicListener=new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch(which){
                    case Dialog.BUTTON_POSITIVE:
                        break;
                }
            }
        };
        mLayoutInflater= LayoutInflater.from(ChooseActivity.this);
        view=mLayoutInflater.inflate(R.layout.dialog_show_picture, null, false);
        ImageView imageView = (ImageView) view.findViewById(R.id.showPicture_image);
        imageView.setImageBitmap(bitmap);
        builder = new AlertDialog.Builder(ChooseActivity.this);
        builder.setView(view)
                .setPositiveButton("确定",dialogOnclicListener)
                .setCancelable(true)
                .create();
        builder.show();
    }   */

    private void showGuideDialog() {
        /*Dialog dialog = new AlertDialog.Builder(this).setCancelable(false)
                .setTitle(R.string.choosePicture_tip_dialog_title)
                .setMessage(R.string.choosePicture_tip_dialog_content)
                .setPositiveButton(res.getString(R.string.choosePicture_tip_dialog_btn_ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).create();
        dialog.show();   */
        /*final FancyShowCaseView fancyShowCaseView1 = new FancyShowCaseView.Builder(this)
                .focusOn(findViewById(R.id.main_guide_pos))
                .title("Focus on View")
                //.showOnce("fancy1")
                .build();
        final FancyShowCaseView fancyShowCaseView2 = new FancyShowCaseView.Builder(this)
                .focusOn(findViewById(R.id.main_recyclerView))
                .title("Focus on View")
                .roundRectRadius(100)
                //.showOnce("fancy1")
                .build();
        FancyShowCaseQueue mQueue = new FancyShowCaseQueue()
                .add(fancyShowCaseView1)
                .add(fancyShowCaseView2);

        mQueue.show();   */
    }

    private void initPictureWathcher() {
        mOnPictureLongPressListener = new ImageWatcher.OnPictureLongPressListener() {
            @Override
            public void onPictureLongPress(ImageView v, final Uri url, final int pos) {
                //Toast.makeText(MainActivity.this, "call long press:"+url, Toast.LENGTH_SHORT).show();
                String[] items;
                //Log.i(TAG, "in longPress path= "+new File(url.toString()).getParent()+" RootPath= "+tool.getSaveRootPath());
                items = new String[] {"保存"};
                AlertDialog.Builder builder = new AlertDialog.Builder(ChooseActivity.this);
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i) {
                            case 0:
                                SimpleDateFormat sDateFormat    =   new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
                                String date    =    sDateFormat.format(new    java.util.Date());
                                date += "-by_EL."+url.toString().substring(url.toString().lastIndexOf(".") + 1);
                                String savePath =  tool.getSaveRootPath() + "/" + date;
                                try {
                                    tool.copyFile(new File(url.toString()), new File(savePath));
                                    MediaScannerConnection.scanFile(ChooseActivity.this, new String[]{savePath}, null, null);
                                    Toast.makeText(ChooseActivity.this, R.string.choosePicture_toast_saveSuccess, Toast.LENGTH_SHORT).show();
                                } catch (IOException e) {
                                    Toast.makeText(ChooseActivity.this, R.string.choosePicture_toast_saveFail, Toast.LENGTH_SHORT).show();
                                }
                                break;
                        }
                    }
                });
                builder.create();
                builder.show();
            }
        };

        vImageWatcher = ImageWatcherHelper.with(this)
                .setTranslucentStatus(0)
                .setErrorImageRes(R.mipmap.error_picture)
                .setOnPictureLongPressListener(mOnPictureLongPressListener)
                .setLoader(new ImageWatcher.Loader() {
                    @Override
                    public void load(Context context, Uri uri, final ImageWatcher.LoadCallback lc) {
                        Log.i(TAG, "call load");
                        RequestOptions options = new RequestOptions().placeholder(R.mipmap.gallery_pick_photo)
                                .skipMemoryCache(true).diskCacheStrategy( DiskCacheStrategy.NONE );   //禁用磁盘缓存，否则多次使用时预览图片会出错
                        Glide.with(context).load(uri.toString()).apply(options).into(new SimpleTarget<Drawable>() {   //不知道为什么直接用URI加载会 load fail
                            @Override
                            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                lc.onResourceReady(resource);
                            }

                            @Override
                            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                lc.onLoadFailed(errorDrawable);
                            }

                            @Override
                            public void onLoadStarted(@Nullable Drawable placeholder) {
                                lc.onLoadStarted(placeholder);
                            }
                        });
                    }
                })
                .create();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!vImageWatcher.handleBackPressed()) {    //没有打开预览图片
                finish();
                return true;
            }
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

}

package com.equationl.videoshotpro.Adapter;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Picture;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.equationl.videoshotpro.ChooseActivity;
import com.equationl.videoshotpro.Image.Tools;
import com.equationl.videoshotpro.R;
import com.equationl.videoshotpro.utils.ChooseTagView;
import com.huxq17.handygridview.scrollrunner.OnItemMovedListener;

public class ChoosePictureAdapter extends BaseAdapter  implements OnItemMovedListener, ChooseTagView.OnTagDeleteListener{
    private Context context;

    private final static String TAG = "EL,In ChooseAdapter";

    private List<Bitmap> pictures;   //用于更新UI
    private List<String> imagePaths;   //用于将正确的顺序返回给Activity
    private List<Uri> imagesUri;     //用于预览图片

    private GridView mGridView;
    public boolean inEditMode = false;


    public ChoosePictureAdapter( List<Bitmap> images, String[] files, List <Uri> imagesUri, Context context) {
        super();
        this.context = context;

        List list = Arrays.asList(files);
        imagePaths = new ArrayList(list);
        pictures = images;
        this.imagesUri = imagesUri;
    }

    public void setInEditMode(boolean inEditMode) {
        this.inEditMode = inEditMode;
        notifyDataSetChanged();
    }

    public List<String> getImagePaths() {
        return imagePaths;
    }

    public List<Uri> getImagesUri() {
        return imagesUri;
    }

    @Override
    public int getCount() {

        if (null != pictures) {
            return pictures.size();
        } else {
            return 0;
        }
    }

    @Override
    public Object getItem(int position) {

        return pictures.get(position);
    }

    @Override
    public long getItemId(int position) {

        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (mGridView == null) {
            mGridView = (GridView) parent;
        }

        ChooseTagView imageview;

        if (convertView == null) {
            imageview = new ChooseTagView(context);
            convertView = imageview;
            //imageview.setGravity(Gravity.CENTER);
            //imageview.setMinimumHeight(300);
            //imageview.setMinimumWidth(300);
            //imageview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        } else {
            imageview = (ChooseTagView) convertView;
        }
        /*if (!isFixed(position)) {
            imageview.showDeleteIcon(inEditMode);
        } else {
            imageview.showDeleteIcon(false);
        }  */
        imageview.showDeleteIcon(inEditMode);
        imageview.setImageBitmap(pictures.get(position));
        imageview.setOnTagDeleteListener(this);
        return convertView;
        /*if (convertView == null) {

            viewHolder = new ViewHolder();
            // 获得容器
            convertView = LayoutInflater.from(this.context).inflate(R.layout.picture_item, null);

            // 初始化组件
            viewHolder.image = (ImageView) convertView.findViewById(R.id.picture_item);
            // 给converHolder附加一个对象
            convertView.setTag(viewHolder);
        } else {
            // 取得converHolder附加的对象
            viewHolder = (ViewHolder) convertView.getTag();
        }

        if (!isFixed(position)) {
            viewHolder.image.showDeleteIcon(inEditMode);
        } else {
            viewHolder.image.showDeleteIcon(false);
        }

        // 给组件设置资源
        //Picture picture = pictures.get(position);
        //viewHolder.image.setImageResource(picture.getImageId());
        viewHolder.image.setImageBitmap(pictures.get(position));

        return convertView;   */
    }

    @Override
    public void onItemMoved(int from, int to) {
        Log.i(TAG, from+" "+to);
        /*String temp = imagePaths[to];
        imagePaths[to] = imagePaths[from];
        imagePaths[from] = temp;  */
        String s = imagePaths.remove(from);
        imagePaths.add(to, s);
        Bitmap b =  pictures.remove(from);
        pictures.add(to, b);
        Uri u = imagesUri.remove(from);
        imagesUri.add(to, u);
    }

    @Override
    public boolean isFixed(int position) {
        //When postion==0,the item can not be dragged.
        /*if (position == 0) {
            return true;
        }*/
        //Log.i(TAG, "fix: "+position);
        return false;
    }

    class ViewHolder {
        public ImageView image;
    }

    @Override
    public void onDelete(View deleteView) {
        if (mPictureDeleteListener == null) {
            Log.e(TAG, "Delete picture fail: not set mPictureDeleteListener");
            Toast.makeText(context, R.string.choosePicture_toast_deletePictureFail, Toast.LENGTH_SHORT).show();
            return;
        }
        int index = mGridView.indexOfChild(deleteView);
        //if (index <= 0) return;
        int position = index + mGridView.getFirstVisiblePosition();
        Tools tool = new Tools();
        tool.deleteFile(new File(ChooseActivity.instance.getExternalCacheDir().toString()+"/"+imagePaths.get(position)));
        imagePaths.remove(position);
        pictures.remove(position);
        imagesUri.remove(position);
        notifyDataSetChanged();
        mPictureDeleteListener.onDelete(position);
    }

   /* class Picture {

        private Bitmap image;

        public Picture(Bitmap image) {
            this.image = image;
        }

        public Bitmap getImage() {
            return image;
        }

    }*/

    private OnPictureDeleteListener mPictureDeleteListener;

    public void setOnPictureDeleteListener(OnPictureDeleteListener listener) {
        mPictureDeleteListener = listener;
    }

    public interface OnPictureDeleteListener {
        /**
         * 删除图片
         *
         * @param position 删除了第 position 张图片
         */
        void onDelete(int position);
    }

}
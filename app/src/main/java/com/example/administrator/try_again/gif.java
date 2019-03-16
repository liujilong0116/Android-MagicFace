//生成的图片过大则动不了，原因不明，怀疑是本人手机太垃圾
package com.example.administrator.try_again;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.gifencoder.AnimatedGifEncoder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class gif extends AppCompatActivity {

    private ImageView imagegif;
    private ProgressDialog progressDialog;
    private String path="";

    private class LoadTask extends AsyncTask {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog=ProgressDialog.show(gif.this,"","GIF生成中...\n完成后请等待GIF加载...",true);
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            File file = new File(path);
            Glide.with(getApplication())
                    .load(file)
                    .into(imagegif);
            progressDialog.dismiss();
        }

        @Override
        protected String doInBackground(Object[] params) {
            function();
            return "Loading finished";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gif);
        imagegif=findViewById(R.id.imagegif);
        LoadTask loadTask=new LoadTask();
        loadTask.execute();
    }

    public void function(){
        Bitmap bitmap = BitmapFactory.decodeFile("/sdcard/faceswap/background.jpg");
        Bitmap bitmap1;
        //无视这个傻逼操作，只有if没有else就会出问题，就这么写了
        if(bitmap.getHeight()!=800)
            bitmap1 = Bitmap.createBitmap(setImgSize(bitmap,800));
        else
            bitmap1 = Bitmap.createBitmap(setImgSize(bitmap,799));

        //因为往回传值的时候，需要用和返回图像尺寸相同的图来接收，所以这么创建pic1-pic10
        Bitmap pic1 = Bitmap.createBitmap(bitmap1);
        Bitmap pic2 = Bitmap.createBitmap(bitmap1);
        Bitmap pic3 = Bitmap.createBitmap(bitmap1);
        Bitmap pic4 = Bitmap.createBitmap(bitmap1);
        Bitmap pic5 = Bitmap.createBitmap(bitmap1);
        Bitmap pic6 = Bitmap.createBitmap(bitmap1);
        Bitmap pic7 = Bitmap.createBitmap(bitmap1);
        Bitmap pic8 = Bitmap.createBitmap(bitmap1);
        Bitmap pic9 = Bitmap.createBitmap(bitmap1);
        Bitmap pic10 = Bitmap.createBitmap(bitmap1);
        if(creatpic()) {
            getpic(pic1,1);
            getpic(pic2,2);
            getpic(pic3,3);
            getpic(pic4,4);
            getpic(pic5,5);
            getpic(pic6,6);
            getpic(pic7,7);
            getpic(pic8,8);
            getpic(pic9,9);
            getpic(pic10,10);

            try {
                //无视这个傻逼操作，只有if没有else就会出问题，就这么写了
                if(bitmap.getHeight()!=800)
                    path = createGif(setImgSize(bitmap,800),pic1,pic2,pic3,pic4,pic5,pic6,pic7,pic8,pic9,pic10);
                else
                    path = createGif(setImgSize(bitmap,799),pic1,pic2,pic3,pic4,pic5,pic6,pic7,pic8,pic9,pic10);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Bitmap setImgSize(Bitmap bm,int newHeight){
        // 获得图片的宽高.
        int width = bm.getWidth();
        int height = bm.getHeight();
        // 计算缩放比例.
        float scale = ((float) newHeight) / height;
        // 取得想要缩放的matrix参数.
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        // 得到新的图片.
        return Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
    }

    //用10张图生成一个渐变的GIF
    public static String createGif(Bitmap pic0,Bitmap pic1,Bitmap pic2,Bitmap pic3,Bitmap pic4,Bitmap pic5,Bitmap pic6,Bitmap pic7,Bitmap pic8,Bitmap pic9,Bitmap pic10) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AnimatedGifEncoder localAnimatedGifEncoder = new AnimatedGifEncoder();
        localAnimatedGifEncoder.start(baos);//start
        localAnimatedGifEncoder.setRepeat(0);//设置生成gif的开始播放时间。0为立即开始播放
        localAnimatedGifEncoder.setDelay(300);//每张图的展示时间，单位ms
        localAnimatedGifEncoder.addFrame(pic0);
        localAnimatedGifEncoder.addFrame(pic1);
        localAnimatedGifEncoder.addFrame(pic2);
        localAnimatedGifEncoder.addFrame(pic3);
        localAnimatedGifEncoder.addFrame(pic4);
        localAnimatedGifEncoder.addFrame(pic5);
        localAnimatedGifEncoder.addFrame(pic6);
        localAnimatedGifEncoder.addFrame(pic7);
        localAnimatedGifEncoder.addFrame(pic8);
        localAnimatedGifEncoder.addFrame(pic9);
        localAnimatedGifEncoder.addFrame(pic10);
        localAnimatedGifEncoder.finish();//finish

        File file = new File("/sdcard/faceswap/gif");
        if (!file.exists()) file.mkdir();
        String path ="/sdcard/faceswap/gif/" + System.currentTimeMillis() + ".gif";
        FileOutputStream fos = new FileOutputStream(path);
        baos.writeTo(fos);
        baos.flush();
        fos.flush();
        baos.close();
        fos.close();

        return path;
    }

    //用两张图生成9张按照不同比例融合的图片
    public native boolean creatpic();
    //将不同比例图片提取出来
    public native void getpic(Bitmap bitmap,int num);
}

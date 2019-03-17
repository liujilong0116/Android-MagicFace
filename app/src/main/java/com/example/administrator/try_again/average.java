package com.example.administrator.try_again;

import android.graphics.Matrix;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.app.ProgressDialog;
import android.view.View;
import android.widget.ImageButton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.content.Intent;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.util.List;
import com.scrat.app.selectorlibrary.ImageSelector;

public class average extends AppCompatActivity implements View.OnClickListener{
    private ImageButton download;
    private ImageButton make;//选择图片
    private ProgressDialog progressDialog;
    private Bitmap output;
    private Bitmap bitmaps[] = new Bitmap[10];
    private double num_pic;//选择的图片数目
    private int state = 0;//为0则执行平均操作，为1则保存到本地,为-1则为出错

    class LoadTask extends AsyncTask {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if(state == 0)
                progressDialog=ProgressDialog.show(average.this,"","生成中...",true);
            if(state == 1)
                progressDialog=ProgressDialog.show(average.this,"","保存中...",true);
            if(state == 2)
                progressDialog=ProgressDialog.show(average.this,"","检查中...",true);
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            progressDialog.dismiss();
            if(state == 0){
                ImageView tv1 = findViewById(R.id.imageaverage);
                tv1.setImageBitmap(output);
            }
            if(state == 1)
            {
                AlertDialog.Builder builder  = new AlertDialog.Builder(average.this);
                builder.setMessage("已保存" ) ;
                builder.setPositiveButton("好的" ,  null );
                builder.show();
            }
            if(state == 2)
                Toast.makeText(average.this,"请确保每张图都有人脸",Toast.LENGTH_SHORT).show();
        }

        @Override
        protected String doInBackground(Object[] params) {
            if(state == 0)
                function();
            if(state == 1)
                savepic();
            if(state == 2)
                return "Loading finished";
            return "Loading finished";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.average);
        download = findViewById(R.id.download);
        make = findViewById(R.id.make);
        download.setOnClickListener(this);
        make.setOnClickListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            state = 0;
            List<String> paths = ImageSelector.getImagePaths(data);
            if(paths.size() > 1)
            {
                FileInputStream fis = null;
                num_pic = paths.size();
                for(int i = 0;i<paths.size();i++) {
                    try {
                        fis = new FileInputStream(paths.get(i));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    bitmaps[i] = Bitmap.createBitmap(setImgSize(BitmapFactory.decodeStream(fis),1000));
                    if(!createpic(bitmaps[i], i))
                        state = 2;
                }
                average.LoadTask loadTask=new average.LoadTask();
                loadTask.execute();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void onClick(View view){
        if(view.getId()==R.id.make){
            ImageSelector.show(this, 1, 10);//最多选择10张图
        }
        if(view.getId()==R.id.download){
            state=1;
            average.LoadTask loadTask=new average.LoadTask();
            loadTask.execute();
        }
    }

    private void  function(){
        Bitmap bitmap = Bitmap.createBitmap(setImgSize68(bitmaps[0]));
        facemorph(bitmap,num_pic);
        output = Bitmap.createBitmap(bitmap);
    }

    public void savepic(){
        File dir = new File("/sdcard/faceswap/morph");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File f = new File("/sdcard/faceswap/morph/"+System.currentTimeMillis() +".jpg");
        if (f.exists()) {
            f.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            output.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //改变图片尺寸便于寻找人脸
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
        Bitmap newbm = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
        return newbm;
    }

    //将图片设置成600*800.因为输出的图片尺寸为600*800
    public Bitmap setImgSize68(Bitmap bm) {
        // 获得图片的宽高
        int width = bm.getWidth();
        int height = bm.getHeight();
        // 计算缩放比例
        float scaleWidth = ((float) 600.0) / width;
        float scaleHeight = ((float) 600.0) / height;
        // 取得想要缩放的matrix参数
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        // 得到新的图片
        Bitmap newbm = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
        return newbm;
    }

    //将图片存入pics[]里，并尽可能的扣出人脸的部分使得平均脸求出来更好看
    public native boolean createpic(Bitmap bitmap,int no);
    //执行平均脸操作，bitmap为传值用，num为选中的人脸数目
    public native boolean facemorph(Bitmap bitmap,double num);
}

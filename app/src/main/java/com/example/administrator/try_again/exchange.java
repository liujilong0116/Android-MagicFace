package com.example.administrator.try_again;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import com.scrat.app.selectorlibrary.ImageSelector;

public class exchange extends AppCompatActivity implements View.OnClickListener{
    private ImageButton download;
    private ImageButton again;
    private ImageButton make;//选择图片
    private ImageButton camera;
    private ProgressDialog progressDialog;
    private Bitmap output;
    private Bitmap bitmap;
    private boolean check;
    private int num_face;
    private int state = 0;//为0则执行平均操作，为1则保存到本地,为-1则为出错

    private Uri imageUri;
    public static File tempFile;

    private class LoadTask extends AsyncTask {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if(state == 0)
                progressDialog=ProgressDialog.show(exchange.this,"","生成中...",true);
            if(state == 1)
                progressDialog=ProgressDialog.show(exchange.this,"","保存中...",true);
            if(state == 2 || state == 3)
                progressDialog=ProgressDialog.show(exchange.this,"","检查中...",true);
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
                AlertDialog.Builder builder  = new AlertDialog.Builder(exchange.this);
                builder.setMessage("已保存" ) ;
                builder.setPositiveButton("好的" ,  null );
                builder.show();
            }
            if(state == 2)
                Toast.makeText(exchange.this,"请确保图中有两张及以上脸",Toast.LENGTH_SHORT).show();
            if(state == 3)
                Toast.makeText(exchange.this,"请先拍照或选择图片",Toast.LENGTH_SHORT).show();
        }

        @Override
        protected String doInBackground(Object[] params) {
            if(state == 0)
            {
                check = true;
                num_face = findface();
                function();
            }
            if(state == 1)
                savepic();
            if(state == 2 || state == 3)
                return "Loading finished";
            return "Loading finished";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.exchange);
        download = findViewById(R.id.download);
        again = findViewById(R.id.again);
        make = findViewById(R.id.make);
        camera = findViewById(R.id.camera);

        check = false;

        download.setOnClickListener(this);
        make.setOnClickListener(this);
        again.setOnClickListener(this);
        camera.setOnClickListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //1为打开相机后返回
        if(requestCode == 1)
        {
            if (resultCode == RESULT_OK) {
                state = 0;
                Intent intent = new Intent("com.android.camera.action.CROP");
                intent.setDataAndType(imageUri, "image/*");
                intent.putExtra("scale", true);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                try {
                    bitmap = Bitmap.createBitmap(setImgSize(BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri)),1000));
                    createpic(bitmap);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                num_face = findface();
                if(num_face<=1)
                    state = 2;
                exchange.LoadTask loadTask=new exchange.LoadTask();
                loadTask.execute();
            }
        }
        //2为打开相册后返回
        if (requestCode == 2) {
            state = 0;
            List<String> paths = ImageSelector.getImagePaths(data);
           if(paths.size() == 1)
           {
               FileInputStream fis = null;
               try {
                   fis = new FileInputStream(paths.get(0));
               } catch (FileNotFoundException e) {
                   e.printStackTrace();
               }
               bitmap = Bitmap.createBitmap(setImgSize(BitmapFactory.decodeStream(fis),1000));
               createpic(bitmap);
               num_face = findface();
               if(num_face<=1)
                   state = 2;
               exchange.LoadTask loadTask=new exchange.LoadTask();
               loadTask.execute();
           }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void onClick(View view){
        if(view.getId()==R.id.make){
            ImageSelector.show(this, 2, 1);//最多选择1张图
        }
        if(view.getId() == R.id.camera)
        {
            openCamera(this);
        }
        if(view.getId() == R.id.again)
        {
            state = 0;
            num_face = findface();
            if(num_face<=1)
                state = 2;
            if(!check)
                state = 3;
            exchange.LoadTask loadTask=new exchange.LoadTask();
            loadTask.execute();
        }
        if(view.getId()==R.id.download){
            state=1;
            exchange.LoadTask loadTask=new exchange.LoadTask();
            loadTask.execute();
        }
    }

    private void  function(){
        Bitmap bitmaptemp = Bitmap.createBitmap(bitmap);
        faceexchange(bitmaptemp,num_face);
        output = Bitmap.createBitmap(bitmaptemp);
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

    public void openCamera(Activity activity) {
        //獲取系統版本
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        // 激活相机
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 判断存储卡是否可以用，可用进行存储
        if (hasSdcard()) {
            SimpleDateFormat timeStampFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
            String filename = timeStampFormat.format(new Date());
            tempFile = new File(Environment.getExternalStorageDirectory(),
                    filename + ".jpg");
            if (currentapiVersion < 24) {
                // 从文件中创建uri
                imageUri = Uri.fromFile(tempFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            } else {
                //兼容android7.0 使用共享文件的形式
                ContentValues contentValues = new ContentValues(1);
                contentValues.put(MediaStore.Images.Media.DATA, tempFile.getAbsolutePath());
                //检查是否有存储权限，以免崩溃
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    //申请WRITE_EXTERNAL_STORAGE权限
                    Toast.makeText(this,"请开启存储权限",Toast.LENGTH_SHORT).show();
                    return;
                }
                imageUri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            }
        }
        // 开启一个带有返回值的Activity，请求码为PHOTO_REQUEST_CAREMA
        activity.startActivityForResult(intent, 1);
    }

    public static boolean hasSdcard() {
        return Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED);
    }

    public native void createpic(Bitmap bitmap);
    //将图片存入pics[]里，并尽可能的扣出人脸的部分使得平均脸求出来更好看
    public native int findface();
    //执行平均脸操作，bitmap为传值用，num为选中的人脸数目
    public native void faceexchange(Bitmap bitmap,int num_face);
}

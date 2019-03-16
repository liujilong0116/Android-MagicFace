package com.example.administrator.try_again;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class swap extends AppCompatActivity implements View.OnClickListener{

    private ImageButton download;
    private ProgressDialog progressDialog;
    private Bitmap output;
    private int state ;//状态0为换脸，状态为1为下载到本地

    private class LoadTask extends AsyncTask {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if(state == 0)
                progressDialog=ProgressDialog.show(swap.this,"","生成中...",true);
            else
                progressDialog=ProgressDialog.show(swap.this,"","保存中...",true);
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            progressDialog.dismiss();
            if(state == 0){
                ImageView tv1 = findViewById(R.id.imageswap);
                tv1.setImageBitmap(output);
            }
            else
            {
                AlertDialog.Builder builder  = new AlertDialog.Builder(swap.this);
                builder.setMessage("已保存" ) ;
                builder.setPositiveButton("好的" ,  null );
                builder.show();
            }
        }

        @Override
        protected String doInBackground(Object[] params) {
            if(state == 0)
                function();
            else
                savepic();
            return "Loading finished";
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.swap);
        download = findViewById(R.id.download);
        state = 0;
        LoadTask loadTask=new LoadTask();
        loadTask.execute();
        download.setOnClickListener(this);
    }

    //只有一个按钮，不区分状态
    public void onClick(View view){
        state=1;
        LoadTask loadTask=new LoadTask();
        loadTask.execute();
    }

    //执行换脸函数
    private void  function(){
        Bitmap bitmap = BitmapFactory.decodeFile("/sdcard/faceswap/background.jpg");
        if(faceswap(bitmap)){
            output = Bitmap.createBitmap(bitmap);
            }
    }

    public void savepic(){
        File dir = new File("/sdcard/faceswap/swap");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File f = new File("/sdcard/faceswap/swap/"+System.currentTimeMillis() +".jpg");
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

    //换脸
    public native boolean faceswap(Bitmap bitmap);
}

//功能为按比例融合，但一开始取名叫vary，懒得改了
package com.example.administrator.try_again;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class vary  extends AppCompatActivity implements View.OnClickListener{
    private ImageButton onetofour;
    private ImageButton twotothree;
    private ImageButton threetotwo;
    private ImageButton fourtoone;
    private ImageButton download;
    private ProgressDialog progressDialog;

    private Bitmap output;
    double alpha;//融合比例
    private int state;//为0保存，为1则1：4融合，为2则2：3融合，为3则3：2融合，为4则4：1融合

    private class LoadTask extends AsyncTask{
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if(state == 0)
                progressDialog=ProgressDialog.show(vary.this,"","生成中...",true);
            else
                progressDialog=ProgressDialog.show(vary.this,"","保存中...",true);
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            progressDialog.dismiss();
            if(state == 0){
                ImageView tv1 = findViewById(R.id.imagevary);
                tv1.setImageBitmap(output);
            }
            else
            {
                AlertDialog.Builder builder  = new AlertDialog.Builder(vary.this);
                builder.setMessage("已保存" ) ;
                builder.setPositiveButton("好的" ,  null );
                builder.show();
            }
        }

        @Override
        protected String doInBackground(Object[] params) {
            if(state == 0)
                function(alpha);
            else
                savepic();
            return "Loading finished";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vary);
        onetofour=findViewById(R.id.onetofour);
        twotothree=findViewById(R.id.twotothree);
        threetotwo=findViewById(R.id.threetotwo);
        fourtoone=findViewById(R.id.fourtoone);
        download = findViewById(R.id.download);

        alpha = 1;
        LoadTask loadTask=new LoadTask();//一进入则为换脸结果，后需要选择融合比例改变
        loadTask.execute();

        onetofour.setOnClickListener(this);
        twotothree.setOnClickListener(this);
        threetotwo.setOnClickListener(this);
        fourtoone.setOnClickListener(this);
        download.setOnClickListener(this);
    }

    public void onClick(View view){
        if(view.getId()==R.id.onetofour){
            state = 0;
            alpha=0.2;
            LoadTask loadTask=new LoadTask();
            loadTask.execute();
        }
        if(view.getId()==R.id.twotothree){
            state = 0;
            alpha=0.4;
            LoadTask loadTask=new LoadTask();
            loadTask.execute();
        }
        if(view.getId()==R.id.threetotwo){
            state = 0;
            alpha=0.6;
            LoadTask loadTask=new LoadTask();
            loadTask.execute();
        }
        if(view.getId()==R.id.fourtoone){
            state = 0;
            alpha=0.8;
            LoadTask loadTask=new LoadTask();
            loadTask.execute();
        }
        if(view.getId()==R.id.download){
            state = 1;
            LoadTask loadTask=new LoadTask();
            loadTask.execute();
        }
    }

    public void savepic(){
        File dir = new File("/sdcard/faceswap/vary");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File f = new File("/sdcard/faceswap/vary/"+System.currentTimeMillis() +".jpg");
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

    //执行融合函数
    private void  function(double a){
        Bitmap bitmaptemp = BitmapFactory.decodeFile("/sdcard/faceswap/background.jpg");
        if(facevary(bitmaptemp,a))
            output = Bitmap.createBitmap(bitmaptemp);
    }

    //融合
    public native boolean facevary(Bitmap bitmap,double alpha);
}

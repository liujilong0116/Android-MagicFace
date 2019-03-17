package com.example.administrator.try_again;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import android.content.Intent;
import android.widget.ImageButton;

public class select extends AppCompatActivity implements View.OnClickListener{
    private ImageButton gotochange ;
    private ImageButton gotomorph ;
    private ImageButton gotoexchange;

    static {
        System.loadLibrary("native-lib");
    }

    //复制文件到项目目录
    private void copyAssetsFile(String name, File dir) throws IOException {
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, name);
        if (!file.exists()){
            InputStream is = getAssets().open(name);
            FileOutputStream fos = new FileOutputStream(file);
            int len;
            byte[] buffer = new byte[2048];
            while ((len = is.read(buffer)) != -1)
                fos.write(buffer, 0, len);
            fos.close();
            is.close();
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select);
        gotochange = findViewById(R.id.gotochange);
        gotomorph = findViewById(R.id.gotomorph);
        gotoexchange = findViewById(R.id.gotoexchange);

        //把assets里的文件下载到指定路径
        try {
            File dir = new File("/sdcard/faceswap");
            copyAssetsFile("haarcascade_frontalface_alt.xml", dir);
            copyAssetsFile("shape_predictor_68_face_landmarks.dat", dir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        createswap();//加载模型，需要占用时间，导致打开项目缓慢
        gotochange.setOnClickListener(this);
        gotomorph.setOnClickListener(this);
        gotoexchange.setOnClickListener(this);
    }

    public void onClick(View view){
        //按下变脸转跳到change
        if(view.getId()==R.id.gotochange){
            Intent intent = new Intent(select.this,MainActivity.class);
            startActivity(intent);
        }
        //按下平均脸转跳到morph
        if(view.getId()==R.id.gotomorph){
            Intent intent = new Intent(select.this,average.class);
            startActivity(intent);
        }
        if(view.getId()==R.id.gotoexchange){
            Intent intent = new Intent(select.this,exchange.class);
            startActivity(intent);
        }
    }

    public native void createswap();//初始化   加载68点和人脸判断模型
}

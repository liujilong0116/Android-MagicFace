//换脸程序第二版
//速度还有点慢    每次都要加载模型   下一版加快速度和改善一下丑陋的界面
//并且加上一些注释
//2018.11.22   刘积隆

package com.example.administrator.try_again;
import android.graphics.Matrix;
import android.os.AsyncTask;
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
import com.scrat.app.selectorlibrary.ImageSelector;
import java.io.FileNotFoundException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private ImageButton pic1 ;
    private ImageButton pic2 ;
    private ImageButton toswap ;
    private ImageButton tovary ;
    private ImageButton togif ;
    private ImageView picture1;
    private ImageView picture2;
    private int state;//为1则转跳到swap，为2则转跳到vary，为3则转跳到gif
    int error=0;//判断错误类型，为1则没选够两张图，为2则某张图中没有人脸
    int whice_pic = 0;  //判断是选择第一张还是第二张照片

    private ProgressDialog progressDialog;

    private boolean check1 = false, check2 = false;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private class LoadTask extends AsyncTask {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog=ProgressDialog.show(MainActivity.this,"","检查图片中...",true);
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            progressDialog.dismiss();
            if(error == 1)
                Toast.makeText(MainActivity.this,"请确保两张图都有人脸",Toast.LENGTH_SHORT).show();
            if(error == 2)
                Toast.makeText(MainActivity.this,"请确保两张图片都选好了",Toast.LENGTH_SHORT).show();
        }

        @Override
        protected String doInBackground(Object[] params) {
            if(check1 == true && check2 == true  ){
                if(judge()){
                    error = 0;
                    if(state == 1){
                        Intent intent = new Intent(MainActivity.this,swap.class);
                        startActivity(intent);
                    }
                    if(state == 2){
                        Intent intent = new Intent(MainActivity.this,vary.class);
                        startActivity(intent);
                    }
                    if(state == 3){
                        Intent intent = new Intent(MainActivity.this,gif.class);
                        startActivity(intent);
                    }
                }
                else
                    error=1;
            }
            else
                error=2;
            return "Loading finished";
        }
    }

    //初始化
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pic1 = findViewById(R.id.pic1);
        pic2 = findViewById(R.id.pic2);
        toswap = findViewById(R.id.toswap);
        tovary = findViewById(R.id.tovary);
        togif = findViewById(R.id.togif);
        picture1 = findViewById(R.id.image1);
        picture2 = findViewById(R.id.image2);

        pic1.setOnClickListener(this);
        pic2.setOnClickListener(this);
        toswap.setOnClickListener(this);
        tovary.setOnClickListener(this);
        togif.setOnClickListener(this);
    }

    public void onClick(View view){
        if(view.getId()==R.id.pic1){
            whice_pic = 1;
            ImageSelector.show(this, 1, 1);
        }
        if(view.getId()==R.id.pic2){
            whice_pic = 2;
            ImageSelector.show(this, 1, 1);
        }
        //按下换脸转跳到swap
        if(view.getId()==R.id.toswap){
            state = 1;
            LoadTask loadTask=new LoadTask();
            loadTask.execute();
        }
        //按下融合转跳到vary
        if(view.getId()==R.id.tovary){
            state=2;
            LoadTask loadTask=new LoadTask();
            loadTask.execute();
        }
        //按下生成gif转跳到gif
        if(view.getId()==R.id.togif){
            state = 3;
            LoadTask loadTask=new LoadTask();
            loadTask.execute();
        }
    }

    //从图片选择返回后的操作
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            List<String> paths = ImageSelector.getImagePaths(data);
            FileInputStream fis = null;
            for (int i = 0; i < paths.size(); i++) {
                try {
                    fis = new FileInputStream(paths.get(i));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                Bitmap bitmap = Bitmap.createBitmap(setImgSize(BitmapFactory.decodeStream(fis), 800));
                bitmaptomat(bitmap,whice_pic - 1);
                //根据whice_pic的值分别给两个ImageView传图
                if(whice_pic == 1)
                {
                    picture1.setImageBitmap(bitmap);
                    check1 = true;
                }
                else {
                    picture2.setImageBitmap(bitmap);
                    tempsave(bitmap);
                    check2 = true;
                }
            }
        }
    }

    //将图二（背景图）储存到指定路径
    public void tempsave(Bitmap bm) {
        File f = new File("/sdcard/faceswap/background.jpg");
        if (f.exists()) {
            f.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //调整图像的尺寸已便于提高运行速度
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

    public native boolean judge();//判断两张图里是否有人脸
    //将图片从Bitmap转换成Mat，存一次就好，之后的换脸、融合、生成GIF直接用
    public native void bitmaptomat(Bitmap bitmap,int whice_pic);
}

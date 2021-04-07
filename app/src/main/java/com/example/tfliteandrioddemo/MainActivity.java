package com.example.tfliteandrioddemo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    /*定义成员*/
    //初始化tflite工具类
    private TFLiteUtil tfLiteUtil;
    //定义控件
    private ImageView imageView;
    private TextView textView;
    //预测标签列表
    private ArrayList<String> classNames;
    //定义模型文件名和标签文件名
    private final String ModelFileName = "mobilenet_v2.tflite";
    private final String LabelFileName = "label_list.txt";
    //将图片的宽按比例缩放ratio = scalePixel / width
    private final int scalePixel = 480;
    //定义requestCode静态变量
    public static int noFunction = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        //Android 6 以上的设备动态申请相册和相机权限
        if (!hasPermission()) {
            requestPermission();
        }

        //获取控件，指定操作
        Button selectImgBtn = findViewById(R.id.select_img_btn);
        Button openCamera = findViewById(R.id.open_camera);
        imageView = findViewById(R.id.image_view);
        textView = findViewById(R.id.result_text);

        //初始化完成，textview展示欢迎消息
        String helloWord = "Hello，World！\n";
        textView.setText(helloWord);

        //打开相册
        selectImgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 打开相册
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, 1);
            }
        });

        //打开摄像头
        openCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 打开实时拍摄识别页面
//                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
//                startActivity(intent);
                Intent intent = new Intent(Intent.ACTION_PICK);
                startActivityForResult(intent, noFunction);
            }
        });

        /*加载模型和标签*/
        classNames = Utils.ReadListFromFile(getAssets(), LabelFileName);
        // TFLite不建议直接从assets里面加载模型，而是先将模型放到一个缓存目录中，然后再从缓存目录加载模型
        String modelPath = getCacheDir().getAbsolutePath() + File.separator + ModelFileName;
        Utils.copyFileFromAsset(MainActivity.this, ModelFileName, modelPath);
        try {
            tfLiteUtil = new TFLiteUtil(modelPath);
            Toast.makeText(MainActivity.this, "模型加载成功！", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "模型加载失败！", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            finish();
        }
    }

    //控件响应
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String imagePath;
        if(requestCode == 1) {
            if (data == null) {
                Log.w("onActivityResult", "user photo data is null");
            }
            Uri imageUri = data.getData();
            imagePath = getPathFromURI(MainActivity.this, imageUri);
            try {
                //图像预处理（不同于tflite模型的预处理，这里的预处理是缩放、裁剪、旋转意义上的）
                FileInputStream fis = new FileInputStream(imagePath);
                Bitmap bitmap = BitmapFactory.decodeStream(fis);
                //旋转图像，当图像是横向时旋转成竖向
                bitmap = Utils.tryRotateBitmap(bitmap);
                //按比例缩放图片，将位图按比例缩放
                bitmap = Utils.scaleBitmap(bitmap, scalePixel);
                //再将图片中心裁剪
                bitmap = Utils.cropBitmap(bitmap, 480, 480);
                imageView.setImageBitmap(bitmap);

                //预测计时
                long start = System.currentTimeMillis();
                //开始预测
                float[] result = tfLiteUtil.predictImage(bitmap);
                long end = System.currentTimeMillis();

                String showText =
                        "\n名称：" + classNames.get((int) result[0]) +
                                "\n概率：" + result[1] +
                                "\n耗时：" + (end - start) + "ms";
                textView.setText(showText);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else if(requestCode == noFunction){
            //摄像头功能还在写，提示用户
            String showText = "抱歉，实时预测功能仍在开发中，敬请期待！";
            textView.setText(showText);
        }
    }

    // check had permission
    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    // request permission
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    // 根据相册的Uri获取图片的路径
    public static String getPathFromURI(Context context, Uri uri) {
        String result;
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor == null) {
            result = uri.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

}
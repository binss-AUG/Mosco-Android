package com.vn.jet.mosco;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.OutputStream;

public class PhotoReviewActivity extends AppCompatActivity {

    private String photoPath;
    private Bitmap cachedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_review);

        photoPath = getIntent().getStringExtra("extra_photo_path");
        if (photoPath == null || photoPath.isEmpty()) {
            Toast.makeText(this, "Không tải được ảnh!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageView ivPreview = findViewById(R.id.iv_photo_preview);
        
        // Load ảnh từ cache
        cachedBitmap = BitmapFactory.decodeFile(photoPath);
        if (cachedBitmap != null) {
            ivPreview.setImageBitmap(cachedBitmap);
        }

        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            saveToGallery();
        });

        findViewById(R.id.btn_share).setOnClickListener(v -> {
            sharePhoto();
        });
    }

    private void saveToGallery() {
        if (cachedBitmap == null) return;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "Mosco_AR_" + System.currentTimeMillis() + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mosco");

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    cachedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                Toast.makeText(this, "Đã lưu ảnh vào Thư viện!", Toast.LENGTH_SHORT).show();
                finish(); // Tự động đóng màn hình sau khi lưu thành công
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khi lưu ảnh!", Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePhoto() {
        try {
            File imageFile = new File(photoPath);
            Uri imageUri = FileProvider.getUriForFile(
                    this, 
                    getPackageName() + ".fileprovider", 
                    imageFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ ảnh Mosco qua..."));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi chia sẻ ảnh!", Toast.LENGTH_SHORT).show();
        }
    }
}

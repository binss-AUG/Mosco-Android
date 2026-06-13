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
        
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
                return;
            }
        }
        
        performSave();
    }

    private void performSave() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "Mosco_AR_" + System.currentTimeMillis() + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mosco");
    
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        cachedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                }
            } else {
                File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Mosco");
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                File file = new File(directory, "Mosco_AR_" + System.currentTimeMillis() + ".png");
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                    cachedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                android.media.MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
            }
            Toast.makeText(this, "Đã lưu ảnh vào Thư viện!", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khi lưu ảnh!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            performSave();
        } else {
            Toast.makeText(this, "Cần cấp quyền lưu trữ để lưu ảnh!", Toast.LENGTH_SHORT).show();
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

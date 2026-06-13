package com.vn.jet.mosco;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.vn.jet.mosco.utils.GlideBindingAdapter;
import com.vn.jet.mosco.utils.CardEffectHelper;
import com.vn.jet.mosco.model.CardDisplayItem;
import com.vn.jet.mosco.utils.RotationGestureDetector;

public class ObjetCameraActivity extends AppCompatActivity {
    public static final String EXTRA_IMAGE_URL = "extra_image_url";

    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private PreviewView viewFinder;
    private ConstraintLayout layoutCameraWrapper;
    private ViewGroup flObjetTransformGroup;
    private com.google.android.material.card.MaterialCardView cvObjetWrapper;
    private ImageView ivCardImage;
    private View layoutCardSkeleton;
    private View viewCardShimmer;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    // Gesture controls
    private float dX, dY;
    private float scaleFactor = 1.0f;
    private float rotationDegrees = -30f;
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector tapGestureDetector;
    private RotationGestureDetector rotationGestureDetector;

    // Camera settings
    private int currentLensFacing = CameraSelector.LENS_FACING_BACK;
    private int currentAspectRatioIndex = 0; // 0: 9:16, 1: 3:4, 2: 1:1
    private final String[] aspectRatios = {"9:16", "3:4", "1:1"};

    // Card data
    private String frontImageUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_objet_camera);

        viewFinder = findViewById(R.id.view_finder);
        layoutCameraWrapper = findViewById(R.id.layout_camera_wrapper);
        flObjetTransformGroup = findViewById(R.id.fl_objet_transform_group);
        cvObjetWrapper = findViewById(R.id.cv_objet_wrapper);
        ivCardImage = findViewById(R.id.card_iv_image);
        layoutCardSkeleton = findViewById(R.id.layout_card_skeleton);
        viewCardShimmer = findViewById(R.id.view_card_shimmer);

        frontImageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        String collectionId = getIntent().getStringExtra("extra_collection_id");
        String bgColor = getIntent().getStringExtra("extra_bg_color");
        int upgradeLevel = getIntent().getIntExtra("extra_upgrade_level", 0);

        // Load front image
        GlideBindingAdapter.loadImage(ivCardImage, frontImageUrl, true, true);

        // Apply Glow and Depth Effect
        if (collectionId != null || frontImageUrl != null) {
            String pseudoId = (collectionId != null && !collectionId.isEmpty()) ? collectionId : frontImageUrl;
            Integer parsedBgColor = null;
            if (bgColor != null && !bgColor.isEmpty()) {
                try {
                    parsedBgColor = Color.parseColor(bgColor);
                } catch (Exception e) { }
            }
            CardDisplayItem dummyItem = new CardDisplayItem();
            dummyItem.setFrontImage(frontImageUrl);
            dummyItem.setCollectionId(pseudoId);
            dummyItem.setBackgroundColor(bgColor);
            dummyItem.setUpgradeLevel(upgradeLevel);
            
            CardEffectHelper.apply(cvObjetWrapper, viewCardShimmer, dummyItem, false, true, parsedBgColor);
        }

        // Setup Buttons
        findViewById(R.id.btn_camera_close).setOnClickListener(v -> finish());
        findViewById(R.id.btn_camera_capture).setOnClickListener(v -> takePhoto());
        findViewById(R.id.btn_camera_flip).setOnClickListener(v -> flipCamera());
        findViewById(R.id.btn_camera_aspect_ratio).setOnClickListener(v -> cycleAspectRatio());

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
        setupTouchListener();
        
        // Instructions are hidden on first touch via touch listener
        TextView tvInstruction = findViewById(R.id.tv_ar_instruction);
    }

    private void flipCamera() {
        currentLensFacing = (currentLensFacing == CameraSelector.LENS_FACING_BACK) ?
                CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        startCamera();
    }

    private void cycleAspectRatio() {
        currentAspectRatioIndex = (currentAspectRatioIndex + 1) % aspectRatios.length;
        String newRatio = aspectRatios[currentAspectRatioIndex];
        
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) layoutCameraWrapper.getLayoutParams();
        params.dimensionRatio = newRatio;
        layoutCameraWrapper.setLayoutParams(params);
        
        TextView btnAspectRatio = findViewById(R.id.btn_camera_aspect_ratio);
        btnAspectRatio.setText(newRatio);
    }

    private void setupTouchListener() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(0.25f, Math.min(scaleFactor, 7.0f));
                flObjetTransformGroup.setScaleX(scaleFactor);
                flObjetTransformGroup.setScaleY(scaleFactor);
                return true;
            }
        });

        rotationGestureDetector = new RotationGestureDetector(new RotationGestureDetector.OnRotationGestureListener() {
            @Override
            public void onRotation(RotationGestureDetector rotationDetector) {
                rotationDegrees += rotationDetector.getAngle();
                flObjetTransformGroup.setRotation(rotationDegrees);
            }
        });

        findViewById(R.id.fl_ar_container).setOnTouchListener((v, event) -> {
            TextView tvInstruction = findViewById(R.id.tv_ar_instruction);
            if (tvInstruction.getVisibility() == View.VISIBLE) {
                tvInstruction.animate().alpha(0f).setDuration(300).withEndAction(() -> tvInstruction.setVisibility(View.GONE));
            }
            
            scaleGestureDetector.onTouchEvent(event);
            rotationGestureDetector.onTouchEvent(event);
            
            int pointerCount = event.getPointerCount();
            if (pointerCount == 1 && !scaleGestureDetector.isInProgress() && !rotationGestureDetector.isInProgress()) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = flObjetTransformGroup.getTranslationX() - event.getX();
                        dY = flObjetTransformGroup.getTranslationY() - event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        flObjetTransformGroup.setTranslationX(event.getX() + dX);
                        flObjetTransformGroup.setTranslationY(event.getY() + dY);
                        break;
                }
            } else {
                if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                    int remainingIndex = (event.getActionIndex() == 0) ? 1 : 0;
                    if (remainingIndex < event.getPointerCount()) {
                        dX = flObjetTransformGroup.getTranslationX() - event.getX(remainingIndex);
                        dY = flObjetTransformGroup.getTranslationY() - event.getY(remainingIndex);
                    }
                }
            }
            return true;
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(currentLensFacing)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        findViewById(R.id.btn_camera_capture).setEnabled(false);
        findViewById(R.id.layout_loading).setVisibility(View.VISIBLE);

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap cameraBitmap = null;
                try {
                    cameraBitmap = imageProxyToBitmap(image);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    image.close();
                }

                if (cameraBitmap == null) {
                    runOnUiThread(() -> {
                        findViewById(R.id.btn_camera_capture).setEnabled(true);
                        findViewById(R.id.layout_loading).setVisibility(View.GONE);
                        Toast.makeText(ObjetCameraActivity.this, "Lỗi đọc ảnh!", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                final Bitmap finalCameraBitmap = cameraBitmap;
                new Thread(() -> {
                    try {
                        // Crop
                        Bitmap croppedCamera = cropToAspectRatio(finalCameraBitmap, layoutCameraWrapper.getWidth(), layoutCameraWrapper.getHeight());
                        
                        // Overlay must run on UI thread
                        runOnUiThread(() -> {
                            Bitmap finalImage = overlayBitmap(croppedCamera);
                            
                            // Save to cache
                            new Thread(() -> {
                                java.io.File cacheDir = getCacheDir();
                                java.io.File tempFile = new java.io.File(cacheDir, "ar_capture_temp.png");
                                try {
                                    java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile);
                                    finalImage.compress(Bitmap.CompressFormat.PNG, 100, out);
                                    out.flush();
                                    out.close();
                                    
                                    runOnUiThread(() -> {
                                        findViewById(R.id.btn_camera_capture).setEnabled(true);
                                        findViewById(R.id.layout_loading).setVisibility(View.GONE);
                                        Intent intent = new Intent(ObjetCameraActivity.this, PhotoReviewActivity.class);
                                        intent.putExtra("extra_photo_path", tempFile.getAbsolutePath());
                                        startActivity(intent);
                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    runOnUiThread(() -> {
                                        findViewById(R.id.btn_camera_capture).setEnabled(true);
                                        findViewById(R.id.layout_loading).setVisibility(View.GONE);
                                        Toast.makeText(ObjetCameraActivity.this, "Lỗi khi lưu ảnh!", Toast.LENGTH_SHORT).show();
                                    });
                                }
                            }).start();
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            findViewById(R.id.btn_camera_capture).setEnabled(true);
                            findViewById(R.id.layout_loading).setVisibility(View.GONE);
                            Toast.makeText(ObjetCameraActivity.this, "Lỗi xử lý ảnh!", Toast.LENGTH_SHORT).show();
                        });
                    }
                }).start();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                findViewById(R.id.btn_camera_capture).setEnabled(true);
                findViewById(R.id.layout_loading).setVisibility(View.GONE);
                Toast.makeText(ObjetCameraActivity.this, "Lỗi Camera", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Bitmap cropToAspectRatio(Bitmap source, int targetWidth, int targetHeight) {
        if (source == null || targetWidth <= 0 || targetHeight <= 0) return source;

        float sourceRatio = (float) source.getWidth() / source.getHeight();
        float targetRatio = (float) targetWidth / targetHeight;

        int newWidth = source.getWidth();
        int newHeight = source.getHeight();

        if (sourceRatio > targetRatio) {
            newWidth = (int) (source.getHeight() * targetRatio);
        } else {
            newHeight = (int) (source.getWidth() / targetRatio);
        }

        int startX = (source.getWidth() - newWidth) / 2;
        int startY = (source.getHeight() - newHeight) / 2;

        return Bitmap.createBitmap(source, startX, startY, newWidth, newHeight);
    }

    private Bitmap overlayBitmap(Bitmap background) {
        Bitmap result = Bitmap.createBitmap(background.getWidth(), background.getHeight(), background.getConfig());
        Canvas canvas = new Canvas(result);
        
        // Draw camera image
        canvas.drawBitmap(background, new Matrix(), null);
        
        // Draw the objet AR overlay exactly as displayed
        View arContainer = findViewById(R.id.fl_ar_container);
        Bitmap overlay = Bitmap.createBitmap(arContainer.getWidth(), arContainer.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas arCanvas = new Canvas(overlay);
        
        arContainer.draw(arCanvas);
        
        float scaleX = (float) background.getWidth() / arContainer.getWidth();
        float scaleY = (float) background.getHeight() / arContainer.getHeight();
        Matrix matrix = new Matrix();
        matrix.postScale(scaleX, scaleY);
        canvas.drawBitmap(overlay, matrix, null);
        
        return result;
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        
        Matrix matrix = new Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees());
        
        if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) {
            matrix.postScale(-1f, 1f, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        }
        
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void saveImage(Bitmap bitmap) {
        String name = "Mosco_AR_" + System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Mosco");
        }

        try {
            android.net.Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                if (outputStream != null) outputStream.close();
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "Đã lưu ảnh vào Thư viện", Toast.LENGTH_SHORT).show();
                    findViewById(R.id.btn_camera_capture).setEnabled(true);
                    
                    // Share Image Intent
                    android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    shareIntent.setType("image/jpeg");
                    shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
                    startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ ảnh Mosco AR"));
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> {
                Toast.makeText(this, "Lỗi lưu ảnh", Toast.LENGTH_SHORT).show();
                findViewById(R.id.btn_camera_capture).setEnabled(true);
            });
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Bạn cần cấp quyền Camera.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        View loading = findViewById(R.id.layout_loading);
        if (loading != null) loading.setVisibility(View.GONE);
        View btnCapture = findViewById(R.id.btn_camera_capture);
        if (btnCapture != null) btnCapture.setEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}

package com.vn.jet.mosco;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.vn.jet.mosco.adapter.OnboardingAdapter;
import com.vn.jet.mosco.model.OnboardingItem;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private OnboardingAdapter adapter;
    private LinearLayout layoutDots;
    private Button btnNext;
    private ViewPager2 viewPager;
    private ImageView ivBackground;
    private ObjectAnimator driftX, driftY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        layoutDots = findViewById(R.id.layout_dots);
        btnNext = findViewById(R.id.btn_next);
        viewPager = findViewById(R.id.viewPager);
        ivBackground = findViewById(R.id.iv_background_parallax);

        // Nhận thời gian chạy từ Splash (nếu có)
        long playTimeX = getIntent().getLongExtra("EXTRA_PLAY_TIME_X", 0L);
        long playTimeY = getIntent().getLongExtra("EXTRA_PLAY_TIME_Y", 0L);

        setupParallax(playTimeX, playTimeY);
        setupOnboardingItems();
        setupDots();
        setCurrentDot(0);

        // --- TỐI ƯU HÓA RECYCLERVIEW BÊN TRONG VIEW PAGER 2 ---
        View recyclerView = viewPager.getChildAt(0);
        if (recyclerView instanceof RecyclerView) {
            RecyclerView rv = (RecyclerView) recyclerView;
            // 1. Tăng bộ nhớ đệm cho các item view (giảm thiểu việc onCreateViewHolder)
            rv.setItemViewCacheSize(3);
            // 2. Tăng số lượng view tối đa trong pool để tránh khởi tạo lại khi vuốt
            rv.getRecycledViewPool().setMaxRecycledViews(0, 5);
            // 3. Tắt hiệu ứng OverScroll để vuốt mượt hơn
            rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                setCurrentDot(position);
                if (position == adapter.getItemCount() - 1) {
                    btnNext.setText("Get Started");
                } else {
                    btnNext.setText("Next");
                }
            }
        });

        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() + 1 < adapter.getItemCount()) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                Intent intent = new Intent(this, SignInActivity.class);
                // Truyền "Nhịp tim" (Thời gian đang chạy) của Animation để màn sau chạy tiếp
                if (driftX != null && driftY != null) {
                    intent.putExtra("EXTRA_PLAY_TIME_X", driftX.getCurrentPlayTime());
                    intent.putExtra("EXTRA_PLAY_TIME_Y", driftY.getCurrentPlayTime());
                }
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
    }

    private void setupParallax(long playTimeX, long playTimeY) {
        if (ivBackground != null) {
            // Đảm bảo ảnh phủ chiều rộng tối ưu cho hiệu ứng trôi
            ivBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivBackground.setScaleX(1.3f); // Tăng scale lên 1.3 để đủ 'đất' trôi ngang
            ivBackground.setScaleY(1.3f);

            driftX = ObjectAnimator.ofFloat(ivBackground, "translationX", -60f, 60f);
            driftX.setDuration(15000);
            driftX.setRepeatMode(ValueAnimator.REVERSE);
            driftX.setRepeatCount(ValueAnimator.INFINITE);

            driftY = ObjectAnimator.ofFloat(ivBackground, "translationY", -40f, 40f);
            driftY.setDuration(20000);
            driftY.setRepeatMode(ValueAnimator.REVERSE);
            driftY.setRepeatCount(ValueAnimator.INFINITE);

            driftX.start();
            driftY.start();
            
            // Nhảy đến đúng nhịp thời gian kế thừa
            driftX.setCurrentPlayTime(playTimeX);
            driftY.setCurrentPlayTime(playTimeY);
        }
    }

    private void setupOnboardingItems() {
        List<OnboardingItem> items = new ArrayList<>();
        items.add(new OnboardingItem(R.drawable.ads1, "Fastest Spin in the world", "Mosco Galaxy"));
        items.add(new OnboardingItem(R.drawable.ads2, "Secure Platform for Collectors", "Mosco Galaxy"));
        items.add(new OnboardingItem(R.drawable.ads3, "Gacha Everything with Convenience", "Mosco Galaxy"));

        adapter = new OnboardingAdapter(items);
        viewPager.setAdapter(adapter);

        // --- GIẢI PHÁP FIX "KHỰNG" KHI CHUYỂN TRANG LẦN ĐẦU ---
        // Load sẵn ít nhất 1 trang tiếp theo vào bộ nhớ đệm. 
        // Điều này khiến trang 2 được render ngay khi trang 1 vừa hiển thị xong.
        viewPager.setOffscreenPageLimit(1);
    }

    private void setupDots() {
        ImageView[] dots = new ImageView[adapter.getItemCount()];
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(8, 0, 8, 0);

        for (int i = 0; i < dots.length; i++) {
            dots[i] = new ImageView(getApplicationContext());
            dots[i].setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.bg_dot_inactive));
            dots[i].setLayoutParams(layoutParams);
            layoutDots.addView(dots[i]);
        }
    }

    private void setCurrentDot(int index) {
        int childCount = layoutDots.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ImageView imageView = (ImageView) layoutDots.getChildAt(i);
            if (i == index) {
                imageView.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.bg_dot_active));
            } else {
                imageView.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.bg_dot_inactive));
            }
        }
    }
}

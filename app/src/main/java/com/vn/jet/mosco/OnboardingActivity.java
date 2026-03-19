package com.vn.jet.mosco;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        layoutDots = findViewById(R.id.layout_dots);
        btnNext = findViewById(R.id.btn_next);
        viewPager = findViewById(R.id.viewPager);

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
                startActivity(new Intent(getApplicationContext(), SignInActivity.class));
            }
        });
    }

    private void setupOnboardingItems() {
        List<OnboardingItem> items = new ArrayList<>();
        items.add(new OnboardingItem(R.drawable.ads1, "Fastest Payment in the world", "Nord Bank"));
        items.add(new OnboardingItem(R.drawable.ads2, "The most Secure Platform for Customer", "Nord Bank"));
        items.add(new OnboardingItem(R.drawable.ads3, "Paying for Everything is Easy and Convenient", "Nord Bank"));

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

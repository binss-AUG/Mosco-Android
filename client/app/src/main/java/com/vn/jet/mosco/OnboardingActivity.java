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
import com.vn.jet.mosco.widget.MoscoButton;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.vn.jet.mosco.adapter.OnboardingAdapter;
import com.vn.jet.mosco.model.OnboardingItem;
import com.vn.jet.mosco.utils.ClickDebounce;
import com.vn.jet.mosco.utils.AuthUIHelper;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private OnboardingAdapter adapter;
    private LinearLayout layoutDots;
    private MoscoButton btnNext;
    private ViewPager2 viewPager;
    
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        
        com.vn.jet.mosco.utils.GalacticBackgroundView galacticBg = findViewById(R.id.galactic_bg);
        if (galacticBg != null) {
            galacticBg.setMode(com.vn.jet.mosco.utils.GalacticBackgroundView.Mode.ONBOARDING);
        }

        layoutDots = findViewById(R.id.layout_dots);
        btnNext = findViewById(R.id.btn_next);
        btnNext.setDebounceTime(0); // Tắt hoàn toàn click debounce để nút phản hồi tức thì
        viewPager = findViewById(R.id.viewPager);
        

        AuthUIHelper.animateAurora(this);
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
                
                // Cập nhật nhãn nút động dựa trên trang hiện tại để tối ưu trải nghiệm (UX)
                if (adapter != null) {
                    if (position == adapter.getItemCount() - 1) {
                        btnNext.setText(R.string.action_get_started);
                    } else {
                        btnNext.setText(R.string.action_next);
                    }
                }
            }
        });

        // Loại bỏ hoàn toàn ClickDebounce và sử dụng View.OnClickListener thông thường
        // để nút bấm phản hồi tức thì, chuyển trang hoặc mở SignInActivity ngay lập tức khi click.
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (viewPager.getCurrentItem() + 1 < adapter.getItemCount()) {
                    viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
                } else {
                    Intent intent = new Intent(OnboardingActivity.this, SignInActivity.class);
                    startActivity(intent);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        AuthUIHelper.saveAnimationState();
    }

    private void setupParallax(long playTimeX, long playTimeY) {
        // Disabled to use AuthUIHelper instead
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
        int dotWidth = getResources().getDimensionPixelSize(R.dimen.page_indicator_width);
        int dotHeight = getResources().getDimensionPixelSize(R.dimen.page_indicator_height);
        int dotSpacing = getResources().getDimensionPixelSize(R.dimen.page_indicator_spacing);

        ImageView[] dots = new ImageView[adapter.getItemCount()];
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dotWidth, dotHeight);
        layoutParams.setMargins(dotSpacing, 0, dotSpacing, 0);

        for (int i = 0; i < dots.length; i++) {
            dots[i] = new ImageView(getApplicationContext());
            dots[i].setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.bg_dot_inactive));
            dots[i].setLayoutParams(layoutParams);
            layoutDots.addView(dots[i]);
        }
    }

    private void setCurrentDot(int index) {
        int childCount = layoutDots.getChildCount();
        int duration = getResources().getInteger(R.integer.daily_indicator_scale_duration);
        float activeScale = getResources().getInteger(R.integer.daily_indicator_scale_active_percent) / 100f;
        float inactiveScale = getResources().getInteger(R.integer.daily_indicator_scale_inactive_percent) / 100f;

        for (int i = 0; i < childCount; i++) {
            ImageView imageView = (ImageView) layoutDots.getChildAt(i);
            if (imageView == null) continue;

            if (i == index) {
                imageView.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.bg_dot_active));
                // Ép hiệu ứng scaleX co giãn dẹt theo tỷ lệ để tạo sự sinh động khi page được chọn
                imageView.animate().scaleX(activeScale).setDuration(duration).start();
            } else {
                imageView.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.bg_dot_inactive));
                // Trả scaleX về nguyên bản khi page bị bỏ chọn để dot thu gọn lại thành thanh dẹt cơ bản
                imageView.animate().scaleX(inactiveScale).setDuration(duration).start();
            }
        }
    }
}

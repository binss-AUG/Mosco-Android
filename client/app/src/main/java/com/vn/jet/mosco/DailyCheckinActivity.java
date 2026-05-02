package com.vn.jet.mosco;

import android.animation.ArgbEvaluator;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình Điểm danh hằng ngày — Option 2: Holographic Horizontal Carousel.
 * Sử dụng ViewPager2 với PageTransformer 3D và chuyển đổi màu nền động.
 * Đã khử Hardcode và thêm ảnh Demo.
 */
public class DailyCheckinActivity extends AppCompatActivity {

    private static final String TAG = "DailyCheckinActivity";
    private GameApiService apiService;
    private ViewPager2 vpDaily;
    private DailyBannerAdapter adapter;
    private View viewBgOverlay;
    private View[] dots;

    // Màu sắc đại diện cho 3 buổi lấy từ resources
    private int[] bgColors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_checkin);

        apiService = ApiClient.getClient(this).create(GameApiService.class);
        viewBgOverlay = findViewById(R.id.view_bg_overlay);
        vpDaily = findViewById(R.id.vp_daily_cards);
        
        dots = new View[]{
            findViewById(R.id.dot_0),
            findViewById(R.id.dot_1),
            findViewById(R.id.dot_2)
        };

        // Khởi tạo màu sắc từ resources để tránh hardcode
        bgColors = new int[]{
            ContextCompat.getColor(this, R.color.daily_morning_bg),
            ContextCompat.getColor(this, R.color.daily_noon_bg),
            ContextCompat.getColor(this, R.color.daily_evening_bg)
        };

        findViewById(R.id.btn_back_daily).setOnClickListener(v -> finish());

        setupViewPager();
        startFloatingAnimation();
        loadDailyStatus();
    }

    private void startFloatingAnimation() {
        android.animation.ObjectAnimator floatingAnim = android.animation.ObjectAnimator.ofFloat(vpDaily, "translationY", 0f, -15f);
        floatingAnim.setDuration(2200);
        floatingAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        floatingAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        floatingAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        floatingAnim.start();
    }

    private void setupViewPager() {
        List<DailySlotData> slots = new ArrayList<>();
        // Sử dụng ảnh Demo ads1, ads2, ads3 và màu accent từ resources
        slots.add(new DailySlotData(getString(R.string.daily_morning), "06:00 - 11:59", 
                "Bắt đầu ngày mới đầy năng lượng với quà tặng từ Mosco.", 500, 1, 
                R.drawable.ads1, ContextCompat.getColor(this, R.color.daily_morning_accent)));
        
        slots.add(new DailySlotData(getString(R.string.daily_afternoon), "12:00 - 17:59", 
                "Nghỉ trưa thư giãn và nhận thêm chút 'vốn' cho hành trình.", 800, 2, 
                R.drawable.ads2, ContextCompat.getColor(this, R.color.daily_noon_accent)));
        
        slots.add(new DailySlotData(getString(R.string.daily_evening), "18:00 - 23:59", 
                "Kết thúc một ngày tuyệt vời với phần thưởng giá trị nhất.", 1200, 3, 
                R.drawable.ads3, ContextCompat.getColor(this, R.color.daily_evening_accent)));

        adapter = new DailyBannerAdapter(slots);
        vpDaily.setAdapter(adapter);
        vpDaily.setOffscreenPageLimit(3);

        // Custom 3D Page Transformer với khử răng cưa (Anti-aliasing)
        vpDaily.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            float scale = 0.85f + (1 - absPos) * 0.15f;
            
            // Debug & Fix: Sử dụng Hardware Layer để khử răng cưa góc nhọn khi xoay 3D
            if (position == 0) {
                page.setLayerType(View.LAYER_TYPE_NONE, null);
            } else {
                page.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }

            page.setScaleX(scale);
            page.setScaleY(scale);
            page.setRotationY(position * -20f);
            page.setAlpha(0.5f + (1 - absPos) * 0.5f);
            page.setTranslationZ((1 - absPos) * 10f);
        });

        // Dynamic Background & Indicator Update
        vpDaily.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (position < bgColors.length - 1) {
                    int color = (int) new ArgbEvaluator().evaluate(positionOffset, bgColors[position], bgColors[position + 1]);
                    viewBgOverlay.setBackgroundColor(color);
                    viewBgOverlay.setAlpha(0.85f);
                }
            }

            @Override
            public void onPageSelected(int position) {
                updateIndicators(position);
            }
        });
    }

    private void updateIndicators(int activePos) {
        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackgroundResource(i == activePos ? R.drawable.bg_indicator_active : R.drawable.bg_indicator_inactive);
            dots[i].animate().scaleX(i == activePos ? 1.4f : 1.0f).setDuration(250).start();
        }
    }

    private void loadDailyStatus() {
        apiService.getDailyStatus().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONObject data = json.optJSONObject("data");
                        if (data == null) return;

                        JSONObject statuses = data.optJSONObject("slotStatuses");
                        if (statuses == null) return;

                        adapter.updateStatuses(new String[]{
                            statuses.optString("0", "locked"),
                            statuses.optString("1", "locked"),
                            statuses.optString("2", "locked")
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing daily status", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Connection error", t);
            }
        });
    }

    private void claimSlot(int position) {
        apiService.claimDaily().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        String message = json.optString("message", "Check-in successful!");
                        Toast.makeText(DailyCheckinActivity.this, message, Toast.LENGTH_SHORT).show();
                        loadDailyStatus();
                    } else {
                        String errorMsg = "Cannot check in";
                        if (response.errorBody() != null) {
                            JSONObject errJson = new JSONObject(response.errorBody().string());
                            errorMsg = errJson.optString("message", errorMsg);
                        }
                        Toast.makeText(DailyCheckinActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing claim", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(DailyCheckinActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- INNER CLASSES ---

    private static class DailySlotData {
        String title, time, desc;
        int coins, diamonds, bannerRes, accentColor;
        String status = "locked";

        DailySlotData(String title, String time, String desc, int coins, int diamonds, int bannerRes, int accentColor) {
            this.title = title;
            this.time = time;
            this.desc = desc;
            this.coins = coins;
            this.diamonds = diamonds;
            this.bannerRes = bannerRes;
            this.accentColor = accentColor;
        }
    }

    private class DailyBannerAdapter extends RecyclerView.Adapter<DailyBannerAdapter.VH> {
        private final List<DailySlotData> items;

        DailyBannerAdapter(List<DailySlotData> items) {
            this.items = items;
        }

        public void updateStatuses(String[] statuses) {
            for (int i = 0; i < items.size(); i++) {
                items.get(i).status = statuses[i];
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_daily_banner_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DailySlotData data = items.get(position);
            holder.tvTitle.setText(data.title);
            holder.tvTime.setText(data.time);
            holder.tvDesc.setText(data.desc);
            holder.tvCoins.setText(String.valueOf(data.coins));
            holder.tvDiamonds.setText(String.valueOf(data.diamonds));
            holder.ivBanner.setImageResource(data.bannerRes);
            holder.ivBanner.setColorFilter(0, android.graphics.PorterDuff.Mode.SRC_OVER);
            
            // Đặc biệt: Đồng bộ viền (stroke) với màu chủ đạo của buổi
            holder.cvCard.setStrokeColor(android.content.res.ColorStateList.valueOf(data.accentColor));
            
            updateButton(holder, data.status, data.accentColor);

            holder.btnClaim.setOnClickListener(v -> claimSlot(position));
        }

        private void updateButton(VH holder, String status, int accentColor) {
            holder.btnClaim.setClickable(false);
            holder.btnClaim.setEnabled(false);
            
            switch (status) {
                case "claimed":
                    holder.tvClaimText.setText("CLAIMED");
                    holder.btnClaim.setCardBackgroundColor(0xFF444444);
                    holder.btnClaim.setAlpha(0.6f);
                    break;
                case "available":
                    holder.tvClaimText.setText("CLAIM");
                    holder.btnClaim.setCardBackgroundColor(accentColor);
                    holder.btnClaim.setAlpha(1.0f);
                    holder.btnClaim.setClickable(true);
                    holder.btnClaim.setEnabled(true);
                    break;
                case "locked":
                default:
                    holder.tvClaimText.setText("CLAIM");
                    holder.btnClaim.setCardBackgroundColor(0xFF333333);
                    holder.btnClaim.setAlpha(0.35f);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvTime, tvDesc, tvCoins, tvDiamonds, tvClaimText;
            ImageView ivBanner;
            com.google.android.material.card.MaterialCardView btnClaim, cvCard;

            VH(@NonNull View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_session_title);
                tvTime = v.findViewById(R.id.tv_time_range);
                tvDesc = v.findViewById(R.id.tv_session_desc);
                tvCoins = v.findViewById(R.id.tv_reward_coin);
                tvDiamonds = v.findViewById(R.id.tv_reward_diamond);
                tvClaimText = v.findViewById(R.id.tv_claim_text);
                ivBanner = v.findViewById(R.id.iv_banner_artwork);
                btnClaim = v.findViewById(R.id.btn_claim_daily);
                cvCard = v.findViewById(R.id.cv_daily_card);
                
                // Ép clipping tuyệt đối để sửa lỗi lòi góc ảnh
                cvCard.setClipToOutline(true);
            }
        }
    }
}

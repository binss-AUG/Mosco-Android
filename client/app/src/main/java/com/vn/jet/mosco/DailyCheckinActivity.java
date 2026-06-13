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
import androidx.core.graphics.ColorUtils;
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
public class DailyCheckinActivity extends MoscoBaseActivity {

    private static final String TAG = "DailyCheckinActivity";
    
    // Khai báo hằng số cho trạng thái của Slot điểm danh để tránh hardcode chuỗi
    private static final String STATUS_CLAIMED = "claimed";
    private static final String STATUS_AVAILABLE = "available";
    private static final String STATUS_LOCKED = "locked";

    // Khai báo khóa JSON trả về từ API
    private static final String SLOT_KEY_MORNING = "0";
    private static final String SLOT_KEY_AFTERNOON = "1";
    private static final String SLOT_KEY_EVENING = "2";

    private GameApiService apiService;
    private ViewPager2 vpDaily;
    private DailyBannerAdapter adapter;
    private View viewBgOverlay, viewHeaderAccent;
    private View[] dots;
    
    // Màu sắc accent tương ứng cho 3 buổi
    private int[] accentColors;

    // Màu sắc đại diện cho 3 buổi lấy từ resources
    private int[] bgColors;

    // Lưu trữ tạm các thông số vẽ của Transformer để tối ưu hóa CPU/GPU khi scroll liên tục
    private float transformerScaleBase;
    private float transformerScaleFactor;
    private float transformerRotationFactor;
    private float transformerTranslationDivisor;
    private float bgOverlayAlpha;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_checkin);

        apiService = ApiClient.getClient(this).create(GameApiService.class);
        viewBgOverlay = findViewById(R.id.view_bg_overlay);
        viewHeaderAccent = findViewById(R.id.view_header_accent);
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
        
        accentColors = new int[]{
            ContextCompat.getColor(this, R.color.daily_morning_accent),
            ContextCompat.getColor(this, R.color.daily_noon_accent),
            ContextCompat.getColor(this, R.color.daily_evening_accent)
        };

        // Thiết lập trạng thái màu sắc ban đầu để tránh giật hình khi mới vào
        viewBgOverlay.setBackgroundColor(bgColors[0]);
        viewBgOverlay.setAlpha(1.0f);
        viewHeaderAccent.setBackgroundColor(accentColors[0]);

        // Đọc trước các thông số giao diện để tránh lookup từ XML trong vòng lặp Render/Scroll
        transformerScaleBase = getResources().getInteger(R.integer.daily_transformer_scale_base_percent) / 100f;
        transformerScaleFactor = getResources().getInteger(R.integer.daily_transformer_scale_factor_percent) / 100f;
        transformerRotationFactor = (float) getResources().getInteger(R.integer.daily_transformer_rotation);
        transformerTranslationDivisor = getResources().getInteger(R.integer.daily_transformer_translation_divisor) / 10f;
        bgOverlayAlpha = getResources().getInteger(R.integer.daily_bg_overlay_alpha_percent) / 100f;

        findViewById(R.id.btn_back_daily).setOnClickListener(v -> finish());

        setupViewPager();
        startFloatingAnimation();
        loadDailyStatus();
    }

    private void startFloatingAnimation() {
        // Tải translationY và duration từ resources để tránh hardcode
        float startY = 0f;
        float endY = getResources().getDimension(R.dimen.daily_floating_translation_y);
        int duration = getResources().getInteger(R.integer.daily_floating_anim_duration);

        android.animation.ObjectAnimator floatingAnim = android.animation.ObjectAnimator.ofFloat(vpDaily, "translationY", startY, endY);
        floatingAnim.setDuration(duration);
        floatingAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        floatingAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        floatingAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        floatingAnim.start();
    }

    private void setupViewPager() {
        List<DailySlotData> slots = new ArrayList<>();
        // Tải dữ liệu các buổi từ resources để tránh hardcode
        slots.add(new DailySlotData(
                getString(R.string.daily_label_morning),
                getString(R.string.daily_time_morning), 
                getString(R.string.daily_desc_morning),
                getResources().getInteger(R.integer.daily_coins_morning),
                getResources().getInteger(R.integer.daily_slot_morning), 
                R.drawable.ads1,
                ContextCompat.getColor(this, R.color.daily_morning_accent)
        ));
        
        slots.add(new DailySlotData(
                getString(R.string.daily_label_afternoon),
                getString(R.string.daily_time_afternoon), 
                getString(R.string.daily_desc_afternoon),
                getResources().getInteger(R.integer.daily_coins_afternoon),
                getResources().getInteger(R.integer.daily_slot_afternoon), 
                R.drawable.ads2,
                ContextCompat.getColor(this, R.color.daily_noon_accent)
        ));
        
        slots.add(new DailySlotData(
                getString(R.string.daily_label_evening),
                getString(R.string.daily_time_evening), 
                getString(R.string.daily_desc_evening),
                getResources().getInteger(R.integer.daily_coins_evening),
                getResources().getInteger(R.integer.daily_slot_evening), 
                R.drawable.ads3,
                ContextCompat.getColor(this, R.color.daily_evening_accent)
        ));

        adapter = new DailyBannerAdapter(slots);
        vpDaily.setAdapter(adapter);
        vpDaily.setOffscreenPageLimit(slots.size());

        // Custom 3D Page Transformer tinh chỉnh bằng các thông số động từ resources
        vpDaily.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            float scale = transformerScaleBase + (1f - absPos) * transformerScaleFactor;
            
            // Xoay card tránh đâm vào nhau gây clipping
            float rotation = position * transformerRotationFactor; 
            
            // Tinh chỉnh translation để giữ card tập trung và mượt mà
            float translationX = -position * (page.getWidth() / transformerTranslationDivisor);

            page.setTranslationX(translationX);
            page.setScaleX(scale);
            page.setScaleY(scale);
            page.setRotationY(rotation);
            
            // Focus Logic: Chỉ hiện card chính diện, ẩn các card lân cận khi idle
            page.setAlpha(Math.max(0f, 1f - absPos));
            
            // Tăng tốc phần cứng khi đang scroll để góc bo tròn mượt mà
            if (absPos > 0f && absPos < 1f) {
                page.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            } else {
                page.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });

        // Đổi màu nền và màu Accent động theo vị trí cuộn trang
        vpDaily.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (position < bgColors.length - 1) {
                    ArgbEvaluator evaluator = new ArgbEvaluator();
                    int bgColor = (int) evaluator.evaluate(positionOffset, bgColors[position], bgColors[position + 1]);
                    viewBgOverlay.setBackgroundColor(bgColor);
                    viewBgOverlay.setAlpha(bgOverlayAlpha);
                    
                    int accentColor = (int) evaluator.evaluate(positionOffset, accentColors[position], accentColors[position + 1]);
                    viewHeaderAccent.setBackgroundColor(accentColor);
                } else if (position == bgColors.length - 1) {
                    viewBgOverlay.setBackgroundColor(bgColors[position]);
                    viewBgOverlay.setAlpha(bgOverlayAlpha);
                    viewHeaderAccent.setBackgroundColor(accentColors[position]);
                }
            }

            @Override
            public void onPageSelected(int position) {
                updateIndicators(position);
            }
        });
    }

    private void updateIndicators(int activePos) {
        int activeAlpha = getResources().getInteger(R.integer.daily_dot_active_alpha);
        int inactiveAlpha = getResources().getInteger(R.integer.daily_dot_inactive_alpha);
        float activeScale = getResources().getInteger(R.integer.daily_indicator_scale_active_percent) / 100f;
        float inactiveScale = getResources().getInteger(R.integer.daily_indicator_scale_inactive_percent) / 100f;
        int duration = getResources().getInteger(R.integer.daily_indicator_scale_duration);

        for (int i = 0; i < dots.length; i++) {
            // Tạo drawable động để đổi màu dot tương ứng với từng buổi sáng/trưa/tối
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            // Đồng bộ corner radius bằng cách lấy giá trị chuẩn từ resources thay vì hardcode 2dp
            drawable.setCornerRadius(getResources().getDimension(R.dimen.page_indicator_radius));
            
            // Tại sao (WHY): Lấy màu của trang hiện tại (activePos) để áp dụng cho TẤT CẢ các dot, 
            // tạo sự đồng nhất về theme khi người dùng vuốt thẻ (trước đây mỗi dot bị fix cứng 1 màu)
            int baseColor = accentColors[activePos];
            int alpha = (i == activePos) ? activeAlpha : inactiveAlpha;
            int color = ColorUtils.setAlphaComponent(baseColor, alpha);
            drawable.setColor(color);
            dots[i].setBackground(drawable);
            
            float scaleX = (i == activePos) ? activeScale : inactiveScale;
            dots[i].animate().scaleX(scaleX).setDuration(duration).start();
        }
    }

    private void loadDailyStatus() {
        // [CHEAT MODE] - Bật tất cả các mốc thành Available để test UI (Quét Laser)
        if (true) {
            adapter.updateStatuses(new String[]{
                STATUS_AVAILABLE, STATUS_AVAILABLE, STATUS_AVAILABLE
            });
            return;
        }

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
                            statuses.optString(SLOT_KEY_MORNING, STATUS_LOCKED),
                            statuses.optString(SLOT_KEY_AFTERNOON, STATUS_LOCKED),
                            statuses.optString(SLOT_KEY_EVENING, STATUS_LOCKED)
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
        // [CHEAT MODE] - Tự động chạy animation rớt Stamp và hiện Dialog xịn xò không cần API
        if (true) {
            long fakeCoins = 999;
            long fakeDiamonds = 50;
            
            RecyclerView rv = (RecyclerView) vpDaily.getChildAt(0);
            RecyclerView.ViewHolder holder = rv != null ? rv.findViewHolderForAdapterPosition(position) : null;
            if (holder instanceof DailyBannerAdapter.VH) {
                DailyBannerAdapter.VH vh = (DailyBannerAdapter.VH) holder;
                vh.tvStampClaimed.setVisibility(View.VISIBLE);
                vh.tvStampClaimed.setTextColor(adapter.items.get(position).accentColor);
                vh.tvStampClaimed.setScaleX(3f);
                vh.tvStampClaimed.setScaleY(3f);
                vh.tvStampClaimed.setAlpha(0f);
                
                vh.tvStampClaimed.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(250)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.0f))
                        .withEndAction(() -> {
                            vh.itemView.animate().translationX(15f).setDuration(40).withEndAction(() -> {
                                vh.itemView.animate().translationX(-15f).setDuration(40).withEndAction(() -> {
                                    vh.itemView.animate().translationX(0f).setDuration(40).start();
                                }).start();
                            }).start();
                            
                            vpDaily.postDelayed(() -> {
                                showRewardDialog(fakeCoins, fakeDiamonds);
                                // Fake chuyển trạng thái thẻ thành CLAIMED
                                adapter.items.get(position).status = STATUS_CLAIMED;
                                adapter.notifyItemChanged(position);
                            }, 400);
                        }).start();
            } else {
                showRewardDialog(fakeCoins, fakeDiamonds);
            }
            return;
        }

        apiService.claimDaily().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            long coins = data.optLong("coinsRewarded", 0);
                            long diamonds = data.optLong("diamondsRewarded", 0);
                            
                            // Tại sao (WHY): Trì hoãn việc hiển thị Dialog phần thưởng để Animation "Đóng dấu" (Stamp)
                            // có đủ thời gian chạy và rung thẻ, tạo phản hồi thị giác (Feedback) uy lực cho Gacha UI.
                            RecyclerView rv = (RecyclerView) vpDaily.getChildAt(0);
                            RecyclerView.ViewHolder holder = rv != null ? rv.findViewHolderForAdapterPosition(position) : null;
                            if (holder instanceof DailyBannerAdapter.VH) {
                                DailyBannerAdapter.VH vh = (DailyBannerAdapter.VH) holder;
                                vh.tvStampClaimed.setVisibility(View.VISIBLE);
                                vh.tvStampClaimed.setTextColor(adapter.items.get(position).accentColor);
                                vh.tvStampClaimed.setScaleX(3f);
                                vh.tvStampClaimed.setScaleY(3f);
                                vh.tvStampClaimed.setAlpha(0f);
                                
                                vh.tvStampClaimed.animate()
                                        .scaleX(1f).scaleY(1f).alpha(1f)
                                        .setDuration(250)
                                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.0f))
                                        .withEndAction(() -> {
                                            // Rung thẻ (Shake effect)
                                            vh.itemView.animate().translationX(15f).setDuration(40).withEndAction(() -> {
                                                vh.itemView.animate().translationX(-15f).setDuration(40).withEndAction(() -> {
                                                    vh.itemView.animate().translationX(0f).setDuration(40).start();
                                                }).start();
                                            }).start();
                                            
                                            vpDaily.postDelayed(() -> {
                                                showRewardDialog(coins, diamonds);
                                                loadDailyStatus();
                                            }, 400);
                                        }).start();
                            } else {
                                showRewardDialog(coins, diamonds);
                                loadDailyStatus();
                            }
                        } else {
                            loadDailyStatus();
                        }
                    } else {
                        String errorMsg = getString(R.string.daily_error_claim);
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
                Toast.makeText(DailyCheckinActivity.this, getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRewardDialog(long coins, long diamonds) {
        android.app.Dialog dialog = new android.app.Dialog(this, R.style.RewardOverlayTheme);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reward_overlay, null);
        dialog.setContentView(dialogView);
        dialog.setCancelable(false);

        // Update to Daily specific strings
        ((TextView) dialogView.findViewById(R.id.tv_reward_title)).setText(R.string.daily_msg_reward_title);
        ((TextView) dialogView.findViewById(R.id.tv_reward_subtitle)).setText(R.string.daily_msg_reward_subtitle);
        ((TextView) dialogView.findViewById(R.id.tv_reward_footer_hint)).setText(R.string.daily_msg_footer_hint);

        TextView tvCoins = dialogView.findViewById(R.id.tv_reward_coins);
        TextView tvDiamonds = dialogView.findViewById(R.id.tv_reward_diamonds);
        tvCoins.setText(String.format("%,d", coins));
        tvDiamonds.setText(String.format("%,d", diamonds));

        // Tải độ mờ (dim alpha) từ resources nếu phần thưởng bằng 0
        float dimAlpha = getResources().getInteger(R.integer.daily_reward_dim_alpha_percent) / 100f;
        if (coins <= 0) {
            dialogView.findViewById(R.id.layout_reward_coins).setAlpha(dimAlpha);
        }
        if (diamonds <= 0) {
            dialogView.findViewById(R.id.layout_reward_diamonds).setAlpha(dimAlpha);
        }

        dialogView.findViewById(R.id.root_reward_layout).setOnClickListener(v -> dialog.dismiss());
        dialogView.setOnClickListener(v -> dialog.dismiss());

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        dialog.show();
    }

    // --- INNER CLASSES ---

    private static class DailySlotData {
        String title, time, desc;
        int coins, diamonds, bannerRes, accentColor;
        String status = STATUS_LOCKED;

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
            
            // Fix: Ép ViewPager2 tính toán lại Transformer để tránh card bị ẩn sau khi refresh
            vpDaily.post(() -> vpDaily.requestTransform());
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_daily_banner_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DailySlotData data = items.get(position);
            android.content.Context context = holder.itemView.getContext();
            
            // Đảm bảo card luôn hiển thị khi bind (trước khi Transformer can thiệp)
            holder.itemView.setAlpha(1.0f);
            
            holder.tvTitle.setText(data.title);
            holder.tvTime.setText(data.time);
            holder.tvDesc.setText(data.desc);
            holder.tvCoins.setText(String.valueOf(data.coins));
            holder.tvDiamonds.setText(String.valueOf(data.diamonds));
            holder.ivBanner.setImageResource(data.bannerRes);
            holder.ivBanner.setColorFilter(0, android.graphics.PorterDuff.Mode.SRC_OVER);
            
            // Đặc biệt: Khôi phục giao diện kính mờ mặc định của Card theo yêu cầu của Sếp (không tô màu theo buổi để tránh trông nhựa)
            holder.cvCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white_05));
            holder.cvCard.setStrokeColor(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white_20)));
            
            // Đồng bộ màu accent cho đường trang trí bên trong card
            if (holder.viewAccentLine != null) {
                holder.viewAccentLine.setBackgroundColor(data.accentColor);
            }
            
            updateButton(holder, data.status, data.accentColor);

            holder.btnClaim.setOnClickListener(v -> claimSlot(position));
        }

        private void updateButton(VH holder, String status, int accentColor) {
            android.content.Context context = holder.itemView.getContext();
            holder.btnClaim.setClickable(false);
            holder.btnClaim.setEnabled(false);
            holder.btnClaim.setStrokeWidth((int) context.getResources().getDimension(R.dimen.stroke_thin));
            
            int colorWhite = ContextCompat.getColor(context, R.color.white);
            int colorTransparent = ContextCompat.getColor(context, R.color.transparent);

            switch (status) {
                case STATUS_CLAIMED:
                    holder.tvClaimText.setText(getString(R.string.daily_status_verified));
                    // Tại sao (WHY): Sử dụng thiết kế Outlined thuần khiết (nền trong suốt, viền và chữ mang màu accent) 
                    // để tạo sự tương phản rõ rệt với nút Available dạng Solid, tối giản hóa UI theo thiết kế chung.
                    holder.btnClaim.setCardBackgroundColor(colorTransparent);
                    holder.btnClaim.setStrokeColor(android.content.res.ColorStateList.valueOf(accentColor));
                    holder.tvClaimText.setTextColor(accentColor);
                    holder.btnClaim.setAlpha(1.0f);
                    
                    holder.tvStampClaimed.setVisibility(View.VISIBLE);
                    holder.tvStampClaimed.setTextColor(accentColor);
                    break;
                case STATUS_AVAILABLE:
                    holder.tvClaimText.setText(getString(R.string.daily_action_authenticate));
                    holder.btnClaim.setCardBackgroundColor(accentColor);
                    holder.btnClaim.setStrokeColor(android.content.res.ColorStateList.valueOf(colorTransparent));
                    holder.tvClaimText.setTextColor(colorWhite);
                    holder.btnClaim.setAlpha(1.0f);
                    holder.btnClaim.setClickable(true);
                    holder.btnClaim.setEnabled(true);
                    
                    holder.tvStampClaimed.setVisibility(View.GONE);
                    break;
                case STATUS_LOCKED:
                default:
                    holder.tvClaimText.setText("[ LOCKED ]");
                    int lockedBgAlpha = context.getResources().getInteger(R.integer.daily_btn_locked_bg_alpha);
                    int lockedStrokeAlpha = context.getResources().getInteger(R.integer.daily_btn_locked_stroke_alpha);
                    int lockedTextAlpha = context.getResources().getInteger(R.integer.daily_btn_locked_text_alpha);

                    holder.btnClaim.setCardBackgroundColor(ColorUtils.setAlphaComponent(accentColor, lockedBgAlpha));
                    holder.btnClaim.setStrokeColor(android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(accentColor, lockedStrokeAlpha)));
                    holder.tvClaimText.setTextColor(ColorUtils.setAlphaComponent(colorWhite, lockedTextAlpha));
                    holder.btnClaim.setAlpha(1.0f);
                    
                    holder.tvStampClaimed.setVisibility(View.GONE);
                    break;
            }
        }



        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvTime, tvDesc, tvCoins, tvDiamonds, tvClaimText;
            TextView tvStampClaimed;
            ImageView ivBanner;
            com.google.android.material.card.MaterialCardView btnClaim, cvCard;
            View viewAccentLine;

            VH(@NonNull View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_session_title);
                tvTime = v.findViewById(R.id.tv_time_range);
                tvDesc = v.findViewById(R.id.tv_session_desc);
                tvCoins = v.findViewById(R.id.tv_reward_coin);
                tvDiamonds = v.findViewById(R.id.tv_reward_diamond);
                tvClaimText = v.findViewById(R.id.tv_claim_text);
                tvStampClaimed = v.findViewById(R.id.tv_stamp_claimed);
                ivBanner = v.findViewById(R.id.iv_banner_artwork);
                btnClaim = v.findViewById(R.id.btn_claim_daily);
                cvCard = v.findViewById(R.id.cv_daily_card);
                viewAccentLine = v.findViewById(R.id.view_accent_line);
                
                // Ép clipping tuyệt đối để sửa lỗi lòi góc ảnh
                cvCard.setClipToOutline(true);
            }
        }
    }
}


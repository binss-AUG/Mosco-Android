package com.vn.jet.mosco.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.AppCompatButton;
import androidx.cardview.widget.CardView;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import com.bumptech.glide.Glide;
import com.google.gson.JsonObject;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.utils.SpinSystem;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class SpinFragment extends Fragment {

    private View btnAddObjet;
    private ImageView ivSelectedObjet;
    private AppCompatButton btnSpin;
    private ImageView ivBgCard1, ivBgCard2, ivBgCard3;
    private CardView cardCenterSlot;
    private VideoView videoSpinEffect;
    private FrameLayout videoContainer;
    
    // UI Phases
    private View layoutSpinMain;
    private View layoutRevealGrid;
    private RecyclerView rvSecretGrid;
    private AppCompatButton btnConfirmSelect;
    private SecretCardAdapter secretAdapter;
    private int selectedPosition = -1;
    private int secretCardHeight = 0;
    private OnBackPressedCallback backPressedCallback;

    // Result UI
    private View layoutResultReveal;
    private CardView cardResultFinal;
    private ImageView ivResultImage;
    private ImageView ivResultBack;
    private View viewNeonGlow;
    private AppCompatButton btnCollect;
    private TextView tvRevealUnchosen;
    private TextView tvResultTitle;
    private TextView tvResultSubtitle;

    // Reveal Result Grid UI
    private View layoutRevealResultGrid;
    private RecyclerView rvRevealResultGrid;
    private AppCompatButton btnTryAgain;
    private ImageView ivRevealBack;
    private RevealResultAdapter revealResultAdapter;
    private int revealCardHeight = 0;

    private SpinSystem spinSystem;
    private SpinSystem.SpinResult currentSpinResult;
    private List<JsonObject> gridSessionCards = new ArrayList<>();
    private int sessionResultIndex = -1;
    
    private volatile boolean videoComplete = false;
    private volatile boolean preloadComplete = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Khởi tạo SpinSystem ngầm
        new Thread(() -> {
            try {
                InputStream dbIs = getContext().getAssets().open("database.json");
                InputStream rateIs = getContext().getAssets().open("spinrate.json");
                spinSystem = new SpinSystem(null);
                spinSystem.loadData(new InputStreamReader(dbIs), new InputStreamReader(rateIs));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_spin, container, false);

        layoutSpinMain = view.findViewById(R.id.layout_spin_main);
        layoutRevealGrid = view.findViewById(R.id.layout_reveal_grid);
        rvSecretGrid = view.findViewById(R.id.rv_secret_grid);
        btnConfirmSelect = view.findViewById(R.id.btn_confirm_select);

        layoutResultReveal = view.findViewById(R.id.layout_result_reveal);
        cardResultFinal = view.findViewById(R.id.card_result_final);
        ivResultImage = view.findViewById(R.id.iv_result_image);
        ivResultBack = view.findViewById(R.id.iv_result_back);
        viewNeonGlow = view.findViewById(R.id.view_neon_glow);
        btnCollect = view.findViewById(R.id.btn_collect);
        tvRevealUnchosen = view.findViewById(R.id.tv_reveal_unchosen);
        tvResultTitle = view.findViewById(R.id.tv_result_title);
        tvResultSubtitle = view.findViewById(R.id.tv_result_subtitle);
        btnCollect.setOnClickListener(v -> resetToSpinMain());

        // Reveal Result Grid UI
        layoutRevealResultGrid = view.findViewById(R.id.layout_reveal_result_grid);
        rvRevealResultGrid = view.findViewById(R.id.rv_reveal_result_grid);
        btnTryAgain = view.findViewById(R.id.btn_try_again);
        ivRevealBack = view.findViewById(R.id.iv_reveal_back);

        // Reveal unchosen Objekts click listener
        tvRevealUnchosen.setOnClickListener(v -> showRevealResultGrid());

        // Back arrow từ reveal result grid → result screen
        ivRevealBack.setOnClickListener(v -> {
            layoutRevealResultGrid.setVisibility(View.GONE);
            layoutResultReveal.setVisibility(View.VISIBLE);
        });

        // Try again → reset to spin main
        btnTryAgain.setOnClickListener(v -> {
            layoutRevealResultGrid.setVisibility(View.GONE);
            resetToSpinMain();
        });

        cardCenterSlot = view.findViewById(R.id.card_center_slot);
        btnAddObjet = view.findViewById(R.id.btn_add_objet);
        ivSelectedObjet = view.findViewById(R.id.iv_selected_objet);
        btnSpin = view.findViewById(R.id.btn_spin);
        ivBgCard1 = view.findViewById(R.id.iv_bg_card_1);
        ivBgCard2 = view.findViewById(R.id.iv_bg_card_2);
        ivBgCard3 = view.findViewById(R.id.iv_bg_card_3);
        videoSpinEffect = view.findViewById(R.id.video_spin_effect);
        videoContainer = (FrameLayout) view.findViewById(R.id.video_container);

        view.setBackgroundColor(Color.BLACK);
        view.post(this::startBackgroundAnimation);

        getParentFragmentManager().setFragmentResultListener("objet_selection", this, (requestKey, result) -> {
            String imageUrl = result.getString("selected_objet_url");
            if (imageUrl != null) updateSelectedObjetUI(imageUrl);
        });

        cardCenterSlot.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.frame_layout, new SelectObjetFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnSpin.setOnClickListener(v -> showConfirmDialog());
        btnConfirmSelect.setOnClickListener(v -> {
            btnConfirmSelect.setEnabled(false);
            
            // QuanTum Swap Matrix: Giúp Card mà player bấm CHẮC CHẮN là result được bốc bởi Server
            if (selectedPosition != -1 && selectedPosition != sessionResultIndex && selectedPosition < gridSessionCards.size()) {
                JsonObject temp = gridSessionCards.get(selectedPosition);
                gridSessionCards.set(selectedPosition, currentSpinResult.result);
                gridSessionCards.set(sessionResultIndex, temp);
                sessionResultIndex = selectedPosition;
            }

            if (layoutRevealGrid != null) {
                // Screen fade before cutscene (opacity 100% -> 0% in 150ms)
                layoutRevealGrid.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                playRewardVideoAnimation();
                            }
                        })
                        .start();
            } else {
                playRewardVideoAnimation();
            }
        });

        // Chặn nút back khi ở trạng thái 16 thẻ
        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                // Không làm gì — chặn back
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);

        return view;
    }

    private void toggleBottomNavigation(boolean show) {
        if (getActivity() != null) {
            View navBar = getActivity().findViewById(R.id.bottom_navigation);
            if (navBar != null) navBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showConfirmDialog() {
        if (getContext() == null) return;
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_spin_confirm, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            dialog.dismiss();
            
            videoComplete = false;
            preloadComplete = false;
            playSpinVideoEffect();

            new Thread(() -> {
                if (spinSystem != null) {
                    currentSpinResult = spinSystem.spin();
                    if (currentSpinResult.result == null) {
                        preloadComplete = true;
                        checkReadyToReveal();
                        return;
                    }
                    
                    gridSessionCards.clear();
                    gridSessionCards.add(currentSpinResult.result);
                    if (currentSpinResult.revealGrid != null) {
                        gridSessionCards.addAll(currentSpinResult.revealGrid);
                    }
                    java.util.Collections.shuffle(gridSessionCards);
                    sessionResultIndex = gridSessionCards.indexOf(currentSpinResult.result);
                    
                    java.util.Set<String> urlsToLoad = new java.util.HashSet<>();
                    for (int i = 0; i < gridSessionCards.size(); i++) {
                        JsonObject card = gridSessionCards.get(i);
                        if (card.has("frontImage") && !card.get("frontImage").isJsonNull()) urlsToLoad.add(card.get("frontImage").getAsString());
                        if (i == sessionResultIndex) {
                            if (card.has("backImage") && !card.get("backImage").isJsonNull()) urlsToLoad.add(card.get("backImage").getAsString());
                        }
                    }
                    
                    int totalAssets = urlsToLoad.size();
                    if (totalAssets == 0) {
                        preloadComplete = true;
                        checkReadyToReveal();
                        return;
                    }
                    
                    java.util.concurrent.atomic.AtomicInteger loadedAssets = new java.util.concurrent.atomic.AtomicInteger(0);
                    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
                    
                    for (String u : urlsToLoad) {
                        pool.submit(() -> {
                            try {
                                if (!u.equals("dummy://trash_object")) {
                                    Glide.with(requireContext().getApplicationContext()).asBitmap().load(u).submit().get();
                                }
                            } catch (Exception e) { e.printStackTrace(); }
                            
                            int progress = loadedAssets.incrementAndGet();
                            if (progress == totalAssets) {
                                preloadComplete = true;
                                checkReadyToReveal();
                            }
                        });
                    }
                    pool.shutdown();
                }
            }).start();
        });
        dialog.show();
    }

    private void checkReadyToReveal() {
        if (videoComplete && preloadComplete) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (currentSpinResult == null || currentSpinResult.result == null) {
                        new AlertDialog.Builder(getContext())
                            .setTitle("Spin Result")
                            .setMessage("No reward!")
                            .setPositiveButton("OK", (d, w) -> resetToSpinMain())
                            .show();
                    } else {
                        spinGridSecret();
                    }
                });
            }
        }
    }

    private void handleVideoComplete() {
        videoComplete = true;
        checkReadyToReveal();
    }

    private void playSpinVideoEffect() {
        playVideo(R.raw.spin_animation, 0, 0, this::handleVideoComplete);
    }

    private void playRewardVideoAnimation() {
        if (selectedPosition == -1) return;
        if (backPressedCallback != null) backPressedCallback.setEnabled(false);
        playVideo(R.raw.spin_reward_animation, 100, 100, this::showFinalResultWithNeonEffect);
    }

    /**
     * @param fadeOutMs nếu > 0, fade out container trong N ms cuối video
     * @param earlyCompleteMs nếu > 0, gọi onComplete sớm hơn N ms trước khi video kết thúc
     */
    private void playVideo(int resId, int fadeOutMs, int earlyCompleteMs, Runnable onComplete) {
        if (videoSpinEffect == null || videoContainer == null || getContext() == null) return;
        if (layoutSpinMain != null) layoutSpinMain.setVisibility(View.GONE);
        if (layoutRevealGrid != null) layoutRevealGrid.setVisibility(View.GONE);
        if (layoutResultReveal != null) layoutResultReveal.setVisibility(View.GONE);
        if (layoutRevealResultGrid != null) layoutRevealResultGrid.setVisibility(View.GONE);
        toggleBottomNavigation(false);
        videoContainer.setVisibility(View.VISIBLE);
        videoContainer.setAlpha(1f);
        videoContainer.setBackgroundColor(Color.BLACK);
        
        // Disable elevation which causes shadow/white frame artifacts
        if (videoSpinEffect != null) {
            videoSpinEffect.animate().cancel();
            videoSpinEffect.setZ(0f);
            videoSpinEffect.setAlpha(0f); // Hide the underlying SurfaceView flash until video generates its first frame
        }

        FrameLayout.LayoutParams resetLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        resetLp.gravity = Gravity.CENTER;
        videoSpinEffect.setLayoutParams(resetLp);
        Uri videoUri = Uri.parse("android.resource://" + requireActivity().getPackageName() + "/" + resId);
        videoSpinEffect.setVideoURI(videoUri);
        final boolean[] completeCalled = {false};
        videoSpinEffect.setOnPreparedListener(mp -> {
            float videoRatio = mp.getVideoWidth() / (float) mp.getVideoHeight();
            int targetWidth = videoContainer.getWidth();
            int targetHeight = (int) (targetWidth / videoRatio);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(targetWidth, targetHeight);
            lp.gravity = Gravity.CENTER;
            videoSpinEffect.setLayoutParams(lp);
            videoSpinEffect.setScaleX(1.0f);
            videoSpinEffect.setScaleY(1.0f);
            mp.setLooping(false);
            
            // Show video instantly once hardware decodes first frame
            videoSpinEffect.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start();
                    
            videoSpinEffect.start();

            int duration = mp.getDuration();

            // Fade out trong N ms cuối video
            if (fadeOutMs > 0) {
                int fadeStartTime = Math.max(0, duration - fadeOutMs);
                videoContainer.postDelayed(() -> {
                    if (isAdded() && videoContainer != null) {
                        videoContainer.animate()
                                .alpha(0f)
                                .setDuration(fadeOutMs)
                                .setInterpolator(new LinearInterpolator())
                                .start();
                    }
                }, fadeStartTime);
            }

            // Gọi onComplete sớm hơn N ms trước khi video kết thúc
            if (earlyCompleteMs > 0) {
                int earlyTime = Math.max(0, duration - earlyCompleteMs);
                videoContainer.postDelayed(() -> {
                    if (isAdded() && !completeCalled[0]) {
                        completeCalled[0] = true;
                        onComplete.run();
                    }
                }, earlyTime);
            }
        });
        videoSpinEffect.setOnCompletionListener(mp -> {
            if (isAdded()) {
                videoContainer.animate().cancel();
                videoContainer.setAlpha(1f);
                videoContainer.setVisibility(View.GONE);
                if (!completeCalled[0]) {
                    completeCalled[0] = true;
                    onComplete.run();
                }
            }
        });
    }

    private void spinGridSecret() {
        toggleBottomNavigation(false);
        if (backPressedCallback != null) backPressedCallback.setEnabled(true);

        // Đợi 0.3s sau khi video tắt hẳn, rồi fade-in giao diện 16 thẻ
        if (layoutRevealGrid != null) {
            layoutRevealGrid.setAlpha(0f);
            layoutRevealGrid.setVisibility(View.VISIBLE);
        }

        if (rvSecretGrid != null) {
            rvSecretGrid.setLayoutManager(new GridLayoutManager(getContext(), 4));

            // Đợi layout đo xong, rồi tính kích thước thẻ
            rvSecretGrid.post(() -> {
                if (!isAdded()) return;

                float density = getResources().getDisplayMetrics().density;
                int itemMarginPx = (int) (4 * density); // 4dp margin mỗi bên

                // Tính chiều cao theo ratio 1:1.54 từ chiều rộng
                int availableWidth = rvSecretGrid.getWidth();
                int totalHorizontalMargins = itemMarginPx * 2 * 4; // left+right * 4 cols
                int itemWidth = (availableWidth - totalHorizontalMargins) / 4;
                secretCardHeight = (int) (itemWidth * 1.54f);

                secretAdapter = new SecretCardAdapter();
                rvSecretGrid.setAdapter(secretAdapter);
                
                // Start shine effect loop
                startShineEffectLoop();

                // Fade-in nhanh
                if (layoutRevealGrid != null) {
                    layoutRevealGrid.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .start();
                }
            });
        }
    }

    private String getCardImageUrl(int position, boolean isFront) {
        if (position >= 0 && position < gridSessionCards.size()) {
            JsonObject obj = gridSessionCards.get(position);
            String key = isFront ? "frontImage" : "backImage";
            if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsString();
            }
        }
        return "";
    }

    private void loadCardImageInto(String url, ImageView imageView) {
        if (!isAdded() || url == null || url.isEmpty() || imageView == null) return;
        try {
            if (url.equals("dummy://trash_object")) {
                Glide.with(this).load(R.drawable.trash_objet).into(imageView);
            } else {
                Glide.with(this).load(url).placeholder(R.drawable.objet_back_spin).into(imageView);
            }
        } catch (Exception e) {}
    }

    private void showFinalResultWithNeonEffect() {
        if (!isAdded()) return;

        layoutResultReveal.setVisibility(View.VISIBLE);

        // Ẩn title/subtitle ban đầu
        if (tvResultTitle != null) {
            tvResultTitle.setAlpha(0f);
            tvResultTitle.setVisibility(View.VISIBLE);
        }
        if (tvResultSubtitle != null) {
            tvResultSubtitle.setAlpha(0f);
            tvResultSubtitle.setVisibility(View.VISIBLE);
        }

        // Setup card: hiện ngay, scale 0.8
        cardResultFinal.setAlpha(1f);
        cardResultFinal.setScaleX(0.8f);
        cardResultFinal.setScaleY(0.8f);
        cardResultFinal.setRotationY(0f);

        // Neon glow: khởi tạo cùng scale, alpha 1.0 đè lên card
        viewNeonGlow.setScaleX(0.8f);
        viewNeonGlow.setScaleY(0.8f);
        viewNeonGlow.setAlpha(1.0f);
        viewNeonGlow.setVisibility(View.VISIBLE);

        // Camera distance cho 3D rotation đẹp
        float scale = getResources().getDisplayMetrics().density;
        cardResultFinal.setCameraDistance(8000 * scale);

        // Hiện mặt trước, ẩn mặt sau
        ivResultImage.setVisibility(View.VISIBLE);
        ivResultImage.setScaleX(1f);
        ivResultBack.setVisibility(View.GONE);
        ivResultBack.setScaleX(-1f); // Lật ngang để bù mirror khi rotationY 90°-270°

        // Load ảnh mặt trước
        String frontUrl = getCardImageUrl(selectedPosition, true);
        loadCardImageInto(frontUrl, ivResultImage);

        // Pre-load ảnh mặt sau
        String backUrl = getCardImageUrl(selectedPosition, false);
        loadCardImageInto(backUrl, ivResultBack);

        // === Phase 1: Neon Glow Fade 100% → 0% trong 1000ms ===
        viewNeonGlow.animate()
                .alpha(0f)
                .setDuration(1000)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewNeonGlow.setVisibility(View.GONE);

                        // Dừng 500ms trước Phase 2
                        cardResultFinal.postDelayed(() -> {
                            if (!isAdded()) return;

                            // === Phase 2: Zoom In → scale 1.4 trong 700ms ===
                            cardResultFinal.animate()
                                    .scaleX(1.4f)
                                    .scaleY(1.4f)
                                    .setDuration(700)
                                    .setInterpolator(new AccelerateDecelerateInterpolator())
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            // === Phase 3: Quick Zoom Out → scale 1.2 trong 100ms ===
                                            cardResultFinal.animate()
                                                    .scaleX(1.2f)
                                                    .scaleY(1.2f)
                                                    .setDuration(200)
                                                    .setInterpolator(new AccelerateDecelerateInterpolator())
                                                    .setListener(new AnimatorListenerAdapter() {
                                                        @Override
                                                        public void onAnimationEnd(Animator animation) {
                                                            // Dừng 300ms trước Phase 4
                                                            cardResultFinal.postDelayed(() -> {
                                                                if (!isAdded()) return;

                                                                // === Phase 4.1: Rotation 0° to 180° ===
                                                                ValueAnimator rot1 = ValueAnimator.ofFloat(0f, 180f);
                                                                rot1.setDuration(750);
                                                                rot1.setInterpolator(new AccelerateDecelerateInterpolator());
                                                                final boolean[] showingBack = {false};
                                                                rot1.addUpdateListener(anim -> {
                                                                    float val = (float) anim.getAnimatedValue();
                                                                    cardResultFinal.setRotationY(val);
                                                                    if (!showingBack[0] && val >= 90f) {
                                                                        showingBack[0] = true;
                                                                        ivResultImage.setVisibility(View.GONE);
                                                                        ivResultBack.setVisibility(View.VISIBLE);
                                                                    }
                                                                });
                                                                rot1.addListener(new AnimatorListenerAdapter() {
                                                                    @Override
                                                                    public void onAnimationEnd(Animator animation) {
                                                                        // PAUSE 500ms at 180°
                                                                        cardResultFinal.postDelayed(() -> {
                                                                            if (!isAdded()) return;
                                                                            
                                                                            // === Phase 4.2: Rotation 180° to 360° ===
                                                                            ValueAnimator rot2 = ValueAnimator.ofFloat(180f, 360f);
                                                                            rot2.setDuration(750); 
                                                                            rot2.setInterpolator(new AccelerateDecelerateInterpolator());
                                                                            rot2.addUpdateListener(anim2 -> {
                                                                                float val = (float) anim2.getAnimatedValue();
                                                                                cardResultFinal.setRotationY(val);
                                                                                if (showingBack[0] && val >= 270f) {
                                                                                    showingBack[0] = false;
                                                                                    ivResultBack.setVisibility(View.GONE);
                                                                                    ivResultImage.setVisibility(View.VISIBLE);
                                                                                }
                                                                            });
                                                                            rot2.addListener(new AnimatorListenerAdapter() {
                                                                                @Override
                                                                                public void onAnimationEnd(Animator animation) {
                                                                                    cardResultFinal.setRotationY(0f);
                                                                                    
                                                                                    // Show frame glowing effect
                                                                                    if (getView() != null) {
                                                                                        ImageView frameSpin = getView().findViewById(R.id.iv_frame_result_spin);
                                                                                        if (frameSpin != null) {
                                                                                            frameSpin.setVisibility(View.VISIBLE);
                                                                                            frameSpin.setAlpha(0f);
                                                                                            frameSpin.setScaleX(0.95f);
                                                                                            frameSpin.setScaleY(0.95f);
                                                                                            frameSpin.animate()
                                                                                                    .alpha(1f)
                                                                                                    .scaleX(1.0f)
                                                                                                    .scaleY(1.0f)
                                                                                                    .setDuration(300)
                                                                                                    .setInterpolator(new AccelerateDecelerateInterpolator())
                                                                                                    .start();
                                                                                        }
                                                                                    }

                                                                                    // === Phase 5: Final Zoom Reset ===
                                                                                    cardResultFinal.animate()
                                                                                            .scaleX(1.0f)
                                                                                            .scaleY(1.0f)
                                                                                            .setDuration(400)
                                                                                            .setInterpolator(new AccelerateDecelerateInterpolator())
                                                                                            .setListener(new AnimatorListenerAdapter() {
                                                                                                @Override
                                                                                                public void onAnimationEnd(Animator animation) {
                                                                                                    // Hiện thông tin
                                                                                                    showResultInfoText();
                                                                                                }
                                                                                            })
                                                                                            .start();
                                                                                }
                                                                            });
                                                                            rot2.start();
                                                                        }, 500);
                                                                    }
                                                                });
                                                                rot1.start();
                                                            }, 300);
                                                        }
                                                    })
                                                    .start();
                                        }
                                    })
                                    .start();
                        }, 100);
                    }
                })
                .start();
    }

    private void showResultInfoText() {
        // Fade-in title
        if (tvResultTitle != null) {
            tvResultTitle.setText("Spin was successful!");
            tvResultTitle.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .start();
        }
        // Fade-in subtitle
        if (tvResultSubtitle != null) {
            String subMsg = currentSpinResult != null && currentSpinResult.message != null 
                ? currentSpinResult.message : "You received the Objekt.";
            tvResultSubtitle.setText(subMsg);
            tvResultSubtitle.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .setStartDelay(100)
                    .start();
        }
        // Fade-in "Reveal unchosen Objekts"
        if (tvRevealUnchosen != null) {
            tvRevealUnchosen.setVisibility(View.VISIBLE);
            tvRevealUnchosen.animate()
                    .alpha(1f)
                    .setDuration(350)
                    .setStartDelay(200)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
        // Fade-in nút Done
        if (btnCollect != null) {
            btnCollect.setVisibility(View.VISIBLE);
            btnCollect.animate()
                    .alpha(1f)
                    .setDuration(350)
                    .setStartDelay(200)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }

    // ============================================================
    // Reveal Result Grid — hiện khi ấn "Reveal unchosen Objekts >"
    // ============================================================
    private void showRevealResultGrid() {
        if (!isAdded()) return;

        // Ẩn result, hiện reveal grid
        layoutResultReveal.setVisibility(View.GONE);
        layoutRevealResultGrid.setVisibility(View.VISIBLE);

        if (rvRevealResultGrid != null) {
            rvRevealResultGrid.setLayoutManager(new GridLayoutManager(getContext(), 4));

            rvRevealResultGrid.post(() -> {
                if (!isAdded()) return;

                float density = getResources().getDisplayMetrics().density;
                int itemMarginPx = (int) (4 * density);

                int availableWidth = rvRevealResultGrid.getWidth();
                int totalHorizontalMargins = itemMarginPx * 2 * 4;
                int itemWidth = (availableWidth - totalHorizontalMargins) / 4;
                revealCardHeight = (int) (itemWidth * 1.54f);

                revealResultAdapter = new RevealResultAdapter(selectedPosition);
                rvRevealResultGrid.setAdapter(revealResultAdapter);

                // Sau 0.6s, flip các thẻ còn lại
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isAdded() && revealResultAdapter != null) {
                        revealResultAdapter.revealAllCards();
                    }
                }, 600);
            });
        }
    }

    private void resetToSpinMain() {
        if (!isAdded()) return;
        // Ẩn result
        layoutResultReveal.setVisibility(View.GONE);
        // Ẩn reveal result grid
        if (layoutRevealResultGrid != null) layoutRevealResultGrid.setVisibility(View.GONE);
        // Reset btn_collect
        if (btnCollect != null) {
            btnCollect.setAlpha(0f);
            btnCollect.setVisibility(View.INVISIBLE);
        }
        // Reset reveal unchosen
        if (tvRevealUnchosen != null) {
            tvRevealUnchosen.setAlpha(0f);
            tvRevealUnchosen.setVisibility(View.INVISIBLE);
        }
        // Reset title/subtitle
        if (tvResultTitle != null) {
            tvResultTitle.animate().cancel();
            tvResultTitle.setAlpha(0f);
        }
        if (tvResultSubtitle != null) {
            tvResultSubtitle.animate().cancel();
            tvResultSubtitle.setAlpha(0f);
        }
        // Reset objet: rotation, scale, back image
        cardResultFinal.setScaleX(1f);
        cardResultFinal.setScaleY(1f);
        cardResultFinal.setRotationY(0f);
        if (ivResultBack != null) {
            ivResultBack.setVisibility(View.GONE);
        }
        if (ivResultImage != null) {
            ivResultImage.setVisibility(View.VISIBLE);
        }
        viewNeonGlow.setAlpha(0f);
        viewNeonGlow.setVisibility(View.GONE);
        
        if (getView() != null) {
            ImageView frameSpin = getView().findViewById(R.id.iv_frame_result_spin);
            if (frameSpin != null) {
                frameSpin.setVisibility(View.GONE);
                frameSpin.setAlpha(0f);
            }
        }
        
        stopShineEffectLoop(); // Reset shine loop

        // Reset trạng thái chọn thẻ
        selectedPosition = -1;
        secretCardHeight = 0;
        secretAdapter = null;
        revealResultAdapter = null;
        revealCardHeight = 0;
        if (rvSecretGrid != null) rvSecretGrid.setAdapter(null);
        if (rvRevealResultGrid != null) rvRevealResultGrid.setAdapter(null);
        // Reset nút Confirm
        if (btnConfirmSelect != null) {
            btnConfirmSelect.setEnabled(false);
            ViewCompat.setBackgroundTintList(btnConfirmSelect, ColorStateList.valueOf(Color.parseColor("#41455E")));
            btnConfirmSelect.setTextColor(Color.parseColor("#A2A2A7"));
        }
        // Hiện lại màn Spin chính
        layoutSpinMain.setVisibility(View.VISIBLE);
        toggleBottomNavigation(true);
        
        // Reset slot nguyên liệu spin
        if (ivSelectedObjet != null) {
            ivSelectedObjet.setVisibility(View.GONE);
            ivSelectedObjet.setImageDrawable(null);
        }
        if (btnAddObjet != null) {
            btnAddObjet.setVisibility(View.VISIBLE);
        }
        if (btnSpin != null) {
            btnSpin.setEnabled(false);
            ViewCompat.setBackgroundTintList(btnSpin, ColorStateList.valueOf(Color.parseColor("#41455E")));
            btnSpin.setTextColor(Color.parseColor("#A2A2A7"));
        }
    }
    
    // ========================
    // Shine Effect Logic
    // ========================
    private final Handler shineHandler = new Handler(Looper.getMainLooper());
    private final java.util.Random shineRandom = new java.util.Random();
    private boolean isShineActive = false;

    private void startShineEffectLoop() {
        if (isShineActive) return;
        isShineActive = true;
        // The loop triggers randomly. We set the first trigger fairly soon.
        shineHandler.postDelayed(shineRunnable, 1000); 
    }

    private void stopShineEffectLoop() {
        isShineActive = false;
        shineHandler.removeCallbacks(shineRunnable);
    }

    private final Runnable shineRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isShineActive || !isAdded() || secretAdapter == null || rvSecretGrid == null) return;
            
            // Randomly select 1 to 2 items each time
            int count = 1 + shineRandom.nextInt(2);
            for(int i = 0; i < count; i++) {
                int randomPos = shineRandom.nextInt(16);
                RecyclerView.ViewHolder vh = rvSecretGrid.findViewHolderForAdapterPosition(randomPos);
                if (vh instanceof SecretCardAdapter.ViewHolder) {
                    SecretCardAdapter.ViewHolder holder = (SecretCardAdapter.ViewHolder) vh;
                    long animDuration = 600 + shineRandom.nextInt(401); // 600 to 1000
                    holder.playShineAnimation(animDuration);
                }
            }
            
            // Every 1-2 seconds
            long delayMs = 1000 + shineRandom.nextInt(1000);
            shineHandler.postDelayed(this, delayMs);
        }
    };
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopShineEffectLoop();
    }

    // ========================
    // SecretCardAdapter (chọn thẻ ban đầu)
    // ========================
    private class SecretCardAdapter extends RecyclerView.Adapter<SecretCardAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_secret_card, parent, false);
            if (secretCardHeight > 0) {
                ViewGroup.LayoutParams params = v.getLayoutParams();
                params.height = secretCardHeight;
                v.setLayoutParams(params);
            }
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
            int strokeWidth = selectedPosition == position ? (int) (3 * density) : 0;
            holder.cardRoot.setStrokeWidth(strokeWidth);
            
            if (isAdded()) {
                Glide.with(SpinFragment.this)
                        .load(R.drawable.objet_back_spin)
                        .into(holder.ivCardBack);
            }
            holder.ivCardBack.setVisibility(View.VISIBLE);
            holder.ivCardFront.setVisibility(View.INVISIBLE);
            
            holder.itemView.setOnClickListener(v -> {
                int oldPos = selectedPosition;
                selectedPosition = holder.getBindingAdapterPosition();
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    if (oldPos != -1) notifyItemChanged(oldPos);
                    notifyItemChanged(selectedPosition);
                    if (btnConfirmSelect != null) {
                        btnConfirmSelect.setEnabled(true);
                        ViewCompat.setBackgroundTintList(btnConfirmSelect, ColorStateList.valueOf(Color.parseColor("#8A2BE2")));
                        btnConfirmSelect.setTextColor(Color.WHITE);
                    }
                }
            });
        }
        @Override
        public int getItemCount() { return gridSessionCards.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardRoot;
            ImageView ivCardBack, ivCardFront;
            View viewShine;

            ViewHolder(View v) {
                super(v);
                cardRoot = v.findViewById(R.id.card_root);
                ivCardBack = v.findViewById(R.id.iv_card_back);
                ivCardFront = v.findViewById(R.id.iv_card_front);
                viewShine = v.findViewById(R.id.view_metallic_shine);
            }
            
            void playShineAnimation(long duration) {
                if (viewShine == null || cardRoot == null) return;
                int cw = cardRoot.getWidth();
                if (cw == 0) return;
                
                viewShine.setVisibility(View.VISIBLE);
                
                // Streak diagonal sweep from right to left
                float startX = cw * 1.5f;
                float endX = -cw * 1.5f;
                
                ObjectAnimator txAnim = ObjectAnimator.ofFloat(viewShine, "translationX", startX, endX);
                txAnim.setDuration(duration);
                txAnim.setInterpolator(new android.view.animation.LinearInterpolator());
                
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(viewShine, "alpha", 0f, 1f);
                fadeIn.setDuration((long)(duration * 0.2f));
                
                ObjectAnimator fadeOut = ObjectAnimator.ofFloat(viewShine, "alpha", 1f, 0f);
                fadeOut.setDuration((long)(duration * 0.2f));
                fadeOut.setStartDelay((long)(duration * 0.8f));
                
                AnimatorSet set = new AnimatorSet();
                set.playTogether(txAnim, fadeIn, fadeOut);
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewShine.setVisibility(View.INVISIBLE);
                    }
                });
                set.start();
            }
        }
    }

    // ========================
    // RevealResultAdapter (hiện kết quả 16 thẻ)
    // ========================
    private class RevealResultAdapter extends RecyclerView.Adapter<RevealResultAdapter.ViewHolder> {
        private final int winPosition;
        private boolean revealed = false;
        private final List<ViewHolder> holders = new ArrayList<>();

        RevealResultAdapter(int winPosition) {
            this.winPosition = winPosition;
        }

        void revealAllCards() {
            revealed = true;
            for (ViewHolder holder : holders) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != winPosition && pos != RecyclerView.NO_POSITION) {
                    flipCardToFront(holder, pos);
                }
            }
        }

        private void flipCardToFront(ViewHolder holder, int position) {
            // Animation lật thẻ: scale X từ 1 → 0, đổi ảnh, rồi 0 → 1
            holder.itemView.animate()
                    .scaleX(0f)
                    .setDuration(200)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // Đổi sang mặt trước
                            holder.ivCardBack.setVisibility(View.GONE);
                            holder.ivCardFront.setVisibility(View.VISIBLE);

                            // Scale X từ 0 → 1
                            holder.itemView.animate()
                                    .scaleX(1f)
                                    .setDuration(200)
                                    .setInterpolator(new AccelerateDecelerateInterpolator())
                                    .setListener(null)
                                    .start();
                        }
                    })
                    .start();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_secret_card, parent, false);
            if (revealCardHeight > 0) {
                ViewGroup.LayoutParams params = v.getLayoutParams();
                params.height = revealCardHeight;
                v.setLayoutParams(params);
            }
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holders.add(holder);
            holder.cardRoot.setStrokeWidth(0);
            
            if (isAdded()) {
                Glide.with(SpinFragment.this)
                        .load(R.drawable.objet_back_spin)
                        .into(holder.ivCardBack);
                        
                String frontUrl = getCardImageUrl(position, true);
                loadCardImageInto(frontUrl, holder.ivCardFront);
            }

            if (position == winPosition) {
                // Thẻ đã chọn — hiện mặt trước + badge "Get"
                holder.ivCardBack.setVisibility(View.GONE);
                holder.ivCardFront.setVisibility(View.VISIBLE);
                
                float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
                holder.cardRoot.setStrokeWidth((int) (3 * density));

                // Thêm badge "Get" lên trên
                holder.addGetBadge();
            } else {
                // Các thẻ khác — hiện mặt sau (card back)
                holder.ivCardBack.setVisibility(View.VISIBLE);
                holder.ivCardFront.setVisibility(View.INVISIBLE);
            }

            // Disable click trong reveal grid
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        }

        @Override
        public int getItemCount() { return gridSessionCards.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardRoot;
            ImageView ivCardBack, ivCardFront;
            TextView tvGetBadge;

            ViewHolder(View v) {
                super(v);
                cardRoot = v.findViewById(R.id.card_root);
                ivCardBack = v.findViewById(R.id.iv_card_back);
                ivCardFront = v.findViewById(R.id.iv_card_front);
            }

            void addGetBadge() {
                if (tvGetBadge != null) return; // Đã thêm rồi

                FrameLayout parent = (FrameLayout) ivCardFront.getParent();
                if (parent == null) return;

                tvGetBadge = new TextView(parent.getContext());
                tvGetBadge.setText("Get");
                tvGetBadge.setTextColor(Color.WHITE);
                tvGetBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                tvGetBadge.setTypeface(null, Typeface.BOLD);
                tvGetBadge.setGravity(Gravity.CENTER);
                tvGetBadge.setBackgroundResource(R.drawable.bg_badge_get);

                float density = parent.getResources().getDisplayMetrics().density;
                int paddingH = (int) (12 * density);
                int paddingV = (int) (4 * density);
                tvGetBadge.setPadding(paddingH, paddingV, paddingH, paddingV);

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.TOP | Gravity.START;
                lp.topMargin = (int) (8 * density);
                lp.leftMargin = (int) (8 * density);

                parent.addView(tvGetBadge, lp);
            }
        }
    }

    private void startBackgroundAnimation() {
        if (getView() == null) return;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float cardWidth = (ivBgCard1 != null) ? ivBgCard1.getWidth() : 0;
        if (cardWidth == 0) return;
        long duration = 36000;
        animateCard(ivBgCard1, -cardWidth, screenWidth, duration, 0);
        animateCard(ivBgCard2, -cardWidth, screenWidth, duration, duration / 3);
        animateCard(ivBgCard3, -cardWidth, screenWidth, duration, 2 * duration / 3);
    }

    private void animateCard(View target, float startX, float endX, long duration, long initialPlayTime) {
        if (target == null) return;
        target.setVisibility(View.VISIBLE);
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, "translationX", startX, endX);
        animator.setDuration(duration);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.start();
        animator.setCurrentPlayTime(initialPlayTime);
    }

    private void updateSelectedObjetUI(String imageUrl) {
        if (btnAddObjet != null) btnAddObjet.setVisibility(View.GONE);
        if (ivSelectedObjet != null) {
            ivSelectedObjet.setVisibility(View.VISIBLE);
            Glide.with(this).load(imageUrl).into(ivSelectedObjet);
        }
        if (btnSpin != null) {
            btnSpin.setEnabled(true);
            ViewCompat.setBackgroundTintList(btnSpin, ColorStateList.valueOf(Color.parseColor("#8A2BE2")));
            btnSpin.setTextColor(Color.WHITE);
        }
    }
}

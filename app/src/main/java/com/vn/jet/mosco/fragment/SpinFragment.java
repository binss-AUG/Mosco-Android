package com.vn.jet.mosco.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.cardview.widget.CardView;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.vn.jet.mosco.R;

public class SpinFragment extends Fragment {

    private View btnAddObjet;
    private ImageView ivSelectedObjet;
    private AppCompatButton btnSpin;
    private ImageView ivBgCard1, ivBgCard2, ivBgCard3;
    private CardView cardCenterSlot;
    private VideoView videoSpinEffect;
    
    // UI Phases
    private View layoutSpinMain;
    private View layoutRevealGrid;
    private RecyclerView rvSecretGrid;
    private AppCompatButton btnConfirmSelect;
    private SecretCardAdapter secretAdapter;
    private int selectedPosition = -1;

    // Result UI
    private View layoutResultReveal;
    private CardView cardResultFinal;
    private ImageView ivResultImage;
    private View viewNeonGlow;

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
        viewNeonGlow = view.findViewById(R.id.view_neon_glow);

        cardCenterSlot = view.findViewById(R.id.card_center_slot);
        btnAddObjet = view.findViewById(R.id.btn_add_objet);
        ivSelectedObjet = view.findViewById(R.id.iv_selected_objet);
        btnSpin = view.findViewById(R.id.btn_spin);
        ivBgCard1 = view.findViewById(R.id.iv_bg_card_1);
        ivBgCard2 = view.findViewById(R.id.iv_bg_card_2);
        ivBgCard3 = view.findViewById(R.id.iv_bg_card_3);
        videoSpinEffect = view.findViewById(R.id.video_spin_effect);

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
        btnConfirmSelect.setOnClickListener(v -> playRewardVideoAnimation());

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
            playSpinVideoEffect();
        });
        dialog.show();
    }

    private void playSpinVideoEffect() {
        playVideo(R.raw.spin_animation, this::startPhase4);
    }

    private void playRewardVideoAnimation() {
        if (selectedPosition == -1) return;
        playVideo(R.raw.spin_reward_animation, this::showFinalResultWithNeonEffect);
    }

    private void playVideo(int resId, Runnable onComplete) {
        if (videoSpinEffect == null || getContext() == null) return;
        if (layoutSpinMain != null) layoutSpinMain.setVisibility(View.GONE);
        if (layoutRevealGrid != null) layoutRevealGrid.setVisibility(View.GONE);
        if (layoutResultReveal != null) layoutResultReveal.setVisibility(View.GONE);
        toggleBottomNavigation(false);
        videoSpinEffect.setVisibility(View.VISIBLE);
        videoSpinEffect.setBackgroundColor(Color.BLACK);
        videoSpinEffect.setZ(2000f);
        Uri videoUri = Uri.parse("android.resource://" + requireActivity().getPackageName() + "/" + resId);
        videoSpinEffect.setVideoURI(videoUri);
        videoSpinEffect.setOnPreparedListener(mp -> {
            videoSpinEffect.setBackgroundColor(Color.TRANSPARENT);
            float videoRatio = mp.getVideoWidth() / (float) mp.getVideoHeight();
            float screenRatio = videoSpinEffect.getWidth() / (float) videoSpinEffect.getHeight();
            float scale = (videoRatio > screenRatio) ? (videoRatio / screenRatio) : (screenRatio / videoRatio);
            videoSpinEffect.setScaleX(scale);
            videoSpinEffect.setScaleY(scale);
            mp.setLooping(false);
            videoSpinEffect.start();
        });
        videoSpinEffect.setOnCompletionListener(mp -> {
            if (isAdded()) {
                videoSpinEffect.setVisibility(View.GONE);
                onComplete.run();
            }
        });
    }

    private void startPhase4() {
        toggleBottomNavigation(true);
        if (layoutRevealGrid != null) layoutRevealGrid.setVisibility(View.VISIBLE);
        if (rvSecretGrid != null) {
            rvSecretGrid.setLayoutManager(new GridLayoutManager(getContext(), 4));
            secretAdapter = new SecretCardAdapter();
            rvSecretGrid.setAdapter(secretAdapter);
        }
    }

    private void showFinalResultWithNeonEffect() {
        if (!isAdded()) return;

        layoutResultReveal.setVisibility(View.VISIBLE);
        
        // 1. Khởi tạo trạng thái: Objet ở xa (0.6), Neon trong suốt (0)
        cardResultFinal.setScaleX(0.6f);
        cardResultFinal.setScaleY(0.6f);
        viewNeonGlow.setAlpha(0.0f);
        viewNeonGlow.setVisibility(View.VISIBLE);

        // Load ảnh thật
        Glide.with(this)
                .load("https://imagedelivery.net/qQuMkbHJ-0s6rwu8vup_5w/d6db7447-13f9-4572-b299-7d9ba8be9e00/original") 
                .into(ivResultImage);

        // 2. Chạy hiệu ứng đồng bộ
        cardResultFinal.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(1200)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        viewNeonGlow.animate()
                .alpha(1.0f) // Tăng dần độ đậm của Neon Tím Trắng
                .setDuration(1200)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // Sau khi hiện rõ nhất, mờ nhẹ để lộ Objet
                        viewNeonGlow.animate().alpha(0f).setDuration(1000).start();
                        toggleBottomNavigation(true);
                        Toast.makeText(getContext(), "You got a Vk tao!", Toast.LENGTH_LONG).show();
                    }
                })
                .start();
    }

    private class SecretCardAdapter extends RecyclerView.Adapter<SecretCardAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_secret_card, parent, false);
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.viewGlow.setVisibility(selectedPosition == position ? View.VISIBLE : View.GONE);
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
        public int getItemCount() { return 16; }
        class ViewHolder extends RecyclerView.ViewHolder {
            CardView cardRoot;
            ImageView ivCardBack, ivCardFront;
            View viewGlow;
            ViewHolder(View v) {
                super(v);
                cardRoot = v.findViewById(R.id.card_root);
                ivCardBack = v.findViewById(R.id.iv_card_back);
                ivCardFront = v.findViewById(R.id.iv_card_front);
                viewGlow = v.findViewById(R.id.view_selection_glow);
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

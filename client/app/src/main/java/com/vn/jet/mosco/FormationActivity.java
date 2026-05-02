package com.vn.jet.mosco;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.content.ClipData;
import android.view.DragEvent;
import com.vn.jet.mosco.adapter.SynergyDashboardAdapter;
import com.vn.jet.mosco.dto.BattleRequest;
import com.vn.jet.mosco.dto.BattleResponse;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.view.NeonLineView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FormationActivity extends AppCompatActivity {

    private RecyclerView rvStage, rvSynergies;
    private TextView tvTotalOvr;
    private NeonLineView neonLineView;
    private View dimOverlay;
    private SynergyDashboardAdapter synergyAdapter;
    private com.google.android.material.bottomsheet.BottomSheetBehavior<View> synergySheetBehavior;
    private android.widget.ImageButton btnSynergyToggle;
    private android.widget.TextView btnGlobalBuffs;
    private View stageContainer;
    
    private final View[] slotViews = new View[6];
    private final List<Objet> formation = new ArrayList<>(Collections.nCopies(6, null));
    private int currentOvr = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_formation);

        initViews();
        setupStage();
        setupSynergies();
        
        loadFormation();
    }

    private void initViews() {
        rvSynergies = findViewById(R.id.rv_active_synergies);
        rvSynergies.setNestedScrollingEnabled(false);
        tvTotalOvr = findViewById(R.id.tv_total_ovr);
        btnGlobalBuffs = findViewById(R.id.btn_global_buffs);
        neonLineView = findViewById(R.id.neon_line_view);
        dimOverlay = findViewById(R.id.dim_overlay);
        stageContainer = findViewById(R.id.stage_container);
        
        slotViews[0] = findViewById(R.id.slot0);
        slotViews[1] = findViewById(R.id.slot1);
        slotViews[2] = findViewById(R.id.slot2);
        slotViews[3] = findViewById(R.id.slot3);
        slotViews[4] = findViewById(R.id.slot4);
        slotViews[5] = findViewById(R.id.slot5);
        
        findViewById(R.id.btn_back_common).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tv_header_title)).setText(R.string.home_nav_formation);
        
        // ── Thiết lập BottomSheet cho Synergy Panel ──
        View synergySheet = findViewById(R.id.synergy_bottom_sheet);
        synergySheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(synergySheet);
        synergySheetBehavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED);
        
        btnSynergyToggle = findViewById(R.id.btn_synergy_toggle);
        btnSynergyToggle.setRotation(180f); // Mũi tên xuống khi collapsed
        
        // Nút Toggle: Ấn sẽ lặp vòng qua 3 trạng thái
        btnSynergyToggle.setOnClickListener(v -> {
            int currentState = synergySheetBehavior.getState();
            if (currentState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED) {
                synergySheetBehavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED);
            } else if (currentState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED) {
                synergySheetBehavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            } else {
                synergySheetBehavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED);
            }
        });
        
        // Lắng nghe trạng thái BottomSheet để xoay icon mũi tên
        synergySheetBehavior.addBottomSheetCallback(new com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED) {
                    btnSynergyToggle.animate().rotation(180f).setDuration(200).start();
                } else if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED) {
                    btnSynergyToggle.animate().rotation(0f).setDuration(200).start();
                } else if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED) {
                    btnSynergyToggle.animate().rotation(90f).setDuration(200).start(); // Góc xoay cho nửa chừng
                }
                updateRecyclerViewPadding(bottomSheet);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // Xoay mượt theo mức kéo
                btnSynergyToggle.setRotation(180f * (1f - slideOffset));

                // Tinh chỉnh thu nhỏ Grid: Khi offset=0.3 (1/4 screen) -> Scale = 0.75
                stageContainer.setPivotY(0f); // Ghim ở giữa trên cùng
                float scale = 1.0f - (0.833f * slideOffset); 
                scale = Math.max(0.16f, scale);
                stageContainer.setScaleX(scale);
                stageContainer.setScaleY(scale);
                stageContainer.setTranslationY(0f); 

                // Thay đổi Opacity nền của Sheet và Header linh hoạt
                android.animation.ArgbEvaluator evaluator = new android.animation.ArgbEvaluator();
                
                View bottomSheetView = findViewById(R.id.synergy_bottom_sheet);
                if (bottomSheetView.getBackground() != null) {
                    android.graphics.drawable.GradientDrawable sheetBg = (android.graphics.drawable.GradientDrawable) bottomSheetView.getBackground().mutate();
                    Integer colorSheet = (Integer) evaluator.evaluate(slideOffset, 0x3BFFFFFF, 0xF2000000);
                    sheetBg.setColor(colorSheet);
                }

                View headerView = findViewById(R.id.synergy_header);
                if (headerView.getBackground() != null) {
                    android.graphics.drawable.GradientDrawable headerBg = (android.graphics.drawable.GradientDrawable) headerView.getBackground().mutate();
                    Integer colorHeader = (Integer) evaluator.evaluate(slideOffset, 0xF2000000, 0xFF000000);
                    headerBg.setColor(colorHeader);
                }

                // Áp dụng lớp mờ
                if (slideOffset > 0) {
                    dimOverlay.setVisibility(View.VISIBLE);
                    dimOverlay.setAlpha(0.6f * slideOffset); // Alpha tối đa 60%
                } else {
                    dimOverlay.setVisibility(View.GONE);
                }

                updateRecyclerViewPadding(bottomSheet);
            }
        });
    }

    private void updateRecyclerViewPadding(View bottomSheet) {
        android.view.ViewGroup parent = (android.view.ViewGroup) bottomSheet.getParent();
        if (parent == null || rvSynergies == null) return;
        int parentHeight = parent.getHeight();
        int offScreenAmount = bottomSheet.getHeight() + bottomSheet.getTop() - parentHeight;
        if (offScreenAmount < 0) offScreenAmount = 0;
        
        // 16dp base padding bottom
        int basePadding = Math.round(16 * getResources().getDisplayMetrics().density);
        rvSynergies.setPadding(rvSynergies.getPaddingLeft(), rvSynergies.getPaddingTop(), rvSynergies.getPaddingRight(), basePadding + offScreenAmount);
    }

    private void setupStage() {
        for (int i = 0; i < 6; i++) {
            final int index = i;
            slotViews[i].setTag(index);

            // Bấm đổi thẻ
            slotViews[i].setOnClickListener(v -> showInventoryBottomSheet(index));

            // Long press logic -> Drag
            slotViews[i].setOnLongClickListener(v -> {
                if (formation.get(index) == null) return false;
                ClipData data = ClipData.newPlainText("slot_index", String.valueOf(index));
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
                v.startDragAndDrop(data, shadow, v, 0);
                return true;
            });

            // Drag Listener cho Drag & Drop
            slotViews[i].setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DROP:
                        ClipData.Item item = event.getClipData().getItemAt(0);
                        int fromIndex = Integer.parseInt(item.getText().toString());
                        int toIndex = (int) v.getTag();
                        if (fromIndex != toIndex) {
                            Collections.swap(formation, fromIndex, toIndex);
                            bindSlotView(fromIndex);
                            bindSlotView(toIndex);
                            fetchBattlePreview();
                            saveFormationState();
                        }
                        return true;
                    case DragEvent.ACTION_DRAG_STARTED:
                    case DragEvent.ACTION_DRAG_ENTERED:
                    case DragEvent.ACTION_DRAG_EXITED:
                    case DragEvent.ACTION_DRAG_ENDED:
                        return true;
                }
                return false;
            });
            
            bindSlotView(i);
        }
    }

    private void setupSynergies() {
        synergyAdapter = new SynergyDashboardAdapter(new SynergyDashboardAdapter.OnSynergyInteractionListener() {
            @Override
            public void onSynergyHold(String synergyName) {
                resetStageHighlight();
                if (synergyName != null) {
                    highlightCardsForSynergy(synergyName);
                }
            }

            @Override
            public void onSynergyRelease() {
                resetStageHighlight();
            }
        });
        rvSynergies.setAdapter(synergyAdapter);
    }

    private void showInventoryBottomSheet(int position) {
        // Debounce: Chống double-click
        if (getSupportFragmentManager().findFragmentByTag("inventory_bottom_sheet") != null) {
            return;
        }

        com.vn.jet.mosco.fragment.InventoryBottomSheet bottomSheet = new com.vn.jet.mosco.fragment.InventoryBottomSheet();
        bottomSheet.setOnObjetSelectedListener(objet -> {
            // Kiểm tra xem thẻ đã có trên sân chưa
            for (int i = 0; i < formation.size(); i++) {
                if (formation.get(i) != null && formation.get(i).getId() == objet.getId()) {
                    if (i == position) return;
                    Collections.swap(formation, position, i);
                    bindSlotView(position);
                    bindSlotView(i);
                    fetchBattlePreview();
                    saveFormationState();
                    return;
                }
            }

            // Chặn duplicate Artist
            String newArtist = objet.getMember();
            if (newArtist != null) {
                for (int i = 0; i < formation.size(); i++) {
                    if (i == position) continue; // Bỏ qua chính thẻ hiện đang xét thay thế
                    if (formation.get(i) != null && newArtist.equalsIgnoreCase(formation.get(i).getMember())) {
                        Toast.makeText(this, "Trong đội hình không thể có 2 Objet cùng artist!", Toast.LENGTH_SHORT).show();
                        return; // Chặn không cho thêm vào
                    }
                }
            }

            formation.set(position, objet);
            bindSlotView(position);
            fetchBattlePreview();
            saveFormationState();
        });
        bottomSheet.show(getSupportFragmentManager(), "inventory_bottom_sheet");
    }

    private void bindSlotView(int index) {
        View slotView = slotViews[index];
        Objet objet = formation.get(index);
        
        View emptyView = slotView.findViewById(R.id.empty_slot_view);
        View cardView = slotView.findViewById(R.id.card_slot_view);
        com.google.android.material.card.MaterialCardView cvShowcaseCard = slotView.findViewById(R.id.cv_showcase_card);
        View viewCardShimmer = slotView.findViewById(R.id.view_card_shimmer);
        android.widget.ImageView ivCard = slotView.findViewById(R.id.card_iv_image);
        TextView tvOvr = slotView.findViewById(R.id.card_tv_ovr);
        android.widget.ImageView ivLevel = slotView.findViewById(R.id.card_iv_level);

        slotView.setBackground(null);
        emptyView.setBackgroundResource(R.drawable.bg_empty_slot_neon);
        
        if (objet != null) {
            emptyView.setVisibility(View.GONE);
            cardView.setVisibility(View.VISIBLE);
            
            if (cvShowcaseCard != null) {
                com.vn.jet.mosco.utils.CardEffectHelper.apply(cvShowcaseCard, viewCardShimmer, objet, true);
            }
            
            if (objet.getImageUrl() != null && !objet.getImageUrl().isEmpty()) {
                java.io.File localFile = com.vn.jet.mosco.utils.CardAssetManager.getLocalFile(this, objet.getImageUrl());
                if (localFile != null && localFile.exists()) {
                    com.bumptech.glide.Glide.with(this).load(localFile).into(ivCard);
                } else {
                    com.bumptech.glide.Glide.with(this).load(objet.getImageUrl()).into(ivCard);
                }
            } else {
                ivCard.setImageDrawable(null);
            }
            
            if (tvOvr != null) {
                tvOvr.setVisibility(View.GONE);
                tvOvr.setText(String.valueOf(objet.getOvr()));
            }

            if (ivLevel != null) {
                if (objet.getUpgradeLevel() > 0) {
                    String assetPath = "file:///android_asset/grade/" + objet.getUpgradeLevel() + ".png";
                    com.bumptech.glide.Glide.with(this).load(assetPath).into(ivLevel);
                    ivLevel.setVisibility(View.VISIBLE);
                    com.vn.jet.mosco.utils.LevelBadgeEffectHelper.apply(ivLevel, objet.getUpgradeLevel());
                } else {
                    ivLevel.setVisibility(View.GONE);
                    com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivLevel);
                }
            }
        } else {
            emptyView.setVisibility(View.VISIBLE);
            cardView.setVisibility(View.GONE);
            if (tvOvr != null) tvOvr.setVisibility(View.GONE);
            if (ivLevel != null) ivLevel.setVisibility(View.GONE);
            if (cvShowcaseCard != null) {
                com.vn.jet.mosco.utils.CardEffectHelper.remove(cvShowcaseCard, viewCardShimmer);
            }
        }
    }

    private void fetchBattlePreview() {
        BattleRequest request = new BattleRequest();
        List<BattleRequest.FormationSlot> slots = new ArrayList<>();
        for (Objet obj : formation) {
            if (obj != null) {
                BattleRequest.FormationSlot slot = new BattleRequest.FormationSlot();
                slot.setUserCardId((long) obj.getId());
                slots.add(slot);
            }
        }
        request.setFormation(slots);

        GameApiService apiService = ApiClient.getClient(this).create(GameApiService.class);
        apiService.postBattlePreview(request).enqueue(new Callback<BattleResponse>() {
            @Override
            public void onResponse(Call<BattleResponse> call, Response<BattleResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    updateUI(response.body());
                }
            }

            @Override
            public void onFailure(Call<BattleResponse> call, Throwable t) {
                Toast.makeText(FormationActivity.this, getString(R.string.formation_preview_failed), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadFormation() {
        Long userId = new com.vn.jet.mosco.utils.SessionManager(this).getUserId();
        if (userId == null) return;

        GameApiService apiService = ApiClient.getClient(this).create(GameApiService.class);
        apiService.getUserFormation(userId).enqueue(new Callback<List<Objet>>() {
            @Override
            public void onResponse(Call<List<Objet>> call, Response<List<Objet>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Objet> fetched = response.body();
                    for (int i = 0; i < Math.min(6, fetched.size()); i++) {
                        formation.set(i, fetched.get(i));
                        bindSlotView(i);
                    }
                    fetchBattlePreview();
                }
            }

            @Override
            public void onFailure(Call<List<Objet>> call, Throwable t) {
                fetchBattlePreview();
            }
        });
    }

    private void saveFormationState() {
        Long userId = new com.vn.jet.mosco.utils.SessionManager(this).getUserId();
        if (userId == null) return;

        List<Long> slotIds = new ArrayList<>();
        for (Objet obj : formation) {
            slotIds.add(obj != null ? (long) obj.getId() : null);
        }

        GameApiService apiService = ApiClient.getClient(this).create(GameApiService.class);
        apiService.saveUserFormation(userId, slotIds).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {}
            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {}
        });
    }

    private void updateUI(BattleResponse data) {
        animateOvr(data.getTotalOvr());
        synergyAdapter.submitList(data.getActiveSynergies(), data.getBuffSummary());
        
        // Cập nhật điểm OVR Tĩnh trực tiếp trên Slot
        if (data.getCardOvrMap() != null) {
            updateDynamicSlotStats(data.getCardOvrMap());
        }

        // Lọc các Buff Đặc Biệt (%) để hiển thị lên Dropdown Header
        Map<String, String> specialBuffs = new HashMap<>();
        if (data.getBuffSummary() != null) {
            for (Map.Entry<String, String> entry : data.getBuffSummary().entrySet()) {
                if (entry.getValue().contains("%")) {
                    specialBuffs.put(entry.getKey(), entry.getValue());
                }
            }
        }

        if (!specialBuffs.isEmpty()) {
            btnGlobalBuffs.setVisibility(View.VISIBLE);
            btnGlobalBuffs.setOnClickListener(v -> showGlobalBuffsPopup(specialBuffs));
        } else {
            btnGlobalBuffs.setVisibility(View.GONE);
        }
    }

    private void updateDynamicSlotStats(Map<Long, Integer> cardOvrMap) {
        for (int i = 0; i < 6; i++) {
            Objet obj = formation.get(i);
            if (obj == null) continue;

            TextView tvOvr = slotViews[i].findViewById(R.id.card_tv_ovr);
            if (tvOvr == null) continue;

            int baseOvr = obj.getOvr();
            int newOvr = cardOvrMap.getOrDefault((long) obj.getId(), baseOvr);

            if (newOvr > baseOvr) {
                tvOvr.setText(String.valueOf(newOvr));
                tvOvr.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.mosco_tertiary)); // Xanh Neon
            } else {
                tvOvr.setText(String.valueOf(baseOvr));
                tvOvr.setTextColor(android.graphics.Color.WHITE); // Trắng gốc
            }
        }
    }

    private void showGlobalBuffsPopup(Map<String, String> specialBuffs) {
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(androidx.core.content.ContextCompat.getColor(this, R.color.mosco_surface_container_high_80)); 
        gd.setCornerRadius(getResources().getDimension(R.dimen.radius_lg));
        gd.setStroke(2, androidx.core.content.ContextCompat.getColor(this, R.color.white_25));
        container.setBackground(gd);

        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        for (Map.Entry<String, String> entry : specialBuffs.entrySet()) {
            TextView tv = new TextView(this);
            tv.setText(entry.getKey() + ": " + entry.getValue());
            tv.setTextColor(android.graphics.Color.WHITE);
            tv.setTextSize(13f);
            try {
                tv.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.poppins));
            } catch (Exception e) {
                // Ignore
            }
            tv.setPadding(0, padding / 2, 0, padding / 2);
            container.addView(tv);
        }

        android.widget.PopupWindow popupWindow = new android.widget.PopupWindow(
                container,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setElevation(20f);
        popupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)); // Allows bounds and shadow to work properly
        popupWindow.showAsDropDown(btnGlobalBuffs, 0, padding / 2);
    }

    private void animateOvr(int targetOvr) {
        ValueAnimator animator = ValueAnimator.ofInt(currentOvr, targetOvr);
        animator.setDuration(800);
        animator.addUpdateListener(animation -> {
            currentOvr = (int) animation.getAnimatedValue();
            tvTotalOvr.setText(getString(R.string.formation_ovr_label, currentOvr));
        });
        animator.start();
    }

    private void highlightCardsForSynergy(String synergyName) {
        dimOverlay.setVisibility(View.GONE); // KHÔNG làm mờ toàn màn hình
        
        // Remove counter suffixes like " (2)" from the name
        String cleanSynergyName = synergyName.replaceAll("\\s*\\(\\d+\\)$", "").trim();

        for (int i = 0; i < formation.size(); i++) {
            Objet objet = formation.get(i);
            View itemView = slotViews[i];
            if (itemView == null || objet == null) continue;

            // Null checks for safety
            java.util.List<String> tags = objet.getAvailableTags();
            String typeKey = objet.getTypeKey() != null ? objet.getTypeKey() : "";
            String dimension = objet.getDimension() != null ? objet.getDimension() : "";

            boolean isRelated = false;
            if (typeKey.equalsIgnoreCase(cleanSynergyName) || dimension.equalsIgnoreCase(cleanSynergyName)) {
                isRelated = true;
            }
            if (!isRelated && (!dimension.isEmpty() && cleanSynergyName.toUpperCase().startsWith(dimension.toUpperCase()))) {
                isRelated = true;
            }
            if (!isRelated && (!typeKey.isEmpty() && cleanSynergyName.toUpperCase().startsWith(typeKey.toUpperCase()))) {
                isRelated = true;
            }
            if (!isRelated && tags != null) {
                for (String t : tags) {
                    if (t.equalsIgnoreCase(cleanSynergyName) || t.toUpperCase().contains(cleanSynergyName.toUpperCase()) || cleanSynergyName.toUpperCase().contains(t.toUpperCase())) {
                        isRelated = true;
                        break;
                    }
                }
            }

            if (!isRelated && cleanSynergyName.toUpperCase().contains("HARMONY")) {
                int level = objet.getUpgradeLevel();
                String tier = getString(R.string.synergy_tier_bronze);
                
                int goldMin = getResources().getInteger(R.integer.synergy_tier_gold_min_level);
                int silverMin = getResources().getInteger(R.integer.synergy_tier_silver_min_level);
                
                if (level >= goldMin) tier = getString(R.string.synergy_tier_gold);
                else if (level >= silverMin) tier = getString(R.string.synergy_tier_silver);
                
                if (cleanSynergyName.toUpperCase().contains(tier.toUpperCase())) {
                    isRelated = true;
                }
            }

            View cvCard = itemView.findViewById(R.id.cv_showcase_card);
            if (cvCard == null) cvCard = itemView; // Fallback

            if (isRelated) {
                // Thẻ liên quan: Hiển thị sáng rõ, KHÔNG tạo float animator mới
                // (CardEffectHelper đã quản lý float mặc định rồi)
                cvCard.setAlpha(1.0f);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && cvCard instanceof android.widget.FrameLayout) {
                    ((android.widget.FrameLayout) cvCard).setForeground(null);
                }
            } else {
                // Thẻ không liên quan: Làm tối để nổi bật thẻ liên quan
                cvCard.setAlpha(1.0f);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && cvCard instanceof android.widget.FrameLayout) {
                    int dimOverlayColor = androidx.core.content.ContextCompat.getColor(this, R.color.mosco_dim_overlay_70);
                    ((android.widget.FrameLayout) cvCard).setForeground(new android.graphics.drawable.ColorDrawable(dimOverlayColor));
                } else {
                    cvCard.setAlpha(0.4f);
                }
            }
        }
    }

    private void resetStageHighlight() {
        if (synergySheetBehavior != null && synergySheetBehavior.getState() != com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED) {
            dimOverlay.setVisibility(View.VISIBLE);
        } else {
            dimOverlay.setVisibility(View.GONE);
        }
        for (int i = 0; i < formation.size(); i++) {
            View itemView = slotViews[i];
            if (itemView != null) {
                View cvCard = itemView.findViewById(R.id.cv_showcase_card);
                if (cvCard == null) cvCard = itemView;
                
                // Chỉ xóa foreground tối và reset alpha
                // KHÔNG reset translationY — CardEffectHelper đang quản lý float mặc định
                cvCard.setAlpha(1.0f);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && cvCard instanceof android.widget.FrameLayout) {
                    ((android.widget.FrameLayout) cvCard).setForeground(null);
                }
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (synergySheetBehavior != null) {
            int action = ev.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                View synergyHeader = findViewById(R.id.synergy_header);
                View synergyBottomSheet = findViewById(R.id.synergy_bottom_sheet);
                
                if (synergyBottomSheet != null) {
                    if (!isPointInsideView(ev.getRawX(), ev.getRawY(), synergyBottomSheet)) {
                        // Clicked completely outside the bottom sheet entirely
                        int state = synergySheetBehavior.getState();
                        if (state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED || state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED) {
                            synergySheetBehavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED);
                            // Let the touch pass so they can immediately select a card underneath
                        }
                    } else {
                        // Clicked inside bottom sheet
                        boolean isContentEmptySpace = false;
                        if (rvSynergies != null) {
                            int[] rvLoc = new int[2];
                            rvSynergies.getLocationOnScreen(rvLoc);
                            float localX = ev.getRawX() - rvLoc[0];
                            float localY = ev.getRawY() - rvLoc[1];
                            if (localX >= 0 && localX <= rvSynergies.getWidth() && localY >= 0 && localY <= rvSynergies.getHeight()) {
                                View child = rvSynergies.findChildViewUnder(localX, localY);
                                if (child == null) {
                                    isContentEmptySpace = true;
                                }
                            }
                        }

                        if (isPointInsideView(ev.getRawX(), ev.getRawY(), synergyHeader) || isContentEmptySpace) {
                            // Clicked on header OR empty space below items -> allow dragging
                            synergySheetBehavior.setDraggable(true);
                        } else {
                            // Clicked on items inside content -> disable dragging so ScrollView handles it
                            synergySheetBehavior.setDraggable(false);
                        }
                    }
                }
            } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                synergySheetBehavior.setDraggable(true);
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean isPointInsideView(float x, float y, View view) {
        if (view == null) return false;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int viewX = location[0];
        int viewY = location[1];
        return (x > viewX && x < (viewX + view.getWidth()) && y > viewY && y < (viewY + view.getHeight()));
    }
}

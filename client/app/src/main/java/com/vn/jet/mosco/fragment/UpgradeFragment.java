package com.vn.jet.mosco.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.UpgradeAlgorithm;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragment quản lý toàn bộ flow nâng cấp thẻ.
 * - Màn hình 1: Chưa chọn thẻ (placeholder)
 * - Màn hình 2: Đã chọn thẻ chính
 * - Màn hình 3: Đã chọn nguyên liệu → sẵn sàng upgrade
 */
public class UpgradeFragment extends Fragment {

    // Views
    private View rootView;
    private View frameMainCard;
    private FrameLayout btnAddMainCard;
    private ImageView ivMainCardImage;
    private View viewCardBg;
    private TextView tvCardOvr;
    private ImageView ivCardLevelBadge;

    private LinearLayout layoutRightStats;
    private TextView tvOvrAfter;
    private TextView tvOvrCurrentSmall;

    private LinearLayout layoutLevelIndicator;
    private ImageView ivLevelCurrent;
    private ImageView ivLevelNext;
    private TextView tvLevelCurrent;
    private TextView tvLevelNext;

    private View viewProgressFill;
    private TextView tvMaterialsCount;

    private View[] frameMaterials = new View[5];
    private ImageView[] ivMaterials = new ImageView[5];
    private TextView[] tvMaterialPlus = new TextView[5];
    private View[] viewMaterialBg = new View[5];
    private TextView[] tvMaterialOvr = new TextView[5];
    private ImageView[] ivMaterialLevel = new ImageView[5];

    private androidx.appcompat.widget.AppCompatButton btnUpgrade;

    // Data
    private Objet mainCard = null;
    private Objet[] materialCards = new Objet[5];
    private int currentMaterialSlot = -1; // -1 = selecting main card

    private UpgradeAlgorithm upgradeAlgorithm;
    private Map<String, Map<String, Integer>> cardOvrData; // typeKey -> level -> ovr

    public UpgradeFragment() {
        // Required empty public constructor
    }

    public void setMainCard(Objet card) {
        this.mainCard = card;
        if (this.mainCard != null && this.mainCard.getOvr() == 0) {
            this.mainCard.setOvr(getOvrFromData(this.mainCard.getTypeKey(), this.mainCard.getCardLevel()));
        }
    }

    public static UpgradeFragment newInstance() {
        return new UpgradeFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadJsonData();
        
        // Recalculate mainCard OVR if it was set before JSON loaded
        if (mainCard != null) {
            mainCard.setOvr(getOvrFromData(mainCard.getTypeKey(), mainCard.getCardLevel()));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_upgrade, container, false);
        bindViews(rootView);
        setupClickListeners();
        updateUI();
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        DatabaseLoader.registerInventoryChangeListener(inventoryChangeListener);
        // Cập nhật lại UI khi quay lại màn hình
        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        DatabaseLoader.unregisterInventoryChangeListener(inventoryChangeListener);
    }

    private final DatabaseLoader.OnInventoryChangeListener inventoryChangeListener = new DatabaseLoader.OnInventoryChangeListener() {
        @Override
        public void onInventoryChanged() {
            if (getActivity() != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    // Cập nhật lại UI nếu cần thiết (ví dụ đếm số lượng slot)
                    updateUI();
                });
            }
        }
    };

    /**
     * Load dữ liệu JSON từ assets
     */
    private void loadJsonData() {
        Context ctx = getContext();
        if (ctx == null) return;

        Gson gson = new Gson();

        try {
            // Load upgradeRate.json
            InputStream isRate = ctx.getAssets().open("upgradeRate.json");
            InputStreamReader readerRate = new InputStreamReader(isRate);
            Type rateType = new TypeToken<Map<String, Double>>() {}.getType();
            Map<String, Double> rawRates = gson.fromJson(readerRate, rateType);
            readerRate.close();

            Map<Integer, Double> upgradeRates = new HashMap<>();
            for (Map.Entry<String, Double> entry : rawRates.entrySet()) {
                upgradeRates.put(Integer.parseInt(entry.getKey()), entry.getValue());
            }

            // Load customUpgrade.json
            InputStream isCustom = ctx.getAssets().open("customUpgrade.json");
            InputStreamReader readerCustom = new InputStreamReader(isCustom);
            JsonObject customJson = gson.fromJson(readerCustom, JsonObject.class);
            readerCustom.close();

            Map<Integer, Map<String, UpgradeAlgorithm.UpgradeConfig>> customUpgrades = new HashMap<>();
            for (Map.Entry<String, JsonElement> levelEntry : customJson.entrySet()) {
                int level = Integer.parseInt(levelEntry.getKey());
                JsonObject typeObj = levelEntry.getValue().getAsJsonObject();
                Map<String, UpgradeAlgorithm.UpgradeConfig> typeMap = new HashMap<>();
                for (Map.Entry<String, JsonElement> typeEntry : typeObj.entrySet()) {
                    UpgradeAlgorithm.UpgradeConfig config = gson.fromJson(
                            typeEntry.getValue(), UpgradeAlgorithm.UpgradeConfig.class);
                    typeMap.put(typeEntry.getKey(), config);
                }
                customUpgrades.put(level, typeMap);
            }

            upgradeAlgorithm = new UpgradeAlgorithm(upgradeRates, customUpgrades);

            // Load cardOvr.json
            InputStream isOvr = ctx.getAssets().open("cardOvr.json");
            InputStreamReader readerOvr = new InputStreamReader(isOvr);
            JsonObject ovrJson = gson.fromJson(readerOvr, JsonObject.class);
            readerOvr.close();

            cardOvrData = new HashMap<>();
            for (Map.Entry<String, JsonElement> typeEntry : ovrJson.entrySet()) {
                String typeKey = typeEntry.getKey();
                JsonObject levels = typeEntry.getValue().getAsJsonObject();
                Map<String, Integer> levelMap = new HashMap<>();
                for (Map.Entry<String, JsonElement> lvlEntry : levels.entrySet()) {
                    levelMap.put(lvlEntry.getKey(), lvlEntry.getValue().getAsInt());
                }
                cardOvrData.put(typeKey, levelMap);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Lấy OVR từ cardOvr.json data
     */
    private int getOvrFromData(String typeKey, int level) {
        // Nếu typeKey trông giống collectionId (chưa được map), hãy cố gắng map nó
        String resolvedKey = typeKey;
        if (typeKey != null && typeKey.contains("-")) {
            org.json.JSONObject meta = DatabaseLoader.findById(requireContext(), typeKey);
            if (meta != null) {
                resolvedKey = mapClassToTypeKey(meta.optString("class", ""));
            }
        }

        if (cardOvrData != null && cardOvrData.containsKey(resolvedKey)) {
            Map<String, Integer> levelMap = cardOvrData.get(resolvedKey);
            Integer ovr = levelMap.get(String.valueOf(level));
            if (ovr != null) return ovr;
        }
        return 80; // fallback
    }

    /**
     * Ánh xạ class từ database.json sang typeKey dùng cho upgrade
     */
    private String mapClassToTypeKey(String cardClass) {
        if (cardClass == null) return "FirstWelcome"; // Mặc định
        String typeKey = cardClass.replaceAll("\\s+", ""); // Xóa khoảng trắng
        if (typeKey.equalsIgnoreCase("FirstWelcome")) return "FirstWelcome";
        if (typeKey.equalsIgnoreCase("Double")) return "Double";
        if (typeKey.equalsIgnoreCase("SpecialUnit") || typeKey.equalsIgnoreCase("Special")) return "SpecialUnit";
        if (typeKey.equalsIgnoreCase("Premier")) return "Premier";
        return "FirstWelcome";
    }

    /**
     * Bind tất cả views từ layout
     */
    private void bindViews(View view) {
        frameMainCard = view.findViewById(R.id.frame_main_card);
        btnAddMainCard = view.findViewById(R.id.btn_add_main_card);
        ivMainCardImage = frameMainCard.findViewById(R.id.card_iv_image);
        viewCardBg = view.findViewById(R.id.view_card_bg);
        tvCardOvr = frameMainCard.findViewById(R.id.card_tv_ovr);
        ivCardLevelBadge = frameMainCard.findViewById(R.id.card_iv_level);

        layoutRightStats = view.findViewById(R.id.layout_right_stats);
        tvOvrAfter = view.findViewById(R.id.tv_ovr_after);
        tvOvrCurrentSmall = view.findViewById(R.id.tv_ovr_current_small);

        layoutLevelIndicator = view.findViewById(R.id.layout_level_indicator);
        ivLevelCurrent = view.findViewById(R.id.iv_level_current);
        ivLevelNext = view.findViewById(R.id.iv_level_next);
        tvLevelCurrent = view.findViewById(R.id.tv_level_current);
        tvLevelNext = view.findViewById(R.id.tv_level_next);

        viewProgressFill = view.findViewById(R.id.view_progress_fill);
        tvMaterialsCount = view.findViewById(R.id.tv_materials_count);

        btnUpgrade = view.findViewById(R.id.btn_upgrade);

        int[] materialFrameIds = {
                R.id.frame_material_1, R.id.frame_material_2, R.id.frame_material_3,
                R.id.frame_material_4, R.id.frame_material_5
        };
        int[] materialPlusIds = {
                R.id.tv_material_plus_1, R.id.tv_material_plus_2, R.id.tv_material_plus_3,
                R.id.tv_material_plus_4, R.id.tv_material_plus_5
        };
        int[] materialBgIds = {
                R.id.view_material_bg_1, R.id.view_material_bg_2, R.id.view_material_bg_3,
                R.id.view_material_bg_4, R.id.view_material_bg_5
        };

        for (int i = 0; i < 5; i++) {
            frameMaterials[i] = view.findViewById(materialFrameIds[i]);
            tvMaterialPlus[i] = view.findViewById(materialPlusIds[i]);
            viewMaterialBg[i] = view.findViewById(materialBgIds[i]);
            
            // Tìm kiếm views nằm bên trong mỗi slot
            ivMaterials[i] = frameMaterials[i].findViewById(R.id.card_iv_image);
            tvMaterialOvr[i] = frameMaterials[i].findViewById(R.id.card_tv_ovr);
            ivMaterialLevel[i] = frameMaterials[i].findViewById(R.id.card_iv_level);
        }
    }

    /**
     * Setup tất cả click listeners
     */
    private void setupClickListeners() {
        // Tap main card → mở chọn thẻ chính
        frameMainCard.setOnClickListener(v -> openCardSelector(-1));

        // Tap material slots → mở chọn thẻ nguyên liệu
        for (int i = 0; i < 5; i++) {
            final int slotIndex = i;
            frameMaterials[i].setOnClickListener(v -> openCardSelector(slotIndex));
        }

        // Upgrade button
        btnUpgrade.setOnClickListener(v -> performUpgrade());
    }

    /**
     * Mở bottom sheet chọn thẻ
     * @param slotIndex -1 = chọn thẻ chính, 0-4 = chọn nguyên liệu slot
     */
    private void openCardSelector(int slotIndex) {
        if (slotIndex != -1 && mainCard == null) {
            android.widget.Toast.makeText(getContext(), "Vui lòng chọn thẻ chính trước!", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        currentMaterialSlot = slotIndex;

        UpgradeBottomSheet bottomSheet = new UpgradeBottomSheet();
        bottomSheet.setOvrCalculator(new UpgradeBottomSheet.OvrCalculator() {
            @Override
            public int getOvr(String typeKey, int level) {
                return getOvrFromData(typeKey, level);
            }

            @Override
            public String getTypeKey(String collectionId) {
                org.json.JSONObject meta = DatabaseLoader.findById(requireContext(), collectionId);
                if (meta != null) {
                    return mapClassToTypeKey(meta.optString("class", ""));
                }
                return "FirstWelcome";
            }
        });
        
        if (slotIndex != -1) {
            List<Objet> currentSelected = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                if (materialCards[i] != null) {
                    currentSelected.add(materialCards[i]);
                }
            }
            bottomSheet.setupMultiSelectMode(mainCard, upgradeAlgorithm, currentSelected);
        }

        bottomSheet.setOnUpgradeCardSelectedListener(new UpgradeBottomSheet.OnUpgradeCardSelectedListener() {
            @Override
            public void onUpgradeCardSelected(Objet card) {
                if (currentMaterialSlot == -1) {
                    mainCard = card;
                    for (int i = 0; i < 5; i++) {
                        materialCards[i] = null;
                    }
                }
                updateUI();
            }

            @Override
            public void onMaterialsSelected(List<Objet> materials) {
                for (int i = 0; i < 5; i++) {
                    if (materials != null && i < materials.size()) {
                        materialCards[i] = materials.get(i);
                    } else {
                        materialCards[i] = null;
                    }
                }
                updateUI();
            }
        });

        bottomSheet.show(getParentFragmentManager(), "upgrade_card_selector");
    }

    /**
     * Thực hiện nâng cấp
     */
    private void performUpgrade() {
        if (mainCard == null) return;
        
        List<Long> materialIds = new ArrayList<>();
        for (Objet mc : materialCards) {
            if (mc != null) {
                materialIds.add((long) mc.getId());
            }
        }
        if (materialIds.isEmpty()) return;

        btnUpgrade.setEnabled(false);
        Long userId = new com.vn.jet.mosco.utils.SessionManager(requireContext()).getUserId();
        com.vn.jet.mosco.model.UpgradeRequest request = new com.vn.jet.mosco.model.UpgradeRequest(userId, (long) mainCard.getId(), materialIds);

        com.vn.jet.mosco.network.GameApiService apiService = com.vn.jet.mosco.network.ApiClient.getClient(requireContext()).create(com.vn.jet.mosco.network.GameApiService.class);
        apiService.upgradeCard(request).enqueue(new retrofit2.Callback<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UpgradeResponse>>() {
            @Override
            public void onResponse(retrofit2.Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UpgradeResponse>> call, retrofit2.Response<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UpgradeResponse>> response) {
                btnUpgrade.setEnabled(true);
                if (response.isSuccessful() && response.body() != null && response.body().getStatus() == 200) {
                    com.vn.jet.mosco.model.UpgradeResponse result = response.body().getData();
                    
                    if (result.isSuccess()) {
                        Toast.makeText(getContext(), "Nâng cấp thành công! +" + result.getNewLevel(), Toast.LENGTH_LONG).show();
                        // Cập nhật lại level và ovr cho thẻ chính
                        mainCard.setCardLevel(result.getNewLevel());
                        mainCard.setOvr(getOvrFromData(mainCard.getTypeKey(), result.getNewLevel()));
                    } else {
                        Toast.makeText(getContext(), "Nâng cấp thất bại! Rớt xuống +" + result.getNewLevel(), Toast.LENGTH_LONG).show();
                        // Cập nhật lại level và ovr cho thẻ chính (bị tụt hạng)
                        mainCard.setCardLevel(result.getNewLevel());
                        mainCard.setOvr(getOvrFromData(mainCard.getTypeKey(), result.getNewLevel()));
                    }

                    // Chỉ reset materials sau khi upgrade, giữ lại thẻ chính
                    for (int i = 0; i < 5; i++) materialCards[i] = null;

                    // Refresh global state ngầm
                    DatabaseLoader.clearUserCache();
                    DatabaseLoader.reloadInventoryFromServer(requireContext(), userId, apiService);
                    
                    // Force update UI ngay lập tức với dữ liệu mainCard đã được cập nhật thủ công
                    updateUI();
                } else {
                    String errorMsg = "Lỗi hệ thống khi nâng cấp";
                    try {
                        if (response.errorBody() != null) {
                            String errorJson = response.errorBody().string();
                            com.google.gson.JsonObject obj = new com.google.gson.Gson().fromJson(errorJson, com.google.gson.JsonObject.class);
                            if (obj.has("message")) errorMsg = obj.get("message").getAsString();
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UpgradeResponse>> call, Throwable t) {
                btnUpgrade.setEnabled(true);
                Toast.makeText(getContext(), "Lỗi mạng: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateInventoryCache() {
        // Obsolete function string due to moving logic to the server.
    }

    /**
     * Cập nhật toàn bộ UI dựa trên state hiện tại
     */
    private void updateUI() {
        if (rootView == null) return;

        updateMainCardUI();
        updateStatsUI();
        updateLevelIndicatorUI();
        updateProgressBarUI();
        updateMaterialsUI();
        updateUpgradeButtonUI();
    }

    /**
     * Cập nhật UI thẻ chính
     */
    private void updateMainCardUI() {
        if (rootView == null) return;
        com.google.android.material.card.MaterialCardView cardMain = rootView.findViewById(R.id.card_main);
        View shimmer = cardMain != null ? cardMain.findViewById(R.id.view_card_shimmer) : null;

        if (mainCard == null) {
            // Placeholder state
            btnAddMainCard.setVisibility(View.VISIBLE);
            ivMainCardImage.setVisibility(View.GONE);
            tvCardOvr.setVisibility(View.GONE);
            ivCardLevelBadge.setVisibility(View.GONE);
            viewCardBg.setBackgroundResource(R.drawable.bg_card_placeholder);
            com.vn.jet.mosco.utils.CardEffectHelper.remove(cardMain, shimmer);
            com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivCardLevelBadge);
        } else {
            // Card selected state
            btnAddMainCard.setVisibility(View.GONE);
            ivMainCardImage.setVisibility(View.VISIBLE);
            viewCardBg.setBackgroundResource(R.drawable.bg_card_filled);

            if (getContext() != null) {
                Glide.with(getContext())
                        .load(mainCard.getImageUrl())
                        .into(ivMainCardImage);
            }

            // Show OVR on card
            tvCardOvr.setVisibility(View.VISIBLE);
            tvCardOvr.setText(String.valueOf(mainCard.getOvr()));

            // Show level badge (as image from assets)
            if (getContext() != null && mainCard.getCardLevel() > 0) {
                ivCardLevelBadge.setVisibility(View.VISIBLE);
                String assetPath = "file:///android_asset/grade/" + Math.min(mainCard.getCardLevel(), 10) + ".png";
                Glide.with(getContext()).load(assetPath).into(ivCardLevelBadge);
                // Phủ ánh kim loại lên Level Badge
                com.vn.jet.mosco.utils.LevelBadgeEffectHelper.apply(ivCardLevelBadge, mainCard.getCardLevel());
            } else {
                ivCardLevelBadge.setVisibility(View.GONE);
                com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivCardLevelBadge);
            }
            
            // True = Áp dụng hiệu ứng bay bổng cho Main Card
            com.vn.jet.mosco.utils.CardEffectHelper.apply(cardMain, shimmer, mainCard, true);
        }
    }

    /**
     * Cập nhật stats bên phải (OVR hiện tại / OVR sau nâng cấp)
     */
    private void updateStatsUI() {
        if (mainCard == null) {
            layoutRightStats.setAlpha(0.2f);
            tvOvrAfter.setText("--");
            tvOvrCurrentSmall.setVisibility(View.GONE);
        } else {
            layoutRightStats.setAlpha(1.0f);

            int currentOvr = mainCard.getOvr();
            int nextLevel = Math.min(mainCard.getCardLevel() + 1, 10);
            int nextOvr = getOvrFromData(mainCard.getTypeKey(), nextLevel);

            tvOvrAfter.setText(String.valueOf(nextOvr));
            tvOvrCurrentSmall.setVisibility(View.VISIBLE);
            tvOvrCurrentSmall.setText(String.valueOf(currentOvr));
        }
    }

    /**
     * Cập nhật level indicator (level hiện tại → level tiếp theo)
     */
    private void updateLevelIndicatorUI() {
        if (mainCard == null) {
            layoutLevelIndicator.setAlpha(0.4f);
            tvLevelCurrent.setVisibility(View.VISIBLE);
            tvLevelCurrent.setText("--");
            tvLevelNext.setVisibility(View.VISIBLE);
            tvLevelNext.setText("--");
            ivLevelCurrent.setVisibility(View.GONE);
            ivLevelNext.setVisibility(View.GONE);
        } else {
            layoutLevelIndicator.setAlpha(1.0f);

            int currentLevel = mainCard.getCardLevel();
            int nextLevel = Math.min(currentLevel + 1, 10);

            // Hiển thị ảnh grade cho level hiện tại từ assets
            if (getContext() != null && currentLevel > 0) {
                tvLevelCurrent.setVisibility(View.GONE);
                ivLevelCurrent.setVisibility(View.VISIBLE);
                String pathCurrent = "file:///android_asset/grade/" + currentLevel + ".png";
                Glide.with(getContext()).load(pathCurrent).into(ivLevelCurrent);
            } else {
                tvLevelCurrent.setVisibility(View.VISIBLE);
                tvLevelCurrent.setText("+" + currentLevel);
                ivLevelCurrent.setVisibility(View.GONE);
            }

            // Hiển thị ảnh grade cho level tiếp theo từ assets
            if (getContext() != null && nextLevel > 0) {
                tvLevelNext.setVisibility(View.GONE);
                ivLevelNext.setVisibility(View.VISIBLE);
                String pathNext = "file:///android_asset/grade/" + nextLevel + ".png";
                Glide.with(getContext()).load(pathNext).into(ivLevelNext);
            } else {
                tvLevelNext.setVisibility(View.VISIBLE);
                tvLevelNext.setText("+" + nextLevel);
                ivLevelNext.setVisibility(View.GONE);
            }

            // Arrow green khi card selected
            TextView tvArrow = rootView.findViewById(R.id.tv_level_arrow);
            if (tvArrow != null) {
                tvArrow.setTextColor(0xFF4ADE80);
            }
        }
    }

    /**
     * Cập nhật progress bar dựa trên thuật toán
     */
    private void updateProgressBarUI() {
        if (mainCard == null || upgradeAlgorithm == null) {
            setProgressWidth(0);
            return;
        }

        List<UpgradeAlgorithm.Card> materials = new ArrayList<>();
        for (Objet mc : materialCards) {
            if (mc != null) {
                UpgradeAlgorithm.Card c = new UpgradeAlgorithm.Card();
                c.id = mc.getIdString();
                c.typeKey = mc.getTypeKey();
                c.level = mc.getCardLevel();
                c.ovr = mc.getOvr();
                materials.add(c);
            }
        }

        if (materials.isEmpty()) {
            setProgressWidth(0);
            return;
        }

        double fillPercent = upgradeAlgorithm.calculateFillPercent(
                createAlgoCard(mainCard), materials);
        setProgressWidth(fillPercent);
    }

    /**
     * Set width của thanh progress bar theo phần trăm
     */
    private void setProgressWidth(double percent) {
        if (viewProgressFill == null) return;

        viewProgressFill.post(() -> {
            ViewGroup parent = (ViewGroup) viewProgressFill.getParent();
            int parentWidth = parent.getWidth();
            int fillWidth = (int) (parentWidth * (percent / 100.0));

            ViewGroup.LayoutParams params = viewProgressFill.getLayoutParams();
            params.width = fillWidth;
            viewProgressFill.setLayoutParams(params);
        });
    }

    /**
     * Cập nhật UI các material slots
     */
    private void updateMaterialsUI() {
        if (rootView == null) return;
        int selectedCount = 0;
        int[] frameIds = {R.id.frame_material_1, R.id.frame_material_2, R.id.frame_material_3, R.id.frame_material_4, R.id.frame_material_5};
        
        for (int i = 0; i < 5; i++) {
            com.google.android.material.card.MaterialCardView cardView = rootView.findViewById(frameIds[i]);
            View shimmer = cardView != null ? cardView.findViewById(R.id.view_card_shimmer) : null;

            if (materialCards[i] != null) {
                selectedCount++;
                // Show card image
                ivMaterials[i].setVisibility(View.VISIBLE);
                tvMaterialPlus[i].setVisibility(View.GONE);

                if (getContext() != null) {
                    Glide.with(getContext())
                            .load(materialCards[i].getImageUrl())
                            .into(ivMaterials[i]);
                }

                // Show material OVR and Level Image from assets
                tvMaterialOvr[i].setVisibility(View.VISIBLE);
                tvMaterialOvr[i].setText(String.valueOf(materialCards[i].getOvr()));
                
                if (getContext() != null && materialCards[i].getCardLevel() > 0) {
                    ivMaterialLevel[i].setVisibility(View.VISIBLE);
                    String assetPath = "file:///android_asset/grade/" + Math.min(materialCards[i].getCardLevel(), 10) + ".png";
                    Glide.with(getContext()).load(assetPath).into(ivMaterialLevel[i]);
                    com.vn.jet.mosco.utils.LevelBadgeEffectHelper.apply(ivMaterialLevel[i], materialCards[i].getCardLevel());
                } else {
                    ivMaterialLevel[i].setVisibility(View.GONE);
                    com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivMaterialLevel[i]);
                }

                // Cập nhật background
                viewMaterialBg[i].setBackgroundResource(R.drawable.bg_material_filled);
                
                // False = Bỏ hiệu ứng bay bổng (Idle Floating) cho Material Card
                com.vn.jet.mosco.utils.CardEffectHelper.apply(cardView, shimmer, materialCards[i], false);
            } else {
                // Empty slot
                ivMaterials[i].setVisibility(View.GONE);
                tvMaterialPlus[i].setVisibility(View.VISIBLE);
                viewMaterialBg[i].setBackgroundResource(R.drawable.bg_material_slot);
                tvMaterialOvr[i].setVisibility(View.GONE);
                ivMaterialLevel[i].setVisibility(View.GONE);
                
                com.vn.jet.mosco.utils.CardEffectHelper.remove(cardView, shimmer);
                com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivMaterialLevel[i]);
            }
        }

        // Update count text
        tvMaterialsCount.setText(selectedCount + " / 5 Selected");
        if (selectedCount > 0) {
            tvMaterialsCount.setTextColor(0xFFa3c9ff); // primary color
        } else {
            tvMaterialsCount.setTextColor(0xFFc2c6d1); // on-surface-variant
        }
    }



    /**
     * Cập nhật state nút Upgrade
     */
    private void updateUpgradeButtonUI() {
        boolean hasMaterials = false;
        for (Objet mc : materialCards) {
            if (mc != null) {
                hasMaterials = true;
                break;
            }
        }

        boolean canUpgrade = mainCard != null && hasMaterials && mainCard.getCardLevel() < 10;

        if (canUpgrade) {
            btnUpgrade.setEnabled(true);
            btnUpgrade.setAlpha(1.0f);
            btnUpgrade.setBackgroundResource(R.drawable.bg_upgrade_button_active);
            btnUpgrade.setTextColor(0xFFFFFFFF);
        } else {
            btnUpgrade.setEnabled(false);
            btnUpgrade.setAlpha(0.6f);
            btnUpgrade.setBackgroundResource(R.drawable.bg_upgrade_button_disabled);
            btnUpgrade.setTextColor(0xFFc2c6d1);
        }
    }

    /**
     * Tạo UpgradeAlgorithm.Card từ UpgradeCard model
     */
    private UpgradeAlgorithm.Card createAlgoCard(Objet card) {
        UpgradeAlgorithm.Card c = new UpgradeAlgorithm.Card();
        c.id = card.getIdString();
        c.typeKey = card.getTypeKey();
        c.level = card.getCardLevel();
        c.ovr = card.getOvr();
        return c;
    }
}

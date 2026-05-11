package com.vn.jet.mosco.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.vn.jet.mosco.ProfileViewModel;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.model.UserStats;
import com.vn.jet.mosco.utils.CardEffectHelper;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.LevelBadgeEffectHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab Exhibit trong Profile V2 - Final Premium Version.
 * [RULE] 100% Pager Mode (View2Paper), NO Grid.
 * [UX] Card enlarged by 25%, Clearer Name Tags, Fixed Data Binding.
 */
public class ProfileExhibitFragment extends Fragment {

    private ViewPager2 vpShowcase;
    private View layoutShowcasePager;
    private View btnPrev, btnNext;
    private ProfileViewModel viewModel;
    private ShowcasePagerAdapter pagerAdapter;
    
    private boolean isEditMode = false;
    private List<String> currentShowcaseIds = new ArrayList<>();
    private static final int SHOWCASE_COUNT = 8;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_exhibit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        vpShowcase = view.findViewById(R.id.vp_showcase);
        layoutShowcasePager = view.findViewById(R.id.layout_showcase_pager);
        btnPrev = view.findViewById(R.id.btn_showcase_prev);
        btnNext = view.findViewById(R.id.btn_showcase_next);
        
        setupViewPager();
        setupNavigationArrows();
        
        // [GLOW FIX] Đảm bảo RecyclerView bên trong ViewPager2 không cắt viền Glow
        View innerRecyclerView = vpShowcase.getChildAt(0);
        if (innerRecyclerView instanceof ViewGroup) {
            ((ViewGroup) innerRecyclerView).setClipChildren(false);
            ((ViewGroup) innerRecyclerView).setClipToPadding(false);
        }

        // [STABILITY] Đảm bảo Master Data được nạp để lấy metadata (Name, Class) chính xác
        DatabaseLoader.initMasterData(requireContext());

        if (getParentFragment() != null) {
            viewModel = new ViewModelProvider(getParentFragment()).get(ProfileViewModel.class);
            viewModel.getUserStats().observe(getViewLifecycleOwner(), this::renderData);
        }
    }

    private void setupNavigationArrows() {
        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                int current = vpShowcase.getCurrentItem();
                if (current > 0) vpShowcase.setCurrentItem(current - 1, true);
            });
        }
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                int current = vpShowcase.getCurrentItem();
                if (current < SHOWCASE_COUNT - 1) vpShowcase.setCurrentItem(current + 1, true);
            });
        }
        
        vpShowcase.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (btnPrev != null) btnPrev.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
                if (btnNext != null) btnNext.setVisibility(position == SHOWCASE_COUNT - 1 ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }

    private void setupViewPager() {
        if (vpShowcase == null) return;
        
        pagerAdapter = new ShowcasePagerAdapter(new ArrayList<>());
        vpShowcase.setAdapter(pagerAdapter);
        vpShowcase.setOffscreenPageLimit(3);
        vpShowcase.setUserInputEnabled(false); // [UX FIX] Disable swiping, use arrows only
        
        CompositePageTransformer transformer = new CompositePageTransformer();
        transformer.addTransformer(new MarginPageTransformer(getResources().getDimensionPixelSize(R.dimen.spacing_md)));
        transformer.addTransformer((page, position) -> {
            float r = 1 - Math.abs(position);
            
            android.util.TypedValue scaleMinVal = new android.util.TypedValue();
            getResources().getValue(R.dimen.carousel_scale_min, scaleMinVal, true);
            android.util.TypedValue scaleRangeVal = new android.util.TypedValue();
            getResources().getValue(R.dimen.carousel_scale_range, scaleRangeVal, true);
            float scale = scaleMinVal.getFloat() + r * scaleRangeVal.getFloat(); 
            page.setScaleY(scale);
            page.setScaleX(scale);
            
            android.util.TypedValue alphaMinVal = new android.util.TypedValue();
            getResources().getValue(R.dimen.carousel_alpha_min, alphaMinVal, true);
            android.util.TypedValue alphaRangeVal = new android.util.TypedValue();
            getResources().getValue(R.dimen.carousel_alpha_range, alphaRangeVal, true);
            page.setAlpha(alphaMinVal.getFloat() + r * alphaRangeVal.getFloat());
        });
        vpShowcase.setPageTransformer(transformer);
    }

    private void renderData(UserStats stats) {
        if (stats == null) return;
        List<String> validIds = new ArrayList<>();
        boolean needsUpdate = false;
        
        List<String> ids = stats.getShowcaseCardIds() != null ? stats.getShowcaseCardIds() : new ArrayList<>();
        
        // [FIX] Nếu là Owner đang xem profile chính mình, kiểm tra xem thẻ còn trong Inventory không
        // [FIX] Chỉ dọn dẹp "thẻ ma" nếu Inventory Cache đã sẵn sàng.
        // Tránh race condition khi Profile load nhanh hơn Inventory dẫn đến bị xóa sạch showcase.
        if (stats.getId() != null && stats.getId().equals(DatabaseLoader.cachedInventoryUserId) && DatabaseLoader.cachedCollectionMap != null) {
            for (String id : ids) {
                if (id == null || id.trim().isEmpty() || id.equals("null")) {
                    validIds.add("");
                } else if (DatabaseLoader.cachedCollectionMap.containsKey(id)) {
                    validIds.add(id);
                } else {
                    // Thẻ này thực sự không còn trong kho -> tự động tháo
                    validIds.add("");
                    needsUpdate = true;
                }
            }
        } else {
            // Nếu cache chưa có hoặc đang xem profile người khác, cứ dùng data từ server
            validIds.addAll(ids);
        }
        
        this.currentShowcaseIds = validIds;
        renderShowcaseZone(currentShowcaseIds);
        
        // Nếu phát hiện thẻ ma, báo ViewModel tự động dọn dẹp trên Server
        if (needsUpdate && viewModel != null) {
            viewModel.updateShowcase(validIds);
        }
    }

    private void renderShowcaseZone(List<String> showcaseCardIds) {
        if (vpShowcase == null || layoutShowcasePager == null) return;
        
        layoutShowcasePager.setVisibility(View.VISIBLE);
        if (pagerAdapter != null) {
            pagerAdapter.updateIds(showcaseCardIds);
        }
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        if (pagerAdapter != null) {
            // [UX FIX] Sử dụng payload để chỉ cập nhật UI Edit (nút +) mà không reset ViewPager2
            pagerAdapter.notifyItemRangeChanged(0, SHOWCASE_COUNT, "PAYLOAD_EDIT_MODE");
        }
    }

    // [DRY] Dùng AppConfig.ASSET_GRADE_PATH thay vì khai báo local
    private static final String PICKER_TAG = "InventoryPicker";
    private static final long CLICK_DEBOUNCE_MS = 500;
    private long lastClickTime = 0;

    private void openInventoryPicker(int index) {
        if (!isEditMode) return;
        
        // [UX] Click Debounce
        if (System.currentTimeMillis() - lastClickTime < CLICK_DEBOUNCE_MS) return;
        lastClickTime = System.currentTimeMillis();

        // Tránh mở chồng nhiều BottomSheet
        if (getChildFragmentManager().findFragmentByTag(PICKER_TAG) != null) return;

        InventoryBottomSheet sheet = new InventoryBottomSheet();
        sheet.setShowcaseMode(true); // Cho phép chọn cả thẻ đang BUSY (Stage)
        sheet.setOnCardSelectedListener(card -> {
            if (card != null) {
                updateShowcase(index, card.getCollectionId());
            }
        });
        sheet.show(getChildFragmentManager(), PICKER_TAG);
    }

    private void updateShowcase(int index, String collectionId) {
        if (collectionId != null && !collectionId.isEmpty()) {
            // [FIX] Kiểm tra trùng Objet
            for (String id : currentShowcaseIds) {
                if (collectionId.equals(id)) {
                    Toast.makeText(getContext(), R.string.showcase_msg_duplicate, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        List<String> newIds = new ArrayList<>(currentShowcaseIds);
        while (newIds.size() < SHOWCASE_COUNT) {
            newIds.add("");
        }
        
        if (index >= 0 && index < SHOWCASE_COUNT) {
            String finalId = collectionId != null ? collectionId : "";
            newIds.set(index, finalId);
            
            // Đồng bộ local state ngay lập tức
            this.currentShowcaseIds = newIds;
            viewModel.updateShowcase(newIds);
            
            // [UX FIX] Cập nhật ngay lập tức adapter tại vị trí thay đổi
            if (pagerAdapter != null) {
                pagerAdapter.updateIdAt(index, finalId);
            }
        }
    }

    private void unequipObjet(int index) {
        updateShowcase(index, "");
    }

    /**
     * [DRY] Bind dữ liệu và áp dụng hiệu ứng "SpinFragment" (Shimmer, Glow, Badge).
     */
    private void bindCardView(View cardView, String collectionId, int position) {
        MaterialCardView cvContainer = cardView.findViewById(R.id.cv_card_container);
        ImageView ivImage = cardView.findViewById(R.id.card_iv_image);
        ImageView ivBack = cardView.findViewById(R.id.card_iv_back);
        View shimmer = cardView.findViewById(R.id.view_card_shimmer);
        TextView tvName = cardView.findViewById(R.id.tv_card_name);
        TextView tvOvr = cardView.findViewById(R.id.card_tv_ovr);
        ImageView ivLevel = cardView.findViewById(R.id.card_iv_level);
        View layoutCore = cardView.findViewById(R.id.layout_core);
        View layoutEmpty = cardView.findViewById(R.id.layout_empty_placeholder);
        View layoutAddPlus = cardView.findViewById(R.id.layout_add_objet_plus);

        if (cvContainer == null) return;

        // Luôn ẩn OVR theo yêu cầu "NO OVR IN EVERY WHERE"
        if (tvOvr != null) tvOvr.setVisibility(View.GONE);

        if (collectionId == null || collectionId.isEmpty() || collectionId.equals("null")) {
            // [SPIN STYLE] Trạng thái slot trống - Hiển thị placeholder giống Spin
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (layoutCore != null) layoutCore.setVisibility(View.GONE);
            if (ivBack != null) ivBack.setVisibility(View.GONE);
            
            if (tvName != null) {
                tvName.setVisibility(View.VISIBLE);
                tvName.setText(R.string.showcase_empty_slot);
            }
            
            // Hiển thị nút "+" nếu đang ở chế độ Edit
            if (layoutAddPlus != null) {
                layoutAddPlus.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            }

            View btnUnequip = cardView.findViewById(R.id.btn_unequip);
            if (btnUnequip != null) btnUnequip.setVisibility(View.GONE);

            CardEffectHelper.applyEmptyStateGlow(cvContainer, true);
        } else {
            // Trạng thái có Objet
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
            if (layoutCore != null) layoutCore.setVisibility(View.VISIBLE);
            if (layoutAddPlus != null) layoutAddPlus.setVisibility(View.GONE);
            
            View btnUnequip = cardView.findViewById(R.id.btn_unequip);
            if (btnUnequip != null) {
                btnUnequip.setVisibility(View.GONE); // Mặc định ẩn, chỉ hiện khi hold
                btnUnequip.setOnClickListener(v -> unequipObjet(position));
            }

            // [FIX] Luôn ẩn các nút "+" (của cả core và placeholder) khi đã có Objet
            if (layoutAddPlus != null) layoutAddPlus.setVisibility(View.GONE);
            View coreAddPlus = cardView.findViewById(R.id.layout_add_objet_plus);
            if (coreAddPlus != null) coreAddPlus.setVisibility(View.GONE);
            
            // [FIX DATA BINDING] Sử dụng DatabaseLoader để lấy metadata
            org.json.JSONObject cardData = DatabaseLoader.findByCollectionId(getContext(), collectionId);
            if (cardData == null) {
                DatabaseLoader.initMasterDataSync(getContext());
                cardData = DatabaseLoader.findByCollectionId(getContext(), collectionId);
            }

            if (cardData == null) {
                if (tvName != null) {
                    tvName.setVisibility(View.VISIBLE);
                    tvName.setText(getString(R.string.showcase_unknown_card, collectionId.substring(0, Math.min(4, collectionId.length()))));
                }
                return;
            }

            if (tvName != null) {
                tvName.setVisibility(View.VISIBLE);
                // [FIX] Binding đầy đủ Name (Member + CollectionNo)
                String member = cardData.optString("member", "");
                String collectionNo = cardData.optString("collectionNo", "");
                String name = member + (collectionNo.isEmpty() ? "" : " " + collectionNo);
                if (name.trim().isEmpty()) name = collectionId;
                tvName.setText(name);
            }
            
            if (ivImage != null) {
                ivImage.setAlpha(1.0f);
                ivImage.setVisibility(View.VISIBLE);
                String frontImage = cardData.optString("frontImage");
                // Luồng tải ưu tiên: Showcase Pager dùng bản Original để đạt độ nét tối đa (Quiet Luxury)
                com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(ivImage, frontImage, false);
            }

            if (ivBack != null) {
                ivBack.setVisibility(View.GONE);
                ivBack.setImageResource(R.drawable.objet_back_spin);
            }

            // Mock Objet để nạp hiệu ứng chuẩn CardEffectHelper
            String frontImageStr = cardData.optString("frontImage");
            Objet mockObj = new Objet(0, collectionId, frontImageStr, 1, 0, cardData.optInt("upgradeLevel", 0));
            
            // Áp dụng hiệu ứng chuẩn (Shimmer + Glow + Floating)
            CardEffectHelper.apply(cvContainer, shimmer, mockObj, true);

            // Hiệu ứng Level Badge
            if (ivLevel != null) {
                int level = cardData.optInt("upgradeLevel", 0);
                if (level > 0) {
                    ivLevel.setVisibility(View.VISIBLE);
                    Glide.with(this).load(getString(R.string.asset_grade_path) + level + ".png").into(ivLevel);
                    LevelBadgeEffectHelper.apply(ivLevel, level);
                } else {
                    ivLevel.setVisibility(View.GONE);
                }
            }
        }
    }

    /**
     * Adapter cho ViewPager2 để hiển thị Showcase dạng Pager.
     */
    private class ShowcasePagerAdapter extends RecyclerView.Adapter<ShowcaseViewHolder> {
        private final List<String> ids = new ArrayList<>();

        public ShowcasePagerAdapter(List<String> initialIds) { 
            if (initialIds != null) this.ids.addAll(initialIds); 
        }

        public void updateIds(List<String> newIds) {
            List<String> paddedIds = new ArrayList<>(newIds);
            while (paddedIds.size() < SHOWCASE_COUNT) paddedIds.add("");
            
            // Tránh refresh toàn bộ nếu dữ liệu không đổi (Ví dụ: trigger từ LiveData sau khi mình vừa tự update)
            if (this.ids.equals(paddedIds)) return;

            this.ids.clear();
            this.ids.addAll(paddedIds);
            notifyDataSetChanged();
        }

        public void updateIdAt(int index, String cardId) {
            if (index >= 0 && index < ids.size()) {
                ids.set(index, cardId);
                notifyItemChanged(index);
            }
        }

        @NonNull
        @Override
        public ShowcaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_showcase_pager, parent, false);
            return new ShowcaseViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ShowcaseViewHolder holder, int position) {
            String cardId = (position < ids.size()) ? ids.get(position) : "";
            bindCardView(holder.itemView, cardId, position);
            
            holder.itemView.setOnClickListener(v -> {
                if (isEditMode) {
                    openInventoryPicker(position);
                } else {
                    // Cơ chế lật 3D nếu không ở Edit Mode
                    toggleFlip(holder.itemView);
                }
            });

            // [NEW] Hold 2s để hiện nút gỡ (Un-equip)
            holder.itemView.setOnLongClickListener(v -> {
                if (isEditMode) {
                    // cardId đã có ở scope bên ngoài
                    if (cardId != null && !cardId.isEmpty() && !cardId.equals("null")) {
                        View btnUnequip = holder.itemView.findViewById(R.id.btn_unequip);
                        if (btnUnequip != null) {
                            btnUnequip.setVisibility(View.VISIBLE);
                            return true;
                        }
                    }
                }
                return false;
            });
        }

        @Override
        public void onBindViewHolder(@NonNull ShowcaseViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (payloads.contains("PAYLOAD_EDIT_MODE")) {
                // Chỉ cập nhật hiển thị nút Plus và ClickListener
                String cardId = (position < ids.size()) ? ids.get(position) : "";
                boolean isEmpty = cardId == null || cardId.isEmpty() || cardId.equals("null");
                View layoutAddPlus = holder.itemView.findViewById(R.id.layout_add_objet_plus);
                if (layoutAddPlus != null && isEmpty) {
                    layoutAddPlus.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
                }
            } else {
                super.onBindViewHolder(holder, position, payloads);
            }
        }

        @Override
        public int getItemCount() { return SHOWCASE_COUNT; }
    }

    private void toggleFlip(View itemView) {
        View layoutCore = itemView.findViewById(R.id.layout_core);
        View ivBack = itemView.findViewById(R.id.card_iv_back);
        View cvContainer = itemView.findViewById(R.id.cv_card_container);
        if (layoutCore == null || ivBack == null || cvContainer == null) return;

        boolean isBackVisible = ivBack.getVisibility() == View.VISIBLE;
        
        // [UX FIX] Hiệu ứng 3D Premium hơn với camera distance trên container
        float distance = 12000;
        float scale = getResources().getDisplayMetrics().density;
        cvContainer.setCameraDistance(distance * scale);

        ObjectAnimator flip1 = ObjectAnimator.ofFloat(cvContainer, "rotationY", 0f, 90f);
        flip1.setDuration(300);
        flip1.setInterpolator(new AccelerateInterpolator());
        
        flip1.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isBackVisible) {
                    ivBack.setVisibility(View.GONE);
                    layoutCore.setVisibility(View.VISIBLE);
                } else {
                    ivBack.setVisibility(View.VISIBLE);
                    layoutCore.setVisibility(View.GONE);
                }
                ObjectAnimator flip2 = ObjectAnimator.ofFloat(cvContainer, "rotationY", -90f, 0f);
                flip2.setDuration(300);
                flip2.setInterpolator(new DecelerateInterpolator());
                flip2.start();
            }
        });
        flip1.start();
    }

    private static class ShowcaseViewHolder extends RecyclerView.ViewHolder {
        public ShowcaseViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

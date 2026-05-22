package com.vn.jet.mosco.adapter;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.CardDisplayItem;
import com.vn.jet.mosco.utils.CardAssetManager;
import com.vn.jet.mosco.utils.GlideBindingAdapter;
import com.vn.jet.mosco.utils.PinManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * UnifiedCardAdapter — Adapter thống nhất cho cả Inventory (Objets) và Album (Collection Book).
 *
 * Tổng hợp điểm mạnh từ BaseInventoryAdapter + CollectionBookAdapter:
 * - Local-First image loading (qua GlideBindingAdapter chuẩn hóa)
 * - Client-side pagination (60 items/page, progressive loading)
 * - Skeleton loading states (ViewType chính xác)
 * - Multi-select mode (Inventory)
 * - Owned/Locked overlay (Album)
 * - Name Tag formatting chuẩn hóa (qua CardDisplayItem.getFormattedNameTag())
 * - Grade badge binding
 * - Disabled/Busy states (Stage)
 *
 * Chế độ hiển thị được quyết định bởi DisplayMode:
 * - INVENTORY: Hiện kho đồ, multi-select, disabled states
 * - ALBUM: Hiện bộ sưu tập với owned/locked overlay
 */
public class UnifiedCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // =============== DISPLAY MODES ===============
    public enum DisplayMode {
        INVENTORY,  // Hiện kho đồ: multi-select, busy/disabled states
        ALBUM       // Hiện bộ sưu tập: owned/locked overlay
    }

    // =============== CALLBACKS ===============
    public interface OnCardClickListener {
        void onCardClick(CardDisplayItem item);
    }

    public interface OnCardSelectListener {
        void onCardSelected(CardDisplayItem item, boolean selected);
    }

    // =============== VIEW TYPES ===============
    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_SKELETON = 1;

    // =============== PAGINATION ===============
    // Số phần tử mỗi trang — tập trung quản lý, không hardcode rải rác
    private static final int PAGE_SIZE = 60;
    // Số skeleton hiển thị khi đang tải (Grid 3 cột × 4 hàng)
    private static final int SKELETON_COUNT = 12;
    // Số skeleton cuối grid khi đang nạp thêm
    private static final int PAGING_SKELETON_COUNT = 3;
    // Ngưỡng kích hoạt nạp thêm (khoảng cách từ cuối danh sách)
    private static final int LOAD_THRESHOLD = 15;

    // =============== STATE ===============
    private final List<CardDisplayItem> allItems;
    private final List<CardDisplayItem> displayItems;
    private List<CardDisplayItem> currentFilteredList;
    private int currentLimit;
    private boolean isPagingLoading = false;
    private boolean isLoading = false;

    private final DisplayMode displayMode;
    private final OnCardClickListener clickListener;
    private final Context mContext;

    // =============== MULTI-SELECT (INVENTORY mode) ===============
    private boolean isMultiSelectMode = false;
    private Set<Long> selectedIds = new HashSet<>();
    private Set<Long> disabledIds = new HashSet<>();
    private Set<String> disabledMembers = new HashSet<>();
    private OnCardSelectListener selectListener;

    // =============== CONSTRUCTOR ===============

    /**
     * Khởi tạo UnifiedCardAdapter.
     *
     * @param items    Danh sách items ban đầu
     * @param rv       RecyclerView cần gắn (để cấu hình tối ưu + scroll listener)
     * @param mode     Chế độ hiển thị (INVENTORY / ALBUM)
     * @param listener Callback khi click item
     */
    public UnifiedCardAdapter(@NonNull List<CardDisplayItem> items,
                              @NonNull RecyclerView rv,
                              @NonNull DisplayMode mode,
                              @NonNull OnCardClickListener listener) {
        this.allItems = new ArrayList<>(items);
        this.currentFilteredList = new ArrayList<>(items);
        this.displayItems = new ArrayList<>();
        this.displayMode = mode;
        this.clickListener = listener;
        this.mContext = rv.getContext();
        this.currentLimit = PAGE_SIZE;

        // Khởi tạo hiển thị trang đầu tiên
        int maxLimit = Math.min(currentLimit, currentFilteredList.size());
        if (maxLimit > 0) {
            displayItems.addAll(currentFilteredList.subList(0, maxLimit));
        }

        // Tối ưu RecyclerView cho hiệu năng cao
        rv.setHasFixedSize(true);
        rv.setItemViewCacheSize(20);

        // Đăng ký tự động phân trang
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && !isPagingLoading && !isLoading) {
                    GridLayoutManager lm = (GridLayoutManager) recyclerView.getLayoutManager();
                    if (lm != null && lm.findLastVisibleItemPosition() >= getItemCount() - LOAD_THRESHOLD) {
                        loadNextPage();
                    }
                }
            }
        });
    }

    // =============== PUBLIC API ===============

    /**
     * Hiển thị trạng thái Skeleton Loading (12 ô) khi đang tải dữ liệu.
     */
    public void setLoading(boolean loading) {
        this.isLoading = loading;
        notifyDataSetChanged();
    }

    /**
     * Cập nhật toàn bộ danh sách — reset phân trang về trang 1.
     */
    public void updateData(@NonNull List<CardDisplayItem> newItems) {
        boolean wasLoading = this.isLoading;

        this.allItems.clear();
        this.allItems.addAll(newItems);
        this.currentFilteredList = new ArrayList<>(newItems);
        this.currentLimit = PAGE_SIZE;
        this.isPagingLoading = false;
        this.isLoading = false;

        List<CardDisplayItem> newDisplayItems = new ArrayList<>();
        int maxLimit = Math.min(currentLimit, currentFilteredList.size());
        if (maxLimit > 0) {
            newDisplayItems.addAll(currentFilteredList.subList(0, maxLimit));
        }

        if (wasLoading) {
            // Nếu trước đó đang ở trạng thái skeleton loading, thực hiện đổi toàn bộ layout sang danh sách thật
            this.displayItems.clear();
            this.displayItems.addAll(newDisplayItems);
            notifyDataSetChanged();
        } else {
            // Sử dụng DiffUtil để so sánh chính xác phần tử thay đổi, loại bỏ hiện tượng giật ẩn hiện khi nạp/cập nhật dữ liệu từ cache và server
            final List<CardDisplayItem> oldDisplayItems = new ArrayList<>(this.displayItems);
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldDisplayItems.size();
                }

                @Override
                public int getNewListSize() {
                    return newDisplayItems.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    CardDisplayItem oldItem = oldDisplayItems.get(oldItemPosition);
                    CardDisplayItem newItem = newDisplayItems.get(newItemPosition);
                    if (oldItem == null || newItem == null) return false;
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    CardDisplayItem oldItem = oldDisplayItems.get(oldItemPosition);
                    CardDisplayItem newItem = newDisplayItems.get(newItemPosition);
                    if (oldItem == null || newItem == null) return false;
                    return oldItem.isOwned() == newItem.isOwned()
                            && oldItem.getUpgradeLevel() == newItem.getUpgradeLevel()
                            && oldItem.getLevel() == newItem.getLevel()
                            && oldItem.getOvr() == newItem.getOvr()
                            && (oldItem.getFrontImage() != null ? oldItem.getFrontImage().equals(newItem.getFrontImage()) : newItem.getFrontImage() == null)
                            && (oldItem.getMember() != null ? oldItem.getMember().equals(newItem.getMember()) : newItem.getMember() == null)
                            && (oldItem.getStatus() != null ? oldItem.getStatus().equals(newItem.getStatus()) : newItem.getStatus() == null);
                }
            });

            this.displayItems.clear();
            this.displayItems.addAll(newDisplayItems);
            diffResult.dispatchUpdatesTo(this);
        }
    }

    /**
     * Bật/tắt chế độ multi-select (chỉ dùng cho INVENTORY mode).
     */
    public void setMultiSelectMode(boolean enabled, OnCardSelectListener listener) {
        this.isMultiSelectMode = enabled;
        this.selectListener = listener;
    }

    public void setSelectedIds(Set<Long> ids) {
        this.selectedIds = ids != null ? ids : new HashSet<>();
        notifyDataSetChanged();
    }

    /**
     * Đặt trạng thái disabled cho các thẻ đang bận (Stage/Mission).
     */
    public void setDisabledStates(Set<Long> ids, Set<String> members) {
        this.disabledIds = ids != null ? ids : new HashSet<>();
        this.disabledMembers = members != null ? members : new HashSet<>();
        notifyDataSetChanged();
    }

    /**
     * Lấy danh sách hiện đang hiển thị (sau filter + phân trang).
     */
    public List<CardDisplayItem> getDisplayItems() {
        return displayItems;
    }

    // =============== PAGINATION ===============

    private void loadNextPage() {
        if (currentLimit >= currentFilteredList.size() || isPagingLoading || isLoading) return;

        isPagingLoading = true;

        // Hiện skeleton chờ cuối grid
        notifyItemRangeInserted(displayItems.size(), PAGING_SKELETON_COUNT);

        // Delay nhẹ (300ms) để tạo gia tốc lướt mượt
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            currentLimit += PAGE_SIZE;
            int maxLimit = Math.min(currentLimit, currentFilteredList.size());
            List<CardDisplayItem> nextChunk = new ArrayList<>(
                    currentFilteredList.subList(displayItems.size(), maxLimit));

            isPagingLoading = false;
            // Xóa skeleton chờ
            notifyItemRangeRemoved(displayItems.size(), PAGING_SKELETON_COUNT);

            // Chèn dữ liệu thực
            int insertPos = displayItems.size();
            displayItems.addAll(nextChunk);
            notifyItemRangeInserted(insertPos, nextChunk.size());

            // Prefetch ảnh cho trang tiếp theo (giới hạn 1 page, không "thả xích")
            preloadNextPageImages();
        }, 300);
    }

    /**
     * Tải ngầm (Prefetch) ảnh cho ĐÚNG 1 trang tiếp theo — không preload toàn bộ.
     * Sử dụng GlideBindingAdapter chuẩn hóa cho nhất quán.
     */
    private void preloadNextPageImages() {
        if (currentFilteredList == null || displayItems == null) return;

        int startPos = displayItems.size();
        // Giới hạn prefetch đúng 1 page tiếp theo (fix bug "thả xích")
        int endPos = Math.min(startPos + PAGE_SIZE, currentFilteredList.size());

        if (startPos >= endPos) return;

        for (int i = startPos; i < endPos; i++) {
            CardDisplayItem item = currentFilteredList.get(i);
            if (item == null || item.getFrontImage() == null) continue;

            // Sử dụng cơ chế local-first thống nhất
            java.io.File localFile = CardAssetManager.getLocalFile(mContext, item.getFrontImage());
            int gridW = mContext.getResources().getDimensionPixelSize(R.dimen.card_grid_width);
            int gridH = mContext.getResources().getDimensionPixelSize(R.dimen.card_grid_height);

            if (localFile != null && localFile.exists()) {
                Glide.with(mContext)
                        .load(localFile)
                        .override(gridW, gridH)
                        .priority(com.bumptech.glide.Priority.LOW)
                        .preload();
            } else {
                String fallbackUrl = CardAssetManager.convertToVariant(item.getFrontImage(), "thumbnail");
                Glide.with(mContext)
                        .load(fallbackUrl)
                        .override(gridW, gridH)
                        .priority(com.bumptech.glide.Priority.LOW)
                        .preload();
            }
        }
    }

    // =============== VIEW TYPE ===============

    @Override
    public int getItemViewType(int position) {
        if (isLoading) return VIEW_TYPE_SKELETON;
        if (displayItems != null && position >= displayItems.size()) return VIEW_TYPE_SKELETON;
        return VIEW_TYPE_ITEM;
    }

    // =============== CREATE VIEW HOLDER ===============

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_SKELETON) {
            View v = inflater.inflate(R.layout.item_objet_skeleton, parent, false);
            return new SkeletonViewHolder(v);
        }

        // Chọn layout theo mode — cả 2 dùng chung viewType ITEM
        int layoutRes = (displayMode == DisplayMode.ALBUM)
                ? R.layout.item_collection_book_card
                : R.layout.item_inventory_card;
        View v = inflater.inflate(layoutRes, parent, false);
        return new CardViewHolder(v, displayMode);
    }

    // =============== BIND VIEW HOLDER ===============

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (!(holder instanceof CardViewHolder)) return;

        CardViewHolder vh = (CardViewHolder) holder;
        if (position >= displayItems.size()) return;

        CardDisplayItem item = displayItems.get(position);
        if (item == null) return;

        // 1. Bind Name Tag (chuẩn hóa qua model)
        bindNameTag(vh, item);

        // 2. Bind Image (thống nhất qua GlideBindingAdapter)
        bindImage(vh, item);

        // 3. Bind Grade Badge
        bindGradeBadge(vh, item);

        // 4. Bind trạng thái theo mode
        if (displayMode == DisplayMode.ALBUM) {
            bindAlbumState(vh, item);
        } else {
            bindInventoryState(vh, item, position);
        }

        // 5. Click handler
        bindClickListener(vh, item, position);
    }

    // =============== BIND HELPERS ===============

    /**
     * Bind Name Tag — sử dụng logic chuẩn hóa từ CardDisplayItem.
     */
    private void bindNameTag(@NonNull CardViewHolder vh, @NonNull CardDisplayItem item) {
        if (vh.tvNameTag == null) return;

        String nameTag = item.getFormattedNameTag();
        if (nameTag.isEmpty()) {
            vh.tvNameTag.setText(mContext.getString(R.string.card_name_unknown));
        } else {
            vh.tvNameTag.setText(nameTag);
        }
        vh.tvNameTag.setVisibility(View.VISIBLE);
    }

    /**
     * Bind Image — thống nhất qua GlideBindingAdapter (Local-First + Cloudflare fallback).
     * Đồng thời quản lý Skeleton visibility.
     */
    private void bindImage(@NonNull CardViewHolder vh, @NonNull CardDisplayItem item) {
        // Hiện skeleton trước khi ảnh load xong
        if (vh.layoutSkeleton != null) {
            vh.layoutSkeleton.setVisibility(View.VISIBLE);
        }

        // Sử dụng GlideBindingAdapter chuẩn hóa — 1 luồng duy nhất
        GlideBindingAdapter.loadImage(vh.ivCardImage, item.getFrontImage(), true);

        // GlideBindingAdapter đã tự quản lý skeleton visibility (tìm R.id.layout_card_skeleton)
        // Nhưng layout Album dùng R.id.layout_skeleton → cần xử lý riêng nếu khác ID
        if (vh.layoutSkeleton != null && vh.layoutSkeleton.getId() != R.id.layout_card_skeleton) {
            // Fallback: tự ẩn skeleton sau khi GlideBindingAdapter xử lý
            vh.ivCardImage.post(() -> {
                if (vh.layoutSkeleton != null) vh.layoutSkeleton.setVisibility(View.GONE);
            });
        }
    }

    /**
     * Bind Grade Badge — hiển thị asset grade theo upgradeLevel.
     */
    private void bindGradeBadge(@NonNull CardViewHolder vh, @NonNull CardDisplayItem item) {
        if (vh.ivLevel == null) return;

        if (item.getUpgradeLevel() > 0) {
            String assetPath = mContext.getString(R.string.asset_grade_path) + item.getUpgradeLevel() + ".png";
            Glide.with(mContext).load(assetPath).into(vh.ivLevel);
            // Áp dụng hiệu ứng hologram xoay 3D
            com.vn.jet.mosco.utils.LevelBadgeEffectHelper.apply(vh.ivLevel, item.getUpgradeLevel());
            // Visibility sẽ được điều khiển bởi trạng thái owned/inventory
        } else {
            vh.ivLevel.setVisibility(View.GONE);
            com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(vh.ivLevel);
        }
    }

    /**
     * Bind trạng thái Album: Owned (rõ nét) vs Locked (grayscale + overlay).
     */
    private void bindAlbumState(@NonNull CardViewHolder vh, @NonNull CardDisplayItem item) {
        int cardBgColor;
        if (item.isOwned()) {
            // === ĐÃ SỞ HỮU ===
            vh.ivCardImage.setColorFilter(null);
            vh.ivCardImage.setAlpha(1.0f);
            if (vh.viewLockedOverlay != null) vh.viewLockedOverlay.setVisibility(View.GONE);
            if (vh.ivLockIcon != null) vh.ivLockIcon.setVisibility(View.GONE);
            if (vh.ivLevel != null) vh.ivLevel.setVisibility(View.GONE); // Luôn ẩn cấp thẻ trong Album
            cardBgColor = ContextCompat.getColor(mContext, R.color.mosco_card_bg_owned);
        } else {
            // === CHƯA SỞ HỮU ===
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0f);
            vh.ivCardImage.setColorFilter(new ColorMatrixColorFilter(matrix));
            vh.ivCardImage.setAlpha(0.2f); // Trả về 0.2f như cũ cho danh sách lưới
            if (vh.viewLockedOverlay != null) vh.viewLockedOverlay.setVisibility(View.VISIBLE);
            if (vh.ivLockIcon != null) vh.ivLockIcon.setVisibility(View.VISIBLE);
            if (vh.ivLevel != null) vh.ivLevel.setVisibility(View.GONE);
            cardBgColor = ContextCompat.getColor(mContext, R.color.mosco_card_bg_locked);
        }

        if (vh.cvCard != null) {
            vh.cvCard.setCardBackgroundColor(cardBgColor);
        }

        // Ẩn OVR badge trong Album mode
        if (vh.tvOvr != null) vh.tvOvr.setVisibility(View.GONE);

        // Name Tag luôn hiển thị và nằm trên cùng
        if (vh.tvNameTag != null) {
            vh.tvNameTag.setVisibility(View.VISIBLE);
            vh.tvNameTag.bringToFront();
        }
    }

    /**
     * Bind trạng thái Inventory: Multi-select, Disabled/Busy states.
     */
    private void bindInventoryState(@NonNull CardViewHolder vh, @NonNull CardDisplayItem item, int position) {
        int cardBgColor = ContextCompat.getColor(mContext, R.color.mosco_card_bg_owned);

        // Hiện skeleton trước, ẩn level badge → GlideListener sẽ hiện lại
        if (vh.layoutSkeleton != null) {
            vh.layoutSkeleton.setVisibility(View.VISIBLE);
            if (vh.ivLevel != null) vh.ivLevel.setVisibility(View.INVISIBLE);
        }

        // Level badge visibility
        if (vh.ivLevel != null && item.getUpgradeLevel() > 0) {
            vh.ivLevel.setVisibility(View.VISIBLE);
        }

        // Ẩn OVR (hiện tại không dùng trong Grid)
        if (vh.tvOvr != null) vh.tvOvr.setVisibility(View.GONE);

        // Multi-select overlay & Disabled states
        boolean isSelected = selectedIds.contains(item.getId());
        boolean isDisabled = disabledIds.contains(item.getId())
                || (item.getMember() != null
                    && disabledMembers.contains(item.getMember().trim().toLowerCase())
                    && !isSelected);

        if (isDisabled) {
            applyGrayscale(vh.ivCardImage, true);
            if (vh.viewDisabledOverlay != null) vh.viewDisabledOverlay.setVisibility(View.VISIBLE);
            vh.itemView.setAlpha(0.6f);

            if (vh.tvBusyStatus != null) {
                if (disabledIds.contains(item.getId())) {
                    String status = item.getStatus();
                    String loc = "Mission";
                    if (status != null && status.startsWith("BUSY_AFK_")) {
                        String mapIdStr = status.substring("BUSY_AFK_".length());
                        loc = getMapLocation(mapIdStr);
                    }
                    vh.tvBusyStatus.setText(mContext.getString(R.string.stage_busy_msg_format, loc));
                    vh.tvBusyStatus.setVisibility(View.VISIBLE);
                } else {
                    vh.tvBusyStatus.setText(R.string.stage_busy_member_format);
                    vh.tvBusyStatus.setVisibility(View.VISIBLE);
                }
            }
        } else {
            applyGrayscale(vh.ivCardImage, false);
            if (vh.viewDisabledOverlay != null) vh.viewDisabledOverlay.setVisibility(View.GONE);
            if (vh.tvBusyStatus != null) vh.tvBusyStatus.setVisibility(View.GONE);
            vh.itemView.setAlpha(1.0f);
        }

        if (vh.viewSelectedOverlay != null) {
            vh.viewSelectedOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        }

        if (vh.cvCard != null) {
            vh.cvCard.setCardBackgroundColor(cardBgColor);
        }

        // Pin Indicator
        if (vh.ivPin != null) {
            boolean isPinned = PinManager.isPinned(mContext, String.valueOf(item.getId()));
            vh.ivPin.setVisibility(isPinned ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Bind click — Inventory hỗ trợ multi-select, Album chỉ click đơn.
     */
    private void bindClickListener(@NonNull CardViewHolder vh, @NonNull CardDisplayItem item, int position) {
        vh.itemView.setOnClickListener(v -> {
            if (displayMode == DisplayMode.INVENTORY) {
                // Kiểm tra disabled
                boolean isDisabled = disabledIds.contains(item.getId())
                        || (item.getMember() != null
                            && disabledMembers.contains(item.getMember().trim().toLowerCase())
                            && !selectedIds.contains(item.getId()));
                if (isDisabled) return;

                if (isMultiSelectMode) {
                    boolean currentlySelected = selectedIds.contains(item.getId());
                    if (currentlySelected) {
                        selectedIds.remove(item.getId());
                    } else {
                        selectedIds.add(item.getId());
                    }
                    notifyItemChanged(position);
                    if (selectListener != null) {
                        selectListener.onCardSelected(item, !currentlySelected);
                    }
                    return;
                }
            }

            if (clickListener != null) clickListener.onCardClick(item);
        });
    }

    // =============== HELPERS ===============

    private void applyGrayscale(ImageView iv, boolean enabled) {
        if (enabled) {
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0);
            iv.setColorFilter(new ColorMatrixColorFilter(matrix));
        } else {
            iv.clearColorFilter();
        }
    }

    private String getMapLocation(String mapId) {
        switch (mapId) {
            case "1": return mContext.getString(R.string.stage_map_1_name);
            case "2": return mContext.getString(R.string.stage_map_2_name);
            case "3": return mContext.getString(R.string.stage_map_3_name);
            case "4": return mContext.getString(R.string.stage_map_4_name);
            default: return "Mission";
        }
    }

    @Override
    public int getItemCount() {
        if (isLoading) return SKELETON_COUNT;
        int count = displayItems == null ? 0 : displayItems.size();
        if (isPagingLoading) count += PAGING_SKELETON_COUNT;
        return count;
    }

    // =============== VIEW HOLDERS ===============

    /**
     * ViewHolder thống nhất — tìm View theo ID chung nhất giữa 2 layout.
     * Các view không tồn tại trong layout sẽ tự null (safe).
     */
    static class CardViewHolder extends RecyclerView.ViewHolder {
        // Chung cho cả 2 mode
        ImageView ivCardImage;
        ImageView ivLevel;
        TextView tvOvr;
        TextView tvNameTag;
        View layoutSkeleton;
        CardView cvCard;
        ImageView ivPin;

        // Chỉ có trong ALBUM mode
        ImageView ivLockIcon;
        View viewLockedOverlay;

        // Chỉ có trong INVENTORY mode
        View viewSelectedOverlay;
        View viewDisabledOverlay;
        TextView tvBusyStatus;

        CardViewHolder(@NonNull View itemView, DisplayMode mode) {
            super(itemView);
            ivCardImage = itemView.findViewById(R.id.card_iv_image);
            ivLevel = itemView.findViewById(R.id.card_iv_level);
            tvOvr = itemView.findViewById(R.id.card_tv_ovr);
            tvNameTag = itemView.findViewById(R.id.tv_card_name);
            ivPin = itemView.findViewById(R.id.card_iv_pin);

            if (mode == DisplayMode.ALBUM) {
                cvCard = itemView.findViewById(R.id.cv_book_card);
                layoutSkeleton = itemView.findViewById(R.id.layout_skeleton);
                ivLockIcon = itemView.findViewById(R.id.iv_lock_icon);
                viewLockedOverlay = itemView.findViewById(R.id.view_locked_overlay);
            } else {
                // Inventory layout: MaterialCardView nằm bên trong ConstraintLayout root
                View cardContainer = itemView.findViewById(R.id.cv_card_container);
                cvCard = cardContainer instanceof CardView ? (CardView) cardContainer : null;
                layoutSkeleton = itemView.findViewById(R.id.layout_card_skeleton);
                viewSelectedOverlay = itemView.findViewById(R.id.view_selected_overlay);
                viewDisabledOverlay = itemView.findViewById(R.id.view_disabled_overlay);
                tvBusyStatus = itemView.findViewById(R.id.tv_busy_status);
            }
        }
    }

    static class SkeletonViewHolder extends RecyclerView.ViewHolder {
        SkeletonViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

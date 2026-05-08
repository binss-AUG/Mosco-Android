package com.vn.jet.mosco.adapter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.SplashActivity;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.utils.CardAssetManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 👑 BaseInventoryAdapter (V5 - Local First)
 * Chiến thuật "Local First": Ảnh bản 2x lưu sẵn ở máy → scale down hiển thị Grid.
 * Không cần gọi mạng → tốc độ tải 0ms, cuộn mượt như bơ.
 */
public class BaseInventoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Objet item);
    }

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_LOADING = 1;
    private static final int VIEW_TYPE_SKELETON = 2;

    // Số ảnh nạp tức thì ban đầu (ảnh local nên load rất nhanh)
    private static final int INSTANT_LOAD_COUNT = 100;
    // Số ảnh gối đầu (buffer) mỗi lần cuộn
    private static final int BUFFER_SIZE = 50;
    // Ngưỡng kích hoạt nạp thêm
    private static final int LOAD_THRESHOLD = 10;

    // Kích thước Grid (scale down từ bản 2x) — tiết kiệm RAM
    private static final int GRID_WIDTH = 150;
    private static final int GRID_HEIGHT = 231;

    private final List<Objet> allObjets;
    protected final List<Objet> displayObjets;
    private final OnItemClickListener listener;
    private final Context mContext;
    private boolean isLoadingMore = false;
    private boolean isLoading = false; // "Quiet Luxury" Skeleton State
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // =============== SUPPORT FILTER & SORT ===============
    public void setLoading(boolean loading) {
        this.isLoading = loading;
        notifyDataSetChanged();
    }

    public void updateData(List<Objet> newAllObjets) {
        this.allObjets.clear();
        this.allObjets.addAll(newAllObjets);
        
        this.currentFilteredList = new ArrayList<>(newAllObjets);
        this.currentLimit = 60;
        this.isPagingLoading = false;
        
        this.displayObjets.clear();
        this.isLoadingMore = false;
        this.isLoading = false; // Tắt skeleton khi dữ liệu về
        
        int maxLimit = Math.min(currentLimit, currentFilteredList.size());
        if (maxLimit > 0) {
            this.displayObjets.addAll(currentFilteredList.subList(0, maxLimit));
        }
        
        notifyDataSetChanged();
    }
    // =======================================================

    // =============== SUPPORT MULTI-SELECT ===============
    private boolean isMultiSelectMode = false;
    private java.util.Set<Long> selectedIds = new java.util.HashSet<>();
    private java.util.Set<Long> disabledIds = new java.util.HashSet<>();
    private java.util.Set<String> disabledMembers = new java.util.HashSet<>();
    private OnItemSelectListener selectListener;

    public interface OnItemSelectListener {
        void onItemSelected(Objet item, boolean selected);
    }

    public void setMultiSelectMode(boolean enabled, OnItemSelectListener listener) {
        this.isMultiSelectMode = enabled;
        this.selectListener = listener;
    }

    public void setSelectedIds(java.util.Set<Long> ids) {
        this.selectedIds = ids != null ? ids : new java.util.HashSet<>();
        notifyDataSetChanged();
    }

    public void setDisabledStates(java.util.Set<Long> ids, java.util.Set<String> members) {
        this.disabledIds = ids != null ? ids : new java.util.HashSet<>();
        this.disabledMembers = members != null ? members : new java.util.HashSet<>();
        notifyDataSetChanged();
    }
    // =======================================================

    private List<Objet> currentFilteredList;
    private int currentLimit = 60;
    private boolean isPagingLoading = false;

    public BaseInventoryAdapter(List<Objet> allObjets, RecyclerView rv, OnItemClickListener listener) {
        this.allObjets = new ArrayList<>(allObjets);
        this.currentFilteredList = new ArrayList<>(allObjets);
        this.displayObjets = new ArrayList<>();
        this.listener = listener;
        this.mContext = rv.getContext();

        // Khởi tạo hiển thị 60 phần tử đầu tiên
        currentLimit = 60;
        int maxLimit = Math.min(currentLimit, currentFilteredList.size());
        if (maxLimit > 0) {
            displayObjets.addAll(currentFilteredList.subList(0, maxLimit));
        }

        // Tối ưu RecyclerView
        rv.setHasFixedSize(true);
        // Tăng Cache Size để tránh GC giật lag khi cuộn nhanh với list 9000 item
        rv.setItemViewCacheSize(20);
        rv.setDrawingCacheEnabled(true);
        rv.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        // Đăng ký tự động phân trang 60 phần tử/lần
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && !isPagingLoading && !isLoading) {
                    GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null && layoutManager.findLastVisibleItemPosition() >= getItemCount() - 15) {
                        loadNextPage(recyclerView);
                    }
                }
            }
        });
    }

    private void loadNextPage(RecyclerView rv) {
        if (currentLimit >= currentFilteredList.size() || isPagingLoading || isLoading) return;

        isPagingLoading = true;
        
        // Hiện 3 skeleton chờ
        rv.post(() -> notifyItemRangeInserted(displayObjets.size(), 3));

        // Khựng nhẹ (300ms) để tải 60 thẻ tiếp theo tạo gia tốc lướt mượt
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            currentLimit += 60;
            int maxLimit = Math.min(currentLimit, currentFilteredList.size());
            List<Objet> nextChunk = new ArrayList<>(currentFilteredList.subList(displayObjets.size(), maxLimit));
            
            isPagingLoading = false;
            // Xóa 3 skeleton chờ
            notifyItemRangeRemoved(displayObjets.size(), 3);
            
            // Chèn dữ liệu thực
            int insertPos = displayObjets.size();
            displayObjets.addAll(nextChunk);
            notifyItemRangeInserted(insertPos, nextChunk.size());

            // 🚀 PROGRESSIVE LOADING: Prefetch ảnh cho trang tiếp theo (60-120 item tiếp)
            preloadNextPageImages();
        }, 300);
    }

    /**
     * Tải ngầm (Prefetch) ảnh cho trang tiếp theo để lướt không bị khựng skeleton.
     */
    private void preloadNextPageImages() {
        if (currentFilteredList == null || displayObjets == null) return;
        
        int startPos = displayObjets.size();
        // [PERFORMANCE TEST] Thả xích: Load toàn bộ danh sách thay vì giới hạn 60 item
        int endPos = currentFilteredList.size();
        
        if (startPos >= endPos) return;

        for (int i = startPos; i < endPos; i++) {
            Objet item = currentFilteredList.get(i);
            if (item == null) continue;

            // Kiểm tra file local trước
            File localFile = CardAssetManager.getLocalFile(mContext, item.getImageUrl());
            if (localFile != null && localFile.exists()) {
                Glide.with(mContext)
                        .load(localFile)
                        .override(GRID_WIDTH, GRID_HEIGHT)
                        .priority(Priority.LOW) // Prefetch dùng độ ưu tiên thấp
                        .preload();
            } else {
                String fallbackUrl = CardAssetManager.convertToVariant(item.getImageUrl(), "1x");
                Glide.with(mContext)
                        .load(fallbackUrl)
                        .override(GRID_WIDTH, GRID_HEIGHT)
                        .priority(Priority.LOW)
                        .preload();
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (isLoading) return VIEW_TYPE_SKELETON;
        if (displayObjets != null && position >= displayObjets.size()) return VIEW_TYPE_SKELETON;
        return VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SKELETON) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_objet_skeleton, parent, false);
            return new SkeletonViewHolder(v);
        } else if (viewType == VIEW_TYPE_LOADING) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_loading_footer, parent, false);
            return new LoadingViewHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inventory_card, parent, false);
            return new ItemViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            Objet item = displayObjets.get(position);
            if (item == null) return;

            // Bind Card Name (Instant Metadata: Artist [Prefix]No format)
            if (itemHolder.tvNameTag != null) {
                String classPrefix = "";
                if (item.getTypeKey() != null && !item.getTypeKey().isEmpty()) {
                    classPrefix = item.getTypeKey().substring(0, 1).toUpperCase();
                }
                String formattedName = (item.getMember() != null ? item.getMember() : "") + " " + classPrefix + (item.getCollectionNo() != null ? item.getCollectionNo() : "");
                itemHolder.tvNameTag.setText(formattedName);
            }

            if (itemHolder.layoutSkeleton != null) {
                itemHolder.layoutSkeleton.setVisibility(View.VISIBLE);
                if (itemHolder.ivLevel != null) itemHolder.ivLevel.setVisibility(View.INVISIBLE);
            }

            // Load grade image into ivLevel regardless, but control visibility later
            if (itemHolder.ivLevel != null && item.getCardLevel() > 0) {
                String assetPath = "file:///android_asset/grade/" + item.getCardLevel() + ".png";
                Glide.with(mContext).load(assetPath).into(itemHolder.ivLevel);
            } else if (itemHolder.ivLevel != null) {
                itemHolder.ivLevel.setVisibility(View.GONE);
            }

            // 🚀 LOCAL FIRST: Tìm file ảnh 2x trong bộ nhớ máy
            File localFile = CardAssetManager.getLocalFile(mContext, item.getImageUrl());

            com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> glideListener = new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                @Override
                public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                    // KHÔNG ẨN SKELETON KHI LỖI để giữ trải nghiệm mượt mà, tránh hiện placeholder xấu
                    // if (itemHolder.layoutSkeleton != null) itemHolder.layoutSkeleton.setVisibility(View.GONE);
                    if (itemHolder.ivLevel != null && item.getCardLevel() > 0) itemHolder.ivLevel.setVisibility(View.VISIBLE);
                    return false;
                }

                @Override
                public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                    if (itemHolder.layoutSkeleton != null) itemHolder.layoutSkeleton.setVisibility(View.GONE);
                    if (itemHolder.ivLevel != null && item.getCardLevel() > 0) itemHolder.ivLevel.setVisibility(View.VISIBLE);
                    return false;
                }
            };

            if (localFile != null && localFile.exists()) {
                // ✅ Ảnh có sẵn ở máy → Nạp từ file local, scale down cho Grid
                Glide.with(mContext)
                        .load(localFile)
                        .override(GRID_WIDTH, GRID_HEIGHT) // Scale down 2x → kích thước grid nhỏ
                        .diskCacheStrategy(DiskCacheStrategy.NONE) // Không cache lại (đã là file local)
                        .skipMemoryCache(false) // Vẫn giữ trong RAM cho lần cuộn lại
                        .priority(Priority.IMMEDIATE) // 🚀 VIEWPORT PRIORITY: Tải ngay lập tức
                        .dontAnimate() // Hiện ngay tức thì
                        .listener(glideListener)
                        .into(itemHolder.ivObjet);
            } else {
                // ⚡ Fallback: Ảnh chưa tải → Gọi URL bản 1x từ Cloudflare (nhẹ nhất)
                String fallbackUrl = CardAssetManager.convertToVariant(item.getImageUrl(), "1x");
                Glide.with(mContext)
                        .load(fallbackUrl)
                        .override(GRID_WIDTH, GRID_HEIGHT)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .priority(Priority.IMMEDIATE) // 🚀 VIEWPORT PRIORITY
                        .transition(DrawableTransitionOptions.withCrossFade(200))
                        .listener(glideListener)
                        .into(itemHolder.ivObjet);
            }


            // BIND OVR TEXT
            if (itemHolder.tvOvr != null) {
                itemHolder.tvOvr.setText(String.valueOf(item.getOvr()));
                itemHolder.tvOvr.setVisibility(View.GONE);
            }

            // 🔥 BIND MULTI-SELECT OVERLAY & DISABLED STATE
            boolean isSelected = selectedIds.contains(item.getId());
            boolean isDisabled = disabledIds.contains(item.getId()) || 
                               (item.getMember() != null && disabledMembers.contains(item.getMember().trim().toLowerCase()) && !isSelected);

            if (isDisabled) {
                applyGrayscale(itemHolder.ivObjet, true);
                if (itemHolder.viewDisabledOverlay != null) itemHolder.viewDisabledOverlay.setVisibility(View.VISIBLE);
                itemHolder.itemView.setAlpha(0.6f);

                if (itemHolder.tvBusyStatus != null) {
                    if (disabledIds.contains(item.getId())) {
                        String status = item.getStatus();
                        String loc = "Mission";
                        if (status != null && status.startsWith("BUSY_AFK_")) {
                            String mapIdStr = status.substring("BUSY_AFK_".length());
                            loc = getMapLocation(mapIdStr);
                        }
                        itemHolder.tvBusyStatus.setText(mContext.getString(R.string.stage_busy_msg_format, loc));
                        itemHolder.tvBusyStatus.setVisibility(View.VISIBLE);
                    } else if (item.getMember() != null && disabledMembers.contains(item.getMember().trim().toLowerCase()) && !isSelected) {
                        itemHolder.tvBusyStatus.setText(R.string.stage_busy_member_format);
                        itemHolder.tvBusyStatus.setVisibility(View.VISIBLE);
                    } else {
                        itemHolder.tvBusyStatus.setVisibility(View.GONE);
                    }
                }
            } else {
                applyGrayscale(itemHolder.ivObjet, false);
                if (itemHolder.viewDisabledOverlay != null) itemHolder.viewDisabledOverlay.setVisibility(View.GONE);
                if (itemHolder.tvBusyStatus != null) itemHolder.tvBusyStatus.setVisibility(View.GONE);
                itemHolder.itemView.setAlpha(1.0f);
            }

            if (itemHolder.viewOverlay != null) {
                itemHolder.viewOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            }

            // Sync color to Album style (Quiet Luxury Deep Blue)
            if (itemHolder.itemView instanceof androidx.cardview.widget.CardView) {
                ((androidx.cardview.widget.CardView) itemHolder.itemView).setCardBackgroundColor(0xFF1A1C29);
            }

            itemHolder.itemView.setOnClickListener(v -> {
                if (isDisabled) return; // Prevent click on disabled items

                if (isMultiSelectMode) {
                    boolean currentlySelected = selectedIds.contains(item.getId());
                    if (currentlySelected) {
                        selectedIds.remove(item.getId());
                    } else {
                        selectedIds.add(item.getId());
                    }
                    notifyItemChanged(position);
                    if (selectListener != null) {
                        selectListener.onItemSelected(item, !currentlySelected);
                    }
                } else {
                    if (listener != null) listener.onItemClick(item);
                }
            });
        }
    }

    private void applyGrayscale(ImageView iv, boolean enabled) {
        if (enabled) {
            android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
            matrix.setSaturation(0);
            iv.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
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
        if (isLoading) return 12; // Hiện 12 ô skeleton (Grid 3 cột)
        int count = displayObjets == null ? 0 : displayObjets.size();
        if (isPagingLoading) count += 3; // Thêm 3 skeleton cuối grid
        return count;
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView ivObjet;
        android.widget.TextView tvOvr;
        ImageView ivLevel;
        View viewOverlay;
        View viewDisabledOverlay;
        android.widget.TextView tvBusyStatus;
        View layoutSkeleton;
        android.widget.TextView tvNameTag;
        
        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            ivObjet = itemView.findViewById(R.id.card_iv_image);
            tvOvr = itemView.findViewById(R.id.card_tv_ovr);
            ivLevel = itemView.findViewById(R.id.card_iv_level);
            viewOverlay = itemView.findViewById(R.id.view_selected_overlay);
            viewDisabledOverlay = itemView.findViewById(R.id.view_disabled_overlay);
            tvBusyStatus = itemView.findViewById(R.id.tv_busy_status);
            layoutSkeleton = itemView.findViewById(R.id.layout_card_skeleton);
            tvNameTag = itemView.findViewById(R.id.tv_card_name);
        }
    }

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class SkeletonViewHolder extends RecyclerView.ViewHolder {
        public SkeletonViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

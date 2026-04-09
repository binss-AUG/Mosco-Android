package com.vn.jet.mosco.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vn.jet.mosco.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter hiển thị danh sách Synergy Buff dạng hàng dọc.
 * Mỗi hàng gồm: Tên buff | Chỉ số effect | Sao đánh giá (1-3).
 */
public class SynergyDashboardAdapter extends RecyclerView.Adapter<SynergyDashboardAdapter.ViewHolder> {

    // Số sao tối đa có thể hiển thị
    private static final int MAX_STARS = 3;
    // Pattern trích xuất con số trong ngoặc, VD: "EVOLution (3)" -> 3
    private static final Pattern COUNT_PATTERN = Pattern.compile("\\((\\d+)\\)");

    private List<String> activeSynergies = new ArrayList<>();
    private Map<String, String> buffSummary = new HashMap<>();
    private final OnSynergyInteractionListener listener;
    private String selectedSynergy = null;

    public interface OnSynergyInteractionListener {
        void onSynergyHold(String synergyName);
        void onSynergyRelease();
    }

    public SynergyDashboardAdapter(OnSynergyInteractionListener listener) {
        this.listener = listener;
    }

    /**
     * Cập nhật danh sách synergy và bản đồ chỉ số buff từ BattleResponse.
     */
    public void submitList(List<String> list, Map<String, String> buffMap) {
        this.activeSynergies = (list != null) ? list : new ArrayList<>();
        this.buffSummary = (buffMap != null) ? buffMap : new HashMap<>();

        // Reset selection nếu synergy đã chọn không còn trong danh sách mới
        if (selectedSynergy != null && !this.activeSynergies.contains(selectedSynergy)) {
            selectedSynergy = null;
            listener.onSynergyRelease();
        }

        notifyDataSetChanged();
    }

    /**
     * Overload để tương thích ngược với code cũ gọi submitList(List).
     */
    public void submitList(List<String> list) {
        submitList(list, this.buffSummary);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_synergy_badge, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String synergy = activeSynergies.get(position);

        // Tách tên sạch và số lượng từ chuỗi synergy
        String cleanName = synergy.replaceAll("\\s*\\(\\d+\\)$", "").trim();
        int count = extractCount(synergy);
        int starCount = calculateStars(count);

        // Gán tên buff
        holder.tvName.setText(cleanName);

        // Gán chỉ số buff từ buffSummary map
        String buffValue = findBuffValue(cleanName);
        if (buffValue != null && !buffValue.isEmpty()) {
            holder.tvBuffValue.setVisibility(View.VISIBLE);
            holder.tvBuffValue.setText(buffValue);
        } else {
            holder.tvBuffValue.setVisibility(View.GONE);
        }

        // Vẽ sao đánh giá
        renderStars(holder.llStarContainer, starCount);

        // Hiệu ứng chọn (highlight/dim)
        if (synergy.equals(selectedSynergy)) {
            holder.itemView.setAlpha(1.0f);
        } else {
            holder.itemView.setAlpha(selectedSynergy == null ? 1.0f : 0.5f);
        }

        // Sự kiện click: Toggle chọn synergy
        holder.itemView.setOnClickListener(v -> {
            if (synergy.equals(selectedSynergy)) {
                selectedSynergy = null;
                listener.onSynergyRelease();
            } else {
                selectedSynergy = synergy;
                listener.onSynergyHold(synergy);
            }
            notifyDataSetChanged();
        });
    }

    @Override
    public int getItemCount() {
        return activeSynergies.size();
    }

    /**
     * Trích xuất số lượng thẻ kích hoạt từ chuỗi synergy.
     * VD: "AAA (2)" -> 2, "SUN (4)" -> 4
     */
    private int extractCount(String synergy) {
        Matcher matcher = COUNT_PATTERN.matcher(synergy);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Thuật toán quy đổi số lượng thẻ kích hoạt thành số sao.
     * <= 2 con = 1 sao | <= 4 con = 2 sao | >= 5 con = 3 sao
     */
    private int calculateStars(int count) {
        if (count <= 0) return 0;
        if (count <= 2) return 1;
        if (count <= 4) return 2;
        return MAX_STARS;
    }

    /**
     * Tìm giá trị buff tương ứng trong buffSummary map.
     * So khớp mềm: Kiểm tra các key chứa tên synergy.
     */
    private String findBuffValue(String cleanName) {
        if (buffSummary == null || cleanName == null) return null;

        // Bỏ ngoặc đơn lấy tên chuẩn (vd "AAA (2)" -> "AAA")
        String finalName = cleanName.replaceAll("\\s*\\(\\d+\\)$", "").trim();

        // Exact match trước
        if (buffSummary.containsKey(finalName)) {
            return buffSummary.get(finalName);
        }
        
        // Exact match nguyên gốc
        if (buffSummary.containsKey(cleanName)) {
            return buffSummary.get(cleanName);
        }

        // Fuzzy match: Tìm key chứa tên synergy (không phân biệt chữ hoa/thường)
        String upperName = finalName.toUpperCase();
        for (Map.Entry<String, String> entry : buffSummary.entrySet()) {
            if (entry.getKey().toUpperCase().contains(upperName) ||
                upperName.contains(entry.getKey().toUpperCase())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Vẽ sao vào container. Sao vàng = kích hoạt, sao xám = chưa đạt.
     */
    private void renderStars(LinearLayout container, int activeStars) {
        container.removeAllViews();
        for (int i = 0; i < MAX_STARS; i++) {
            ImageView star = new ImageView(container.getContext());
            int size = (int) (16 * container.getContext().getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginEnd((int) (2 * container.getContext().getResources().getDisplayMetrics().density));
            star.setLayoutParams(params);

            if (i < activeStars) {
                star.setImageResource(R.drawable.ic_star);
            } else {
                star.setImageResource(R.drawable.ic_star_empty);
            }
            container.addView(star);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvBuffValue;
        LinearLayout llStarContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_synergy_badge_name);
            tvBuffValue = itemView.findViewById(R.id.tv_synergy_buff_value);
            llStarContainer = itemView.findViewById(R.id.ll_star_container);
        }
    }
}

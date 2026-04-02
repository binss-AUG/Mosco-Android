package com.vn.jet.mosco.fragment;

import android.app.Dialog;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.model.UpgradeCard;
import com.vn.jet.mosco.utils.UpgradeAlgorithm;

import java.util.ArrayList;
import java.util.List;

public class UpgradeBottomSheet extends BottomSheetDialogFragment {

    private OnUpgradeCardSelectedListener listener;
    private List<UpgradeCard> cardList;

    private boolean isMultiSelect = false;
    private List<UpgradeCard> selectedMaterials = new ArrayList<>();
    private UpgradeCard mainCard;
    private UpgradeAlgorithm upgradeAlgorithm;
    private androidx.appcompat.widget.AppCompatButton btnConfirm;

    public interface OnUpgradeCardSelectedListener {
        void onUpgradeCardSelected(UpgradeCard card);
        void onMaterialsSelected(List<UpgradeCard> materials);
    }

    public void setOnUpgradeCardSelectedListener(OnUpgradeCardSelectedListener listener) {
        this.listener = listener;
    }

    public void setCardList(List<UpgradeCard> cardList) {
        this.cardList = cardList != null ? new ArrayList<>(cardList) : new ArrayList<>();
    }

    public void setupMultiSelectMode(UpgradeCard mainCard, UpgradeAlgorithm algorithm, List<UpgradeCard> preSelected) {
        this.isMultiSelect = true;
        this.mainCard = mainCard;
        this.upgradeAlgorithm = algorithm;
        if (preSelected != null) {
            for (UpgradeCard c : preSelected) {
                if (c != null) this.selectedMaterials.add(c);
            }
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.setLayoutParams(layoutParams);

                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_inventory_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView ivBack = view.findViewById(R.id.iv_back);
        RecyclerView rvInventory = view.findViewById(R.id.rv_inventory);
        LinearLayout layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        TextView tvTitle = view.findViewById(R.id.tv_title);
        btnConfirm = view.findViewById(R.id.btn_confirm);

        ivBack.setOnClickListener(v -> dismiss());

        rvInventory.setLayoutManager(new GridLayoutManager(getContext(), 3));

        if (cardList == null || cardList.isEmpty()) {
            rvInventory.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvInventory.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            UpgradeCardAdapter adapter = new UpgradeCardAdapter(cardList);
            rvInventory.setAdapter(adapter);
        }

        if (isMultiSelect) {
            btnConfirm.setVisibility(View.VISIBLE);
            tvTitle.setText("Select Materials");
            btnConfirm.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMaterialsSelected(selectedMaterials);
                }
                dismiss();
            });
            updateConfirmButtonText();
        } else {
            btnConfirm.setVisibility(View.GONE);
            tvTitle.setText("Select a Card");
        }
    }

    private double calculateCurrentProgress() {
        if (!isMultiSelect || mainCard == null || upgradeAlgorithm == null) return 0.0;
        List<UpgradeAlgorithm.Card> algoMaterials = new ArrayList<>();
        for (UpgradeCard mc : selectedMaterials) {
            UpgradeAlgorithm.Card c = new UpgradeAlgorithm.Card();
            c.id = mc.getId();
            c.typeKey = mc.getTypeKey();
            c.level = mc.getLevel();
            c.ovr = mc.getOvr();
            algoMaterials.add(c);
        }

        UpgradeAlgorithm.Card target = new UpgradeAlgorithm.Card();
        target.id = mainCard.getId();
        target.typeKey = mainCard.getTypeKey();
        target.level = mainCard.getLevel();
        target.ovr = mainCard.getOvr();

        return upgradeAlgorithm.calculateFillPercent(target, algoMaterials);
    }

    private void updateConfirmButtonText() {
        if (btnConfirm == null) return;
        double percent = calculateCurrentProgress();
        if (selectedMaterials.isEmpty()) {
            btnConfirm.setText("Confirm");
        } else {
            btnConfirm.setText(String.format("Confirm (%.1f%%)", percent));
        }
    }

    private class UpgradeCardAdapter extends RecyclerView.Adapter<UpgradeCardAdapter.ViewHolder> {
        private final List<UpgradeCard> cards;

        public UpgradeCardAdapter(List<UpgradeCard> cards) {
            this.cards = cards;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inventory_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            UpgradeCard card = cards.get(position);

            Glide.with(holder.itemView.getContext())
                    .load(card.getImageUrl())
                    .into(holder.ivObjet);

            holder.tvOvr.setText(String.valueOf(card.getOvr()));
            holder.tvOvr.setVisibility(View.VISIBLE);

            if (card.getLevel() > 0) {
                String assetPath = "file:///android_asset/grade/" + card.getLevel() + ".png";
                Glide.with(holder.itemView.getContext()).load(assetPath).into(holder.ivLevel);
                holder.ivLevel.setVisibility(View.VISIBLE);
            } else {
                holder.ivLevel.setVisibility(View.GONE);
            }

            if (isMultiSelect) {
                boolean isSelected = false;
                for (UpgradeCard sc : selectedMaterials) {
                    if (sc.getId().equals(card.getId())) {
                        isSelected = true;
                        break;
                    }
                }
                holder.viewOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            } else {
                holder.viewOverlay.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (isMultiSelect) {
                    boolean currentlySelected = false;
                    for (UpgradeCard sc : selectedMaterials) {
                        if (sc.getId().equals(card.getId())) {
                            currentlySelected = true;
                            break;
                        }
                    }

                    if (currentlySelected) {
                        selectedMaterials.removeIf(sc -> sc.getId().equals(card.getId()));
                        notifyItemChanged(position);
                        updateConfirmButtonText();
                    } else {
                        // "Muốn thay thế material: BẮT BUỘC phải gỡ object hiện tại trước..." 
                        if (selectedMaterials.size() >= 5) {
                            Toast.makeText(getContext(), "Vui lòng gỡ thẻ cũ trước khi thêm mới (Tối đa 5 thẻ)!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        double currentProgress = calculateCurrentProgress();
                        if (currentProgress >= 100.0) {
                            Toast.makeText(getContext(), "Tỷ lệ đã đủ 100%, không cần thêm thẻ!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        if (mainCard != null && mainCard.getId().equals(card.getId())) {
                            Toast.makeText(getContext(), "Không thể rèn chính nó!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Hành vi giống stack (push lên đầu)
                        selectedMaterials.add(0, card); 
                        notifyItemChanged(position);
                        updateConfirmButtonText();
                    }
                } else {
                    if (listener != null) {
                        listener.onUpgradeCardSelected(card);
                    }
                    dismiss();
                }
            });
        }

        @Override
        public int getItemCount() {
            return cards.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivObjet;
            TextView tvOvr;
            ImageView ivLevel;
            View viewOverlay;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivObjet = itemView.findViewById(R.id.card_iv_image);
                tvOvr = itemView.findViewById(R.id.card_tv_ovr);
                ivLevel = itemView.findViewById(R.id.card_iv_level);
                viewOverlay = itemView.findViewById(R.id.view_selected_overlay);
            }
        }
    }
}

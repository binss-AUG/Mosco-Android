package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vn.jet.mosco.R;

import java.util.ArrayList;
import java.util.List;

/**
 * ShopFragment — "The Galactic Interface" shop screen.
 * <p>
 * Displays a glassmorphism currency header, horizontal filter chips,
 * and a 2‑column product grid. All styling follows the Mosco Design System;
 * NO hardcoded colour values are used in this class.
 */
public class ShopFragment extends Fragment {

    private LinearLayout chipContainer;
    private RecyclerView rvShop;
    private final List<String> categories = List.of("All", "Skins", "Boosters", "Consumables", "Bundles");
    private int selectedChipIndex = 0;

    // ──────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shop, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupHeader(view);
        setupChips(view);
        setupRecyclerView(view);
    }

    // ──────────────────────────────────────────────────────────
    // Header (Back button + currency)
    // ──────────────────────────────────────────────────────────

    private void setupHeader(@NonNull View root) {
        // Back button — navigates back to CollectionFragment
        root.findViewById(R.id.btn_shop_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.frame_layout, new CollectionFragment())
                        .commit();
            }
        });

        // Currency values — would be populated from a ViewModel in production
        TextView tvCoins    = root.findViewById(R.id.tv_coins);
        TextView tvDiamonds = root.findViewById(R.id.tv_diamonds);
        tvCoins.setText("1,250");
        tvDiamonds.setText("80");
    }

    // ──────────────────────────────────────────────────────────
    // Category Chips
    // ──────────────────────────────────────────────────────────

    private void setupChips(@NonNull View root) {
        chipContainer = root.findViewById(R.id.ll_chip_container);

        // Wire click listeners for every chip
        for (int i = 0; i < chipContainer.getChildCount(); i++) {
            final int index = i;
            View chip = chipContainer.getChildAt(i);
            chip.setOnClickListener(v -> selectChip(index));
        }

        // Select the first chip by default
        selectChip(0);
    }

    /**
     * Highlights the selected chip and applies a tinted background
     * using the primary colour. Non-selected chips keep the default
     * FilterChipStyle appearance.
     */
    private void selectChip(int index) {
        selectedChipIndex = index;

        for (int i = 0; i < chipContainer.getChildCount(); i++) {
            View chip = chipContainer.getChildAt(i);
            if (chip instanceof TextView) {
                TextView tv = (TextView) chip;
                if (i == index) {
                    // Active: tinted surface + primary text
                    tv.setBackgroundResource(R.drawable.bg_shop_buy_btn);
                    tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.mosco_on_surface));
                } else {
                    // Inactive: default chip style
                    tv.setBackgroundResource(R.drawable.bg_filter_chip);
                    tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.mosco_on_surface_variant));
                }
            }
        }

        // TODO: Filter items by category when real data is wired up
        Toast.makeText(requireContext(),
                "Category: " + categories.get(index), Toast.LENGTH_SHORT).show();
    }

    // ──────────────────────────────────────────────────────────
    // RecyclerView — 2‑column product grid
    // ──────────────────────────────────────────────────────────

    private void setupRecyclerView(@NonNull View root) {
        rvShop = root.findViewById(R.id.rv_shop);
        rvShop.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvShop.setHasFixedSize(true);

        // Dummy items for UI testing
        List<ShopItem> dummyItems = new ArrayList<>();
        dummyItems.add(new ShopItem("Cosmic Cape",     "A shimmering cape woven from stardust.",   "350",  "EPIC"));
        dummyItems.add(new ShopItem("Nebula Orb",      "Grants a temporary shield in battle.",     "120",  "RARE"));
        dummyItems.add(new ShopItem("Void Fragment",    "Material for crafting legendary gear.",    "80",   "COMMON"));
        dummyItems.add(new ShopItem("Star Shard",       "Boosts XP gain by 25% for 1 hour.",       "200",  "RARE"));
        dummyItems.add(new ShopItem("Lunar Elixir",     "Restores full HP and MP instantly.",       "500",  "LEGENDARY"));
        dummyItems.add(new ShopItem("Plasma Coil",      "Used to enhance items and objets.",        "150",  "COMMON"));

        rvShop.setAdapter(new ShopAdapter(dummyItems));
    }

    // ──────────────────────────────────────────────────────────
    // Data model
    // ──────────────────────────────────────────────────────────

    private static class ShopItem {
        final String name;
        final String desc;
        final String price;
        final String rarity;

        ShopItem(String name, String desc, String price, String rarity) {
            this.name   = name;
            this.desc   = desc;
            this.price  = price;
            this.rarity = rarity;
        }
    }

    // ──────────────────────────────────────────────────────────
    // Adapter
    // ──────────────────────────────────────────────────────────

    private static class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.ViewHolder> {

        private final List<ShopItem> items;

        ShopAdapter(List<ShopItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_shop_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int position) {
            ShopItem item = items.get(position);

            h.tvName.setText(item.name);
            h.tvDesc.setText(item.desc);
            h.tvPrice.setText(item.price);
            h.tvRarity.setText(item.rarity);

            // Placeholder image — swap for real asset URLs in production
            Glide.with(h.itemView.getContext())
                    .load("")
                    .placeholder(R.drawable.item_shop_demo)
                    .into(h.ivImage);

            // Card‑level click → would open a BottomSheetDialog with item details
            h.itemView.setOnClickListener(v ->
                    Toast.makeText(v.getContext(),
                            "Details: " + item.name, Toast.LENGTH_SHORT).show()
            );

            // Buy button click
            h.btnBuy.setOnClickListener(v ->
                    // TODO: Replace with BottomSheetDialog purchase confirmation
                    Toast.makeText(v.getContext(),
                            "Purchasing: " + item.name + " for " + item.price + " G",
                            Toast.LENGTH_SHORT).show()
            );
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        // ── ViewHolder ──────────────────────────────────────────

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivImage;
            final TextView  tvName;
            final TextView  tvDesc;
            final TextView  tvPrice;
            final TextView  tvRarity;
            final TextView  btnBuy;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivImage  = itemView.findViewById(R.id.iv_shop_item_image);
                tvName   = itemView.findViewById(R.id.tv_shop_item_name);
                tvDesc   = itemView.findViewById(R.id.tv_shop_item_desc);
                tvPrice  = itemView.findViewById(R.id.tv_shop_item_price);
                tvRarity = itemView.findViewById(R.id.tv_shop_item_rarity);
                btnBuy   = itemView.findViewById(R.id.btn_buy);
            }
        }
    }
}

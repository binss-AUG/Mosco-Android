package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.util.Log;
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
import com.vn.jet.mosco.model.ShopItem;
import com.vn.jet.mosco.model.UserStats;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.BuyRequest;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.NumberUtils;
import com.vn.jet.mosco.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ShopFragment extends Fragment {

    private LinearLayout chipContainer;
    private RecyclerView rvShop;
    private final List<String> categories = List.of("All", "OBJET", "PACK", "BUFF", "RESOURCE");
    private int selectedChipIndex = 0;
    
    private TextView tvCoins;
    private TextView tvDiamonds;
    
    private GameApiService apiService;
    private SessionManager sessionManager;
    private List<ShopItem> allShopItems = new ArrayList<>();

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

        sessionManager = new SessionManager(requireContext());
        apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);

        setupHeader(view);
        setupChips(view);
        setupRecyclerView(view);
        
        fetchUserResources();
        fetchShopItems();
    }

    private void setupHeader(@NonNull View root) {
        root.findViewById(R.id.btn_shop_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        tvCoins    = root.findViewById(R.id.tv_coins);
        tvDiamonds = root.findViewById(R.id.tv_diamonds);
        tvCoins.setText("0");
        tvDiamonds.setText("0");
    }

    private void fetchUserResources() {
        Long userId = sessionManager.getUserId();
        if (userId == null) return;
        
        apiService.getUserStats(userId).enqueue(new Callback<UserStats>() {
            @Override
            public void onResponse(Call<UserStats> call, Response<UserStats> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tvCoins.setText(NumberUtils.format(getContext(), response.body().getCoins()));
                    tvDiamonds.setText(NumberUtils.format(getContext(), response.body().getDiamonds()));
                }
            }
            @Override
            public void onFailure(Call<UserStats> call, Throwable t) {
                Log.e("ShopFragment", "Failed to fetch stats", t);
            }
        });
    }

    private void fetchShopItems() {
        apiService.getShopItems().enqueue(new Callback<List<ShopItem>>() {
            @Override
            public void onResponse(Call<List<ShopItem>> call, Response<List<ShopItem>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    allShopItems = response.body();
                    filterItems();
                }
            }
            @Override
            public void onFailure(Call<List<ShopItem>> call, Throwable t) {
                Toast.makeText(requireContext(), "Error loading shop", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupChips(@NonNull View root) {
        chipContainer = root.findViewById(R.id.ll_chip_container);

        for (int i = 0; i < chipContainer.getChildCount(); i++) {
            final int index = i;
            View chip = chipContainer.getChildAt(i);
            chip.setOnClickListener(v -> selectChip(index));
        }
        selectChip(0); // Select "All" by default
    }

    private void selectChip(int index) {
        selectedChipIndex = index;

        for (int i = 0; i < chipContainer.getChildCount(); i++) {
            View chip = chipContainer.getChildAt(i);
            if (chip instanceof TextView) {
                TextView tv = (TextView) chip;
                if (i == index) {
                    tv.setBackgroundResource(R.drawable.bg_shop_buy_btn);
                    tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.mosco_on_surface));
                } else {
                    tv.setBackgroundResource(R.drawable.bg_filter_chip);
                    tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.mosco_on_surface_variant));
                }
            }
        }
        filterItems();
    }
    
    private void filterItems() {
        if (allShopItems.isEmpty()) return;
        
        List<ShopItem> filteredList = new ArrayList<>();
        String selectedCat = categories.get(Math.min(selectedChipIndex, categories.size() - 1));
        
        for (ShopItem item : allShopItems) {
            if (selectedCat.equals("All") || item.getType().equalsIgnoreCase(selectedCat)) {
                filteredList.add(item);
            }
        }
        rvShop.setAdapter(new ShopAdapter(filteredList));
    }

    private void setupRecyclerView(@NonNull View root) {
        rvShop = root.findViewById(R.id.rv_shop);
        rvShop.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvShop.setHasFixedSize(true);
    }
    
    private void executePurchase(ShopItem item, int quantity) {
        Long userId = sessionManager.getUserId();
        if (userId == null) {
            Toast.makeText(requireContext(), "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        BuyRequest request = new BuyRequest(userId, item.getProductCode(), quantity);
        apiService.buyItem(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    showSuccessDialog(item, quantity);
                    fetchUserResources(); // Refresh coins and diamonds
                } else {
                    Toast.makeText(requireContext(), "Purchase failed: Not enough resources", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(requireContext(), "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSuccessDialog(ShopItem item, int quantity) {
        android.app.Dialog dialog = new android.app.Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_shop_success);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvMessage = dialog.findViewById(R.id.tv_success_message);
        com.google.android.material.button.MaterialButton btnInventory = dialog.findViewById(R.id.btn_go_inventory);
        com.google.android.material.button.MaterialButton btnUse = dialog.findViewById(R.id.btn_use_now);
        com.google.android.material.button.MaterialButton btnOk = dialog.findViewById(R.id.btn_ok);

        tvMessage.setText("You bought " + quantity + "x " + item.getName());

        String itemType = item.getType() != null ? item.getType().toUpperCase() : "";
        boolean isResource = itemType.equals("RESOURCE");

        if (isResource) {
            // Resource items: just show OK
            btnInventory.setVisibility(View.GONE);
            btnUse.setVisibility(View.GONE);
            btnOk.setVisibility(View.VISIBLE);
            btnOk.setOnClickListener(v -> dialog.dismiss());
        } else {
            // Pack/Objet/Buff: show Inventory + Use Now
            btnInventory.setVisibility(View.VISIBLE);
            btnUse.setVisibility(View.VISIBLE);
            btnOk.setVisibility(View.GONE);

            btnInventory.setOnClickListener(v -> {
                dialog.dismiss();
                if (getActivity() != null) {
                    CollectionFragment collectionFragment = new CollectionFragment();
                    android.os.Bundle args = new android.os.Bundle();
                    args.putInt("default_tab", 3);
                    collectionFragment.setArguments(args);
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.frame_layout, collectionFragment)
                            .commit();
                }
            });

            btnUse.setOnClickListener(v -> {
                dialog.dismiss();
                if (itemType.equals("PACK")) {
                    // Chỉ PACK mới đi vào luồng open pack.
                    if (getActivity() != null) {
                        ItemRevealFragment revealFragment = ItemRevealFragment.newInstance(
                                item.getName(), item.getDescription(),
                                item.getImageUri(), quantity, item.getProductCode());
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .replace(R.id.frame_layout, revealFragment)
                                .addToBackStack(null)
                                .commit();
                    }
                } else if (itemType.equals("OBJET")) {
                    Toast.makeText(requireContext(), getString(R.string.reveal_only_pack_supported), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Used " + quantity + "x " + item.getName(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        dialog.show();
    }

    private void showBuyDialog(ShopItem item) {
        android.app.Dialog dialog = new android.app.Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_shop_buy);
        
        // Ensure the dialog takes full width of the screen
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ImageView ivImage = dialog.findViewById(R.id.iv_dialog_image);
        TextView tvName = dialog.findViewById(R.id.tv_dialog_name);
        TextView tvRarity = dialog.findViewById(R.id.tv_dialog_rarity);
        TextView tvDesc = dialog.findViewById(R.id.tv_dialog_desc);
        TextView tvTimer = dialog.findViewById(R.id.tv_dialog_timer);
        TextView btnMinus = dialog.findViewById(R.id.btn_minus);
        TextView btnPlus = dialog.findViewById(R.id.btn_plus);
        android.widget.EditText etQuantity = dialog.findViewById(R.id.et_quantity);
        ImageView ivTotalIcon = dialog.findViewById(R.id.iv_total_icon);
        TextView tvTotalPrice = dialog.findViewById(R.id.tv_total_price);
        com.google.android.material.button.MaterialButton btnConfirm = dialog.findViewById(R.id.btn_confirm_buy);
        ImageView btnClose = dialog.findViewById(R.id.btn_dialog_close);

        final int[] qty = {1};
        final long priceC = item.getPriceCoins() != null ? item.getPriceCoins() : 0;
        final long priceD = item.getPriceDiamonds() != null ? item.getPriceDiamonds() : 0;

        tvName.setText(item.getName());
        tvRarity.setText(item.getType());
        tvDesc.setText(item.getDescription());
        
        if (item.getEndTime() != null && item.getEndTime() != -1L) {
            long timeLeftMs = item.getEndTime() - System.currentTimeMillis();
            if (timeLeftMs > 0) {
                long hours = timeLeftMs / (1000 * 60 * 60);
                long days = hours / 24;
                tvTimer.setVisibility(View.VISIBLE);
                if (days > 0) {
                    tvTimer.setText("⏳ Ends in " + days + " days");
                } else {
                    tvTimer.setText("⏳ Ends in " + hours + " hours");
                }
            } else {
                tvTimer.setVisibility(View.GONE);
            }
        } else {
            tvTimer.setVisibility(View.GONE);
        }
        
        if (priceD > 0) ivTotalIcon.setImageResource(R.drawable.ic_item_diamond);
        else ivTotalIcon.setImageResource(R.drawable.ic_item_coin);

        Runnable updatePrice = () -> {
            long total = (priceD > 0 ? priceD : priceC) * qty[0];
            tvTotalPrice.setText(NumberUtils.format(getContext(), total));
        };
        updatePrice.run(); // Initial calculation

        etQuantity.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (s.length() == 0) return;
                try {
                    int val = Integer.parseInt(s.toString());
                    if (val < 1) {
                        qty[0] = 1;
                    } else {
                        qty[0] = val;
                    }
                } catch (Exception e) {
                    qty[0] = 1;
                }
                updatePrice.run();
            }
        });

        Glide.with(this)
                .load(item.getImageUri() != null && !item.getImageUri().isEmpty() ? item.getImageUri() : "")
                .placeholder(R.drawable.item_shop_demo)
                .into(ivImage);

        btnMinus.setOnClickListener(v -> { if (qty[0] > 1) { qty[0]--; etQuantity.setText(String.valueOf(qty[0])); }});
        btnPlus.setOnClickListener(v -> { qty[0]++; etQuantity.setText(String.valueOf(qty[0])); });
        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Confirm Purchase")
                    .setMessage("Do you want to buy " + qty[0] + "x " + item.getName() + " for " + tvTotalPrice.getText().toString() + "?")
                    .setPositiveButton("Buy", (dialogInterface, i) -> {
                        dialog.dismiss();
                        executePurchase(item, qty[0]);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        dialog.show();
    }

    private class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.ViewHolder> {
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

            h.tvName.setText(item.getName());
            h.tvDesc.setText(item.getDescription());
            
            // Handle Limited Time display via separate TextView
            if (item.getEndTime() != null && item.getEndTime() != -1L) {
                long timeLeftMs = item.getEndTime() - System.currentTimeMillis();
                if (timeLeftMs > 0) {
                    long hours = timeLeftMs / (1000 * 60 * 60);
                    long days = hours / 24;
                    h.tvTimer.setVisibility(View.VISIBLE);
                    if (days > 0) {
                        h.tvTimer.setText("⏳ Ends in " + days + " days");
                    } else {
                        h.tvTimer.setText("⏳ Ends in " + hours + " hours");
                    }
                } else {
                    h.tvTimer.setVisibility(View.INVISIBLE);
                }
            } else {
                h.tvTimer.setVisibility(View.INVISIBLE); // Keep layout height stable
            }
            
            // Format price string based on what it costs
            long price = (item.getPriceCoins() != null && item.getPriceCoins() > 0) ? item.getPriceCoins() : (item.getPriceDiamonds() != null ? item.getPriceDiamonds() : 0);
            h.tvPrice.setText(NumberUtils.format(h.itemView.getContext(), price));
            h.tvRarity.setText(item.getType());
            
            // Set currency icon
            if (item.getPriceDiamonds() != null && item.getPriceDiamonds() > 0) {
                h.ivPriceIcon.setImageResource(R.drawable.ic_item_diamond);
            } else {
                h.ivPriceIcon.setImageResource(R.drawable.ic_item_coin);
            }

            Glide.with(h.itemView.getContext())
                    .load(item.getImageUri() != null && !item.getImageUri().isEmpty() ? item.getImageUri() : "")
                    .placeholder(R.drawable.item_shop_demo)
                    .into(h.ivImage);

            h.itemView.setOnClickListener(v -> showBuyDialog(item));

            h.btnBuy.setOnClickListener(v -> showBuyDialog(item));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivImage;
            final ImageView ivPriceIcon;
            final TextView  tvName;
            final TextView  tvDesc;
            final TextView  tvTimer;
            final TextView  tvPrice;
            final TextView  tvRarity;
            final TextView  btnBuy;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivImage      = itemView.findViewById(R.id.iv_shop_item_image);
                ivPriceIcon  = itemView.findViewById(R.id.iv_price_icon);
                tvName       = itemView.findViewById(R.id.tv_shop_item_name);
                tvDesc       = itemView.findViewById(R.id.tv_shop_item_desc);
                tvTimer      = itemView.findViewById(R.id.tv_shop_item_timer);
                tvPrice      = itemView.findViewById(R.id.tv_shop_item_price);
                tvRarity     = itemView.findViewById(R.id.tv_shop_item_rarity);
                btnBuy       = itemView.findViewById(R.id.btn_buy);
            }
        }
    }
}

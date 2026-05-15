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
import com.vn.jet.mosco.widget.MoscoDialogManager;
import com.vn.jet.mosco.widget.MoscoNotification;
import com.vn.jet.mosco.widget.MoscoButton;

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
        tvCoins.setText(getString(R.string.placeholder_empty));
        tvDiamonds.setText(getString(R.string.placeholder_empty));
    }

    private void fetchUserResources() {
        Long userId = sessionManager.getUserId();
        if (userId == null) return;
        
        apiService.getUserStats(userId).enqueue(new Callback<UserStats>() {
            @Override
            public void onResponse(Call<UserStats> call, Response<UserStats> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tvCoins.setText(NumberUtils.format(requireContext(), response.body().getCoins()));
                    tvDiamonds.setText(NumberUtils.format(requireContext(), response.body().getDiamonds()));
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
                MoscoNotification.showError(requireActivity(), getString(R.string.shop_error_load));
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
                    tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.lg_text_primary));
                } else {
                    tv.setBackgroundResource(R.drawable.bg_filter_chip);
                    tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.lg_text_secondary));
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
            boolean matchesCategory = selectedCat.equals("All") || item.getType().equalsIgnoreCase(selectedCat);
            if (matchesCategory) {
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
            Toast.makeText(requireContext(), getString(R.string.shop_msg_login_first), Toast.LENGTH_SHORT).show();
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
                    MoscoNotification.showError(requireActivity(), getString(R.string.shop_msg_purchase_failed));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                MoscoNotification.showError(requireActivity(), getString(R.string.common_error_network));
            }
        });
    }

    private void showSuccessDialog(ShopItem item, int quantity) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_shop_success, null);
        android.app.Dialog dialog = MoscoDialogManager.createLiquidDialog(requireContext(), view);

        TextView tvMsg = view.findViewById(R.id.tv_success_message);
        tvMsg.setText(getString(R.string.shop_format_buy_success, quantity, item.getName()));

        com.vn.jet.mosco.widget.MoscoButton btnInventory = view.findViewById(R.id.btn_go_inventory);
        com.vn.jet.mosco.widget.MoscoButton btnUseNow = view.findViewById(R.id.btn_use_now);
        com.vn.jet.mosco.widget.MoscoButton btnOk = view.findViewById(R.id.btn_ok);

        // Nếu là tài nguyên (Resource), chỉ hiện nút OK
        if (item.getType().equalsIgnoreCase("RESOURCE")) {
            view.findViewById(R.id.ll_actions_standard).setVisibility(View.GONE);
            btnOk.setVisibility(View.VISIBLE);
            btnOk.setOnClickListener(v -> dialog.dismiss());
        } else {
            // Điều hướng về Inventory (Tab Collection → sub-tab Items)
            btnInventory.setOnClickListener(v -> {
                dialog.dismiss();
                if (getActivity() != null) {
                    // Tạo CollectionFragment với default_tab = 3 (Items tab)
                    CollectionFragment collectionFragment = new CollectionFragment();
                    Bundle args = new Bundle();
                    args.putInt("default_tab", 3);
                    collectionFragment.setArguments(args);

                    getActivity().getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                            .replace(R.id.frame_layout, collectionFragment)
                            .commit();

                    // Đồng bộ BottomNav highlight về tab Collection
                    if (getActivity() instanceof com.vn.jet.mosco.MainActivity) {
                        android.widget.FrameLayout frame = getActivity().findViewById(R.id.frame_layout);
                        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                                getActivity().findViewById(R.id.bottom_navigation);
                        if (nav != null) nav.setSelectedItemId(R.id.nav_collect);
                    }
                }
            });

            // Điều hướng: "Dùng ngay" — mở màn hình mở thẻ cho PACK, hoặc về Inventory cho loại khác
            btnUseNow.setOnClickListener(v -> {
                dialog.dismiss();
                if (getActivity() == null) return;

                if (item.getType().equalsIgnoreCase("PACK")) {
                    // Mở màn hình Open Pack (ItemRevealFragment)
                    ItemRevealFragment revealFragment = ItemRevealFragment.newInstance(
                            item.getName(),
                            item.getDescription(),
                            item.getImageUri() != null ? item.getImageUri() : "",
                            quantity,
                            item.getProductCode()
                    );
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                            .replace(R.id.frame_layout, revealFragment)
                            .addToBackStack(null)
                            .commit();
                } else {
                    // BUFF/OBJET: chuyển về Collection → sub-tab Items để dùng
                    CollectionFragment collectionFragment = new CollectionFragment();
                    Bundle args = new Bundle();
                    args.putInt("default_tab", 3);
                    collectionFragment.setArguments(args);

                    getActivity().getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                            .replace(R.id.frame_layout, collectionFragment)
                            .commit();

                    if (getActivity() instanceof com.vn.jet.mosco.MainActivity) {
                        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                                getActivity().findViewById(R.id.bottom_navigation);
                        if (nav != null) nav.setSelectedItemId(R.id.nav_collect);
                    }
                }
            });
        }

        dialog.show();
    }

    private void showBuyDialog(ShopItem item) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_shop_buy, null);
        android.app.Dialog dialog = MoscoDialogManager.createLiquidDialog(requireContext(), dialogView);
        
        ImageView ivImage = dialogView.findViewById(R.id.iv_dialog_image);
        TextView tvName = dialogView.findViewById(R.id.tv_dialog_name);
        TextView tvRarity = dialogView.findViewById(R.id.tv_dialog_rarity);
        TextView tvDesc = dialogView.findViewById(R.id.tv_dialog_desc);
        TextView tvTimer = dialogView.findViewById(R.id.tv_dialog_timer);
        TextView btnMinus = dialogView.findViewById(R.id.btn_minus);
        TextView btnPlus = dialogView.findViewById(R.id.btn_plus);
        android.widget.EditText etQuantity = dialogView.findViewById(R.id.et_quantity);
        ImageView ivTotalIcon = dialogView.findViewById(R.id.iv_total_icon);
        TextView tvTotalPrice = dialogView.findViewById(R.id.tv_total_price);
        com.vn.jet.mosco.widget.MoscoButton btnConfirm = dialogView.findViewById(R.id.btn_confirm_buy);
        ImageView btnClose = dialogView.findViewById(R.id.btn_dialog_close);

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
                    tvTimer.setText(getString(R.string.shop_format_timer_days, (int)days));
                } else {
                    tvTimer.setText(getString(R.string.shop_format_timer_hours, (int)hours));
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
            tvTotalPrice.setText(NumberUtils.format(requireContext(), total));
        };
        updatePrice.run(); 

        etQuantity.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (s.length() == 0) return;
                try {
                    int val = Integer.parseInt(s.toString());
                    qty[0] = Math.max(1, val);
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
            dialog.dismiss();
            MoscoDialogManager.showConfirm(requireContext(),
                    getString(R.string.shop_dialog_buy_confirm_title),
                    getString(R.string.shop_dialog_buy_confirm_msg, qty[0], item.getName(), tvTotalPrice.getText().toString()),
                    getString(R.string.action_buy),
                    () -> executePurchase(item, qty[0]));
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
            
            if (item.getEndTime() != null && item.getEndTime() != -1L) {
                long timeLeftMs = item.getEndTime() - System.currentTimeMillis();
                if (timeLeftMs > 0) {
                    long hours = timeLeftMs / (1000 * 60 * 60);
                    long days = hours / 24;
                    h.tvTimer.setVisibility(View.VISIBLE);
                    if (days > 0) {
                        h.tvTimer.setText(getString(R.string.shop_format_timer_days, (int)days));
                    } else {
                        h.tvTimer.setText(getString(R.string.shop_format_timer_hours, (int)hours));
                    }
                } else {
                    h.tvTimer.setVisibility(View.INVISIBLE);
                }
            } else {
                h.tvTimer.setVisibility(View.INVISIBLE);
            }
            
            long price = (item.getPriceCoins() != null && item.getPriceCoins() > 0) ? item.getPriceCoins() : (item.getPriceDiamonds() != null ? item.getPriceDiamonds() : 0);
            h.tvPrice.setText(NumberUtils.format(h.itemView.getContext(), price));
            h.tvRarity.setText(item.getType());
            
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
            final com.vn.jet.mosco.widget.MoscoButton btnBuy;

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


package com.vn.jet.mosco;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.vn.jet.mosco.adapter.FriendSelectAdapter;
import com.vn.jet.mosco.adapter.GiftHistoryAdapter;
import com.vn.jet.mosco.fragment.InventoryBottomSheet;
import com.vn.jet.mosco.model.Objet;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.SmartFaceCropTransformation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * GiftActivity — Giao diện gửi và nhận Objet giữa các user.
 *
 * 2 Tab chính:
 *   - Tab "GỬI": Wizard 3 bước (Chọn thẻ → Chọn bạn → Xác nhận)
 *   - Tab "NHẬN": Danh sách quà đã nhận (inbox)
 *
 * Phí gửi: 36,000 Coin + 36 Diamond. Giới hạn: 5 lần/ngày.
 * Thẻ trong Formation không cho gửi.
 */
public class GiftActivity extends AppCompatActivity {

    private static final String TAG = "GiftActivity";

    // ── Services ──
    private GameApiService apiService;

    // ── UI References ──
    private TabLayout tabLayout;
    private View layoutTabSend, layoutTabReceived;
    private View layoutStep1, layoutStep2, layoutStep3;
    private TextView tvStep1, tvStep2, tvStep3;
    private TextView tvDailyRemaining;

    // Step 1: Chọn thẻ
    private View cvSelectCardBtn, btnStep1Next;
    private MaterialCardView cvSelectedCard;

    // Step 2: Chọn bạn bè
    private RecyclerView rvFriendSelect;
    private View btnStep2Next, btnStep2Prev, tvNoFriends;
    private FriendSelectAdapter friendSelectAdapter;
    private android.widget.EditText etSearchFriend;

    // Step 3: Xác nhận
    private ImageView ivConfirmFriendAvatar;
    private TextView tvConfirmFriendName, tvSenderName;
    private View btnConfirmCancel, btnConfirmSend, btnConfirmDone;
    private View layoutConfirmActions, layoutGiftCost;

    // Tab Nhận
    private RecyclerView rvGiftReceived;
    private View tvNoGifts;
    private GiftHistoryAdapter giftHistoryAdapter;

    // ── State ──
    private Objet selectedObjet = null;
    private JSONObject selectedFriend = null;
    private int currentStep = 1;
    private List<JSONObject> allFriendsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gift);

        apiService = ApiClient.getClient(this).create(GameApiService.class);

        initViews();
        setupTabs();
        setupStep1();
        setupStep2();
        setupStep3();
        loadDailyRemaining();
        handleIntent();
    }

    private void handleIntent() {
        String cardId = getIntent().getStringExtra("target_collection_id");
        if (cardId != null && !cardId.isEmpty() && DatabaseLoader.cachedUserInventory != null) {
            for (DatabaseLoader.UserInventoryItem item : DatabaseLoader.cachedUserInventory) {
                if (item.collectionId.equals(cardId)) {
                    selectedObjet = Objet.fromCacheItem(item);
                    bindSelectedCard();
                    goToStep(2);
                    loadFriendList();
                    break;
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  KHỞI TẠO
    // ════════════════════════════════════════════════════════════════

    private void initViews() {
        // Header
        findViewById(R.id.btn_back_common).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tv_header_title)).setText(R.string.gift_header_title);
        tvDailyRemaining = findViewById(R.id.tv_daily_remaining);

        // Tabs
        tabLayout = findViewById(R.id.tab_layout_gift);
        layoutTabSend = findViewById(R.id.layout_tab_send);
        layoutTabReceived = findViewById(R.id.layout_tab_received);

        // Steps
        layoutStep1 = findViewById(R.id.layout_step1_select_card);
        layoutStep2 = findViewById(R.id.layout_step2_select_friend);
        layoutStep3 = findViewById(R.id.layout_step3_confirm);
        tvStep1 = findViewById(R.id.tv_step_1);
        tvStep2 = findViewById(R.id.tv_step_2);
        tvStep3 = findViewById(R.id.tv_step_3);

        // Step 1
        cvSelectCardBtn = findViewById(R.id.cv_select_card_btn);
        cvSelectedCard = findViewById(R.id.cv_selected_card);
        btnStep1Next = findViewById(R.id.btn_step1_next);

        // Step 2
        rvFriendSelect = findViewById(R.id.rv_friend_select);
        btnStep2Next = findViewById(R.id.btn_step2_next);
        btnStep2Prev = findViewById(R.id.btn_step2_prev);
        tvNoFriends = findViewById(R.id.tv_no_friends);
        etSearchFriend = findViewById(R.id.et_gift_search_friend);

        // Step 3
        ivConfirmFriendAvatar = findViewById(R.id.iv_confirm_friend_avatar);
        tvConfirmFriendName = findViewById(R.id.tv_confirm_friend_name);
        tvSenderName = findViewById(R.id.tv_sender_name);
        btnConfirmCancel = findViewById(R.id.btn_confirm_cancel);
        btnConfirmSend = findViewById(R.id.btn_confirm_send);
        btnConfirmDone = findViewById(R.id.btn_confirm_done);
        layoutConfirmActions = findViewById(R.id.layout_confirm_actions);
        layoutGiftCost = findViewById(R.id.layout_gift_cost);

        // Tab Nhận
        rvGiftReceived = findViewById(R.id.rv_gift_received);
        tvNoGifts = findViewById(R.id.tv_no_gifts);
    }

    // ════════════════════════════════════════════════════════════════
    //  TABS: GỬI | NHẬN
    // ════════════════════════════════════════════════════════════════

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.gift_tab_send));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.gift_tab_received));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    layoutTabSend.setVisibility(View.VISIBLE);
                    layoutTabReceived.setVisibility(View.GONE);
                } else {
                    layoutTabSend.setVisibility(View.GONE);
                    layoutTabReceived.setVisibility(View.VISIBLE);
                    loadReceivedGifts();
                    markGiftsAsRead();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    // ════════════════════════════════════════════════════════════════
    //  STEP 1: CHỌN OBJET
    // ════════════════════════════════════════════════════════════════

    private void setupStep1() {
        // Nhấn vào ô trống → mở InventoryBottomSheet
        cvSelectCardBtn.setOnClickListener(v -> openCardSelector());

        // Nhấn vào card đã chọn → đổi card
        cvSelectedCard.setOnClickListener(v -> openCardSelector());

        // Nút Next → chuyển Step 2
        btnStep1Next.setOnClickListener(v -> {
            if (selectedObjet != null) {
                goToStep(2);
                loadFriendList();
            }
        });
    }

    /**
     * Mở InventoryBottomSheet để chọn thẻ tặng.
     * Reuse component đã có sẵn trong project.
     */
    private void openCardSelector() {
        InventoryBottomSheet bottomSheet = new InventoryBottomSheet();
        bottomSheet.setOnObjetSelectedListener(card -> {
            if (card != null) {
                selectedObjet = card;
                bindSelectedCard();
            }
        });
        bottomSheet.show(getSupportFragmentManager(), "gift_card_selector");
    }

    /**
     * Hiển thị thẻ đã chọn lên UI.
     */
    private void bindSelectedCard() {
        if (selectedObjet == null) return;

        // Ẩn placeholder, hiện card
        cvSelectCardBtn.setVisibility(View.GONE);
        cvSelectedCard.setVisibility(View.VISIBLE);
        btnStep1Next.setVisibility(View.VISIBLE);

        // Access internal layout_core_card views
        ImageView ivCardImage = cvSelectedCard.findViewById(R.id.card_iv_image);
        TextView tvCardOvr = cvSelectedCard.findViewById(R.id.card_tv_ovr);
        ImageView ivCardLevel = cvSelectedCard.findViewById(R.id.card_iv_level);
        View viewCardShimmer = cvSelectedCard.findViewById(R.id.view_card_shimmer);

        Glide.with(this)
                .load(selectedObjet.getImageUrl())
                .placeholder(R.drawable.objet_back_spin)
                .into(ivCardImage);

        // HIỆU ỨNG SHOWCASE CAO CẤP (Bê nguyên từ HomeFragment)
        if (tvCardOvr != null) {
            tvCardOvr.setVisibility(View.GONE);
        }

        if (ivCardLevel != null) {
            if (selectedObjet.getUpgradeLevel() > 0) {
                String assetPath = "file:///android_asset/grade/" + selectedObjet.getUpgradeLevel() + ".png";
                Glide.with(this).load(assetPath).into(ivCardLevel);
                ivCardLevel.setVisibility(View.VISIBLE);
                
                // Hiệu ứng Glow cho Badge
                com.vn.jet.mosco.utils.LevelBadgeEffectHelper.apply(ivCardLevel, selectedObjet.getUpgradeLevel());
            } else {
                ivCardLevel.setVisibility(View.GONE);
                com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivCardLevel);
            }
        }

        // Hiệu ứng Shimmer + TriplesBorder + Neon Glow bao quanh
        com.vn.jet.mosco.utils.CardEffectHelper.apply(cvSelectedCard, viewCardShimmer, selectedObjet, true);
    }

    // ════════════════════════════════════════════════════════════════
    //  STEP 2: CHỌN BẠN BÈ
    // ════════════════════════════════════════════════════════════════

    private void setupStep2() {
        rvFriendSelect.setLayoutManager(new LinearLayoutManager(this));
        friendSelectAdapter = new FriendSelectAdapter(new ArrayList<>(), (friend, pos) -> {
            selectedFriend = friend;
            btnStep2Next.setAlpha(1.0f);
            btnStep2Next.setVisibility(View.VISIBLE);
        });
        rvFriendSelect.setAdapter(friendSelectAdapter);

        btnStep2Prev.setOnClickListener(v -> goToStep(1));

        btnStep2Next.setOnClickListener(v -> {
            if (selectedFriend != null) {
                goToStep(3);
                bindConfirmation();
            } else {
                Toast.makeText(this, R.string.gift_msg_select_friend_first, Toast.LENGTH_SHORT).show();
            }
        });

        etSearchFriend.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                filterFriends(s.toString());
            }
        });
    }

    private void filterFriends(String query) {
        if (query == null || query.trim().isEmpty()) {
            friendSelectAdapter.updateData(allFriendsList);
            return;
        }

        String lowerQuery = query.toLowerCase().trim();
        List<JSONObject> filtered = new ArrayList<>();
        for (JSONObject friend : allFriendsList) {
            String name = friend.optString("ingameName", "").toLowerCase();
            String username = friend.optString("username", "").toLowerCase();
            if (name.contains(lowerQuery) || username.contains(lowerQuery)) {
                filtered.add(friend);
            }
        }
        friendSelectAdapter.updateData(filtered);
    }

    /**
     * Load danh sách bạn bè từ API.
     */
    private void loadFriendList() {
        apiService.getFriendList().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray dataArr = json.optJSONArray("data");
                        allFriendsList.clear();
                        if (dataArr != null) {
                            for (int i = 0; i < dataArr.length(); i++) {
                                allFriendsList.add(dataArr.getJSONObject(i));
                            }
                        }
                        
                        // Clear search if loading new
                        etSearchFriend.setText("");
                        friendSelectAdapter.updateData(allFriendsList);

                        // Hiện/ẩn empty state
                        tvNoFriends.setVisibility(allFriendsList.isEmpty() ? View.VISIBLE : View.GONE);
                        rvFriendSelect.setVisibility(allFriendsList.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi load friend list", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Lỗi kết nối", t);
                Toast.makeText(GiftActivity.this, R.string.common_error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ════════════════════════════════════════════════════════════════
    //  STEP 3: XÁC NHẬN & GỬI
    // ════════════════════════════════════════════════════════════════

    private void setupStep3() {
        btnConfirmCancel.setOnClickListener(v -> {
            // Quay lại Step 2
            goToStep(2);
        });

        btnConfirmSend.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.gift_dialog_confirm_title)
                .setMessage(getString(R.string.gift_dialog_confirm_msg, selectedFriend.optString("ingameName", "this friend")))
                .setPositiveButton(R.string.gift_action_send, (d, w) -> executeSendGift())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
        });

        btnConfirmDone.setOnClickListener(v -> {
            selectedObjet = null;
            selectedFriend = null;
            resetSendWizard();
            goToStep(1);
        });
    }

    /**
     * Bind dữ liệu lên giao diện xác nhận.
     */
    private void bindConfirmation() {
        if (selectedObjet == null || selectedFriend == null) return;

        // No card preview in Confirm step as requested

        // Sender info & Avatar
        com.vn.jet.mosco.utils.SessionManager session = new com.vn.jet.mosco.utils.SessionManager(this);
        tvSenderName.setText(session.getIngameName() != null ? session.getIngameName() : getString(R.string.common_label_you));

        // Load personal avatar
        String myAvatarId = session.getAvatarId();
        JSONObject myAvatarCard = DatabaseLoader.findByCollectionId(this, myAvatarId);
        ImageView ivSenderAvatar = findViewById(R.id.iv_sender_avatar_img);
        if (myAvatarCard != null) {
            Glide.with(this)
                    .load(myAvatarCard.optString("frontImage", ""))
                    .transform(new SmartFaceCropTransformation())
                    .placeholder(R.drawable.ic_user)
                    .into(ivSenderAvatar);
        }

        // Friend info
        tvConfirmFriendName.setText(selectedFriend.optString("ingameName", "Unknown"));

        // Avatar bạn bè
        String avatarId = selectedFriend.optString("avatarId", "1");
        JSONObject card = DatabaseLoader.findByCollectionId(this, avatarId);
        if (card != null) {
            Glide.with(this)
                    .load(card.optString("frontImage", ""))
                    .transform(new SmartFaceCropTransformation())
                    .placeholder(R.drawable.ic_user)
                    .into(ivConfirmFriendAvatar);
        }
    }

    /**
     * Gọi API gửi tặng — sau khi thành công, reset wizard và refresh daily remaining.
     */
    private void executeSendGift() {
        if (selectedObjet == null || selectedFriend == null) return;

        // Disable nút gửi để chống double-tap
        btnConfirmSend.setEnabled(false);

        Long cardId = (long) selectedObjet.getId();
        Long receiverId = selectedFriend.optLong("userId");

        Map<String, Long> body = new HashMap<>();
        body.put("cardId", cardId);
        body.put("receiverId", receiverId);

        apiService.sendGift(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                btnConfirmSend.setEnabled(true);
                try {
                    String msg;
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        msg = json.optString("message", "Gift sent successfully!");

                        // Reload inventory từ Server vì card đã chuyển chủ
                        Long userId = new com.vn.jet.mosco.utils.SessionManager(GiftActivity.this).getUserId();
                        DatabaseLoader.reloadInventoryFromServer(GiftActivity.this, userId, apiService);

                        // Display Success Dialog
                        new android.app.AlertDialog.Builder(GiftActivity.this)
                            .setTitle(R.string.gift_dialog_success_title)
                            .setMessage(msg)
                            .setPositiveButton(R.string.action_done, null)
                            .show();
                        
                        // Change UI state to Done
                        layoutConfirmActions.setVisibility(View.GONE);
                        layoutGiftCost.setVisibility(View.INVISIBLE);
                        btnConfirmDone.setVisibility(View.VISIBLE);

                        loadDailyRemaining();
                        
                    } else if (response.errorBody() != null) {
                        JSONObject json = new JSONObject(response.errorBody().string());
                        msg = json.optString("message", getString(R.string.social_msg_request_error));
                        Toast.makeText(GiftActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing response", e);
                    Toast.makeText(GiftActivity.this, R.string.common_error_unknown, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                btnConfirmSend.setEnabled(true);
                Log.e(TAG, "Lỗi kết nối", t);
                Toast.makeText(GiftActivity.this, R.string.common_error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ════════════════════════════════════════════════════════════════
    //  TAB NHẬN — Inbox
    // ════════════════════════════════════════════════════════════════

    /**
     * Load danh sách quà đã nhận từ API.
     */
    private void loadReceivedGifts() {
        if (giftHistoryAdapter != null) {
            giftHistoryAdapter.setLoading(true);
            rvGiftReceived.setVisibility(View.VISIBLE);
            tvNoGifts.setVisibility(View.GONE);
        }

        apiService.getReceivedGifts().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray dataArr = json.optJSONArray("data");
                        List<JSONObject> gifts = new ArrayList<>();
                        if (dataArr != null) {
                            for (int i = 0; i < dataArr.length(); i++) {
                                gifts.add(dataArr.getJSONObject(i));
                            }
                        }

                        if (giftHistoryAdapter == null) {
                            giftHistoryAdapter = new GiftHistoryAdapter(gifts, true);
                            rvGiftReceived.setLayoutManager(new LinearLayoutManager(GiftActivity.this));
                            rvGiftReceived.setAdapter(giftHistoryAdapter);
                        } else {
                            giftHistoryAdapter.updateData(gifts);
                        }

                        // Hiện/ẩn empty state
                        tvNoGifts.setVisibility(gifts.isEmpty() ? View.VISIBLE : View.GONE);
                        rvGiftReceived.setVisibility(gifts.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi load received gifts", e);
                    if (giftHistoryAdapter != null) giftHistoryAdapter.setLoading(false);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Lỗi kết nối", t);
                if (giftHistoryAdapter != null) giftHistoryAdapter.setLoading(false);
            }
        });
    }

    /**
     * Đánh dấu tất cả quà nhận là đã đọc.
     */
    private void markGiftsAsRead() {
        apiService.markGiftsAsRead().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {}
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {}
        });
    }

    // ════════════════════════════════════════════════════════════════
    //  DAILY REMAINING
    // ════════════════════════════════════════════════════════════════

    private void loadDailyRemaining() {
        apiService.getDailyGiftRemaining().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            int remaining = data.optInt("remaining", 5);
                            int limit = data.optInt("limit", 5);
                            tvDailyRemaining.setText(getString(R.string.format_fraction, String.valueOf(remaining), String.valueOf(limit)));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi load daily remaining", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                tvDailyRemaining.setText(getString(R.string.format_fraction, "?", "5"));
            }
        });
    }

    // ════════════════════════════════════════════════════════════════
    //  WIZARD NAVIGATION
    // ════════════════════════════════════════════════════════════════

    /**
     * Chuyển đến step chỉ định (1, 2, hoặc 3).
     * Cập nhật indicator + ẩn/hiện layout tương ứng.
     */
    private void goToStep(int step) {
        currentStep = step;

        // Ẩn tất cả step
        layoutStep1.setVisibility(View.GONE);
        layoutStep2.setVisibility(View.GONE);
        layoutStep3.setVisibility(View.GONE);

        // Reset màu indicator — dùng color resource thay vì hardcode
        int activeColor = getResources().getColor(R.color.mosco_primary, getTheme());
        int dimColor = getResources().getColor(R.color.mosco_text_dim, getTheme());

        tvStep1.setTextColor(step >= 1 ? activeColor : dimColor);
        tvStep2.setTextColor(step >= 2 ? activeColor : dimColor);
        tvStep3.setTextColor(step >= 3 ? activeColor : dimColor);

        // Hiện step hiện tại
        switch (step) {
            case 1: layoutStep1.setVisibility(View.VISIBLE); break;
            case 2: layoutStep2.setVisibility(View.VISIBLE); break;
            case 3: layoutStep3.setVisibility(View.VISIBLE); break;
        }
    }

    /**
     * Reset wizard về trạng thái ban đầu.
     */
    private void resetSendWizard() {
        // Xóa sạch hiệu ứng Showcase của thẻ cũ (Glow, Shimmer)
        View viewCardShimmer = cvSelectedCard.findViewById(R.id.view_card_shimmer);
        com.vn.jet.mosco.utils.CardEffectHelper.remove(cvSelectedCard, viewCardShimmer);

        cvSelectCardBtn.setVisibility(View.VISIBLE);
        cvSelectedCard.setVisibility(View.GONE);
        btnStep1Next.setVisibility(View.GONE);
        if (btnStep2Next != null) btnStep2Next.setVisibility(View.GONE);
        if (etSearchFriend != null) etSearchFriend.setText("");
        
        // Reset Step 3 UI
        layoutConfirmActions.setVisibility(View.VISIBLE);
        layoutGiftCost.setVisibility(View.VISIBLE);
        btnConfirmDone.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        // Cho phép quay lại step trước trong wizard
        if (currentStep > 1 && layoutTabSend.getVisibility() == View.VISIBLE) {
            goToStep(currentStep - 1);
        } else {
            super.onBackPressed();
        }
    }
}

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
import com.vn.jet.mosco.model.CardDisplayItem;
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
public class GiftActivity extends MoscoBaseActivity {

    private static final String TAG = "GiftActivity";

    // ── Services ──
    private GameApiService apiService;

    // ── UI References ──
    private TabLayout tabLayout;
    private View layoutTabSend, layoutTabReceived;
    private View layoutStep1, layoutStep2;
    private TextView tvStep1, tvStep2;
    private TextView tvDailyRemaining;
    private View layoutHeaderGiftContainer, layoutDailyRemainingContainer;

    // Step 1: Chọn thẻ
    private View cvSelectCardBtn, btnStep1Next;
    private MaterialCardView cvSelectedCard;

    // Step 2: Chọn bạn bè
    private RecyclerView rvFriendSelect;
    private View btnStep2Next, btnStep2Prev, tvNoFriends;
    private FriendSelectAdapter friendSelectAdapter;
    private android.widget.EditText etSearchFriend;



    // Success Screen & Indicators
    private View layoutStepIndicator, flStepContent;
    private View layoutSuccessScreen;
    private TextView tvSuccessTimestamp, tvSuccessCardInfo, tvSuccessSenderInfo, tvSuccessReceiverInfo;
    private MaterialCardView cvSuccessSelectedCard;
    private View btnSuccessCollect, btnSuccessNewGift;

    // Tab Nhận
    private RecyclerView rvGiftReceived;
    private View tvNoGifts;
    private GiftHistoryAdapter giftHistoryAdapter;

    // ── State ──
    private CardDisplayItem selectedObjet = null;
    private JSONObject selectedFriend = null;
    private int currentStep = 1;
    private List<JSONObject> allFriendsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gift);

        // Đăng ký bộ lắng nghe Window Insets để tự động tính toán padding động cho root view.
        // Lý do (WHY): Nhằm đưa các thành phần UI (đặc biệt là các nút hành động ở đáy màn hình như Previous/Next)
        // vào vùng an toàn (Safe Area), tránh bị che hoặc đè bởi thanh trạng thái (Status Bar)
        // và thanh điều hướng ảo (Navigation Bar) khi kích hoạt chế độ Edge-to-Edge tràn viền.
        View rootLayout = findViewById(R.id.root_gift_layout);
        if (rootLayout != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return androidx.core.view.WindowInsetsCompat.CONSUMED;
            });
        }

        apiService = ApiClient.getClient(this).create(GameApiService.class);
        com.vn.jet.mosco.utils.DatabaseLoader.initMasterData(this);

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
                    selectedObjet = CardDisplayItem.fromCacheItem(item);
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
        layoutHeaderGiftContainer = findViewById(R.id.layout_header_gift_container);
        layoutDailyRemainingContainer = findViewById(R.id.layout_daily_remaining_container);

        // Tabs
        tabLayout = findViewById(R.id.tab_layout_gift);
        layoutTabSend = findViewById(R.id.layout_tab_send);
        layoutTabReceived = findViewById(R.id.layout_tab_received);

        // Steps
        layoutStep1 = findViewById(R.id.layout_step1_select_card);
        layoutStep2 = findViewById(R.id.layout_step2_select_friend);
        tvStep1 = findViewById(R.id.tv_step_1);
        tvStep2 = findViewById(R.id.tv_step_2);

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

        // Success Screen & Wizard controls
        layoutStepIndicator = findViewById(R.id.layout_step_indicator);
        flStepContent = findViewById(R.id.fl_step_content);
        layoutSuccessScreen = findViewById(R.id.layout_success_screen);
        tvSuccessTimestamp = findViewById(R.id.tv_success_timestamp);
        tvSuccessCardInfo = findViewById(R.id.tv_success_card_info);
        tvSuccessSenderInfo = findViewById(R.id.tv_success_sender_info);
        tvSuccessReceiverInfo = findViewById(R.id.tv_success_receiver_info);
        cvSuccessSelectedCard = findViewById(R.id.cv_success_selected_card);
        btnSuccessCollect = findViewById(R.id.btn_success_collect);
        btnSuccessNewGift = findViewById(R.id.btn_success_new_gift);

        // Tab Nhận
        rvGiftReceived = findViewById(R.id.rv_gift_received);
        rvGiftReceived.setHasFixedSize(true);
        rvGiftReceived.setItemViewCacheSize(20);
        rvGiftReceived.setDrawingCacheEnabled(true);
        rvGiftReceived.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        rvFriendSelect = findViewById(R.id.rv_friend_select);
        rvFriendSelect.setHasFixedSize(true);
        rvFriendSelect.setItemViewCacheSize(20);
        rvFriendSelect.setDrawingCacheEnabled(true);
        rvFriendSelect.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        tvNoGifts = findViewById(R.id.tv_no_gifts);
    }

    // ════════════════════════════════════════════════════════════════
    //  TABS: GỬI | NHẬN
    // ════════════════════════════════════════════════════════════════

    private void setupTabs() {
        if (tabLayout != null) {
            tabLayout.setVisibility(View.GONE);
        }
        layoutTabSend.setVisibility(View.VISIBLE);
        layoutTabReceived.setVisibility(View.GONE);
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
        bottomSheet.setOnCardSelectedListener(card -> {
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
        
        // Luồng tải ưu tiên: Thẻ đang chọn gửi quà dùng bản Original chất lượng cao
        com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(ivCardImage, selectedObjet.getFrontImage(), false);

        // HIỆU ỨNG SHOWCASE CAO CẤP (Bê nguyên từ HomeFragment)
        if (tvCardOvr != null) {
            tvCardOvr.setVisibility(View.GONE);
        }

        if (ivCardLevel != null) {
            if (selectedObjet.getLevel() > 0) {
                String assetPath = "file:///android_asset/grade/" + selectedObjet.getLevel() + ".png";
                Glide.with(this).load(assetPath).into(ivCardLevel);
                ivCardLevel.setVisibility(View.VISIBLE);
                
                // Hiệu ứng Glow cho Badge
                com.vn.jet.mosco.utils.LevelBadgeEffectHelper.apply(ivCardLevel, selectedObjet.getLevel());
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
                showSendConfirmDialog();
            } else {
                Toast.makeText(this, R.string.gift_msg_select_friend_first, Toast.LENGTH_SHORT).show();
            }
        });

        etSearchFriend.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                performSearch(s.toString());
            }
        });
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            friendSelectAdapter.updateData(allFriendsList);
            tvNoFriends.setVisibility(allFriendsList.isEmpty() ? View.VISIBLE : View.GONE);
            rvFriendSelect.setVisibility(allFriendsList.isEmpty() ? View.GONE : View.VISIBLE);
            return;
        }

        apiService.searchUsers(query.trim()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray dataArr = json.optJSONArray("data");
                        List<JSONObject> searchResults = new ArrayList<>();
                        if (dataArr != null) {
                            for (int i = 0; i < dataArr.length(); i++) {
                                searchResults.add(dataArr.getJSONObject(i));
                            }
                        }
                        friendSelectAdapter.updateData(searchResults);
                        tvNoFriends.setVisibility(searchResults.isEmpty() ? View.VISIBLE : View.GONE);
                        rvFriendSelect.setVisibility(searchResults.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi phân tích kết quả tìm kiếm", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Lỗi kết nối khi tìm kiếm", t);
            }
        });
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

    /**
     * Hiển thị Dialog Xác nhận gửi thẻ (Đồng bộ UI/UX với Spin Confirmation).
     * Yêu cầu: Tiêu đề và nội dung hoàn toàn bằng Tiếng Anh.
     * Cấu trúc: Are you sure you want to send {full name} +{level} to {username} ({idusername})?
     */
    private void showSendConfirmDialog() {
        if (selectedObjet == null || selectedFriend == null) return;

        // Tên đầy đủ của thẻ: Season + Member + CollectionNo
        String fullCardName = (selectedObjet.getSeason() != null ? selectedObjet.getSeason() : "") + " "
                + (selectedObjet.getMember() != null ? selectedObjet.getMember() : "") + " "
                + (selectedObjet.getCollectionNo() != null ? selectedObjet.getCollectionNo() : "");
        fullCardName = fullCardName.trim();

        String levelStr = selectedObjet.getLevel() > 0 ? " +" + selectedObjet.getLevel() : "";

        // Thông tin bạn bè nhận
        String receiverName = selectedFriend.optString("ingameName", "Unknown");
        String receiverUsername = selectedFriend.optString("username", "");

        // Tạo nội dung thông báo chuẩn tiếng Anh
        String msg = "Are you sure you want to send " + fullCardName + levelStr + " to " + receiverName + " (" + receiverUsername + ")?";

        View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_gift_confirm, null);
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setView(dialogView);
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        com.vn.jet.mosco.widget.MoscoButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        com.vn.jet.mosco.widget.MoscoButton btnConfirm = dialogView.findViewById(R.id.btn_confirm);

        if (tvTitle != null) {
            tvTitle.setText("Confirm Send");
        }
        if (tvMessage != null) {
            tvMessage.setText(msg);
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();
                executeSendGift();
            });
        }

        dialog.show();
    }

    // ════════════════════════════════════════════════════════════════
    //  STEP 3: XÁC NHẬN & GỬI
    // ════════════════════════════════════════════════════════════════

    private void setupStep3() {
        if (btnSuccessCollect != null) {
            btnSuccessCollect.setOnClickListener(v -> finish());
        }

        if (btnSuccessNewGift != null) {
            btnSuccessNewGift.setOnClickListener(v -> {
                // Ẩn Success Screen
                if (layoutSuccessScreen != null) layoutSuccessScreen.setVisibility(View.GONE);
                
                // Hiện lại các Header của Activity đã ẩn trước đó
                if (layoutHeaderGiftContainer != null) layoutHeaderGiftContainer.setVisibility(View.VISIBLE);
                if (layoutDailyRemainingContainer != null) layoutDailyRemainingContainer.setVisibility(View.VISIBLE);
                if (tabLayout != null) tabLayout.setVisibility(View.GONE);
                
                // Hiện lại màn hình chính của Send Wizard
                if (layoutStepIndicator != null) layoutStepIndicator.setVisibility(View.VISIBLE);
                if (flStepContent != null) flStepContent.setVisibility(View.VISIBLE);
                
                // Xóa sạch hiệu ứng Showcase của card success để tránh leak
                if (cvSuccessSelectedCard != null) {
                    View viewSuccessShimmer = cvSuccessSelectedCard.findViewById(R.id.view_card_shimmer);
                    com.vn.jet.mosco.utils.CardEffectHelper.remove(cvSuccessSelectedCard, viewSuccessShimmer);
                }
                
                // Reset trạng thái
                selectedObjet = null;
                selectedFriend = null;
                resetSendWizard();
                goToStep(1);
            });
        }
    }



    /**
     * Gọi API gửi tặng — sau khi thành công, reset wizard và refresh daily remaining.
     */
    private void executeSendGift() {
        if (selectedObjet == null || selectedFriend == null) return;

        Long cardId = selectedObjet.getId();
        Long receiverId = selectedFriend.optLong("userId");

        Map<String, Long> body = new HashMap<>();
        body.put("cardId", cardId);
        body.put("receiverId", receiverId);

        apiService.sendGift(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String msg;
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        msg = json.optString("message", "Gift sent successfully!");

                        // Reload inventory từ Server vì card đã chuyển chủ
                        Long userId = new com.vn.jet.mosco.utils.SessionManager(GiftActivity.this).getUserId();
                        DatabaseLoader.reloadInventoryFromServer(GiftActivity.this, userId, apiService);

                        // Ẩn Send Wizard UI (Indicator + Content)
                        if (layoutStepIndicator != null) layoutStepIndicator.setVisibility(View.GONE);
                        if (flStepContent != null) flStepContent.setVisibility(View.GONE);

                        // Ẩn các Header, tab bar của Activity theo đúng yêu cầu UI Success Screen
                        if (layoutHeaderGiftContainer != null) layoutHeaderGiftContainer.setVisibility(View.GONE);
                        if (layoutDailyRemainingContainer != null) layoutDailyRemainingContainer.setVisibility(View.GONE);
                        if (tabLayout != null) tabLayout.setVisibility(View.GONE);

                        // Hiển thị Success Screen
                        if (layoutSuccessScreen != null) layoutSuccessScreen.setVisibility(View.VISIBLE);

                        // 1. Bind Timestamp: định dạng đúng "19:36 Mon 18/05/2026"
                        if (tvSuccessTimestamp != null) {
                            try {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm EEE dd/MM/yyyy", java.util.Locale.US);
                                String currentTime = sdf.format(new java.util.Date());
                                tvSuccessTimestamp.setText(currentTime);
                            } catch (Exception e) {
                                tvSuccessTimestamp.setText("");
                            }
                        }

                        // 2. Bind Card Preview to cvSuccessSelectedCard
                        if (cvSuccessSelectedCard != null) {
                            ImageView ivSuccessCardImage = cvSuccessSelectedCard.findViewById(R.id.card_iv_image);
                            TextView tvSuccessCardOvr = cvSuccessSelectedCard.findViewById(R.id.card_tv_ovr);
                            ImageView ivSuccessCardLevel = cvSuccessSelectedCard.findViewById(R.id.card_iv_level);
                            View viewSuccessCardShimmer = cvSuccessSelectedCard.findViewById(R.id.view_card_shimmer);

                            if (ivSuccessCardImage != null) {
                                com.vn.jet.mosco.utils.GlideBindingAdapter.loadImage(ivSuccessCardImage, selectedObjet.getFrontImage(), false);
                            }
                            if (tvSuccessCardOvr != null) {
                                tvSuccessCardOvr.setVisibility(View.GONE);
                            }
                            if (ivSuccessCardLevel != null) {
                                if (selectedObjet.getLevel() > 0) {
                                    String assetPath = "file:///android_asset/grade/" + selectedObjet.getLevel() + ".png";
                                    Glide.with(GiftActivity.this).load(assetPath).into(ivSuccessCardLevel);
                                    ivSuccessCardLevel.setVisibility(View.VISIBLE);
                                    com.vn.jet.mosco.utils.LevelBadgeEffectHelper.apply(ivSuccessCardLevel, selectedObjet.getLevel());
                                } else {
                                    ivSuccessCardLevel.setVisibility(View.GONE);
                                    com.vn.jet.mosco.utils.LevelBadgeEffectHelper.remove(ivSuccessCardLevel);
                                }
                            }
                            // Bỏ floating bồng bềnh (applyFloating = false), kích hoạt viền phát sáng (applyGlow = true)
                            com.vn.jet.mosco.utils.CardEffectHelper.apply(cvSuccessSelectedCard, viewSuccessCardShimmer, selectedObjet, false, true);
                        }

                        // 3. Bind Info Text
                        // Line 1: Name, Serial + Grade level (chữ IN HOA, ví dụ "KAEDE BINARY02 205Z +1")
                        if (tvSuccessCardInfo != null) {
                            String levelStr = selectedObjet.getLevel() > 0 ? " +" + selectedObjet.getLevel() : "";
                            String fullInfo = selectedObjet.getFormattedNameTag() + levelStr;
                            tvSuccessCardInfo.setText(fullInfo.toUpperCase());
                        }

                        // Line 2: Sender Username (không có "From: " prefix)
                        if (tvSuccessSenderInfo != null) {
                            com.vn.jet.mosco.utils.SessionManager session = new com.vn.jet.mosco.utils.SessionManager(GiftActivity.this);
                            String senderName = session.getIngameName() != null ? session.getIngameName() : "cc3m";
                            tvSuccessSenderInfo.setText(senderName);
                        }

                        // Line 3: Receiver Username (không có "To: " prefix)
                        if (tvSuccessReceiverInfo != null) {
                            String receiverName = selectedFriend.optString("ingameName", "prime");
                            tvSuccessReceiverInfo.setText(receiverName);
                        }

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
     * Chuyển đến step chỉ định (1 hoặc 2).
     * Cập nhật indicator + ẩn/hiện layout tương ứng.
     */
    private void goToStep(int step) {
        currentStep = step;

        // Ẩn tất cả step
        if (layoutStep1 != null) layoutStep1.setVisibility(View.GONE);
        if (layoutStep2 != null) layoutStep2.setVisibility(View.GONE);

        // Reset màu indicator — dùng color resource thay vì hardcode
        int activeColor = getResources().getColor(R.color.lg_accent_primary, getTheme());
        int dimColor = getResources().getColor(R.color.lg_text_dim, getTheme());

        if (tvStep1 != null) tvStep1.setTextColor(step >= 1 ? activeColor : dimColor);
        if (tvStep2 != null) tvStep2.setTextColor(step >= 2 ? activeColor : dimColor);

        // Hiện step hiện tại
        switch (step) {
            case 1: if (layoutStep1 != null) layoutStep1.setVisibility(View.VISIBLE); break;
            case 2: if (layoutStep2 != null) layoutStep2.setVisibility(View.VISIBLE); break;
        }
    }

    /**
     * Reset wizard về trạng thái ban đầu.
     */
    private void resetSendWizard() {
        // Xóa sạch hiệu ứng Showcase của thẻ cũ (Glow, Shimmer)
        View viewCardShimmer = cvSelectedCard.findViewById(R.id.view_card_shimmer);
        com.vn.jet.mosco.utils.CardEffectHelper.remove(cvSelectedCard, viewCardShimmer);

        if (cvSuccessSelectedCard != null) {
            View viewSuccessShimmer = cvSuccessSelectedCard.findViewById(R.id.view_card_shimmer);
            com.vn.jet.mosco.utils.CardEffectHelper.remove(cvSuccessSelectedCard, viewSuccessShimmer);
        }

        cvSelectCardBtn.setVisibility(View.VISIBLE);
        cvSelectedCard.setVisibility(View.GONE);
        btnStep1Next.setVisibility(View.GONE);
        if (btnStep2Next != null) btnStep2Next.setVisibility(View.GONE);
        if (etSearchFriend != null) etSearchFriend.setText("");
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


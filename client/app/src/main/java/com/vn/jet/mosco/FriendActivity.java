package com.vn.jet.mosco;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.vn.jet.mosco.fragment.FriendListFragment;
import com.vn.jet.mosco.fragment.FriendRequestFragment;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Quản lý Bạn bè — TabLayout + ViewPager2 giống CollectionFragment.
 * 2 Tab: FRIENDS (danh sách bạn), REQUESTS (lời mời đang chờ).
 * Search bar cho phép tìm và gửi lời mời kết bạn.
 */
public class FriendActivity extends MoscoBaseActivity {

    private static final String TAG = "FriendActivity";
    private GameApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend);

        apiService = ApiClient.getClient(this).create(GameApiService.class);
        com.vn.jet.mosco.utils.DatabaseLoader.initMasterData(this);

        // Nút back & Title
        findViewById(R.id.btn_back_common).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tv_header_title)).setText(R.string.social_header_friends);

        // Setup Tab — giống Collection
        TabLayout tabLayout = findViewById(R.id.tab_layout_friend);
        ViewPager2 viewPager = findViewById(R.id.view_pager_friend);

        FriendPagerAdapter adapter = new FriendPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setUserInputEnabled(false);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText(getString(R.string.social_tab_explore)); break;
                case 1: tab.setText(getString(R.string.social_tab_friends)); break;
                case 2: tab.setText(getString(R.string.social_tab_requests)); break;
            }
        }).attach();

        // Lập trình thay đổi động chuỗi gợi ý (hint) và làm sạch thanh tìm kiếm khi chuyển Tab
        // Lý do (WHY): Giúp người chơi nhận biết rõ ràng bối cảnh tra cứu hiện tại, tự động thiết lập lại danh sách về trạng thái đầy đủ ban đầu
        EditText etSearch = findViewById(R.id.et_search_friend);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (etSearch != null) {
                    // Tạm thời gỡ bỏ chuỗi text hiện tại để tránh kích hoạt bộ lọc chéo không mong muốn
                    etSearch.setText("");
                    switch (position) {
                        case 0:
                            etSearch.setHint(R.string.social_search_hint_explore);
                            break;
                        case 1:
                            etSearch.setHint(R.string.social_search_hint_friends);
                            break;
                        case 2:
                            etSearch.setHint(R.string.social_search_hint_requests);
                            break;
                    }
                }
            }
        });

        // Setup Search — lọc danh sách cũ hoặc tìm user mới
        setupSearch();

        // Setup QR Code Button
        findViewById(R.id.btn_friend_qr).setOnClickListener(v -> showGalacticIdDialog());
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            com.vn.jet.mosco.utils.NavigationUtils.handleBackPress();
            getSupportFragmentManager().popBackStack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Hiện Dialog thẻ căn cước thiên hà của Sếp kèm mã QR.
     */
    private void showGalacticIdDialog() {
        com.vn.jet.mosco.widget.MoscoQrDialog.show(this);
    }

    /**
     * Thiết lập thanh tìm kiếm: Real-time filtering cho bạn cũ, Enter/Search cho bạn mới.
     */
    private void setupSearch() {
        EditText etSearch = findViewById(R.id.et_search_friend);
        if (etSearch == null) return;

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            private java.util.Timer timer = new java.util.Timer();
            private final long DELAY = 300; 

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                ViewPager2 viewPager = findViewById(R.id.view_pager_friend);
                // Kiểm tra nếu Tab hiện tại là Explore (Vị trí 0), tuyệt đối chặn không gửi truy vấn tự động
                // Tại sao (WHY): Ngăn chặn việc gửi hàng loạt request API lên máy chủ mỗi khi thay đổi ký tự, bảo vệ Backend khỏi nguy cơ sập tải (Flooding).
                // Tìm kiếm API trên Tab Khám phá sẽ chỉ được thực thi duy nhất khi người dùng chủ động nhấn nút Search/Enter trên bàn phím.
                if (viewPager != null && viewPager.getCurrentItem() == 0) {
                    return;
                }

                // Tab Bạn bè và Lời mời tiếp tục áp dụng lọc Real-time nội bộ mượt mà
                timer.cancel();
                timer = new java.util.Timer();
                timer.schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> handleSearch(s.toString()));
                    }
                }, DELAY);
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = etSearch.getText().toString().trim();
                handleGlobalSearch(query);
                return true;
            }
            return false;
        });
    }

    private void handleSearch(String query) {
        if (query == null) return;
        handleGlobalSearch(query);
    }

    /**
     * Phân phối luồng xử lý từ khóa độc lập dựa trên ngữ cảnh Tab hiện tại.
     * Lý do (WHY): Tránh tình trạng tự động chuyển trang gây mất phương hướng, cho phép tra cứu thời gian thực mượt mà trên danh sách bạn bè và lời mời.
     */
    private void handleGlobalSearch(String query) {
        ViewPager2 viewPager = findViewById(R.id.view_pager_friend);
        if (viewPager == null) return;

        int currentPosition = viewPager.getCurrentItem();
        Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + currentPosition);

        if (currentFragment != null) {
            switch (currentPosition) {
                case 0: // Tab Khám phá (Explore)
                    if (!query.trim().isEmpty() && currentFragment instanceof com.vn.jet.mosco.fragment.FriendSearchFragment) {
                        ((com.vn.jet.mosco.fragment.FriendSearchFragment) currentFragment).performSearch(query.trim());
                    }
                    break;
                case 1: // Tab Bạn bè (Friends)
                    if (currentFragment instanceof com.vn.jet.mosco.fragment.FriendListFragment) {
                        ((com.vn.jet.mosco.fragment.FriendListFragment) currentFragment).filterFriends(query.trim());
                    }
                    break;
                case 2: // Tab Lời mời (Requests)
                    if (currentFragment instanceof com.vn.jet.mosco.fragment.FriendRequestFragment) {
                        ((com.vn.jet.mosco.fragment.FriendRequestFragment) currentFragment).filterRequests(query.trim());
                    }
                    break;
            }
        }
    }

    /**
     * Tìm kiếm user và hiện dialog xác nhận gửi lời mời.
     */
    private void searchAndAddFriend(String query) {
        apiService.searchUsers(query).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray data = json.optJSONArray("data");

                        if (data != null && data.length() > 0) {
                            // Lấy kết quả đầu tiên
                            JSONObject firstResult = data.getJSONObject(0);
                            Long targetId = firstResult.optLong("userId");
                            String targetName = firstResult.optString("ingameName", "Unknown");

                            // Hiện dialog xác nhận
                            new androidx.appcompat.app.AlertDialog.Builder(FriendActivity.this)
                                    .setTitle(R.string.social_dialog_add_friend_title)
                                    .setMessage(getString(R.string.social_dialog_add_friend_msg, targetName))
                                    .setPositiveButton(R.string.social_dialog_action_send, (d, w) -> sendFriendRequest(targetId))
                                    .setNegativeButton(R.string.action_cancel, null)
                                    .show();
                        } else {
                            Toast.makeText(FriendActivity.this, R.string.social_msg_player_not_found, Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi search", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(FriendActivity.this, R.string.common_error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Tra cứu thông tin người dùng từ ID quét được qua mã QR hoặc Deep Link và hiển thị xem trước.
     * Xử lý bất đồng bộ, đảm bảo an toàn luồng và kiểm tra null nghiêm ngặt.
     */
    public void fetchAndShowProfile(String userIdStr) {
        if (userIdStr == null || userIdStr.trim().isEmpty()) return;
        Toast.makeText(this, R.string.social_msg_opening_galactic_id, Toast.LENGTH_SHORT).show();
        
        apiService.searchUsers(userIdStr.trim()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray data = json.optJSONArray("data");

                        if (data != null && data.length() > 0) {
                            // Tìm chính xác user có ID tương ứng để tránh nhầm lẫn kết quả tra cứu gần đúng
                            JSONObject targetUser = null;
                            long targetId = Long.parseLong(userIdStr.trim());
                            for (int i = 0; i < data.length(); i++) {
                                JSONObject u = data.getJSONObject(i);
                                if (u.optLong("userId", -1) == targetId) {
                                    targetUser = u;
                                    break;
                                }
                            }
                            if (targetUser == null) {
                                targetUser = data.getJSONObject(0);
                            }
                            // Sử dụng lại toàn bộ màn hình ProfileFragment cao cấp đã có sẵn thay vì tạo mới bản xem trước
                            com.vn.jet.mosco.utils.NavigationUtils.openProfile(FriendActivity.this, targetUser.optLong("userId"));
                        } else {
                            Toast.makeText(FriendActivity.this, R.string.social_msg_player_not_found, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(FriendActivity.this, R.string.social_msg_player_not_found, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi phân tích dữ liệu tra cứu QR", e);
                    Toast.makeText(FriendActivity.this, R.string.common_error_unknown, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(FriendActivity.this, R.string.common_error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Gửi lời mời kết bạn qua API.
     */
    private void sendFriendRequest(Long addresseeId) {
        Map<String, Long> body = new HashMap<>();
        body.put("addresseeId", addresseeId);

        apiService.addFriend(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String msg;
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        msg = json.optString("message", getString(R.string.social_msg_request_sent));
                    } else if (response.errorBody() != null) {
                        JSONObject json = new JSONObject(response.errorBody().string());
                        msg = json.optString("message", getString(R.string.social_msg_request_error));
                    } else {
                        msg = getString(R.string.common_error_unknown);
                    }
                    Toast.makeText(FriendActivity.this, msg, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi gửi lời mời", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(FriendActivity.this, R.string.common_error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Hiển thị BottomSheet xem trước thông tin người dùng (Phiên bản V2 - Tinh chỉnh).
     */
    public void showUserProfile(JSONObject user) {
        if (user == null) return;

        com.google.android.material.bottomsheet.BottomSheetDialog dialog = 
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.GalacticBottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.layout_user_profile_preview, null);
        dialog.setContentView(view);

        // Cấu hình chiều cao BottomSheet
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior = 
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            int targetHeight = (int) (screenHeight * 0.85); // 85% cho sang
            
            bottomSheet.getLayoutParams().height = targetHeight;
            behavior.setPeekHeight(targetHeight);
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        }

        // Ánh xạ views - V5.0 ULTRA MINIMALIST
        ImageView ivAvatar = view.findViewById(R.id.iv_preview_avatar);
        TextView tvName = view.findViewById(R.id.tv_preview_name);
        TextView tvLevel = view.findViewById(R.id.tv_info_level);
        TextView tvObjets = view.findViewById(R.id.tv_info_objets);
        TextView tvId = view.findViewById(R.id.tv_info_id);
        TextView tvJoinDate = view.findViewById(R.id.tv_info_join_date);
        View viewStatusDot = view.findViewById(R.id.view_preview_status_dot);

        // Đổ dữ liệu
        String name = user.optString("ingameName", getString(R.string.profile_preview_default_name));
        long id = user.optLong("userId", 0);
        int level = user.optInt("level", 1);
        boolean isOnline = user.optBoolean("online", false);

        tvName.setText(name.toUpperCase());
        tvLevel.setText(String.valueOf(level));
        tvObjets.setText(String.valueOf(user.optInt("objetsCount", 42))); 
        tvId.setText(String.valueOf(10000000 + id));
        
        String rawDate = user.optString("createdAt", getString(R.string.placeholder_empty));
        tvJoinDate.setText(rawDate.split("T")[0]);

        // Status logic
        viewStatusDot.setBackgroundResource(isOnline ? R.drawable.bg_status_online : R.drawable.bg_dot_inactive);

        // Avatar
        String avatarId = user.optString("avatarId", "1");
        JSONObject card = com.vn.jet.mosco.utils.DatabaseLoader.findByCollectionId(this, avatarId);
        if (card != null) {
            String imgUrl = card.optString("frontImage", "");
            com.bumptech.glide.Glide.with(this)
                    .load(imgUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .transform(new com.vn.jet.mosco.utils.SmartFaceCropTransformation(imgUrl))
                    .placeholder(R.drawable.ic_user)
                    .into(ivAvatar);
        }

        // Main Action Button
        androidx.appcompat.widget.AppCompatButton btnAction = view.findViewById(R.id.btn_profile_action_main);
        boolean isFriend = user.optBoolean("isFriend", false); 
        btnAction.setText(isFriend ? getString(R.string.profile_v3_btn_unfriend) : getString(R.string.profile_v3_btn_add));
        
        btnAction.setOnClickListener(v -> {
            if (isFriend) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.profile_preview_unfriend_title)
                    .setMessage(getString(R.string.profile_preview_unfriend_msg, name))
                    .setPositiveButton(R.string.profile_preview_btn_remove, (d, w) -> {
                         Toast.makeText(this, getString(R.string.profile_preview_unfriend_success, name), Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            } else {
                sendFriendRequest(id);
            }
            dialog.dismiss();
        });

        view.findViewById(R.id.btn_preview_message_small).setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.profile_preview_msg_chat_coming, name), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }



    /**
     * PagerAdapter — 2 Tab con giống Collection.
     */
    private static class FriendPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {
        public FriendPagerAdapter(@NonNull AppCompatActivity activity) { super(activity); }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new com.vn.jet.mosco.fragment.FriendSearchFragment();
                case 1: return new FriendListFragment();
                case 2: return new FriendRequestFragment();
                default: return new com.vn.jet.mosco.fragment.FriendSearchFragment();
            }
        }

        @Override
        public int getItemCount() { return 3; }
    }
}

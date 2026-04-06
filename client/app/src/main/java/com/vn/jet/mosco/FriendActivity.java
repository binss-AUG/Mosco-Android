package com.vn.jet.mosco;

import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
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
public class FriendActivity extends AppCompatActivity {

    private static final String TAG = "FriendActivity";
    private GameApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend);

        apiService = ApiClient.getClient(this).create(GameApiService.class);

        // Nút back
        findViewById(R.id.btn_back_friend).setOnClickListener(v -> finish());

        // Setup Tab — giống Collection
        TabLayout tabLayout = findViewById(R.id.tab_layout_friend);
        ViewPager2 viewPager = findViewById(R.id.view_pager_friend);

        FriendPagerAdapter adapter = new FriendPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setUserInputEnabled(false);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("FRIENDS"); break;
                case 1: tab.setText("REQUESTS"); break;
            }
        }).attach();

        // Setup Search — tìm user và gửi lời mời
        setupSearch();
    }

    /**
     * Thiết lập thanh tìm kiếm: nhấn Enter gửi lời mời kết bạn theo ID hoặc tên.
     */
    private void setupSearch() {
        EditText etSearch = findViewById(R.id.et_search_friend);
        if (etSearch == null) return;

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = etSearch.getText().toString().trim();
                if (!query.isEmpty()) {
                    searchAndAddFriend(query);
                }
                return true;
            }
            return false;
        });
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
                                    .setTitle("Add Friend")
                                    .setMessage("Send friend request to " + targetName + "?")
                                    .setPositiveButton("Send", (d, w) -> sendFriendRequest(targetId))
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        } else {
                            Toast.makeText(FriendActivity.this, "Player not found", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi search", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(FriendActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
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
                        msg = json.optString("message", "Friend request sent!");
                    } else if (response.errorBody() != null) {
                        JSONObject json = new JSONObject(response.errorBody().string());
                        msg = json.optString("message", "Could not send request");
                    } else {
                        msg = "Unknown error";
                    }
                    Toast.makeText(FriendActivity.this, msg, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi gửi lời mời", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(FriendActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * PagerAdapter — 2 Tab con giống Collection.
     */
    private static class FriendPagerAdapter extends FragmentStateAdapter {
        public FriendPagerAdapter(@NonNull AppCompatActivity activity) { super(activity); }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new FriendListFragment();
                case 1: return new FriendRequestFragment();
                default: return new FriendListFragment();
            }
        }

        @Override
        public int getItemCount() { return 2; }
    }
}

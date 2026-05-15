package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.adapter.FriendAdapter;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Tab "FRIENDS" — Hiển thị danh sách bạn bè đã kết nối áp dụng chiến lược AAA Caching 2 phút và Cooldown 15 giây.
 */
public class FriendListFragment extends Fragment {

    private static final String TAG = "FriendListFragment";
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvFriends;
    private ShimmerFrameLayout shimmerSkeleton;
    private View layoutEmpty;
    private TextView tvEmpty;
    private FriendAdapter adapter;

    // Bộ nhớ đệm danh sách bạn bè và quản lý tuổi thọ Cache
    private List<JSONObject> cachedFriends = new ArrayList<>();
    private long lastUpdatedTime = 0;
    private static final long CACHE_EXPIRY = 2 * 60 * 1000; // 2 phút cập nhật 1 lần

    // Quản lý trạng thái khóa hồi chiêu của tính năng kéo làm mới
    private static boolean isCooldownActive = false;
    private static long cooldownEndTime = 0;
    private CountDownTimer cooldownTimer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        swipeRefresh = view.findViewById(R.id.swipe_refresh_friend_list);
        rvFriends = view.findViewById(R.id.rv_friend_list);
        shimmerSkeleton = view.findViewById(R.id.shimmer_friend_skeleton);
        layoutEmpty = view.findViewById(R.id.layout_friend_empty);
        tvEmpty = view.findViewById(R.id.tv_friend_empty);

        rvFriends.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FriendAdapter(new ArrayList<>());
        rvFriends.setAdapter(adapter);
        
        // Thiết lập bộ lắng nghe thao tác vuốt làm mới danh sách
        // Tại sao (WHY): Ngăn chặn việc spam request liên tục từ phía Client gây quá tải hệ thống, đồng thời cho phép người dùng chủ động vượt qua bộ nhớ đệm khi cần cập nhật tức thời.
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(android.R.color.holo_orange_light, android.R.color.holo_red_light);
            swipeRefresh.setOnRefreshListener(() -> {
                if (isCooldownActive) {
                    swipeRefresh.setRefreshing(false);
                    long remainingSeconds = (cooldownEndTime - System.currentTimeMillis()) / 1000;
                    if (remainingSeconds < 1) remainingSeconds = 1;
                    if (isAdded()) {
                        Toast.makeText(requireContext(), getString(R.string.social_msg_pull_cooldown, remainingSeconds), Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                
                swipeRefresh.setRefreshing(false);
                startRefreshCooldown();
                
                // Đặt lại thời gian Cache để bắt buộc tải mới từ Server
                lastUpdatedTime = 0;
                
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                if (rvFriends != null) rvFriends.setVisibility(View.GONE);
                if (shimmerSkeleton != null) {
                    shimmerSkeleton.setVisibility(View.VISIBLE);
                    shimmerSkeleton.startShimmer();
                }
                loadFriendListInternal();
            });
        }

        loadFriendList();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFriendList();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cooldownTimer != null) {
            cooldownTimer.cancel();
        }
    }

    /**
     * Kích hoạt đếm ngược thời gian hồi chiêu làm mới.
     */
    private void startRefreshCooldown() {
        isCooldownActive = true;
        cooldownEndTime = System.currentTimeMillis() + 15000;
        
        if (cooldownTimer != null) cooldownTimer.cancel();
        cooldownTimer = new CountDownTimer(15000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                isCooldownActive = false;
            }
        }.start();
    }

    /**
     * Điều phối tải danh sách, ưu tiên sử dụng bộ nhớ đệm.
     */
    private void loadFriendList() {
        long currentTime = System.currentTimeMillis();
        boolean isCacheValid = !cachedFriends.isEmpty() && (currentTime - lastUpdatedTime) <= CACHE_EXPIRY;

        // Tại sao (WHY): Mang lại trải nghiệm chuyển Tab tức thì (Local-First UX) với độ trễ 0ms, tiết kiệm băng thông và tài nguyên hệ thống.
        if (isCacheValid) {
            if (shimmerSkeleton != null) {
                shimmerSkeleton.stopShimmer();
                shimmerSkeleton.setVisibility(View.GONE);
            }
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
            if (rvFriends != null) rvFriends.setVisibility(View.VISIBLE);
            adapter.updateData(cachedFriends);
            return;
        }

        if (shimmerSkeleton != null) {
            shimmerSkeleton.setVisibility(View.VISIBLE);
            shimmerSkeleton.startShimmer();
        }
        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        if (rvFriends != null) rvFriends.setVisibility(View.GONE);
        loadFriendListInternal();
    }

    /**
     * Thực thi kết nối mạng tải bạn bè.
     */
    private void loadFriendListInternal() {
        if (requireContext() == null) return;
        GameApiService api = ApiClient.getClient(requireContext()).create(GameApiService.class);

        api.getFriendList().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                stopLoadingIndicators();
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray data = json.optJSONArray("data");

                        if (data != null && data.length() > 0) {
                            cachedFriends.clear();
                            for (int i = 0; i < data.length(); i++) {
                                JSONObject friend = data.getJSONObject(i);
                                friend.put("isFriend", true);
                                cachedFriends.add(friend);
                            }
                            lastUpdatedTime = System.currentTimeMillis();
                            adapter.updateData(cachedFriends);
                            
                            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                            if (rvFriends != null) rvFriends.setVisibility(View.VISIBLE);
                        } else {
                            cachedFriends.clear();
                            handleEmptyState();
                        }
                    } else {
                        handleEmptyState();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading friend list", e);
                    handleEmptyState();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                stopLoadingIndicators();
                Log.e(TAG, "Connection error", t);
                handleEmptyState();
            }
        });
    }

    /**
     * Dừng êm ái hoạt ứng Shimmer.
     */
    private void stopLoadingIndicators() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        if (shimmerSkeleton != null) {
            shimmerSkeleton.stopShimmer();
            shimmerSkeleton.setVisibility(View.GONE);
        }
    }

    /**
     * Hiển thị thông điệp Fallback chất thơ khi rỗng.
     */
    private void handleEmptyState() {
        if (layoutEmpty != null && isAdded()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            if (tvEmpty != null) {
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText(getString(R.string.social_friend_list_empty));
            }
            if (rvFriends != null) rvFriends.setVisibility(View.GONE);
        }
    }

    /**
     * Lọc danh sách bạn bè hiện tại theo từ khóa.
     */
    public void filterFriends(String query) {
        // Tại sao (WHY): Ngăn ngừa sự xáo trộn hiển thị giao diện rỗng khi các dải Skeleton Loading vẫn đang thực thi nhiệm vụ báo tải.
        if (shimmerSkeleton != null && shimmerSkeleton.getVisibility() == View.VISIBLE) {
            return;
        }
        if (adapter != null) {
            adapter.filter(query);
            if (adapter.getItemCount() == 0 && !query.isEmpty()) {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                if (tvEmpty != null) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText(getString(R.string.social_msg_no_matches));
                }
            } else if (adapter.getItemCount() > 0) {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
            }
        }
    }
}

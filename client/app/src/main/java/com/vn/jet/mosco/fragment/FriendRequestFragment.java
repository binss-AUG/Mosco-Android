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
import com.vn.jet.mosco.FriendActivity;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.adapter.FriendRequestAdapter;
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
 * Tab "REQUESTS" — Hiển thị danh sách lời mời kết bạn tích hợp bộ nhớ đệm Caching 2 phút, hồi chiêu 15 giây và tự động làm sạch khi có thay đổi.
 */
public class FriendRequestFragment extends Fragment implements FriendRequestAdapter.OnRequestActionListener {

    private static final String TAG = "FriendRequestFragment";
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvRequests;
    private ShimmerFrameLayout shimmerSkeleton;
    private View layoutEmpty;
    private TextView tvEmpty;
    private FriendRequestAdapter adapter;
    private GameApiService apiService;

    // Bộ nhớ đệm lời mời kết bạn và quản lý tuổi thọ Cache
    private List<JSONObject> cachedRequests = new ArrayList<>();
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
        rvRequests = view.findViewById(R.id.rv_friend_list);
        shimmerSkeleton = view.findViewById(R.id.shimmer_friend_skeleton);
        layoutEmpty = view.findViewById(R.id.layout_friend_empty);
        tvEmpty = view.findViewById(R.id.tv_friend_empty);

        if (tvEmpty != null) {
            tvEmpty.setText(getString(R.string.social_msg_no_requests));
        }

        rvRequests.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FriendRequestAdapter(new ArrayList<>(), this);
        rvRequests.setAdapter(adapter);

        if (requireContext() != null) {
            apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        }

        // Cài đặt cơ chế vuốt làm mới
        // Tại sao (WHY): Giảm thiểu số lượng request trùng lặp gửi về máy chủ, đồng thời cung cấp phương thức ngắt Cache khi người dùng muốn cập nhật trạng thái mới nhất.
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
                
                // Đặt lại thời gian Cache để tải mới hoàn toàn
                lastUpdatedTime = 0;
                
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                if (rvRequests != null) rvRequests.setVisibility(View.GONE);
                if (shimmerSkeleton != null) {
                    shimmerSkeleton.setVisibility(View.VISIBLE);
                    shimmerSkeleton.startShimmer();
                }
                loadRequestsInternal();
            });
        }

        loadRequests();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRequests();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cooldownTimer != null) {
            cooldownTimer.cancel();
        }
    }

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
     * Điều phối tải danh sách lời mời, ưu tiên bộ nhớ đệm.
     */
    private void loadRequests() {
        long currentTime = System.currentTimeMillis();
        boolean isCacheValid = !cachedRequests.isEmpty() && (currentTime - lastUpdatedTime) <= CACHE_EXPIRY;

        // Tại sao (WHY): Giúp các tab chuyển đổi qua lại tức thì mà không gây chớp giật hay gọi API dư thừa.
        if (isCacheValid) {
            if (shimmerSkeleton != null) {
                shimmerSkeleton.stopShimmer();
                shimmerSkeleton.setVisibility(View.GONE);
            }
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
            if (rvRequests != null) rvRequests.setVisibility(View.VISIBLE);
            adapter.updateData(cachedRequests);
            return;
        }

        if (shimmerSkeleton != null) {
            shimmerSkeleton.setVisibility(View.VISIBLE);
            shimmerSkeleton.startShimmer();
        }
        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (rvRequests != null) rvRequests.setVisibility(View.GONE);
        loadRequestsInternal();
    }

    /**
     * Thực thi gọi API ngầm định.
     */
    private void loadRequestsInternal() {
        if (apiService == null || requireContext() == null) return;

        apiService.getFriendRequests().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                stopLoadingIndicators();
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray data = json.optJSONArray("data");

                        if (data != null && data.length() > 0) {
                            cachedRequests.clear();
                            for (int i = 0; i < data.length(); i++) {
                                cachedRequests.add(data.getJSONObject(i));
                            }
                            lastUpdatedTime = System.currentTimeMillis();
                            adapter.updateData(cachedRequests);
                            
                            if (isAdded() && getActivity() instanceof FriendActivity) {
                                ((FriendActivity) getActivity()).updateRequestBadge(cachedRequests.size());
                            }
                            
                            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                            if (rvRequests != null) rvRequests.setVisibility(View.VISIBLE);
                        } else {
                            cachedRequests.clear();
                            handleEmptyState();
                        }
                    } else {
                        handleEmptyState();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading requests", e);
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
     * Ngắt trơn tru hiệu ứng khung xương.
     */
    private void stopLoadingIndicators() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        if (shimmerSkeleton != null) {
            shimmerSkeleton.stopShimmer();
            shimmerSkeleton.setVisibility(View.GONE);
        }
    }

    /**
     * Xử lý hiển thị thông báo rỗng.
     */
    private void handleEmptyState() {
        if (isAdded()) {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (tvEmpty != null) {
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText(getString(R.string.social_msg_no_requests));
            }
            if (rvRequests != null) rvRequests.setVisibility(View.GONE);

            if (getActivity() instanceof FriendActivity) {
                ((FriendActivity) getActivity()).updateRequestBadge(0);
            }
        }
    }

    /**
     * Lọc danh sách lời mời hiện tại theo từ khóa.
     */
    public void filterRequests(String query) {
        // Tại sao (WHY): Ngăn chặn việc trạng thái trống xuất hiện chồng lấn trong thời gian dải Skeleton Loading đang nhấp nháy phát sáng.
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
                if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
            } else {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                if (tvEmpty != null) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText(getString(R.string.social_msg_no_requests));
                }
            }
        }
    }

    @Override
    public void onAccept(Long friendshipId) {
        if (apiService == null) return;

        apiService.acceptFriend(friendshipId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (requireContext() == null) return;
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), getString(R.string.social_msg_request_accepted), Toast.LENGTH_SHORT).show();
                    // Tại sao (WHY): Xóa bộ nhớ đệm để bắt buộc danh sách tải lại trạng thái mới nhất ngay lập tức.
                    lastUpdatedTime = 0;
                    loadRequests();
                } else {
                    Toast.makeText(requireContext(), getString(R.string.social_error_accept), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (requireContext() != null) {
                    Toast.makeText(requireContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onReject(Long friendshipId) {
        if (apiService == null) return;

        apiService.removeFriend(friendshipId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (requireContext() == null) return;
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), getString(R.string.social_msg_request_declined), Toast.LENGTH_SHORT).show();
                    // Tại sao (WHY): Làm sạch bộ nhớ đệm để phản ánh sự thay đổi kết nối ngay lập tức.
                    lastUpdatedTime = 0;
                    loadRequests();
                } else {
                    Toast.makeText(requireContext(), getString(R.string.social_error_decline), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (requireContext() != null) {
                    Toast.makeText(requireContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}

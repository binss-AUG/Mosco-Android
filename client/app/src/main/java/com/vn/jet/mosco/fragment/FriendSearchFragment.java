package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
 * Tab "EXPLORE" — Tìm kiếm toàn cầu để kết bạn mới tối ưu hóa giao diện thuần Skeleton Shimmer sang trọng.
 */
public class FriendSearchFragment extends Fragment {

    private static final String TAG = "FriendSearchFragment";
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvResults;
    private ShimmerFrameLayout shimmerSkeleton;
    private View layoutEmpty;
    private FriendAdapter adapter;
    private GameApiService apiService;

    // Quản lý trạng thái khóa hồi chiêu của tính năng kéo làm mới
    private static boolean isCooldownActive = false;
    private static long cooldownEndTime = 0;
    private CountDownTimer cooldownTimer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend_explore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        swipeRefresh = view.findViewById(R.id.swipe_refresh_explore);
        rvResults = view.findViewById(R.id.rv_search_results);
        shimmerSkeleton = view.findViewById(R.id.shimmer_friend_skeleton);
        layoutEmpty = view.findViewById(R.id.layout_search_empty);

        rvResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FriendAdapter(new ArrayList<>());
        rvResults.setAdapter(adapter);

        apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);

        // Thiết lập bộ lắng nghe thao tác vuốt kéo làm mới
        // Tại sao (WHY): Đồng nhất hiệu ứng phản hồi trơn tru và trực quan duy nhất qua danh sách khung xương, loại bỏ triệt để sự rối rắm về mặt hiệu ứng đồ họa.
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
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                if (rvResults != null) rvResults.setVisibility(View.GONE);
                if (shimmerSkeleton != null) {
                    shimmerSkeleton.setVisibility(View.VISIBLE);
                    shimmerSkeleton.startShimmer();
                }
                startRefreshCooldown();
                loadExploreSuggestionsInternal();
            });
        }

        loadExploreSuggestions();
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
     * Tải danh sách gợi ý ban đầu từ API.
     */
    private void loadExploreSuggestions() {
        if (shimmerSkeleton != null) {
            shimmerSkeleton.setVisibility(View.VISIBLE);
            shimmerSkeleton.startShimmer();
        }
        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        if (rvResults != null) rvResults.setVisibility(View.GONE);
        loadExploreSuggestionsInternal();
    }

    /**
     * Thực thi gọi API ngầm định.
     * Tại sao (WHY): Tách biệt tầng kết nối mạng khỏi các hiệu ứng giao diện nhằm đảm bảo độ phản hồi ổn định.
     */
    private void loadExploreSuggestionsInternal() {
        if (apiService == null || requireContext() == null) return;

        apiService.getExploreSuggestions().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                stopLoadingIndicators();
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray dataArray = json.optJSONArray("data");

                        if (dataArray != null && dataArray.length() > 0) {
                            List<JSONObject> results = new ArrayList<>();
                            for (int i = 0; i < dataArray.length(); i++) {
                                results.add(dataArray.getJSONObject(i));
                            }
                            adapter.updateData(results);
                            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                            if (rvResults != null) rvResults.setVisibility(View.VISIBLE);
                        } else {
                            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                        }
                    } else {
                        if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Load suggestions error", e);
                    if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                stopLoadingIndicators();
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                if (isAdded()) {
                    Toast.makeText(requireContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void stopLoadingIndicators() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        if (shimmerSkeleton != null) {
            shimmerSkeleton.stopShimmer();
            shimmerSkeleton.setVisibility(View.GONE);
        }
    }

    /**
     * Thực hiện tìm kiếm toàn cầu khi gõ từ khóa.
     */
    public void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) return;

        if (shimmerSkeleton != null) {
            shimmerSkeleton.setVisibility(View.VISIBLE);
            shimmerSkeleton.startShimmer();
        }
        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        if (rvResults != null) rvResults.setVisibility(View.GONE);

        apiService.searchUsers(query).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                stopLoadingIndicators();
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray dataArray = json.optJSONArray("data");

                        if (dataArray != null && dataArray.length() > 0) {
                            List<JSONObject> results = new ArrayList<>();
                            for (int i = 0; i < dataArray.length(); i++) {
                                results.add(dataArray.getJSONObject(i));
                            }
                            adapter.updateData(results);
                            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                            if (rvResults != null) rvResults.setVisibility(View.VISIBLE);
                        } else {
                            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                        }
                    } else {
                        if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Search error", e);
                    if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                stopLoadingIndicators();
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                if (isAdded()) {
                    Toast.makeText(requireContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}

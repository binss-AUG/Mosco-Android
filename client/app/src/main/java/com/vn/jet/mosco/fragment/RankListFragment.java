package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ConcatAdapter;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.adapter.RankAdapter;
import com.vn.jet.mosco.adapter.PodiumAdapter;
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
 * Fragment dùng chung cho 3 tab Rank: Level, OVR, Collection.
 * Nhận param "rankType" để quyết định gọi API nào.
 */
public class RankListFragment extends Fragment {

    private static final String TAG = "RankListFragment";
    private static final String ARG_RANK_TYPE = "rankType";

    private RecyclerView rvRankList;
    private TextView tvEmpty;
    private View lottieLoading;
    private PodiumAdapter podiumAdapter;
    private RankAdapter rankAdapter;
    private String rankType;
    private JSONObject myRankData;
    private List<JSONObject> cachedPodiumData = new ArrayList<>();
    private List<JSONObject> cachedRestData = new ArrayList<>();
    private boolean isDataLoaded = false;
    private long lastUpdatedTime = 0;
    private static final long CACHE_EXPIRY = 2 * 60 * 1000; // 2 phút cập nhật 1 lần

    /**
     * Factory method — tạo fragment với param loại rank.
     */
    public static RankListFragment newInstance(String rankType) {
        RankListFragment fragment = new RankListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RANK_TYPE, rankType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            rankType = getArguments().getString(ARG_RANK_TYPE, "level");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rank_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvRankList = view.findViewById(R.id.rv_rank_list);
        tvEmpty = view.findViewById(R.id.tv_rank_empty);
        lottieLoading = view.findViewById(R.id.lottie_rank_loading);

        rvRankList.setLayoutManager(new LinearLayoutManager(requireContext()));
        podiumAdapter = new PodiumAdapter(new ArrayList<>(), rankType);
        rankAdapter = new RankAdapter(new ArrayList<>(), rankType);
        rvRankList.setAdapter(new ConcatAdapter(podiumAdapter, rankAdapter));

    }

    @Override
    public void onResume() {
        super.onResume();
        
        long currentTime = System.currentTimeMillis();
        boolean isCacheExpired = (currentTime - lastUpdatedTime) > CACHE_EXPIRY;

        if (isDataLoaded && !isCacheExpired) {
            // AAA Strategy: Dữ liệu vẫn còn mới (dưới 2p), hiện luôn không gọi API
            showRankListWithAnimation();
        } else {
            // Cache hết hạn hoặc chưa có data, tiến hành fetch mới
            if (!isDataLoaded) {
                // Nếu chưa có gì thì ẩn đi để hiện loading
                if (rvRankList != null) {
                    rvRankList.setVisibility(View.INVISIBLE);
                    rvRankList.setAlpha(0f);
                }
            }
            loadRankData();
        }
    }

    /**
     * Ép buộc tải lại dữ liệu (Bỏ qua AAA Cache) — Dùng cho Pull Refresh.
     */
    public void refreshData() {
        isDataLoaded = false;
        lastUpdatedTime = 0;
        loadRankData();
    }

    private void showRankListWithAnimation() {
        if (rvRankList == null) return;
        
        // AAA Strategy: Show list immediately to avoid perceived lag
        rvRankList.setVisibility(View.VISIBLE);
        rvRankList.setAlpha(1f);
        
        // Only the Podium (Top 3) keeps its beautiful entrance animation
        if (podiumAdapter != null) podiumAdapter.resetAnimation();
        if (rankAdapter != null) rankAdapter.notifyDataSetChanged();
        
        // Update footer immediately
        if (getActivity() instanceof com.vn.jet.mosco.RankActivity) {
            ((com.vn.jet.mosco.RankActivity) getActivity()).updateMyRank(myRankData, rankType);
        } else if (getParentFragment() instanceof RankFragment) {
            ((RankFragment) getParentFragment()).updateMyRank(myRankData, rankType);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Do NOT setAdapter(null) here anymore. Keeping the adapter alive is key to AAA smoothness.
        // Just cancel current animations to avoid glitches
        if (rvRankList != null) {
            rvRankList.animate().cancel();
        }
        
        if (getActivity() instanceof com.vn.jet.mosco.RankActivity) {
            ((com.vn.jet.mosco.RankActivity) getActivity()).hideMyRank();
        } else if (getParentFragment() instanceof RankFragment) {
            // Có thể thêm hideMyRank vào RankFragment nếu cần
        }
    }

    /**
     * Gọi API rank tương ứng và cập nhật RecyclerView.
     */
    private void loadRankData() {
        if (requireContext() == null) return;

        GameApiService apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        Call<ResponseBody> call;

        // Chọn API dựa trên loại rank
        switch (rankType) {
            case "wealth":
                call = apiService.getRankByWealth();
                break;
            case "collection":
                call = apiService.getRankByCollection();
                break;
            case "streak":
                call = apiService.getRankByStreak();
                break;
            case "level":
            default:
                call = apiService.getRankByLevel();
                break;
        }

        if (lottieLoading != null) lottieLoading.setVisibility(View.VISIBLE);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (lottieLoading != null) lottieLoading.setVisibility(View.GONE);
                
                // Stop Pull Refresh UI
                if (getActivity() instanceof com.vn.jet.mosco.RankActivity) {
                    ((com.vn.jet.mosco.RankActivity) getActivity()).stopRefresh();
                } else if (getParentFragment() instanceof RankFragment) {
                    ((RankFragment) getParentFragment()).stopRefresh();
                }

                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseStr = response.body().string();
                        JSONObject rootJson = new JSONObject(responseStr);
                        JSONArray array = rootJson.optJSONArray("data");
                        
                        if (array == null) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvRankList.setVisibility(View.GONE);
                            return;
                        }

                        List<JSONObject> rankings = new java.util.ArrayList<>();
                        
                        Long currentUserId = null;
                        if (getActivity() instanceof com.vn.jet.mosco.RankActivity) {
                            currentUserId = ((com.vn.jet.mosco.RankActivity) getActivity()).getCurrentUserId();
                        }

                        // Xóa myRankData cũ
                        myRankData = null;

                        // Chỉ lấy Top 99
                        int limit = Math.min(array.length(), 99);
                        for (int i = 0; i < limit; i++) {
                            JSONObject obj = array.getJSONObject(i);
                            obj.put("rank", i + 1); // Gắn rank để adapter hiển thị
                            rankings.add(obj);

                            // Tìm sếp trong Top 99
                            if (currentUserId != null && obj.optLong("userId") == currentUserId) {
                                myRankData = obj;
                            }
                        }

                        if (!rankings.isEmpty()) {
                            lastUpdatedTime = System.currentTimeMillis(); // Đánh dấu thời gian cập nhật
                            cachedPodiumData.clear();
                            cachedRestData.clear();
                            
                            for (int j = 0; j < rankings.size(); j++) {
                                if (j < 3) {
                                    cachedPodiumData.add(rankings.get(j));
                                } else {
                                    cachedRestData.add(rankings.get(j));
                                }
                            }
                            
                            podiumAdapter.updateData(cachedPodiumData);
                            rankAdapter = new RankAdapter(cachedRestData, rankType, currentUserId);
                            rvRankList.setAdapter(new ConcatAdapter(podiumAdapter, rankAdapter));
                            
                            isDataLoaded = true;
                            showRankListWithAnimation();
                        } else {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvRankList.setVisibility(View.GONE);
                            if (getActivity() instanceof com.vn.jet.mosco.RankActivity) {
                                ((com.vn.jet.mosco.RankActivity) getActivity()).updateMyRank(null, rankType);
                            }
                        }
                    } else {
                        // Trường hợp API lỗi (404, 500...)
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvRankList.setVisibility(View.GONE);
                        if (getActivity() instanceof com.vn.jet.mosco.RankActivity) {
                            ((com.vn.jet.mosco.RankActivity) getActivity()).updateMyRank(null, rankType);
                        }
                        Log.e(TAG, "API trả về lỗi: " + response.code());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi parse rank data: " + rankType, e);
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvRankList.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (lottieLoading != null) lottieLoading.setVisibility(View.GONE);
                
                // Stop Pull Refresh UI
                if (getActivity() instanceof com.vn.jet.mosco.RankActivity) {
                    ((com.vn.jet.mosco.RankActivity) getActivity()).stopRefresh();
                } else if (getParentFragment() instanceof RankFragment) {
                    ((RankFragment) getParentFragment()).stopRefresh();
                }

                Log.e(TAG, "Lỗi kết nối API rank: " + rankType, t);
                if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                if (getActivity() instanceof com.vn.jet.mosco.RankActivity) {
                    ((com.vn.jet.mosco.RankActivity) getActivity()).updateMyRank(null, rankType);
                } else if (getParentFragment() instanceof RankFragment) {
                    ((RankFragment) getParentFragment()).updateMyRank(null, rankType);
                }
            }
        });
    }
}

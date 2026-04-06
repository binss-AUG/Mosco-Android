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

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.adapter.RankAdapter;
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
    private RankAdapter adapter;
    private String rankType;

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

        rvRankList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new RankAdapter(new ArrayList<>(), rankType);
        rvRankList.setAdapter(adapter);

        loadRankData();
    }

    /**
     * Gọi API rank tương ứng và cập nhật RecyclerView.
     */
    private void loadRankData() {
        if (getContext() == null) return;

        GameApiService apiService = ApiClient.getClient(getContext()).create(GameApiService.class);
        Call<ResponseBody> call;

        // Chọn API dựa trên loại rank
        switch (rankType) {
            case "ovr":
                call = apiService.getRankByOvr();
                break;
            case "collection":
                call = apiService.getRankByCollection();
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
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray data = json.optJSONArray("data");

                        if (data != null && data.length() > 0) {
                            List<JSONObject> rankings = new ArrayList<>();
                            for (int i = 0; i < data.length(); i++) {
                                rankings.add(data.getJSONObject(i));
                            }
                            adapter.updateData(rankings);
                            tvEmpty.setVisibility(View.GONE);
                            rvRankList.setVisibility(View.VISIBLE);
                        } else {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvRankList.setVisibility(View.GONE);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi parse rank data: " + rankType, e);
                    tvEmpty.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (lottieLoading != null) lottieLoading.setVisibility(View.GONE);
                Log.e(TAG, "Lỗi kết nối API rank: " + rankType, t);
                if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }
}

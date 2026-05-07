package com.vn.jet.mosco.fragment;

import android.os.Bundle;
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

import com.airbnb.lottie.LottieAnimationView;
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
 * Tab "EXPLORE" — Tìm kiếm toàn cầu để kết bạn mới.
 */
public class FriendSearchFragment extends Fragment {

    private static final String TAG = "FriendSearchFragment";
    private RecyclerView rvResults;
    private View layoutEmpty;
    private LottieAnimationView lottieLoading;
    private FriendAdapter adapter;
    private GameApiService apiService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend_explore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        rvResults = view.findViewById(R.id.rv_search_results);
        layoutEmpty = view.findViewById(R.id.layout_search_empty);
        lottieLoading = view.findViewById(R.id.lottie_search_loading);

        rvResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FriendAdapter(new ArrayList<>());
        rvResults.setAdapter(adapter);

        apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
    }

    /**
     * Thực hiện tìm kiếm toàn cầu qua API.
     */
    public void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) return;

        lottieLoading.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        rvResults.setVisibility(View.GONE);

        apiService.searchUsers(query).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                lottieLoading.setVisibility(View.GONE);
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
                            rvResults.setVisibility(View.VISIBLE);
                        } else {
                            layoutEmpty.setVisibility(View.VISIBLE);
                        }
                    } else {
                        layoutEmpty.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Search error", e);
                    layoutEmpty.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                lottieLoading.setVisibility(View.GONE);
                layoutEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), getString(R.string.common_error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }
}

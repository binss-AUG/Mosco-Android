package com.vn.jet.mosco.fragment;

import android.content.Context;
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
import com.vn.jet.mosco.adapter.GiftHistoryAdapter;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.view.InventoryFilterBar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.ResponseBody;

/**
 * Fragment quản lý Quà tặng nhận từ người chơi khác (Player Gifts).
 * Tách biệt hoàn toàn khỏi CollectionFragment (Decoupled & Standalone).
 */
public class PlayerGiftsFragment extends Fragment {

    private RecyclerView rvPlayerGifts;
    private GiftHistoryAdapter giftHistoryAdapter;
    private List<JSONObject> originalGifts = new ArrayList<>();
    private List<JSONObject> filteredGifts = new ArrayList<>();
    private InventoryFilterBar filterBar;
    private TextView tvPlayerGiftsCount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mailbox_player_gifts, container, false);

        rvPlayerGifts = view.findViewById(R.id.rv_player_gifts);
        filterBar = view.findViewById(R.id.filter_bar_player_gifts);
        tvPlayerGiftsCount = view.findViewById(R.id.tv_player_gifts_count);

        setupFilterBar();
        if (filterBar != null) {
            filterBar.setVisibility(View.GONE);
        }

        rvPlayerGifts.setLayoutManager(new LinearLayoutManager(getContext()));
        giftHistoryAdapter = new GiftHistoryAdapter(filteredGifts, true);
        rvPlayerGifts.setAdapter(giftHistoryAdapter);

        loadPlayerGifts();

        return view;
    }

    private void setupFilterBar() {
        if (filterBar == null) return;
        filterBar.setSortOptions(new String[] { 
            CollectionFragment.SORT_NEWEST, 
            CollectionFragment.SORT_LOWEST_NO, 
            CollectionFragment.SORT_HIGHEST_NO 
        });
        filterBar.setListener(new InventoryFilterBar.OnFilterChangeListener() {
            @Override
            public void onFilterChanged(String sortOption, boolean isAscending) {
                filterGifts();
            }

            @Override
            public void onFilterRequested() {
                // Filter handling
                filterGifts();
            }
        });
    }

    private void loadPlayerGifts() {
        Context context = getContext();
        if (context == null) return;

        GameApiService api = ApiClient.getClient(context).create(GameApiService.class);

        if (giftHistoryAdapter != null) {
            giftHistoryAdapter.setLoading(true);
        }

        api.getReceivedGifts().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray dataArr = json.optJSONArray("data");
                        originalGifts.clear();
                        if (dataArr != null) {
                            for (int i = 0; i < dataArr.length(); i++) {
                                originalGifts.add(dataArr.getJSONObject(i));
                            }
                        }
                        filterGifts();
                    }
                } catch (Exception e) {
                    Log.e("PlayerGiftsFragment", "Failed to parse received gifts", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("PlayerGiftsFragment", "Failed to load player gifts", t);
            }
        });
    }

    private void filterGifts() {
        filteredGifts.clear();
        String currentSort = filterBar != null ? filterBar.getSortOption() : CollectionFragment.SORT_NEWEST;
        
        for (JSONObject gift : originalGifts) {
            filteredGifts.add(gift);
        }
        
        // Sorting logic based on standard JSONObject
        filteredGifts.sort((a, b) -> {
            int res;
            if (CollectionFragment.SORT_LOWEST_NO.equals(currentSort)) {
                res = Integer.compare(a.optInt("quantity", 0), b.optInt("quantity", 0));
            } else if (CollectionFragment.SORT_HIGHEST_NO.equals(currentSort)) {
                res = Integer.compare(b.optInt("quantity", 0), a.optInt("quantity", 0));
            } else {
                res = Long.compare(b.optLong("id", 0), a.optLong("id", 0));
            }
            return filterBar != null && filterBar.isAscending() ? res : -res;
        });
        
        giftHistoryAdapter.updateData(filteredGifts);

        if (tvPlayerGiftsCount != null) {
            tvPlayerGiftsCount.setText(String.valueOf(filteredGifts.size()));
        }
    }
}

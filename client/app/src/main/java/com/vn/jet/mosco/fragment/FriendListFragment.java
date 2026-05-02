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
 * Tab "FRIENDS" — Shows accepted friends list with proper FriendAdapter.
 */
public class FriendListFragment extends Fragment {

    private static final String TAG = "FriendListFragment";
    private RecyclerView rvFriends;
    private View layoutEmpty;
    private com.airbnb.lottie.LottieAnimationView lottieEmpty;
    private TextView tvEmpty;
    private FriendAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvFriends = view.findViewById(R.id.rv_friend_list);
        layoutEmpty = view.findViewById(R.id.layout_friend_empty);
        lottieEmpty = view.findViewById(R.id.lottie_friend_empty);
        tvEmpty = view.findViewById(R.id.tv_friend_empty);
        
        if (lottieEmpty != null) {
            lottieEmpty.setAnimation(R.raw.loading);
            lottieEmpty.playAnimation();
        }

        rvFriends.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FriendAdapter(new ArrayList<>());
        rvFriends.setAdapter(adapter);
        loadFriendList();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFriendList();
    }

    /**
     * Load accepted friends from API.
     */
    private void loadFriendList() {
        if (getContext() == null) return;
        GameApiService api = ApiClient.getClient(getContext()).create(GameApiService.class);

        api.getFriendList().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray data = json.optJSONArray("data");

                        if (data != null && data.length() > 0) {
                            List<JSONObject> friends = new ArrayList<>();
                            for (int i = 0; i < data.length(); i++) {
                                JSONObject friend = data.getJSONObject(i);
                                friend.put("isFriend", true);
                                friends.add(friend);
                            }
                            adapter.updateData(friends);
                            layoutEmpty.setVisibility(View.GONE);
                            rvFriends.setVisibility(View.VISIBLE);
                        } else {
                            layoutEmpty.setVisibility(View.VISIBLE);
                            rvFriends.setVisibility(View.GONE);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading friend list", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Connection error", t);
            }
        });
    }

    /**
     * Lọc danh sách bạn bè hiện tại.
     */
    public void filterFriends(String query) {
        if (adapter != null) {
            adapter.filter(query);
            if (adapter.getItemCount() == 0 && !query.isEmpty()) {
                layoutEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText("No matches found in your galaxy");
            } else if (adapter.getItemCount() > 0) {
                layoutEmpty.setVisibility(View.GONE);
            }
        }
    }
}

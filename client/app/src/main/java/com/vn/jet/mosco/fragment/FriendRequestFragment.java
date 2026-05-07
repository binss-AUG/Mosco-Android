package com.vn.jet.mosco.fragment;

import android.os.Bundle;
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
 * Tab "REQUESTS" — Shows pending friend requests with Accept/Reject buttons.
 */
public class FriendRequestFragment extends Fragment implements FriendRequestAdapter.OnRequestActionListener {

    private static final String TAG = "FriendRequestFragment";
    private RecyclerView rvRequests;
    private TextView tvEmpty;
    private FriendRequestAdapter adapter;
    private GameApiService apiService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rank_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvRequests = view.findViewById(R.id.rv_rank_list);
        tvEmpty = view.findViewById(R.id.tv_rank_empty);
        tvEmpty.setText(getString(R.string.social_msg_no_requests));

        rvRequests.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FriendRequestAdapter(new ArrayList<>(), this);
        rvRequests.setAdapter(adapter);

        if (requireContext() != null) {
            apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        }
        loadRequests();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRequests();
    }

    /**
     * Load pending friend requests from API.
     */
    private void loadRequests() {
        if (apiService == null || requireContext() == null) return;

        apiService.getFriendRequests().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray data = json.optJSONArray("data");

                        if (data != null && data.length() > 0) {
                            List<JSONObject> requests = new ArrayList<>();
                            for (int i = 0; i < data.length(); i++) {
                                requests.add(data.getJSONObject(i));
                            }
                            adapter.updateData(requests);
                            tvEmpty.setVisibility(View.GONE);
                            rvRequests.setVisibility(View.VISIBLE);
                        } else {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvRequests.setVisibility(View.GONE);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading requests", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Connection error", t);
                if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    // ════════════════════════════════════════════════════════════════
    //  Accept / Reject callback — from FriendRequestAdapter
    // ════════════════════════════════════════════════════════════════

    @Override
    public void onAccept(Long friendshipId) {
        if (apiService == null) return;

        apiService.acceptFriend(friendshipId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (requireContext() == null) return;
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), getString(R.string.social_msg_request_accepted), Toast.LENGTH_SHORT).show();
                    loadRequests(); // Refresh list
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
                    loadRequests(); // Refresh list
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

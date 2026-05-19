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
import com.vn.jet.mosco.adapter.ConversationAdapter;
import com.vn.jet.mosco.database.AppDatabase;
import com.vn.jet.mosco.database.MessageDao;
import com.vn.jet.mosco.model.PrivateChatMessage;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.utils.NavigationUtils;
import com.vn.jet.mosco.view.InventoryFilterBar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.ResponseBody;

/**
 * Fragment quản lý danh sách Hộp thư Chat cá nhân (Private Messages).
 * Triển khai cơ chế Local-First chạy dưới luồng ngầm tránh ANR và đồng bộ hóa trạng thái Online theo thời gian thực.
 * Tách biệt hoàn toàn khỏi CollectionFragment (Decoupled & Standalone).
 */
public class PrivateChatListFragment extends Fragment implements ConversationAdapter.OnConversationClickListener {

    private RecyclerView rvPrivateChats;
    private ConversationAdapter conversationAdapter;
    private List<ConversationAdapter.ConversationWrapper> conversationsList = new ArrayList<>();
    private List<ConversationAdapter.ConversationWrapper> filteredConversationsList = new ArrayList<>();
    private InventoryFilterBar filterBar;
    private TextView tvPrivateChatsCount;

    private int activeFilter = 0; // 0: All, 1: Online

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mailbox_private_chats, container, false);

        rvPrivateChats = view.findViewById(R.id.rv_private_chats);
        filterBar = view.findViewById(R.id.filter_bar_private_chats);
        tvPrivateChatsCount = view.findViewById(R.id.tv_private_chats_count);

        setupFilterBar();
        if (filterBar != null) {
            filterBar.setVisibility(View.GONE);
        }

        rvPrivateChats.setLayoutManager(new LinearLayoutManager(getContext()));
        
        String myId = String.valueOf(new SessionManager(requireContext()).getUserId());
        conversationAdapter = new ConversationAdapter(myId, this);
        rvPrivateChats.setAdapter(conversationAdapter);

        loadConversationsLocalFirst();

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
                filterConversations();
            }

            @Override
            public void onFilterRequested() {
                filterConversations();
            }
        });
    }

    private void loadConversationsLocalFirst() {
        Context context = getContext();
        if (context == null) return;

        long myIdLong = new SessionManager(context).getUserId();
        String myId = String.valueOf(myIdLong);

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                if (db != null) {
                    MessageDao dao = db.messageDao();
                    List<PrivateChatMessage> lastMessages = dao.getRecentConversations(myId);

                    Set<String> uniquePartners = new LinkedHashSet<>();
                    List<ConversationAdapter.ConversationWrapper> uniqueWrappers = new ArrayList<>();

                    for (PrivateChatMessage msg : lastMessages) {
                        String partnerId = (msg.getSenderId().equals(myId)) ? msg.getReceiverId() : msg.getSenderId();
                        if (!uniquePartners.contains(partnerId)) {
                            uniquePartners.add(partnerId);

                            String name = dao.getPartnerName(partnerId);
                            String avatar = dao.getPartnerAvatar(partnerId);
                            
                            ConversationAdapter.ConversationWrapper wrapper = new ConversationAdapter.ConversationWrapper(
                                    msg,
                                    partnerId,
                                    name != null ? name : "User #" + partnerId,
                                    avatar
                            );
                            uniqueWrappers.add(wrapper);

                            // Nếu tên là null hoặc chứa tên tạm "User #", ta tải thông tin người dùng từ server để đồng bộ đầy đủ (hỗ trợ cả người lạ)
                            if (name == null || name.startsWith("User #") || name.startsWith("User ")) {
                                fetchStrangerProfileFromServer(partnerId, wrapper);
                            }
                        }
                    }

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            conversationsList.clear();
                            conversationsList.addAll(uniqueWrappers);
                            filterConversations();
                            syncRealtimeOnlineStatuses();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e("PrivateChatListFragment", "Error loading local chat history", e);
            }
        }).start();
    }

    private void fetchStrangerProfileFromServer(String partnerId, ConversationAdapter.ConversationWrapper wrapper) {
        Context context = getContext();
        if (context == null) return;
        try {
            long partnerIdLong = Long.parseLong(partnerId);
            GameApiService api = ApiClient.getClient(context).create(GameApiService.class);
            api.getUserStats(partnerIdLong).enqueue(new Callback<com.vn.jet.mosco.model.UserStats>() {
                @Override
                public void onResponse(Call<com.vn.jet.mosco.model.UserStats> call, Response<com.vn.jet.mosco.model.UserStats> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        com.vn.jet.mosco.model.UserStats stats = response.body();
                        String fullName = stats.getIngameName();
                        String avatar = stats.getAvatarId();
                        if (fullName != null && !fullName.isEmpty()) {
                            wrapper.setPartnerName(fullName);
                        }
                        if (avatar != null && !avatar.isEmpty()) {
                            wrapper.setPartnerAvatar(avatar);
                        }
                        // Cập nhật giao diện
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                filterConversations();
                            });
                        }
                    }
                }

                @Override
                public void onFailure(Call<com.vn.jet.mosco.model.UserStats> call, Throwable t) {
                    Log.e("PrivateChatListFragment", "Failed to fetch profile for stranger " + partnerId, t);
                }
            });
        } catch (NumberFormatException e) {
            Log.e("PrivateChatListFragment", "Invalid partnerId format for fetchStrangerProfile", e);
        }
    }

    private void syncRealtimeOnlineStatuses() {
        Context context = getContext();
        if (context == null) return;

        GameApiService api = ApiClient.getClient(context).create(GameApiService.class);
        api.getFriendList().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray friendsArr = json.optJSONArray("data");
                        if (friendsArr != null) {
                            java.util.Set<String> friendIds = new java.util.HashSet<>();
                            for (int i = 0; i < friendsArr.length(); i++) {
                                JSONObject friendObj = friendsArr.getJSONObject(i);
                                friendIds.add(String.valueOf(friendObj.optLong("userId")));
                            }

                            for (int i = 0; i < friendsArr.length(); i++) {
                                JSONObject friendObj = friendsArr.getJSONObject(i);
                                String friendId = String.valueOf(friendObj.optLong("userId"));
                                boolean online = friendObj.optBoolean("online", false);
                                String name = friendObj.optString("ingameName", "User #" + friendId);
                                String avatar = friendObj.optString("avatarId", "");
                                
                                boolean found = false;
                                for (ConversationAdapter.ConversationWrapper w : conversationsList) {
                                    if (w.getPartnerId().equals(friendId)) {
                                        w.setOnline(online);
                                        w.setStranger(false);
                                        // ĐỒNG BỘ TRỰC TIẾP TÊN VÀ AVATAR TỪ DANH SÁCH BẠN BÈ MỚI NHẤT! (Sửa lỗi hiển thị "User #1")
                                        if (w.getPartnerName() == null || w.getPartnerName().startsWith("User #") || w.getPartnerName().startsWith("User ")) {
                                            w.setPartnerName(name);
                                        }
                                        if (w.getPartnerAvatar() == null || w.getPartnerAvatar().isEmpty()) {
                                            w.setPartnerAvatar(avatar);
                                        }
                                        found = true;
                                        break;
                                    }
                                }

                                if (!found) {
                                    ConversationAdapter.ConversationWrapper newWrapper = 
                                        new ConversationAdapter.ConversationWrapper(null, friendId, name, avatar);
                                    newWrapper.setOnline(online);
                                    newWrapper.setStranger(false);
                                    conversationsList.add(newWrapper);
                                }
                            }

                            for (ConversationAdapter.ConversationWrapper w : conversationsList) {
                                if (!friendIds.contains(w.getPartnerId())) {
                                    w.setStranger(true);
                                }
                            }
                            // Re-filter so online matches
                            filterConversations();
                        }
                    }
                } catch (Exception e) {
                    Log.e("PrivateChatListFragment", "Failed to parse friends online statuses", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("PrivateChatListFragment", "Failed to sync friends online statuses", t);
            }
        });
    }

    private void filterConversations() {
        if (getContext() == null) return;

        filteredConversationsList.clear();
        for (ConversationAdapter.ConversationWrapper wrapper : conversationsList) {
            filteredConversationsList.add(wrapper);
        }
        
        conversationAdapter.updateData(filteredConversationsList);

        if (tvPrivateChatsCount != null) {
            tvPrivateChatsCount.setText(String.valueOf(filteredConversationsList.size()));
        }
    }

    @Override
    public void onConversationClick(ConversationAdapter.ConversationWrapper wrapper) {
        if (getActivity() == null) return;
        try {
            long partnerId = Long.parseLong(wrapper.getPartnerId());
            NavigationUtils.openPrivateChat(getActivity(), partnerId, wrapper.getPartnerName(), wrapper.getPartnerAvatar());
        } catch (NumberFormatException e) {
            Log.e("PrivateChatListFragment", "Invalid partner ID format", e);
        }
    }
}

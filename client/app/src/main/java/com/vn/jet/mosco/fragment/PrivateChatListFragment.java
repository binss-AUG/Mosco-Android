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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.disposables.Disposable;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.ResponseBody;
import com.vn.jet.mosco.network.WebSocketManager;
import com.vn.jet.mosco.utils.AppExecutors;

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
    // Subscription WebSocket để lắng nghe tin nhắn mới → tự refresh danh sách (giống Messenger)
    private Disposable privateMessageSubscription;

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

    private final androidx.fragment.app.FragmentManager.OnBackStackChangedListener backStackListener = () -> {
        if (isAdded()) {
            loadConversationsLocalFirst();
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        subscribeToIncomingMessages();
        try {
            requireActivity().getSupportFragmentManager().addOnBackStackChangedListener(backStackListener);
        } catch (Exception e) {
            Log.e("PrivateChatList", "Failed to add backstack listener", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Cập nhật lại danh sách khi quay lại từ màn hình chat (vì onStart có thể không được gọi lại, hoặc tin nhắn đã gửi trong lúc Fragment này onStop)
        loadConversationsLocalFirst();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            // Cập nhật lại danh sách nếu Fragment được hiện lại (trong trường hợp dùng add/hide)
            loadConversationsLocalFirst();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            requireActivity().getSupportFragmentManager().removeOnBackStackChangedListener(backStackListener);
        } catch (Exception e) {
            Log.e("PrivateChatList", "Failed to remove backstack listener", e);
        }
        // Hủy subscription khi Fragment không hiển thị để tránh memory leak
        if (privateMessageSubscription != null && !privateMessageSubscription.isDisposed()) {
            privateMessageSubscription.dispose();
            privateMessageSubscription = null;
        }
    }

    /**
     * Lắng nghe tin nhắn private đến qua WebSocket STOMP.
     * Tại sao (WHY): Khi có tin nhắn mới (gửi/nhận), Room DB được cập nhật ngay,
     * sau đó gọi lại loadConversationsLocalFirst() để refresh danh sách tức thì như Messenger.
     */
    private void subscribeToIncomingMessages() {
        Context context = getContext();
        if (context == null) return;
        String myId = String.valueOf(new SessionManager(context).getUserId());

        if (privateMessageSubscription != null && !privateMessageSubscription.isDisposed()) return;

        privateMessageSubscription = WebSocketManager.getInstance()
            .subscribeToPrivateChat(myId, message -> {
                if (message == null || !isAdded()) return;

                // Tại sao (WHY): Bỏ qua tin nhắn điều khiển [SEEN] hệ thống, không lưu vào DB và không hiển thị Inbox.
                if (message.getContent() != null && message.getContent().startsWith("[SEEN]:")) {
                    return;
                }

                // Lưu vào Room DB ở luồng ngầm, sau đó reload danh sách ở luồng UI
                final String incomingMyId = myId;
                AppExecutors.getInstance().diskIO().execute(() -> {
                    try {
                        AppDatabase db = AppDatabase.getInstance(context);
                        if (db != null) {
                            db.messageDao().insertMessage(message);
                            // Tại sao (WHY): Cleanup message cũ để tránh heap overflow
                            String partnerId = message.getSenderId().equals(incomingMyId)
                                    ? message.getReceiverId() : message.getSenderId();
                            db.messageDao().trimConversation(incomingMyId, partnerId, 200);
                        }
                    } catch (Exception e) {
                        Log.e("PrivateChatListFragment", "Error saving incoming message", e);
                    }
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (isAdded()) loadConversationsLocalFirst();
                        });
                    }
                });
            });
    }

    // PLACEHOLDER_RETURN — sẽ bị xóa sau khi merge với return view bên dưới
    private void _dummy() {
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

        AppExecutors.getInstance().diskIO().execute(() -> {
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
                            int unreadCount = dao.getUnreadCount(myId, partnerId);

                            // Tại sao (WHY): Tra cứu cache user_stats từ Room DB trước nếu DB private_messages chưa lưu kịp tên/avatar
                            if (name == null || name.isEmpty() || name.equals("User")) {
                                try {
                                    long partnerIdLong = Long.parseLong(partnerId);
                                    com.vn.jet.mosco.model.UserStats cachedStats = db.userStatsDao().getUserStatsSync(partnerIdLong);
                                    if (cachedStats != null) {
                                        String cachedName = cachedStats.getIngameName();
                                        if (cachedName == null || cachedName.isEmpty()) {
                                            cachedName = cachedStats.getUsername();
                                        }
                                        if (cachedName != null && !cachedName.isEmpty()) {
                                            name = cachedName;
                                        }
                                        String cachedAvatar = cachedStats.getAvatarId();
                                        if (cachedAvatar != null && !cachedAvatar.isEmpty()) {
                                            avatar = cachedAvatar;
                                        }
                                    }
                                } catch (NumberFormatException e) {
                                    Log.e("PrivateChatListFragment", "Invalid partnerId format for local cache lookup", e);
                                }
                            }

                            ConversationAdapter.ConversationWrapper wrapper = new ConversationAdapter.ConversationWrapper(
                                    msg,
                                    partnerId,
                                    (name != null && !name.isEmpty()) ? name : "User",
                                    avatar
                            );
                            wrapper.setUnreadCount(unreadCount);
                            // Tại sao (WHY): Mặc định tất cả là stranger, tránh flash "User" → "User • Stranger"
                            // khi syncRealtimeOnlineStatuses chạy xong (delay ~1-2s)
                            wrapper.setStranger(true);
                            uniqueWrappers.add(wrapper);
                        }
                    }

                    if (getActivity() != null) {
                        final List<ConversationAdapter.ConversationWrapper> finalWrappers = uniqueWrappers;
                        getActivity().runOnUiThread(() -> {
                            // === PASS 1: Show local data instantly ===
                            conversationsList.clear();
                            conversationsList.addAll(finalWrappers);
                            filterConversations();

                            // === PREPARE BATCH: đếm số lượng API requests ===
                            int profileCount = 0;
                            for (ConversationAdapter.ConversationWrapper w : finalWrappers) {
                                if ("User".equals(w.getPartnerName())) {
                                    profileCount++;
                                }
                            }
                            int totalRequests = finalWrappers.size() // streak cho mỗi partner
                                    + profileCount                   // stranger profile
                                    + 1;                             // friend list
                            AtomicInteger pending = new AtomicInteger(totalRequests);

                            // Bộ nhớ đệm kết quả từ các API — KHÔNG mutate wrapper giữa chừng
                            ConcurrentHashMap<String, com.vn.jet.mosco.model.CoupleStreakDto> streakMap = new ConcurrentHashMap<>();
                            ConcurrentHashMap<String, Boolean> onlineMap = new ConcurrentHashMap<>();
                            ConcurrentLinkedQueue<String> friendIdsFromApi = new ConcurrentLinkedQueue<>();
                            ConcurrentHashMap<String, String> friendNameMap = new ConcurrentHashMap<>();
                            ConcurrentHashMap<String, String> profileNameMap = new ConcurrentHashMap<>();
                            ConcurrentHashMap<String, String> friendAvatarMap = new ConcurrentHashMap<>();
                            ConcurrentHashMap<String, String> profileAvatarMap = new ConcurrentHashMap<>();

                            Runnable onBatchDone = () -> {
                                if (pending.decrementAndGet() == 0) {
                                    if (getActivity() != null) {
                                        getActivity().runOnUiThread(() -> {
                                            if (!isAdded()) return;
                                            applyBatchUpdates(streakMap, onlineMap, friendIdsFromApi,
                                                    friendNameMap, profileNameMap, friendAvatarMap, profileAvatarMap);
                                        });
                                    }
                                }
                            };

                            // Fire streak API cho ALL partners
                            for (ConversationAdapter.ConversationWrapper w : finalWrappers) {
                                fetchStreakForConversation(myIdLong, w.getPartnerId(), streakMap, onBatchDone);
                            }

                            // Fire stranger profile API cho các partner có tên "User"
                            for (ConversationAdapter.ConversationWrapper w : finalWrappers) {
                                if ("User".equals(w.getPartnerName())) {
                                    fetchStrangerProfileFromServer(w.getPartnerId(), profileNameMap, profileAvatarMap, onBatchDone);
                                }
                            }

                            // Fire friend list API
                            syncRealtimeOnlineStatuses(onlineMap, friendIdsFromApi, friendNameMap, friendAvatarMap, onBatchDone);
                        });
                    }
                }
            } catch (Exception e) {
                Log.e("PrivateChatListFragment", "Error loading local chat history", e);
            }
        });
    }

    private void fetchStreakForConversation(long myId, String partnerId,
            ConcurrentHashMap<String, com.vn.jet.mosco.model.CoupleStreakDto> streakMap,
            Runnable onDone) {
        Context context = getContext();
        if (context == null) { if (onDone != null) onDone.run(); return; }
        try {
            long partnerIdLong = Long.parseLong(partnerId);
            GameApiService api = ApiClient.getClient(context).create(GameApiService.class);
            api.checkCoupleStreak(myId, partnerIdLong).enqueue(new Callback<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>>() {
                @Override
                public void onResponse(Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> call, Response<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                        streakMap.put(partnerId, response.body().getData());
                    }
                    if (onDone != null) onDone.run();
                }

                @Override
                public void onFailure(Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> call, Throwable t) {
                    Log.e("PrivateChatListFragment", "Failed to fetch streak for partner " + partnerId, t);
                    if (onDone != null) onDone.run();
                }
            });
        } catch (NumberFormatException e) {
            Log.e("PrivateChatListFragment", "Invalid partnerId format for fetchStreakStatus", e);
            if (onDone != null) onDone.run();
        }
    }

    private void fetchStrangerProfileFromServer(String partnerId,
            ConcurrentHashMap<String, String> nameMap,
            ConcurrentHashMap<String, String> avatarMap,
            Runnable onDone) {
        Context context = getContext();
        if (context == null) { if (onDone != null) onDone.run(); return; }
        try {
            long partnerIdLong = Long.parseLong(partnerId);
            GameApiService api = ApiClient.getClient(context).create(GameApiService.class);
            api.getUserStats(partnerIdLong).enqueue(new Callback<com.vn.jet.mosco.model.UserStats>() {
                @Override
                public void onResponse(Call<com.vn.jet.mosco.model.UserStats> call, Response<com.vn.jet.mosco.model.UserStats> response) {
                    if (response.isSuccessful() && response.body() != null) {
                         com.vn.jet.mosco.model.UserStats stats = response.body();
                         String fullName = stats.getIngameName();
                         String username = stats.getUsername();
                         String avatar = stats.getAvatarId();
                         String displayName = (fullName != null && !fullName.isEmpty()) ? fullName : username;
                         if (displayName == null || displayName.isEmpty()) {
                             displayName = "User";
                         }
                         nameMap.put(partnerId, displayName);
                         if (avatar != null && !avatar.isEmpty()) {
                             avatarMap.put(partnerId, avatar);
                         }

                         // Tại sao (WHY): Cache thông tin stranger vừa tải xuống Room DB vĩnh viễn và đồng bộ vào bảng tin nhắn để lần sau load tức thì
                         final String finalDisplayName = displayName;
                         AppExecutors.getInstance().diskIO().execute(() -> {
                             try {
                                 AppDatabase dbInstance = AppDatabase.getInstance(context);
                                 if (dbInstance != null) {
                                     dbInstance.userStatsDao().insertUserStats(stats);
                                     dbInstance.messageDao().updatePartnerName(partnerId, finalDisplayName);
                                     if (avatar != null && !avatar.isEmpty()) {
                                         dbInstance.messageDao().updatePartnerAvatar(partnerId, avatar);
                                     }
                                 }
                             } catch (Exception e) {
                                 Log.e("PrivateChatListFragment", "Error saving stranger stats to Room DB", e);
                             }
                         });
                    }
                    if (onDone != null) onDone.run();
                }

                @Override
                public void onFailure(Call<com.vn.jet.mosco.model.UserStats> call, Throwable t) {
                    Log.e("PrivateChatListFragment", "Failed to fetch profile for stranger " + partnerId, t);
                    if (onDone != null) onDone.run();
                }
            });
        } catch (NumberFormatException e) {
            Log.e("PrivateChatListFragment", "Invalid partnerId format for fetchStrangerProfile", e);
            if (onDone != null) onDone.run();
        }
    }

    private void syncRealtimeOnlineStatuses(
            ConcurrentHashMap<String, Boolean> onlineMap,
            ConcurrentLinkedQueue<String> friendIdsOut,
            ConcurrentHashMap<String, String> nameMap,
            ConcurrentHashMap<String, String> avatarMap,
            Runnable onDone) {
        Context context = getContext();
        if (context == null) { if (onDone != null) onDone.run(); return; }

        GameApiService api = ApiClient.getClient(context).create(GameApiService.class);
        api.getFriendList().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray friendsArr = json.optJSONArray("data");
                        if (friendsArr != null) {
                            for (int i = 0; i < friendsArr.length(); i++) {
                                JSONObject friendObj = friendsArr.getJSONObject(i);
                                String friendId = String.valueOf(friendObj.optLong("userId"));
                                boolean online = friendObj.optBoolean("online", false);
                                String username = friendObj.optString("username", "");
                                String fullName = friendObj.optString("ingameName", "");
                                String name = (!fullName.isEmpty()) ? fullName : (!username.isEmpty() ? username : "User");
                                String avatar = friendObj.optString("avatarId", "");

                                friendIdsOut.add(friendId);
                                onlineMap.put(friendId, online);
                                nameMap.put(friendId, name);
                                if (avatar != null && !avatar.isEmpty()) {
                                    avatarMap.put(friendId, avatar);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("PrivateChatListFragment", "Failed to parse friends online statuses", e);
                }
                if (onDone != null) onDone.run();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("PrivateChatListFragment", "Failed to sync friends online statuses", t);
                if (onDone != null) onDone.run();
            }
        });
    }

    /**
     * Tại sao (WHY): Gom toàn bộ kết quả từ streak, stranger profile, friend list API
     * vào 1 lần duy nhất. Tạo wrapper object mới để DiffUtil detect chính xác sự thay đổi
     * so với danh sách cũ (local-only) — tránh 3 pass riêng lẻ gây jank.
     */
    private void applyBatchUpdates(
            ConcurrentHashMap<String, com.vn.jet.mosco.model.CoupleStreakDto> streakMap,
            ConcurrentHashMap<String, Boolean> onlineMap,
            ConcurrentLinkedQueue<String> friendIdsFromApi,
            ConcurrentHashMap<String, String> friendNameMap,
            ConcurrentHashMap<String, String> profileNameMap,
            ConcurrentHashMap<String, String> friendAvatarMap,
            ConcurrentHashMap<String, String> profileAvatarMap) {
        if (!isAdded()) return;

        // Set để tra cứu nhanh friend IDs
        java.util.HashSet<String> friendIdSet = new java.util.HashSet<>(friendIdsFromApi);

        // Build danh sách wrapper mới với tất cả dữ liệu remote đã gom
        List<ConversationAdapter.ConversationWrapper> finalList = new ArrayList<>();

        // Xử lý các wrapper đã có trong conversationsList
        for (ConversationAdapter.ConversationWrapper w : conversationsList) {
            String pid = w.getPartnerId();
            // Ưu tiên tên: friend list > stranger profile > original
            String finalName = w.getPartnerName();
            if (friendNameMap.containsKey(pid)) {
                finalName = friendNameMap.get(pid);
            } else if (profileNameMap.containsKey(pid)) {
                finalName = profileNameMap.get(pid);
            }
            // Ưu tiên avatar: friend list > stranger profile > original
            String finalAvatar = w.getPartnerAvatar();
            if (friendAvatarMap.containsKey(pid)) {
                finalAvatar = friendAvatarMap.get(pid);
            } else if (profileAvatarMap.containsKey(pid)) {
                finalAvatar = profileAvatarMap.get(pid);
            }

            ConversationAdapter.ConversationWrapper copy = new ConversationAdapter.ConversationWrapper(
                    w.getLastMessage(), pid, finalName, finalAvatar);
            copy.setOnline(onlineMap.getOrDefault(pid, false));
            copy.setUnreadCount(w.getUnreadCount());
            copy.setStreakData(streakMap.get(pid));
            // isStranger = không có trong danh sách bạn bè từ API
            copy.setStranger(!friendIdSet.contains(pid));
            finalList.add(copy);
        }

        // Thêm bạn bè từ friend list API chưa có trong conversationsList
        for (String friendId : friendIdsFromApi) {
            boolean exists = false;
            for (ConversationAdapter.ConversationWrapper w : finalList) {
                if (w.getPartnerId().equals(friendId)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                String fName = friendNameMap.getOrDefault(friendId, "User");
                String fAvatar = friendAvatarMap.getOrDefault(friendId, "");
                ConversationAdapter.ConversationWrapper newWrapper =
                        new ConversationAdapter.ConversationWrapper(null, friendId, fName, fAvatar);
                newWrapper.setOnline(onlineMap.getOrDefault(friendId, false));
                newWrapper.setStranger(false); // bạn bè
                newWrapper.setStreakData(streakMap.get(friendId));
                finalList.add(newWrapper);
            }
        }

        // Thay thế danh sách cũ và gọi 1 filterConversations duy nhất
        conversationsList.clear();
        conversationsList.addAll(finalList);
        filterConversations();
    }

    private void filterConversations() {
        if (getContext() == null) return;

        // Sắp xếp các cuộc hội thoại: tin nhắn mới nhất lên đầu, bạn bè chưa có tin nhắn ở dưới cùng
        conversationsList.sort((a, b) -> {
            long tsA = (a.getLastMessage() != null) ? a.getLastMessage().getTimestamp() : 0;
            long tsB = (b.getLastMessage() != null) ? b.getLastMessage().getTimestamp() : 0;
            return Long.compare(tsB, tsA);
        });

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
            NavigationUtils.openPrivateChat(getActivity(), partnerId, wrapper.getPartnerName(), wrapper.getPartnerAvatar(), wrapper.isOnline(), wrapper.isStranger());
        } catch (NumberFormatException e) {
            Log.e("PrivateChatListFragment", "Invalid partner ID format", e);
        }
    }
}

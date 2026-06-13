package com.vn.jet.mosco.network;

import com.vn.jet.mosco.model.ShopItem;
import com.vn.jet.mosco.model.UserStats;

import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface GameApiService {

    @GET("/api/user/{userId}")
    Call<UserStats> getUserStats(@Path("userId") Long userId);

    @GET("/api/shop")
    Call<List<ShopItem>> getShopItems();

    @POST("/api/shop/buy")
    Call<ResponseBody> buyItem(@Body BuyRequest buyRequest);

    @GET("/api/inventory/items/{userId}")
    Call<List<com.vn.jet.mosco.model.UserItem>> getUserItems(@Path("userId") Long userId);

    @GET("/api/inventory/cards/{userId}")
    Call<List<com.vn.jet.mosco.model.UserCard>> getUserCards(@Path("userId") Long userId);

    @GET("/api/mailbox/{userId}")
    Call<List<com.vn.jet.mosco.model.UserMail>> getUserMails(@Path("userId") Long userId);

    @POST("/api/pack/open")
    Call<com.vn.jet.mosco.model.ApiResponse<java.util.Map<String, Object>>> openPack(
            @Query("userId") Long userId, 
            @Query("packCode") String packCode,
            @Query("quantity") int quantity);

    /**
     * Gacha roll endpoint (JWT-protected, rate-limited).
     */
    @POST("/api/gacha/roll")
    Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.GachaRollResponse>> rollGacha(
            @Body com.vn.jet.mosco.model.GachaRollRequest request);

    /**
     * Spin (Recycle/Sacrifice) endpoint.
     */
    @POST("/api/gacha/spin")
    Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.GachaSpinResponse>> spinCard(
            @Body com.vn.jet.mosco.model.GachaSpinRequest request);

    /**
     * Upgrade endpoint.
     */
    @POST("/api/v1/upgrade")
    Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UpgradeResponse>> upgradeCard(
            @Body com.vn.jet.mosco.model.UpgradeRequest request);

    /**
     * Đặt tên hiển thị trong game (Display Name).
     */
    @POST("/api/user/set-display-name")
    Call<ResponseBody> setDisplayName(@Body java.util.Map<String, String> body);

    /**
     * Nhận quà từ thư (Claim Gift).
     */
    @POST("/api/mailbox/claim/{mailId}")
    Call<ResponseBody> claimMail(@Path("mailId") Long mailId);

    /**
     * Nhận toàn bộ quà từ thư hệ thống (Claim All).
     */
    @POST("/api/mailbox/claim-all/{userId}")
    Call<ResponseBody> claimAllMails(@Path("userId") Long userId);

    @retrofit2.http.PUT("/api/user/update-profile")
    Call<com.vn.jet.mosco.model.ApiResponse<UserStats>> updateProfile(@Body UpdateProfileRequest body);

    @POST("/api/user/streak/restore")
    Call<com.vn.jet.mosco.model.ApiResponse<UserStats>> restoreStreak();

    @POST("/api/user/delete-account")
    Call<com.vn.jet.mosco.model.ApiResponse<Void>> deleteAccount(@Query("code") String code);

    /**
     * Thích hoặc bỏ thích hồ sơ người chơi khác.
     */
    @POST("/api/user/{targetUserId}/like")
    Call<com.vn.jet.mosco.model.ApiResponse<java.util.Map<String, Object>>> likeProfile(@Path("targetUserId") Long targetUserId);

    // ══════════════════════════════════════════════════════════════
    //  DAILY CHECK-IN — Điểm danh hằng ngày
    // ══════════════════════════════════════════════════════════════

    /**
     * Lấy trạng thái 3 slot trong ngày (claimed/available/locked).
     */
    @GET("/api/daily/status")
    Call<ResponseBody> getDailyStatus();

    @POST("/api/daily/claim")
    Call<ResponseBody> claimDaily();

    /**
     * Lấy lịch sử chat private (Offline Sync) từ Server.
     */
    @GET("/api/chat/history")
    Call<com.vn.jet.mosco.model.ApiResponse<List<com.vn.jet.mosco.model.PrivateChatMessage>>> getChatHistory(
            @Query("user1") Long user1, 
            @Query("user2") Long user2);

    /**
     * Xác nhận đã đồng bộ Offline Chat thành công để Server xóa tin nhắn chờ.
     */
    @POST("/api/chat/ack")
    Call<ResponseBody> ackMessages(@Body List<Long> messageIds);

    /**
     * Preview OVR và Synergy cho đội hình (Passive Synergy Logic).
     */
    @POST("/api/battle/preview")
    Call<com.vn.jet.mosco.dto.BattleResponse> postBattlePreview(@Body com.vn.jet.mosco.dto.BattleRequest request);

    @POST("/api/battle")
    Call<com.vn.jet.mosco.dto.BattleResponse> postBattle(@Body com.vn.jet.mosco.dto.BattleRequest request);

    @GET("/api/battle/formation/{userId}")
    Call<List<com.vn.jet.mosco.model.Objet>> getUserFormation(@Path("userId") Long userId);

    @POST("/api/battle/formation/{userId}/save")
    Call<ResponseBody> saveUserFormation(@Path("userId") Long userId, @Body java.util.List<Long> slotIds);

    // ══════════════════════════════════════════════════════════════
    //  COLLECTION BOOK — Bộ Sưu Tập (Pokédex-style)
    // ══════════════════════════════════════════════════════════════

    /** Lấy toàn bộ Bộ Sưu Tập với trạng thái sở hữu. */
    @GET("/api/collection/book/{userId}")
    Call<com.vn.jet.mosco.model.CollectionBookResponse> getCollectionBook(@Path("userId") Long userId);

    // ══════════════════════════════════════════════════════════════
    //  RANKING — Bảng xếp hạng (Public, không cần auth)
    // ══════════════════════════════════════════════════════════════

    /** Top 10 theo Level. */
    @GET("/api/rank/level")
    Call<ResponseBody> getRankByLevel();

    /** Top 10 theo OVR (Objet to nhất). */
    @GET("/api/rank/ovr")
    Call<ResponseBody> getRankByOvr();

    /** Top 10 theo Sưu tập. */
    @GET("/api/rank/collection")
    Call<ResponseBody> getRankByCollection();

    /** Top 10 theo Nhiều bạn bè nhất (Social). */
    @GET("/api/rank/social")
    Call<ResponseBody> getRankBySocial();

    /** Top 10 theo Chuỗi đăng nhập (Streak). */
    @GET("/api/rank/streak")
    Call<ResponseBody> getRankByStreak();

    /** Top 10 theo Danh vọng (Fame/Likes). */
    @GET("/api/rank/fame")
    Call<ResponseBody> getRankByFame();

    /** Top 10 theo Duo Streak. */
    @GET("/api/rank/duo-streak")
    Call<ResponseBody> getRankByDuoStreak();

    // ══════════════════════════════════════════════════════════════
    //  FRIEND — Quản lý Bạn bè
    // ══════════════════════════════════════════════════════════════

    /** Danh sách bạn bè đã chấp nhận. */
    @GET("/api/friends/list")
    Call<ResponseBody> getFriendList();

    /** Lời mời kết bạn đang chờ. */
    @GET("/api/friends/requests")
    Call<ResponseBody> getFriendRequests();

    /** Gửi lời mời kết bạn. Body: { "addresseeId": 123 } */
    @POST("/api/friends/add")
    Call<ResponseBody> addFriend(@Body java.util.Map<String, Long> body);

    /** Chấp nhận lời mời. */
    @POST("/api/friends/accept/{friendshipId}")
    Call<ResponseBody> acceptFriend(@Path("friendshipId") Long friendshipId);

    /** Xóa bạn / Từ chối. */
    @retrofit2.http.DELETE("/api/friends/remove/{friendshipId}")
    Call<ResponseBody> removeFriend(@Path("friendshipId") Long friendshipId);

    /** Hủy kết bạn hoặc lời mời kết bạn trực tiếp theo ID người chơi. */
    @retrofit2.http.DELETE("/api/friends/remove-by-user/{targetUserId}")
    Call<com.vn.jet.mosco.model.ApiResponse<Void>> removeFriendByUser(@Path("targetUserId") Long targetUserId);

    /** Chấp nhận lời mời kết bạn trực tiếp theo ID người chơi. */
    @POST("/api/friends/accept-by-user/{targetUserId}")
    Call<com.vn.jet.mosco.model.ApiResponse<Void>> acceptFriendByUser(@Path("targetUserId") Long targetUserId);

    /** Tìm kiếm user theo tên hoặc ID. */
    @GET("/api/friends/search")
    Call<ResponseBody> searchUsers(@Query("query") String query);

    /**
     * Lấy danh sách tối đa 20 người chơi gợi ý mới chưa kết bạn.
     * Tại sao (WHY): Tách biệt truy vấn ngẫu nhiên khỏi truy vấn chuỗi tìm kiếm, cho phép giao diện tự động nạp danh sách tươi mới khi mở Tab Explore.
     */
    @GET("/api/friends/explore")
    Call<ResponseBody> getExploreSuggestions();

    // ══════════════════════════════════════════════════════════════
    //  GIFT — Tặng Objet giữa các user
    // ══════════════════════════════════════════════════════════════

    /** Gửi tặng thẻ. Body: { "cardId": 1, "receiverId": 2 } */
    @POST("/api/gift/send")
    Call<ResponseBody> sendGift(@Body java.util.Map<String, Long> body);

    /** Danh sách quà đã nhận (Inbox). */
    @GET("/api/gift/received")
    Call<ResponseBody> getReceivedGifts();

    /** Danh sách quà đã gửi. */
    @GET("/api/gift/sent")
    Call<ResponseBody> getSentGifts();

    /** Đánh dấu tất cả quà nhận là đã đọc. */
    @POST("/api/gift/mark-read")
    Call<ResponseBody> markGiftsAsRead();

    /** Số lượt tặng còn lại trong ngày. */
    @GET("/api/gift/daily-remaining")
    Call<ResponseBody> getDailyGiftRemaining();

    /** Số quà chưa đọc (cho badge thông báo). */
    @GET("/api/gift/unread-count")
    Call<ResponseBody> getGiftUnreadCount();

    // ══════════════════════════════════════════════════════════════
    //  AFK STAGE — Hệ thống phái cử tự động
    // ══════════════════════════════════════════════════════════════

    @POST("/api/stage/start/{userId}")
    Call<com.vn.jet.mosco.dto.StageSessionResponse> startStage(@Path("userId") Long userId, @Body com.vn.jet.mosco.dto.StartStageRequest request);

    @POST("/api/stage/claim/{userId}/{sessionId}")
    Call<com.vn.jet.mosco.dto.StageRewardResponse> claimStageReward(@Path("userId") Long userId, @Path("sessionId") Long sessionId);

    @POST("/api/stage/abort/{userId}/{sessionId}")
    Call<ResponseBody> abortStage(@Path("userId") Long userId, @Path("sessionId") Long sessionId);

    @POST("/api/stage/speed-up/{userId}/{sessionId}")
    Call<ResponseBody> speedUpStage(@Path("userId") Long userId, @Path("sessionId") Long sessionId);

    @GET("/api/stage/my-sessions/{userId}")
    Call<List<com.vn.jet.mosco.dto.StageSessionResponse>> getMyStageSessions(@Path("userId") Long userId);

    // ══════════════════════════════════════════════════════════════
    //  CONFIG & SYNC — Đồng bộ cấu hình động
    // ══════════════════════════════════════════════════════════════

    @GET("/api/config/db-version")
    Call<Map<String, String>> getDatabaseVersion();

    @GET("/api/config/db-download")
    Call<ResponseBody> downloadDatabase();

    @GET("/api/v1/cards/sync")
    Call<List<com.vn.jet.mosco.dto.CardSummaryDto>> getCardsSync(@Query("lastSyncTime") Long lastSyncTime);

    // ══════════════════════════════════════════════════════════════
    //  ASSET SYNC — Đồng bộ Metadata mới nhất
    // ══════════════════════════════════════════════════════════════

    @GET("/api/assets/manifest")
    Call<okhttp3.ResponseBody> getAssetManifest();

    @GET("api/v1/assets/database")
    retrofit2.Call<okhttp3.ResponseBody> getFullDatabase(@retrofit2.http.Query("t") String timestamp);
    @retrofit2.http.Multipart
    @POST("/api/backup/upload")
    Call<com.vn.jet.mosco.model.ApiResponse<String>> uploadBackup(@retrofit2.http.Part okhttp3.MultipartBody.Part file);

    @GET("/api/backup/list")
    Call<com.vn.jet.mosco.model.ApiResponse<List<String>>> listCloudBackups();

    @GET("/api/backup/download/{filename}")
    @retrofit2.http.Streaming
    Call<ResponseBody> downloadCloudBackup(@Path("filename") String filename);

    // ══════════════════════════════════════════════════════════════
    //  COUPLE STREAK — Hệ thống chuỗi đôi
    // ══════════════════════════════════════════════════════════════

    @POST("/api/v1/streaks/request")
    Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> requestCoupleStreak(
            @Query("requesterId") Long requesterId, 
            @Query("partnerId") Long partnerId);

    @GET("/api/v1/streaks/check")
    Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> checkCoupleStreak(
            @Query("user1") Long user1, 
            @Query("user2") Long user2);

    @POST("/api/v1/streaks/accept")

    Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> acceptCoupleStreak(
            @Query("userId") Long userId, 
            @Query("requesterId") Long requesterId);

    @POST("/api/v1/streaks/decline")
    public Call<com.vn.jet.mosco.model.ApiResponse<Void>> declineCoupleStreak(
            @Query("userId") Long userId, 
            @Query("requesterId") Long requesterId);

    @POST("/api/v1/streaks/update-objet")
    Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.CoupleStreakDto>> updateCoupleStreakObjet(
            @Query("streakId") Long streakId,
            @Query("userId") Long userId,
            @Query("objetId") String objetId,
            @Query("grade") int grade);
}

package com.vn.jet.mosco.network;

import com.vn.jet.mosco.model.ShopItem;
import com.vn.jet.mosco.model.UserStats;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import java.util.Map;

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
    Call<Map<String, Object>> openPack(@Query("userId") Long userId, @Query("packCode") String packCode);

    /**
     * Gacha roll endpoint (JWT-protected, rate-limited).
     * Bearer token is auto-attached by ApiClient's OkHttp interceptor.
     * @param request { packCode: "PACK_METAL", quantity: 1 }
     * @return GachaRollResponse with itemId, rarity, quantity, cardData
     */
    @POST("/api/gacha/roll")
    Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.GachaRollResponse>> rollGacha(
            @Body com.vn.jet.mosco.model.GachaRollRequest request);

    /**
     * Spin (Recycle/Sacrifice) endpoint.
     * Trade one card for a chance to get a new one.
     */
    @POST("/api/gacha/spin")
    Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.GachaSpinResponse>> spinCard(
            @Body com.vn.jet.mosco.model.GachaSpinRequest request);

    /**
     * Upgrade endpoint.
     */
    @POST("/api/gacha/upgrade")
    Call<com.vn.jet.mosco.model.ApiResponse<com.vn.jet.mosco.model.UpgradeResponse>> upgradeCard(
            @Body com.vn.jet.mosco.model.UpgradeRequest request);

    /**
     * Đặt tên hiển thị trong game (Display Name).
     * Endpoint được bảo vệ bởi JWT + Galactic Name Shield validation.
     */
    @POST("/api/user/set-display-name")
    Call<ResponseBody> setDisplayName(@Body java.util.Map<String, String> body);

    /**
     * Cập nhật thông tin cá nhân (username + ingameName).
     * Email KHÔNG cho sửa — Server sẽ bỏ qua.
     */
    /**
     * Nhận quà từ thư (Claim Gift).
     */
    @POST("/api/mailbox/claim/{mailId}")
    Call<ResponseBody> claimMail(@Path("mailId") Long mailId);

    @retrofit2.http.PUT("/api/user/update-profile")
    Call<ResponseBody> updateProfile(@Body java.util.Map<String, String> body);

    // ══════════════════════════════════════════════════════════════
    //  DAILY CHECK-IN — Điểm danh hằng ngày
    // ══════════════════════════════════════════════════════════════

    /**
     * Lấy trạng thái 3 slot trong ngày (claimed/available/locked).
     */
    @GET("/api/daily/status")
    Call<ResponseBody> getDailyStatus();

    /**
     * Claim phần thưởng cho slot hiện tại.
     */
    @POST("/api/daily/claim")
    Call<ResponseBody> claimDaily();

    // ══════════════════════════════════════════════════════════════
    //  RANKING — Bảng xếp hạng (Public, không cần auth)
    // ══════════════════════════════════════════════════════════════

    /** Top 10 theo Level. */
    @GET("/api/rank/level")
    Call<ResponseBody> getRankByLevel();

    /** Top 10 theo OVR (Objet to nhất). */
    @GET("/api/rank/ovr")
    Call<ResponseBody> getRankByOvr();

    /** Top 10 theo số thẻ không trùng. */
    @GET("/api/rank/collection")
    Call<ResponseBody> getRankByCollection();

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

    /** Tìm kiếm user theo tên hoặc ID. */
    @GET("/api/friends/search")
    Call<ResponseBody> searchUsers(@Query("query") String query);
}

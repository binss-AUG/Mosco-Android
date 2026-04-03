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
}

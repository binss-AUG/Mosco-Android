package com.vn.jet.mosco.network;

import com.vn.jet.mosco.model.AuthRequest;
import com.vn.jet.mosco.model.AuthResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApiService {

    @POST("/api/auth/signup")
    @com.google.gson.annotations.SerializedName("request")
    retrofit2.Call<AuthResponse> signup(@Body AuthRequest request);

    @POST("/api/auth/signin")
    retrofit2.Call<AuthResponse> signin(@Body AuthRequest request);

    @retrofit2.http.POST("/api/auth/send-code")
    retrofit2.Call<AuthResponse> sendCode(@retrofit2.http.Query("email") String email);

    @retrofit2.http.POST("/api/auth/forgot-password")
    retrofit2.Call<AuthResponse> forgotPassword(@retrofit2.http.Query("email") String email);

    @retrofit2.http.POST("/api/auth/reset-password")
    retrofit2.Call<AuthResponse> resetPassword(@retrofit2.http.Body com.vn.jet.mosco.model.ResetPasswordRequest request);
}

package com.vn.jet.mosco.network;

import com.vn.jet.mosco.model.AuthRequest;
import com.vn.jet.mosco.model.AuthResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApiService {

    @POST("/api/auth/signup")
    Call<AuthResponse> signup(@Body AuthRequest request);

    @POST("/api/auth/signin")
    Call<AuthResponse> signin(@Body AuthRequest request);
}

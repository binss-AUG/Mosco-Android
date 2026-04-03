package com.vn.jet.mosco.network;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.Gson;
import com.vn.jet.mosco.model.AuthRequest;
import com.vn.jet.mosco.model.AuthResponse;
import com.vn.jet.mosco.utils.Resource;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {

    private final AuthApiService apiService;
    private static volatile AuthRepository instance;

    private AuthRepository(Context context) {
        apiService = ApiClient.getClient(context).create(AuthApiService.class);
    }

    public static AuthRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (AuthRepository.class) {
                if (instance == null) {
                    instance = new AuthRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void signIn(String username, String password,
                       MutableLiveData<Resource<AuthResponse>> result) {
        result.setValue(Resource.loading());
        AuthRequest request = new AuthRequest(username, password, true);
        apiService.signin(request).enqueue(createCallback(result));
    }

    public void signUp(String username, String email, String password, String code,
                       MutableLiveData<Resource<AuthResponse>> result) {
        result.setValue(Resource.loading());
        AuthRequest request = new AuthRequest(username, email, password, code);
        apiService.signup(request).enqueue(createCallback(result));
    }

    public void sendVerificationCode(String email, MutableLiveData<Resource<AuthResponse>> result) {
        result.setValue(Resource.loading());
        apiService.sendCode(email).enqueue(createCallback(result));
    }

    private Callback<AuthResponse> createCallback(MutableLiveData<Resource<AuthResponse>> result) {
        return new Callback<AuthResponse>() {
            @Override
            public void onResponse(@NonNull Call<AuthResponse> call,
                                   @NonNull Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    result.setValue(Resource.success(response.body()));
                } else {
                    String message = "An unexpected error occurred.";
                    try {
                        if (response.errorBody() != null) {
                            AuthResponse err = new Gson().fromJson(
                                    response.errorBody().string(), AuthResponse.class);
                            if (err != null && err.getMessage() != null) {
                                message = err.getMessage();
                            }
                        }
                    } catch (Exception ignored) {}
                    result.setValue(Resource.error(message));
                }
            }

            @Override
            public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                result.setValue(Resource.error("Cannot connect to server."));
            }
        };
    }
}

package com.vn.jet.mosco;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.vn.jet.mosco.model.AuthResponse;
import com.vn.jet.mosco.network.AuthRepository;
import com.vn.jet.mosco.utils.Resource;

public class SignInViewModel extends AndroidViewModel {

    private final AuthRepository repository;
    private final MutableLiveData<Resource<AuthResponse>> signInResult = new MutableLiveData<>();

    public SignInViewModel(@NonNull Application application) {
        super(application);
        repository = AuthRepository.getInstance(application);
    }

    public LiveData<Resource<AuthResponse>> getSignInResult() {
        return signInResult;
    }

    public void signIn(String username, String password) {
        repository.signIn(username, password, signInResult);
    }
}

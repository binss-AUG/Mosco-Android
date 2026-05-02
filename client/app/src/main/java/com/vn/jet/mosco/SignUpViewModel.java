package com.vn.jet.mosco;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import android.os.CountDownTimer;

import com.vn.jet.mosco.model.AuthResponse;
import com.vn.jet.mosco.network.AuthRepository;
import com.vn.jet.mosco.utils.Resource;

/**
 * ViewModel for SignUpActivity.
 * Manages:
 * - API sign-up call via AuthRepository + Resource pattern
 * - Countdown timer for "Send Code" button (survives config change)
 */
public class SignUpViewModel extends AndroidViewModel {

    private final MutableLiveData<Boolean> isTimerRunning = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> codeSentOnce = new MutableLiveData<>(false);
    private final MutableLiveData<Long> timeLeftMillis = new MutableLiveData<>(0L);
    private CountDownTimer countDownTimer;

    private final AuthRepository repository;
    private final MutableLiveData<Resource<AuthResponse>> signUpResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<AuthResponse>> sendCodeResult = new MutableLiveData<>();

    public SignUpViewModel(@NonNull Application application) {
        super(application);
        repository = AuthRepository.getInstance(application);
    }

    public LiveData<Boolean> getIsTimerRunning() {
        return isTimerRunning;
    }

    public LiveData<Boolean> getCodeSentOnce() {
        return codeSentOnce;
    }

    public LiveData<Long> getTimeLeftMillis() {
        return timeLeftMillis;
    }

    public LiveData<Resource<AuthResponse>> getSignUpResult() {
        return signUpResult;
    }

    public LiveData<Resource<AuthResponse>> getSendCodeResult() {
        return sendCodeResult;
    }

    public void startCountdown() {
        if (Boolean.TRUE.equals(isTimerRunning.getValue())) return;

        isTimerRunning.setValue(true);
        codeSentOnce.setValue(true);
        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftMillis.setValue(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                isTimerRunning.setValue(false);
                timeLeftMillis.setValue(0L);
            }
        }.start();
    }

    /**
     * Calls the real API via AuthRepository.
     * Result is observed through getSignUpResult() LiveData.
     */
    public void signUpUser(String username, String email, String password, String code) {
        repository.signUp(username, email, password, code, signUpResult);
    }

    public void sendVerificationCode(String email) {
        repository.sendVerificationCode(email, sendCodeResult);
    }

    public void socialLogin(com.vn.jet.mosco.model.SocialAuthRequest request) {
        repository.socialLogin(request, signUpResult);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}

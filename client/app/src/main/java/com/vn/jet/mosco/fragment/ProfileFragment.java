package com.vn.jet.mosco.fragment;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.vn.jet.mosco.ForgotPasswordActivity;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.MainActivity;
import com.vn.jet.mosco.SignInActivity;
import com.vn.jet.mosco.model.UserStats;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ProfileFragment - Manages user profile display, stats, and account actions.
 * Standardized English UI and Static background (bithw).
 * Custom Logout Dialog styled to match Spin confirmation.
 */
public class ProfileFragment extends Fragment {

    private TextView tvUsername, tvEmail, tvCoins, tvDiamonds;
    private View btnLogout, btnChangePassword, btnInventory;
    private SessionManager sessionManager;
    private GameApiService gameApiService;

    public ProfileFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        initViews(view);
        setupSession();
        setupListeners();
        fetchUserStats();
        return view;
    }

    private void initViews(View v) {
        tvUsername = v.findViewById(R.id.tv_username);
        tvEmail = v.findViewById(R.id.tv_email);
        tvCoins = v.findViewById(R.id.tv_coins);
        tvDiamonds = v.findViewById(R.id.tv_diamonds);
        btnLogout = v.findViewById(R.id.btn_logout);
        btnChangePassword = v.findViewById(R.id.btn_change_password);
        btnInventory = v.findViewById(R.id.btn_inventory);
    }

    private void setupSession() {
        sessionManager = new SessionManager(requireContext());
        gameApiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        tvUsername.setText(sessionManager.getUsername());
        tvEmail.setText(sessionManager.getEmail());
    }

    private void setupListeners() {
        btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());

        btnChangePassword.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ForgotPasswordActivity.class);
            startActivity(intent);
        });

        btnInventory.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                BottomNavigationView nav = getActivity().findViewById(R.id.bottom_navigation);
                if (nav != null) {
                    nav.setSelectedItemId(R.id.nav_collect);
                }
            }
        });
    }

    private void fetchUserStats() {
        Long userId = sessionManager.getUserId();
        if (userId == null) return;

        gameApiService.getUserStats(userId).enqueue(new Callback<UserStats>() {
            @Override
            public void onResponse(Call<UserStats> call, Response<UserStats> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserStats stats = response.body();
                    tvCoins.setText(String.format("%,d", stats.getCoins() != null ? stats.getCoins() : 0));
                    tvDiamonds.setText(String.format("%,d", stats.getDiamonds() != null ? stats.getDiamonds() : 0));
                }
            }

            @Override
            public void onFailure(Call<UserStats> call, Throwable t) {
                Log.e("ProfileFragment", "Error fetching stats", t);
            }
        });
    }

    private void showLogoutConfirmationDialog() {
        if (getContext() == null) return;
        
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_logout_confirm, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            dialog.dismiss();
            sessionManager.clearSession();
            Intent intent = new Intent(getActivity(), SignInActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().finish();
                getActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        dialog.show();
    }
}
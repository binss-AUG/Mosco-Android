package com.vn.jet.mosco.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vn.jet.mosco.R;

public class ProfileMenuFragment extends Fragment {

    public interface OnMenuActionListener {
        void onForgotPassword();
        void onSwitchAccount();
        void onSettings();
        void onLogout();
    }

    private OnMenuActionListener listener;

    public void setOnMenuActionListener(OnMenuActionListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Back button
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });



        setupMenuItem(view.findViewById(R.id.menu_forgot_password), 
            getString(R.string.auth_title_recovery), 
            "Recover or reset your security credentials", 
            v -> { if(listener != null) listener.onForgotPassword(); });

        setupMenuItem(view.findViewById(R.id.menu_switch_account), 
            "Switch Account", 
            "Login with a different identity", 
            v -> { if(listener != null) listener.onSwitchAccount(); });

        setupMenuItem(view.findViewById(R.id.menu_settings), 
            getString(R.string.profile_action_settings), 
            "Audio, Theme, and System preferences", 
            v -> { if(listener != null) listener.onSettings(); });

        setupMenuItem(view.findViewById(R.id.menu_logout), 
            getString(R.string.profile_action_logout), 
            "Securely terminate your current session", 
            v -> { if(listener != null) listener.onLogout(); });
    }

    private void setupMenuItem(View container, String title, String desc, View.OnClickListener clickListener) {
        if (container == null) return;
        TextView tvTitle = container.findViewById(R.id.tv_ghost_title);
        TextView tvDesc = container.findViewById(R.id.tv_ghost_desc);
        
        if (tvTitle != null) tvTitle.setText(title);
        if (tvDesc != null) tvDesc.setText(desc);
        
        container.setOnClickListener(clickListener);
    }
}

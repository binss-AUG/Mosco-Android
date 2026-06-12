package com.vn.jet.mosco.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.vn.jet.mosco.MainActivity;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.SignInActivity;
import com.vn.jet.mosco.model.AuthResponse;
import com.vn.jet.mosco.model.SavedAccount;
import com.vn.jet.mosco.utils.DatabaseLoader;
import com.vn.jet.mosco.utils.SessionManager;

import java.util.List;

public class AccountSwitchBottomSheet extends BottomSheetDialogFragment {

    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_bottom_sheet_account_switch, container, false);
        sessionManager = new SessionManager(requireContext());

        RecyclerView rvAccounts = view.findViewById(R.id.rv_accounts);
        rvAccounts.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<SavedAccount> accounts = sessionManager.getSavedAccounts();
        long currentUserId = sessionManager.getUserId() != null ? sessionManager.getUserId() : -1L;

        AccountAdapter adapter = new AccountAdapter(accounts, currentUserId, account -> {
            if (account.getUserId() == currentUserId) {
                dismiss();
                return;
            }

            // Thực hiện Switch Account
            AuthResponse.UserData newSessionData = new AuthResponse.UserData();
            newSessionData.setId(account.getUserId());
            newSessionData.setUsername(account.getUsername());
            newSessionData.setEmail(account.getEmail());
            newSessionData.setIngameName(account.getIngameName());
            newSessionData.setAvatarId(account.getAvatarId());
            newSessionData.setToken(account.getToken());

            sessionManager.saveSession(newSessionData);
            DatabaseLoader.clearUserCache();

            dismiss();

            Intent intent = new Intent(requireActivity(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });

        rvAccounts.setAdapter(adapter);

        view.findViewById(R.id.btn_add_account).setOnClickListener(v -> {
            dismiss();
            Intent intent = new Intent(requireActivity(), SignInActivity.class);
            startActivity(intent);
        });

        return view;
    }

    private static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {

        private final List<SavedAccount> accounts;
        private final long currentUserId;
        private final OnAccountClickListener listener;

        public interface OnAccountClickListener {
            void onClick(SavedAccount account);
        }

        public AccountAdapter(List<SavedAccount> accounts, long currentUserId, OnAccountClickListener listener) {
            this.accounts = accounts;
            this.currentUserId = currentUserId;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_account, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SavedAccount account = accounts.get(position);
            holder.tvName.setText(account.getIngameName() != null ? account.getIngameName() : account.getUsername());
            holder.tvEmail.setText(account.getEmail());

            Context context = holder.itemView.getContext();

            // Avatar placeholder setup
            int placeholderRes = R.drawable.ic_user;
            if ("google".equals(account.getAuthType())) {
                holder.ivAuthType.setImageResource(R.drawable.ic_google); // Giả sử có icon này
            } else if ("discord".equals(account.getAuthType())) {
                holder.ivAuthType.setImageResource(R.drawable.ic_discord); // Giả sử có icon này
            } else {
                holder.ivAuthType.setImageResource(R.drawable.ic_email);
            }

            Glide.with(context)
                    .load(account.getAvatarId() != null ? account.getAvatarId() : placeholderRes)
                    .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .into(holder.ivAvatar);

            if (account.getUserId() == currentUserId) {
                holder.ivActiveIndicator.setVisibility(View.VISIBLE);
                holder.itemView.setAlpha(1.0f);
            } else {
                holder.ivActiveIndicator.setVisibility(View.GONE);
                holder.itemView.setAlpha(0.7f);
            }

            holder.itemView.setOnClickListener(v -> listener.onClick(account));
        }

        @Override
        public int getItemCount() {
            return accounts.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvName;
            TextView tvEmail;
            ImageView ivAuthType;
            ImageView ivActiveIndicator;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                tvName = itemView.findViewById(R.id.tv_name);
                tvEmail = itemView.findViewById(R.id.tv_email);
                ivAuthType = itemView.findViewById(R.id.iv_auth_type);
                ivActiveIndicator = itemView.findViewById(R.id.iv_active_indicator);
            }
        }
    }
}

package com.vn.jet.mosco.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vn.jet.mosco.R;
import com.vn.jet.mosco.adapter.MailboxAdapter;
import com.vn.jet.mosco.model.UserMail;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.SessionManager;
import com.vn.jet.mosco.utils.NumberUtils;
import com.vn.jet.mosco.view.InventoryFilterBar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.ResponseBody;

/**
 * Fragment quản lý Thư Hệ Thống (System Mails).
 * Tách biệt hoàn toàn khỏi CollectionFragment (Decoupled & Standalone).
 */
public class SystemMailFragment extends Fragment implements MailboxAdapter.OnMailClickListener {

    private RecyclerView rvSystemMails;
    private MailboxAdapter mailboxAdapter;
    private List<UserMail> systemMailsList = new ArrayList<>();
    private List<UserMail> filteredMailsList = new ArrayList<>();
    private InventoryFilterBar filterBar;
    private TextView tvSystemMailsCount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mailbox_system, container, false);

        rvSystemMails = view.findViewById(R.id.rv_system_mails);
        filterBar = view.findViewById(R.id.filter_bar_system_mails);
        tvSystemMailsCount = view.findViewById(R.id.tv_system_mails_count);

        setupFilterBar();

        rvSystemMails.setLayoutManager(new LinearLayoutManager(getContext()));
        mailboxAdapter = new MailboxAdapter(filteredMailsList, this);
        rvSystemMails.setAdapter(mailboxAdapter);

        View btnReceiveAll = view.findViewById(R.id.btn_receive_all);
        if (btnReceiveAll != null) {
            btnReceiveAll.setOnClickListener(v -> receiveAllMails());
        }

        loadSystemMails();

        return view;
    }

    private void setupFilterBar() {
        if (filterBar == null) return;
        filterBar.setSortOptions(new String[] { 
            CollectionFragment.SORT_NEWEST, 
            CollectionFragment.SORT_LOWEST_NO, 
            CollectionFragment.SORT_HIGHEST_NO 
        });
        filterBar.setListener(new InventoryFilterBar.OnFilterChangeListener() {
            @Override
            public void onFilterChanged(String sortOption, boolean isAscending) {
                filterMails();
            }

            @Override
            public void onFilterRequested() {
                // Filter requests can be delegated or handled locally
                // In standalone mode, we apply sorting locally.
                filterMails();
            }
        });
    }

    private void loadSystemMails() {
        Context context = getContext();
        if (context == null) return;

        GameApiService api = ApiClient.getClient(context).create(GameApiService.class);
        Long userId = new SessionManager(context).getUserId();
        if (userId == null) return;

        api.getUserMails(userId).enqueue(new Callback<List<UserMail>>() {
            @Override
            public void onResponse(Call<List<UserMail>> call, Response<List<UserMail>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    systemMailsList.clear();
                    systemMailsList.addAll(response.body());
                    filterMails();
                }
            }

            @Override
            public void onFailure(Call<List<UserMail>> call, Throwable t) {
                Log.e("SystemMailFragment", "Failed to load user mails", t);
                if (getContext() != null) {
                    Toast.makeText(getContext(), getString(R.string.collection_msg_error_mailbox), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void filterMails() {
        filteredMailsList.clear();
        String currentSort = filterBar != null ? filterBar.getSortOption() : CollectionFragment.SORT_NEWEST;
        
        for (UserMail mail : systemMailsList) {
            if (!mail.isReceived()) {
                filteredMailsList.add(mail);
            }
        }
        
        filteredMailsList.sort((a, b) -> {
            int res;
            if (CollectionFragment.SORT_LOWEST_NO.equals(currentSort)) {
                res = Integer.compare(a.getQuantity() != null ? a.getQuantity() : 0,
                        b.getQuantity() != null ? b.getQuantity() : 0);
            } else if (CollectionFragment.SORT_HIGHEST_NO.equals(currentSort)) {
                res = Integer.compare(b.getQuantity() != null ? b.getQuantity() : 0,
                        a.getQuantity() != null ? a.getQuantity() : 0);
            } else {
                res = b.getId().compareTo(a.getId());
            }
            return filterBar != null && filterBar.isAscending() ? res : -res; // Support ascending/descending
        });

        mailboxAdapter.notifyDataSetChanged();

        if (tvSystemMailsCount != null) {
            tvSystemMailsCount.setText(String.valueOf(filteredMailsList.size()));
        }
    }

    private void receiveAllMails() {
        Context context = getContext();
        if (context == null) return;

        Long userId = new SessionManager(context).getUserId();
        if (userId == null) return;

        GameApiService api = ApiClient.getClient(context).create(GameApiService.class);
        api.claimAllMails(userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    // Mark all current system mails as received locally
                    for (UserMail m : systemMailsList) {
                        m.setReceived(true);
                    }
                    filterMails();
                    Toast.makeText(context, "Đã nhận thành công tất cả quà!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Nhận quà thất bại!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(context, "Lỗi kết nối máy chủ!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMailClick(UserMail mail) {
        String giftInfo = (mail.getItemCode() != null && mail.getQuantity() != null)
                ? getString(R.string.social_gift_summary_format, mail.getItemCode(),
                NumberUtils.format(requireContext(), mail.getQuantity()))
                : "";

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(),
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(mail.getTitle())
                .setMessage(mail.getContent() + "\n" + giftInfo);

        if (!mail.isReceived()) {
            builder.setPositiveButton(getString(R.string.mailbox_action_claim), (dialog, which) -> claimSingleMail(mail));
            builder.setNegativeButton(getString(R.string.mailbox_action_later), null);
        } else {
            builder.setPositiveButton(getString(R.string.mailbox_action_received), null);
            builder.setNegativeButton(getString(R.string.mailbox_action_close), null);
        }

        builder.show();
    }

    private void claimSingleMail(UserMail mail) {
        Context context = getContext();
        if (context == null) return;

        GameApiService api = ApiClient.getClient(context).create(GameApiService.class);
        api.claimMail(mail.getId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    mail.setReceived(true);
                    filterMails();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("SystemMailFragment", "Failed to claim mail", t);
            }
        });
    }
}

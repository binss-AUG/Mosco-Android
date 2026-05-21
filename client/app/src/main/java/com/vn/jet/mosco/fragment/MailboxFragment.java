package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.vn.jet.mosco.R;

/**
 * Fragment máy chủ quản lý 3 Tab Hộp thư: System Mails, Player Gifts, Private Chats.
 * Tách biệt hoàn toàn khỏi CollectionFragment (Decoupled & Standalone).
 */
public class MailboxFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mailbox, container, false);

        setupCommonHeader(view);

        tabLayout = view.findViewById(R.id.tab_layout_mailbox);
        viewPager = view.findViewById(R.id.view_pager_mailbox);

        if (viewPager != null && tabLayout != null) {
            // Vô hiệu hóa thao tác vuốt thủ công để tránh xung đột với cuộn danh sách RecyclerView con
            viewPager.setUserInputEnabled(false);
            viewPager.setOffscreenPageLimit(3);
            viewPager.setAdapter(new MailboxPagerAdapter(requireActivity()));

            new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
                switch (position) {
                    case 0:
                        tab.setText(getString(R.string.mailbox_tab_system));
                        break;
                    case 1:
                        tab.setText(getString(R.string.mailbox_tab_player_gifts));
                        break;
                    case 2:
                        tab.setText(getString(R.string.mailbox_tab_private_chats));
                        break;
                }
            }).attach();
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void setupCommonHeader(View root) {
        View header = root.findViewById(R.id.layout_header_mailbox);
        if (header != null) {
            TextView tvTitle = header.findViewById(R.id.tv_header_title);
            if (tvTitle != null) {
                tvTitle.setText(getString(R.string.collection_tab_mailbox));
            }
            // Back arrow (đồng bộ với các sub-screen khác)
            ImageView btnBack = header.findViewById(R.id.btn_back_common);
            if (btnBack != null) {
                btnBack.setOnClickListener(v -> {
                    if (getActivity() != null) {
                        getActivity().getSupportFragmentManager().popBackStack();
                    }
                });
            }
        }
    }

    private static class MailboxPagerAdapter extends FragmentStateAdapter {
        public MailboxPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new SystemMailFragment();
                case 1:
                    return new PlayerGiftsFragment();
                case 2:
                    return new PrivateChatListFragment();
                default:
                    return new SystemMailFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}

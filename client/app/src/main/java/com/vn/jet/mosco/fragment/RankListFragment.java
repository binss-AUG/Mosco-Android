package com.vn.jet.mosco.fragment;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.adapter.RankAdapter;
import com.vn.jet.mosco.utils.SkeletonHelper;
import com.vn.jet.mosco.network.ApiClient;
import com.vn.jet.mosco.network.GameApiService;
import com.vn.jet.mosco.utils.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RankListFragment extends Fragment {
    private static final String TAG = "RankListFragment";
    private static final String ARG_RANK_TYPE = "rankType";

    private RecyclerView rvRankList;
    private TextView tvEmpty;
    private View lottieLoading;
    private View layoutPodiumHeader;
    
    // Podium Views
    private View layoutGold, layoutSilver, layoutBronze;
    private ImageView ivAvatarGold, ivAvatarSilver, ivAvatarBronze;
    private TextView tvNameGold, tvNameSilver, tvNameBronze;
    private TextView tvValueGold, tvValueSilver, tvValueBronze;
    private View flAvatarGold, flAvatarSilver, flAvatarBronze;

    private RankAdapter rankAdapter;
    private String rankType;
    private JSONObject myRankData;
    private List<JSONObject> cachedPodiumData = new ArrayList<>();
    private List<JSONObject> cachedRestData = new ArrayList<>();
    private boolean isDataLoaded = false;
    private boolean isLoading = false;
    private boolean isPodiumAnimated = false;
    private long lastUpdatedTime = 0;
    private static final long CACHE_EXPIRY = 2 * 60 * 1000;

    public static RankListFragment newInstance(String rankType) {
        RankListFragment fragment = new RankListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RANK_TYPE, rankType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            rankType = getArguments().getString(ARG_RANK_TYPE, "level");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rank_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvRankList = view.findViewById(R.id.rv_rank_list);
        tvEmpty = view.findViewById(R.id.tv_rank_empty);
        lottieLoading = view.findViewById(R.id.shimmer_friend_skeleton);
        layoutPodiumHeader = view.findViewById(R.id.layout_podium_header);

        // Init Podium Views
        if (layoutPodiumHeader != null) {
            layoutGold = layoutPodiumHeader.findViewById(R.id.layout_podium_gold);
            layoutSilver = layoutPodiumHeader.findViewById(R.id.layout_podium_silver);
            layoutBronze = layoutPodiumHeader.findViewById(R.id.layout_podium_bronze);

            ivAvatarGold = layoutPodiumHeader.findViewById(R.id.iv_avatar_gold);
            ivAvatarSilver = layoutPodiumHeader.findViewById(R.id.iv_avatar_silver);
            ivAvatarBronze = layoutPodiumHeader.findViewById(R.id.iv_avatar_bronze);

            tvNameGold = layoutPodiumHeader.findViewById(R.id.tv_name_gold);
            tvNameSilver = layoutPodiumHeader.findViewById(R.id.tv_name_silver);
            tvNameBronze = layoutPodiumHeader.findViewById(R.id.tv_name_bronze);

            tvValueGold = layoutPodiumHeader.findViewById(R.id.tv_value_gold);
            tvValueSilver = layoutPodiumHeader.findViewById(R.id.tv_value_silver);
            tvValueBronze = layoutPodiumHeader.findViewById(R.id.tv_value_bronze);

            flAvatarGold = layoutPodiumHeader.findViewById(R.id.fl_avatar_gold);
            flAvatarSilver = layoutPodiumHeader.findViewById(R.id.fl_avatar_silver);
            flAvatarBronze = layoutPodiumHeader.findViewById(R.id.fl_avatar_bronze);
        }

        rvRankList.setLayoutManager(new LinearLayoutManager(requireContext()));

        if (lottieLoading instanceof ViewGroup) {
            ViewGroup container = lottieLoading.findViewById(R.id.ll_skeleton_container);
            SkeletonHelper.populateShimmerContainer(container, R.layout.item_rank_entry, 6);
        }

        // Tải dữ liệu ngay khi Fragment vừa tạo xong UI (để ViewPager2 preload mượt mà)
        if (!isDataLoaded && !isLoading) {
            loadRankData();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        
        long currentTime = System.currentTimeMillis();
        boolean isCacheExpired = (currentTime - lastUpdatedTime) > CACHE_EXPIRY;

        if (isDataLoaded && !isCacheExpired) {
            showRankListWithAnimation();
        } else if (!isLoading) {
            if (!isDataLoaded) {
                if (rvRankList != null) {
                    rvRankList.setVisibility(View.INVISIBLE);
                    rvRankList.setAlpha(0f);
                }
                if (layoutPodiumHeader != null) layoutPodiumHeader.setVisibility(View.GONE);
            }
            loadRankData();
        }
    }

    public void refreshData() {
        isDataLoaded = false;
        lastUpdatedTime = 0;
        isPodiumAnimated = false;
        loadRankData();
    }

    private void showRankListWithAnimation() {
        if (rvRankList == null) return;
        
        rvRankList.setVisibility(View.VISIBLE);
        rvRankList.setAlpha(1f);
        
        if (rankAdapter != null) rankAdapter.notifyDataSetChanged();
        
        if (getParentFragment() instanceof com.vn.jet.mosco.fragment.RankFragment) {
            ((com.vn.jet.mosco.fragment.RankFragment) getParentFragment()).updateMyRank(myRankData, rankType);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (rvRankList != null) {
            rvRankList.animate().cancel();
        }
    }

    private void loadRankData() {
        if (requireContext() == null) return;

        GameApiService apiService = ApiClient.getClient(requireContext()).create(GameApiService.class);
        Call<ResponseBody> call;

        switch (rankType) {
            case "social":
                call = apiService.getRankBySocial();
                break;
            case "collection":
                call = apiService.getRankByCollection();
                break;
            case "streak":
                call = apiService.getRankByStreak();
                break;
            case "fame":
                call = apiService.getRankByFame();
                break;
            case "duo-streak":
                call = apiService.getRankByDuoStreak();
                break;
            case "level":
            default:
                call = apiService.getRankByLevel();
                break;
        }

        if (isLoading) return;
        isLoading = true;
        if (lottieLoading != null) lottieLoading.setVisibility(View.VISIBLE);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                isLoading = false;
                if (lottieLoading != null) lottieLoading.setVisibility(View.GONE);
                
                if (getParentFragment() instanceof RankFragment) {
                    ((RankFragment) getParentFragment()).stopRefresh();
                }

                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseStr = response.body().string();
                        JSONObject rootJson = new JSONObject(responseStr);
                        JSONArray array = rootJson.optJSONArray("data");
                        
                        if (array == null || array.length() == 0) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvRankList.setVisibility(View.GONE);
                            if (layoutPodiumHeader != null) layoutPodiumHeader.setVisibility(View.GONE);
                            return;
                        }

                        List<JSONObject> rankings = new java.util.ArrayList<>();
                        
                        SessionManager sm = new SessionManager(requireContext());
                        Long currentUserId = sm.getUserId();

                        myRankData = null;

                        int limit = Math.min(array.length(), 99);
                        for (int i = 0; i < limit; i++) {
                            JSONObject obj = array.getJSONObject(i);
                            obj.put("rank", i + 1);
                            rankings.add(obj);

                            if (currentUserId != null && obj.optLong("userId") == currentUserId) {
                                myRankData = obj;
                            }
                        }

                        if (!rankings.isEmpty()) {
                            lastUpdatedTime = System.currentTimeMillis();
                            cachedPodiumData.clear();
                            cachedRestData.clear();
                            
                            for (int j = 0; j < rankings.size(); j++) {
                                if (j < 3) cachedPodiumData.add(rankings.get(j));
                                else cachedRestData.add(rankings.get(j));
                            }
                            
                            bindPodiumHeader(cachedPodiumData);
                            
                            rankAdapter = new RankAdapter(cachedRestData, rankType, currentUserId);
                            rvRankList.setAdapter(rankAdapter);
                            
                            isDataLoaded = true;
                            showRankListWithAnimation();
                        } else {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvRankList.setVisibility(View.GONE);
                            if (layoutPodiumHeader != null) layoutPodiumHeader.setVisibility(View.GONE);
                        }
                    } else {
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvRankList.setVisibility(View.GONE);
                        if (layoutPodiumHeader != null) layoutPodiumHeader.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing rank data: " + rankType, e);
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvRankList.setVisibility(View.GONE);
                    if (layoutPodiumHeader != null) layoutPodiumHeader.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                isLoading = false;
                if (lottieLoading != null) lottieLoading.setVisibility(View.GONE);
                if (getParentFragment() instanceof RankFragment) {
                    ((RankFragment) getParentFragment()).stopRefresh();
                }
                if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                if (layoutPodiumHeader != null) layoutPodiumHeader.setVisibility(View.GONE);
            }
        });
    }

    private void bindPodiumHeader(List<JSONObject> top3List) {
        if (layoutPodiumHeader == null) return;

        if (top3List == null || top3List.isEmpty()) {
            layoutPodiumHeader.setVisibility(View.GONE);
            return;
        }

        layoutPodiumHeader.setVisibility(View.VISIBLE);

        if (top3List.size() > 0) {
            bindPillar(requireContext(), top3List.get(0), ivAvatarGold, tvNameGold, tvValueGold);
            layoutGold.setVisibility(View.VISIBLE);
        } else {
            layoutGold.setVisibility(View.INVISIBLE);
        }

        if (top3List.size() > 1) {
            bindPillar(requireContext(), top3List.get(1), ivAvatarSilver, tvNameSilver, tvValueSilver);
            layoutSilver.setVisibility(View.VISIBLE);
        } else {
            layoutSilver.setVisibility(View.INVISIBLE);
        }

        if (top3List.size() > 2) {
            bindPillar(requireContext(), top3List.get(2), ivAvatarBronze, tvNameBronze, tvValueBronze);
            layoutBronze.setVisibility(View.VISIBLE);
        } else {
            layoutBronze.setVisibility(View.INVISIBLE);
        }

        if (!isPodiumAnimated) {
            runEntranceAnimation();
            isPodiumAnimated = true;
        }
    }

    private void bindPillar(Context context, JSONObject user, ImageView ivAvatar, TextView tvName, TextView tvValue) {
        tvName.setText(user.optString("ingameName", "Unknown"));
        int value = user.optInt("value", 0);
        
        switch (rankType) {
            case "level": 
                tvValue.setText(String.format("Lv. %d", value));
                break;
            case "wealth": 
                tvValue.setText(com.vn.jet.mosco.utils.NumberUtils.format(context, (long)value)); 
                break;
            case "collection": 
                tvValue.setText(String.format("%d Objets", value));
                break;
            case "streak":
                tvValue.setText(String.format("%d Days", value));
                break;
            case "fame":
                tvValue.setText(String.format("%d Likes", value));
                break;
            case "social":
                tvValue.setText(String.format("%d Friends", value));
                break;
            case "duo-streak":
                tvValue.setText(String.format("%d Days", value));
                break;
        }

        String avatarId = user.optString("avatarId", "1");
        long userId = user.optLong("userId", -1L);
        com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(context, ivAvatar, userId, avatarId);

        View cvPartner = null;
        ImageView ivPartner = null;
        if (ivAvatar.getId() == R.id.iv_avatar_gold) {
            cvPartner = layoutPodiumHeader.findViewById(R.id.cv_partner_avatar_gold);
            ivPartner = layoutPodiumHeader.findViewById(R.id.iv_partner_avatar_gold);
        } else if (ivAvatar.getId() == R.id.iv_avatar_silver) {
            cvPartner = layoutPodiumHeader.findViewById(R.id.cv_partner_avatar_silver);
            ivPartner = layoutPodiumHeader.findViewById(R.id.iv_partner_avatar_silver);
        } else if (ivAvatar.getId() == R.id.iv_avatar_bronze) {
            cvPartner = layoutPodiumHeader.findViewById(R.id.cv_partner_avatar_bronze);
            ivPartner = layoutPodiumHeader.findViewById(R.id.iv_partner_avatar_bronze);
        }

        if (rankType.equals("duo-streak") && user.has("partnerAvatarId") && cvPartner != null && ivPartner != null) {
            cvPartner.setVisibility(View.VISIBLE);
            String partnerAvatarId = user.optString("partnerAvatarId", "1");
            long partnerId = user.optLong("partnerId", -1L);
            com.vn.jet.mosco.utils.AvatarUtils.loadAvatar(context, ivPartner, partnerId, partnerAvatarId);
        } else if (cvPartner != null) {
            cvPartner.setVisibility(View.GONE);
        }

        ivAvatar.setOnClickListener(v -> {
            if (userId != -1L) {
                com.vn.jet.mosco.utils.NavigationUtils.openProfile((androidx.fragment.app.FragmentActivity) context, userId);
            }
        });
        if (ivPartner != null) {
            ivPartner.setOnClickListener(v -> {
                long partnerId = user.optLong("partnerId", -1L);
                if (partnerId != -1L) {
                    com.vn.jet.mosco.utils.NavigationUtils.openProfile((androidx.fragment.app.FragmentActivity) context, partnerId);
                }
            });
        }
    }

    private void runEntranceAnimation() {
        if (layoutSilver == null || layoutBronze == null || layoutGold == null) return;
        prepareViewForAnimation(layoutSilver);
        prepareViewForAnimation(layoutBronze);
        prepareViewForAnimation(layoutGold);
        prepareAvatarForAnimation(flAvatarSilver);
        prepareAvatarForAnimation(flAvatarBronze);
        prepareAvatarForAnimation(flAvatarGold);

        animatePillar(layoutBronze, flAvatarBronze, 100);
        animatePillar(layoutSilver, flAvatarSilver, 250);
        animatePillar(layoutGold, flAvatarGold, 400);
    }

    private void prepareViewForAnimation(View view) {
        view.animate().cancel();
        view.setTranslationY(200f);
        view.setAlpha(0f);
    }

    private void prepareAvatarForAnimation(View view) {
        view.animate().cancel();
        Object oldHover = view.getTag();
        if (oldHover instanceof ObjectAnimator) {
            ((ObjectAnimator) oldHover).cancel();
        }
        view.setTag(null);
        view.setScaleX(0f);
        view.setScaleY(0f);
        view.setTranslationY(0f);
    }

    private void animatePillar(View pillar, View avatar, long startDelay) {
        pillar.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setStartDelay(startDelay)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                avatar.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(500)
                    .setInterpolator(new OvershootInterpolator(1.5f))
                    .withEndAction(() -> {
                        ObjectAnimator hover = ObjectAnimator.ofFloat(avatar, "translationY", 0f, -15f);
                        hover.setRepeatCount(ObjectAnimator.INFINITE);
                        hover.setRepeatMode(ObjectAnimator.REVERSE);
                        hover.setDuration(1200);
                        hover.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                        avatar.setTag(hover);
                        hover.start();
                    })
                    .start();
            })
            .start();
    }
}

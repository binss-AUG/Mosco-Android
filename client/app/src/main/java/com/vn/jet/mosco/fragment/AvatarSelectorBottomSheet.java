package com.vn.jet.mosco.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.vn.jet.mosco.R;
import com.vn.jet.mosco.adapter.AvatarSelectorAdapter;
import com.vn.jet.mosco.utils.DatabaseLoader;
import org.json.JSONObject;
import java.util.List;

/**
 * Bottom Sheet to choose an Objet as an Avatar.
 */
public class AvatarSelectorBottomSheet extends BottomSheetDialogFragment {

    private final String selectedId;
    private final OnAvatarSelectedListener listener;

    public interface OnAvatarSelectedListener {
        void onAvatarSelected(String collectionId);
    }

    public AvatarSelectorBottomSheet(String selectedId, OnAvatarSelectedListener listener) {
        this.selectedId = selectedId;
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_avatar_selector, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        RecyclerView rv = view.findViewById(R.id.rv_avatar_list);
        rv.setLayoutManager(new GridLayoutManager(getContext(), 4));

        // Load available avatars based on user's inventory
        List<JSONObject> ownedCards = new java.util.ArrayList<>();
        if (DatabaseLoader.cachedUserInventory != null) {
            java.util.Set<String> uniqueIds = new java.util.HashSet<>();
            for (DatabaseLoader.UserInventoryItem item : DatabaseLoader.cachedUserInventory) {
                if (uniqueIds.add(item.collectionId)) {
                    // item.collectionId actually holds the UUID format from the database UserCard entity
                    JSONObject card = DatabaseLoader.findById(requireContext(), item.collectionId);
                    if (card != null) {
                        ownedCards.add(card);
                    }
                }
            }
        }
        
        // Add default generic avatar if no cards owned? We can just ensure they see what they have.
        if (ownedCards.isEmpty()) {
            JSONObject defaultObj = DatabaseLoader.findByCollectionId(requireContext(), "Binary02 JiYeon 503Z");
            if(defaultObj != null) ownedCards.add(defaultObj);
        }

        AvatarSelectorAdapter adapter = new AvatarSelectorAdapter(ownedCards, selectedId, collectionId -> {
            if (listener != null) listener.onAvatarSelected(collectionId);
            dismiss(); // Automatically close after selection
        });
        
        rv.setAdapter(adapter);
    }
}

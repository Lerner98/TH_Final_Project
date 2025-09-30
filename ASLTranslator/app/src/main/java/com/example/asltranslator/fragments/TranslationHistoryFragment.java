package com.example.asltranslator.fragments;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.asltranslator.R;
import com.example.asltranslator.adapters.HistoryAdapter;
import com.example.asltranslator.models.TranslationHistory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages translation history display from Firebase data.
 * Currently not shown in UI to avoid clutter; planned for a future activity with detailed accuracy, achievements, dates, and lesson/practice info.
 */
public class TranslationHistoryFragment extends Fragment {

    private RecyclerView rvHistory;
    private LinearLayout emptyState;
    private HistoryAdapter adapter;
    private List<TranslationHistory> historyList;

    private DatabaseReference mDatabase;
    private String userId;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_translation_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        initFirebase();
        loadHistory();
    }

    private void initViews(View view) {
        rvHistory = view.findViewById(R.id.rv_history);
        emptyState = view.findViewById(R.id.empty_state);

        historyList = new ArrayList<>();
        adapter = new HistoryAdapter(historyList);

        rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        rvHistory.setAdapter(adapter);
    }

    private void initFirebase() {
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    private void loadHistory() {
        Query query = mDatabase.child("history").child(userId)
                .orderByChild("timestamp")
                .limitToLast(50); // Last 50 translations

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                historyList.clear();

                for (DataSnapshot historySnapshot : snapshot.getChildren()) {
                    TranslationHistory history = historySnapshot.getValue(TranslationHistory.class);
                    if (history != null) {
                        historyList.add(history);
                    }
                }

                // Reverse to show newest first
                Collections.reverse(historyList);

                adapter.notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }

    private void updateEmptyState() {
        if (historyList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvHistory.setVisibility(View.VISIBLE);
        }
    }
}
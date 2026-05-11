package com.vn.jet.mosco;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Event screen — Currently locked (COMING SOON).
 * Placeholder until Event system is fully designed.
 */
public class MissionActivity extends MoscoBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mission);

        // Back button
        findViewById(R.id.btn_back_mission).setOnClickListener(v -> finish());
    }
}

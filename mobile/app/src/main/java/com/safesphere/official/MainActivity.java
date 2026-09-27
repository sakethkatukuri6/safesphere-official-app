package com.safesphere.official;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.safesphere.official.model.Models.*;
import com.safesphere.official.net.BackendClient;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private TextView tvModeStatus, tvIncidentId, tvVictimName, tvBloodType;
    private EditText etOtp;
    private MaterialButton btnSubmitOtp, btnSimulateCommand, btnSimulateField;
    private MaterialCardView cardIncident;

    private BackendClient client;
    private OfficialIncidentView currentFieldIncident;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvModeStatus = findViewById(R.id.tvModeStatus);
        cardIncident = findViewById(R.id.cardIncident);
        tvIncidentId = findViewById(R.id.tvIncidentId);
        tvVictimName = findViewById(R.id.tvVictimName);
        tvBloodType = findViewById(R.id.tvBloodType);
        
        etOtp = findViewById(R.id.etOtp);
        btnSubmitOtp = findViewById(R.id.btnSubmitOtp);
        btnSimulateCommand = findViewById(R.id.btnSimulateCommand);
        btnSimulateField = findViewById(R.id.btnSimulateField);

        client = new BackendClient("10.0.2.2", 8080); // 10.0.2.2 is localhost for Android emulator

        btnSimulateCommand.setOnClickListener(v -> simulateCommandDesk());
        btnSimulateField.setOnClickListener(v -> simulateFieldMode());
        btnSubmitOtp.setOnClickListener(v -> submitOtp());
    }

    private void simulateCommandDesk() {
        Toast.makeText(this, "Command Desk simulation not shown in Field Mode UI", Toast.LENGTH_SHORT).show();
    }

    private void simulateFieldMode() {
        // Randomize Capsule ID and OTP
        Random random = new Random();
        int caseNum = 100 + random.nextInt(900); // 100 to 999
        String capsuleId = "CR-" + caseNum;
        
        // Random 6-digit OTP
        int otpNum = 100000 + random.nextInt(900000); 
        String otp = String.valueOf(otpNum);

        OfficialIncidentView mock = new OfficialIncidentView();
        mock.capsuleId = capsuleId;
        mock.fsmState = FsmState.RESPONDER_ASSIGNED;
        
        mock.victimProfile = new VictimProfile();
        mock.victimProfile.name = "Jane Doe";
        mock.victimProfile.bloodType = "O+";
        mock.victimProfile.allergies = Arrays.asList("Penicillin");
        
        mock.silenceOtp = otp;
        mock.otpExpiresAt = "2026-09-27T23:59:59Z";

        currentFieldIncident = mock;
        
        // Update UI
        tvModeStatus.setVisibility(View.VISIBLE);
        cardIncident.setVisibility(View.VISIBLE);
        
        tvIncidentId.setText("INCIDENT: " + capsuleId + "               ⚠️");
        tvVictimName.setText("👤 Victim: " + mock.victimProfile.name);
        tvBloodType.setText(" " + mock.victimProfile.bloodType + " ");
        
        etOtp.setText("");
        
        Toast.makeText(this, "Assigned to " + capsuleId, Toast.LENGTH_SHORT).show();
    }

    private void submitOtp() {
        if (currentFieldIncident == null) {
            Toast.makeText(this, "No active field incident", Toast.LENGTH_SHORT).show();
            return;
        }
        String entered = etOtp.getText().toString().trim();
        if (!entered.equals(currentFieldIncident.silenceOtp)) {
            Toast.makeText(this, "Invalid OTP!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "OTP Verified! Alert Silenced.", Toast.LENGTH_LONG).show();
        etOtp.setText("");
        
        // Hide card on success
        cardIncident.setVisibility(View.GONE);
        tvModeStatus.setVisibility(View.GONE);
        currentFieldIncident = null;
    }
}

package com.safesphere.official.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Models {

    public enum FsmState {
        SAFE, SUSPICIOUS, CHECKING, EMERGENCY, VOLUNTEER_ASSIGNED,
        ESCALATING, RESPONDER_ASSIGNED, ON_SCENE, RESOLVED
    }

    public enum DispatchAction {
        FORCE_MATCH, CLOSE_INCIDENT
    }

    // Command Desk - Inbound
    public static class IncidentQueueView {
        @SerializedName("capsule_id") public String capsuleId;
        @SerializedName("fsm_state") public FsmState fsmState;
        public double latitude;
        public double longitude;
        @SerializedName("task_requirement") public String taskRequirement;
        @SerializedName("last_updated") public String lastUpdated;
    }

    // Command Desk - Outbound
    public static class DispatchDecisionEvent {
        @SerializedName("capsule_id") public String capsuleId;
        @SerializedName("staff_id") public String staffId;
        public DispatchAction action;
        @SerializedName("target_responder_id") public String targetResponderId;
        
        public DispatchDecisionEvent(String c, String s, DispatchAction a, String t) {
            this.capsuleId = c; this.staffId = s; this.action = a; this.targetResponderId = t;
        }
    }

    // Field Mode - Inbound
    public static class OfficialIncidentView {
        @SerializedName("capsule_id") public String capsuleId;
        @SerializedName("fsm_state") public FsmState fsmState;
        @SerializedName("victim_profile") public VictimProfile victimProfile;
        @SerializedName("live_location") public LiveLocation liveLocation;
        @SerializedName("hazard_notes") public String hazardNotes;
        @SerializedName("silence_otp") public String silenceOtp;
        @SerializedName("otp_expires_at") public String otpExpiresAt;
    }

    public static class VictimProfile {
        public String name;
        @SerializedName("blood_type") public String bloodType;
        public List<String> allergies;
        @SerializedName("known_conditions") public List<String> knownConditions;
        @SerializedName("emergency_contacts") public List<String> emergencyContacts;
    }

    public static class LiveLocation {
        public double latitude;
        public double longitude;
        @SerializedName("updated_at") public String updatedAt;
        @SerializedName("stream_interval_seconds") public int streamIntervalSeconds;
    }

    // Field Mode - Outbound
    public static class SilenceAckEvent {
        @SerializedName("capsule_id") public String capsuleId;
        @SerializedName("official_id") public String officialId;
        @SerializedName("on_scene_at") public String onSceneAt;
        @SerializedName("entered_otp") public String enteredOtp;

        public SilenceAckEvent(String c, String o, String d, String e) {
            this.capsuleId = c; this.officialId = o; this.onSceneAt = d; this.enteredOtp = e;
        }
    }
}

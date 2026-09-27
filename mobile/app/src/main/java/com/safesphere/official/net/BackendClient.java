package com.safesphere.official.net;

import com.google.gson.Gson;
import com.safesphere.official.model.Models.*;
import okhttp3.*;
import java.io.IOException;

public class BackendClient {
    private final String baseUrl;
    private final String wsUrl;
    private final OkHttpClient client;
    private final Gson gson;
    private WebSocket queueWs;
    private WebSocket fieldWs;

    public BackendClient(String host, int port) {
        this.baseUrl = "http://" + host + ":" + port;
        this.wsUrl = "ws://" + host + ":" + port;
        this.client = new OkHttpClient();
        this.gson = new Gson();
    }

    public interface Listener<T> {
        void onMessage(T data);
        void onError(String err);
    }

    public void subscribeToQueue(Listener<IncidentQueueView> listener) {
        Request request = new Request.Builder().url(wsUrl + "/ws/v1/incidents/queue").build();
        queueWs = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    IncidentQueueView view = gson.fromJson(text, IncidentQueueView.class);
                    listener.onMessage(view);
                } catch (Exception e) {
                    listener.onError("Parse error");
                }
            }
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                listener.onError("WS Failure: " + t.getMessage());
            }
        });
    }

    public void subscribeToField(String officialId, Listener<OfficialIncidentView> listener) {
        Request request = new Request.Builder().url(wsUrl + "/ws/v1/incidents/official?official_id=" + officialId).build();
        fieldWs = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    OfficialIncidentView view = gson.fromJson(text, OfficialIncidentView.class);
                    listener.onMessage(view);
                } catch (Exception e) {
                    listener.onError("Parse error");
                }
            }
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                listener.onError("WS Failure: " + t.getMessage());
            }
        });
    }

    public void sendDispatchDecision(DispatchDecisionEvent event, Callback callback) {
        RequestBody body = RequestBody.create(gson.toJson(event), MediaType.get("application/json"));
        Request req = new Request.Builder().url(baseUrl + "/api/v1/dispatch/decision").post(body).build();
        client.newCall(req).enqueue(callback);
    }

    public void sendSilenceAck(SilenceAckEvent event, Callback callback) {
        RequestBody body = RequestBody.create(gson.toJson(event), MediaType.get("application/json"));
        Request req = new Request.Builder().url(baseUrl + "/api/v1/field/silence-ack").post(body).build();
        client.newCall(req).enqueue(callback);
    }
}

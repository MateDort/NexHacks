package com.nexhacks.tapmate.vision;

import android.graphics.Bitmap;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OvershootClient {
    private static final String TAG = "OvershootClient";
    private static final String API_KEY = com.nexhacks.tapmate.utils.Config.OVERSHOOT_API_KEY;
    private static final String ENDPOINT = "https://api.overshoot.tv/v1/detect"; // Hypothetical endpoint

    private final OkHttpClient client;

    public OvershootClient() {
        // Short timeouts for real-time vision
        this.client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    public interface VisionCallback {
        void onDetection(String objectName, String attributes, float confidence);
        void onError(Exception e);
    }

    // Main Vision Loop Call
    public void detectObject(Bitmap frame, String targetLabel, VisionCallback callback) {
        try {
            byte[] imageBytes = compressBitmap(frame);

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", "frame.jpg",
                            RequestBody.create(imageBytes, MediaType.parse("image/jpeg")))
                    .addFormDataPart("target", targetLabel) // e.g. "car", "traffic_light"
                    .build();

            Request request = new Request.Builder()
                    .url(ENDPOINT)
                    .header("Authorization", "Bearer " + API_KEY)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        callback.onError(new IOException("Overshoot API Error: " + response.code()));
                        return;
                    }

                    try {
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);
                        
                        // Hypothetical Response: {"detected": true, "label": "car", "attrs": "Red Toyota", "conf": 0.95}
                        if (json.optBoolean("detected")) {
                            String label = json.optString("label");
                            String attrs = json.optString("attrs");
                            float conf = (float) json.optDouble("conf", 0.0);
                            callback.onDetection(label, attrs, conf);
                        } else {
                            callback.onDetection(null, null, 0);
                        }
                    } catch (Exception e) {
                        callback.onError(e);
                    }
                }
            });

        } catch (Exception e) {
            callback.onError(e);
        }
    }

    private byte[] compressBitmap(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        // Quality 80 is good tradeoff for speed/accuracy
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
        return stream.toByteArray();
    }
}

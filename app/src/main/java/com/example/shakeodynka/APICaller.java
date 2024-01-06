package com.example.shakeodynka;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class APICaller {
    private final APICallback callback;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public APICaller(final APICallback callback) {
        this.callback = callback;
    }
    public interface APICallback {
        void onApiCallSuccess(String response);
        void onApiCallFailure();
    }

    /**
     * Metoda wysyłająca zapytanie do zewnętrznego API oraz podająca dalej jego wynik.
     * @param apiUrl adres URL prowadzący do API.
     */
    public void makeApiCall(final String apiUrl) {
        executor.execute(() -> {
            try {
                URL url = new URL(apiUrl);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();

                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    bufferedReader.close();

                    String response = stringBuilder.toString();
                    handler.post(() -> callback.onApiCallSuccess(response));
                } finally {
                    urlConnection.disconnect();
                }
            } catch (IOException e) {
                Log.w("Shakeodynka", e);
                handler.post(callback::onApiCallFailure);
            }
        });
    }
}

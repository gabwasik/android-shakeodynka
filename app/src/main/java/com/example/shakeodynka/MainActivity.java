package com.example.shakeodynka;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements APICaller.APICallback, ShakeDetector.ShakeListener {
    /** Instancja klasy odpowiedzialnej za wykonywanie zapytań do API i dostarczanie ich wyników. */
    private final APICaller apiCaller = new APICaller(this);
    /** Adres do zewnętrznego API generującego losowe dane lokalizacyjne. */
    private final String randomApiUrl = "https://randomuser.me/api/?inc=location";
    /** Obiekt zawierający dane pogodowe wylosowanej lokalizacji. */
    private JSONObject weatherData;
    /** Instancja klasy odpowiedzialnej za wykrywanie wstrząśnięć urządzeniem. */
    private ShakeDetector shakeDetector;
    /** Adres API, do którego ma zostać wysłane zapytanie. */
    private String apiUrl;
    /** Instancja klasy odpowiedzialnej za aktualizację czasu lokalnego na żywo. */
    private Timer timer;
    /** Zmienna informująca o tym, czy wyświetlany jest aktualnie ekran początkowy/ekran błędu. */
    private boolean onInfoScreen;
    /** Zmienna zliczająca ilość błędów podczas komunikacji z API. */
    private int errorCount;
    /** Godzina czasu lokalnego wylosowanej lokalizacji. */
    private int locationLocaltimeHour;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        shakeDetector = new ShakeDetector(this, this);
        shakeDetector.registerListener();
    }
    @Override protected void onResume() {
        super.onResume();
        shakeDetector.registerListener();
    }
    @Override protected void onPause() {
        super.onPause();
        shakeDetector.unregisterListener();
    }
    @Override protected void onDestroy() {
        super.onDestroy();
        shakeDetector.unregisterListener();
    }

    /**
     * Metoda wywoływana po uzyskaniu odpowiedzi z zewnętrznego API.
     * @param response ciało odpowiedzi zwrócone przez API.
     */
    @Override public void onApiCallSuccess(final String response) {
        // reset licznika błędów
        errorCount = 0;

        try {
            if (response.contains("results")) { // klucz "results" istnieje tylko w odpowiedziach z domeny randomuser.me
                // pobranie danych pogodowych z wylosowanego miasta
                String apiKey = BuildConfig.WEATHER_API_KEY;
                String apiQuery = new JSONObject(response).getJSONArray("results").getJSONObject(0).getJSONObject("location").getString("city");

                apiUrl = String.format("https://api.weatherapi.com/v1/current.json?key=%s&q=%s", apiKey, apiQuery);
                apiCaller.makeApiCall(apiUrl);
            } else if (response.contains("location")) { // klucz "location" istnieje tylko w odpowiedziach z poddomeny api.weatherapi.com
                // pobranie tłumaczeń stanów pogodowych
                weatherData = new JSONObject(response);
                apiCaller.makeApiCall("https://www.weatherapi.com/docs/conditions.json");
            } else { // jeżeli odpowiedź nie ma żadnego z tych kluczy, to mamy do czynienia z tablicą tłumaczeń z domeny weatherapi.com
                // wyświetlenie wszystkich pobranych danych
                displayWeather(new JSONArray(response));
            }
        } catch (JSONException e) {
            Log.w("Shakeodynka", e);
        }
    }

    /** Metoda wywoływana po wystąpieniu błędu w procesie komunikacji z API. */
    @Override public void onApiCallFailure() {
        // podniesienie licznika błędów o 1
        errorCount++;

        // jeżeli wystąpi awaria któregoś z API (tj. wystąpi zbyt dużo błędów)
        if (errorCount >= 10) {
            // wyświetlenie ekranu błędu
            ImageView infoIcon = findViewById(R.id.infoIcon);
            infoIcon.setImageResource(R.drawable.ic_error);
            infoIcon.setVisibility(View.VISIBLE);

            TextView infoText = findViewById(R.id.infoText);
            infoText.setText(R.string.api_error);
            infoText.setVisibility(View.VISIBLE);
        } else {
            // pobranie nowych losowych danych
            apiUrl = randomApiUrl;
            apiCaller.makeApiCall(apiUrl);
        }
    }

    /**
     * Metoda zwracająca lokalny czas wylosowanej lokalizacji.
     * @return Ciąg znaków zawierający zegary 24- oraz 12-godzinne oddzielone prawym ukośnikiem.
     */
    @SuppressLint("DefaultLocale")
    private String getLocalTime() {
        Calendar calendar = java.util.Calendar.getInstance();
        int second = calendar.get(java.util.Calendar.SECOND);
        int minute = calendar.get(java.util.Calendar.MINUTE);

        // jeżeli wykryto równą godzinę
        if (second == 0 && minute == 0) {
            if (locationLocaltimeHour == 23) locationLocaltimeHour = 0; // zmiana godziny z 23:59 na 00:00
            else locationLocaltimeHour++;
        }

        // przygotowanie formatu 12-godzinnego
        String period = "a.m.";
        int hour = locationLocaltimeHour;
        if (locationLocaltimeHour > 12) { // godziny popołudniowe (13-23)
            period = "p.m.";
            hour -= 12;
        } else if (locationLocaltimeHour == 12) period = "p.m."; // południe
        else if (locationLocaltimeHour == 0) hour = 12; // północ

        return String.format("%02d:%02d:%02d / %d:%02d:%02d %s", locationLocaltimeHour, minute, second, hour, minute, second, period);
    }

    /**
     * Metoda wywoływana przy każdym wykryciu zdarzenia wstrząśnięcia urządzeniem.
     */
    public void onShake() {
        // ukrycie ekranu początkowego/ekranu błędu jeżeli jest on widoczny
        if (!onInfoScreen) {
            findViewById(R.id.infoIcon).setVisibility(View.INVISIBLE);
            findViewById(R.id.infoText).setVisibility(View.INVISIBLE);
            onInfoScreen = true;
        }

        // zatrzymanie poprzedniego timera aktualizującego zegary czasu lokalnego (jeżeli takowy istnieje)
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }

        // pobranie losowych danych lokalizacyjnych
        apiUrl = randomApiUrl;
        apiCaller.makeApiCall(apiUrl);
    }

    /**
     * Metoda wyświetlająca dane pogodowe w głównym widoku.
     * @param conditions tablica zawierająca oficjalne tłumaczenia stanów pogodowych.
     * @throws JSONException jeżeli wystąpi błąd podczas operowania na danych.
     */
    private void displayWeather(final JSONArray conditions) throws JSONException {
        // pogoda
        String currentConditionText = weatherData.getJSONObject("current").getJSONObject("condition").getString("text");
        String currentConditionIcon = weatherData.getJSONObject("current").getJSONObject("condition").getString("icon");
        String dayOrNightText = (currentConditionIcon.contains("day")) ? "day" : "night";
        TextView weatherText = findViewById(R.id.weatherText);
        weatherText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);

        // pogoda – wyświetlenie ikony pogody
        Picasso.get().load("https:" + currentConditionIcon.replace("64x64", "128x128")).into((ImageView) findViewById(R.id.weatherIcon));

        // pogoda – wyszukanie tłumaczenia stanu pogody
        for (int i = 0; i < conditions.length(); i++) {
            JSONObject condition = (JSONObject) conditions.get(i);

            // jeżeli stan pogody zwrócony przez API i stan pogody z tablicy tłumaczeń jest taki sam, to zastępujemy oryginał tłumaczeniem
            if (currentConditionText.equalsIgnoreCase(condition.getString(dayOrNightText))) {
                condition = (JSONObject) condition.getJSONArray("languages").get(19); // indeks o wartości 19 odpowiada językowi polskiemu
                currentConditionText = condition.getString(dayOrNightText + "_text");

                break;
            } else {
                // jeżeli nie, to może to być stan bez oficjalnego tłumaczenia
                switch (currentConditionText) {
                    case "Sunny": currentConditionText = "Czyste niebo"; break;
                    case "Patchy rain possible": currentConditionText = "Możliwe miejscowe opady deszczu"; break;
                    case "Thundery outbreaks possible": currentConditionText = "Możliwe gwałtowne grzmienia"; break;
                    case "Moderate or heavy rain with thunder": currentConditionText = "Miejscowe, średnie lub ciężkie opady deszczu z grzmieniem"; break;
                }
            }
        }

        weatherText.setText(String.format("%s", currentConditionText));

        // temperatury
        int currentTempC = weatherData.getJSONObject("current").getInt("temp_c");
        int currentTempF = weatherData.getJSONObject("current").getInt("temp_f");

        TextView temperatureText = findViewById(R.id.weatherTemperature);
        temperatureText.setText(String.format("%s°C / %s°F", currentTempC, currentTempF));

        // mapa
        double locationLat = weatherData.getJSONObject("location").getDouble("lat");
        double locationLon = weatherData.getJSONObject("location").getDouble("lon");

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        Objects.requireNonNull(mapFragment).getMapAsync(googleMap -> {
            // zmiana stylu mapy w zależności od pory dnia
            if (dayOrNightText.equals("night")) googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_night));
            else googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_standard));

            // zmiana wyświetlanej na mapie pozycji według wylosowanych współrzędnych
            LatLng location = new LatLng(locationLat, locationLon);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 12.0f));
        });

        findViewById(R.id.mapLayout).setVisibility(android.view.View.VISIBLE);

        // lokalizacja
        String locationName = weatherData.getJSONObject("location").getString("name");
        String locationCountry = weatherData.getJSONObject("location").getString("country");

        TextView locationText = findViewById(R.id.locationText);
        locationText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);

        // lokalizacja – tłumaczenie nazw krajów z angielskiego na polski
        try (InputStream credentialsStream = getResources().openRawResource(R.raw.google_credentials)) {
            TranslateService translateService = new TranslateService(credentialsStream);
            translateService.translateTextAsync(locationCountry, "pl", result -> {
                // poprawa błędnych/nienaturalnie brzmiących tłumaczeń
                switch(result) {
                    case "الدنمارك": result = "Dania"; break;
                    case "Zjednoczone Królestwo": result = "Wielka Brytania"; break;

                    case "Stany Zjednoczone Ameryki":
                    case "USA Stany Zjednoczone Ameryki":
                        result = "Stany Zjednoczone"; break;

                    case "dinde":
                    case "Indyk":
                    case "Turkey":
                        result = "Turcja"; break;

                    // terytoria krajów
                    case "Guernsey": result = "Guernsey (Wielka Brytania)"; break;
                    case "Martynika": result = "Martynika (Francja)"; break;
                }

                locationText.setText(String.format("%s, %s", locationName, result));
            });
        } catch (IOException e) {
            Log.w("Shakeodynka", e);

            // jeżeli wystąpił błąd, to wyświetlamy nieprzetłumaczony tekst
            locationText.setText(String.format("%s, %s", locationName, locationCountry));
        }

        // czas lokalny
        String locationLocaltime = weatherData.getJSONObject("location").getString("localtime");
        locationLocaltimeHour = Integer.parseInt(locationLocaltime.substring(locationLocaltime.length() - 5, locationLocaltime.length() - 3).trim());

        TextView textClock = findViewById(R.id.timeText);
        textClock.setVisibility(View.VISIBLE);

        // czas lokalny – utworzenie timera odświeżającego zegary co sekundę
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                runOnUiThread(() -> textClock.setText(getLocalTime()));
            }
        }, 0, 1000);
    }
}
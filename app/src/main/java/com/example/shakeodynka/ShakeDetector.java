package com.example.shakeodynka;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Vibrator;

public class ShakeDetector implements SensorEventListener {
    /** Wartość przeciążenia, jakie musi osiągnąć urządzenie, aby uznać wstrząśnięcie. */
    private static final float SHAKE_THRESHOLD_GFORCE = 2.7f;
    /** Ilość wstrząśnięć potrzebnych do zarejestrowania zdarzenia. */
    private static final int SHAKE_COUNT_NEEDED = 2;
    /** Czas, po upływie którego wykrywanie wstrząśnięć jest resetowane. */
    private static final int SHAKE_COUNT_RESET_TIME_MS = 3000;
    /** Okno czasu podczas którego kolejne wykryte wstrząśnięcia będą zaliczane do jednego zdarzenia. */
    private static final int SHAKE_SLOP_TIME_MS = 500;
    /** Czas, który musi upłynąć zanim możliwe będzie wykrywanie kolejnego zdarzenia. */
    private static final int SHAKE_COOLDOWN_TIME_MS = 2000;

    private final Context context;
    private final SensorManager manager;
    private final ShakeListener listener;
    /** Moment wykrycia ostatniego wstrząśnięcia <b><u>w serii</u></b>. */
    private long lastShakeTime;
    /** Moment wykrycia ostatniego wstrząśnięcia <b><u>całego zdarzenia</u></b>. */
    private long lastShakeDetectedTime;
    /** Licznik wstrząśnięć wykrytych w danym oknie czasowym ({@link #SHAKE_SLOP_TIME_MS}). */
    private int shakeCount;

    public ShakeDetector(final Context context, final ShakeListener listener) {
        this.context = context;
        this.listener = listener;

        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        registerListener();
    }
    public interface ShakeListener {
        void onShake();
    }

    @Override public void onAccuracyChanged(final Sensor sensor, final int accuracy) {}
    @Override public void onSensorChanged(final SensorEvent event) {
        // obliczenie przeciążenia urządzenia
        float gForce = (float) Math.sqrt(
            event.values[0] * event.values[0] +
            event.values[1] * event.values[1] +
            event.values[2] * event.values[2]
        ) / SensorManager.GRAVITY_EARTH;

        // jeżeli przeciążenie jest wyższe od zdefiniowanego progu
        if (gForce > SHAKE_THRESHOLD_GFORCE) handleShakeDetection(System.currentTimeMillis());
    }
    public void registerListener() {
        Sensor accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }
    public void unregisterListener() {
        manager.unregisterListener(this);
    }

    /**
     * Metoda decydująca o tym, czy urządzenie zostało wstrząśnięte zgodnie z wcześniejszymi założeniami ({@link #SHAKE_THRESHOLD_GFORCE}, {@link #SHAKE_COUNT_NEEDED}, {@link #SHAKE_COUNT_RESET_TIME_MS}, {@link #SHAKE_SLOP_TIME_MS}, {@link #SHAKE_COOLDOWN_TIME_MS}).
     * @param now aktualny czas uniksowy.
     */
    private void handleShakeDetection(final long now) {
        // jeżeli jest to pierwsze *zdarzenie* od rozpoczęcia aplikacji LUB odstęp od ostatniego *zdarzenia* jest większy od wymaganego
        if (lastShakeDetectedTime == 0 || (now - lastShakeDetectedTime) > SHAKE_COOLDOWN_TIME_MS) {
            // jeżeli jest to pierwsze *wstrząśnięcie* od rozpoczęcia aplikacji LUB odstęp od ostatniego *wstrząśnięcia* jest większy od wymaganego
            if (lastShakeTime == 0 || (now - lastShakeTime) > SHAKE_SLOP_TIME_MS) {
                // anulowanie całego zdarzenia
                lastShakeTime = now;
                shakeCount = 0;
            }
            shakeCount++;

            // jeżeli liczba wstrząśnięć jest większa lub równa od wymaganej I odstęp od ostatniego wstrząśnięcia mieści się w oknie czasowym
            if (shakeCount >= SHAKE_COUNT_NEEDED && (now - lastShakeTime) < SHAKE_COUNT_RESET_TIME_MS) {
                if (listener != null) {
                    listener.onShake();
                    vibrate();
                    lastShakeDetectedTime = now;
                }

                shakeCount = 0;
            }
        }
    }

    /**
     * Metoda wywołująca wibracje w urządzeniu.
     */
    private void vibrate() {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(new long[] {500, 500, 500}, -1);
    }
}

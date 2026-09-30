package com.example.mormyshka;

import android.app.Activity;
import android.os.Bundle;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Deque;

public class MainActivity extends Activity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private GameView gameView;

    private float gravityX;
    private float gravityY;
    private float gravityZ;

    private float shakeValue;
    private long lastJigTime = 0;

    private final Deque<Long> jigTimes = new ArrayDeque<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setNavigationBarColor(Color.rgb(7, 19, 29));

        gameView = new GameView(this);
        setContentView(gameView);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (accelerometer != null) {
            sensorManager.registerListener(
                    this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_GAME
            );
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        // Убираем постоянное влияние гравитации.
        gravityX = gravityX * 0.88f + x * 0.12f;
        gravityY = gravityY * 0.88f + y * 0.12f;
        gravityZ = gravityZ * 0.88f + z * 0.12f;

        float dx = x - gravityX;
        float dy = y - gravityY;
        float dz = z - gravityZ;

        shakeValue = (float) Math.sqrt(
                dx * dx + dy * dy + dz * dz
        );

        // Подёргивание удочки.
        if (shakeValue > 1.8f) {
            long now = System.currentTimeMillis();

            if (now - lastJigTime > 100) {
                lastJigTime = now;
                jig();
            }
        }

        gameView.invalidate();
    }

    private void jig() {
        long now = System.currentTimeMillis();

        jigTimes.addLast(now);

        // Оставляем только движения за последние 1.8 секунды.
        while (!jigTimes.isEmpty()
                && now - jigTimes.peekFirst() > 1800) {
            jigTimes.removeFirst();
        }

        int count = jigTimes.size();

        // Достаточно ровный активный ритм.
        if (count >= 8 && gameView.state == 0) {
            gameView.startBite();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Не требуется.
    }

    private void vibrate(long duration) {
        Vibrator vibrator =
                (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator == null) {
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(
                    VibrationEffect.createOneShot(
                            duration,
                            VibrationEffect.DEFAULT_AMPLITUDE
                    )
            );
        } else {
            vibrator.vibrate(duration);
        }
    }

    private class GameView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        // 0 — ловим, 1 — поклёвка, 2 — поймана рыба.
        private int state = 0;

        private long biteUntil = 0;

        private float downY;
        private float holePulse = 0;

        private final RectFHelper buttonRect = new RectFHelper();

        GameView(Context context) {
            super(context);

            paint.setTypeface(
                    android.graphics.Typeface.create(
                            "sans",
                            android.graphics.Typeface.NORMAL
                    )
            );

            setFocusable(true);
        }

        void startBite() {
            state = 1;
            biteUntil = System.currentTimeMillis() + 1800;

            vibrate(220);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int w = getWidth();
            int h = getHeight();

            drawBackground(canvas, w, h);
            drawIce(canvas, w, h);
            drawHole(canvas, w, h);
            drawFish(canvas, w, h);
            drawFishingRod(canvas, w, h);
            drawRhythm(canvas, w, h);
            drawButton(canvas, w, h);

            if (state == 1) {
                drawBite(canvas, w, h);

                if (System.currentTimeMillis() > biteUntil) {
                    state = 0;
                }

                postInvalidateDelayed(40);
            }

            if (state == 2) {
                drawCaught(canvas, w, h);
            }
        }

        private void drawBackground(Canvas canvas, int w, int h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(135, 196, 220));
            canvas.drawRect(0, 0, w, h, paint);

            // Верхнее небо.
            paint.setColor(Color.rgb(90, 155, 190));
            canvas.drawRect(0, 0, w, h * 0.36f, paint);

            // Снег.
            paint.setColor(Color.rgb(232, 244, 247));
            canvas.drawRect(0, h * 0.36f, w, h, paint);

            // Снежинки.
            paint.setColor(Color.WHITE);

            for (int i = 0; i < 35; i++) {
                float x = (i * 83) % w;
                float y = (i * 47) % (int) (h * 0.45f);

                canvas.drawCircle(x, y, 2.5f, paint);
            }
        }

        private void drawIce(Canvas canvas, int w, int h) {
            float iceTop = h * 0.46f;

            paint.setColor(Color.rgb(185, 222, 232));
            canvas.drawRect(0, iceTop, w, h, paint);

            paint.setColor(Color.rgb(220, 242, 247));

            path.reset();
            path.moveTo(0, iceTop + 25);

            for (int x = 0; x <= w; x += 80) {
                float y = iceTop + 18 +
                        (float) Math.sin(x * 0.035) * 8;

                path.lineTo(x, y);
            }

            path.lineTo(w, h);
            path.lineTo(0, h);
            path.close();

            canvas.drawPath(path, paint);
        }

        private void drawHole(Canvas canvas, int w, int h) {
            float cx = w / 2f;
            float cy = h * 0.55f;

            holePulse += 0.08f;

            float pulse = (float) Math.sin(holePulse) * 3f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(38, 69, 79));

            canvas.drawOval(
                    cx - 125 - pulse,
                    cy - 65 - pulse,
                    cx + 125 + pulse,
                    cy + 65 + pulse,
                    paint
            );

            paint.setColor(Color.rgb(20, 38, 48));

            canvas.drawOval(
                    cx - 105,
                    cy - 53,
                    cx + 105,
                    cy + 53,
                    paint
            );

            // Вода в лунке.
            paint.setColor(Color.rgb(38, 95, 116));

            canvas.drawOval(
                    cx - 92,
                    cy - 45,
                    cx + 92,
                    cy + 45,
                    paint
            );
        }

        private void drawFish(Canvas canvas, int w, int h) {
            float cx = w / 2f;
            float cy = h * 0.69f;

            float move = 0;

            if (state == 1) {
                move = (float) Math.sin(
                        System.currentTimeMillis() * 0.012
                ) * 12;
            }

            cx += move;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(185, 190, 184));

            canvas.drawOval(
                    cx - 70,
                    cy - 25,
                    cx + 70,
                    cy + 25,
                    paint
            );

            // Хвост.
            path.reset();
            path.moveTo(cx - 65, cy);
            path.lineTo(cx - 105, cy - 35);
            path.lineTo(cx - 105, cy + 35);
            path.close();

            canvas.drawPath(path, paint);

            // Плавник.
            paint.setColor(Color.rgb(150, 157, 154));

            path.reset();
            path.moveTo(cx, cy - 20);
            path.lineTo(cx + 15, cy - 48);
            path.lineTo(cx + 30, cy - 20);
            path.close();

            canvas.drawPath(path, paint);

            // Глаз.
            paint.setColor(Color.BLACK);
            canvas.drawCircle(cx + 48, cy - 7, 4, paint);

            if (state == 1) {
                paint.setColor(Color.WHITE);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(25);
                canvas.drawText("🐟", cx, cy + 8, paint);
            }
        }

        private void drawFishingRod(Canvas canvas, int w, int h) {
            float cx = w / 2f;

            paint.setStrokeWidth(9);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.rgb(75, 48, 30));

            canvas.drawLine(
                    cx + 115,
                    h * 0.22f,
                    cx + 25,
                    h * 0.53f,
                    paint
            );

            // Леска.
            paint.setStrokeWidth(3);
            paint.setColor(Color.WHITE);

            canvas.drawLine(
                    cx + 25,
                    h * 0.53f,
                    cx,
                    h * 0.70f,
                    paint
            );

            // Мормышка.
            paint.setColor(Color.rgb(210, 190, 80));

            canvas.drawCircle(
                    cx,
                    h * 0.70f,
                    9,
                    paint
            );
        }

        private void drawRhythm(Canvas canvas, int w, int h) {
            float left = 45;
            float right = w - 45;
            float top = h * 0.79f;

            paint.setStyle(Paint.Style.FILL);

            paint.setColor(Color.rgb(30, 55, 65));

            canvas.drawRoundRect(
                    left,
                    top,
                    right,
                    top + 42,
                    20,
                    20,
                    paint
            );

            int count = jigTimes.size();

            float score = Math.min(
                    1f,
                    count / 10f
            );

            paint.setColor(Color.rgb(100, 190, 175));

            canvas.drawRoundRect(
                    left + 4,
                    top + 4,
                    left + 4 +
                            (right - left - 8) * score,
                    top + 38,
                    17,
                    17,
                    paint
            );

            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(18);

            String text;

            if (state == 1) {
                text = "ПОКЛЁВКА! ПОДСЕКАЙ ↑";
            } else if (state == 2) {
                text = "РЫБА ПОЙМАНА!";
            } else if (count >= 6) {
                text = "Ритм отличный...";
            } else if (count >= 3) {
                text = "Продолжай...";
            } else {
                text = "Подёргивай телефон";
            }

            canvas.drawText(
                    text,
                    w / 2f,
                    top + 28,
                    paint
            );
        }

        private void drawButton(Canvas canvas, int w, int h) {
            float left = 75;
            float right = w - 75;
            float top = h - 95;
            float bottom = h - 35;

            buttonRect.left = left;
            buttonRect.top = top;
            buttonRect.right = right;
            buttonRect.bottom = bottom;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(45, 86, 96));

            canvas.drawRoundRect(
                    left,
                    top,
                    right,
                    bottom,
                    25,
                    25,
                    paint
            );

            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(20);

            canvas.drawText(
                    "Новая лунка",
                    w / 2f,
                    top + 39,
                    paint
            );
        }

        private void drawBite(Canvas canvas, int w, int h) {
            paint.setColor(Color.rgb(255, 245, 170));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(32);

            canvas.drawText(
                    "ПОЛОСКА!",
                    w / 2f,
                    h * 0.39f,
                    paint
            );

            paint.setTextSize(24);

            canvas.drawText(
                    "СВАЙП ВВЕРХ!",
                    w / 2f,
                    h * 0.43f,
                    paint
            );
        }

        private void drawCaught(Canvas canvas, int w, int h) {
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(30);

            canvas.drawText(
                    "🎣 Поймал!",
                    w / 2f,
                    h * 0.35f,
                    paint
            );

            paint.setTextSize(20);

            canvas.drawText(
                    "Нажми «Новая лунка»",
                    w / 2f,
                    h * 0.40f,
                    paint
            );
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    downY = event.getY();
                    return true;

                case MotionEvent.ACTION_UP:

                    float upY = event.getY();
                    float dy = upY - downY;

                    // Свайп вверх во время поклёвки.
                    if (state == 1 && dy < -90) {
                        state = 2;

                        vibrate(350);

                        Toast.makeText(
                                MainActivity.this,
                                "Рыба поймана!",
                                Toast.LENGTH_SHORT
                        ).show();

                        invalidate();
                        return true;
                    }

                    // Кнопка "Новая лунка".
                    float x = event.getX();
                    float y = event.getY();

                    if (buttonRect.contains(x, y)) {
                        state = 0;
                        jigTimes.clear();

                        Toast.makeText(
                                MainActivity.this,
                                "Новая лунка!",
                                Toast.LENGTH_SHORT
                        ).show();

                        invalidate();
                        return true;
                    }

                    return true;
            }

            return true;
        }
    }

    private static class RectFHelper {

        float left;
        float top;
        float right;
        float bottom;

        boolean contains(float x, float y) {
            return x >= left
                    && x <= right
                    && y >= top
                    && y <= bottom;
        }
    }
}

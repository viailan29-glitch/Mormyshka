package com.example.mormyshka

import android.app.Activity
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity(), SensorEventListener {
    private lateinit var game: GameView
    private lateinit var sensor: SensorManager
    private var lastShake = 0L
    private var shakePower = 0f
    private var rhythm = 0f
    private var biteCooldown = 0L
    private var active = false

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        game = GameView(this); setContentView(game)
        sensor = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }
    override fun onDestroy() { sensor.unregisterListener(this); super.onDestroy() }

    override fun onSensorChanged(e: SensorEvent) {
        val ax=e.values[0]; val ay=e.values[1]; val az=e.values[2]
        val magnitude = kotlin.math.sqrt((ax*ax+ay*ay+az*az).toDouble()).toFloat()
        val delta = abs(magnitude - 9.81f)
        val now=System.currentTimeMillis()
        if(delta > 1.1f && now-lastShake>45) {
            val interval=now-lastShake; lastShake=now
            shakePower=min(1f, shakePower + min(.35f, delta/14f))
            if(interval in 90..750) rhythm = min(1f, rhythm + .10f) else rhythm *= .88f
            active=true
            game.invalidate()
            if(rhythm>.78f && shakePower>.55f && now>biteCooldown) {
                biteCooldown=now+2600; game.biteUntil=now+1200; vibrate(); rhythm=.35f
            }
        } else { shakePower*=.96f; rhythm*=.997f; game.invalidate() }
    }
    private fun vibrate(){ val v = if(android.os.Build.VERSION.SDK_INT>=31) (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else getSystemService(VIBRATOR_SERVICE) as Vibrator
        if(android.os.Build.VERSION.SDK_INT>=26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0,90,60,130),-1)) else @Suppress("DEPRECATION") v.vibrate(220)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    inner class GameView(c: Context): View(c) {
        private val p=Paint(Paint.ANTI_ALIAS_FLAG); var biteUntil=0L
        override fun onDraw(canvas: Canvas){ super.onDraw(canvas); val w=width.toFloat(); val h=height.toFloat();
            p.shader=LinearGradient(0f,0f,0f,h,Color.rgb(8,28,43),Color.rgb(3,12,19),Shader.TileMode.CLAMP); canvas.drawRect(0f,0f,w,h,p); p.shader=null
            // ice
            p.color=Color.rgb(190,225,238); canvas.drawRect(0f,h*.67f,w,h,p)
            p.color=Color.rgb(150,205,224); canvas.drawCircle(w*.5f,h*.77f,w*.29f,p); p.color=Color.rgb(10,31,42); canvas.drawCircle(w*.5f,h*.77f,w*.22f,p)
            // rod
            p.strokeWidth=10f; p.strokeCap=Paint.Cap.ROUND; p.color=Color.rgb(88,55,35); canvas.drawLine(w*.72f,h*.18f,w*.54f,h*.68f,p)
            p.strokeWidth=3f; p.color=Color.WHITE; canvas.drawLine(w*.54f,h*.35f,w*.5f,h*.76f,p)
            val jigY=h*.76f + (shakePower*12f); p.color=Color.RED; canvas.drawCircle(w*.5f,jigY,8f,p)
            p.strokeWidth=2f; p.color=Color.WHITE; canvas.drawLine(w*.5f,h*.68f,w*.5f,jigY,p)
            p.textAlign=Paint.Align.CENTER; p.typeface=Typeface.DEFAULT_BOLD
            p.textSize=34f; p.color=Color.WHITE; canvas.drawText("МОРМЫШКА",w/2,h*.09f,p)
            p.textSize=17f; p.typeface=Typeface.DEFAULT; p.color=0xFFB9D8E6.toInt(); canvas.drawText(if(active) "Трясите телефон плавно" else "Трясите телефон, чтобы начать",w/2,h*.13f,p)
            // gauge
            val left=w*.12f; val right=w*.88f; val top=h*.23f; val bottom=h*.29f
            p.color=0x55333333; canvas.drawRoundRect(left,top,right,bottom,20f,20f,p)
            p.color=0xFF5CC8FF.toInt(); canvas.drawRoundRect(left,top,left+(right-left)*rhythm,bottom,20f,20f,p)
            p.textSize=14f; p.color=Color.WHITE; canvas.drawText("ПРАВИЛЬНОСТЬ ПРОВОДКИ",w/2,top-12f,p)
            if(System.currentTimeMillis()<biteUntil){ p.textSize=42f; p.color=Color.WHITE; p.typeface=Typeface.DEFAULT_BOLD; canvas.drawText("ПОКЛЁВКА!",w/2,h*.48f,p); postInvalidateDelayed(40) }
            p.typeface=Typeface.DEFAULT; p.textSize=15f; p.color=0xFF315A6B.toInt(); canvas.drawText("Поймай момент — и подсечка сработает сама",w/2,h*.94f,p)
        }
        override fun onTouchEvent(event: MotionEvent)=true
    }
}

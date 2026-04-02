package com.volleyhub.pro;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.os.Handler;
import android.os.Looper;

/**
 * Gesture detector for Wear OS that combines:
 * 1. Touch gestures (swipe, double tap, pinch) for Team A/B points
 * 2. Sensor-based gestures (accelerometer + gyroscope) for wrist/hand movements
 * 
 * NOTE: Samsung's native "Double Pinch" (EMG-based) is NOT accessible to third-party apps.
 * We use accelerometer patterns to detect similar gestures.
 */
public class WearGestureDetector implements SensorEventListener {
    private static final String TAG = "WearGestureDetector";
    
    private Context context;
    private GestureDetector gestureDetector;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    
    private GestureListener listener;
    
    // Cooldown globale tra i punti (2 secondi)
    private static final long POINT_COOLDOWN = 2000; // 2 secondi
    private long lastPointTime = 0;
    private Handler cooldownHandler;
    
    // GESTI BLOCCATI/SBLOCCATI
    private boolean gesturesEnabled = true;
    
    // Accelerometer thresholds for gesture detection - ALTA SENSIBILITA'
    private static final float SHAKE_THRESHOLD = 4.0f;  // RIDOTTA da 6.0 per più sensibilità
    private static final long SHAKE_COOLDOWN = 600; // ms between shakes (RIDOTTO)
    private static final long GESTURE_WINDOW = 2500; // ms for double gesture
    
    private long lastShakeTime = 0;
    private int shakeCount = 0;
    private long firstShakeTime = 0;
    
    // Pinch gesture detection - ALTA SENSIBILITA'
    private static final float PINCH_TWITCH_THRESHOLD = 1.0f;  // RIDOTTA da 1.5 per più sensibilità
    private static final float PINCH_MAX_THRESHOLD = 6.0f;     // AUMENTATA per accettare più movimenti
    private static final long PINCH_COOLDOWN = 300; // ms between pinches (RIDOTTO)
    private static final long DOUBLE_PINCH_WINDOW = 2000; // ms for double pinch (AUMENTATO)
    private static final long PINCH_RESET_TIMEOUT = 3000; // ms after which pinch count resets (AUMENTATO)
    private long lastPinchTime = 0;
    private int pinchCount = 0;
    private long firstPinchTime = 0;
    private boolean inPinchGesture = false;
    
    // Gyro threshold - più tolleranza
    private static final float PINCH_MAX_GYRO = 4.0f;  // rad/s - AUMENTATO da 3.5
    
    // Low-pass filter for smoothing accelerometer data (wowMouse style)
    private static final float FILTER_ALPHA = 0.5f;  // Aumentato da 0.3 per meno smoothing
    private float[] filteredAcceleration = new float[3];
    private boolean filterInitialized = false;
    
    // Track recent gyro rotation to distinguish gestures
    private float recentGyroRotation = 0;
    private long lastGyroTime = 0;
    
    // Touch gesture thresholds
    private static final float SWIPE_THRESHOLD = 50f;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;
    
    // Touch pinch detection (fingers on screen)
    private static final float TOUCH_PINCH_THRESHOLD = 0.6f;
    private float initialFingerDistance = 0;
    private boolean isTouchPinching = false;
    private int touchPinchCount = 0;
    private long lastTouchPinchTime = 0;
    
    public interface GestureListener {
        void onTeamAPoint();
        void onTeamBPoint();
    }
    
    public WearGestureDetector(Context context, GestureListener listener) {
        Log.d(TAG, "=== WearGestureDetector constructor ===");
        this.context = context;
        this.listener = listener;
        this.cooldownHandler = new Handler(Looper.getMainLooper());
        
        // Initialize touch gesture detector
        gestureDetector = new GestureDetector(context, new TouchGestureListener());
        
        // Initialize sensor manager
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Log.d(TAG, "sensorManager obtained: " + (sensorManager != null));
        
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Log.d(TAG, "accelerometer: " + (accelerometer != null ? "FOUND" : "NOT FOUND"));
            
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            Log.d(TAG, "gyroscope: " + (gyroscope != null ? "FOUND" : "NOT FOUND"));
            
            // Fallback: if gyroscope not available, use only accelerometer
            if (gyroscope == null) {
                Log.d(TAG, "Gyroscope not available on this device, using accelerometer only");
            }
        } else {
            Log.e(TAG, "ERROR: sensorManager is null!");
        }
    }
    
    /**
     * Enable or disable gesture detection
     */
    public void setGesturesEnabled(boolean enabled) {
        gesturesEnabled = enabled;
        Log.d(TAG, "Gestures " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Check if gestures are enabled
     */
    public boolean isGesturesEnabled() {
        return gesturesEnabled;
    }
    
    /**
     * Check if cooldown is active
     */
    private boolean isCooldownActive() {
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastPointTime) < POINT_COOLDOWN;
    }
    
    /**
     * Reset cooldown timer
     */
    private void triggerCooldown() {
        lastPointTime = System.currentTimeMillis();
        Log.d(TAG, "Point awarded. Cooldown active for " + (POINT_COOLDOWN/1000) + " seconds");
    }
    
    /**
     * Register sensor listeners - call in onResume()
     */
    public void registerSensorListener() {
        Log.d(TAG, "=== registerSensorListener called ===");
        try {
            if (sensorManager == null) {
                Log.e(TAG, "ERROR: sensorManager is null!");
                return;
            }
            
            Log.d(TAG, "sensorManager is OK");
            
            if (accelerometer == null) {
                Log.e(TAG, "ERROR: accelerometer is null - device may not have this sensor");
            } else {
                Log.d(TAG, "accelerometer found: " + accelerometer.getName());
                // Use SENSOR_DELAY_UI instead of FASTEST (no special permission needed)
                boolean registered = sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
                Log.d(TAG, "Accelerometer listener registered: " + registered);
            }
            
            if (gyroscope != null) {
                try {
                    sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
                    Log.d(TAG, "Gyroscope listener registered");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to register gyroscope: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "Gyroscope not available, using accelerometer only");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering sensors: " + e.getMessage(), e);
        }
    }
    
    /**
     * Unregister sensor listeners - call in onPause()
     */
    public void unregisterSensorListener() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            Log.d(TAG, "All sensor listeners unregistered");
        }
    }
    
    /**
     * Handle touch events - call from Activity's onTouchEvent()
     */
    public boolean onTouchEvent(MotionEvent event) {
        // Handle multi-touch for pinch detection (on screen)
        if (event.getPointerCount() >= 2) {
            handleTouchPinchGesture(event);
        }
        return gestureDetector.onTouchEvent(event);
    }
    
    /**
     * Detect touch pinch gestures (two fingers on screen moving together)
     */
    private void handleTouchPinchGesture(MotionEvent event) {
        float x1 = event.getX(0);
        float y1 = event.getY(0);
        float x2 = event.getX(1);
        float y2 = event.getY(1);
        
        // Calculate distance between two fingers
        float currentDistance = (float) Math.sqrt(
            Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2)
        );
        
        int action = event.getActionMasked();
        
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // Two fingers just touched - store initial distance
            initialFingerDistance = currentDistance;
            isTouchPinching = false;
            Log.d(TAG, "Touch pinch started. Initial distance: " + initialFingerDistance);
        } else if (action == MotionEvent.ACTION_MOVE && initialFingerDistance > 0) {
            // Check if fingers are moving together (pinch in)
            if (!isTouchPinching && currentDistance < (initialFingerDistance * TOUCH_PINCH_THRESHOLD)) {
                isTouchPinching = true;
                touchPinchCount++;
                
                long currentTime = System.currentTimeMillis();
                Log.d(TAG, "Touch pinch detected! Count: " + touchPinchCount);
                
                // Check for double touch pinch
                if (touchPinchCount >= 2 && (currentTime - lastTouchPinchTime) < DOUBLE_PINCH_WINDOW) {
                    Log.d(TAG, "DOUBLE TOUCH PINCH detected - Team A point!");
                    if (!isCooldownActive()) {
                        listener.onTeamAPoint();
                        triggerCooldown();
                    } else {
                        Log.d(TAG, "Point ignored - cooldown active");
                    }
                    touchPinchCount = 0;
                }
                
                lastTouchPinchTime = currentTime;
                
                // Reset after short delay to allow next pinch
                cooldownHandler.postDelayed(() -> {
                    isTouchPinching = false;
                    initialFingerDistance = 0;
                }, 300);
            }
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            // One finger lifted - reset
            initialFingerDistance = 0;
            isTouchPinching = false;
        }
    }
    
    // ========== SensorEventListener Methods ==========

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null) {
            return;
        }
        
        try {
            int sensorType = event.sensor.getType();

            if (sensorType == Sensor.TYPE_ACCELEROMETER) {
                handleAccelerometerData(event.values);
            } else if (sensorType == Sensor.TYPE_GYROSCOPE) {
                handleGyroscopeData(event.values);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing sensor data: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle accelerometer data for wrist flick and air pinch detection
     * Uses low-pass filter (wowMouse style) for noise reduction
     */
    private void handleAccelerometerData(float[] values) {
        // BLOCK all gestures if disabled
        if (!gesturesEnabled) {
            return;
        }
        
        float x = values[0];
        float y = values[1];
        float z = values[2];

        // Apply low-pass filter to reduce noise (wowMouse algorithm)
        if (!filterInitialized) {
            filteredAcceleration[0] = x;
            filteredAcceleration[1] = y;
            filteredAcceleration[2] = z;
            filterInitialized = true;
        } else {
            filteredAcceleration[0] = FILTER_ALPHA * x + (1 - FILTER_ALPHA) * filteredAcceleration[0];
            filteredAcceleration[1] = FILTER_ALPHA * y + (1 - FILTER_ALPHA) * filteredAcceleration[1];
            filteredAcceleration[2] = FILTER_ALPHA * z + (1 - FILTER_ALPHA) * filteredAcceleration[2];
        }
        
        // Calculate total acceleration (excluding gravity) using filtered values
        float filteredX = filteredAcceleration[0];
        float filteredY = filteredAcceleration[1];
        float filteredZ = filteredAcceleration[2];
        
        float acceleration = (float) Math.sqrt(
            filteredX * filteredX + 
            filteredY * filteredY + 
            filteredZ * filteredZ
        ) - SensorManager.GRAVITY_EARTH;
        
        // Log SEMPRE i valori per debug
        Log.d(TAG, "Accel: " + String.format(java.util.Locale.US, "%.2f", acceleration) + " m/s²");

        // 1. Detect wrist flick (strong movement) - Team B
        if (Math.abs(acceleration) > SHAKE_THRESHOLD) {
            Log.d(TAG, ">>> Wrist flick: " + String.format(java.util.Locale.US, "%.2f", acceleration));
            handleWristFlick();
        }
        // 2. Detect subtle "air pinch" twitch - Team A
        else if (Math.abs(acceleration) > PINCH_TWITCH_THRESHOLD && !inPinchGesture) {
            Log.d(TAG, ">>> Potential air pinch: " + String.format(java.util.Locale.US, "%.2f", acceleration));
            handleAirPinch(acceleration);
        }
    }
    
    /**
     * Handle gyroscope data for rotation detection
     * wowMouse uses gyroscope to detect subtle rotation during pinch
     */
    private void handleGyroscopeData(float[] values) {
        // Calculate angular velocity
        float angularVelocity = (float) Math.sqrt(
            values[0] * values[0] +
            values[1] * values[1] +
            values[2] * values[2]
        );
        
        // Store recent gyro rotation for gesture differentiation
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastGyroTime < 500) {
            // Keep max rotation in last 500ms
            recentGyroRotation = Math.max(recentGyroRotation, angularVelocity);
        } else {
            recentGyroRotation = angularVelocity;
        }
        lastGyroTime = currentTime;

        // When pinching, there's often a small inward wrist rotation (1-3 rad/s)
        // Wrist flick has higher rotation (3+ rad/s)
        if (angularVelocity > 1.0f && angularVelocity < 8.0f) {
            Log.d(TAG, "Gyro: " + String.format(java.util.Locale.US, "%.2f", angularVelocity) + " rad/s");
        }
    }
    
    /**
     * Detect double wrist flick for Team B point
     */
    private void handleWristFlick() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastShake = currentTime - lastShakeTime;

        // Primo flick o nuovo gesto
        if (timeSinceLastShake > SHAKE_COOLDOWN) {
            // Reset per nuovo gesto
            if (shakeCount > 0) {
                Log.d(TAG, "Wrist flick timeout - resetting count");
            }
            shakeCount = 1;
            firstShakeTime = currentTime;
            Log.d(TAG, "First wrist flick detected");
        }
        // Secondo flick entro il tempo limite
        else if (timeSinceLastShake <= SHAKE_COOLDOWN && timeSinceLastShake > 100) {
            shakeCount++;
            Log.d(TAG, "Second wrist flick detected! Count: " + shakeCount);

            if (shakeCount >= 2 && currentTime - firstShakeTime < GESTURE_WINDOW) {
                Log.d(TAG, "✓ Double wrist flick completed - Team B point!");
                if (!isCooldownActive()) {
                    listener.onTeamBPoint();
                    triggerCooldown();
                } else {
                    Log.d(TAG, "Point ignored - cooldown active");
                }
                shakeCount = 0;
                firstShakeTime = 0;
            }
        }

        lastShakeTime = currentTime;
        
        // Reset gyro rotation after wrist flick to avoid affecting pinch detection
        cooldownHandler.postDelayed(() -> {
            recentGyroRotation = 0;
            Log.d(TAG, "[handleWristFlick] Reset gyro rotation");
        }, 500);
    }
    
    /**
     * Detect "air pinch" gesture for Team A point
     * Based on wowMouse algorithm - detects sharp wrist twitch when fingers pinch together
     * Uses gyroscope to reject wrist flicks (which have high rotation)
     */
    private void handleAirPinch(float acceleration) {
        long currentTime = System.currentTimeMillis();
        float absAcceleration = Math.abs(acceleration);
        
        Log.d(TAG, "[handleAirPinch] accel=" + String.format(java.util.Locale.US, "%.2f", absAcceleration));
        Log.d(TAG, "[handleAirPinch] recentGyro=" + String.format(java.util.Locale.US, "%.2f", recentGyroRotation));
        Log.d(TAG, "[handleAirPinch] time since lastShake=" + (currentTime - lastShakeTime));
        Log.d(TAG, "[handleAirPinch] time since lastPinch=" + (currentTime - lastPinchTime));
        
        // REJECT if acceleration is too high (it's a wrist flick, not a pinch)
        if (absAcceleration > PINCH_MAX_THRESHOLD) {
            Log.d(TAG, "[handleAirPinch] REJECTED: acceleration too high (wrist flick)");
            return;
        }
        
        // REJECT if gyro rotation is too high (it's a wrist flick, not a pinch)
        if (recentGyroRotation > PINCH_MAX_GYRO) {
            Log.d(TAG, "[handleAirPinch] REJECTED: gyro rotation too high (wrist flick) - " + 
                String.format(java.util.Locale.US, "%.2f", recentGyroRotation) + " rad/s");
            return;
        }
        
        // Avoid duplicate detection during an ongoing gesture
        if (inPinchGesture) {
            Log.d(TAG, "[handleAirPinch] BLOCKED: inPinchGesture is true");
            return;
        }

        // Check if we're not in a regular shake gesture (avoid false positives)
        if (currentTime - lastShakeTime < 300) {
            Log.d(TAG, "[handleAirPinch] BLOCKED: too close to wrist flick");
            return;
        }

        // Check if too much time passed since first pinch (timeout)
        if (pinchCount == 1 && currentTime - firstPinchTime > PINCH_RESET_TIMEOUT) {
            Log.d(TAG, "[handleAirPinch] TIMEOUT - resetting pinch count");
            pinchCount = 0;
            firstPinchTime = 0;
        }

        // Validate the pinch pattern: should be a sharp, quick twitch
        if (currentTime - lastPinchTime > PINCH_COOLDOWN) {
            pinchCount++;
            inPinchGesture = true;

            Log.d(TAG, "[handleAirPinch] Pinch count: " + pinchCount);
            
            if (pinchCount == 1) {
                firstPinchTime = currentTime;
                Log.d(TAG, "✋ Air pinch detected (1/2). Waiting for second pinch...");
            } else if (pinchCount >= 2 && currentTime - firstPinchTime < DOUBLE_PINCH_WINDOW) {
                Log.d(TAG, "👌 DOUBLE AIR PINCH detected - Team A point!");
                if (!isCooldownActive()) {
                    listener.onTeamAPoint();
                    triggerCooldown();
                } else {
                    Log.d(TAG, "Point ignored - cooldown active");
                }
                pinchCount = 0;
                firstPinchTime = 0;
            } else if (pinchCount >= 2) {
                Log.d(TAG, "✋ Pinch timeout - resetting (took too long: " + (currentTime - firstPinchTime) + "ms)");
                pinchCount = 1;
                firstPinchTime = currentTime;
            }

            lastPinchTime = currentTime;

            // Reset pinch detection state after cooldown
            cooldownHandler.postDelayed(() -> {
                inPinchGesture = false;
                Log.d(TAG, "[handleAirPinch] Reset inPinchGesture to false");
            }, PINCH_COOLDOWN);
            
            // Reset gyro rotation after pinch to avoid affecting next detection
            cooldownHandler.postDelayed(() -> {
                recentGyroRotation = 0;
                Log.d(TAG, "[handleAirPinch] Reset gyro rotation");
            }, 500);
        } else {
            Log.d(TAG, "[handleAirPinch] BLOCKED: cooldown active (" + (currentTime - lastPinchTime) + "ms)");
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used for gesture detection
    }
    
    // ========== Touch Gesture Listener ==========
    
    private class TouchGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float deltaX = e2.getX() - e1.getX();
            float deltaY = e2.getY() - e1.getY();
            
            // Check if it's a horizontal swipe
            if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > SWIPE_THRESHOLD) {
                if (Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (deltaX > 0) {
                        Log.d(TAG, "Swipe RIGHT detected - Team A point!");
                        if (!isCooldownActive()) {
                            listener.onTeamAPoint();
                            triggerCooldown();
                        } else {
                            Log.d(TAG, "Point ignored - cooldown active");
                        }
                    } else {
                        Log.d(TAG, "Swipe LEFT detected - Team B point!");
                        if (!isCooldownActive()) {
                            listener.onTeamBPoint();
                            triggerCooldown();
                        } else {
                            Log.d(TAG, "Point ignored - cooldown active");
                        }
                    }
                    return true;
                }
            }
            
            // Check if it's a vertical swipe
            if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > SWIPE_THRESHOLD) {
                if (Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (deltaY > 0) {
                        Log.d(TAG, "Swipe DOWN detected - Team A point!");
                        if (!isCooldownActive()) {
                            listener.onTeamAPoint();
                            triggerCooldown();
                        } else {
                            Log.d(TAG, "Point ignored - cooldown active");
                        }
                    } else {
                        Log.d(TAG, "Swipe UP detected - Team B point!");
                        if (!isCooldownActive()) {
                            listener.onTeamBPoint();
                            triggerCooldown();
                        } else {
                            Log.d(TAG, "Point ignored - cooldown active");
                        }
                    }
                    return true;
                }
            }
            
            return false;
        }
        
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            Log.d(TAG, "Double tap detected - Team A point!");
            if (!isCooldownActive()) {
                listener.onTeamAPoint();
                triggerCooldown();
            } else {
                Log.d(TAG, "Point ignored - cooldown active");
            }
            return true;
        }
    }
}

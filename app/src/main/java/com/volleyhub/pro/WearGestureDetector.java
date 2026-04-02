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
    
    // Cooldown globale tra i punti (5 secondi)
    private static final long POINT_COOLDOWN = 5000; // 5 secondi
    private long lastPointTime = 0;
    private Handler cooldownHandler;
    
    // Accelerometer thresholds for gesture detection
    private static final float SHAKE_THRESHOLD = 12.0f;
    private static final long SHAKE_COOLDOWN = 1000; // ms between shakes
    private static final long GESTURE_WINDOW = 2000; // ms for double gesture
    
    private long lastShakeTime = 0;
    private int shakeCount = 0;
    private long firstShakeTime = 0;
    
    // Pinch gesture detection (air pinch - fingers together without touching screen)
    // When user pinches fingers, there's a subtle wrist twitch
    private static final float PINCH_TWITCH_THRESHOLD = 8.0f;
    private static final long PINCH_COOLDOWN = 800; // ms between pinches
    private static final long DOUBLE_PINCH_WINDOW = 1500; // ms for double pinch
    private long lastPinchTime = 0;
    private int pinchCount = 0;
    private long firstPinchTime = 0;
    private boolean inPinchGesture = false;
    
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
        this.context = context;
        this.listener = listener;
        this.cooldownHandler = new Handler(Looper.getMainLooper());
        
        // Initialize touch gesture detector
        gestureDetector = new GestureDetector(context, new TouchGestureListener());
        
        // Initialize sensor manager
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
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
        if (sensorManager != null) {
            if (accelerometer != null) {
                // Use fastest rate for gesture detection
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
                Log.d(TAG, "Accelerometer listener registered");
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
                Log.d(TAG, "Gyroscope listener registered");
            }
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
        int sensorType = event.sensor.getType();
        
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            handleAccelerometerData(event.values);
        } else if (sensorType == Sensor.TYPE_GYROSCOPE) {
            handleGyroscopeData(event.values);
        }
    }
    
    /**
     * Handle accelerometer data for wrist flick and air pinch detection
     */
    private void handleAccelerometerData(float[] values) {
        float x = values[0];
        float y = values[1];
        float z = values[2];
        
        // Calculate total acceleration (excluding gravity)
        float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
        
        // 1. Detect wrist flick (strong movement) - Team B
        if (Math.abs(acceleration) > SHAKE_THRESHOLD) {
            handleWristFlick();
        }
        
        // 2. Detect subtle "air pinch" twitch - Team A
        // When user pinches fingers together, there's a small wrist twitch (3-8 m/s²)
        else if (Math.abs(acceleration) > PINCH_TWITCH_THRESHOLD && 
                 Math.abs(acceleration) < SHAKE_THRESHOLD) {
            handleAirPinch(acceleration);
        }
    }
    
    /**
     * Handle gyroscope data for rotation detection
     */
    private void handleGyroscopeData(float[] values) {
        float angularVelocity = (float) Math.sqrt(
            values[0] * values[0] + 
            values[1] * values[1] + 
            values[2] * values[2]
        );
        
        // Gyroscope can help detect the rotation component of a pinch gesture
        // When pinching, there's often a small inward rotation
        if (angularVelocity > 2.0f && angularVelocity < 10.0f) {
            // Potential pinch rotation detected
            // We combine this with accelerometer data for better accuracy
        }
    }
    
    /**
     * Detect double wrist flick for Team B point
     */
    private void handleWristFlick() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastShakeTime < SHAKE_COOLDOWN) {
            shakeCount++;
            
            if (shakeCount == 1) {
                firstShakeTime = currentTime;
            } else if (shakeCount >= 2 && currentTime - firstShakeTime < GESTURE_WINDOW) {
                Log.d(TAG, "Double wrist flick detected - Team B point!");
                if (!isCooldownActive()) {
                    listener.onTeamBPoint();
                    triggerCooldown();
                } else {
                    Log.d(TAG, "Point ignored - cooldown active");
                }
                shakeCount = 0;
                firstShakeTime = 0;
            }
        } else {
            shakeCount = 1;
            firstShakeTime = currentTime;
        }
        
        lastShakeTime = currentTime;
    }
    
    /**
     * Detect "air pinch" gesture for Team A point
     * This tries to detect the subtle wrist movement when user pinches fingers
     */
    private void handleAirPinch(float acceleration) {
        // Avoid duplicate detection during an ongoing gesture
        if (inPinchGesture) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Check if we're not in a regular shake gesture
        if (currentTime - lastShakeTime < 500) {
            return;
        }
        
        // Detect the twitch pattern of a pinch
        if (currentTime - lastPinchTime > PINCH_COOLDOWN) {
            pinchCount++;
            inPinchGesture = true;
            
            if (pinchCount == 1) {
                firstPinchTime = currentTime;
                Log.d(TAG, "Air pinch detected (1/2). Waiting for second pinch...");
            } else if (pinchCount >= 2 && currentTime - firstPinchTime < DOUBLE_PINCH_WINDOW) {
                Log.d(TAG, "DOUBLE AIR PINCH detected - Team A point!");
                if (!isCooldownActive()) {
                    listener.onTeamAPoint();
                    triggerCooldown();
                } else {
                    Log.d(TAG, "Point ignored - cooldown active");
                }
                pinchCount = 0;
                firstPinchTime = 0;
            }
            
            lastPinchTime = currentTime;
            
            // Reset pinch detection state
            cooldownHandler.postDelayed(() -> {
                inPinchGesture = false;
            }, 500);
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

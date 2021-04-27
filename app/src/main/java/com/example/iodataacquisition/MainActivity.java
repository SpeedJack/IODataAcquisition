package com.example.iodataacquisition;

import androidx.appcompat.app.AppCompatActivity;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {

    /* Constant for the selection of the type of the accelerometer
       admitted value [STANDARD\LINEAR] */
    static final String whichAccelerometer = "LINEAR";
    static final String TAG = MainActivity.class.getName();
    static final int samplingFrequency = 5; //Hz

    /* Instance of the listener class */
    AccelerometerListener accelerometerListener;
    /*Is listening?*/
    boolean accelerometerListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

//  Obtain a reference to the accelerometer and register a listener
    public void initAccelerometer(View view){
        if(this.accelerometerListening){
            return;
        }
        SensorManager sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        Sensor accelerometer;
        if(whichAccelerometer.equals("LINEAR")){
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        }else if(whichAccelerometer.equals("STANDARD")){
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }else{
            Log.w(TAG,"Incorrect configuration - check whichAccelerometer variable");
            return;
        }
        TextView tvT = findViewById(R.id.timestamp);
        TextView tvX = findViewById(R.id.accX);
        TextView tvY = findViewById(R.id.accY);
        TextView tvZ = findViewById(R.id.accZ);
        accelerometerListener = new AccelerometerListener(tvT,tvX,tvY,tvZ);
        if(! sensorManager.registerListener(accelerometerListener,accelerometer,samplingFrequency)){
            Log.w(TAG,"Unable to reach the sensor and register a listener");
            return;
        }
        this.accelerometerListening = true;
    }

    public void deregisterAccelerometer(View view){
        if(this.accelerometerListening = false){
            return;
        }
        SensorManager sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        sensorManager.unregisterListener(accelerometerListener);
        this.accelerometerListening = false;
    }
}
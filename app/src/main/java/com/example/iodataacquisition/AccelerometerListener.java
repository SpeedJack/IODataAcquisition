package com.example.iodataacquisition;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;
import android.widget.TextView;

public class AccelerometerListener implements SensorEventListener {
    TextView timestampLabel;
    TextView accXLabel;
    TextView accYLabel;
    TextView accZLabel;
    static final String TAG = MainActivity.class.getName();

    public AccelerometerListener(TextView timestampLabel, TextView accXLabel, TextView accYLabel, TextView accZLabel) {
        this.timestampLabel = timestampLabel;
        this.accXLabel = accXLabel;
        this.accYLabel = accYLabel;
        this.accZLabel = accZLabel;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long timestamp = event.timestamp;
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        this.timestampLabel.setText(String.valueOf(timestamp));
        this.accXLabel.setText(String.valueOf(x));
        this.accYLabel.setText(String.valueOf(y));
        this.accZLabel.setText(String.valueOf(z));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG,"Accuracy for accelerometer changed! New accuracy is " + String.valueOf(accuracy));
    }
}

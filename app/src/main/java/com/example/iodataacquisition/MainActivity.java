package com.example.iodataacquisition;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {

    /* Constant for the selection of the type of the accelerometer
       admitted value [STANDARD\LINEAR] */
    static final String whichAccelerometer = "LINEAR";
    static final String TAG = MainActivity.class.getName();
    static final int servicePeriod = 10; //seconds. Measure after how many seconds a service is woke up in order to obtain the data from the sensor

    /*Reference to the scheduler that periodically call the service*/
    AlarmManager scheduler = null;
    PendingIntent scheduledIntent = null;

    /* Instance of the listener class */
    // AccelerometerListener accelerometerListener;
    sensorMonitoringService sensorMonitoringService;
    /*Is listening?*/
    boolean accelerometerListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        registerReceiver(broadcastReceiver, new IntentFilter(sensorMonitoringService.BROADCAST_ACTION));
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG,"Ricevuto Intent");
            TextView tvT = findViewById(R.id.timestamp);
            TextView tvX = findViewById(R.id.accX);
            TextView tvY = findViewById(R.id.accY);
            TextView tvZ = findViewById(R.id.accZ);
            long timestamp = intent.getLongExtra("timestamp",0);
            float x = intent.getFloatExtra("accX",0);
            float y = intent.getFloatExtra("accY",0);
            float z = intent.getFloatExtra("accZ",0);
            tvT.setText(String.valueOf(timestamp));
            tvX.setText(String.valueOf(x));
            tvY.setText(String.valueOf(y));
            tvZ.setText(String.valueOf(z));
        }
    };

//  Obtain a reference to the accelerometer and register a listener
    public void initAccelerometer(View view){
        Intent i = new Intent(getApplicationContext(), sensorMonitoringService.class );
        startService(i);
        if(this.accelerometerListening){
            return;
        }
        this.scheduler = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(getApplicationContext(), sensorMonitoringService.class);
        if(((Switch) findViewById(R.id.IOSwitch)).isChecked()){
            intent.putExtra("indoor",1);
        }else{
            intent.putExtra("indoor",0);
        }
        Log.i(TAG, "initAccelerometer: " + ((Switch) findViewById(R.id.IOSwitch)).isChecked());
        this.scheduledIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        scheduler.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 100000, scheduledIntent);
        this.accelerometerListening = true;
    }

    public void deregisterAccelerometer(View view){
        if(this.accelerometerListening = false){
            return;
        }
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.cancel(this.scheduledIntent);
        this.accelerometerListening = false;
    }
}
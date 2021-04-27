package com.example.iodataacquisition;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import static com.example.iodataacquisition.MainActivity.servicePeriod;

public class sensorMonitoringService extends Service implements SensorEventListener {

    private SensorManager sensorManager = null;
    static final String TAG = MainActivity.class.getName();
    public static final String BROADCAST_ACTION = "com.example.tracking.updateGUI";
    int indoor = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.indoor = intent.getIntExtra("indoor",0);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if(sensorManager.registerListener(this, accelerometer, 0)){
            Log.i(TAG, "onStartCommand: Listner registrato correttamente");
        }else{
            Log.i(TAG, "onStartCommand: Problemi nella registrazione");
        }
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long timestamp = event.timestamp;
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        Intent i = new Intent(BROADCAST_ACTION);
        i.putExtra("accX",x);
        i.putExtra("accY",y);
        i.putExtra("accZ",z);
        i.putExtra("timestamp",timestamp);
        sendBroadcast(i);
        this.sensorManager.unregisterListener(this);
        new SensorEventLoggerTask().execute(event);
        // stop the service
        stopSelf();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG,"Accuracy for accelerometer changed! New accuracy is " + String.valueOf(accuracy));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class SensorEventLoggerTask extends AsyncTask<SensorEvent, Void, Void> {
        @Override
        protected Void doInBackground(SensorEvent... events) {
            SensorEvent event = events[0];
            long timestamp = event.timestamp;
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            String baseDir = getFilesDir().getAbsolutePath();
            String fileName = "sensorsData.csv";
            String filePath = baseDir + File.separator + fileName;
            Log.i(TAG, "doInBackground: saving data to: " + filePath);
            File f = new File(filePath);
            CSVWriter writer;

            // File exist
            if(f.exists()&&!f.isDirectory())
            {
                FileWriter mFileWriter = null;
                try {
                    mFileWriter = new FileWriter(filePath, true);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
                writer = new CSVWriter(mFileWriter);
            }
            else
            {
                try {
                    writer = new CSVWriter(new FileWriter(filePath));
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            /*AGGIUNGI DATI DAI SENSORI QUI PER SALVARLI SUL CSV*/
            String[] data = {String.valueOf(indoor),String.valueOf(timestamp),String.valueOf(x),String.valueOf(y),String.valueOf(z)};

            writer.writeNext(data);
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            return null;
        }
    }

    @Override
    public void onDestroy() {
        // I want to restart this service again in one 10 seconds
        AlarmManager alarm = (AlarmManager)getSystemService(ALARM_SERVICE);
        alarm.set(
                alarm.RTC_WAKEUP,
                System.currentTimeMillis() + (1000 * servicePeriod),
                PendingIntent.getService(this, 0, new Intent(this, sensorMonitoringService.class), 0)
        );
    }
}

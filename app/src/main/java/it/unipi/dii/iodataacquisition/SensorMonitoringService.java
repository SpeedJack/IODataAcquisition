package it.unipi.dii.iodataacquisition;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.widget.Switch;

import androidx.annotation.Nullable;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SensorMonitoringService extends Service implements SensorEventListener
{

	public static final String BROADCAST_ACTION = "com.example.tracking.updateLight";
	static final String TAG = MainActivity.class.getName();
	int indoor = 0;
	private SensorManager sensorManager = null;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		this.indoor = intent.getIntExtra("indoor", 0);
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
		Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		if (sensorManager.registerListener(this, proximitySensor, -1)) {
			Log.i(TAG, "onStartCommand: Proximity listener correctly registered");
		} else {
			Log.i(TAG, "onStartCommand: Error occurred while registering the proximity listener");
		}
		if (sensorManager.registerListener(this, lightSensor, 0)) {
			Log.i(TAG, "onStartCommand: Light listener correctly registered");
		} else {
			Log.i(TAG, "onStartCommand: Error occurred while registering the light listener");
		}
		return START_STICKY;
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		Log.i(TAG, "onSensorChanged called");
		if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
			long timestamp = event.timestamp;
			float prox = event.values[0];
		} else {
			long timestamp = event.timestamp;
			float lightLevel = event.values[0];
			Intent i = new Intent(BROADCAST_ACTION);
			i.putExtra("light", lightLevel);
			i.putExtra("timestamp", timestamp);
			sendBroadcast(i);
		}
		this.sensorManager.unregisterListener(this);
		new SensorEventLoggerTask().execute(event);
		// stop the service
		stopSelf();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
		Log.i(TAG, "Accuracy for light sensor changed! New accuracy is " + accuracy);
	}

	@Override
	public void onDestroy()
	{
		AlarmManager scheduler = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(getApplicationContext(), SensorMonitoringService.class);
		PendingIntent scheduledIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		scheduler.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,1000, scheduledIntent);
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	private class SensorEventLoggerTask extends AsyncTask<SensorEvent, Void, Void>
	{
		@Override
		protected Void doInBackground(SensorEvent... events)
		{
			SensorEvent event = events[0];
			long timestamp = event.timestamp;
			float lightLevel = event.values[0];
			String baseDir = getFilesDir() + File.separator + "DataAcquired";

			File directory = new File(baseDir);
			if (!directory.exists())
				directory.mkdir();
            /*SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss");
            Date curDate = new Date();
            String stringDate = sdf.format(curDate);*/
			String fileName = "sensorsData.csv";
			String filePath = baseDir + File.separator + fileName;
			File f = new File(filePath);
			CSVWriter writer;

			// File exist
			Boolean x = f.exists();
			if (f.exists() && !f.isDirectory()) {
				FileWriter mFileWriter = null;
				try {
					mFileWriter = new FileWriter(filePath, true);
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
				writer = new CSVWriter(mFileWriter);
			} else {
				try {
					writer = new CSVWriter(new FileWriter(filePath));
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
			}

			/*AGGIUNGI DATI DAI SENSORI QUI PER SALVARLI SUL CSV*/
			String[] data = {String.valueOf(indoor), String.valueOf(timestamp), String.valueOf(event.sensor.getType()), String.valueOf(lightLevel)};

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
}

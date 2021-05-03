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

import androidx.annotation.Nullable;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static it.unipi.dii.iodataacquisition.MainActivity.BROADCAST_UPDATE_PROXIMITY;
import static it.unipi.dii.iodataacquisition.MainActivity.CODE_PROXIMITY;
import static it.unipi.dii.iodataacquisition.MainActivity.SAMPLING_RATE;

public class ProximityMonitoringService extends Service implements SensorEventListener
{

	static final String TAG = ProximityMonitoringService.class.getName();
	private SensorManager sensorManager = null;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		if (sensorManager.registerListener(this, proximitySensor, 0)) {
			Log.i(TAG, "onStartCommand: Proximity listener correctly registered");
		} else {
			Log.i(TAG, "onStartCommand: Error occurred while registering the proximity listener");
		}
		return START_STICKY;
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		long sensorTimeReference = 0;
		long myTimeReference = 0;
		if(sensorTimeReference == 0l && myTimeReference == 0l) {
			sensorTimeReference = event.timestamp;
			myTimeReference = System.currentTimeMillis();
		}
		// set event timestamp to current time in milliseconds
		event.timestamp = myTimeReference +
			Math.round((event.timestamp - sensorTimeReference) / 1000000.0);

		long timestamp = event.timestamp;
		float proximity = event.values[0];
		Intent i = new Intent(BROADCAST_UPDATE_PROXIMITY);
		i.putExtra("proximity", proximity);
		i.putExtra("timestamp", timestamp);
		sendBroadcast(i);
		this.sensorManager.unregisterListener(this);
		new ProximityMonitoringService.SensorEventLoggerTask().execute(event);
		// stop the service
		stopSelf();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
		Log.i(TAG, "Accuracy for proximity sensor changed! New accuracy is " + accuracy);
	}

	@Override
	public void onDestroy()
	{
		AlarmManager scheduler = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(getApplicationContext(), ProximityMonitoringService.class);
		PendingIntent scheduledIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		scheduler.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,SAMPLING_RATE, scheduledIntent);
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
			float proximity = event.values[0];

			/*Reference to the directory where the data will be saved*/
			String baseDir = getFilesDir() + File.separator + "DataAcquired";
			/*Reference to the file where the data will be saved*/
			String filePath = baseDir + File.separator + "sensorsData.csv";

			File directory = new File(baseDir);
			if (!directory.exists()) {
				directory.mkdir();
			}
			File f = new File(filePath);
			CSVWriter writer;

			// File exist
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
			String[] data = {String.valueOf(timestamp), String.valueOf(CODE_PROXIMITY), String.valueOf(proximity)};

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
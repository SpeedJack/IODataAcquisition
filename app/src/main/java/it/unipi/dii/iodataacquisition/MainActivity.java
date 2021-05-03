package it.unipi.dii.iodataacquisition;

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

import androidx.appcompat.app.AppCompatActivity;

import it.unipi.dii.iodataacquisition.R;


public class MainActivity extends AppCompatActivity
{

	static final String TAG = MainActivity.class.getName();
	static final int servicePeriod = 10; //seconds. Measure after how many seconds a service is woke up in order to obtain the data from the sensor
	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Log.i(TAG, "Intent received");
			TextView tvT = findViewById(R.id.timestamp);
			TextView tvL = findViewById(R.id.light);
			long timestamp = intent.getLongExtra("timestamp", 0);
			float light = intent.getFloatExtra("light", 0);
			tvT.setText(String.valueOf(timestamp));
			tvL.setText(String.valueOf(light));
		}
	};
	/*Reference to the scheduler that periodically call the service*/
	AlarmManager scheduler = null;
	PendingIntent scheduledIntent = null;
	/* Instance of the listener class */
	// AccelerometerListener accelerometerListener;
	sensorMonitoringService sensorMonitoringService;
	/*Is listening?*/
	boolean sensorListening = false;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		registerReceiver(broadcastReceiver, new IntentFilter(it.unipi.dii.iodataacquisition.sensorMonitoringService.BROADCAST_ACTION));
	}

	//  Obtain a reference to the accelerometer and register a listener
	public void initMonitoring(View view)
	{
		Intent i = new Intent(getApplicationContext(), sensorMonitoringService.class);
		startService(i);
		if (this.sensorListening) {
			return;
		}
		this.scheduler = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(getApplicationContext(), sensorMonitoringService.class);
		if (((Switch) findViewById(R.id.IOSwitch)).isChecked()) {
			intent.putExtra("indoor", 1);
		} else {
			intent.putExtra("indoor", 0);
		}
		Log.i(TAG, "initAccelerometer: " + ((Switch) findViewById(R.id.IOSwitch)).isChecked());
		this.scheduledIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		scheduler.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000, scheduledIntent);
		this.sensorListening = true;
	}

	public void stopMonitoring(View view)
	{
		if (this.sensorListening = false) {
			return;
		}
		AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		alarmManager.cancel(this.scheduledIntent);
		this.sensorListening = false;
	}
}
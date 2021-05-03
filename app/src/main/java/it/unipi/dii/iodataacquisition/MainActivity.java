package it.unipi.dii.iodataacquisition;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
{

	/*------------------------------------Sensor-type----------------------------------------*/
	/*
	 This codes will be used in order to distinguish between the different data logged into the
	 csv file
	*/

	public static int CODE_LIGHT_SENSOR = 1;
	public static int CODE_WIFI_AP = 2;
	public static int CODE_PROXIMITY = 3;
	/*---------------------------------------------------------------------------------------*/

	/*-----------------------Useful-in-order-to-update-the-GUI--------------------------------*/
	public static final String BROADCAST_UPDATE_LIGHT = "it.unipi.dii.iodataacquisition.updateLight";
	public static final String BROADCAST_UPDATE_WIFI_NUMBER = "it.unipi.dii.iodataacquisition.updateWiFiNumber";
	public static final String BROADCAST_UPDATE_PROXIMITY = "it.unipi.dii.iodataacquisition.updateProximity";

	private final BroadcastReceiver broadcastLightReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Log.i(TAG, "Update for the light value received");
			TextView tvT = findViewById(R.id.timestamp_L);
			TextView tvL = findViewById(R.id.light);
			long timestamp = intent.getLongExtra("timestamp", 0);
			float light = intent.getFloatExtra("light", 0);
			tvT.setText(getDate(timestamp));
			tvL.setText(String.valueOf(light));
		}
	};

	private final BroadcastReceiver broadcastWiFiNumberReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Log.i(TAG, "Update for the number of WiFi AP received");
			TextView tvW = findViewById(R.id.wifi);
			int wifi_number = intent.getIntExtra("wifi_number", 0);
			tvW.setText(String.valueOf(wifi_number));
		}
	};

	private final BroadcastReceiver broadcastProximityReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Log.i(TAG, "Update for the proximity value received");
			TextView tvT = findViewById(R.id.timestamp_P);
			TextView tvL = findViewById(R.id.proximity);
			long timestamp = intent.getLongExtra("timestamp", 0);
			float proximity = intent.getFloatExtra("proximity", 0);
			tvT.setText(getDate(timestamp));
			tvL.setText(String.valueOf(proximity));
		}
	};
	/*----------------------------------------------------------------------------------------*/

	public static int SAMPLING_RATE = 1000000;


	static final String TAG = MainActivity.class.getName();
	WiFiAPCounter wiFiAPCounter;


	/*Reference to the scheduler that periodically call the service*/
	AlarmManager scheduler = null;
	/* Instance of the listener class */
	LightMonitoringService sensorMonitoringService;
	/*Is listening?*/
	boolean sensorListening = false;
	/*Unique constant in order to distinguish between the different requests*/
	private static final int ACCESS_FINE_LOCATION_STATE_PERMISSION_CODE = 100;
	private PendingIntent scheduledIntentL;
	private PendingIntent scheduledIntentP;
	private PendingIntent scheduledIntentW;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		/*-----------------Register-the-receivers-in-order-to-update-the-GUI--------------*/

		registerReceiver(broadcastLightReceiver, new IntentFilter(BROADCAST_UPDATE_LIGHT));
		registerReceiver(broadcastWiFiNumberReceiver, new IntentFilter(BROADCAST_UPDATE_WIFI_NUMBER));
		registerReceiver(broadcastProximityReceiver, new IntentFilter(BROADCAST_UPDATE_PROXIMITY));

		/*--------------------------------------------------------------------------------*/
	}

	//  Obtain a reference to the accelerometer and register a listener
	public void initMonitoring(View view)
	{

		if (this.sensorListening) {
			return;
		}

		/*Check for the permissions in order to scan the WiFi Access Points*/
		checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, ACCESS_FINE_LOCATION_STATE_PERMISSION_CODE);

		/*Create the Broadcast Receiver that will obtain the Intent containing the WiFi AP
		information, take a reference in order to unregister it when stop is pressed*/
		if(wiFiAPCounter == null){
			/*Reference to the directory where the data will be saved*/
			String baseDir = getFilesDir() + File.separator + "DataAcquired";
			/*Reference to the file where the data will be saved*/
			String filePath = baseDir + File.separator + "sensorsData.csv";
			wiFiAPCounter = new WiFiAPCounter(baseDir,filePath);
			/* Register the WiFi Access Points Counter with an Intent filter*/
			IntentFilter intentFilter = new IntentFilter();
			intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
			getApplicationContext().registerReceiver(wiFiAPCounter,intentFilter);
		}


		this.scheduler = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

		Intent intentL = new Intent(getApplicationContext(), LightMonitoringService.class);
		Intent intentP = new Intent(getApplicationContext(), ProximityMonitoringService.class);
		Intent intentW = new Intent(getApplicationContext(), WiFiMonitoringService.class);
		/*
		if (((Switch) findViewById(R.id.IOSwitch)).isChecked()) {
			intent.putExtra("indoor", 1);
		} else {
			intent.putExtra("indoor", 0);
		}
		*/

		this.scheduledIntentL = PendingIntent.getService(getApplicationContext(), 0, intentL, PendingIntent.FLAG_UPDATE_CURRENT);
		this.scheduledIntentP = PendingIntent.getService(getApplicationContext(), 0, intentP, PendingIntent.FLAG_UPDATE_CURRENT);
		this.scheduledIntentW = PendingIntent.getService(getApplicationContext(), 0, intentW, PendingIntent.FLAG_UPDATE_CURRENT);
		scheduler.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,SAMPLING_RATE, scheduledIntentL);
		scheduler.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,SAMPLING_RATE, scheduledIntentP);
		scheduler.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,SAMPLING_RATE, scheduledIntentW);

		this.sensorListening = true;
	}

	public void stopMonitoring(View view)
	{
		if (this.sensorListening = false) {
			return;
		}
		AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		alarmManager.cancel(this.scheduledIntentL);
		alarmManager.cancel(this.scheduledIntentP);
		alarmManager.cancel(this.scheduledIntentW);

		this.sensorListening = false;
	}

	// Function to check and request permission.
	public void checkPermission(String permission, int requestCode)
	{
		if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {

			// Requesting the permission
			ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
		}
		else {
			Toast.makeText(MainActivity.this, "Permission Access_Fine_Location already granted", Toast.LENGTH_SHORT).show();
		}
	}

	// This function is called when the user accepts or decline the permission.
	// Request Code is used to check which permission called this function.
	// This request code is provided when the user is prompt for permission.

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == ACCESS_FINE_LOCATION_STATE_PERMISSION_CODE) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				Toast.makeText(MainActivity.this, "Access_Fine_Location Permission Granted", Toast.LENGTH_SHORT) .show();
			}
			else {
				Toast.makeText(MainActivity.this, "Access_Fine_Location Permission Denied", Toast.LENGTH_SHORT) .show();
			}
		}/*
		else if (requestCode == STORAGE_PERMISSION_CODE) {
			if (grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				Toast.makeText(MainActivity.this, "Storage Permission Granted", Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(MainActivity.this, "Storage Permission Denied", Toast.LENGTH_SHORT).show();
			}
		}*/
	}

	private String getDate(long time) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
		return formatter.format(new Date(time));
	}
}
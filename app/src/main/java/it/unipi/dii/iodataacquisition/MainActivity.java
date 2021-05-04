package it.unipi.dii.iodataacquisition;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.SensorEvent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity
{
	private static final int REQUEST_ENABLE_BT = 200;

	/*------------------------------------Sensor-type----------------------------------------*/
	/*
	 This codes will be used in order to distinguish between the different data logged into the
	 csv file
	*/

	public static int CODE_LIGHT_SENSOR = 1;
	public static int CODE_WIFI_AP = 2;
	public static int CODE_PROXIMITY = 3;
	public static int CODE_BLT = 4;
	/*---------------------------------------------------------------------------------------*/

	/*-----------------------Useful-in-order-to-update-the-GUI--------------------------------*/
	public static final String BROADCAST_UPDATE_LIGHT = "it.unipi.dii.iodataacquisition.updateLight";
	public static final String BROADCAST_UPDATE_WIFI_NUMBER = "it.unipi.dii.iodataacquisition.updateWiFiNumber";
	public static final String BROADCAST_UPDATE_BLT_NUMBER = "it.unipi.dii.iodataacquisition.updateBLTNumber";
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

	private final BroadcastReceiver broadcastBLTReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Log.i(TAG, "Update for the BLT value received");
			TextView tvB = findViewById(R.id.blt);
			int BLTNumber = intent.getIntExtra("BLT_number", 0);
			tvB.setText(String.valueOf(BLTNumber));
		}
	};
	/*----------------------------------------------------------------------------------------*/

	public static long TIMEOUT_HW_MILLISECONDS = 10000;

	/*--------Since scanning process takes 12 seconds we can use a minor frequency------------*/
	public static long TIMEOUT_WIFI_BLT_MILLISECONDS = 60000;

	static final String TAG = MainActivity.class.getName();

	WiFiAPCounter wiFiAPCounter = null;
	BLTCounter bltCounter = null;

	/*Reference to the scheduler that periodically call the service*/
	AlarmManager scheduler = null;
	/* Instance of the listener class */
	LightMonitoringService sensorMonitoringService;
	/*Is listening?*/
	boolean sensorListening = false;

	/*---Unique constant in order to distinguish between the different permission requests----*/

	private static final int ACCESS_FINE_LOCATION_STATE_PERMISSION_CODE = 100;
	private static final int ACCESS_BACKGROUND_LOCATION_PERMISSION_CODE = 101;

	/*----------------------------------------------------------------------------------------*/

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
		registerReceiver(broadcastBLTReceiver, new IntentFilter(BROADCAST_UPDATE_BLT_NUMBER));

		/*--------------------------------------------------------------------------------*/
	}

	//  Obtain a reference to the accelerometer and register a listener
	public void initMonitoring(View view)
	{

		if (this.sensorListening) {
			return;
		}

		/*--------------------------Check for the permissions-----------------------------*/
		checkPermission(Manifest.permission.ACCESS_FINE_LOCATION,
			ACCESS_FINE_LOCATION_STATE_PERMISSION_CODE);

		/*--------------------------Android-Version->=-10---------------------------------*/
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION,
				ACCESS_BACKGROUND_LOCATION_PERMISSION_CODE);
		}
		/*--------------------------------------------------------------------------------*/

		/*--------------Check if the device has bluetooth and turn on it------------------*/
		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {
			Log.i(TAG, "initMonitoring: Device doesn't support bluetooth");
			// Device doesn't support Bluetooth
		}
		if (!bluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}

		/*--------------------------------------------------------------------------------*/

		String fileName = new SimpleDateFormat("'DataAcquired'yyyyMMddHHmm'.csv'").format(new Date());

		/*Reference to the directory where the data will be saved*/
		String baseDir = getFilesDir() + File.separator + "DataAcquired";
		/*Reference to the file where the data will be saved*/
		String filePath = baseDir + File.separator + fileName;

		/*Create the Broadcast Receiver that will obtain the Intent containing the WiFi AP
		information, take a reference in order to unregister it when stop is pressed*/
		if(wiFiAPCounter == null){
			wiFiAPCounter = new WiFiAPCounter(baseDir,filePath);
			/* Register the WiFi Access Points Counter with an Intent filter*/
			IntentFilter intentFilter = new IntentFilter();
			intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
			getApplicationContext().registerReceiver(wiFiAPCounter,intentFilter);
		}

		if(bltCounter == null){
			bltCounter = new BLTCounter(baseDir,filePath);
			IntentFilter intentFilter = new IntentFilter();
			intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
			intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
			getApplicationContext().registerReceiver(bltCounter,intentFilter);
		}

		this.scheduler = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

		Intent intentL = new Intent(getApplicationContext(), LightMonitoringService.class);
		Intent intentP = new Intent(getApplicationContext(), ProximityMonitoringService.class);
		Intent intentW = new Intent(getApplicationContext(), WiFiBltMonitoringService.class);

		intentL.putExtra("file_name",fileName);
		intentP.putExtra("file_name",fileName);

		this.scheduledIntentL = PendingIntent.getService(getApplicationContext(), 0, intentL, PendingIntent.FLAG_UPDATE_CURRENT);
		this.scheduledIntentP = PendingIntent.getService(getApplicationContext(), 0, intentP, PendingIntent.FLAG_UPDATE_CURRENT);
		this.scheduledIntentW = PendingIntent.getService(getApplicationContext(), 0, intentW, PendingIntent.FLAG_UPDATE_CURRENT);

		/*Without timeout in order to make the monitoring start immediately*/
		long at = System.currentTimeMillis();

		scheduler.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at, scheduledIntentL);
		scheduler.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at, scheduledIntentP);
		scheduler.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at, scheduledIntentW);

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

	public void logTransition(View view){
		SwitchCompat switchCompat = (SwitchCompat)view;
		boolean transition = switchCompat.isChecked();
		String position = "";
		if(transition){
			position = "Indoor";
		}else{
			position = "Outdoor";
		}
		Log.i(TAG, "logTransition: " + position);
		String baseDir = getFilesDir() + File.separator + "DataAcquired";
		/*Reference to the file where the data will be saved*/
		String filePath = baseDir + File.separator + "Transition.csv";
		long timestamp = System.currentTimeMillis();

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
				return;
			}
			writer = new CSVWriter(mFileWriter);
		} else {
			try {
				writer = new CSVWriter(new FileWriter(filePath));
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}

		/*AGGIUNGI DATI DAI SENSORI QUI PER SALVARLI SUL CSV*/
		String[] data = {String.valueOf(timestamp), position};

		writer.writeNext(data);
		try {
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Function to check and request permission.
	public void checkPermission(String permission, int requestCode)
	{
		if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {

			// Requesting the permission
			ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
		}
		else {
			Toast.makeText(MainActivity.this, "Permission "+ permission +" already granted", Toast.LENGTH_SHORT).show();
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
				Toast.makeText(MainActivity.this, "ACCESS_FINE_LOCATION_STATE Permission Granted", Toast.LENGTH_SHORT) .show();
			}
			else {
				Toast.makeText(MainActivity.this, "ACCESS_FINE_LOCATION_STATE Permission Denied", Toast.LENGTH_SHORT) .show();
			}
		} else if (requestCode == ACCESS_BACKGROUND_LOCATION_PERMISSION_CODE) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				Toast.makeText(MainActivity.this, "ACCESS_BACKGROUND_LOCATION Granted", Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(MainActivity.this, "ACCESS_BACKGROUND_LOCATION Denied", Toast.LENGTH_SHORT).show();
			}
		}
	}

	private String getDate(long time) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
		return formatter.format(new Date(time));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
	{
		if(requestCode == REQUEST_ENABLE_BT){
			if(resultCode == RESULT_OK){
				Toast.makeText(MainActivity.this, "Bluetooth turned on", Toast.LENGTH_SHORT).show();
			}else{
				Log.i(TAG, "onActivityResult: Can't turn on the bluetooth");
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
}
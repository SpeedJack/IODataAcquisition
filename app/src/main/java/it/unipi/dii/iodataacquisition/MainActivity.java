package it.unipi.dii.iodataacquisition;

import android.Manifest;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import info.hoang8f.android.segmented.SegmentedGroup;

import static com.google.android.gms.location.DetectedActivity.*;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, RadioGroup.OnCheckedChangeListener
{
	private static final String TAG = MainActivity.class.getName();
	/*The request code associated to the request for the Bluetooth activation*/
	private static final int REQUEST_ENABLE_BT = 0xBEEF;

	/*Request code associated to the permissions requests*/
	private static final int ACCESS_FINE_LOCATION_STATE_PERMISSION_CODE = 100;
	private static final int ACCESS_BACKGROUND_LOCATION_PERMISSION_CODE = 101;
	private static final int ACTIVITY_RECOGNITION_PERMISSION_CODE = 102;

	/*Reference to the SensorMonitoringService*/
	private SensorMonitoringService boundService;
	/*This Intent will contain the description of the SensorMonitoringService.*/
	private Intent serviceIntent;

	/*Utility function used in order to convert the timestamp into a string that contains the date*/
	private String getDate(long time)
	{
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());
		return formatter.format(new Date(time));
	}

	/*Utility function to convert the activity recognized by the Google API expressed (int) to
	the correspondent string value*/
	private String ActivityToString(int activityType){
		switch (activityType){
			case  IN_VEHICLE:
				return "In vehicle";
			case ON_BICYCLE:
				return "On bicycle";
			case ON_FOOT:
				return "On foot";
			case  RUNNING:
				return "Running";
			case STILL:
				return "Still";
			case  TILTING:
				return  "Tilting";
			case WALKING:
				return "Walking";
			default:
				return "Unknown";
		}
	}


	/*This is the BroadcastReceiver responsible for updating the GUI*/
	private final BroadcastReceiver sensorDataReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			SensorData data = intent.getParcelableExtra("data");
			if (data == null)
				return;
			TextView valueTextView = null;
			TextView timeTextView = null;
			if (data.getSensorType() == Sensor.TYPE_LIGHT) {
				valueTextView = findViewById(R.id.light);
				timeTextView = findViewById(R.id.timestamp_L);
			} else if (data.getSensorType() == Sensor.TYPE_PROXIMITY) {
				valueTextView = findViewById(R.id.proximity);
				timeTextView = findViewById(R.id.timestamp_P);
			} else if (data.getSensorName().equals("WIFI_ACCESS_POINTS")) {
				valueTextView = findViewById(R.id.wifi);
			} else if (data.getSensorName().equals("BLUETOOTH_DEVICES")) {
				valueTextView = findViewById(R.id.blt);
			} else if (data.getSensorName().equals("DETECTED_ACTIVITY")) {
				valueTextView = findViewById(R.id.activity);
			} else if (data.getSensorName().equals("GPS_SATELLITES")) {
				valueTextView = findViewById(R.id.satellites);
			} else if (data.getSensorName().equals("GPS_FIX_SATELLITES")) {
				valueTextView = findViewById(R.id.fixSatellites);
			} else if (data.getSensorName().equals("GPS_FIX")) {
				timeTextView = findViewById(R.id.lastFix);
			}

			if (valueTextView != null)
				if (data.getSensorName().equals("DETECTED_ACTIVITY")) {
					valueTextView.setText(ActivityToString((int) data.getValue()));
				} else {
					DecimalFormat df = new DecimalFormat("#.##");
					valueTextView.setText(df.format(data.getValue()));
				}
			if (timeTextView != null)
				timeTextView.setText(getDate(data.getTimestamp()));
		}
	};

	private void setMonitoringEnabled()
	{
		setMonitoringEnabled(true);
	}

	/*Function that update the GUI in order to show if the smartphone is currently monitoring*/
	private void setMonitoringEnabled(boolean enabled)
	{
		TextView statusTextView = findViewById(R.id.monitoringStatusTextView);
		statusTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(enabled ?
				R.drawable.monitoring_enabled : R.drawable.monitoring_disabled,
			0, 0, 0);
		statusTextView.setText(enabled ?
			R.string.monitoring_service_enabled : R.string.monitoring_service_disabled);
		ToggleButton toggleButton = findViewById(R.id.monitoringToggleButton);
		toggleButton.setChecked(enabled);
		/* User has the possibility to delete or share the log only if the smartphone is not
		monitoring */
		Button shareButton = findViewById(R.id.shareButton);
		shareButton.setEnabled(!enabled);
		Button deleteButton = findViewById(R.id.deleteButton);
		deleteButton.setEnabled(!enabled);
	}

	/*Class for monitoring the state of the service*/
	private final ServiceConnection serviceConnection = new ServiceConnection() {
		/*Called when a connection to the SensorMonitoringService has been established*/
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			/*Obtain a reference to the SensorMonitoringService*/
			boundService = ((SensorMonitoringService.ServiceBinder)service).getService();
			/*Update properly the GUI*/
			SegmentedGroup ioSwitch = findViewById(R.id.ioSwitch);
			ioSwitch.check(boundService.isIndoor() ? R.id.indoorButton : R.id.outdoorButton);
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			boundService = null;
		}

		@Override
		public void onBindingDied(ComponentName name)
		{
			boundService = null;
		}
	};

	/*Generic function that is used in order to request to user permissions*/
	private void checkPermission(String permission, int requestCode)
	{
		if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
			Log.d(TAG, "Permission " + permission + " already granted.");
			return;
		}
		/*If the permission is not already granted we request it*/
		requestPermissions(new String[] { permission }, requestCode);
	}

	/*Generic function that is called when the users gives the permission*/
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		String permissionName = String.valueOf(requestCode);
		if (requestCode == ACCESS_FINE_LOCATION_STATE_PERMISSION_CODE)
			permissionName = "ACCESS_FINE_LOCATION_STATE";
		else if (requestCode == ACCESS_BACKGROUND_LOCATION_PERMISSION_CODE)
			permissionName = "ACCESS_BACKGROUND_LOCATION";
		else if (requestCode == ACTIVITY_RECOGNITION_PERMISSION_CODE)
			permissionName = "ACTIVITY_RECOGNITION";
		boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
		Log.d(TAG, permissionName + " " + (granted ? "granted." : "denied."));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
	{
		/*if the user turn on the Bluetooth a Toast is created and shown*/
		if(requestCode == REQUEST_ENABLE_BT)
			if(resultCode == RESULT_OK)
				Toast.makeText(MainActivity.this, "Bluetooth turned on", Toast.LENGTH_SHORT).show();
			else
				Log.e(TAG, "onActivityResult: Can't turn on the bluetooth.");
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		SegmentedGroup ioSwitch = findViewById(R.id.ioSwitch);
		/*We select OUTDOOR as default in the radio button*/
		ioSwitch.check(R.id.outdoorButton);
		/*If the SensorMonitoringService is running we properly update the GUI*/
		boolean serviceRunning = isServiceRunning();
		setMonitoringEnabled(serviceRunning);
		/*If the SensorMonitoringService is running we bind it*/
		if (serviceRunning) {
			if (serviceIntent == null)
				serviceIntent = new Intent(MainActivity.this, SensorMonitoringService.class);
			if (!bindService(serviceIntent, serviceConnection, 0)) {
				Log.e(TAG, "Can not bind service.");
				stopMonitoring();
			}
		}
		/* We register the current class as listener in order to catch the transition
		between indoor and outdoor that user indicates by means of the radio button */
		ioSwitch.setOnCheckedChangeListener(this);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		/*Unregister the BroadcastReceiver that updates the GUI*/
		if (isServiceRunning())
			try {
				unregisterReceiver(sensorDataReceiver);
			} catch (Exception exception) {
				exception.printStackTrace();
			}
		/*Unbind the SensorMonitoringService*/
		if (boundService != null)
			try {
				unbindService(serviceConnection);
			} catch (Exception exception) {
				exception.printStackTrace();
			}
		boundService = null;
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		/*If the SensorMonitoringService is running we properly update the GUI*/
		boolean serviceRunning = isServiceRunning();
		setMonitoringEnabled(serviceRunning);
		/*We register the BroadcastReceiver in order to obtain updates for the GUI*/
		registerReceiver(sensorDataReceiver, new IntentFilter("it.unipi.dii.iodataacquisition.SENSORDATA"));
		if (!serviceRunning)
			return;
		/*If the SensorMonitoringService is running we obtain again a reference to the SensorMonitoringService*/
		if (serviceIntent == null)
			serviceIntent = new Intent(MainActivity.this, SensorMonitoringService.class);
		if (boundService == null && !bindService(serviceIntent, serviceConnection, 0)) {
			Log.e(TAG, "Can not bind service.");
			stopMonitoring();
		}
	}

	private void startMonitoring()
	{
		if (isServiceRunning())
			return;
		/*Get a reference to the file where the data will be logged, if the file doesn't
		* exists will be created with the corresponding headers.*/
		File outputFile = new File(getFilesDir() + File.separator + "collected-data.csv");
		if (!outputFile.exists()) {
			CSVWriter writer;
			try {
				writer = new CSVWriter(new FileWriter(outputFile, true),
					',', '"', '\\', "\n");
				writer.writeNext(
					new String[]{"timestamp", "sensor_type", "sensor_name", "value", "accuracy"},
					false);
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
		/*Check if the user has already granted the necessary permissions, if not they will
		* be requested. */
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
			checkPermission(Manifest.permission.ACTIVITY_RECOGNITION, ACTIVITY_RECOGNITION_PERMISSION_CODE);
		checkPermission(Manifest.permission.ACCESS_FINE_LOCATION,
			ACCESS_FINE_LOCATION_STATE_PERMISSION_CODE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
			checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION,
				ACCESS_BACKGROUND_LOCATION_PERMISSION_CODE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
			checkPermission(Manifest.permission.ACTIVITY_RECOGNITION,ACTIVITY_RECOGNITION_PERMISSION_CODE);

		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {
			Log.e(TAG, "startMonitoring: Device doesn't support bluetooth.");
		} else if (!bluetoothAdapter.isEnabled()) {
			/*If the Bluetooth is off we request the user to turn it on*/
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}

		/*Into the intent used to start the SensorMonitoringService in foreground we include
		* the current setting as indicated by the user.*/
		serviceIntent = new Intent(MainActivity.this, SensorMonitoringService.class);
		serviceIntent.putExtra("Indoor", ((RadioButton)findViewById(R.id.indoorButton)).isChecked());

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			startForegroundService(serviceIntent);
		else
			startService(serviceIntent);

		if (!bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
			Log.e(TAG, "Can not bind service.");
			return;
		}

		/*We register the BroadcastReceiver in order to properly update the GUI*/
		registerReceiver(sensorDataReceiver, new IntentFilter("it.unipi.dii.iodataacquisition.SENSORDATA"));
		setMonitoringEnabled();
	}

	/*Function that is used in order to stop the SensorMonitoringService*/
	private void stopMonitoring()
	{
		if (!isServiceRunning())
			return;
		try {
			/*We unregister the BroadcastReceiver that is used in order to update the
			* GUI*/
			unregisterReceiver(sensorDataReceiver);
		} catch (Exception e) {
			Log.d(TAG, "BroadcastReceiver already unregistered.");
		}
		if (boundService != null) {
			/*We call the flush function on the reference of the SensorMonitoringService
			* class in order to save the last value logged.*/
			boundService.flush(true);
			/*We unbind the connection with the SensorMonitoringService*/
			unbindService(serviceConnection);
		}
		if (serviceIntent != null)
			/*Request to stop the SensorMonitoringService.*/
			stopService(serviceIntent);
		/*Properly update the GUI*/
		setMonitoringEnabled(false);
	}

	/*Utility function that select the proper function to call if the SensorMonitoringService is
	* is running.*/
	private void toggleMonitoring()
	{
		if (isServiceRunning())
			stopMonitoring();
		else
			startMonitoring();
	}

	/*Function that is used in order to share the log .csv file.*/
	private void shareLog()
	{
		String filePath = getFilesDir() + File.separator + "collected-data.csv";
		File logFile = new File(filePath);
		Intent intentShareFile = new Intent(Intent.ACTION_SEND);

		if(!logFile.exists())
			return;
		Uri fileUri = FileProvider.getUriForFile(this, "it.unipi.dii.iodataacquisition", logFile);
		intentShareFile.setDataAndType(fileUri, "text/csv");
		intentShareFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intentShareFile.putExtra(Intent.EXTRA_STREAM, fileUri);
		intentShareFile.putExtra(Intent.EXTRA_SUBJECT, "Log File");
		intentShareFile.putExtra(Intent.EXTRA_TEXT, "IODataAcquisition Log File");
		startActivity(Intent.createChooser(intentShareFile, "Share Log"));
	}

	/*Function that is used in order to delete the log .csv file, after that the buttons delete
	* and share are disabled.*/
	private void deleteLog()
	{
		String filePath = getFilesDir() + File.separator + "collected-data.csv";
		File logFile = new File(filePath);
		if (logFile.exists() && logFile.delete()) {
			findViewById(R.id.deleteButton).setEnabled(false);
			findViewById(R.id.shareButton).setEnabled(false);
		}
	}

	/*General onClick function that is shared by all the buttons*/
	@Override
	public void onClick(View v)
	{
		if (v.getId() == R.id.monitoringToggleButton)
			toggleMonitoring();
		else if (v.getId() == R.id.shareButton)
			shareLog();
		else if (v.getId() == R.id.deleteButton)
			deleteLog();
	}

	/*When the user change the value of the radio button the function in setIndoor of the class
	* SensorMonitoringService is called in order to register the users transition through the
	* reference to the SensorMonitoringService.*/
	@Override
	public void onCheckedChanged(RadioGroup group, int checkedId)
	{
		if (group.getId() == R.id.ioSwitch && boundService!=null)
			boundService.setIndoor(checkedId == R.id.indoorButton);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		super.onWindowFocusChanged(hasFocus);
		RadioButton radioButtonOutdoor = findViewById(R.id.outdoorButton);
		int textSize = getTextSizeRadioButtons(10, 150);
		radioButtonOutdoor.setTextSize(textSize);
		((RadioButton)findViewById(R.id.indoorButton)).setTextSize(textSize);
	}

	/*Utility function that is used in order to change the text size in order to fill the screen
	* size.*/
	public int getTextSizeRadioButtons(float textSize, int width)
	{
		Paint paint = new Paint();
		paint.setTextSize(textSize);
		String text = " Outdoor ";
		Rect bounds = new Rect();
		paint.getTextBounds(text, 0, text.length(), bounds);
		Log.i(TAG, "getTextSizeRadioButtons: " + bounds.width());
		if (bounds.width() * getApplicationContext().getResources().getDisplayMetrics().density < width)
			return getTextSizeRadioButtons(textSize + 5, width);
		else
			return (int) (textSize - 5);
	}

	/*Utility function that can be used in order to check if the SensorMonitoringService is
	* currently running*/
	private boolean isServiceRunning()
	{
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service: manager.getRunningServices(Integer.MAX_VALUE))
			if (SensorMonitoringService.class.getName().equals(service.service.getClassName()))
				return true;
		return false;
	}
}
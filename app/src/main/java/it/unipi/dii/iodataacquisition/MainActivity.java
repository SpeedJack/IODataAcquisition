package it.unipi.dii.iodataacquisition;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import info.hoang8f.android.segmented.SegmentedGroup;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, RadioGroup.OnCheckedChangeListener
{
	private static final int REQUEST_ENABLE_BT = 0xDEADBEEF;

	private boolean monitoringEnabled = false;
	private Intent serviceIntent;

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
			TextView accuracyTextView = null;
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
			}

			if (valueTextView != null) {
				DecimalFormat df = new DecimalFormat("#.##");
				valueTextView.setText(df.format(data.getValue()));
			}
			if (timeTextView != null)
				timeTextView.setText(getDate(data.getTimestamp()));
			if (accuracyTextView != null)
				accuracyTextView.setText(data.getAccuracy());
		}
	};

	public static long TIMEOUT_HW_MILLISECONDS = 10000;

	/*--------Since scanning process takes 12 seconds we can use a minor frequency------------*/
	public static long TIMEOUT_WIFI_BLT_MILLISECONDS = 60000;

	static final String TAG = MainActivity.class.getName();

	/*---Unique constant in order to distinguish between the different permission requests----*/

	private static final int ACCESS_FINE_LOCATION_STATE_PERMISSION_CODE = 100;
	private static final int ACCESS_BACKGROUND_LOCATION_PERMISSION_CODE = 101;

	private SensorMonitoringService boundService;

	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			boundService = ((SensorMonitoringService.ServiceBinder)service).getService();
			boundService.setIOStatus(((RadioButton)findViewById(R.id.indoorButton)).isChecked());
			boundService.collectCurrentIOStatus();
			boundService.enableCollection();
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

	private void checkPermission(String permission, int requestCode)
	{
		if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
			Log.d(TAG, "Permission " + permission + " already granted.");
			return;
		}
		requestPermissions(new String[] { permission }, requestCode);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		String permissionName = String.valueOf(requestCode);
		if (requestCode == ACCESS_FINE_LOCATION_STATE_PERMISSION_CODE)
			permissionName = "ACCESS_FINE_LOCATION_STATE";
		else if (requestCode == ACCESS_BACKGROUND_LOCATION_PERMISSION_CODE)
			permissionName = "ACCESS_BACKGROUND_LOCATION";
		boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
		Log.d(TAG, permissionName + " " + (granted ? "granted." : "denied."));
	}

	private String getDate(long time)
	{
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
		return formatter.format(new Date(time));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
	{
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
		boolean monenabled = false;
		boolean iostatus = false;
		if (savedInstanceState != null) {
			monenabled = savedInstanceState.getBoolean("monitoring_enabled");
			iostatus = savedInstanceState.getBoolean("status_indoor");
		}

		SegmentedGroup ioSwitch = (SegmentedGroup)findViewById(R.id.ioSwitch);
		ioSwitch.check(iostatus ? R.id.indoorButton : R.id.outdoorButton);
		ioSwitch.setOnCheckedChangeListener(this);

		setMonitoringEnabled(monenabled);
		if (monenabled) {

			if(serviceIntent == null){
				serviceIntent = new Intent(MainActivity.this, SensorMonitoringService.class);
			}

			if (!bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
				Log.e(TAG, "Can not bind service.");
				stopMonitoring();
				return;
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putBoolean("monitoring_enabled", monitoringEnabled);
		outState.putBoolean("status_indoor", ((RadioButton)findViewById(R.id.indoorButton)).isChecked());
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (monitoringEnabled)
			unregisterReceiver(sensorDataReceiver);
		if (boundService != null)
			try {
				unbindService(serviceConnection);
			} catch (Exception e) {
				boundService = null;
			}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		if (!monitoringEnabled)
			return;
		registerReceiver(sensorDataReceiver, new IntentFilter("it.unipi.dii.iodataacquisition.SENSORDATA"));
		if (!bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
			Log.e(TAG, "Can not bind service.");
			stopMonitoring();
		}
	}

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
		monitoringEnabled = enabled;
	}

	private void setMonitoringEnabled()
	{
		setMonitoringEnabled(true);
	}

	private void startMonitoring()
	{
		if (monitoringEnabled)
			return;

		checkPermission(Manifest.permission.ACCESS_FINE_LOCATION,
			ACCESS_FINE_LOCATION_STATE_PERMISSION_CODE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
			checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION,
				ACCESS_BACKGROUND_LOCATION_PERMISSION_CODE);

		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null)
			Log.e(TAG, "startMonitoring: Device doesn't support bluetooth.");
		else if (!bluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}

		serviceIntent = new Intent(MainActivity.this, SensorMonitoringService.class);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForegroundService(serviceIntent);
		}else {
			startService(serviceIntent);
		}
		if (!bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
			Log.e(TAG, "Can not bind service.");
			return;
		}

		registerReceiver(sensorDataReceiver, new IntentFilter("it.unipi.dii.iodataacquisition.SENSORDATA"));
		setMonitoringEnabled();
	}

	private void stopMonitoring()
	{
		if (!monitoringEnabled)
			return;
		try {
			unregisterReceiver(sensorDataReceiver);
		} catch (Exception e) {
			Log.d(TAG, "BroadcastReceiver already unregistered.");
		}
		if (boundService != null) {
			boundService.flush();
			unbindService(serviceConnection);
		}
		if (serviceIntent != null)
			stopService(serviceIntent);
		setMonitoringEnabled(false);
	}

	private void toggleMonitoring()
	{
		if (monitoringEnabled)
			stopMonitoring();
		else
			startMonitoring();
	}

	@Override
	public void onClick(View v)
	{
		if (v.getId() == R.id.monitoringToggleButton)
			toggleMonitoring();
	}

	@Override
	public void onCheckedChanged(RadioGroup group, int checkedId)
	{
		if (group.getId() == R.id.ioSwitch && boundService != null)
			boundService.setIOStatus(checkedId == R.id.indoorButton);
	}
}
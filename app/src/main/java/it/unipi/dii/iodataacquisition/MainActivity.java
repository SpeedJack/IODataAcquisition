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
import android.hardware.Sensor;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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

	private static final String TAG = MainActivity.class.getName();

	private static final int ACCESS_FINE_LOCATION_STATE_PERMISSION_CODE = 100;
	private static final int ACCESS_BACKGROUND_LOCATION_PERMISSION_CODE = 101;
	private static final int ACTIVITY_RECOGNITION_PERMISSION_CODE = 102;

	private SensorMonitoringService boundService;

	private final ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			boundService = ((SensorMonitoringService.ServiceBinder)service).getService();
			SegmentedGroup ioSwitch = (SegmentedGroup)findViewById(R.id.ioSwitch);
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
		else if (requestCode == ACTIVITY_RECOGNITION_PERMISSION_CODE)
			permissionName = "ACTIVITY_RECOGNITION";
		boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
		Log.d(TAG, permissionName + " " + (granted ? "granted." : "denied."));
	}

	private String getDate(long time)
	{
		//FIXME: warning
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

		SegmentedGroup ioSwitch = (SegmentedGroup)findViewById(R.id.ioSwitch);
		ioSwitch.check(R.id.outdoorButton);
		boolean serviceRunning = isServiceRunning();
		setMonitoringEnabled(serviceRunning);
		if (serviceRunning) {
			if (serviceIntent == null)
				serviceIntent = new Intent(MainActivity.this, SensorMonitoringService.class);
			if (!bindService(serviceIntent, serviceConnection, 0)) {
				Log.e(TAG, "Can not bind service.");
				stopMonitoring();
			}
		}
		ioSwitch.setOnCheckedChangeListener(this);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (isServiceRunning())
			try {
				unregisterReceiver(sensorDataReceiver);
			} catch (Exception ignored) {
			}
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
		boolean serviceRunning = isServiceRunning();
		setMonitoringEnabled(serviceRunning);
		if (serviceRunning)
			return;
		//FIXME: receiver does not work after resume
		registerReceiver(sensorDataReceiver, new IntentFilter("it.unipi.dii.iodataacquisition.SENSORDATA"));
		if (serviceIntent == null)
			serviceIntent = new Intent(MainActivity.this, SensorMonitoringService.class);
		if (boundService == null && !bindService(serviceIntent, serviceConnection, 0)) {
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
	}

	private void setMonitoringEnabled()
	{
		setMonitoringEnabled(true);
	}

	private void startMonitoring()
	{
		if (isServiceRunning())
			return;

		File outputFile = new File(getExternalFilesDir(null) + File.separator + "collected-data.csv");
		Log.i(TAG, "startMonitoring: " + getExternalFilesDir(null));
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

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
			checkPermission(Manifest.permission.ACTIVITY_RECOGNITION, ACTIVITY_RECOGNITION_PERMISSION_CODE);
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
		serviceIntent.putExtra("Indoor", ((RadioButton)findViewById(R.id.indoorButton)).isChecked());

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			startForegroundService(serviceIntent);
		else
			startService(serviceIntent);

		if (!bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
			Log.e(TAG, "Can not bind service.");
			return;
		}

		registerReceiver(sensorDataReceiver, new IntentFilter("it.unipi.dii.iodataacquisition.SENSORDATA"));
		setMonitoringEnabled();
	}

	private void stopMonitoring()
	{
		if (!isServiceRunning())
			return;
		try {
			unregisterReceiver(sensorDataReceiver);
		} catch (Exception e) {
			Log.d(TAG, "BroadcastReceiver already unregistered.");
		}
		if (boundService != null) {
			boundService.flush(true);
			unbindService(serviceConnection);
		}
		if (serviceIntent != null)
			stopService(serviceIntent);
		setMonitoringEnabled(false);
	}

	private void toggleMonitoring()
	{
		if (isServiceRunning())
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
			boundService.setIndoor(checkedId == R.id.indoorButton);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		super.onWindowFocusChanged(hasFocus);
		adaptRadioButtonsToScreen();
	}

	public void adaptRadioButtonsToScreen()
	{
		RadioButton radioButtonOutdoor = findViewById(R.id.outdoorButton);
		int widthDP = radioButtonOutdoor.getMeasuredWidth();
		Log.i(TAG, String.format("adaptRadioButtonsToScreen: %d DP", widthDP));
		int widthPX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDP, getApplicationContext().getResources().getDisplayMetrics());
		// 2 is added in order to have some margin
		int stringLength = radioButtonOutdoor.getText().length() + 2;
		int letterPX = Math.floorDiv(widthPX , stringLength);
		float letterSP = letterPX / getResources().getDisplayMetrics().scaledDensity;
		Log.i(TAG, String.format("adaptRadioButtonsToScreen: %s", letterSP));
		radioButtonOutdoor.setTextSize(TypedValue.COMPLEX_UNIT_DIP,widthDP/stringLength);
		((RadioButton)findViewById(R.id.indoorButton)).setTextSize(TypedValue.COMPLEX_UNIT_DIP,widthDP/stringLength);
	}
	private boolean isServiceRunning()
	{
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service: manager.getRunningServices(Integer.MAX_VALUE))
			if (SensorMonitoringService.class.getName().equals(service.service.getClassName()))
				return true;
		return false;
	}
}
package it.unipi.dii.iodataacquisition;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SensorMonitoringService extends Service implements SensorEventListener
{
	private static final String TAG = SensorMonitoringService.class.getName();
	private static final long SCAN_INTERVAL = 60;
	private static final long PERIODIC_DELAY = 1000;
	private static final long WIFI_BT_COLLECT_INTERVAL = 60;
	private static final long FLUSH_INTERVAL = 60;

	private SensorManager sensorManager;
	private WifiManager wifiManager;
	private BluetoothAdapter bluetoothAdapter;
	private ConcurrentLinkedQueue<SensorData> collectedData = new ConcurrentLinkedQueue<SensorData>();
	private Handler periodicHandler;
	private Runnable periodicRunnable;
	private long lastLightTimestamp = -1;
	private long lastProxTimestamp = -1;
	private long lastScan = -1;
	private long lastWiFiBTCheck = -1;
	private long lastFlush = -1;
	private boolean indoor;
	private boolean previousIndoor;
	private final IBinder binder = new ServiceBinder();
	private boolean enabled = false;
	private WiFiAPCounter wiFiAPCounter;
	private BLTCounter bltCounter;
	private PowerManager.WakeLock mWakeLock;

	public class ServiceBinder extends Binder
	{
		SensorMonitoringService getService()
		{
			return SensorMonitoringService.this;
		}
	}

	public SensorMonitoringService()
	{
		indoor = false;
		previousIndoor = indoor;
	}

	private void scan()
	{
		if (System.currentTimeMillis() - lastScan < SCAN_INTERVAL * 1000)
			return;
		lastScan = System.currentTimeMillis();
		if (!wifiManager.isWifiEnabled())
			Log.w(TAG, "WiFi is not enabled.");
		else if (!wifiManager.startScan()) // FIXME: deprecated
			Log.e(TAG, "Failed to start WiFi scan.");
		if (!bluetoothAdapter.isEnabled())
			Log.w(TAG, "Bluetooth is not enabled.");
		else if (!bluetoothAdapter.isDiscovering())
			if (!bluetoothAdapter.startDiscovery())
				Log.e(TAG, "Failed to start BT discovery.");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		this.enabled = false;
		sensorManager = (SensorManager)getApplicationContext().getSystemService(SENSOR_SERVICE);
		wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		periodicHandler = new Handler(Looper.getMainLooper());
		periodicRunnable = new Runnable() {
			@Override
			public void run()
			{
				periodicHandler.postDelayed(this, PERIODIC_DELAY);
				periodicCollection();
			}
		};

		Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
		if (lightSensor == null)
			Log.e(TAG, "Device does not have a light sensor!");
		else if (!sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_FASTEST))
			Log.e(TAG, "Unable to register listener for sensor " + lightSensor.getName() + ".");
		Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		if (proximitySensor == null)
			Log.e(TAG, "Device does not have a proximity sensor!");
		else if (!sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_FASTEST))
			Log.e(TAG, "Unable to register listener for sensor " + proximitySensor.getName() + ".");
		periodicHandler.post(periodicRunnable);

		wiFiAPCounter = new WiFiAPCounter();
		IntentFilter intentFilter_WIFI = new IntentFilter();
		intentFilter_WIFI.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		getApplicationContext().registerReceiver(wiFiAPCounter, intentFilter_WIFI);

		bltCounter = new BLTCounter();
		IntentFilter intentFilter_BLT = new IntentFilter();
		intentFilter_BLT.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		intentFilter_BLT.addAction(BluetoothDevice.ACTION_FOUND);
		getApplicationContext().registerReceiver(bltCounter,intentFilter_BLT);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		mWakeLock.acquire();

		Log.d(TAG, "Service started!");
		return START_STICKY;
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		Log.i(TAG, "onCreate: The service has been created");
		Notification notification = createNotification();
		startForeground(1, notification);
	}

	private Notification createNotification() {
		String notificationChannelId = "IO DATA ACQUISITION NOTIFICATION CHANNEL";

		// depending on the Android API that we're dealing with we will have
		// to use a specific method to create the notification
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			NotificationChannel notificationChannel = new NotificationChannel(notificationChannelId,"IO Data Acquisition",NotificationManager.IMPORTANCE_HIGH);
			notificationChannel.setLightColor(Color.RED);
			notificationChannel.setDescription("IO Data Acquisition");
			notificationChannel.enableLights(true);
			notificationChannel.enableVibration(true);
			notificationManager.createNotificationChannel(notificationChannel);
		}

		Intent notificationIntent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this,0,notificationIntent,0);

		Notification.Builder builder;
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
			builder = new Notification.Builder(this,notificationChannelId);
		}else{
			builder = new Notification.Builder(this);
		}

		builder.setContentTitle("IODataAcquisition");
		builder.setContentText("Acquiring data from sensors...");
		builder.setContentIntent(pendingIntent);
		builder.setSmallIcon(R.mipmap.ic_launcher);
		builder.setPriority(Notification.PRIORITY_HIGH);

		return builder.build();
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return binder;
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		if (!enabled || event == null || event.values.length == 0)
			return;
		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
			if (event.timestamp - lastLightTimestamp < 1000 * 1000 * 1000)
				return;
			lastLightTimestamp = event.timestamp;
		} else if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
			if (event.timestamp - lastProxTimestamp < 1000 * 1000 * 1000)
				return;
			lastProxTimestamp = event.timestamp;
		} else {
			return;
		}
		SensorData data = new SensorData(event);
		collectedData.add(data);
		sendSensorDataToActivity(data);
	}

	private void sendSensorDataToActivity(SensorData data)
	{
		Intent intent = new Intent("it.unipi.dii.iodataacquisition.SENSORDATA");
		intent.putExtra("data", data);
		sendBroadcast(intent);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
	}

	private void collectActivity()
	{

	}

	public void flush()
	{
		if (System.currentTimeMillis() - lastFlush < FLUSH_INTERVAL * 1000)
			return;
		lastFlush = System.currentTimeMillis();

		File output = new File(getFilesDir() + File.separator + "collected-data.csv");
		CSVWriter writer;
		try {
			writer = new CSVWriter(new FileWriter(output, true),
				',', '"', '\\', "\n");
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		SensorData data;
		while ((data = collectedData.poll()) != null)
			writer.writeNext(data.toStringArray(), false);
		try {
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void collectWiFiBTCount()
	{
		if (System.currentTimeMillis() - lastWiFiBTCheck < WIFI_BT_COLLECT_INTERVAL * 1000)
			return;
		lastWiFiBTCheck = System.currentTimeMillis();
		int lastWiFiAPNumber = wiFiAPCounter.getLastWiFiAPNumber();
		if(lastWiFiAPNumber != -1){
			SensorData data = new SensorData("WIFI_ACCESS_POINTS",lastWiFiAPNumber);
			sendSensorDataToActivity(data);
			collectedData.add(data);
		}
		int lastBLTNumber = bltCounter.getLastBLTNumber();
		if(lastBLTNumber != -1){
			SensorData data = new SensorData("BLUETOOTH_DEVICES",lastBLTNumber);
			sendSensorDataToActivity(data);
			collectedData.add(data);
		}
	}

	public void setIOStatus(boolean indoor)
	{
		this.indoor = indoor;
	}

	public void indoor()
	{
		setIOStatus(true);
	}

	public void outdoor()
	{
		setIOStatus(false);
	}

	public void enableCollection()
	{
		this.enabled = true;
	}

	public void collectCurrentIOStatus()
	{
		previousIndoor = indoor;
		SensorData data = new SensorData("INDOOR", indoor ? 1 : 0);
		collectedData.add(data);
		sendSensorDataToActivity(data);
	}

	public synchronized void periodicCollection()
	{
		if (!enabled)
			return;
		if (indoor != previousIndoor)
			collectCurrentIOStatus();
		collectWiFiBTCount();
		collectActivity();
		flush();
		scan();
	}

	@Override
	public void onDestroy()
	{
		Log.i(TAG, "onDestroy: Distrutto");
		if (periodicHandler != null && periodicRunnable != null)
			periodicHandler.removeCallbacks(periodicRunnable);
		if (sensorManager != null)
			sensorManager.unregisterListener(this);
		if (wiFiAPCounter != null)
			getApplicationContext().unregisterReceiver(wiFiAPCounter);
		if (bltCounter != null)
			getApplicationContext().unregisterReceiver(bltCounter);
		super.onDestroy();
		mWakeLock.release();
	}

}
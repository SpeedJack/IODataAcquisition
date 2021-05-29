package it.unipi.dii.iodataacquisition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.tasks.Task;
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SensorMonitoringService extends Service implements SensorEventListener, LocationListener
{
	private static final String TAG = SensorMonitoringService.class.getName();

	/*----------------------------------CONSTANT-INTERVALS----------------------------------*/
	private static final long SCAN_INTERVAL = 60;
	private static final long PERIODIC_DELAY = 1000;
	private static final long WIFI_BT_COLLECT_INTERVAL = 60;
	private static final long FLUSH_INTERVAL = 60;
	private static final long WAKELOCK_TIMEOUT = 120 * 60 * 1000L;
	private static final long GPS_UPDATE_INTERVAL = 30 * 1000L;
	private static final long ACTIVITY_COLLECTION_INTERVAL = 30 * 1000;
	/*----------------------------------------------------------------------------------------*/

	private SensorManager sensorManager;
	private WifiManager wifiManager;
	private BluetoothAdapter bluetoothAdapter;
	private LocationManager locationManager;
	/*Temporary list of datas collected from the sensors*/
	private final ConcurrentLinkedQueue<SensorData> collectedData = new ConcurrentLinkedQueue<>();
	private Handler periodicHandler;
	private Runnable periodicRunnable;
	private long lastScan = -1;
	private long lastWiFiBTCheck = -1;
	private long lastFlush = -1;
	private boolean indoor;
	private final IBinder binder = new ServiceBinder();
	private WiFiAPCounter wiFiAPCounter;
	private BLTCounter bltCounter;
	private PowerManager.WakeLock mWakeLock;
	private GnssStatus.Callback gnssStatusCallback;
	private int lastSatCount = -1;
	private int lastFixCount = -1;

	private ActivityRecognitionClient mActivityRecognitionClient;

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
	}

	/*Utility function that is used in order to start the scan both for Bluetooth devices and
	* for the Wi-Fi Access Points.*/
	private void scan()
	{
		/*Scan only SCAN_INTERVAL seconds*/
		if (System.currentTimeMillis() - lastScan < SCAN_INTERVAL * 1000)
			return;
		/*Update the value of the last scan*/
		lastScan = System.currentTimeMillis();
		if (!wifiManager.isWifiEnabled())
			Log.w(TAG, "WiFi is not enabled.");
		else if (!wifiManager.startScan())
			Log.e(TAG, "Failed to start WiFi scan.");
		if (!bluetoothAdapter.isEnabled())
			Log.w(TAG, "Bluetooth is not enabled.");
		else if (!bluetoothAdapter.isDiscovering())
			if (!bluetoothAdapter.startDiscovery())
				Log.e(TAG, "Failed to start BT discovery.");
	}

	/*Called by the system every time the MainActivity explicitly starts the service by calling
	* Context.startService(Intent). Note that the intent contains the current setting in Boolean
	* format INDOOR -> true, OUTDOOR -> false.*/
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		boolean isIndoor = intent.getBooleanExtra("Indoor", false);
		indoor = !isIndoor;
		setIndoor(isIndoor);

		sensorManager = (SensorManager) getApplicationContext().getSystemService(SENSOR_SERVICE);
		wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		mActivityRecognitionClient = new ActivityRecognitionClient(getApplicationContext());

		/*Function that will periodically executed by the Foreground Service, in particular
		* every PERIODIC_DELAY MilliSeconds the function periodicCollection will be called*/
		periodicHandler = new Handler(Looper.getMainLooper());
		periodicRunnable = new Runnable()
		{
			@Override
			public void run()
			{
				periodicHandler.postDelayed(this, PERIODIC_DELAY);
				periodicCollection();
			}
		};

		/*Get the reference to the LightSensor and register this class as the listener for
		* values from that sensor.*/
		Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
		if (lightSensor == null)
			Log.e(TAG, "Device does not have a light sensor!");
		else if (!sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_FASTEST))
			Log.e(TAG, "Unable to register listener for sensor " + lightSensor.getName() + ".");

		/*Get the reference to the ProximitySensor and register this class as the listener for
		 * values from that sensor.*/
		Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		if (proximitySensor == null)
			Log.e(TAG, "Device does not have a proximity sensor!");
		else if (!sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_FASTEST))
			Log.e(TAG, "Unable to register listener for sensor " + proximitySensor.getName() + ".");
		periodicHandler.post(periodicRunnable);

		/*Register a new instance of the WiFiAPCounter class as BroadcastReceiver both
		* for SCAN_RESULTS_AVAILABLE_ACTION */
		wiFiAPCounter = new WiFiAPCounter();
		IntentFilter intentFilter_WIFI = new IntentFilter();
		intentFilter_WIFI.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		getApplicationContext().registerReceiver(wiFiAPCounter, intentFilter_WIFI);

		/*Register a new instance of the BLTCounter class as BroadcastReceiver both for
		* ACTION_DISCOVERY_FINISHED and ACTION_FOUND */
		bltCounter = new BLTCounter();
		IntentFilter intentFilter_BLT = new IntentFilter();
		intentFilter_BLT.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		intentFilter_BLT.addAction(BluetoothDevice.ACTION_FOUND);
		getApplicationContext().registerReceiver(bltCounter, intentFilter_BLT);

		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			Log.e(TAG, "ACCESS_FINE_LOCATION not granted. Can not get GPS status.");
		} else {
			/*GPS callback registration*/
			locationManager = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
			gnssStatusCallback = new GnssStatus.Callback() {
				@Override
				public void onStarted()
				{
					Log.i(TAG, "onStarted: gnssStatusCallback -> Started ");
				}

				@Override
				public void onStopped()
				{
					Log.i(TAG, "onStarted: gnssStatusCallback -> Stopped ");
				}

				@SuppressLint("MissingPermission")
				@Override
				public void onFirstFix(int ttffMillis)
				{
					/*Data about the last time when a GPS fix was performed are
					* added to the temporary list and used to update the GUI. */
					SensorData data = new SensorData("GPS_FIX", 1);
					collectedData.add(data);
					sendSensorDataToActivity(data);
				}

				@Override
				public void onSatelliteStatusChanged(GnssStatus status)
				{
					int satCount = status.getSatelliteCount();
					if (satCount != lastSatCount) {
						/*If the number of GPS satellites on the line of sight
						* changes the new value is added to the temporary
						* list and the GUI is properly updated*/
						SensorData data = new SensorData("GPS_SATELLITES", satCount);
						collectedData.add(data);
						sendSensorDataToActivity(data);
					}
					lastSatCount = satCount;

					int fixCount = 0;
					for (int i = 0; i < satCount; i++)
						if (status.usedInFix(i))
							fixCount++;
					if (fixCount != lastFixCount) {
						/*If the number of GPS satellites fixed
						* changes the new value is added to the temporary
						* list and the GUI is properly updated*/
						SensorData data = new SensorData("GPS_FIX_SATELLITES", fixCount);
						collectedData.add(data);
						sendSensorDataToActivity(data);
					}
					lastFixCount = fixCount;
				}
			};
			locationManager.registerGnssStatusCallback(gnssStatusCallback, new Handler(Looper.getMainLooper()));
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				GPS_UPDATE_INTERVAL, 0, this);
		}

		/*Request the Google Activity Recognition API to perform the Activity recognition
		* and register the Intent Service that will receive the results.*/
		Task<Void> task = mActivityRecognitionClient.requestActivityUpdates(
			ACTIVITY_COLLECTION_INTERVAL,
			getActivityDetectionPendingIntent());
		task.addOnSuccessListener(result -> {});

		task.addOnFailureListener(e -> Log.e(TAG, "Activity detection failure: " + e.toString()));

		registerReceiver(activityDataReceiver,new IntentFilter(DetectedActivitiesIntentService.ACTIVITY_DETECTED_ACTION));

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		mWakeLock.acquire(WAKELOCK_TIMEOUT);

		SensorData data = new SensorData("MONITORING", 1);
		collectedData.add(data);
		Log.d(TAG, "Service started!");
		return START_STICKY;
	}

	/**
	 * Gets a PendingIntent to be sent for each activity detection.
	 */
	private PendingIntent getActivityDetectionPendingIntent()
	{
		Intent intent = new Intent(getApplicationContext(), DetectedActivitiesIntentService.class);
		return PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		Log.d(TAG, "onCreate: The service has been created.");
		Notification notification = createNotification();
		startForeground(1, notification);
	}

	/*Utility function that creates the Notification associated to this foreground service*/
	private Notification createNotification()
	{
		String notificationChannelId = "IO DATA ACQUISITION NOTIFICATION CHANNEL";

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			NotificationChannel notificationChannel = new NotificationChannel(notificationChannelId, "IO Data Acquisition", NotificationManager.IMPORTANCE_HIGH);
			notificationChannel.setLightColor(Color.RED);
			notificationChannel.setDescription("IO Data Acquisition");
			notificationChannel.enableLights(true);
			notificationChannel.enableVibration(true);
			notificationManager.createNotificationChannel(notificationChannel);
		}

		Intent notificationIntent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

		Notification.Builder builder;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			builder = new Notification.Builder(this, notificationChannelId);
		else
			builder = new Notification.Builder(this);

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

	/*Method called when the vaules detected by the hardware sensors(Light Sensor and Proximity
	* Sensor) changes*/
	@Override
	public void onSensorChanged(SensorEvent event)
	{
		if (event == null || event.values.length == 0
			|| (event.sensor.getType() != Sensor.TYPE_LIGHT
			&& event.sensor.getType() != Sensor.TYPE_PROXIMITY))
			return;
		/*The data obtained from the sensor will be encoded in a new object SensorData and
		* added to the list collected data.*/
		SensorData data = new SensorData(event);
		collectedData.add(data);
		sendSensorDataToActivity(data);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
		Log.i(TAG, "onAccuracyChanged: sensor = " + sensor.toString() + "accuracy = "
			+ accuracy);
	}

	/*Utility function that is used in order to send the updates to the Main Activity in order
	* to update properly the GUI.*/
	private void sendSensorDataToActivity(SensorData data)
	{
		Intent intent = new Intent("it.unipi.dii.iodataacquisition.SENSORDATA");
		intent.putExtra("data", data);
		getApplicationContext().sendBroadcast(intent);
	}

	/*This method is used in order to store the data acquired from the sensor in the .csv file
	* and the temporary list of data collected is flushed.
	* Synchronized method is a method which can be used by only one thread at a time in this
	* case, the flush method can be called both from:
	* - MainActivity
	* - SensorMonitoringService
	* force argument is used in order to force the data log even if the time elapsed is not
	* sufficient.*/
	public synchronized void flush(boolean force)
	{
		/*Check if at least FLUSH_INTERVAL seconds have been passed*/
		if (!force && System.currentTimeMillis() - lastFlush < FLUSH_INTERVAL * 1000)
			return;
		lastFlush = System.currentTimeMillis();

		/*Write to the csv file all the data temporary stored locally*/
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

	public void flush()
	{
		flush(false);
	}

	/*Utility function that calls the getter from the WiFiAPCounter and the BLTCounter in order
	* to get the value of the number of Bluetooth devices and Wi-Fi Access points in the nearby.*/
	private void collectCounters()
	{
		if (System.currentTimeMillis() - lastWiFiBTCheck < WIFI_BT_COLLECT_INTERVAL * 1000)
			return;
		lastWiFiBTCheck = System.currentTimeMillis();
		int lastWiFiAPNumber = wiFiAPCounter.getLastWiFiAPNumber();
		if (lastWiFiAPNumber != -1) {
			SensorData data = new SensorData("WIFI_ACCESS_POINTS", lastWiFiAPNumber);
			collectedData.add(data);
			sendSensorDataToActivity(data);
		}
		int lastBLTNumber = bltCounter.getLastBLTNumber();
		if (lastBLTNumber != -1) {
			SensorData data = new SensorData("BLUETOOTH_DEVICES", lastBLTNumber);
			collectedData.add(data);
			sendSensorDataToActivity(data);
		}
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
			&& ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			return;
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
			&& ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
			return;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
			locationManager.getCurrentLocation(LocationManager.GPS_PROVIDER, null, getMainExecutor(), this::onLocationChanged);
		else
			locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, Looper.getMainLooper());
	}

	/*Function used in order to log transitions between an indoor and outdoor setting*/
	public void setIndoor(boolean indoor)
	{
		Log.d(TAG, "SET INDOOR: " + indoor + "; WAS: " + this.indoor);
		if (indoor != this.indoor) {
			SensorData data = new SensorData("INDOOR", indoor ? 1 : 0);
			collectedData.add(data);
		}
		this.indoor = indoor;
	}

	public boolean isIndoor()
	{
		return indoor;
	}
	/*Function periodically called that will collect all the fresh recently acquired values store
	* in into a csv file and start again the scanning for Wi-Fi Access Point and Bluetooth
	* devices*/
	public synchronized void periodicCollection()
	{
		collectCounters();
		flush();
		scan();
		if (!mWakeLock.isHeld())
			mWakeLock.acquire(WAKELOCK_TIMEOUT);
	}

	@Override
	public void onDestroy()
	{
		Log.d(TAG, "onDestroy: Service destroyed.");
		if (mWakeLock != null && mWakeLock.isHeld())
			mWakeLock.release();
		if (periodicHandler != null && periodicRunnable != null)
			periodicHandler.removeCallbacks(periodicRunnable);
		if (sensorManager != null)
			sensorManager.unregisterListener(this);
		if (wiFiAPCounter != null)
			getApplicationContext().unregisterReceiver(wiFiAPCounter);
		if (bltCounter != null)
			getApplicationContext().unregisterReceiver(bltCounter);
		if (locationManager != null) {
			locationManager.removeUpdates(this);
			locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
		}

		this.mActivityRecognitionClient.removeActivityUpdates(getActivityDetectionPendingIntent());
		unregisterReceiver(activityDataReceiver);

		SensorData data = new SensorData("MONITORING", 0);
		collectedData.add(data);
		flush(true);
		super.onDestroy();
	}

	/*BroadcastReceiver for the Activity Detected by the Google Activity Detection API that is
	* is sent by the DetectedActivitiesIntentService*/
	private final BroadcastReceiver activityDataReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			/*Add to the list of the data collected the Activity Detection detected*/
			SensorData activityData = intent.getParcelableExtra("data");
			collectedData.add(activityData);
			sendSensorDataToActivity(activityData);
		}
	};

	@Override
	public void onLocationChanged(@NonNull Location location)
	{
		Log.i(TAG, "onLocationChanged: location = " + location.toString());
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras)
	{
		Log.i(TAG, "onStatusChanged: provider = " + provider);
		Log.i(TAG, "onStatusChanged: status = " + status);
		Log.i(TAG, "onStatusChanged: extras = " + extras.toString());
	}

	@Override
	public void onProviderEnabled(@NonNull String provider)
	{
		Log.i(TAG, "onProviderEnabled: provider = " + provider);
	}

	@Override
	public void onProviderDisabled(@NonNull String provider)
	{
		Log.i(TAG, "onProviderDisabled: provider = " + provider);
	}
}
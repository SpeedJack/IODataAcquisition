package it.unipi.dii.iodataacquisition;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import static it.unipi.dii.iodataacquisition.MainActivity.TIMEOUT_HW_MILLISECONDS;
import static it.unipi.dii.iodataacquisition.MainActivity.TIMEOUT_WIFI_BLT_MILLISECONDS;

public class WiFiBltMonitoringService extends Service
{
	static final String TAG = WiFiBltMonitoringService.class.getName();

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		/*Get the reference to the WiFiManager and BluetoothAdapter*/
		WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		/*Start scanning the WiFi AccessPoint if it fails false possible reasons are:
			- too many scans in a short time.
			- device is idle and scanning is disabled.
		 */
		if(!wifiManager.startScan()){
			Log.i(TAG, "onStartCommand: failed to start WiFi Access Points scanning");
		}
		if(!bluetoothAdapter.startDiscovery()){
			Log.i(TAG, "onStartCommand: failed to start Bluetooth devices discovery");
		}

		/*Nothing to do except rescheduling the next WiFi and BLT scan*/
		stopSelf();
		return START_STICKY;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	@Override
	public void onDestroy()
	{
		AlarmManager scheduler = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(getApplicationContext(), WiFiBltMonitoringService.class);
		PendingIntent scheduledIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		long at = System.currentTimeMillis() + TIMEOUT_WIFI_BLT_MILLISECONDS;
		scheduler.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at, scheduledIntent);
	}
}
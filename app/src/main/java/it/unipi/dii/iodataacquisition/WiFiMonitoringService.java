package it.unipi.dii.iodataacquisition;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import static it.unipi.dii.iodataacquisition.MainActivity.SAMPLING_RATE;

public class WiFiMonitoringService extends Service
{
	static final String TAG = WiFiMonitoringService.class.getName();

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		/*Get the reference to the WiFiManager*/
		WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		/*Start scanning the WiFi AccessPoint if it fails false possible reasons are:
			- too many scans in a short time.
			- device is idle and scanning is disabled.
		 */
		if(!wifiManager.startScan()){
			Log.i(TAG, "onStartCommand: failed to start WiFi Access Points scanning ");
		}
		/*Nothing to do except rescheduling the next WiFi scan*/
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
		Intent intent = new Intent(getApplicationContext(), WiFiMonitoringService.class);
		PendingIntent scheduledIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		scheduler.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,SAMPLING_RATE, scheduledIntent);
	}
}
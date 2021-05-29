package it.unipi.dii.iodataacquisition;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;
import java.util.List;

/*Class responsible for counting the Wi-Fi Access Point in the nearby*/
public class WiFiAPCounter extends BroadcastReceiver
{
	static final String TAG = WiFiAPCounter.class.getName();
	int lastWiFiAPNumber = -1;
	int previousValue = -1;

	@Override
	public void onReceive(Context context, Intent intent)
	{
		boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
		if (success) {
			WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			/*We obtain the result of the Wi-Fi access points scan and its size*/
			List<ScanResult> results = wifiManager.getScanResults();
			lastWiFiAPNumber = results.size();
		} else {
			Log.w(TAG, "onReceive: requests too frequent! Just wait.");
		}
	}

	/*Getter that returns the number of Wi-Fi Access Points if is a fresh information, instead
	* -1 will be returned.*/
	public int getLastWiFiAPNumber()
	{
		if(previousValue == lastWiFiAPNumber)
			return -1;
		previousValue = lastWiFiAPNumber;
		return lastWiFiAPNumber;
	}
}

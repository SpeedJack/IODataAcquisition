package it.unipi.dii.iodataacquisition;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class WiFiAPCounter extends BroadcastReceiver
{
	static final String TAG = WiFiAPCounter.class.getName();
	int lastWiFiAPNumber = -1;
	int previousValue = -1;

	@Override
	public void onReceive(Context context, Intent intent)
	{
		boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
		if (success){
			/*Accessing the results of the scanning*/
			WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			List<ScanResult> results = wifiManager.getScanResults();
			lastWiFiAPNumber = results.size();
		}else{
			Log.i(TAG, "onReceive: requests too frequent! Just wait ");
		}
	}

	public int getLastWiFiAPNumber()
	{
		if(previousValue != lastWiFiAPNumber) {
			previousValue = lastWiFiAPNumber;
			return lastWiFiAPNumber;
		}else{
			return -1;
		}
	}
}

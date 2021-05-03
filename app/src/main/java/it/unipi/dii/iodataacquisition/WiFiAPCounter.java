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

import static it.unipi.dii.iodataacquisition.MainActivity.BROADCAST_UPDATE_WIFI_NUMBER;
import static it.unipi.dii.iodataacquisition.MainActivity.CODE_WIFI_AP;

public class WiFiAPCounter extends BroadcastReceiver
{
	static final String TAG = WiFiAPCounter.class.getName();
	int lastWiFiAPNumber = -1;
	String baseDir;
	String filePath;

	public WiFiAPCounter(String baseDir, String filePath)
	{
		this.baseDir = baseDir;
		this.filePath = filePath;
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
		boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
		if (success){
			/*Accessing the results of the scanning*/
			WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			List<ScanResult> results = wifiManager.getScanResults();
			lastWiFiAPNumber = results.size();

			/*------------------Code-in-order-to-update-the-GUI-----------------------*/
			Intent intentWiFiNumberUpdateGUI = new Intent(BROADCAST_UPDATE_WIFI_NUMBER);
			intentWiFiNumberUpdateGUI.putExtra("wifi_number", lastWiFiAPNumber);
			context.sendBroadcast(intentWiFiNumberUpdateGUI);
			/*------------------------------------------------------------------------*/

		}else{
			Log.i(TAG, "onReceive: requests too frequent! Just wait ");
		}
		/*Log the number of WiFi Access point on the same csv file*/
		if(this.lastWiFiAPNumber >= 0){

			long timestamp = System.currentTimeMillis();
			File directory = new File(this.baseDir);
			if (!directory.exists()) {
				directory.mkdir();
			}
			File f = new File(filePath);
			CSVWriter writer;
			// File exist
			if(f.exists() && !f.isDirectory()){
				FileWriter mFileWriter = null;
				try {
					mFileWriter = new FileWriter(filePath, true);
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
				writer = new CSVWriter(mFileWriter);
			}else{
				try{
					writer = new CSVWriter(new FileWriter(filePath));
				}catch (IOException e){
					e.printStackTrace();
					return;
				}
			}
			String[] data = {String.valueOf(timestamp), String.valueOf(CODE_WIFI_AP), String.valueOf(this.lastWiFiAPNumber)};
			writer.writeNext(data);
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			return;
		}
	}
}

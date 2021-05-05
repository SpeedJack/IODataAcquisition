package it.unipi.dii.iodataacquisition;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class BLTCounter extends BroadcastReceiver
{
	static final String TAG = BLTCounter.class.getName();
	int tmpBLTNumber = 0;
	int lastBLTNumber = -1;
	int previousBLTNumber = -1;

	@Override
	public void onReceive(Context context, Intent intent)
	{
		String action = intent.getAction();
		if(BluetoothDevice.ACTION_FOUND.equals(action)){
			/*---------------------A new device is discovered-------------------------*/
			Log.i(TAG, "onReceive: A new BLT device discovered");
			tmpBLTNumber += 1;
		}else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
			lastBLTNumber = tmpBLTNumber;
			tmpBLTNumber = 0;
		}
	}

	public int getLastBLTNumber()
	{
		if(lastBLTNumber != previousBLTNumber){
			previousBLTNumber = lastBLTNumber;
			return 	lastBLTNumber;
		}else{
			return -1;
		}
	}
}

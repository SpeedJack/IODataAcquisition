package it.unipi.dii.iodataacquisition;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

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
		if (BluetoothDevice.ACTION_FOUND.equals(action)) { // new device discovered
			Log.d(TAG, "onReceive: A new BLT device discovered");
			tmpBLTNumber += 1;
		} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
			lastBLTNumber = tmpBLTNumber;
			tmpBLTNumber = 0;
		}
	}

	public int getLastBLTNumber()
	{
		if (lastBLTNumber == previousBLTNumber)
			return -1;
		previousBLTNumber = lastBLTNumber;
		return lastBLTNumber;
	}
}

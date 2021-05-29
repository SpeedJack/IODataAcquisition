package it.unipi.dii.iodataacquisition;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/*Class responsible for counting the Bluetooth devices in the nearby*/
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
		/*If a BluetoothDevice is found the number of Bluetooth devices is incremented*/
		if (BluetoothDevice.ACTION_FOUND.equals(action)) {
			Log.d(TAG, "New bluetooth device discovered.");
			tmpBLTNumber += 1;
		} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
			/*If the Bluetooth discovery is finished the number of devices is saved*/
			lastBLTNumber = tmpBLTNumber;
			tmpBLTNumber = 0;
		}
	}

	/*The function return the last number of bluetooth devices founded if is a fresh information,
	* otherwise it returns -1*/
	public int getLastBLTNumber()
	{
		if (lastBLTNumber == previousBLTNumber)
			return -1;
		previousBLTNumber = lastBLTNumber;
		return lastBLTNumber;
	}
}

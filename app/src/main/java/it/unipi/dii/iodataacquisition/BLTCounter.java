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

//import static it.unipi.dii.iodataacquisition.MainActivity.BROADCAST_UPDATE_BLT_NUMBER;
//import static it.unipi.dii.iodataacquisition.MainActivity.CODE_BLT;

public class BLTCounter extends BroadcastReceiver
{
	static final String TAG = BLTCounter.class.getName();
	int lastBLTNumber = 0;
	String baseDir;
	String filePath;

	public BLTCounter(String baseDir, String filePath)
	{
		this.baseDir = baseDir;
		this.filePath = filePath;
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
		String action = intent.getAction();
		if(BluetoothDevice.ACTION_FOUND.equals(action)){
			/*---------------------A new device is discovered-------------------------*/
			Log.i(TAG, "onReceive: A new BLT device discovered");
			lastBLTNumber += 1;
		}else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
			/*-------------------The discovery is finished----------------------------*/

			/*------------------Code-in-order-to-update-the-GUI-----------------------*/
	//		Intent intentBLTNumberUpdateGUI = new Intent(BROADCAST_UPDATE_BLT_NUMBER);
	//		intentBLTNumberUpdateGUI.putExtra("BLT_number", lastBLTNumber);
	//		context.sendBroadcast(intentBLTNumberUpdateGUI);
			/*------------------------------------------------------------------------*/

			/*---------------------Logging--------------------------------------------*/
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
	//		String[] data = {String.valueOf(timestamp), String.valueOf(CODE_BLT), String.valueOf(lastBLTNumber)};
	//		writer.writeNext(data);
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			lastBLTNumber = 0;
		}
	}
}

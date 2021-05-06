package it.unipi.dii.iodataacquisition;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ActivityDetection extends BroadcastReceiver
{
	private final ConcurrentLinkedQueue<SensorData> collectedData = new ConcurrentLinkedQueue<SensorData>();
	private static final String TAG = ActivityDetection.class.getName();
	public static final String INTENT_ACTION = "it.unipi.dii.iodataacquisition.activity";
	@Override
	public void onReceive(Context context, Intent intent)
	{
		Log.i(TAG, "onReceive: RICEVUTO INTENT ACTIVITY");
		if (ActivityTransitionResult.hasResult(intent)) {
			ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
			if (result == null)
				return;
			for (ActivityTransitionEvent event : result.getTransitionEvents()) {
				SensorData data = new SensorData(event);
				Log.i(TAG, "onReceive: " + data.toString());
				collectedData.add(data);
			}
		}
	}

	public List<SensorData> flush(){
		List<SensorData> tmp = new ArrayList<>();
		SensorData data;
		while ((data = collectedData.poll()) != null){
				tmp.add(data);
		}
		return tmp;
	}
}

package it.unipi.dii.iodataacquisition;

import android.app.IntentService;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;

public class DetectedActivitiesIntentService extends IntentService
{
	public static final String TAG = DetectedActivitiesIntentService.class.getName();
	public static final String ACTIVITY_DETECTED_ACTION = "it.unipi.dii.iodataacquisition.ACTIVITYDETECTEDACTION";
	private int lastActivityType = -1;

	public DetectedActivitiesIntentService()
	{
		super(TAG);
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
	}

	@Override
	protected void onHandleIntent(@Nullable Intent intent)
	{
		ActivityRecognitionResult activityRecognitionResult = null;
		if (intent != null)
			activityRecognitionResult = ActivityRecognitionResult.extractResult(intent);
		ArrayList<DetectedActivity> detectedActivities = null;
		if (activityRecognitionResult != null)
			detectedActivities = (ArrayList<DetectedActivity>) activityRecognitionResult.getProbableActivities();
		if(detectedActivities != null && detectedActivities.get(0) != null) {
			int newActivityType = detectedActivities.get(0).getType();
			if (newActivityType != lastActivityType) {
				lastActivityType = newActivityType;
				Intent detectedActivityIntent = new Intent(ACTIVITY_DETECTED_ACTION);
				SensorData data = new SensorData("DETECTED_ACTIVITY",lastActivityType);
				detectedActivityIntent.putExtra("data",data);
				getApplicationContext().sendBroadcast(detectedActivityIntent);
			}
		}
	}
}

package it.unipi.dii.iodataacquisition;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class DetectedActivitiesIntentService extends IntentService
{
	public static final String TAG = DetectedActivitiesIntentService.class.getName();

	public DetectedActivitiesIntentService()
	{
		super(TAG);
	}
	private int lastActivityType = -1;
	private int newActivityType = -1;

	@Override
	public void onCreate()
	{
		super.onCreate();
	}

	@Override
	protected void onHandleIntent(@Nullable Intent intent)
	{
		ActivityRecognitionResult activityRecognitionResult = ActivityRecognitionResult.extractResult(intent);
		ArrayList<DetectedActivity> detectedActivities = (ArrayList<DetectedActivity>) activityRecognitionResult.getProbableActivities();
		if(detectedActivities != null && detectedActivities.get(0) != null){
			newActivityType = detectedActivities.get(0).getType();
			if(newActivityType != lastActivityType){
				lastActivityType = newActivityType;
				writeToCSV(lastActivityType);
			}
		}
	}

	public void writeToCSV(int activityType)
	{
		File output = new File(getExternalFilesDir(null) + File.separator + "collected-data.csv");
		CSVWriter writer;
		try {
			writer = new CSVWriter(new FileWriter(output, true),
				',', '"', '\\', "\n");
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		SensorData data = new SensorData("Activity",lastActivityType);
		writer.writeNext(data.toStringArray(), false);
		try {
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

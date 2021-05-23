import math
import multiprocessing
from datetime import datetime
from multiprocessing import freeze_support
from os import walk, path

import matplotlib.pyplot as plt
import pandas as pd
from tqdm import tqdm

# The day is divided into 3 different classes of light:
# - Daylight
# - Twilight
# - Night
# Since we expect a strong correlation between these values and the light acquired from the light sensor we extract from
# the timestamp the relative class of the light. The extreme of the intervals are expressed in minutes.
day_frames = {
    (0, 5 * 60 + 23): 'NIGHT',
    (5 * 60 + 24, 5 * 60 + 56): 'TWILIGHT',
    (5 * 60 + 57, 20 * 60 + 33): 'DAYLIGHT',
    (20 * 60 + 34, 21 * 60 + 6): 'TWILIGHT',
    (21 * 60 + 7, 24 * 60): 'NIGHT'
}

# Utility dictionary exploited to convert the output of the Google Activity Recognition into a string format
Detected_Activity_List = {
    0.0: "IN_VEHICLE",
    1.0: "ON_BICYCLE",
    2.0: "ON_FOOT",
    3.0: "STILL",
    4.0: "UNKNOWN",
    5.0: "TILTING",
    7.0: "WALKING",
    8.0: "RUNNING"
}

# Dictionary that contains a fictitious sensor type for all the non-hardware source of data
sensor_type_dict = {
    'MONITORING': -1,
    'INDOOR': -2,
    'GPS_SATELLITES': -3,
    'GPS_FIX_SATELLITES': -4,
    'GPS_FIX': -5,
    'DETECTED_ACTIVITY': -6,
    'WIFI_ACCESS_POINTS': -7,
    'BLUETOOTH_DEVICES': -8
}

# The final desired order of the columns
ordered_columns = [
    'LUMINOSITY',

    'LUMINOSITY30S',
    'LAST_LUMINOSITY_WHEN_FAR',
    'LAST_LUMINOSITY30S_WHEN_FAR',
    'TIME_FROM_LAST_FAR',

    'WIFI_ACCESS_POINTS',
    'BLUETOOTH_DEVICES',
    'GPS_SATELLITES',
    'GPS_FIX_SATELLITES',
    'GPS_TIME_FROM_FIX',

    'PROXIMITY',
    'DAYLIGHT',
    'TWILIGHT',
    'NIGHT',
    'IN_VEHICLE',
    'ON_BICYCLE',
    'ON_FOOT',
    'STILL',
    'TILTING',
    'WALKING',
    'RUNNING',

    'INDOOR'
]


# This is the main function the argument is the name of the file that it has to process
def preprocess_data(input_file):
    print(input_file)
    df = pd.read_csv(input_file)

    # Sensors accuracy was not consider in this phase of the study
    del df['accuracy']

    # Remove duplicate adjacent rows:
    cols = df.columns[1:]
    df = df.loc[(df[cols].shift() != df[cols]).any(axis=1)]

    # Since at the end all the dataframe will be merged in a single file the timestamp is not a sufficient index(
    # different sensor from different phones may have sampled at the same instant). For this reason a column containing
    # the file name is added and will be used -with the timestamp- as index.
    df.loc[:, 'FileName'] = input_file

    # We assign to each non-hardware source of information a sensor type according to the previously defined dictionary:
    for index, row in df.iterrows():
        if df.loc[index, 'sensor_type'] == -1:
            df.loc[index, 'sensor_type'] = sensor_type_dict[row['sensor_name']]

    # In order to create the feature together the feature vectors each time a "sensor" provide a new value we combine it
    # with the last value of each "sensor". In order to keep track of which is the last value for each "sensor" we
    # create the following dictionary:
    last_seen_values = {}
    sensor_types = df['sensor_name'].unique()
    for sensor_type in sensor_types:
        last_seen_values[sensor_type] = float('nan')

    proximity_name = ""
    light_name = ""

    for sensor_type in sensor_types:
        if 'proximity' in sensor_type.lower():
            proximity_name = sensor_type
            continue
        if 'light' in sensor_type.lower():
            light_name = sensor_type

    # In order to take into account between the fact that a certain amount of time can elapse from the instant in which
    # a transition indoor-outdoor has occurred and the instant in which the user signal it we drop the value from the
    # light sensor in a 6 seconds windows center at the signaling instant.
    switch_list = []
    for _, row in df.iterrows():
        print(row)
        if row['sensor_name'] == 'INDOOR':
            switch_list.append(row.loc['timestamp'])
    drop_list = []
    for index, row in df.iterrows():
        if row['sensor_name'] != light_name:
            continue
        for switch in switch_list:
            if switch - 3 * 1000 < row['timestamp'] < switch + 3 * 1000:
                drop_list.append(index)
    df.drop(drop_list, inplace=True)

    df_wide = df.pivot_table(index=['FileName', 'timestamp'], columns='sensor_name', values='value', aggfunc='first')

    last_gps_fix = -1
    last_lum_far = float('nan')
    time_last_far = -1
    is_far = True
    luminosity_far = {}
    for index, row in df_wide.iterrows():
        # If we found a row with "sensor" MONITORING and value 0 it means that the user has stopped the service, in this
        # case resetting all the last seen value is appropriate in order to avoid to create incorrect feature vector
        # when the user will restart the monitoring
        if row['MONITORING'] == 0:
            for sensor_type in sensor_types:
                last_seen_values[sensor_type] = float('nan')
            last_gps_fix = -1
            last_lum_far = float('nan')
            time_last_far = -1
            is_far = True
            luminosity_far = {}
        else:

            # The GPS_FIX column is replaced with the value of the interval of time elapse from the last GPS fix.
            if row['GPS_FIX'] > 0:
                last_gps_fix = index[1]
            if last_gps_fix == -1:
                df_wide.loc[index, 'GPS_FIX'] = -1
            else:
                df_wide.loc[index, 'GPS_FIX'] = (index[1] - last_gps_fix) / 1000

            # Computation of the features related to light and proximity:
            # LAST_LUMINOSITY_WHEN_FAR = last light value obtained when there aren't object that obstructed the
            #                            light sensor.
            # TIME_FROM_LAST_FAR = interval of time elapsed from the last time when there aren't objects that obstructed
            #                       the device.
            # LUMINOSITY30S = average luminosity in the last 30 seconds
            # LAST_LUMINOSITY30S_WHEN_FAR = average of the luminosity in the last 30 seconds when there aren't object
            #                               that are not obstructing the light sensor.
            if not math.isnan(row[proximity_name]):
                is_far = row[proximity_name] > 0.0
            if (not math.isnan(row[light_name])) and is_far:
                last_lum_far = row[light_name]
            if (is_far or time_last_far == -1) and not math.isnan(last_lum_far):
                time_last_far = index[1]
                luminosity_far[index[1]] = last_lum_far
            if time_last_far == -1:
                time_last_far = index[1]
            df_wide.loc[index, "TIME_FROM_LAST_FAR"] = (index[1] - time_last_far) / 1000
            df_wide.loc[index, 'LAST_LUMINOSITY_WHEN_FAR'] = last_lum_far
            lum30s = df[(df['timestamp'] >= (index[1] - 30 * 1000)) & (df['timestamp'] <= index[1]) & (
                    df['sensor_name'] == light_name)]['value'].mean()
            df_wide.loc[index, 'LUMINOSITY30S'] = lum30s
            luminosity_far = {k: v for k, v in luminosity_far.items() if k >= (time_last_far - 30 * 1000)}
            if len(luminosity_far.values()) > 0:
                luminosity_sum = 0
                for value in luminosity_far.values():
                    luminosity_sum += value
                luminosity_30s_far = luminosity_sum / len(luminosity_far.values())
            else:
                luminosity_30s_far = last_lum_far
            df_wide.loc[index, 'LAST_LUMINOSITY30S_WHEN_FAR'] = luminosity_30s_far

            # Updating the last_seen_values for each sensor
            for sensor_type in sensor_types:
                if math.isnan(row[sensor_type]):
                    df_wide.loc[index, sensor_type] = last_seen_values[sensor_type]
                last_seen_values[sensor_type] = row[sensor_type]

    # Since immediately after the start of a new monitoring session some "sensors" may not have returned a value we add
    # utility column called contains_nan in order to drop the incomplete feature vectors.
    for index, row in df_wide.iterrows():
        contains_nan = 0.0
        for feature in df_wide.columns:
            if feature != 'contains_nan' and math.isnan(row[feature]):
                contains_nan = 1.0
                break
        df_wide.loc[index, 'contains_nan'] = contains_nan

    df_wide = df_wide[(df_wide['MONITORING'] == 1.0) & (df_wide['contains_nan'] == 0.0) & (df_wide['GPS_FIX'] != -1)]
    del df_wide['contains_nan']

    # Using the day_frames dictionary described above we add a One Hot Encoding relative to the light class to the
    # feature vector. We expect that this value is strongly correlated with the light values collected in the OUTDOOR
    # setting
    for index, row in df_wide.iterrows():
        dt = datetime.fromtimestamp(index[1] / 1000)
        minutes_of_day = dt.hour * 60 + dt.minute
        for key in day_frames.keys():
            if key[1] > minutes_of_day > key[0]:
                df_wide.loc[index, 'TIME_OF_DAY'] = day_frames[key]

    for value in day_frames.values():
        df_wide[value] = df_wide['TIME_OF_DAY'] == value

    del df_wide['TIME_OF_DAY']

    # Convert the proximity values -that is vendor dependant but is always characterized by a value equal to 0 and a
    # value greater than 0- into a One Hot Encoding
    df_wide.loc[(df_wide[proximity_name] > 0.0), proximity_name] = 1

    # Convert the detected activity from Google Activity Recognition API into a One Hot Encoding
    for key in Detected_Activity_List.keys():
        df_wide[Detected_Activity_List[key]] = df_wide['DETECTED_ACTIVITY'] == key

    # Drop useless columns, order the dataframe according to the timestamp and reorder the column in the desired order
    df_wide.sort_values(['timestamp'], inplace=True)
    del df_wide['MONITORING']
    del df_wide['DETECTED_ACTIVITY']
    del df_wide['UNKNOWN']

    cols = [sensor_type for sensor_type in df_wide.columns.to_list() if sensor_type != proximity_name]
    cols.append(proximity_name)
    df_wide = df_wide[cols].replace(True, 1.0).replace(False, 0.0)
    df_wide.rename(columns={light_name: "LUMINOSITY", proximity_name: "PROXIMITY", 'GPS_FIX': "GPS_TIME_FROM_FIX"},
                   inplace=True)
    df_wide = df_wide[ordered_columns]
    return df_wide


if __name__ == '__main__':

    # Retrieve the filename of all the csv file contained inside the datasets directory
    dfs = []
    root_directory = "datasets"
    _, _, filenames = next(walk(root_directory))
    filenames_clean = [path.join(root_directory, f) for f in filenames if f.endswith('.csv')]
    # Set up the pool of processes that will execute the preprocess_data function
    n_cpu = multiprocessing.cpu_count()
    freeze_support()
    with multiprocessing.Pool(n_cpu) as pool:
        for result in tqdm(pool.imap_unordered(preprocess_data, filenames_clean), total=len(filenames_clean)):
            dfs.append(result)
        pool.close()
        pool.join()

    # Create the final dataset made by the concatenation of all the results
    df_wide = pd.concat(dfs)

    # Drop row from the general dataset in order to have the same number of indoor and outdoor feature vectors
    print(df_wide['INDOOR'].value_counts())
    df_wide.drop_duplicates(inplace=True)
    print(df_wide['INDOOR'].value_counts())
    indoor_count = df_wide[df_wide['INDOOR'] == 1.0]['INDOOR'].count()
    outdoor_count = df_wide[df_wide['INDOOR'] == 0.0]['INDOOR'].count()
    bias = abs(indoor_count - outdoor_count)
    print("Bias:", bias)
    above = 1.0 if indoor_count > outdoor_count else 0.0

    if bias > 0:
        sample = df_wide[df_wide['INDOOR'] == above].sample(n=bias)
        df_wide.drop(df_wide[df_wide.index.isin(sample.index)].index, inplace=True)
    print(df_wide['INDOOR'].value_counts())

    # Finally we apply the normalize the non-one hot encoded features of each row subtracting the mean and dividing them
    # by the standard deviation. Such value can be obtained with the describe function
    description = df_wide.describe()
    del description['INDOOR']

    bypass_norm = ['PROXIMITY', 'DAYLIGHT', 'TWILIGHT', 'NIGHT', 'IN_VEHICLE', 'ON_BICYCLE', 'ON_FOOT', 'STILL',
                   'TILTING', 'WALKING', 'RUNNING']

    for col in df_wide.columns:
        if col == 'INDOOR':
            continue
        if description.at['max', col] == 0.0:
            description.at['max', col] = 1.0
        if col in bypass_norm:
            description.at['mean', col] = 0.0
            description.at['std', col] = 1.0
            continue
        colMean = description.at['mean', col]
        colStd = description.at['std', col]
        if colMean == 0 and colStd == 0:
            description.at['std', col] = 1.0
            continue
        df_wide[col] = df_wide[col].apply(lambda x: (x - colMean) / colStd)

    df_wide.to_csv('preprocessed_data_train.csv')
    description.to_csv('meta_train.csv')

# IODataAcquisition
### Introduction
This application is part of the university project for the course of Mobile and Social Sensing Systems. The overall idea is to study possible improvement -related to context awarness- on the distance estimation problem with smartphone. In particular, we focus our attention on the problem of estimating distance with smartphone's Bluetooth module and the differences in the received signal strength value between an Indoor and Outdoor setting. An accurate estimation of the distance with smartphone enable the possibility to develop social distance monitoring app that is crucial to prevent Covid-19 diffusion.
### Application
In order acquire a dataset -useful in the development of a neural network able to detect if the users is indoor or outdoor- this application was developed. In particular, this Android application acquire and store in a file called _data-collected.csv_ the following data :
* Values detected by the proximity sensor
* Values detected by the light sensor
* Number of Wi-Fi Access Point available
* Number of Bluetooth devices available
* Number of GPS in the line of sight 
* Number of fixed GPS
* Type of the activity performed by the user exploiting the Google Activity Recognition API

In order to work properly the following permission must be granted to the IODataAcquisition application:
* ACCESS_FINE_LOCATION
* ACCESS_BACKGROUND_LOCATION
* ACTIVITY_RECOGNITION


Moreover in order to work properly GPS and bluetooth must be active, but the app don't have to be in foreground. 
The application interface is the following: </br>
<p align="center">
  <img src="doc/ApplicationInterface.jpg" width="180" height="360">
</p>

At the top of the application there is the button **START** that can be used in order to start the data acquisition, and the radio button **INDOOR** and **OUTDOOR** that can be used in order to label the data acquired by the sensors. The list of the data acquired from the sensors is displayed in order to give a glance to whats going on. At the application bottom there are two different buttons: **SHARE** in order to ease the recorded data sharing and **DELETE** that instead can be used in order to delete all the data recorded up to now. 

### Neural Network
The "nn" folder contains both the Jupyter notbooks that were used in order to extract the feature vectors from the raw data obtained by the Android application and the Colab notebooks that were used in order to train and test our custom neural network.

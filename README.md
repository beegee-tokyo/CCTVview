# CCTVview
Simple ONVIF RTSP camera viewer for Android

# IMPORTANT
This started as a project to search and access ONVIF compatible cameras.
Unfortunately the manufacturer GCWells crippled down the ONVIF compatibility, so this code is no longer a good example for detecting/accessing an ONVIF camera, but it works with my specific camera.
In addition I added a feature to show data from my local solar panel monitor, which will be useless for others.

Features:
- Searches for ONVIF compatible cameras on the LAN.
- Request and parse ONVIF Scopes, Device Information, Device Capabilities, ... from the camera
- View RTSP stream from ONVIF camera

Missing:
- Not all ONVIF commands/requests are supported
- Only video stream is available, audio stream is not implemented

# External sources
htwahzs/Rtsp-Android-Client ==> https://github.com/htwahzs/Rtsp-Android-Client
ilovecmu/android-rtsp-client-hw-decoder-demo ==> https://github.com/ilovecmu/android-rtsp-client-hw-decoder-demo

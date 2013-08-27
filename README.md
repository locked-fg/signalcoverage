# SignalCoverage 
(see also https://github.com/locked-fg/signalcoverage-server & https://github.com/locked-fg/signalcoverage-share)

[Android app](https://play.google.com/store/apps/details?id=de.locked.cellmapper)
to record signal data with GPS coordinates.
You can export your data to CSV and KML or upload to a server.

Currently the only server side application is hosted at 
[signalcoverage-locked.rhcloud.com](https://signalcoverage-locked.rhcloud.com) and is in an early beta phase.


## Upload Protocol:
In order to upload your data to a server, a very simple protocol is used.
In order to avoid some hazzle about details, the 
[signalcoverage-share](https://github.com/locked-fg/signalcoverage-share)
library covers some implementations.

The conversion from/to JSON is currently done via [GSON](https://code.google.com/p/google-gson/).

### Acquire a user
Url: ```https://<configurable server url>/<api version>/user/signUp/```  
Example: ```https://example.com/1/user/signUp/```

The **api version** defines the ```SignalShare``` protocol used to upload data (not the Android API).
The **configurable server url** is the Url that can be configured by the user in the app. 

In order to get a user:

* simply get the url
* returns: ```{"userId":2203,"secret":"mypassword"}```

These are the user credentials used in the upload process. The server side only stores a hashed 
version of the password. Be aware that obtaining the user credentials will change in the near future
Hashing is done by:

* Concatenate the userId and the password
* compute the ```SHA``` hash
* BASE64 encode the hash

This hash is further called the ```secret```.  


### Upload data 
Uploading data is done by signed REST PUT requests:

* Url: ```https://<configurable server url>/<api version>/data/<userId>/<timestamp>/<signature>/```
* The header is set to ```Content-Type: application/json```
* The data is a JSON array of the recorded data: 
```[{"time":1366491291,"accuracy":12.34,"altitude":1234.1,"satellites":5,"latitude":47.761474,"longitude":11.565152,"speed":123.45,"cdmaDbm":-1,"evdoDbm":-1,"evdoSnr":-1,"signalStrength":12,"carrier":"YourCarrier"},
... next element ...]```
 
The signature is used to validate the upload and is computed by 

* concatenating ```userId```, ```secret```, ```timestamp``` and the JSON data list
* compute the MD5 hash
* convert the hash into hex format


## Changelog:
* v2.2.0:
    * add passive provider (including setting)
    * improved logging
    * request updates on signal change
    * minor internal changes
* v2.1.0: save some new values (os, android version, ...), less battery usage, internal changes, better satellite count
* v2.0.5: Fixed possible service when stopping the service
* v2.0.4: Progressbar moved to Notifications (fixed #1)
* v2.0.3: Issue fixed #4 (DB handling)
* v2.0.2: Issue fixed #5 (hopefully), suppressed spell check in several input fields, Issue fixed #6 
* v2.0.1: Fixed renamings
* v2.0.0: Lots of rewrites & bug fixes / less permissions required / Server API / Android 4 compatibility 
* v1.0.1: Removed non working menu option
* v1:     Hello World!


## License
The app is licensed under the Apache 2.0 License.

# Starmax Bluetooth SDK Documentation

## 1. Integration Method

Import the SDK into the project libs directory

**build.gradle format:**

```gradle
sourceSets { 
    main { 
        ...other directory structure
        jniLibs.srcDirs = ['src/main/libs'] 
    }
}

dependencies { 
    //OTA library
    implementation fileTree(dir: 'src/main/libs', include: ['*.jar','*.aar'])
}
```

## 2. Required Permissions

**Bluetooth permissions:**

- android.permission.BLUETOOTH
- android.permission.BLUETOOTH_ADMIN
- android.permission.BLUETOOTH_CONNECT
- android.permission.BLUETOOTH_ADVERTISE

**Location permissions (required for BLE):**

- android.permission.ACCESS_FINE_LOCATION

**Search permissions:**

- android.permission.BLUETOOTH_SCAN

**Storage permissions:**

- android.permission.MANAGE_EXTERNAL_STORAGE
- android.permission.WRITE_EXTERNAL_STORAGE
- android.permission.READ_EXTERNAL_STORAGE

**Network permissions:**

- android.permission.INTERNET

**Foreground service:**

- android.permission.FOREGROUND_SERVICE

## 3. AndroidManifest.xml Attributes

Add to application:

```xml
<uses-feature
    android:name="android.hardware.bluetooth_le"
    android:required="true"/>

<application
    android:requestLegacyExternalStorage="true">
</application>
```

Service to enable:

```xml
<service
    android:name="com.realsil.sdk.dfu.DfuService"
    android:exported="false">
</service>
```

## 4. Return Data Structure

```kotlin
class StarmaxMapResponse(obj: Map<String, Any?>?, type: NotifyType) :
    StarmaxNotifyResponse<Map<String, Any?>>(obj, type)

class StarmaxProtobufResponse(obj: ByteArray, type: NotifyType) :
    StarmaxNotifyResponse<ByteArray>(obj, type)
```

| Field | Type              | Description                                                  | Example |
| ----- | ----------------- | ------------------------------------------------------------ | ------- |
| obj   | Map<String, Any?> | Returned message body, structured as Map type, with sub-structures containing Map or ArrayList types |         |
| type  | NotifyType        | Returned message type                                        |         |

## 5. Interface Usage

### 5.1 Usage Flow Chart:

1. Bluetooth connection
2. Connection status (Connecting/Success/Failed)
3. If successful: Modify MTU → Enable NOTIFY
4. If failed: Reconnect
5. requestMTU → Set packet transmission max value to mtuSize - 3
6. onCharacteristicChanged → Pass device return values to SDK for parsing
7. User uses SDK to wrap byteArray → Send byteArray to device

### 5.2 In Bluetooth system callback function onCharacteristicChanged(), pass device reply Bluetooth packets to SDK:

```kotlin
val response = MapStarmaxNotify.instance.notify(byteArray)
if(response.type == NotifyType.CrcFailure){
    //Handle CRC verification error
    return
}
if(response.type == NotifyType.Failure){
    //Handle error
    return
}
//Start processing response below
...
```

### 5.3 Using SDK to wrap data sent to device:

```kotlin
//Like pairing command, all other commands can be found in StarmaxSend
val data : ByteArray = StarmaxSend().pair()
```

## 6. Commands and Return Parameters

The receive section is JSON for demonstration. Actually it's a combination of HashMap and ArrayList. Status codes refer to section 7.

### 6.1 Pairing Command:

**Request:**

```kotlin
StarmaxSend().pair()
```

**Response:**

- Type: `NotifyType.Pair`
- Object:

```json
{
    "status": 0,
    "pair_status": 0
}
```

**Field Description:**

| Field       | Type | Description  | Example               |
| ----------- | ---- | ------------ | --------------------- |
| status      | Int  | Status code  |                       |
| pair_status | Int  | Pairing code | 1: Confirm, 0: Cancel |

### 6.2 Device Status (Bidirectional):

#### 6.2.1 Get Device Status:

**Request:**

```kotlin
StarmaxSend().getState()
```

**Response:**

- Type: `NotifyType.GetState`
- Object:

```json
{
    "status": 0,
    "time_format": 0,
    "unit_format": 0,
    "temp_format": 0,
    "language": 0,
    "backlighting": 0,
    "screen": 0,
    "wrist_up": 0
}
```

#### 6.2.2 Set Device Status:

**Request:**

```kotlin
StarmaxSend().setState( 
    timeFormat: Int, 
    unitFormat: Int, 
    tempFormat: Int, 
    language: Int, 
    backlighting: Int, 
    screen: Int, 
    wristUp: Boolean
)
```

**Response:**

- Type: `NotifyType.SetState`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field        | Type    | Description                    | Example                                                      |
| ------------ | ------- | ------------------------------ | ------------------------------------------------------------ |
| status       | Int     | Status code                    |                                                              |
| time_format  | Int     | Time format                    | 0: 24-hour, 1: 12-hour                                       |
| unit_format  | Int     | Metric/Imperial                | 0: Metric, 1: Imperial                                       |
| temp_format  | Int     | Temperature format             | 0: Celsius, 1: Fahrenheit                                    |
| language     | Int     | Language                       | 0: Simplified Chinese, 1: Traditional Chinese, 2: English, 3: Russian, 4: French, 5: Spanish, 6: German, 7: Japanese, 8: Italian, 9: Korean, 10: Dutch, 11: Thai |
| backlighting | Int     | Backlight duration (seconds)   |                                                              |
| screen       | Int     | Screen brightness (percentage) |                                                              |
| wrist_up     | Boolean | Raise to wake switch           |                                                              |

### 6.3 Find Device (Bidirectional):

#### 6.3.1 Find Device:

**Request:**

```kotlin
StarmaxSend().findPhone(isFind: Boolean)
```

**No response**

#### 6.3.2 Find Phone:

**No request**

**Response:**

- Type: `NotifyType.FindPhone`
- Object:

```json
{
    "is_find": true
}
```

**Field Description:**

| Field  | Type    | Description | Example                         |
| ------ | ------- | ----------- | ------------------------------- |
| isFind | Boolean | Find method | true: Find, false: Stop finding |

### 6.4 Camera Control (Bidirectional):

#### 6.4.1 Phone Controls Device:

**Request:**

```kotlin
StarmaxSend().cameraControl(cameraControlType: CameraControlType)
```

**No response**

#### 6.4.2 Device Controls Phone:

**No request**

**Response:**

- Type: `NotifyType.CameraControl`
- Object:

```json
{
    "type": "CameraIn"
}
```

**Field Description:**

| Field             | Type              | Description    | Example                                                      |
| ----------------- | ----------------- | -------------- | ------------------------------------------------------------ |
| cameraControlType | CameraControlType | Control method | CameraIn: Enter camera interface, CameraExit: Exit camera interface, TakePhoto: Shake to take photo |

### 6.5 Call Control (Bidirectional):

#### 6.5.1 Phone Controls Device:

**Request:**

```kotlin
StarmaxSend().phoneControl(callControlType: CallControlType, number: String, isNumber: Boolean)
```

**No response**

#### 6.5.2 Device Controls Phone:

**No request**

**Response:**

- Type: `NotifyType.PhoneControl`
- Object:

```json
{
    "type": "HangUp",
    "value": "13700000000"
}
```

**Field Description:**

| Field           | Type                      | Description          | Example                                                      |
| --------------- | ------------------------- | -------------------- | ------------------------------------------------------------ |
| callControlType | CallControlType or String | Control method       | HangUp: Hang up, Answer: Answer, Incoming: Incoming call, Exit: Outgoing call |
| number          | String                    | Name or phone number |                                                              |
| isNumber        | Boolean                   | Is it a phone number |                                                              |
| value           | String                    | Same as number       |                                                              |

### 6.6 Get Battery Level Command:

**Request:**

```kotlin
StarmaxSend().getPower()
```

**Response:**

- Type: `NotifyType.Power`
- Object:

```json
{
    "status": 0,
    "power": 75,
    "is_charge": false
}
```

**Field Description:**

| Field     | Type    | Description   | Example |
| --------- | ------- | ------------- | ------- |
| status    | Int     | Status code   |         |
| power     | Int     | Battery level |         |
| is_charge | Boolean | Is charging   |         |

### 6.7 Get Version Information:

**Request:**

```kotlin
StarmaxSend().getVersion()
```

**Response:**

- Type: `NotifyType.Version`
- Object:

```json
{
    "status": 0,
    "version": "v1.0.1",
    "ui_version": "v1.0.1",
    "buffer_size": "4000",
    "lcd_width": "240",
    "lcd_height": "280",
    "screen_type": 1,
    "model": "X01G001",
    "ui_force_update": false,
    "ui_support_differential_upgrade": false,
    "support_sugar": false,
    "protocol_version": "v1.0.1"
}
```

**Field Description:**

| Field                           | Type    | Description                     | Example                           |
| ------------------------------- | ------- | ------------------------------- | --------------------------------- |
| status                          | Int     | Status code                     |                                   |
| version                         | String  | Firmware version                |                                   |
| ui_version                      | String  | UI version                      |                                   |
| buffer_size                     | String  | Device buffer size              |                                   |
| screen_type                     | Int     | Screen type                     | 0: Round screen, 1: Square screen |
| model                           | String  | Device batch number             |                                   |
| ui_force_update                 | Boolean | UI forced update                |                                   |
| ui_support_differential_upgrade | Boolean | UI differential upgrade support |                                   |
| support_sugar                   | Boolean | Blood sugar support             |                                   |
| protocol_version                | String  | Protocol version                |                                   |

### 6.8 Set Time and Timezone:

**Request:**

```kotlin
StarmaxSend().setTime()
```

**Response:**

- Type: `NotifyType.SetTime`
- Object:

```json
{
    "status": 0
}
```

### 6.9 Set User Information:

**Request:**

```kotlin
StarmaxSend().setUserInfo(sex: Int, age: Int, height: Int, weight: Int)
```

**Response:**

- Type: `NotifyType.SetUserInfo`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field  | Type | Description     | Example            |
| ------ | ---- | --------------- | ------------------ |
| status | Int  | Status code     |                    |
| sex    | Int  | Gender          | 0: Female, 1: Male |
| age    | Int  | Age             |                    |
| height | Int  | Height (CM)     |                    |
| weight | Int  | Weight (0.1 KG) |                    |

### 6.10 Daily Exercise Goals (Bidirectional):

#### 6.10.1 Get Daily Exercise Goals:

**Request:**

```kotlin
StarmaxSend().getGoals()
```

**Response:**

- Type: `NotifyType.GetGoals`
- Object:

```json
{
    "status": 0,
    "steps": 100,
    "heat": 100,
    "distance": 100
}
```

#### 6.10.2 Set Daily Exercise Goals:

**Request:**

```kotlin
StarmaxSend().setGoals( 
    steps: Int, 
    heat: Int, 
    distance: Int
)
```

**Response:**

- Type: `NotifyType.SetGoals`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field    | Type | Description         | Example |
| -------- | ---- | ------------------- | ------- |
| status   | Int  | Status code         |         |
| steps    | Int  | Step goal           |         |
| heat     | Int  | Calorie goal (kcal) |         |
| distance | Int  | Distance goal (km)  |         |

### 6.11 Get Current Device Health Data:

**Request:**

```kotlin
StarmaxSend().getHealthDetail()
```

**Response:**

- Type: `NotifyType.HealthDetail`
- Object:

```json
{
    "status": 0,
    "total_steps": 100,
    "total_heat": 100,
    "total_distance": 100,
    "total_sleep": 8122,
    "total_deep_sleep": 7000,
    "total_light_sleep": 1122,
    "current_heart_rate": 80,
    "current_fz": 100,
    "current_ss": 80,
    "current_blood_oxygen": 100,
    "current_pressure": 30,
    "current_met": 3,
    "current_mai": 76,
    "current_temp": 30,
    "current_blood_sugar": 56,
    "is_wear": 1
}
```

**Field Description:**

| Field                | Type | Description             | Example                                       |
| -------------------- | ---- | ----------------------- | --------------------------------------------- |
| status               | Int  | Status code             |                                               |
| total_steps          | Int  | Total steps             |                                               |
| total_heat           | Int  | Total calories (kcal)   |                                               |
| total_distance       | Int  | Total distance (km)     |                                               |
| total_sleep          | Int  | Total sleep (minutes)   |                                               |
| total_deep_sleep     | Int  | Deep sleep (minutes)    |                                               |
| total_light_sleep    | Int  | Light sleep (minutes)   |                                               |
| current_heart_rate   | Int  | Heart rate (bpm)        |                                               |
| current_fz           | Int  | Diastolic pressure      |                                               |
| current_ss           | Int  | Systolic pressure       |                                               |
| current_blood_oxygen | Int  | Blood oxygen saturation |                                               |
| current_pressure     | Int  | Pressure                |                                               |
| current_met          | Int  | MET                     |                                               |
| current_mai          | Int  | MAI                     |                                               |
| current_temp         | Int  | Temperature (0.1°C)     |                                               |
| current_blood_sugar  | Int  | Blood sugar (0.1)       |                                               |
| is_wear              | Int  | Wearing status          | 1: Wearing, 0: Not wearing, (-1/255): Invalid |

### 6.12 Health Data Detection Switch (Bidirectional):

#### 6.12.1 Get Health Data Detection Switch:

**Request:**

```kotlin
StarmaxSend().getHealthOpen()
```

**Response:**

- Type: `NotifyType.GetHealthOpen`
- Object:

```json
{
    "status": 0,
    "heart_rate": true,
    "blood_pressure": true,
    "blood_oxygen": true,
    "pressure": true,
    "temp": true,
    "blood_sugar": true
}
```

#### 6.12.2 Set Health Data Detection Switch:

**Request:**

```kotlin
StarmaxSend().setHealthOpen( 
    heartRate: Boolean, 
    bloodPressure: Boolean, 
    bloodOxygen: Boolean,
    pressure: Boolean,
    temp: Boolean,
    bloodSugar: Boolean
)
```

**Response:**

- Type: `NotifyType.SetHealthOpen`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field          | Type    | Description           | Example |
| -------------- | ------- | --------------------- | ------- |
| status         | Int     | Status code           |         |
| heart_rate     | Boolean | Heart rate switch     |         |
| blood_pressure | Boolean | Blood pressure switch |         |
| blood_oxygen   | Boolean | Blood oxygen switch   |         |
| pressure       | Boolean | Pressure switch       |         |
| temp           | Boolean | Temperature switch    |         |
| blood_sugar    | Boolean | Blood sugar switch    |         |

### 6.13 Factory Reset:

**Request:**

```kotlin
StarmaxSend().reset()
```

**Response:**

- Type: `NotifyType.Reset`
- Object:

```json
{
    "status": 0
}
```

### 6.14 Heart Rate Detection Interval and Range (Bidirectional):

#### 6.14.1 Get Heart Rate Detection Interval and Range:

**Request:**

```kotlin
StarmaxSend().getHeartRateControl()
```

**Response:**

- Type: `NotifyType.GetHeartRate`
- Object:

```json
{
    "status": 0,
    "start_hour": 0,
    "start_minute": 0,
    "end_hour": 0,
    "end_minute": 0,
    "period": 0,
    "alarm_threshold": 0
}
```

#### 6.14.2 Set Heart Rate Detection Interval and Range:

**Request:**

```kotlin
StarmaxSend().setHeartRateControl( 
    startHour: Int, 
    startMinute: Int, 
    endHour: Int, 
    endMinute: Int, 
    period: Int, 
    alarmThreshold: Int
)
```

**Response:**

- Type: `NotifyType.SetHeartRate`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field          | Type | Description                  | Example |
| -------------- | ---- | ---------------------------- | ------- |
| status         | Int  | Status code                  |         |
| start_hour     | Int  | Start hour                   |         |
| start_minute   | Int  | Start minute                 |         |
| end_hour       | Int  | End hour                     |         |
| end_minute     | Int  | End minute                   |         |
| period         | Int  | Period (minutes)             |         |
| alarmThreshold | Int  | Alarm threshold (percentage) |         |

### 6.15 Frequent Contacts (Bidirectional):

**Note: Device supports max 20 frequent contacts**

#### 6.15.1 Get Frequent Contacts:

**Request:**

```kotlin
StarmaxSend().getContacts()
```

**Response:**

- Type: `NotifyType.GetContact`
- Object:

```json
{
    "status": 0,
    "contacts": [
        {
            "name": "Zhang San",
            "phone": "123123123122"
        },
        {
            "name": "Li Si",
            "phone": "123123123422"
        }
    ]
}
```

#### 6.15.2 Set Frequent Contacts:

**Request:**

```kotlin
StarmaxSend().setContacts(contacts: List<Contact>)
```

**Response:**

- Type: `NotifyType.SetContact`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field         | Type   | Description   | Example |
| ------------- | ------ | ------------- | ------- |
| status        | Int    | Status code   |         |
| contacts      | List   | Contact array |         |
| contact.name  | String | Name          |         |
| contact.phone | String | Phone number  |         |

### 6.16 Emergency Contacts (Bidirectional):

**Note: Device supports max 3 emergency contacts**

#### 6.16.1 Get Emergency Contacts:

**Request:**

```kotlin
StarmaxSend().getSos()
```

**Response:**

- Type: `NotifyType.GetSos`
- Object:

```json
{
    "status": 0,
    "contacts": [
        {
            "name": "Zhang San",
            "phone": "123123123122"
        },
        {
            "name": "Li Si",
            "phone": "123123123422"
        }
    ]
}
```

#### 6.16.2 Set Emergency Contacts:

**Request:**

```kotlin
StarmaxSend().setSos(contacts: List<Contact>)
```

**Response:**

- Type: `NotifyType.SetSos`
- Object:

```json
{
    "status": 0
}
```

### 6.17 Do Not Disturb Mode (Bidirectional):

#### 6.17.1 Get Do Not Disturb Mode:

**Request:**

```kotlin
StarmaxSend().getNotDisturb()
```

**Response:**

- Type: `NotifyType.GetNotDisturb`
- Object:

```json
{
    "status": 0,
    "all_day_on_off": false,
    "on_off": false,
    "start_hour": 0,
    "start_minute": 0,
    "end_hour": 23,
    "end_minute": 59
}
```

#### 6.17.2 Set Do Not Disturb Mode:

**Request:**

```kotlin
StarmaxSend().setNotDisturb(
    onOff: Boolean,
    allDayOnOff: Boolean,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int
)
```

**Response:**

- Type: `NotifyType.SetNotDisturb`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field          | Type    | Description              | Example |
| -------------- | ------- | ------------------------ | ------- |
| status         | Int     | Status code              |         |
| all_day_on_off | Boolean | All day do not disturb   |         |
| on_off         | Boolean | Scheduled do not disturb |         |
| start_hour     | Int     | Start hour               |         |
| start_minute   | Int     | Start minute             |         |
| end_hour       | Int     | End hour                 |         |
| end_minute     | Int     | End minute               |         |

### 6.18 Alarm (Bidirectional):

#### 6.18.1 Get Alarm:

**Request:**

```kotlin
StarmaxSend().getClock()
```

**Response:**

- Type: `NotifyType.GetClock`
- Object:

```json
{
    "status": 0,
    "clock_list": [
        {
            "hour": 0,
            "minute": 0,
            "onOff": true,
            "repeats": [true, true, true, true, true, true, true],
            "type": 0
        }
    ]
}
```

#### 6.18.2 Set Alarm:

**Request:**

```kotlin
StarmaxSend().setClock(clocks: List<Clock>)
```

**Response:**

- Type: `NotifyType.SetClock`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field         | Type    | Description                                | Example |
| ------------- | ------- | ------------------------------------------ | ------- |
| status        | Int     | Status code                                |         |
| clock_list    | List    | Alarm list                                 |         |
| clock.hour    | Int     | Hour                                       |         |
| clock.minute  | Int     | Minute                                     |         |
| clock.onOff   | Boolean | Alarm switch                               |         |
| clock.repeats | List    | Weekly repeat switch (Sunday is first day) |         |
| clock.type    | Int     | Type (no meaning)                          |         |

### 6.19 Sedentary Reminder (Bidirectional):

#### 6.19.1 Get Sedentary Reminder:

**Request:**

```kotlin
StarmaxSend().getLongSit()
```

**Response:**

- Type: `NotifyType.GetLongSit`
- Object:

```json
{
    "status": 0,
    "on_off": false,
    "start_hour": 0,
    "start_minute": 0,
    "end_hour": 23,
    "end_minute": 59,
    "interval": 60
}
```

#### 6.19.2 Set Sedentary Reminder:

**Request:**

```kotlin
StarmaxSend().setLongSit(
    onOff: Boolean,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    interval: Int
)
```

**Response:**

- Type: `NotifyType.SetLongSit`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field        | Type    | Description                 | Example |
| ------------ | ------- | --------------------------- | ------- |
| status       | Int     | Status code                 |         |
| on_off       | Boolean | Reminder switch             |         |
| start_hour   | Int     | Start hour                  |         |
| start_minute | Int     | Start minute                |         |
| end_hour     | Int     | End hour                    |         |
| end_minute   | Int     | End minute                  |         |
| interval     | Int     | Reminder interval (minutes) |         |

### 6.20 Drink Water Reminder (Bidirectional):

#### 6.20.1 Get Drink Water Reminder:

**Request:**

```kotlin
StarmaxSend().getDrinkWater()
```

**Response:**

- Type: `NotifyType.GetDrinkWater`
- Object:

```json
{
    "status": 0,
    "on_off": false,
    "start_hour": 0,
    "start_minute": 0,
    "end_hour": 23,
    "end_minute": 59,
    "interval": 60
}
```

#### 6.20.2 Set Drink Water Reminder:

**Request:**

```kotlin
StarmaxSend().setDrinkWater(
    onOff: Boolean,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    interval: Int
)
```

**Response:**

- Type: `NotifyType.SetDrinkWater`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field        | Type    | Description                 | Example |
| ------------ | ------- | --------------------------- | ------- |
| status       | Int     | Status code                 |         |
| on_off       | Boolean | Reminder switch             |         |
| start_hour   | Int     | Start hour                  |         |
| start_minute | Int     | Start minute                |         |
| end_hour     | Int     | End hour                    |         |
| end_minute   | Int     | End minute                  |         |
| interval     | Int     | Reminder interval (minutes) |         |

### 6.21 Push Message:

**Request:**

```kotlin
StarmaxSend().sendMessage(messageType: MessageType, title: String, content: String)
```

**Response:**

- Type: `NotifyType.SendMessage`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field       | Type        | Description  | Example                          |
| ----------- | ----------- | ------------ | -------------------------------- |
| status      | Int         | Status code  |                                  |
| messageType | MessageType | Message type | Refer to MessageType enumeration |
| title       | String      | Title        |                                  |
| content     | String      | Content      |                                  |

### 6.22 Push Weather:

**Request:**

```kotlin
StarmaxSend().setWeather(weatherDays: List<WeatherDay>)
```

**Response:**

- Type: `NotifyType.SetWeather`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field                 | Type | Description                                 | Example                                                      |
| --------------------- | ---- | ------------------------------------------- | ------------------------------------------------------------ |
| status                | Int  | Status code                                 |                                                              |
| weatherDays           | List | Weather list (next 4 days, including today) |                                                              |
| weatherDay.temp       | Int  | Current temperature                         |                                                              |
| weatherDay.maxTemp    | Int  | Maximum temperature                         |                                                              |
| weatherDay.minTemp    | Int  | Minimum temperature                         |                                                              |
| weatherDay.windSpeed  | Int  | Wind speed                                  |                                                              |
| weatherDay.dampness   | Int  | Humidity                                    |                                                              |
| weatherDay.seeing     | Int  | Visibility                                  |                                                              |
| weatherDay.airQuality | Int  | Air quality                                 | 1: Excellent, 2: Good, 3: Poor                               |
| weatherDay.type       | Int  | Weather type                                | 1: Light rain, 2: Moderate rain, 3: Heavy rain, 4: Cloudy, 5: Partly cloudy, 6: Sunny, 7: Haze, 8: Typhoon, 9: Thunderstorm, 10: Hail, 11: Light snow, 12: Moderate snow, 13: Heavy snow, 14: Sleet, 15: Sandstorm, 16: Snow and hail, 17: Strong wind, 18: High wind, 19: Light wind, 20: Tornado, 21: Tropical storm, 22: Thunderstorm, 23: Severe thunderstorm, 24: Unknown |

### 6.23 Music Control (Bidirectional):

#### 6.23.1 App Controls Device:

**Request:**

```kotlin
StarmaxSend().musicControl(playState: Int, volPercent: Int, ratePercent: Int, musicTitle: String, lyric: String)
```

**No response**

#### 6.23.2 Device Controls App:

**No request**

**Response:**

- Type: `NotifyType.MusicControl`
- Object:

```json
{
    "type": "Play"
}
```

**Field Description:**

| Field       | Type             | Description       | Example                                                      |
| ----------- | ---------------- | ----------------- | ------------------------------------------------------------ |
| playState   | Int              | Player status     | 0: Pause, 1: Play                                            |
| volPercent  | Int              | Volume percentage |                                                              |
| ratePercent | Int              | Song progress     |                                                              |
| musicTitle  | String           | Song name         |                                                              |
| lyric       | String           | Lyrics            |                                                              |
| type        | MusicControlType | Control command   | Play: Play, Stop: Stop playing, Continue: Continue playing, Previous: Previous song, Next: Next song, AddVol: Volume+, SubVol: Volume- |

### 6.24 Event Reminder (Bidirectional):

#### 6.24.1 Get Event Reminder:

**Request:**

```kotlin
StarmaxSend().getEventReminder()
```

**Response:**

- Type: `NotifyType.GetEventReminder`
- Object:

```json
{
    "status": 0,
    "event_reminders": [
        {
            "year": 2023,
            "month": 2,
            "day": 6,
            "hour": 17,
            "minute": 26,
            "content": "Go out with friends",
            "remind_type": 1,
            "repeat_type": 1,
            "repeats": [true, false, false, false, false, false, true]
        }
    ]
}
```

#### 6.24.2 Set Event Reminder:

**Request:**

```kotlin
StarmaxSend().setEventReminder(reminders: List<EventReminder>)
```

**Response:**

- Type: `NotifyType.SetEventReminder`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field                      | Type   | Description                        | Example                                             |
| -------------------------- | ------ | ---------------------------------- | --------------------------------------------------- |
| status                     | Int    | Status code                        |                                                     |
| event_reminders            | List   | Event list                         |                                                     |
| event_reminder.year        | Int    | Year                               |                                                     |
| event_reminder.month       | Int    | Month                              |                                                     |
| event_reminder.day         | Int    | Day                                |                                                     |
| event_reminder.hour        | Int    | Hour                               |                                                     |
| event_reminder.minute      | Int    | Minute                             |                                                     |
| event_reminder.content     | String | Content                            |                                                     |
| event_reminder.remind_type | Int    | Reminder type                      | 1, 2, 3, 4                                          |
| event_reminder.repeat_type | Int    | Repeat type                        | 1: Once, 2: Daily, 3: Weekly, 4: Monthly, 5: Yearly |
| event_reminder.repeats     | List   | Weekly repeat, Monday is first day |                                                     |

## 6.25 Sync Exercise Data:

**Note: Get one record at a time, multiple records can be obtained by calling this interface multiple times**

**Request:**

```kotlin
StarmaxSend().getSportHistory()
```

**Response:**

- Type: `NotifyType.SportHistory`
- Object:

```json
{
    "status": 0,
    "sport_length": 1,
    "current_sport_id": 1,
    "current_sport_data_length": 1000,
    "year": 2023,
    "month": 2,
    "day": 6,
    "hour": 19,
    "minute": 16,
    "second": 30,
    "type": 1,
    "steps": 1000,
    "distance": 1000,
    "speed": 1,
    "calorie": 1000,
    "pace_time": 300,
    "step_frequency": 10,
    "heart_rate_length": 3,
    "heart_rate_list": [100, 90, 80]
}
```

**Field Description:**

| Field                     | Type | Description                                | Example |
| ------------------------- | ---- | ------------------------------------------ | ------- |
| status                    | Int  | Status code                                |         |
| sport_length              | Int  | Total exercise count                       |         |
| current_sport_id          | Int  | Current exercise ID                        |         |
| current_sport_data_length | Int  | Current exercise data length               |         |
| year                      | Int  | Year                                       |         |
| month                     | Int  | Month                                      |         |
| day                       | Int  | Day                                        |         |
| hour                      | Int  | Hour                                       |         |
| minute                    | Int  | Minute                                     |         |
| second                    | Int  | Second                                     |         |
| type                      | Int  | Exercise type: Refer to 100 exercise types |         |
| steps                     | Int  | Total steps                                |         |
| distance                  | Int  | Total distance (m)                         |         |
| speed                     | Int  | Speed (m/s)                                |         |
| calorie                   | Int  | Calories (cal)                             |         |
| pace_time                 | Int  | Pace                                       |         |
| step_frequency            | Int  | Step frequency                             |         |
| heart_rate_length         | Int  | Heart rate data length                     |         |
| heart_rate_list           | List | Heart rate data array                      |         |

## 6.26 Sync Step and Sleep Data:

**Request:**

```kotlin
StarmaxSend().getStepHistory(calendar: Calendar)
```

**Response:**

- Type: `NotifyType.StepHistory`
- Object:

```json
{
    "status": 0,
    "interval": 1,
    "year": 2023,
    "month": 2,
    "day": 7,
    "data_length": 2000,
    "step_list": [
        {
            "hour": 16,
            "minute": 8,
            "data_type": 1,
            "steps": 1000,
            "calorie": 1000,
            "distance": 1000
        }
    ],
    "sleep_list": [
        {
            "hour": 16,
            "minute": 8,
            "data_type": 2,
            "sleep_status": 1
        }
    ]
}
```

**Field Description:**

| Field                     | Type | Description       | Example                                                      |
| ------------------------- | ---- | ----------------- | ------------------------------------------------------------ |
| status                    | Int  | Status code       |                                                              |
| interval                  | Int  | Sampling interval |                                                              |
| year                      | Int  | Year              |                                                              |
| month                     | Int  | Month             |                                                              |
| day                       | Int  | Day               |                                                              |
| step.hour, sleep.hour     | Int  | Hour              |                                                              |
| step.minute, sleep.minute | Int  | Minute            |                                                              |
| step_list                 | List | Step list         |                                                              |
| sleep_list                | List | Sleep list        |                                                              |
| step.steps                | Int  | Steps             |                                                              |
| step.distance             | Int  | Distance (meters) |                                                              |
| step.calorie              | Int  | Calories (cal)    |                                                              |
| sleep.sleep_status        | Int  | Sleep status      | 1: Start sleep, 2: Light sleep, 3: Deep sleep, 4: Awake, 5: REM, 129: Start nap, 130: Light sleep (nap), 131: Deep sleep (nap), 132: Awake (nap), 133: REM (nap) |

## 6.27 Sync Heart Rate History:

**Request:**

```kotlin
StarmaxSend().getHeartRateHistory(calendar: Calendar)
```

**Response:**

- Type: `NotifyType.HeartRateHistory`
- Object:

```json
{
    "status": 0,
    "interval": 1,
    "year": 2023,
    "month": 2,
    "day": 7,
    "data_length": 2000,
    "heart_rate_list": [
        {
            "hour": 16,
            "minute": 8,
            "heart_rate": 100
        }
    ]
}
```

**Field Description:**

| Field                 | Type | Description       | Example |
| --------------------- | ---- | ----------------- | ------- |
| status                | Int  | Status code       |         |
| interval              | Int  | Sampling interval |         |
| year                  | Int  | Year              |         |
| month                 | Int  | Month             |         |
| day                   | Int  | Day               |         |
| data_length           | Int  | Total data length |         |
| heart_rate_list       | List | Heart rate array  |         |
| heart_rate.hour       | Int  | Hour              |         |
| heart_rate.minute     | Int  | Minute            |         |
| heart_rate.heart_rate | Int  | Heart rate        |         |

## 6.28 Sync Blood Pressure Data:

**Request:**

```kotlin
StarmaxSend().getBloodPressureHistory(calendar: Calendar)
```

**Response:**

- Type: `NotifyType.BloodPressureHistory`
- Object:

```json
{
    "status": 0,
    "interval": 1,
    "year": 2023,
    "month": 2,
    "day": 7,
    "data_length": 2000,
    "blood_pressure_list": [
        {
            "hour": 16,
            "minute": 8,
            "ss": 100,
            "fz": 80
        }
    ]
}
```

**Field Description:**

| Field                 | Type | Description          | Example |
| --------------------- | ---- | -------------------- | ------- |
| status                | Int  | Status code          |         |
| interval              | Int  | Sampling interval    |         |
| year                  | Int  | Year                 |         |
| month                 | Int  | Month                |         |
| day                   | Int  | Day                  |         |
| data_length           | Int  | Total data length    |         |
| blood_pressure_list   | List | Blood pressure array |         |
| blood_pressure.hour   | Int  | Hour                 |         |
| blood_pressure.minute | Int  | Minute               |         |
| blood_pressure.ss     | Int  | Systolic pressure    |         |
| blood_pressure.fz     | Int  | Diastolic pressure   |         |

## 6.29 Sync Blood Oxygen Data:

**Request:**

```kotlin
StarmaxSend().getBloodOxygenHistory(calendar: Calendar)
```

**Response:**

- Type: `NotifyType.BloodOxygenHistory`
- Object:

```json
{
    "status": 0,
    "interval": 1,
    "year": 2023,
    "month": 2,
    "day": 7,
    "data_length": 2000,
    "blood_oxygen_list": [
        {
            "hour": 16,
            "minute": 8,
            "blood_oxygen": 100
        }
    ]
}
```

**Field Description:**

| Field                     | Type | Description        | Example |
| ------------------------- | ---- | ------------------ | ------- |
| status                    | Int  | Status code        |         |
| interval                  | Int  | Sampling interval  |         |
| year                      | Int  | Year               |         |
| month                     | Int  | Month              |         |
| day                       | Int  | Day                |         |
| data_length               | Int  | Total data length  |         |
| blood_oxygen_list         | List | Blood oxygen array |         |
| blood_oxygen.hour         | Int  | Hour               |         |
| blood_oxygen.minute       | Int  | Minute             |         |
| blood_oxygen.blood_oxygen | Int  | Blood oxygen       |         |

## 6.30 Sync Pressure Data:

**Request:**

```kotlin
StarmaxSend().getPressureHistory(calendar: Calendar)
```

**Response:**

- Type: `NotifyType.PressureHistory`
- Object:

```json
{
    "status": 0,
    "interval": 1,
    "year": 2023,
    "month": 2,
    "day": 7,
    "data_length": 2000,
    "pressure_list": [
        {
            "hour": 16,
            "minute": 8,
            "pressure": 60
        }
    ]
}
```

**Field Description:**

| Field             | Type | Description       | Example |
| ----------------- | ---- | ----------------- | ------- |
| status            | Int  | Status code       |         |
| interval          | Int  | Sampling interval |         |
| year              | Int  | Year              |         |
| month             | Int  | Month             |         |
| day               | Int  | Day               |         |
| data_length       | Int  | Total data length |         |
| pressure_list     | List | Pressure array    |         |
| pressure.hour     | Int  | Hour              |         |
| pressure.minute   | Int  | Minute            |         |
| pressure.pressure | Int  | Pressure          |         |

## 6.31 Sync MET Data:

**Request:**

```kotlin
StarmaxSend().getMetHistory(calendar: Calendar)
```

**Response:**

- Type: `NotifyType.MetHistory`
- Object:

```json
{
    "status": 0,
    "interval": 1,
    "year": 2023,
    "month": 2,
    "day": 7,
    "data_length": 2000,
    "met_list": [
        {
            "met": 3
        }
    ]
}
```

**Field Description:**

| Field       | Type | Description       | Example |
| ----------- | ---- | ----------------- | ------- |
| status      | Int  | Status code       |         |
| interval    | Int  | Sampling interval |         |
| year        | Int  | Year              |         |
| month       | Int  | Month             |         |
| day         | Int  | Day               |         |
| data_length | Int  | Total data length |         |
| met_list    | List | MET array         |         |
| met.met     | Int  | MET               |         |

## 6.32 Sync Temperature Data:

**Request:**

```kotlin
StarmaxSend().getTempHistory(calendar: Calendar)
```

**Response:**

- Type: `NotifyType.TempHistory`
- Object:

```json
{
    "status": 0,
    "interval": 1,
    "year": 2023,
    "month": 2,
    "day": 7,
    "data_length": 2000,
    "temp_list": [
        {
            "hour": 16,
            "minute": 8,
            "temp": 365
        }
    ]
}
```

**Field Description:**

| Field       | Type | Description       | Example |
| ----------- | ---- | ----------------- | ------- |
| status      | Int  | Status code       |         |
| interval    | Int  | Sampling interval |         |
| year        | Int  | Year              |         |
| month       | Int  | Month             |         |
| day         | Int  | Day               |         |
| data_length | Int  | Total data length |         |
| temp_list   | List | Temperature array |         |
| temp.hour   | Int  | Hour              |         |
| temp.minute | Int  | Minute            |         |
| temp.temp   | Int  | Celsius           |         |

## 6.33 Sync MAI Data:

**Request:**

```kotlin
StarmaxSend().getMaiHistory(calendar: Calendar)
```

**Response:**

- Type: `NotifyType.Mai`
- Object:

```json
{
    "status": 0,
    "interval": 1,
    "year": 2023,
    "month": 2,
    "day": 7,
    "data_length": 2000,
    "mai_list": [
        {
            "mai": 50
        }
    ]
}
```

**Field Description:**

| Field       | Type | Description       | Example |
| ----------- | ---- | ----------------- | ------- |
| status      | Int  | Status code       |         |
| interval    | Int  | Sampling interval |         |
| year        | Int  | Year              |         |
| month       | Int  | Month             |         |
| day         | Int  | Day               |         |
| data_length | Int  | Total data length |         |
| mai_list    | List | MAI array         |         |
| mai.mai     | Int  | MAI               |         |

## 6.34 Sync Valid History Data Dates:

**Request:**

```kotlin
StarmaxSend().getValidHistoryDates(historyType: HistoryType)
```

**Response:**

- Type: `NotifyType.ValidHistoryDates`
- Object:

```json
{
    "status": 0,
    "valid_history_dates": [
        {
            "year": 2023,
            "month": 2,
            "day": 7
        }
    ]
}
```

## 6.35 Sync Blood Sugar Data:

**Request:**

```kotlin
StarmaxSend().getBloodSugarHistory(calendar: Calendar)
```

**Response:**

- Type: `NotifyType.BloodSugarHistory`
- Object:

```json
{
    "status": 0,
    "interval": 1,
    "year": 2023,
    "month": 2,
    "day": 7,
    "data_length": 2000,
    "blood_sugar_list": [
        {
            "hour": 16,
            "minute": 8,
            "blood_sugar": 64
        }
    ]
}
```

**Field Description:**

| Field                   | Type | Description       | Example |
| ----------------------- | ---- | ----------------- | ------- |
| status                  | Int  | Status code       |         |
| interval                | Int  | Sampling interval |         |
| year                    | Int  | Year              |         |
| month                   | Int  | Month             |         |
| day                     | Int  | Day               |         |
| data_length             | Int  | Total data length |         |
| blood_sugar_list        | List | Blood sugar array |         |
| blood_sugar.hour        | Int  | Hour              |         |
| blood_sugar.minute      | Int  | Minute            |         |
| blood_sugar.blood_sugar | Int  | Blood sugar       |         |

## 6.36 Switch Watch Face:

**Request:**

```kotlin
StarmaxSend().switchDial(dialId: Int)
```

**Response:**

- Type: `NotifyType.SwitchDial`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field  | Type | Description   | Example |
| ------ | ---- | ------------- | ------- |
| status | Int  | Status code   |         |
| dialId | Int  | Watch face ID |         |

## 6.37 Sync Watch Face Data:

**Request:**

```kotlin
StarmaxSend().getDialInfo()
```

**Response:**

- Type: `NotifyType.DialInfo`
- Object:

```json
{
    "status": 0,
    "dial_list": [
        {
            "is_selected": 16,
            "dial_id": 5001
        }
    ]
}
```

**Field Description:**

| Field            | Type | Description      | Example                      |
| ---------------- | ---- | ---------------- | ---------------------------- |
| status           | Int  | Status code      |                              |
| dial_list        | List | Watch face array |                              |
| dial.is_selected | Int  | Selection status | 1: Selected, 0: Not selected |
| dial.dial_id     | Int  | Watch face ID    |                              |

## 6.38 Push Weather (7 Days) - GTS7 Compatible:

**Request:**

```kotlin
StarmaxSend().setWeatherSeven(cityName: String, weatherDays: List<WeatherDay>)
```

**Response:**

- Type: `NotifyType.SetWeather`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field                     | Type   | Description                                 | Example                                                      |
| ------------------------- | ------ | ------------------------------------------- | ------------------------------------------------------------ |
| status                    | Int    | Status code                                 |                                                              |
| cityName                  | String | City name                                   |                                                              |
| weatherDays               | List   | Weather list (next 4 days, including today) |                                                              |
| weatherDay.temp           | Int    | Current temperature                         |                                                              |
| weatherDay.maxTemp        | Int    | Maximum temperature                         |                                                              |
| weatherDay.minTemp        | Int    | Minimum temperature                         |                                                              |
| weatherDay.windSpeed      | Int    | Wind speed                                  |                                                              |
| weatherDay.dampness       | Int    | Humidity                                    |                                                              |
| weatherDay.seeing         | Int    | Visibility                                  |                                                              |
| weatherDay.airQuality     | Int    | Air quality                                 | 1: Excellent, 2: Good, 3: Poor                               |
| weatherDay.type           | Int    | Weather type                                | 1: Light rain, 2: Moderate rain, 3: Heavy rain, 4: Cloudy, 5: Partly cloudy, 6: Sunny, 7: Haze, 8: Typhoon, 9: Thunderstorm, 10: Hail, 11: Light snow, 12: Moderate snow, 13: Heavy snow, 14: Sleet, 15: Sandstorm, 16: Snow and hail, 17: Strong wind, 18: High wind, 19: Light wind, 20: Tornado, 21: Tropical storm, 22: Thunderstorm, 23: Severe thunderstorm, 24: Unknown |
| weatherDay.sunriseHour    | Int    | Sunrise hour                                |                                                              |
| weatherDay.sunriseMinute  | Int    | Sunrise minute                              |                                                              |
| weatherDay.sunsetHour     | Int    | Sunset hour                                 |                                                              |
| weatherDay.sunsetMinute   | Int    | Sunset minute                               |                                                              |
| weatherDay.moonriseHour   | Int    | Moonrise hour                               |                                                              |
| weatherDay.moonriseMinute | Int    | Moonrise minute                             |                                                              |
| weatherDay.moonsetHour    | Int    | Moonset hour                                |                                                              |
| weatherDay.moonsetMinute  | Int    | Moonset minute                              |                                                              |

## 6.39 App Store (Bidirectional, GTS7):

#### 6.39.1 Get App Store:

**Request:**

```kotlin
StarmaxSend().getApps()
```

**Response:**

- Type: `NotifyType.GetApps`
- Object:

```json
{
    "status": 0,
    "apps": [1, 2, 3]
}
```

#### 6.39.2 Set App Store:

**Request:**

```kotlin
StarmaxSend().setApps(apps: List<Int>)
```

**Response:**

- Type: `NotifyType.SetApps`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field  | Type     | Description | Example                                                      |
| ------ | -------- | ----------- | ------------------------------------------------------------ |
| status | Int      | Status code |                                                              |
| apps   | IntArray | App list    | 1: Breathing training, 2: MET, 3: Voice assistant, 4: Timer, 5: Stopwatch, 6: Timer, 7: Alarm, 8: Flashlight, 9: Find phone, 10: World clock, 11: Pomodoro, 12: Women's health, 13: Blood sugar, 14: Blood pressure |

## 6.40 World Clock (Bidirectional, GTS7):

#### 6.40.1 Get World Clock:

**Request:**

```kotlin
StarmaxSend().getWorldClocks()
```

**Response:**

- Type: `NotifyType.GetWorldClocks`
- Object:

```json
{
    "status": 0,
    "citys": [1, 2, 3]
}
```

#### 6.40.2 Set World Clock:

**Request:**

```kotlin
StarmaxSend().setWorldClocks(apps: List<Int>)
```

**Response:**

- Type: `NotifyType.SetWorldClocks`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field  | Type     | Description | Example                                                      |
| ------ | -------- | ----------- | ------------------------------------------------------------ |
| status | Int      | Status code |                                                              |
| citys  | IntArray | City list   | 1: Beijing (GMT+8), 2: Washington (GMT-5), 3: London (GMT+0), 4: Paris (GMT+1), 5: New York (GMT-5), 6: Tokyo (GMT+9), 7: Shanghai (GMT+8), 8: Mumbai (GMT+5:30), 9: Sydney (GMT+11), 10: Los Angeles (GMT-8), 11: Moscow (GMT+3), 12: Berlin (GMT+1), 13: Rome (GMT+1), 14: Istanbul (GMT+3), 15: Cairo (GMT+2), 16: Nanjing (GMT+8), 17: Vancouver (GMT-8), 18: Chicago (GMT-6), 19: Rio de Janeiro (GMT-3), 20: Amsterdam (GMT+1), 21: Singapore (GMT+8), 22: Seoul (GMT+9), 23: Melbourne (GMT+11), 24: New Delhi (GMT+5:30), 25: Canberra (GMT+11), 26: Brasília (GMT-3), 27: Mexico City (GMT-6), 28: Hong Kong (GMT+8), 29: Stockholm (GMT+1), 30: Barcelona (GMT+1), 31: Munich (GMT+1). Note: Maximum 16 supported |

## 6.41 Set Password and Wrist Lock Switch (Bidirectional, GTS7):

#### 6.41.1 Get Password:

**Request:**

```kotlin
StarmaxSend().getPassword()
```

**Response:**

- Type: `NotifyType.GetPassword`
- Object:

```json
{
    "status": 0,
    "password": "123456",
    "is_open": false
}
```

#### 6.41.2 Set Password:

**Request:**

```kotlin
StarmaxSend().setPassword(password: String, isOpen: Boolean)
```

**Response:**

- Type: `NotifyType.SetPassword`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field    | Type    | Description | Example |
| -------- | ------- | ----------- | ------- |
| status   | Int     | Status code |         |
| password | String  | Password    |         |
| isOpen   | Boolean | Is enabled  |         |

## 6.42 Women's Health (Bidirectional, GTS7):

#### 6.42.1 Get Women's Health:

**Request:**

```kotlin
StarmaxSend().getFemaleHealth()
```

**Response:**

- Type: `NotifyType.GetFemaleHealth`
- Object:

```json
{
    "status": 0,
    "number_of_days": 5,
    "cycle_days": 28,
    "year": 2023,
    "month": 2,
    "day": 6
}
```

#### 6.42.2 Set Women's Health:

**Request:**

```kotlin
StarmaxSend().setFemaleHealth(numberOfDays: Int, cycleDays: Int, lastDate: Calendar)
```

**Response:**

- Type: `NotifyType.SetFemaleHealth`
- Object:

```json
{
    "status": 0
}
```

**Field Description:**

| Field          | Type | Description     | Example |
| -------------- | ---- | --------------- | ------- |
| status         | Int  | Status code     |         |
| number_of_days | Int  | Menstrual days  |         |
| cycle_days     | Int  | Menstrual cycle |         |
| year           | Int  | Last year       |         |
| month          | Int  | Last month      |         |
| day            | Int  | Last day        |         |

## 6.43 Sync Sleep Records:

**Request:**

```kotlin
StarmaxSend().getSleepHistory(calendar: Calendar)
```

**Response:**

- Type: `NotifyType.SleepHistory`
- Object:

```json
{
    "status": 0,
    "interval": 1,
    "year": 2023,
    "month": 2,
    "day": 7,
    "data_length": 2000,
    "sleep_list": [
        {
            "hour": 16,
            "minute": 8,
            "sleep_status": 1
        }
    ]
}
```

**Field Description:**

| Field                   | Type | Description       | Example                                                 |
| ----------------------- | ---- | ----------------- | ------------------------------------------------------- |
| status                  | Int  | Status code       |                                                         |
| interval                | Int  | Sampling interval |                                                         |
| year                    | Int  | Year              |                                                         |
| month                   | Int  | Month             |                                                         |
| day                     | Int  | Day               |                                                         |
| data_length             | Int  | Total data length |                                                         |
| sleep_list              | List | Sleep array       |                                                         |
| sleep_list.hour         | Int  | Hour              |                                                         |
| sleep_list.minute       | Int  | Minute            |                                                         |
| sleep_list.sleep_status | Int  | Sleep status      | 1: Start sleep, 2: Light sleep, 3: Deep sleep, 4: Awake |

## 7. Status Code Description:

| Status Code | Description       |
| ----------- | ----------------- |
| 0           | Command correct   |
| 1           | Command error     |
| 2           | Checksum error    |
| 3           | Data length error |
| 4           | Data invalid      |

## 8. 100 Exercise Modes:

| Type | Description         |
| ---- | ------------------- |
| 0    | Outdoor cycling     |
| 1    | Outdoor running     |
| 2    | Mountain climbing   |
| 3    | Indoor cycling      |
| 4    | Burpees             |
| 5    | Tennis              |
| 6    | Jump rope           |
| 7    | Badminton           |
| 8    | Yoga                |
| 9    | Football            |
| 10   | Squats              |
| 11   | Indoor bike         |
| 12   | Outdoor walking     |
| 13   | High knees exercise |
| 14   | Hiking              |
| 15   | Brisk walking       |
| 16   | Indoor running      |
| 17   | Indoor walking      |
| 18   | Strength training   |
| 19   | Arm training        |
| 20   | Elliptical          |
| 21   | Basketball          |
| 22   | Leg training        |
| 23   | Stepper             |
| 24   | Walker              |
| 25   | Aerobics            |
| 26   | Group exercise      |
| 27   | Pilates             |
| 28   | CrossFit            |
| 29   | Functional training |
| 30   | Physical training   |
| 31   | Taekwondo           |
| 32   | Boxing              |
| 33   | Free sparring       |
| 34   | Karate              |
| 35   | Fencing             |
| 36   | Orienteering        |
| 37   | Core training       |
| 38   | Combat aerobics     |
| 39   | Kendo               |
| 40   | Pull-up bar         |
| 41   | Parallel bars       |
| 42   | Belly dance         |
| 43   | Jazz dance          |
| 44   | Latin dance         |
| 45   | Ballet              |
| 46   | Street dance        |
| 47   | Square dance        |
| 48   | Other dance         |
| 49   | Roller skating      |
| 50   | Martial arts        |
| 51   | Tai Chi             |
| 52   | Hula hoop           |
| 53   | Free exercise       |
| 54   | Racing              |
| 55   | Frisbee             |
| 56   | Darts               |
| 57   | Archery             |
| 58   | Horse riding        |
| 59   | Battle games        |
| 60   | Kite flying         |
| 61   | Tug of war          |
| 62   | Swing               |
| 63   | Stair climbing      |
| 64   | Obstacle race       |
| 65   | Fishing             |
| 66   | Ping pong           |
| 67   | Pool/Billiards      |
| 68   | Bowling             |
| 69   | Volleyball          |
| 70   | Shuttlecock         |
| 71   | Handball            |
| 72   | Baseball            |
| 73   | Softball            |
| 74   | Cricket             |
| 75   | Rowing machine      |
| 76   | Rugby               |
| 77   | Beach volleyball    |
| 78   | Skydiving           |
| 79   | Gate ball           |
| 80   | Field hockey        |
| 81   | Wall ball           |
| 82   | Sepak takraw        |
| 83   | Dodgeball           |
| 84   | Ice skating         |
| 85   | Ice hockey          |
| 86   | Push-ups            |
| 87   | Plank               |
| 88   | Skiing              |
| 89   | Curling             |
| 90   | Bobsled             |
| 91   | Luge                |
| 92   | Sit-ups             |
| 93   | Biathlon            |
| 94   | Skateboard          |
| 95   | Rock climbing       |
| 96   | Bungee jumping      |
| 97   | Parkour             |
| 98   | BMX                 |
| 99   | Jumping jacks       |

## 9. Update Log:

| Date       | Version | Content                                                      |
| ---------- | ------- | ------------------------------------------------------------ |
| 2023/4/14  | 1.0.2   | Added blood sugar, StarmaxNotify changed to abstract class, and inherited MapStarmaxNotify |
| 2023/8/21  | 1.0.3   | Added wearing detection to current health data               |
| 2023/11/22 | 1.0.4   | Added 100 exercise modes upgrade                             |
| 2024/1/29  | 1.0.5   | Added GTS7 weather, app store, world clock, wrist password, women's health, SDK adjusted based on protobuf |
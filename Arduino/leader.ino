#include <MPU9250.h>
#include <PubSubClient.h>
#include <ESP8266WiFi.h>

const char* ssid="yourwifissid";
const char* pass="yourpassword";
const char* mqtt_server="yourmqttserver";

MPU9250 IMU(Wire,0x68);
WiFiClient espClient;
PubSubClient client(espClient);

bool isStart = false;
bool isCalibrateAccel = false;
bool isCalibrateMag = false;

float ax,ay,az,gx,gy,gz,mx,my,mz,t;

long prevMsg = millis();

int freq = 25;
int iData=0;

char acc[120];
char gyr[120];
char mag[120];
char tem[120];

String separator=",";
String sacc="";
String sgyr="";
String smag="";
String stem="";

void setupWifi(){
  delay(10);
  // We start by connecting to a WiFi network
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(ssid);

  WiFi.begin(ssid, pass);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  randomSeed(micros());

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());
}

void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("] ");
  for (int i = 0; i < length; i++) {
    Serial.print((char)payload[i]);
  }     
  if(String(topic)=="PDR/isStart"){
      if ((char)payload[0] == '1') {
        isStart=true;
      } else {
        isStart=false;
      }
  }
  else if(String(topic)=="PDR/isCalibrateAccel"){    
      isCalibrateAccel=true;
  }
  else if(String(topic)=="PDR/isCalibrateMag"){
      isCalibrateMag=true;
  }
}

void reconnect() {
  // Loop until we're reconnected
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    // Create a random client ID
    String clientId = "ESP8266Client-";
    clientId += String(random(0xffff), HEX);
    // Attempt to connect
    if (client.connect(clientId.c_str())) {
      Serial.println("connected");
      // Once connected, publish an announcement...
      client.publish("PDR/espStatus", "Connected");
      client.subscribe("PDR/#");
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 3 seconds");
      delay(3000);
    }
  }
}

void pubData(){
  IMU.readSensor();
  ax = IMU.getAccelX();
  ay = IMU.getAccelY();
  az = IMU.getAccelZ();
  gx = IMU.getGyroX();
  gy = IMU.getGyroY();
  gz = IMU.getGyroZ();
  mx = IMU.getMagX();
  my = IMU.getMagY();
  mz = IMU.getMagZ();
  t  = IMU.getTemp();
  if(iData<5){
    sacc+=String(ax)+separator+String(ay)+separator+String(az)+";";    
    sgyr+=String(gx)+separator+String(gy)+separator+String(gz)+";";    
    smag+=String(mx)+separator+String(my)+separator+String(mz)+";";    
    stem+=String(t)+";";    
    iData++;
    if(iData==5){
      Serial.println(iData);    
      sacc.toCharArray(acc,sacc.length()+1);
      sgyr.toCharArray(gyr,sgyr.length()+1);
      smag.toCharArray(mag,smag.length()+1);
      stem.toCharArray(tem,stem.length()+1);
      client.publish("PDR/Data/Acc",acc);
      client.publish("PDR/Data/Gyr",gyr);
      client.publish("PDR/Data/Mag",mag);
      client.publish("PDR/Data/Tem",tem);
      sacc="";
      sgyr="";
      smag="";
      stem="";
      iData=0;  
    }
  }
}

void setup()
{
  Serial.begin(115200);
  IMU.begin();
  if(IMU.calibrateGyro()){
    setupWifi();
    client.setServer(mqtt_server,1883);
    client.setCallback(callback);    
  }
}

void loop(){
  if(!client.connected()){
    reconnect();
  }
  client.loop();

  if(isStart){
    if(millis()-prevMsg >= 1000/freq){
      pubData();
      prevMsg=millis();
    }  
  }

  if(isCalibrateAccel){
    isStart=false;
    if(IMU.calibrateAccel()){
      isCalibrateAccel=false;
      client.publish("PDR/espStatus","Accel Calibration Successfull");      
    }
  }

  if(isCalibrateMag){\
    isStart=false;
    if(IMU.calibrateMag()){
      isCalibrateMag=false;      
      client.publish("PDR/espStatus","Magneto Calibration Successfull");      
    }
  }
}

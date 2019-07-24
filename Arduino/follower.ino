// 1-5-19
// - Send sLength and Heading

#include "QMC5883L.h"
#include "WiFiEsp.h"
#include "SoftwareSerial.h"
#include <PubSubClient.h>
#include <WiFiEspClient.h>
#include <Wire.h>

#define M_PI 3.14159265358979323846264338327950288

SoftwareSerial WifiSerial(8, 9); // RX, TX
const char* ssid="PDR-Server";
const char* pass="zukbkdskzytxghfl";
const char* mqtt_server="192.168.43.1";
int status = WL_IDLE_STATUS;     // the Wifi radio's status

QMC5883L compass;
WiFiEspClient espClient;
PubSubClient client(espClient);

float mag_min[3] = {-2935,3980,-2467};
float mag_max[3] = {4697,11740,1912};
// Motor Configuration
// Right motor
byte enR = 10;
byte inR1 = 11;
byte inR2 = 12;
byte pwmMinR = 105;
// Left motor
byte enL = 5;
byte inL1 = 6;
byte inL2 = 7;
byte pwmMinL = 135;
bool last = true;

const float dist_wheel = 13.4;
//const float slip = 0.51;

char buff[50];
float xNow = 0;
float yNow = 0;
int headingTarget[25];
int sLengthTarget[25];
int theta_now = 0;
byte iWaypointReceived = 0;
byte iWaypointTarget = 0;
byte process = 1;
float sLengthWalked = 0;

// Speed Sensor Configuration
byte encoderR = 2;
byte encoderL = 3;
float pulsesL; 
float pulsesR; 
bool initTime = true;
long prevTime = millis(); 

// Controller
float getRunningTime(String trFor,float input,float set_point){
  float gain;
  if(trFor=="theta"){
    if(set_point<-90 && input>90){
      set_point+=360;
    }
    else if(set_point>90  && input<-90){
      set_point-=360;
    }
    gain = 4;    
  }
  else{
    gain = 20;      
  }
  float error = set_point-input;
  return gain*error;
}

void callback(char* topic, byte* payload, unsigned int length) {
  if((strcmp(topic,"PDR/Leader/Position")==0)){
    Serial.println(iWaypointReceived);
    for (byte i = 0; i < length; i++) {
      if ((char)payload[i] == ',') {
        char sLengthReceived[i+1];
        char headingReceived[length-i];
        for(byte j =0; j<i;j++){
          sLengthReceived[j] = (char) payload[j];
          Serial.print(sLengthReceived[j]);
        }
        byte k=0;
        for(byte j =i+1; j<length;j++){
          headingReceived[k] = (char) payload[j];
          k++;
        }
        sLengthTarget[iWaypointReceived] = atoi(sLengthReceived);
        headingTarget[iWaypointReceived] = atoi(headingReceived);
        break;
      }
      client.loop(); //IMPORTANT : modify received value if missing
    }
    Serial.println();
    iWaypointReceived++;
    if(iWaypointReceived==25){
      iWaypointReceived=0;
    }
    client.publish("PDR/isReceived", "1");  
  }
  if((strcmp(topic,"PDR/Follower/GetHeading")==0)){
    String theta_now= String(getHeading());
    theta_now.toCharArray(buff, theta_now.length()+1);
    client.publish("PDR/Follower/Heading", buff);
    client.loop(); //IMPORTANT : modify received value if missing
    Serial.println(theta_now);
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
      client.publish("PDR/Status", "Follower Connected");
      client.subscribe("PDR/Leader/Position");
      client.subscribe("PDR/Follower/GetHeading");
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 3 seconds");
      delay(3000);
    }
  }
}

void counterL(){pulsesL++;}
void counterR(){pulsesR++;}

void setupWifi(){
  WifiSerial.begin(9600);
  // initialize ESP module
  WiFi.init(&WifiSerial);
  // check for the presence of the shield
  if (WiFi.status() == WL_NO_SHIELD) {
    Serial.println("WiFi shield not present");
    // don't continue
    while (true);
  }
  // attempt to connect to WiFi network
  while ( status != WL_CONNECTED) {
    Serial.print("Attempting to connect to WPA SSID: ");
    Serial.println(ssid);
    // Connect to WPA/WPA2 network
    status = WiFi.begin(ssid, pass);
  }
  // you're connected now, so print out the data
  Serial.println("You're connected to the network");
  client.setServer(mqtt_server,1883);
  client.setCallback(callback);    
}

void moveMotor(String dir_x, String dir_y){
  if(dir_y=="left"){
    if(dir_x=="forward"){
      digitalWrite(inL1, HIGH);
      digitalWrite(inL2, LOW);      
    }
    else{
      digitalWrite(inL1, LOW);
      digitalWrite(inL2, HIGH);
    }
  }
  else{
    if(dir_x=="forward"){
      digitalWrite(inR1, HIGH);
      digitalWrite(inR2, LOW);      
    }
    else{
      digitalWrite(inR1, LOW);
      digitalWrite(inR2, HIGH);
    }
  }
}

void turnOffMotors(){
  digitalWrite(inR1, LOW);
  digitalWrite(inR2, LOW);
  digitalWrite(inL1, LOW);
  digitalWrite(inL2, LOW);
  analogWrite(enR, 0);
  analogWrite(enL, 0);
}

void rotate(String _direction){
  if(_direction=="CW"){
    moveMotor("forward","left");
    analogWrite(enL, pwmMinL);
    moveMotor("back","right");
    analogWrite(enR, pwmMinR);
  }
  else{
    moveMotor("back","left");
    analogWrite(enL, pwmMinL);
    moveMotor("forward","right");
    analogWrite(enR, pwmMinR);
  }
}

void go(){
  moveMotor("forward","left");
  analogWrite(enL, pwmMinL);
  moveMotor("forward","right");
  analogWrite(enR, pwmMinR);    
}

int getHeading(){
    int16_t _x,_y,_z,_t;
    compass.readRaw(&_x,&_y,&_z,&_t);
    float mag[] = {0,0,0};
    mag[0] = (float)_x;
    mag[1] = (float)_y;
    mag[2] = (float)_z;
    float avg_delta_all=0; 
    float offset[] = {0,0,0};
    float avg_delta[] = {0,0,0};
    for(byte i=0;i<3;i++){
      if(mag_max[i]<mag[i]){
        mag_max[i]=mag[i];
      }
      if(mag_min[i]>mag[i]){
        mag_min[i]=mag[i];
      }
      offset[i]=(mag_max[i]+mag_min[i])/2;
      avg_delta[i]=(mag_max[i]-mag_min[i])/2;
      avg_delta_all+=avg_delta[i];
    }
    avg_delta_all/=3;
    float scale[] = {0,0,0};
    for(byte i=0;i<3;i++){
      scale[i] = avg_delta_all/avg_delta[i];
      mag[i] = (mag[i]-offset[i])*scale[i];
    }
    float offsetLeader = 1.396; //Offset magneto from leader
    if(atan2(-mag[1],mag[0])>=-offsetLeader){
      return (-(M_PI-atan2(-mag[1],mag[0])) + (offsetLeader))*180/M_PI;
    }
    else{
      return ((M_PI+atan2(-mag[1],mag[0])) + (offsetLeader))*180/M_PI;     
    }
}

void setup() {  
  Wire.begin();
  Serial.begin(9600);
  compass.init();
  setupWifi();
  
  // set all the motor control pins to outputs
  pinMode(enR, OUTPUT);
  pinMode(enL, OUTPUT);
  pinMode(inR1, OUTPUT);
  pinMode(inR2, OUTPUT);
  pinMode(inL1, OUTPUT);
  pinMode(inL2, OUTPUT);

  // set encoder to input
  pinMode(encoderR, INPUT);
  pinMode(encoderL, INPUT);
  // Initialize
  pulsesR = 0;
  pulsesL = 0;
}

void loop() {
  String payload = "";            
  if(!client.connected()){reconnect();}
  else{
    if(iWaypointTarget!=iWaypointReceived){
      float theta_now = getHeading();
      float theta_target = headingTarget[iWaypointTarget];
      float velR;
      float velL;
      if((theta_target-theta_now<5 && theta_target-theta_now>-5) || theta_target-theta_now>355 || theta_target-theta_now<-355){
        process=2;
      }
      payload+="P=";
      payload+=String(process);
      if(process==1){
        payload+=",TR=";
        float timeRun = getRunningTime("theta",theta_now,theta_target);
        prevTime=millis(); 
        attachInterrupt(encoderL-2, counterL, FALLING);
        attachInterrupt(encoderR-2, counterR, FALLING);
        if(timeRun>0){
          if(timeRun<50){
            timeRun=50;
          }
          while(millis()-prevTime<timeRun){
            rotate("CCW");
            delay(10);
            client.loop();
          } 
          detachInterrupt(encoderL-2);
          detachInterrupt(encoderR-2);
          velR = pulsesR;
          velL = -pulsesL;
        }
        else{
          if(timeRun>-50){
            timeRun=-50;
          }
          while(millis()-prevTime<-timeRun){
            rotate("CW");
            delay(10);
            client.loop();
          }        
          detachInterrupt(encoderL-2);
          detachInterrupt(encoderR-2);
          velR = -pulsesR;
          velL = pulsesL;
        }
        turnOffMotors();
        payload+=String(timeRun);
        payload+=",VR=";
        payload+=String(velR);
        payload+=",VL=";
        payload+=String(velR);
        payload+=",TN=";
        payload+=String(theta_now);
        payload+=",TT=";
        payload+=String(theta_target);
        theta_now = getHeading();
        if((theta_target-theta_now<5 && theta_target-theta_now>-5) || theta_target-theta_now>355 || theta_target-theta_now<-355){
          process=2;
        }
//        payload.toCharArray(buff, payload.length()+1);
//        client.publish("PDR/isReceived", buff);
      }
      else if(process==2){
        float timeRun = 21.239*sLengthTarget[iWaypointTarget] - 6.022;
        payload = String(timeRun);
        prevTime=millis(); 
        while(millis()-prevTime<timeRun){
          go();            
          delay(10);
          client.loop();
        }
        turnOffMotors();
//        payload+=",Heading";
//        payload+=String(headingTarget[iWaypointTarget]);
        iWaypointTarget++;
        if(iWaypointTarget==25){
          iWaypointTarget=0;
        }
        process = 1;
//        payload.toCharArray(buff, payload.length()+1);
//        client.publish("PDR/isReceived", buff);
      }   
      pulsesR = 0;
      pulsesL = 0;
    }
  }
  delay(10);
  client.loop();
}

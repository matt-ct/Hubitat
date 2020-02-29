/*
  Reading CO2, humidity and temperature from the SCD30
  Ambiant light from VEML6030 Sensor Displays on D1 Robot ST-1113 LCD hat
  Data being sent to MonaLisa ZigBee board to transmit to Hubitat hub

  2/20/2020 works saving as is and changing name to work on display
  Program has a mix of Serial and LCD writes in it.
  2/27/20 added in Lisa1.ico to talk to MonaLisa board

  more SCD30 commands:

  airSensor.begin(); //This will cause readings to occur every two seconds
  airSensor.setMeasurementInterval(4); //Change number of seconds between measurements: 2 to 1800 (30 minutes)
  My desk is ~1600m above sealevel
  airSensor.setAltitudeCompensation(1600); //Set altitude of the sensor in m
  Pressure in Boulder, CO is 24.65inHg or 834.74mBar
  airSensor.setAmbientPressure(835); //Current ambient pressure in mBar: 700 to 1200

  Methods and Functions SCD30 from keywords.txt in old Sparkfun folder
  Working commands
  begin
  getCO2
  getHumidity
  getTemperature
  setMeasurementInterval  2-1800 sec
  setAmbientPressure 700-1200 in mBar
  setAltitudeCompensation height above sea level in M Setting altitude is disregarded when an ambient pressure is given to the sensor
  dataAvailable 1 = yes

  To Test:
  setTemperatureOffset Temperature offset, unit [°C x 100], i.e. one tick corresponds to 0.01°C  Didn't wor as expected
  setAutoSelfCalibration “1”: Activate continuous ASC “0”: Deactivate continuous ASC (OK)
  setForcedRecalibrationFactor Set Forced Recalibration value  CO2 concentration in ppm 400-2000 ??? (Found in cpp Library)

  Commands Not utilized
  getTemperatureOffset    'class SCD30' has no member named 'getTemperatureOffset'

  Other Keywords/commands
  readMeasurement all 3 parameters
  sendCommand
  sendCommand
  readRegister
  computeCRC8
  beginMeasuring
  SCD30 KEYWORD2

*/

#include <LiquidCrystal.h> //using LiquidCrystal library
#include <Wire.h>  // I2C library
#include "SparkFun_SCD30_Arduino_Library.h"
#include "SparkFun_VEML6030_Ambient_Light_Sensor.h"
#include <SoftwareSerial.h>  // comm to/from the MonaLisa board
SoftwareSerial mySerial(3, 2); // RX, TX

/*  The circuit:
   RX is digital pin 2 (connect to TX of other device)
   TX is digital pin 3 (connect to RX of other device)
*/

#define AL_ADDR 0x48  // I^2C address of the Light sensor
// select the pins used on the LCD panel
LiquidCrystal lcd(8, 9, 4, 5, 6, 7);

// define some values used by the panel and buttons
int lcd_key = 0;
int adc_key_in = 0;

// define values for sending strings to the MonaLisa board
String LuString, COString, RHString, TeString, TxString;

#define btnRIGHT 0
#define btnUP 1
#define btnDOWN 2
#define btnLEFT 3
#define btnSELECT 4
#define btnNONE 5

int read_LCD_buttons()    // Not using
{
  adc_key_in = analogRead(0); // read the value from the Analog input 0
  // my buttons when read are centered at these valies: 0, 144, 329, 504, 741
  // we add approx 50 to those values and check to see if we are close
  if (adc_key_in > 1000) return btnNONE; // We make this the 1st option for speed reasons since it
  // will be the most likely result
  // For V1.1 us this threshold
  if (adc_key_in < 50) return btnRIGHT;
  if (adc_key_in < 250) return btnUP;
  if (adc_key_in < 450) return btnDOWN;
  if (adc_key_in < 650) return btnLEFT;
  if (adc_key_in < 850) return btnSELECT;
  // For V1.0 comment the other threshold and use the one below:
  /*
    if (adc_key_in < 50) return btnRIGHT;
    if (adc_key_in < 195) return btnUP;
    if (adc_key_in < 380) return btnDOWN;
    if (adc_key_in < 555) return btnLEFT;
    if (adc_key_in < 790) return btnSELECT;
  */
  return btnNONE; // when all others fail, return this...
}

SCD30 airSensor;
SparkFun_Ambient_Light light(AL_ADDR);

// Setting up gain and intigration time for light sensor.

// Possible values: .125, .25, 1, 2
// Both .125 and .25 should be used in most cases except darker rooms.
// A gain of 2 should only be used if the sensor will be covered by a dark
// glass.
float gain = .125;

/*
  Maximum Light Detection Range: Lux
  Integration Time (milliseconds)  GAIN 2  GAIN 1  GAIN 1/4  GAIN 1/8
  800                               236     472     1887      3775
  400                               472     944     3775      7550
  200                               944     1887    7550      15099
  100                               1887    3775    15099     30199
  50                                3775    7550    30199     60398
  25                                7550    15099   60398     120796
*/

// Possible integration times in milliseconds: 800, 400, 200, 100, 50, 25
// Higher times give higher resolutions and should be used in darker light.
int time = 100;
long luxVal = 0;
int DspN = 0;  //  counter for showing display updates ie something changes

void gotCommand(char* com, int comlength) {
  Serial.print("Got command of length: "); Serial.print(comlength); Serial.print(" : ");
  Serial.print(com);
  // Can do something else with that info here...
  // You'll also get commands back like...
  // evt_0x8000 -> some event is being processed with id 0x8000 (system event), 0xfeed (timer), etc.
  // pkt_0xd1_-32_7 -> a packet was received with header id 0xd1 (ZDO_STATE_CHANGE), etc.
  // same. -> we got an event but there is no new command
  // sendping. -> we send a ping to the hub
}
char myInputBuffer[20] = "";  //declare the input buffer and its length ME
int myInputBuffer_p = 0;

void setup()
{
  mySerial.begin(38400);  // set the data rate for the SoftwareSerial port From Arduino to Lisa
  Wire.begin();
  Serial.begin(9600); 
  lcd.begin(16, 2); // start the library

  // startup, check the sensors
  lcd.clear();  // Clear the screen
  lcd.setCursor(0, 0); // move cursor to  line "0" and 0 spaces over
  lcd.print("SCD30 VEML6030");
  lcd.setCursor(0, 1); // move cursor to  line "2" and 0 spaces over
  lcd.print("CO2 Control Prog");
  delay(2000);

  lcd.clear();  // Clear the screen
  lcd.setCursor(0, 0); // move cursor to  line "0" and 0 spaces over

  if (airSensor.begin()) //This will cause readings to occur every two seconds
  {
    lcd.print("SCD30 OK");
    Serial.println("SCD30 OK ");
  }
  else
  {
    lcd.print("No SCD30 ");
    Serial.println(" SCD30 fail ");
  }
  delay(2000);

  lcd.clear();  // Clear the screen
  lcd.setCursor(0, 0); // move cursor to  line "0" and 0 spaces over
  if (light.begin())
  {
    lcd.print("Light Sensor OK");
    Serial.println(" Light Sensor OK ");
  }
  else
  {
    lcd.print("No comm light");
    Serial.println(" Light Sensor fail ");
  }
  delay(2000);

  // Again the gain and integration times determine the resolution of the lux
  // value, and give different ranges of possible light readings. Check out
  // hoookup guide for more info.
  light.setGain(gain);
  light.setIntegTime(time);

  lcd.clear();  // Clear the screen                                    Gain
  lcd.setCursor(0, 0); // move cursor to  line "1" and 0 spaces over
  lcd.print("Lt Sensor set");
  lcd.setCursor(0, 1); // move cursor to  line "2" and 0 spaces over
  lcd.print("Gain: ");
  float gainVal = light.readGain();
  lcd.print(gainVal, 3);
  delay(2000);

  lcd.clear();  // Clear the screen                                   Intigration Time
  lcd.setCursor(0, 0); // move cursor to  line "1" and 0 spaces over
  lcd.print("Integration Time: ");
  int timeVal = light.readIntegTime();
  lcd.setCursor(0, 1); // move cursor to  line "2" and 0 spaces over
  lcd.print(timeVal);
  delay(2000);
}


void loop()
{

  if (mySerial.available()) {       // Reads the string from the MonaLisa and calls gotCommand to return string and length
    myInputBuffer[myInputBuffer_p] = mySerial.read();
    if (myInputBuffer[myInputBuffer_p] == '\n') {
      for (int n = myInputBuffer_p + 1; n < 20; n++) myInputBuffer[n] = '\0';
      gotCommand(myInputBuffer, myInputBuffer_p);
      myInputBuffer_p = 0;
    }
    else {
      myInputBuffer_p++;
      if (myInputBuffer_p > 16) myInputBuffer_p = 0;
    }
  }

  if (Serial.available()) {   // This will not be used in final version of program.
    char c = Serial.read();  //  Reads one character from the Serial Monitor and uses
    Serial.println(c);       //   the mySerial.write() sends command. to the MonaLisa!

    if (c == 'f') {
      mySerial.write("off.");//turn LEDs/outputs off (off1. would do just output 1, off2. 2, off3. 3, and off4. 4)
    }
    if (c == 'o') {
      mySerial.write("on.");//turn LEDs/outputs on (on1. would do just output 1, on2. 2, on3. 3, and on4. 4)
    }
    if (c == 's') {
      mySerial.write("someinfo.");//not a command for the cc2530, but will still get sent back to the hub / device handler
    }
    if (c == '0') {
      mySerial.write("getadc0.");//get value of ADC 0
    }
    if (c == '1') {
      mySerial.write("getadc1.");//get value of ADC 1
    }
    if (c == '4') {
      mySerial.write("getadc4.");//get value of ADC 4
    }
    if (c == '5') {
      mySerial.write("getadc5.");//get value of ADC 5
    }
    if (c == 'e') {
      mySerial.write("echo_off.");//send commands just to hub
    }
    if (c == 'E') {
      mySerial.write("echo_on.");//send commands to cc2530, not just to hub
    }
    if (c == 'p') {
      mySerial.write("ping_0.");//ping back to hub never
    }
    if (c == 'P') {
      mySerial.write("ping_2.");//ping back to hub every 2*10=20s // can be at most 65535*10 = ~1 week
    }
    if (c == 'b') {
      mySerial.write("getbutt1.");//get state of button 1
    }
    if (c == 'B') {
      mySerial.write("getbutt2.");//get state of button 2
    }
    if (c == 'g') {
      mySerial.write("getadcg.");//get "ground" adc value - only works with 5V power?!
    }
    if (c == '3') {
      mySerial.write("getadc3.");//get "reference voltage/3" - only works with 5V power?!
    }
    if (c == 'x') {
      mySerial.write("TxHello Hub."); //Return Lisa.  MUST Have . to be a command.
      Serial.println(" text Hello Hub");
    }
  }
  luxVal = light.readLight();

  if (airSensor.dataAvailable())   // MAIN LCD and MonaLisa OUTPUT
  {
    // update the LCD
    lcd.clear();  // Clear the screen
    lcd.setCursor(0, 0); // move cursor to  line "0" and 0 spaces over
    lcd.print(((1.8 * airSensor.getTemperature()) + 32), 0); // add ,1 to get decimal point
    lcd.print("F ");
    lcd.print("CO2 ");
    lcd.print(airSensor.getCO2());
    lcd.print("PPM "); // this fixed the clearing of digits when the count went from
    // Line 2
    lcd.setCursor(0, 1); // move cursor to second line "1" and 0 spaces over
    lcd.print(airSensor.getHumidity() , 1);
    lcd.println("%H ");
    lcd.setCursor(7, 1); // move cursor to second line "1" and 7 spaces over
    lcd.print(luxVal);
    lcd.println(" Lux      ");
    
    lcd.setCursor(15, 1); // move cursor to second line "1" and 7 spaces over
    lcd.print(DspN);   // counter 1....9 to show LCD updating
    DspN ++;
    if (DspN > 9){
      DspN = 0;
    }
    
    delay(3000);    //  delay to Let SCD30 catch up?
    
    // Send data to the MonaLisa board
    // had to swith from write to mySerial.print(TeString)
    // take each value trun it into a string and add key to string
    // Lu, CO, RH, Te, Tx String LuString, COString, RHString, TeString, TxString;
    mySerial.write("TxData sent."); //Return Lisa.  MUST Have . to be a command.
    Serial.println("TxData sent.");
    delay(500);    //  delay to Lisa catch up?
    
    LuString = String("Lu");
    LuString.concat(luxVal);
    LuString += "." ;
    mySerial.print(LuString); //  Illuminance starts with Lu Return Lisa.  MUST Have . to be a command.
    Serial.println(LuString);
    delay(500);    //  delay to Lisa catch up?
    
    COString = String("CO");
    COString.concat(airSensor.getCO2());
    COString += "." ;
    mySerial.print(COString); //CO2 starts with CO  Return Lisa.  MUST Have . to be a command.
    Serial.println(COString);
    delay(500);    //  delay to Lisa catch up?
    
    RHString = String("RH");
    RHString.concat(airSensor.getHumidity());
    RHString.replace(".", ""); // get rid of decimal point in string Lisa hates decimal points now have xxxx
    RHString += "." ;
    mySerial.print(RHString);   //RH
    Serial.println(RHString);
    delay(500);    //  delay to Lisa catch up?

    TeString = String("Te");
    TeString.concat(((1.8 * airSensor.getTemperature()) + 32));
    TeString.replace(".", ""); // get rid of decimal point in string Lisa hates decimal points now have xxxx
    TeString += "." ;
    mySerial.print(TeString); // Temp Te
    Serial.println(TeString);
    delay(500);    //  delay to Lisa catch up?
  }
  
  else                                                      // No SCD30 data availble
  {
    lcd.clear();  // Clear the screen
    lcd.setCursor(0, 0); // move cursor to  line "0" and 0 spaces over
    lcd.print("No SCD30 data ");
    Serial.println("No SCD30 data ");                                             // added for troubleshooting
    mySerial.write("No SCD30 data."); //Return Lisa.  MUST Have . to be a command.
    lcd.setCursor(0, 1); // move cursor to second line "1" and 0 spaces over
    lcd.println("T,H,CO2");
    lcd.setCursor(8, 1); // move cursor to second line "1" and 8 spaces over
    lcd.print(luxVal);
    lcd.println(" Lux      ");
  }

  /*   Out for now
    // Now read the buttons and desplay button pushed

      lcd.setCursor(0,1); // move to the begining of the second line
      lcd_key = read_LCD_buttons(); // read the buttons

    switch (lcd_key) // depending on which button was pushed, we perform an action
    {
    case btnRIGHT:
    {
    lcd.print("RIGHT ");
    // Serial.println(" Case Right "); //print the CASE
    break;
    }
    case btnLEFT:
    {
    lcd.print("LEFT ");
    // Serial.println(" Case Left "); //print the CASE
    break;
    }
    case btnUP:
    {
    lcd.print("UP  ");
    // Serial.println(" Case Up "); //print the CASE
    break;
    }
    case btnDOWN:
    {
    lcd.print("DOWN ");
    // Serial.println(" Case Down "); //print the CASE
    break;
    }
    case btnSELECT:
    {
    lcd.print("SEL");
    //Serial.println(" Case Select "); //print the CASE
    break;
    }
    case btnNONE:
    {
    lcd.print("NONE ");
    // Serial.println(" Case None "); //print the CASE
    break;
    }
    }
  */
  delay(60000);  // Needs to be longer than 2 seconds  Not too chatty with the MonaLisa every Min to start
  // too long makes the the button push take too much time

}

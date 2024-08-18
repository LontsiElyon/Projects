#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <MFRC522.h>
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <ArduinoJson.h>
#include <Adafruit_NeoPixel.h>

// WiFi settings
const char *ssid = "Apartment Y119" ;    // TODO: Change to your WiFi SSID   ""  "SmartFactoryLab"  Apartment Y119
const char *password = "99704532092388225373"; // TODO: Change to your WiFi Passwordt  "99704532092388225373"  "smartfactorylab"

// MQTT settings
const char *mqtt_server = "192.168.178.43"; // Laptop IP Address  192.168.50.199         192.168.178.43
const int mqtt_port = 1883;
const char *mqtt_user = "sose24";
const char *mqtt_password = "informatik";



// Define the OLED display width and height
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64

// Define the I2C address for the SSD1306 display
#define OLED_I2C_ADDRESS 0x3C // 0x3C is the default for most OLEDs, use 0x3D if needed

// Declaration for an SSD1306 display connected to I2C (SDA, SCL pins)
#define OLED_RESET     -1 // Reset pin # (or -1 if sharing Arduino reset pin)
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

#define SS_PIN 5
#define RST_PIN 4 

MFRC522 mfrc522(SS_PIN, RST_PIN);

#define NEOPIXEL_PIN 15
#define NUMPIXELS 2 

Adafruit_NeoPixel strip = Adafruit_NeoPixel(NUMPIXELS, NEOPIXEL_PIN, NEO_RGB + NEO_KHZ800);

const uint8_t ledPins[] = {32, 25, 27, 12};
const uint8_t buttonPins[] = {33, 26, 14, 13};

WiFiClient espClient;
PubSubClient client(espClient);


bool controllerIdPublished = false; // Flag to check if the controller ID has been published
uint32_t colorSequence[100];
//String sequenceColor = "";

// Define some colors
#define COLOR_RED    strip.Color(255, 0, 0)
#define COLOR_GREEN  strip.Color(0, 255, 0)
#define COLOR_BLUE   strip.Color(0, 0, 255)
#define COLOR_YELLOW strip.Color(255, 255, 0)
#define COLOR_OFF    strip.Color(0, 0, 0)

// Assuming ButtonColor is an enum like this:
enum ButtonColor {
    RED,
    YELLOW,
    GREEN,
    BLUE
};

const char* getColorName(uint32_t colorIndex) {
    switch (colorIndex) {
        case 0: return "YELLOW";
        case 1: return "BLUE";
        case 2: return "GREEN";
        case 3: return "RED";
        default: return "UNKNOWN";
    }
}

// Constants for debounce time
const unsigned long debounceDelay = 50; // 50 milliseconds debounce time

// Variables to track the last button state and last debounce time
int lastButtonState[4] = {HIGH, HIGH, HIGH, HIGH}; // Assuming pull-up resistors, buttons are HIGH when not pressed
int currentButtonState[4] = {HIGH, HIGH, HIGH, HIGH}; // Track current state
unsigned long lastDebounceTime[4] = {0, 0, 0, 0};

// Track the start time of the input window
unsigned long inputWindowStart = 0;
const unsigned long inputWindowDuration = 10000; // 5 seconds input window

// Array to store the sequence of colors
uint32_t sequenceColor[100];  // Assuming a maximum of 20 colors in the sequence
int sequenceIndex = 0;

// Function to show a single color
void showColor(uint32_t color) {
    for (int i = 0; i < strip.numPixels(); i++) {
        strip.setPixelColor(i, color);
        //Serial.print("Color set suceesfully");
    }
    strip.show();
    delay(500);  // Wait for half a second
}

// Function to show the sequence of colors
void showColorSequence(uint32_t* sequence, int length) {
    for (int i = 0; i < length; i++) {
        showColor(sequence[i]);
        delay(500);  // Pause between colors
        showColor(COLOR_OFF);  // Turn off before the next color
        delay(250);  // Short pause with LEDs off
    }
}


void setup_wifi()
{
  delay(10);
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED)
  {
    delay(500);
    Serial.print(".");
  }

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());
}





void reconnect()
{
  while (!client.connected())
  {
    Serial.print("Attempting MQTT connection...");
    if (client.connect("ESP32Client", mqtt_user, mqtt_password))
    {
      Serial.println("connected");
      //client.subscribe("player/update"); // Subscribe to the player/update topic
     if(client.subscribe("neopixel/display")) {
       Serial.println("Subscripted to neopixel successfully");
     }
     if(client.subscribe("oled/display/controller_2")){
       Serial.println("Subscripted to Oled successfully");
     }
    }
    else{
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      delay(5000);
    }
  }
}

void displayPlayerInfo(const String& playerName, int points, int round) {

  Serial.println("Displaying player info:");
  Serial.println("Player: " + playerName);
  Serial.println("Points: " + String(points));
  Serial.println("Round: " + String(round));
    display.clearDisplay();

    // Display Player Name
    display.setTextSize(1);
    display.setCursor(0, 0);
    display.println("Player: ");
    if(playerName.length() > 10){
        display.setTextSize(1);
    } else{
        display.setTextSize(2);
    }
    display.setCursor(0, 10);
    display.println(playerName);

    // Display Points
    display.setTextSize(1);
    display.setCursor(10, 40);
    display.println("Points: ");
    display.setTextSize(2);
    display.setCursor(10, 50);
    display.println(points);

    // Display Round
    display.setTextSize(1);
    display.setCursor(SCREEN_WIDTH / 2 +20, 40);
    display.println("Round: ");
    display.setTextSize(2);
    display.setCursor(SCREEN_WIDTH / 2 +20, 50);
    display.println(round);

    display.display(); 

}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
    Serial.print("Message arrived [");
    Serial.print(topic);
    Serial.print("] ");
  
    String message = "";
    for (unsigned int i = 0; i < length; i++) {
        message += (char)payload[i];
    }
    Serial.println(message);

    if (String(topic) == "neopixel/display") {
        // Process color sequence
        DynamicJsonDocument doc(512); // Increase size if needed
        DeserializationError error = deserializeJson(doc, message);

        if (error) {
            Serial.print("deserializeJson() failed: ");
            Serial.println(error.c_str());
            return;
        }

        // Clear the colorSequence array
        for (int i = 0; i < NUMPIXELS; i++) {
            colorSequence[i] = COLOR_OFF; // Default color
        }
             Serial.println("ALL LEDS OFF");
        // Update colorSequence array based on the JSON array
        int sequenceLength = doc.size();
        Serial.print(sequenceLength);
        for (int i = 0; i < sequenceLength || i < NUMPIXELS; i++) {
                    
            String colorStr = doc[i].as<String>();
            Serial.print(colorStr);
            if (colorStr == "RED") {
                colorSequence[i] = COLOR_RED;
                  Serial.println("RED");
            } else if (colorStr == "GREEN") {
                colorSequence[i] = COLOR_GREEN;
                Serial.println("GREEN");
            } else if (colorStr == "BLUE") {
                colorSequence[i] = COLOR_BLUE;
                Serial.println("BLUE");
            } else if (colorStr == "YELLOW") {
                colorSequence[i] = COLOR_YELLOW;
                Serial.println("YELLOW");
            } else {
                colorSequence[i] = COLOR_OFF; // Default color if unknown
            }
        }

        // Show the received color sequence
        showColorSequence(colorSequence, sequenceLength);
    }

    // Handle the OLED display update
    if (String(topic) == "oled/display/controller_2") {
      Serial.println("Displaying on OLED");
        DynamicJsonDocument doc(512);
        DeserializationError error = deserializeJson(doc, message);

        if (error) {
            Serial.print("Failed to parse JSON: ");
            Serial.println(error.c_str());
            return;
        }

        String username = doc["username"];
        int points = doc["points"];
        int round = doc["round"];

        displayPlayerInfo(username, points, round);
    }
}

void controller(){
    // Check if the controller ID has already been published
    if (!controllerIdPublished)
  {
    Serial.println("Publishing Controller ID");

    // Define the controller ID
    String controllerId = "controller_2"; // Change to a unique ID for each controller

    // Publish the controller ID to the MQTT server
    if (client.publish("controller/connect", controllerId.c_str()))
    {
      Serial.println("Controller ID published successfully.");
      controllerIdPublished = true; // Set the flag to true after publishing
    }
    else
    {
      Serial.println("Failed to publish Controller ID.");
    }
  }

}

void rfid(){
    // Look for new cards
  if (!mfrc522.PICC_IsNewCardPresent()) {
    return;
  }

  // Select one of the cards
  if (!mfrc522.PICC_ReadCardSerial()) {
    return;
  }


  // Dump UID of the card
  String rfidTag = "";
  for (byte i = 0; i < mfrc522.uid.size; i++) {
    rfidTag += String(mfrc522.uid.uidByte[i] < 0x10 ? "0" : "");
    rfidTag += String(mfrc522.uid.uidByte[i], HEX);
  }
  rfidTag.toUpperCase();

  Serial.println(rfidTag);

  // Print Card type
  MFRC522::PICC_Type piccType = mfrc522.PICC_GetType(mfrc522.uid.sak);
  Serial.print("PICC Type: ");
  Serial.println(mfrc522.PICC_GetTypeName(piccType));

  // Halt PICC
  mfrc522.PICC_HaltA();

  // Stop encryption on PCD
  mfrc522.PCD_StopCrypto1();  

   // Log and publish the RFID tag
  Serial.print("RFID Tag Detected: ");
  String payload = "{\"controllerId\":\"controller_2\", \"rfidTag\":\"" + rfidTag + "\", \"username\":\"Elyon's RFID\"}";
   // Publish the RFID tag
  if (client.publish("controller/rfid", payload.c_str())) {
    Serial.println("RFID tag published successfully.");
  } else {
    Serial.println("Failed to publish RFID tag.");
  }


}

void game(){
   if (inputWindowStart == 0) {
    inputWindowStart = millis(); // Start the input window when the loop begins
  }

  // Check if within the input window
  if (millis() - inputWindowStart < inputWindowDuration) {
    //Serial.println("Waiting for button presses...");

    for (byte i = 0; i < 4; i++) {
      int reading = digitalRead(buttonPins[i]);

      if (reading != currentButtonState[i]) {
        lastDebounceTime[i] = millis();
      }

      if ((millis() - lastDebounceTime[i]) > debounceDelay) {
        if (reading != lastButtonState[i]) {
          lastButtonState[i] = reading;

          if (reading == HIGH) {
            // Button pressed
            digitalWrite(ledPins[i], HIGH);  // Turn on the corresponding LED
            Serial.println("Button pressed!");

            // Record the color in the sequence array
            if (sequenceIndex < 20) {
              switch (i) {
                case 0: sequenceColor[sequenceIndex] = RED; break;
                case 1: sequenceColor[sequenceIndex] = YELLOW; break;
                case 2: sequenceColor[sequenceIndex] = GREEN; break;
                case 3: sequenceColor[sequenceIndex] = BLUE; break;
              }
              sequenceIndex++;
            }
          } else {
            digitalWrite(ledPins[i], LOW);  // Turn off the corresponding LED
          }
        }
      }

      currentButtonState[i] = reading;
    }

  } else {
    // Publish the sequence after the input window ends
    if (sequenceIndex > 0) {
      String sequencePayload = "{\"controllerId\":\"controller_2\", \"sequence\":[";

      for (int i = 0; i < sequenceIndex; i++) {
        sequencePayload += "\"" + String(getColorName(sequenceColor[i])) + "\"";
        if (i < sequenceIndex - 1) {
          sequencePayload += ",";
        }
      }

      sequencePayload += "]}";

      // Publish to the server
      if (client.publish("controller/color_sequence", sequencePayload.c_str())) {
        Serial.println("Color sequence published: " + sequencePayload);
      } else {
        Serial.println("Failed to publish color sequence.");
      }

      // Reset the sequence index for the next round
      sequenceIndex = 0;
    }

    // Reset the input window timer
    inputWindowStart = millis();
  }


}

void setup()
{
  Serial.begin(115200);
  setup_wifi();
  client.setServer(mqtt_server, mqtt_port);
   client.setCallback(mqttCallback);

  SPI.begin();           // Init SPI bus  
  delay(4);
  mfrc522.PCD_Init();      // Init MFRC522
  delay(4);
  mfrc522.PCD_DumpVersionToSerial();       
  Serial.println("Scan an RFID card or tag.");

  // Start the I2C communication
  Wire.begin(21, 22);

  // Initialize the display
  if (!display.begin(SSD1306_SWITCHCAPVCC, OLED_I2C_ADDRESS)) { // Initialize with VCC switch
    Serial.println(F("SSD1306 allocation failed"));
    for (;;); // Don't proceed, loop forever
  }

  display.display();
    delay(2000); // Pause for 2 seconds

    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 0);
    display.println(F("OLED display ready!"));
    display.display();
    delay(1000);

    strip.begin();
    strip.show();  // Initialize all pixels to 'off'
    strip.setBrightness(50);


  for (byte i = 0; i < 4; i++) {
    pinMode(ledPins[i], OUTPUT);
    pinMode(buttonPins[i], INPUT_PULLUP);
  }  
 

}


void loop()
{
  if (!client.connected())
  {
    reconnect();
  }
   client.loop();
   
  //Serial.println("Acessed Controller");
  controller();

  //Serial.println("Acessed RFID");
  rfid();

  game();

  delay(1000); // Publish every 2 seconds
}





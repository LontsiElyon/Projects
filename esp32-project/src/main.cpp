#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <MFRC522.h>
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <ArduinoJson.h>

// WiFi settings
const char *ssid = "SmartFactoryLab" ;    // TODO: Change to your WiFi SSID   "FRITZ!Box 7530 LL"  "SmartFactoryLab"
const char *password = "smartfactorylab"; // TODO: Change to your WiFi Passwordt  "99704532092388225373"  "smartfactorylab"

// MQTT settings
const char *mqtt_server = "192.168.50.199 "; // Laptop IP Address
const int mqtt_port = 1883;
const char *mqtt_user = "sose24";
const char *mqtt_password = "informatik";

#define SS_PIN 5
#define RST_PIN 4

MFRC522 mfrc522(SS_PIN, RST_PIN);

// Define the OLED display width and height
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64

// Define the I2C address for the SSD1306 display
#define OLED_I2C_ADDRESS 0x3C // 0x3C is the default for most OLEDs, use 0x3D if needed

// Declaration for an SSD1306 display connected to I2C (SDA, SCL pins)
#define OLED_RESET     -1 // Reset pin # (or -1 if sharing Arduino reset pin)
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

WiFiClient espClient;
PubSubClient client(espClient);

bool controllerIdPublished = false; // Flag to check if the controller ID has been published


// Data to display
String playerName = "Unknown";
int points = 0;
int round = 0;

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
      client.subscribe("player/update"); // Subscribe to the player/update topic
      
    }
    else{
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      delay(5000);
    }
  }
}

/*void displayPlayerInfo(const String& playerName, int points, int round) {
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    
    // Display player name
    display.setCursor(0, 0);
    display.println("Player: " + playerName);
    
    // Display player points
    display.setCursor(0, 10);
    display.println("Points: " + String(points));

    // Display round number
    display.setCursor(0, 20);
    display.println("Round: " + String(roundNumber));

    // Update the display with new content
    display.display();
}*/

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

    // Update colorSequence array based on the JSON array
    int length = doc.size();
    for (int i = 0; i < length && i < NUMPIXELS; i++) {
        String colorStr = doc[i].as<String>();
        if (colorStr == "RED") {
            colorSequence[i] = COLOR_RED;
        } else if (colorStr == "GREEN") {
            colorSequence[i] = COLOR_GREEN;
        } else if (colorStr == "BLUE") {
            colorSequence[i] = COLOR_BLUE;
        } else if (colorStr == "YELLOW") {
            colorSequence[i] = COLOR_YELLOW;
        } else {
            colorSequence[i] = COLOR_OFF; // Default color if unknown
        }
    }

    // Show the received color sequence
    showColorSequence(colorSequence, length);
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

/*  // Clear the display buffer
  display.clearDisplay();
  
  // Set text size and color
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);

  // Display a message
  display.setCursor(0, 0);
  display.println("Hello, ESP32!");
  display.println("OLED Display Demo");
  display.display(); // Update the display

*/
 
}


void loop()
{
  if (!client.connected())
  {
    reconnect();
  }

   // Check if the controller ID has already been published
  if (!controllerIdPublished)
  {
    Serial.println("Publishing Controller ID");

    // Define the controller ID
    String controllerId = "controller_1"; // Change to a unique ID for each controller

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
  String payload = "{\"controllerId\":\"controller_1\", \"rfidTag\":\"" + rfidTag + "\"}";
   // Publish the RFID tag
  if (client.publish("controller/rfid", payload.c_str())) {
    Serial.println("RFID tag published successfully.");
  } else {
    Serial.println("Failed to publish RFID tag.");
  }

  delay(2000); // Publish every 2 seconds
}


void displayPlayerInfo(const String& playerName, int points, int round) {
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    
    // Display player name
    display.setCursor(0, 0);
    display.println("Player: " + playerName);
    
    // Display player points
    display.setCursor(0, 10);
    display.println("Points: " + String(points));

    // Display round number
    display.setCursor(0, 20);
    display.println("Round: " + String(round));

    // Update the display with new content
    display.display();
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("] ");
  String message = "";
  for (int i = 0; i < length; i++) {
    message += (char)payload[i];
  }
  Serial.println(message);

  if (String(topic) == "player/update") {
    // Assuming the message is JSON like {"playerName":"John", "points":10, "round":1}
    DynamicJsonDocument doc(256);
    deserializeJson(doc, message);
    playerName = doc["playerName"].as<String>();
    points = doc["points"].as<int>();
    round = doc["round"].as<int>();

    // Update the display with new player info
    displayPlayerInfo(playerName, points, round);
  }
}
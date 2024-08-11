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
const char *ssid = "iPhone von patso" ;    // TODO: Change to your WiFi SSID   ""  "SmartFactoryLab"  Apartment Y119
const char *password = "123456789"; // TODO: Change to your WiFi Passwordt  "99704532092388225373"  "smartfactorylab"

// MQTT settings
const char *mqtt_server = "172.20.10.4"; // Laptop IP Address
const int mqtt_port = 1883;
const char *mqtt_user = "sose24";
const char *mqtt_password = "informatik";

#define SS_PIN 5
#define RST_PIN 4

#define NEOPIXEL_PIN 15
#define NUMPIXELS 4 

Adafruit_NeoPixel strip = Adafruit_NeoPixel(NUMPIXELS, NEOPIXEL_PIN, NEO_GRB + NEO_KHZ800);

// Define some colors
#define COLOR_RED    strip.Color(255, 0, 0)
#define COLOR_GREEN  strip.Color(0, 255, 0)
#define COLOR_BLUE   strip.Color(0, 0, 255)
#define COLOR_YELLOW strip.Color(255, 255, 0)
#define COLOR_OFF    strip.Color(0, 0, 0)

uint32_t colorSequence[NUMPIXELS];

// Function to show a single color
void showColor(uint32_t color) {
    for (int i = 0; i < strip.numPixels(); i++) {
        strip.setPixelColor(i, color);
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

/*void showColorSequence(uint32_t* sequence, int length) {
    // Show each color in the sequence
    for (int i = 0; i < length; i++) {
        // Set the color for each pixel individually
        for (int j = 0; j < strip.numPixels(); j++) {
            strip.setPixelColor(j, sequence[j % length]); // Wrap around if more pixels than colors
        }
        strip.show();
        delay(500);  // Pause between colors
        // Optional: Turn off LEDs before the next color
        for (int j = 0; j < strip.numPixels(); j++) {
            strip.setPixelColor(j, COLOR_OFF);
        }
        strip.show();
        delay(250);  // Short pause with LEDs off
    }
}*/

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
//String playerName = "Unknown";
//int points = 0;
//int roundNumber = 0;

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
       Serial.println("Subscripted successfully");
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
strip.begin();
    strip.show();  // Initialize all pixels to 'off'

   /* // Example sequence
    colorSequence[0] = COLOR_RED;
    colorSequence[1] = COLOR_GREEN;
    colorSequence[2] = COLOR_BLUE;
    colorSequence[3] = COLOR_YELLOW;

    // Show the color sequence
    showColorSequence(colorSequence, 4);*/
 
}


void loop()
{
  if (!client.connected())
  {
    reconnect();
  }
   client.loop();
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
  String payload = "{\"controllerId\":\"controller_1\", \"rfidTag\":\"" + rfidTag + "\", \"username\":\"Elyon's RFID\"}";
   // Publish the RFID tag
  if (client.publish("controller/rfid", payload.c_str())) {
    Serial.println("RFID tag published successfully.");
  } else {
    Serial.println("Failed to publish RFID tag.");
  }

  delay(2000); // Publish every 2 seconds
}





/**
 * @file main.cpp
 * @brief Game controller using Wi-Fi, MQTT, NeoPixel LEDs, and an RFID module.
 *
 * This program connects to an MQTT server, communicates with various hardware
 * components like NeoPixels and an RFID reader, and handles game logic where
 * players input sequences of button presses.
 * 
 * The program also interfaces with OLED displays, updates via MQTT, and handles game sequences.
 * 
 * @date 2024
 */

/**
 * @defgroup config Configuration and Initialization
 * @brief Initialization and configuration related functions.
 */
#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <MFRC522.h>
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <ArduinoJson.h>
#include <Adafruit_NeoPixel.h>
#include "display.h"
#include "rfid.h"
/**
 * @brief Wi-Fi credentials and MQTT server settings.
 * 
 * @defgroup settings Settings
 * @{
 */
// WiFi settings
const char *ssid = "SmartFactoryLab" ;    // TODO: Change to your WiFi SSID   ""  "SmartFactoryLab"  Apartment Y119  ///< Wi-Fi SSID
const char *password = "smartfactorylab"; // TODO: Change to your WiFi Passwordt  "99704532092388225373"  "smartfactorylab"  ///< Wi-Fi Password

// MQTT settings
const char *mqtt_server = "192.168.50.199"; // Laptop IP Address  192.168.50.199         192.168.178.43   ///< MQTT server IP address
const int mqtt_port = 1883;   ///< MQTT server port
const char *mqtt_user = "sose24";  ///< MQTT username
const char *mqtt_password = "informatik";   ///< MQTT password
/** @} */

/**
 * @defgroup hardware Hardware Components
 * @brief Initialization and interaction with hardware components like RFID, LEDs, and buttons.
 */

/**
 * @brief Initialize the RFID reader and related settings.
 * 
 * @defgroup rfid RFID Handling
 * @{
 */
#define SS_PIN 5
#define RST_PIN 4 

MFRC522 mfrc522(SS_PIN, RST_PIN);
/** @} */

/**
 * @brief Array of LED pins and button pins.
 * 
 * @defgroup io IO Configuration
 * @{
 */
const uint8_t ledPins[] = {32, 25, 27, 12};
const uint8_t buttonPins[] = {33, 26, 14, 13};
/** @} */

WiFiClient espClient;
PubSubClient client(espClient);

/**
 * @brief Pin definitions and NeoPixel setup.
 * 
 * @defgroup neopixel NeoPixel Control
 * @{
 */
#define NEOPIXEL_PIN 15  ///< NeoPixel data pin
#define NUMPIXELS 4 ///< Number of NeoPixels

Adafruit_NeoPixel strip = Adafruit_NeoPixel(NUMPIXELS, NEOPIXEL_PIN, NEO_RGB + NEO_KHZ800);
/** @} */

bool controllerIdPublished = false; // Flag to check if the controller ID has been published
uint32_t colorSequence[100];


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
unsigned long inputWindowDuration ; // 5 seconds input window
const unsigned long baseInputDuration = 10000;
const unsigned long additionalTimePerColor = 2000;
unsigned long lastReconnectAttempt = 0;
const unsigned long reconnectInterval = 5000; // Try to reconnect every 5 seconds
unsigned long lastHeartbeat = 0;
const unsigned long heartbeatInterval = 10000; // Send heartbeat every 10 seconds
bool readyForNextSequence = true;
bool hasLost = false;
bool sequenceEntered = false;
const int maxSequenceLength = 20;
bool gameStarted = false;
String controllerId;

// Define the LWT topic and message
const char* willTopic = "controller/status";
const char* willMessage = "offline";  // Message to be sent if client disconnects unexpectedly
int willQoS = 1;
bool willRetain = true;

// Array to store the sequence of colors
uint32_t sequenceColor[100];  // Assuming a maximum of 20 colors in the sequence
int sequenceIndex = 0;

/**
 * @brief Shows a single color on the NeoPixel strip.
 * 
 * @param color The color to display.
 * 
 * @ingroup neopixel
 */
// Function to show a single color
void showColor(uint32_t color) {
    for (int i = 0; i < strip.numPixels(); i++) {
        strip.setPixelColor(i, color);
        //Serial.print("Color set suceesfully");
    }
    strip.show();
    delay(500);  // Wait for half a second
}

/**
 * @brief Displays a sequence of colors on the NeoPixel strip.
 * 
 * @param sequence The array of colors to display.
 * @param length The length of the color sequence.
 * 
 * @ingroup neopixel
 */
// Function to show the sequence of colors
void showColorSequence(uint32_t* sequence, int length) {
    for (int i = 0; i < length; i++) {
        showColor(sequence[i]);
        delay(500);  // Pause between colors
        showColor(COLOR_OFF);  // Turn off before the next color
        delay(250);  // Short pause with LEDs off
    }
}
/**
 * @defgroup mqtt MQTT Handling
 * @brief Functions for connecting to and interacting with the MQTT server.
 */

/**
 * @brief Connects the ESP32 to the specified Wi-Fi network.
 * 
 * @ingroup config
 */
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

/**
 * @brief Initializes and publishes the controller ID to the MQTT server.
 * 
 * @return True if the controller ID was successfully published, false otherwise.
 * 
 * @ingroup mqtt
 */
bool initializeControllerId() {
    if (!controllerIdPublished) {
        Serial.println("Initializing and Publishing Controller ID");

        // Generate a unique ID for the device
        char ssid[23];
        snprintf(ssid, sizeof(ssid), "Controller-%llX", ESP.getEfuseMac());
        controllerId = String(ssid);  // Assign the result to controllerId

        Serial.println("Generated Controller ID: " + controllerId);

        // Publish the controller ID to the MQTT server
        if (client.publish("controller/connect", controllerId.c_str())) {
            Serial.println("Controller ID published successfully.");
            controllerIdPublished = true;  // Mark it as published
            return true;
        } else {
            Serial.println("Failed to publish Controller ID.");
            return false;
        }
    }
    return false;
}

bool connectMQTT(){
   // Publish Controller ID if not already done
            if (!controllerIdPublished) {
                if (initializeControllerId()) {
                    Serial.println("Controller ID published and ready for subscriptions.");
                } else {
                    Serial.println("Waiting for Controller ID to be published...");
                }
            } 

           // Generate a unique client ID using the controller ID
    String clientId = "ESP32Client-" + controllerId;
    
    Serial.print("Attempting MQTT connection with client ID: ");
    Serial.println(clientId);
    
    if (client.connect(clientId.c_str(), mqtt_user, mqtt_password, willTopic, willQoS, willRetain, willMessage)) {
        Serial.println("Connected to MQTT broker");
        
        // Subscribe to necessary topics
        String neoTopic = "neopixel/display" + controllerId;
        if (client.subscribe(neoTopic.c_str())) {
            Serial.println("Subscribed to neopixel successfully");
        }
        
        
            String oledTopic = "oled/display/" + controllerId;
            String actionTopic = "controller/action/" + controllerId;
            
            if (client.subscribe(oledTopic.c_str())) {
                Serial.println("Subscribed to OLED topic: " + oledTopic);
            }
            
            if (client.subscribe(actionTopic.c_str())) {
                Serial.println("Subscribed to action topic: " + actionTopic);
            }
        
        
        // Publish online status
        if (client.publish(willTopic, "online", true)) {
            Serial.println("Published online status.");
        }
        
        // Publish reconnection message
        String reconnectMessage = "{\"controllerId\":\"" + controllerId + "\", \"status\":\"connected\"}";
        client.publish("controller/status", reconnectMessage.c_str());
        
        return true;
    }
    
    Serial.print("Failed to connect, rc=");
    Serial.println(client.state());
    return false;   
}
/**
 * @brief Attempts to reconnect to the MQTT server and subscribes to relevant topics.
 * 
 * @return True if reconnected successfully, false otherwise.
 * 
 * @ingroup mqtt
 */
bool reconnect()
{
  while (!client.connected())
  {
    Serial.print("Attempting MQTT connection...");
        return connectMQTT();
  }
  return true;   // Already connected
}


/**
 * @brief Handles game start and input windows for color sequences.
 * 
 * @param sequenceLength The length of the color sequence received.
 */
void onSequenceReceived(int sequenceLength) {
  inputWindowDuration = (baseInputDuration + (sequenceLength * additionalTimePerColor))/2;
  inputWindowStart = millis();
  Serial.print("Input window started for ");
  Serial.print(inputWindowDuration);
  Serial.println(" milliseconds");

}

/**
 * @brief Requests the next color sequence for the game.
 * 
 * @ingroup game
 */
void requestNextSequence() {
  String topic = "controller/request_sequence";
  String message = controllerId;
  
  if (client.publish(topic.c_str(), message.c_str())) {
    Serial.println("Next sequence requested");
  } else {
    Serial.println("Failed to request next sequence");
  }
}
/**
 * @brief Sends a heartbeat message to the MQTT server.
 * 
 * @ingroup mqtt
 */
void sendHeartbeat() {
  if (client.connected()) {
    String heartbeatMessage = "{\"controllerId\":\"" + controllerId + "\", \"status\":\"alive\"}";
    client.publish("controller/heartbeat", heartbeatMessage.c_str());
    Serial.println("Heartbeat sent");
  }
}


/**
 * @brief Callback function that handles incoming MQTT messages.
 * 
 * @param topic The topic of the incoming message.
 * @param payload The payload of the message.
 * @param length The length of the payload.
 * 
 * @ingroup mqtt
 */
void mqttCallback(char* topic, byte* payload, unsigned int length) {
    Serial.print("Message arrived [");
    Serial.print(topic);
    Serial.print("] ");
  
    String message = "";
    for (unsigned int i = 0; i < length; i++) {
        message += (char)payload[i];
    }
    Serial.println(message);

    if (String(topic) == "neopixel/display"+ controllerId) {
        gameStarted = true;
        hasLost = false;
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
        onSequenceReceived(sequenceLength);
    }

    // Handle the OLED display update
    if (String(topic) == "oled/display/" + controllerId) {
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
      String gameMessage = doc["message"];

      if (gameMessage == "You lost!") {
          hasLost = true;
          displayLossMessage();
      }else if (gameMessage == "Game Over!") {
            int finalRound = doc["round"];
            gameStarted = false;
            displayGameOverMessage(finalRound);
      } else {
          displayPlayerInfo(username, points, round);
          requestNextSequence();
      }

    }

     if (String(topic) == "controller/action/" + controllerId) {
        DynamicJsonDocument doc(256);
        DeserializationError error = deserializeJson(doc, message);
        
        if (!error) {
            String action = doc["action"].as<String>();
            if (action == "countdown") {
                startCountdown();
            }
        }
    }     

}

/**
 * @brief Handles the sequence of button inputs from players.
 * 
 * This function checks for button presses, debounces them, and updates the
 * sequence of colors entered by the player.
 * 
 * @ingroup game
 */
void game() {
    if (!gameStarted || hasLost) {
        return; // Don't process any inputs if the game hasn't started or if this controller has lost
    }

    if (inputWindowStart == 0) {
        inputWindowStart = millis();
        sequenceEntered = false; // Reset the flag at the start of each input window
    }

    if (millis() - inputWindowStart < inputWindowDuration) {
        for (byte i = 0; i < 4; i++) {
            int reading = digitalRead(buttonPins[i]);

            if (reading != currentButtonState[i]) {
                lastDebounceTime[i] = millis();
            }

            if ((millis() - lastDebounceTime[i]) > debounceDelay) {
                if (reading != lastButtonState[i]) {
                    lastButtonState[i] = reading;

                    if (reading == HIGH) {
                        digitalWrite(ledPins[i], HIGH);
                        Serial.println("Button pressed!");

                        if (sequenceIndex < 20) {
                            switch (i) {
                                case 0: sequenceColor[sequenceIndex] = RED; break;
                                case 1: sequenceColor[sequenceIndex] = YELLOW; break;
                                case 2: sequenceColor[sequenceIndex] = GREEN; break;
                                case 3: sequenceColor[sequenceIndex] = BLUE; break;
                            }
                            sequenceIndex++;
                            sequenceEntered = true;
                        }
                    } else {
                        digitalWrite(ledPins[i], LOW);
                    }
                }
            }

            currentButtonState[i] = reading;
        }
    } else {
        if (sequenceEntered) {
            String sequencePayload = "{\"controllerId\":\""+ controllerId +"\", \"sequence\":[";

            for (int i = 0; i < sequenceIndex; i++) {
                sequencePayload += "\"" + String(getColorName(sequenceColor[i])) + "\"";
                if (i < sequenceIndex - 1) {
                    sequencePayload += ",";
                }
            }

            sequencePayload += "]}";

            if (client.publish("controller/color_sequence", sequencePayload.c_str())) {
                Serial.println("Color sequence published: " + sequencePayload);
            } else {
                Serial.println("Failed to publish color sequence.");
            }

            sequenceIndex = 0;
            //requestNextSequence();
        }else {
            // No sequence entered within the time limit
            hasLost = true;
            displayLossMessage();
            // Optionally, inform the server about the loss
            String lossPayload = "{\"controllerId\":\""+controllerId+"\", \"status\":\"lost\"}";
            if (client.publish("controller/playerstatus", lossPayload.c_str())) {
                Serial.println("Loss status published");
            } else {
                Serial.println("Failed to publish loss status");
            }
        }

        inputWindowStart = 0;

    }
    
}

/**
 * @brief Initializes the game-related hardware and settings.
 * 
 * @ingroup hardware
 */
void setup()
{
  Serial.begin(115200);
  setup_wifi();
  client.setServer(mqtt_server, mqtt_port);
   client.setCallback(mqttCallback);

  SPI.begin();           // Init SPI bus  
  setup_rfid(); // Initialize RFID reader
  
  Wire.begin(21, 22); // Start the I2C communication

    strip.begin();
    strip.setBrightness(50); // Set initial brightness
    strip.show(); // Initialize all pixels to 'off'

    initializeDisplay();

  for (byte i = 0; i < 4; i++) {
    pinMode(ledPins[i], OUTPUT);
    pinMode(buttonPins[i], INPUT_PULLUP);
  }  
    gameStarted = false;
    hasLost = false;
    inputWindowStart = 0;
    sequenceIndex = 0;

}

/**
 * @brief Main loop of the program, handles MQTT connection, game logic, and RFID checking.
 * 
 * @ingroup main
 */
void loop()
{

  unsigned long currentMillis = millis();

  if (!client.connected()) {
    if (currentMillis - lastReconnectAttempt > reconnectInterval) {
      lastReconnectAttempt = currentMillis;
      Serial.println("Attempting to reconnect MQTT...");
      if (reconnect()) {
        lastReconnectAttempt = 0;
      }
    }
  } else {
    client.loop();

    // Send heartbeat
    if (currentMillis - lastHeartbeat > heartbeatInterval) {
      lastHeartbeat = currentMillis;
      sendHeartbeat();
    }
  } 

  // Call the RFID logic from the new RFID module
   rfid_check();

    if (gameStarted) {
        game();
    }

  //delay(1000); // Publish every 2 seconds


}




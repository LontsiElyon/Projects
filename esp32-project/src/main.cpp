#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>

// WiFi settings
const char *ssid = "Lovro's Pixel 7 Pro";     // TODO: Change to your WiFi SSID
const char *password = "123456789"; // TODO: Change to your WiFi Password

// MQTT settings
const char *mqtt_server = "192.168.76.5"; // Laptop IP Address
const int mqtt_port = 1883;
const char *mqtt_user = "sose24";
const char *mqtt_password = "informatik";

WiFiClient espClient;
PubSubClient client(espClient);

// ID Global Variable
String ssid1;

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
    }
    else{
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      delay(5000);
    }
  }
}

void setup()
{
  Serial.begin(115200);
  setup_wifi();
  client.setServer(mqtt_server, mqtt_port);

  // Generate a unique ID for the device
  char ssid[23];
  snprintf(ssid, sizeof(ssid), "Controller-%llX", ESP.getEfuseMac());
  ssid1 = String(ssid);  // Assign the result to ssid

  Serial.print("Device ID: ");
  Serial.println(ssid1);
}

void loop()
{
  if (!client.connected())
  {
    reconnect();
  }
  client.loop();

  // ID status
  String payload = "{\"status\": \"online\"}";
  client.publish(ssid1.c_str(), payload.c_str());
  delay(2000); // Publish every 2 seconds
}

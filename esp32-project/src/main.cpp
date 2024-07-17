#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>

// WiFi settings
const char *ssid = "SmartFactoryLab";     // TODO: Change to your WiFi SSID
const char *password = "smartfactorylab"; // TODO: Change to your WiFi Password

// MQTT settings
const char *mqtt_server = "192.168.50.206"; // Laptop IP Address
const int mqtt_port = 1883;
const char *mqtt_user = "sose24";
const char *mqtt_password = "informatik";

WiFiClient espClient;
PubSubClient client(espClient);

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
    else
    {
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
}

void loop()
{
  if (!client.connected())
  {
    reconnect();
  }
  client.loop();

  // Example payload
  String payload = "{\"message\": \"Hello from ESP32\"}";
  client.publish("simon/game", payload.c_str());
  delay(2000); // Publish every 2 seconds
}

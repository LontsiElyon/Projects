/**
 * @file rfid.cpp
 * @brief RFID handling for the Simon Game application
 * @details This file contains functions for initializing the RFID reader and checking RFID tags.
 *          It interacts with the RFID reader to scan cards or tags and publishes the RFID data 
 *          to an MQTT topic. It also logs RFID information to the serial console.
 */
#include "rfid.h"
#include <MFRC522.h>
#include <PubSubClient.h>

extern MFRC522 mfrc522;  // Use the RFID reader instance from the main file
extern PubSubClient client; // Access the MQTT client from the main file
extern String controllerId; // Use the controller ID from the main file

void setup_rfid() {
  mfrc522.PCD_Init();  // Initialize the RFID reader
  delay(4);
  mfrc522.PCD_DumpVersionToSerial();  // Output RFID version info
  Serial.println("Scan an RFID card or tag.");
}

void rfid_check() {
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
  String payload = "{\"controllerId\":\"" + controllerId + "\", \"rfidTag\":\"" + rfidTag + "\", \"username\":\"Elyon's RFID\"}";

  // Publish the RFID tag
  if (client.publish("controller/rfid", payload.c_str())) {
    Serial.println("RFID tag published successfully.");
  } else {
    Serial.println("Failed to publish RFID tag.");
  }
}
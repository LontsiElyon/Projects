#ifndef RFID_H
#define RFID_H

/**
 * @brief Connects the ESP32 to the specified Wi-Fi network.
 */
void setup_rfid();

/**
 * @brief Checks for and processes RFID tags scanned by the controller.
 */
void rfid_check();

#endif
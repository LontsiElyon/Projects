/**
 * @file display.cpp
 * @brief Display management for the Simon Game application
 * @details This file contains functions for interacting with the OLED display.
 *          It includes initialization of the display, clearing the display, and displaying various messages
 *          such as player information, game over messages, and countdowns. It uses the Adafruit SSD1306 library 
 *          to control the OLED display and provide visual feedback for the game.
 */
#include "display.h"
#include <Adafruit_SSD1306.h>
#include "display.h"

// Define the OLED display width and height
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64

// Define the I2C address for the SSD1306 display
#define OLED_I2C_ADDRESS 0x3C // 0x3C is the default for most OLEDs, use 0x3D if needed

// Declaration for an SSD1306 display connected to I2C (SDA, SCL pins)
#define OLED_RESET     -1 // Reset pin # (or -1 if sharing Arduino reset pin)
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

// Initialize the display
void initializeDisplay() {
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

}

// Clear the display
void clearDisplay() {
    display.clearDisplay();
    display.display();
}

void displayLossMessage() {
    display.clearDisplay();
    display.setTextSize(2);
    display.setTextColor(SSD1306_WHITE);

    int16_t x1, y1;
    uint16_t width, height;

    display.getTextBounds("YOU LOST!", 0, 0, &x1, &y1, &width, &height);
    int16_t xPos = (SCREEN_WIDTH - width) / 2;
    int16_t yPos = 15;
    display.setCursor(xPos, yPos);
    display.println("YOU LOST!");

    display.setTextSize(1);
    display.getTextBounds("wrong combination", 0, 0, &x1, &y1, &width, &height);
    xPos = (SCREEN_WIDTH - width) / 2;
    yPos = 40; 
    display.setCursor(xPos, yPos);
    display.println("wrong combination");

    display.display(); 

    delay(1500); 

    // Vertical lines clearing from bottom to top
    for (int y = SCREEN_HEIGHT - 1; y >= 0; y--) {
        display.drawLine(0, y, SCREEN_WIDTH, y, SSD1306_BLACK); // Draw horizontal line from left to right
        display.display();
        delay(10); // Adjust delay to control the speed of the clearing animation
    }

    delay(1000); // Hold the final cleared screen for 1 second
    display.clearDisplay(); // Clear the display at the end to ensure it's completely empty
    display.display();

}

void startCountdown() {
    for (int i = 3; i > 0; i--) {
        display.clearDisplay();
        display.setTextSize(4);
        display.setTextColor(SSD1306_WHITE);
        display.setCursor(SCREEN_WIDTH/2 - 12, SCREEN_HEIGHT/2 - 16);
        display.println(i);
        display.display();
        delay(1000);
    }
    
    display.clearDisplay();
    display.setTextSize(2);
    display.setCursor(SCREEN_WIDTH/2 - 24, SCREEN_HEIGHT/2 - 8);
    display.println("GO!");
    display.display();
    delay(1000);
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

void displayGameOverMessage(int round) {
    display.clearDisplay();
    display.setTextSize(2);
    display.setTextColor(SSD1306_WHITE);

    // Display "Game Over!"
    int16_t x1, y1;
    uint16_t width, height;
    display.getTextBounds("Game Over!", 0, 0, &x1, &y1, &width, &height);
    int16_t xPos = (SCREEN_WIDTH - width) / 2;
    display.setCursor(xPos, 10);
    display.println("Game Over!");

    // Display the final round
    display.setTextSize(1);
    String roundText = "Final Round: " + String(round);
    display.getTextBounds(roundText, 0, 0, &x1, &y1, &width, &height);
    xPos = (SCREEN_WIDTH - width) / 2;
    display.setCursor(xPos, 40);
    display.println(roundText);

    display.display();
    delay(5000); // Display for 5 seconds
}
#ifndef DISPLAY_H
#define DISPLAY_H

#include <Arduino.h>

// Function declarations
void initializeDisplay();
void clearDisplay();

void displayLossMessage();
void startCountdown();
void displayPlayerInfo(const String& playerName, int points, int round);
void displayGameOverMessage(int round);

#endif
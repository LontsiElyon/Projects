#ifndef DISPLAY_H
#define DISPLAY_H

#include <Arduino.h>

// Function declarations
void initializeDisplay();
void clearDisplay();
/**
 * @brief Displays a loss message on the OLED screen.
 */
void displayLossMessage();

/**
 * @brief Starts the countdown for the next game round.
 */
void startCountdown();

/**
 * @brief Displays player information on the OLED screen.
 * 
 * @param playerName The username of the player.
 * @param points The player's score.
 * @param round The current game round.
 */
void displayPlayerInfo(const String& playerName, int points, int round);

/**
 * @brief Displays a game over message on the OLED screen.
 * 
 * @param round The final round number when the game ends.
 */
void displayGameOverMessage(int round);

#endif
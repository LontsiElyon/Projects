/**
 * @file main.js
 * @brief JavaScript code for interacting with the backend via REST and WebSockets.
 * @details This file contains code to handle the user login, controller loading, and game start/stop functionalities using AJAX requests to the backend.
 */
$(document).ready(function() {
    /**
     * @brief Load available controllers from the backend.
     * @details This function makes an AJAX GET request to the backend to fetch available controllers and populates the dropdown with the controller IDs.
     */
    loadControllers();

    /**
     * @brief Handle form submission for player login.
     * @details This function handles the login form submission and sends an AJAX POST request to register the player and create a session.
     * @param event The form submission event object.
     */
    $('#login-form').submit(function(event) {
        event.preventDefault();

        const username = $('#username').val();
        const controllerId = $('#controller').val();

        if (!username || !controllerId) {
            $('#login-result').text('Please provide a username and select a controller.');
            return;
        }

        $.ajax({
            url: '/api/login',
            method: 'POST',
            data: {
                username: username,
                controller: controllerId
            },
            /**
             * @brief Callback function when the login request succeeds.
             * @param response The response from the backend.
             */
            success: function(response) {
                $('#login-result').text('Player registered and session created successfully.');
            },
            /**
             * @brief Callback function when the login request fails.
             * @param xhr The XMLHttpRequest object.
             * @param status The status of the request.
             * @param error The error message.
             */
            error: function(xhr, status, error) {
                $('#login-result').text('Failed to register player or create session.');
                console.error('Error:', error);
            }
        });
    });

    /**
     * @brief Load available controllers from the backend.
     * @details This function fetches the list of controllers via an AJAX GET request and updates the select dropdown with controller options.
     */
    function loadControllers() {
        $.get('/api/controllers', function(data) {
            const controllerSelect = $('#controller');
            controllerSelect.empty();

            if (data.length === 0) {
                controllerSelect.append('<option value="">No controllers available</option>');
            } else {
                data.forEach(controller => {
                    controllerSelect.append(`<option value="${controller.controller_id}">${controller.controller_id}</option>`);
                });
            }
        });
    }

    /**
     * @brief Start the game by sending a request to the backend.
     * @details Sends a POST request to start the game and begins polling for the round winner.
     */
    $('#start-game').click(function() {
        $.ajax({
            url: '/api/generate-sequence',
            method: 'POST',
            success: function(response) {
                $('#info-display').html('<p>Game has started!</p>');
                startWinnerPolling();
            },
            /**
             * @brief Callback function when the game start request fails.
             * @param xhr The XMLHttpRequest object.
             * @param status The status of the request.
             * @param error The error message.
             */
            error: function(xhr, status, error) {
                $('#info-display').html('<p>Failed to start the game.</p>');
                console.error('Error:', error);
            }
        });
    });

    $('#stop-game').click(function() {
        stopWinnerPolling();
        $('#info-display').html('<p>Game stopped</p>');
    });


    /**
     * @brief Fetch the round winner from the backend.
     * @details This function sends a GET request to fetch the round winner and updates the display with the results.
     */
    function fetchRoundWinner() {
        $.ajax({
            url: '/api/round-winner',
            method: 'GET',
            dataType: 'json',
            /**
             * @brief Callback function when the round winner request succeeds.
             * @param response The response from the backend containing the round winner details.
             */
            success: function(response) {
                console.log('Round winner response:', response); // Debug log
                if (response.round !== undefined && response.name) {
                    $('#info-display').html(`<p>Round ${response.round} Winner: ${response.name}</p>`);
                } else if (response.message) {
                    $('#info-display').html(`<p>${response.message}</p>`);
                } else {
                    $('#info-display').html('<p>Waiting for round results...</p>');
                }
            },
            /**
             * @brief Callback function when the round winner request fails.
             * @param xhr The XMLHttpRequest object.
             * @param status The status of the request.
             * @param error The error message.
             */
            error: function(xhr, status, error) {
                console.error('Failed to fetch round winner:', error);
                $('#info-display').html('<p>Failed to fetch round winner. Retrying...</p>');
            }
        });
    }

    let winnerPollingInterval;
     
    /**
     * @brief Start polling for the round winner.
     * @details This function sets up an interval to repeatedly fetch the round winner every 5 seconds.
     */

    function startWinnerPolling() {
        // Clear any existing interval
        stopWinnerPolling();
        
        // Start polling every 5 seconds
        winnerPollingInterval = setInterval(fetchRoundWinner, 5000);
        // Fetch immediately on start
        fetchRoundWinner();
    }
      /**
     * @brief Stop polling for the round winner.
     */
    function stopWinnerPolling() {
        if (winnerPollingInterval) {
            clearInterval(winnerPollingInterval);
        }
    }


});

      
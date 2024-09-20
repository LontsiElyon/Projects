/**
 * @file app.js
 * @brief JavaScript code for interacting with the backend via REST and WebSockets.
 * @details This file contains code to handle the user login, controller loading, and game start/stop functionalities using AJAX requests to the backend.
 */

/**
 * @function
 * @name documentReady
 * @description Function executed when the document is ready.
 */
$(document).ready(function() {
   
    loadControllers();

    /**
     * @function
     * @name handleLoginFormSubmit
     * @description Handles login form submission.
     * @param {Event} event - The form submission event object.
     * @details Prevents the default form submission, retrieves the username and controller ID, and sends a POST request to '/api/login'.
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
         * @function
         * @name success
         * @description Callback function when the login request succeeds.
         * @param {Object} response - The response from the backend.
         */
            success: function(response) {
                $('#login-result').text('Player registered and session created successfully.');
            },
            /**
         * @function
         * @name handleLoginError
         * @description Callback function when the login request fails.
         * @param {jqXHR} xhr - The jqXHR object.
         * @param {string} status - The status of the request.
         * @param {string} error - The error message.
         */
            error: function(xhr, status, error) {
                $('#login-result').text('Failed to register player or create session.');
                console.error('Error:', error);
            }
        });
    });

    /**
     * @function
     * @name loadControllers
     * @description Fetches the list of available controllers from the backend and populates the dropdown.
     * @details This function sends a GET request to '/api/controllers' and updates the controller select element.
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
     * @function
     * @name handleStartGame
     * @description Start the game by sending a request to the backend.
     */
    $('#start-game').click(function() {
        $.ajax({
            url: '/api/generate-sequence',
            method: 'POST',
            /**
             * @function
             * @name Success
             * @description Callback function when the game start request succeeds.
             * @param {Object} response - The response from the backend.
             */
            success: function(response) {
                $('#info-display').html('<p>Game has started!</p>');
                startWinnerPolling();
            },
            /**
             * @function
             * @name Error
             * @description Callback function when the game start request fails.
             * @param {jqXHR} xhr - The jqXHR object.
             * @param {string} status - The status of the request.
             * @param {string} error - The error message.
             */
            error: function(xhr, status, error) {
                $('#info-display').html('<p>Failed to start the game.</p>');
                console.error('Error:', error);
            }
        });
    });

    /**
     * @function
     * @name handleStopGame
     * @description Stops the game and clears the winner polling.
     */
    $('#stop-game').click(function() {
        stopWinnerPolling();
        $('#info-display').html('<p>Game stopped</p>');
    });


     /**
     * @function
     * @name fetchRoundWinner
     * @description Fetches the current round winner from the backend.
     * @details Sends a GET request to '/api/round-winner' and updates the display with the round winner or a message.
     */
    function fetchRoundWinner() {
        $.ajax({
            url: '/api/round-winner',
            method: 'GET',
            dataType: 'json',
            /**
             * @function
             * @name handleFetchRoundWinnerSuccess
             * @description Callback function when the round winner request succeeds.
             * @param {Object} response - The response from the backend containing the round winner details.
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
             * @function
             * @name handleFetchRoundWinnerError
             * @description Callback function when the round winner request fails.
             * @param {jqXHR} xhr - The jqXHR object.
             * @param {string} status - The status of the request.
             * @param {string} error - The error message.
             */
            error: function(xhr, status, error) {
                console.error('Failed to fetch round winner:', error);
                $('#info-display').html('<p>Failed to fetch round winner. Retrying...</p>');
            }
        });
    }

    let winnerPollingInterval;
     
    /**
     * @function
     * @name startWinnerPolling
     * @description Start polling for the round winner.
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
     * @function
     * @name stopWinnerPolling
     * @description Stop polling for the round winner.
     */
    function stopWinnerPolling() {
        if (winnerPollingInterval) {
            clearInterval(winnerPollingInterval);
        }
    }


});

      
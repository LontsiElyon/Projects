// JavaScript to interact with the Backend via REST and / or WebSockets
 // Document ready function
$(document).ready(function() {
    // Load available controllers from the backend
    loadControllers();

    // Handle form submission for player login
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
            success: function(response) {
                $('#login-result').text('Player registered and session created successfully.');
            },
            error: function(xhr, status, error) {
                $('#login-result').text('Failed to register player or create session.');
                console.error('Error:', error);
            }
        });
    });

    // Load available controllers from the backend
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

    // Handle Start Game button click
    $('#start-game').click(function() {
        $.ajax({
            url: '/api/generate-sequence',
            method: 'POST',
            success: function(response) {
                $('#info-display').html('<p>Game has started!</p>');
                startWinnerPolling();
            },
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


    // Function to fetch round winner via REST API
    function fetchRoundWinner() {
        $.ajax({
            url: '/api/round-winner',
            method: 'GET',
            success: function(response) {
                if (response.name && response.score !== undefined) {
                    $('#info-display').html(`<p>Round Winner: ${response.name} (Score: ${response.score})</p>`);
                } else if (response.message) {
                    $('#info-display').html(`<p>${response.message}</p>`);
                } else {
                    $('#info-display').html('<p>Waiting for round results...</p>');
                }
            },
            error: function(xhr, status, error) {
                $('#info-display').html('<p>Failed to fetch round winner.</p>');
                console.error('Error:', error);
            }
        });
    }

    let winnerPollingInterval;

function startWinnerPolling() {
    // Clear any existing interval
    if (winnerPollingInterval) {
        clearInterval(winnerPollingInterval);
    }
    
    // Start polling every 5 seconds
    winnerPollingInterval = setInterval(fetchRoundWinner, 5000);
}

function stopWinnerPolling() {
    if (winnerPollingInterval) {
        clearInterval(winnerPollingInterval);
    }
}


});

      
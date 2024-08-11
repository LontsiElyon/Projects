// JavaScript to interact with the Backend via REST and / or WebSockets
 // Document ready function
 /*$(document).ready(function() {
    // Fetch available controllers from the backend
    fetchControllers();

    // Handle form submission for player login
    $('#login-form').on('submit', function(event) {
        event.preventDefault();
        assignPlayerToController();
    });

    // Handle start game button click
    $('#start-game').on('click', function() {
        startGame();
    });
});

// Function to fetch available controllers
function fetchControllers() {
    $.ajax({
        url: '/api/controllers',
        method: 'GET',
        success: function(data) {
            populateControllerOptions(data);
        },
        error: function() {
            alert('Failed to load controllers. Please try again later.');
        }
    });
}

// Populate controller dropdown with options
function populateControllerOptions(controllers) {
    var controllerSelect = $('#controller');
    controllerSelect.empty();
    controllerSelect.append('<option value="">Select a controller</option>');
    controllers.forEach(function(controller) {
        controllerSelect.append('<option value="' + controller.controller_id + '">' + controller.controller_id + '</option>');
    });
}

// Assign player to a selected controller
function assignPlayerToController() {
    var username = $('#username').val();
    var controllerId = $('#controller').val();

    if (controllerId === "") {
        alert("Please select a controller.");
        return;
    }

    $.ajax({
        url: '/api/login',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            username: username,
            controllerId: controllerId
        }),
        success: function(response) {
            $('#login-result').html('<div class="alert alert-success">Player assigned successfully</div>');
            updateGameInfo(response);
        },
        error: function(xhr, status, error) {
            $('#login-result').html('<div class="alert alert-danger">Failed to assign player: ' + error + '</div>');
        }
    });
}

// Start the game
function startGame() {
    $.ajax({
        url: '/api/start-game',
        method: 'POST',
        success: function(response) {
            alert('Game started successfully!');
            updateGameInfo(response);
        },
        error: function() {
            alert('Failed to start the game.');
        }
    });
}

// Update game information display
function updateGameInfo(info) {
    var infoDisplay = $('#info-display');
    infoDisplay.empty();
    infoDisplay.append('<div class="player-info"><span>Player:</span> ' + info.player + '</div>');
    infoDisplay.append('<div class="player-info"><span>Controller:</span> ' + info.controller + '</div>');
    infoDisplay.append('<div class="player-info"><span>Status:</span> ' + info.status + '</div>');
}*/

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
            url: '/api/start-game',
            method: 'POST',
            success: function(response) {
                $('#info-display').html('<p>Game has started!</p>');
            },
            error: function(xhr, status, error) {
                $('#info-display').html('<p>Failed to start the game.</p>');
                console.error('Error:', error);
            }
        });
    });
});

/*const client = mqtt.connect('ws://broker.hivemq.com:8080/mqtt');

client.on('connect', function () {
    console.log('Connected to MQTT broker');
    client.subscribe('frontend/registration', function (err) {
        if (!err) {
            console.log('Subscribed to registration topic');
        }
    });
});

client.on('message', function (topic, message) {
    const data = JSON.parse(message.toString());

    if (data.action === 'register') {
        const rfidTag = data.rfidTag;
        promptUserForRegistration(rfidTag);
    }
});

function promptUserForRegistration(rfidTag) {
    // Show a prompt on the frontend to register the user
    const username = prompt(`Enter username for RFID tag ${rfidTag}:`);

    if (username) {
        // Send registration details to the backend
        registerUserWithRFID(rfidTag, username);
    }
}

function registerUserWithRFID(rfidTag, username) {
    // Send a request to your backend API to register the user
    fetch('/api/register', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ rfidTag, username }),
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            alert('User registered successfully!');
        } else {
            alert('Failed to register user: ' + data.error);
        }
    })
    .catch(error => {
        console.error('Error registering user:', error);
    });
}*/       
## Detailed Explanation

### Backend

The backend is a Java application built using Vert.x, a toolkit for building reactive applications on the JVM. It exposes a REST API to interact with the Simon game data stored in the MariaDB database.

#### Key Components

- **MainVerticle.java**: The main entry point of the backend application.
- **ObjectController.java**: Handles HTTP requests and routes them to the appropriate service.
- **ObjectService.java**: Contains the business logic for handling the Simon game data.
- **ObjectRepository.java**: Interacts with the MariaDB database to perform CRUD operations.

### Frontend

The frontend is a simple web application built with plain HTML and Bootstrap for styling. It interacts with the backend via AJAX requests to perform CRUD operations on the Simon game data.

#### Key Files

- **index.html**: The main HTML file for the frontend.
- **styles.css**: Custom CSS styles for the frontend.

### ESP32 Microcontroller

The ESP32 microcontroller is programmed to implement the Simon game. It sends game data via MQTT to the MQTT broker, which then forwards it to the backend application.

#### Key Files

- **main.cpp**: The main program file for the ESP32 microcontroller.

## Usage

### Testing the API

You can use tools like [Postman](https://www.postman.com/) or [curl](https://curl.se/) to test the API endpoints.

#### Create an Object

```bash
curl -X POST http://localhost:8080/api/create -d "message=your_message"
```

#### Get All Objects

```bash
curl http://localhost:8080/api/objects
```

#### Update an Object

```bash
curl -X PUT http://localhost:8080/api/update/1 -d "message=

updated_message"
```

#### Delete an Object

```bash
curl -X DELETE http://localhost:8080/api/delete/1
```

### Sending MQTT Messages

You can use tools like [MQTT Explorer](http://mqtt-explorer.com/) or `mosquitto_pub` to send MQTT messages.

```bash
mosquitto_pub -h localhost -t simon/game -m "your_message" -u "your_mqtt_username" -P "your_mqtt_password"
```

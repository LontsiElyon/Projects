#!/bin/sh

# Check if the password file exists
if [ ! -f /mosquitto/config/passwordfile ]; then
  # Generate the password file
  echo "Generating password file..."
  echo "Credentials: ${MQTT_USERNAME} / ${MQTT_PASSWORD}"
  touch /mosquitto/config/passwordfile
  mosquitto_passwd -b /mosquitto/config/passwordfile ${MQTT_USERNAME} ${MQTT_PASSWORD}
  echo "Password file generated."
else
  echo "Password file already exists."
fi


# Run the original Mosquitto command
echo "Starting Mosquitto..."
exec mosquitto -c /mosquitto/config/mosquitto.conf

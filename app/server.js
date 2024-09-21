const express = require('express');
const http = require('http');
const socketIo = require('socket.io');

// Initialize Express and HTTP server
const app = express();
const server = http.createServer(app);
const io = socketIo(server);

// Handle connection and location updates
io.on('connection', (socket) => {
    console.log('A user connected');

    socket.on('locationUpdate', (data) => {
        console.log(`Location Update: Latitude: ${data.latitude}, Longitude: ${data.longitude}`);
    });

    socket.on('disconnect', () => {
        console.log('User disconnected');
    });
});

server.listen(3000, () => {
    console.log('Server is running on port 3000');
});

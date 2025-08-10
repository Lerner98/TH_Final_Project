// services/authService.js - CLEAN VERSION
const express = require('express');
const cors = require('cors');
const path = require('path');
const dotenv = require('dotenv');
const authRoutes = require('../routes/AuthRoutes');

dotenv.config({ path: path.resolve(__dirname, '../.env') });

const app = express();
const port = 3001;

console.log('[Auth Service] Starting on port 3001...');

// EXACT same CORS and middleware as original
app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'DELETE', 'PUT', 'PATCH'],
  allowedHeaders: ['Content-Type', 'Authorization'],
}));
app.use(express.json({ limit: '50mb' }));

// EXACT same _method handler as original
app.use((req, res, next) => {
  if (req.method === 'POST' && req.query._method) {
    req.method = req.query._method.toUpperCase();
  }
  next();
});

// Use auth routes
app.use('/', authRoutes);

// Health check
app.get('/health', (req, res) => {
  res.json({ 
    status: 'OK', 
    timestamp: new Date().toISOString(),
    service: 'Auth Service'
  });
});

app.listen(port, '0.0.0.0', () => {
  console.log(`[Auth Service] Server running on port ${port}`);
});
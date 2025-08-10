// services/userDataService.js
const express = require('express');
const cors = require('cors');
const path = require('path');
const dotenv = require('dotenv');
const userDataRoutes = require('../routes/UserDataRoutes');

dotenv.config({ path: path.resolve(__dirname, '../.env') });

const app = express();
const port = 3003;

console.log('[User Data Service] Starting on port 3003...');

// CORS + body parsing
app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'DELETE', 'PUT', 'PATCH'],
  allowedHeaders: ['Content-Type', 'Authorization'],
}));
app.use(express.json({ limit: '50mb' }));

// 🔍 Per-request low-level logger (before anything else)
app.use((req, res, next) => {
  console.log(`[UDS] ${req.method} ${req.originalUrl}`);
  console.log(`[UDS] Headers:`, {
    host: req.headers.host,
    authorization: req.headers.authorization ? 'Bearer PRESENT' : 'NONE',
    'content-type': req.headers['content-type'],
    origin: req.headers.origin,
  });
  next();
});

// _method override (unchanged)
app.use((req, res, next) => {
  if (req.method === 'POST' && req.query._method) {
    req.method = req.query._method.toUpperCase();
  }
  next();
});

// Routes
app.use('/', userDataRoutes);

// Health check
app.get('/health', (req, res) => {
  res.json({
    status: 'OK',
    timestamp: new Date().toISOString(),
    service: 'User Data Service'
  });
});

// 404 handler with logging
app.use((req, res) => {
  console.log(`[UDS][404] No route matched: ${req.method} ${req.originalUrl}`);
  res.status(404).json({ error: 'Not found' });
});

// Error handler with logging
app.use((err, req, res, next) => {
  console.error('[UDS][ERR]', err?.message, err?.stack);
  res.status(500).json({ error: 'Internal Server Error' });
});

app.listen(port, '0.0.0.0', () => {
  console.log(`[User Data Service] Server running on port ${port}`);
});

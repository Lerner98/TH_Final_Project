/**
 * server.js - TranslationHub Main Application Entry Point
 * Bootstraps and starts all three microservices (Auth, Translation, User Data).
 * Loads environment variables, initializes services on different ports,
 * and provides graceful shutdown handling for the entire application.
 */

require('dotenv').config();

console.log('🚀 Starting TranslationHub...');

// Start all services
console.log('🔐 Starting Auth Service...');
require('./services/authService.js');

console.log('🌐 Starting Translation Service...');
require('./services/translation.js');

console.log('👤 Starting User Data Service...');
require('./services/userDataService.js');

console.log('\n✅ All services running!');
console.log('📱 React Native should connect to:');
console.log('   Auth: http://192.168.1.26:3001');
console.log('   Translation: http://192.168.1.26:3002'); 
console.log('   User Data: http://192.168.1.26:3003');
console.log('\nPress Ctrl+C to stop');

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('\n🛑 Stopping server...');
  process.exit(0);
});
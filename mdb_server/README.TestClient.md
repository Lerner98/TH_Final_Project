# 🧪 ERROR REPORTING SYSTEM DEMO

## 📋 **Complete Demo Guide**

This guide demonstrates the **React Native ↔ MongoDB Error Reporting System** integration between your Translation Hub app and MDB server.

---

## 🚀 **SETUP (Before Demo)**

### **1. Start MongoDB Server**
```bash
# Terminal 1
mongod --port 27017 --dbpath C:\data\db --auth
```

### **2. Start MDB Server**
```bash
# Terminal 2
cd C:\TH_Final_Test\mdb_server
node server.js
```
**Expected Output:** `MDB Server running on port 3005`

### **3. Start React Native App**
```bash
# Terminal 3
cd C:\TH_Final_Test\your-app-name
npx expo start
```

### **4. Open MongoDB Shell**
```bash
# Terminal 4
mongosh mongodb://adminUser:adminPass123@127.0.0.1:27017/admin
```

---

## 🎯 **DEMO STEPS**

### **Step 1: Show Current Database State**
```javascript
// In MongoDB shell
use translationhub
db.reports.find().sort({createdAt: -1}).limit(5)
```

**Demo Note:** "This shows existing error reports. We'll add a new one from the mobile app."

---

### **Step 2: Demonstrate Error Capture from Mobile App**

**Actions:**
1. **Open the React Native app** on your device/emulator
2. **Navigate to the Welcome screen** (should be the first screen you see)
3. **Wait 2 seconds** - automatic test will run
4. **Tap the orange "🧪 Test Error Reporting" button** manually

**Demo Note:** "The app just captured and sent an error report to our MongoDB database through our MDB server. Let's verify it was received."

---

### **Step 3: Verify Error Report in Database**
```javascript
// In MongoDB shell - Run this command immediately after the test
db.reports.find().sort({createdAt: -1}).limit(1)
```

**Expected Result:**
```javascript
{
  _id: ObjectId("..."),
  userId: "guest",
  type: "error", 
  message: "Manual test from Welcome screen",
  errorStack: "Error: Manual test from Welcome screen...",
  screen: "WelcomeScreen",
  platform: "android", 
  appVersion: "1.0.0",
  deviceInfo: {
    model: "Pixel 6",
    brand: "google", 
    os: "android 35",
    osVersion: "15",
    platform: "android",
    isDevice: true
  },
  extra: {
    timestamp: "2025-XX-XXTXX:XX:XX.XXXZ",
    errorType: "manual_test",
    isFatal: false,
    testSource: "welcome_button"
  },
  createdAt: ISODate("..."),
  updatedAt: ISODate("...")
}
```

---

### **Step 4: Show Error Report Details**

**Key Points to Highlight:**

1. **Real-time capture:** "The error was captured instantly from the mobile app"

2. **Rich device information:** 
   - Device model, OS version, platform
   - Real device vs emulator detection

3. **User context:** 
   - `userId: "guest"` - tracks if user is logged in or guest
   - `screen: "WelcomeScreen"` - knows exactly where error occurred

4. **Error details:**
   - Full error message and stack trace
   - Error type classification
   - Timestamp and source tracking

5. **Automatic data collection:**
   - No user interaction required
   - Happens silently in background

---

### **Step 5: Show Multiple Error Reports**
```javascript
// Show comparison of different error sources
db.reports.find({}, {
  userId: 1, 
  message: 1, 
  screen: 1, 
  platform: 1, 
  "deviceInfo.model": 1,
  createdAt: 1
}).sort({createdAt: -1}).limit(5)
```

**Demo Note:** "This shows errors from different sources - mobile app, server tests, etc. Each has different context data."

---

## 📊 **KEY TECHNICAL POINTS**

### **Architecture:**
- **React Native App** → **MDB Server (Port 3005)** → **MongoDB Database**
- **Secure API key authentication** between app and server
- **Separate microservice** for error reporting (doesn't affect main app)

### **Automatic Error Capture:**
- **Global error handlers** catch unhandled JavaScript errors
- **Promise rejection handlers** catch async errors  
- **Console error monitoring** for logged errors
- **User session tracking** (guest vs authenticated users)

### **Real-world Usage:**
- **Production error monitoring** - see what breaks in users' hands
- **Device-specific debugging** - identify problems on specific models
- **User experience tracking** - understand where users encounter issues
- **Proactive problem solving** - fix issues before users report them

---

## 🔧 **TROUBLESHOOTING**

### **If no error appears in database:**
```javascript
// Check if MDB server received the request
// Look at Terminal 2 (MDB server) for POST requests

// Check database connection
db.runCommand({ping: 1})

// Check collection exists
show collections
```

### **If app doesn't connect:**
- Verify IP address matches in Constants.js: `YOUR_IP:3005`
- Check API key matches between app and server
- Ensure MDB server is running and shows "Connected to MongoDB"

---

## ✅ **DEMO SUCCESS CRITERIA**

**Expected Results:**
1. ✅ Error generated from mobile app
2. ✅ Error immediately appears in MongoDB 
3. ✅ Rich contextual data captured automatically
4. ✅ System works transparently (no user disruption)
5. ✅ Professional logging with timestamps and device info

**Expected Demo Duration:** 3-5 minutes

---

## 🎯 **SYSTEM OVERVIEW**

*This demonstrates a production-ready error reporting system that can monitor our Translation Hub app in real-time, helping us identify and fix issues before they impact users. The system captures detailed device and context information automatically, making debugging and quality assurance much more effective.*
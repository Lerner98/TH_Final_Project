# 📱 React Native Client ↔ MDB Server Demo

## Quick Error Reporting Test for  Demo

This guide shows how to demonstrate the **real-time error reporting** from your React Native app to the MDB MongoDB database.

---

## 🎯 **Demo Overview**

Your React Native app automatically captures and sends error reports to your MDB server, which stores them in MongoDB with rich contextual data.

---

## 🚀 **Setup (Before Demo)**

### **Prerequisites:**
1. **MDB Server running** on port 3005
2. **MongoDB running** with `translationhub` database
3. **React Native app** running on device/emulator
4. **MongoDB shell** connected for verification

### **Quick Setup:**
```bash
# Terminal 1: Start MDB Server
cd C:\TH_Final_Test\mdb_server
node server.js

# Terminal 2: Start React Native App  
cd C:\TH_Final_Test\your-react-native-app
npx expo start

# Terminal 3: MongoDB Shell
mongosh mongodb://adminUser:adminPass123@127.0.0.1:27017/admin
```

---

## 🧪 **Demo Steps**

### **Step 1: Show Current Database State**
```javascript
use translationhub
db.reports.find().sort({createdAt: -1}).limit(3)
```

### **Step 2: Demonstrate Error Capture**

**In the React Native app:**
1. **Navigate to Welcome Screen** (first screen)
2. **Wait 2 seconds** - automatic test runs (optional)
3. **Tap the orange "🧪 Test Error Reporting" button**

**Watch the console logs:**
```
🧪 Testing error reporting manually...
[ErrorReporting] Sending report: {"message": "Manual test from Welcome screen", "screen": "WelcomeScreen", "type": "error"}
✅ Error report sent
[ErrorReporting] Error reported successfully
```

### **Step 3: Verify in MongoDB Instantly**
```javascript
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
  platform: "android", // or "ios"
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
  createdAt: ISODate("just now"),
  updatedAt: ISODate("just now")
}
```

---

## 🎯 **Key Points to Highlight**

### **1. Real-Time Capture**
- **Instant reporting:** Error appears in database within 1-2 seconds
- **No user disruption:** Happens silently in background

### **2. Rich Contextual Data**
- **Device Information:** Model, OS, brand, real device vs emulator
- **User Context:** Guest vs authenticated user tracking
- **Screen Context:** Exact screen where error occurred
- **Error Details:** Full stack trace and error classification
- **Timestamps:** Precise timing for debugging

### **3. Automatic vs Manual**
- **Automatic:** Captures real errors without any code changes
- **Manual:** Button for demonstration purposes
- **Production Ready:** Same system handles real app crashes

### **4. Scalable Architecture**
- **Separate Service:** MDB server handles only error reporting
- **API Security:** Protected with API keys
- **Database Flexibility:** MongoDB stores any error structure

---

## 🔍 **Additional Verification Commands**

### **Count total reports:**
```javascript
db.reports.count()
```

### **View reports by user:**
```javascript
db.reports.find({userId: "guest"}).sort({createdAt: -1})
```

### **View reports by screen:**
```javascript
db.reports.find({screen: "WelcomeScreen"}).sort({createdAt: -1})
```

### **View only manual test reports:**
```javascript
db.reports.find({"extra.testSource": "welcome_button"}).sort({createdAt: -1})
```

---

## ✅ **Success Criteria for Demo**

The demo is successful when you can show:

1. ✅ **Mobile app button click**
2. ✅ **Console logs showing success**  
3. ✅ **New report in MongoDB**
4. ✅ **Rich device and context data**
5. ✅ **Precise timestamps matching button press**

---

## 🎓 ** Demo Script**

*"This demonstrates our production-ready error reporting system. When users encounter errors in our Translation Hub app, the system automatically captures detailed information and sends it to our MongoDB database for analysis."*

1. **Show the button** → "I'll simulate an error report"
2. **Click button** → "The app just sent an error report"
3. **Show database** → "And here it is in our database instantly"
4. **Highlight data** → "Notice the rich device information, timestamps, and context"
5. **Explain benefits** → "This helps us identify and fix issues before users report them"

**Demo Duration:** 2-3 minutes

---

## 🚨 **Troubleshooting**

**If no report appears:**
- Check MDB server is running (port 3005)
- Verify API key in Constants.js matches server .env
- Ensure device can reach server IP: `192.168.1.26:3005`
- Check React Native console for error messages

**If demo fails:**
- Have backup: show existing reports in database
- Explain the system works (show previous test results)
- Mention this is a live system handling real errors
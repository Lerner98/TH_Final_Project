# 🧪 Test Runner Guide

## Step-by-Step Testing Instructions

### **Step 1: Navigate to MDB Server Directory**
```bash
cd C:\TH_Final_Test\mdb_server
```

### **Step 2: Run the Test Script**
```bash
node test/test-runner.js
```

**Expected Output:**
```
🧪 Starting MDB Server Tests...
📡 Base URL: http://localhost:3005
🔑 API Key: Found

🧪 Testing: Error Report Submission
   ✅ Error report submitted successfully
✅ PASSED: Error Report Submission

🧪 Testing: Get Reports
   ✅ Retrieved X reports
   ✅ Found our test report: "Test error from Node.js script"
✅ PASSED: Get Reports

🧪 Testing: Invalid API Key Rejection
   ✅ Correctly rejected invalid API key (403)
✅ PASSED: Invalid API Key Rejection

🧪 Testing: Missing Message Field Rejection
   ✅ Correctly rejected missing message field (400)
✅ PASSED: Missing Message Field Rejection

🧪 Testing: Feedback Report Submission
   ✅ Feedback report submitted
✅ PASSED: Feedback Report Submission

==================================================
🎯 TEST SUMMARY
==================================================
✅ Tests Passed: 5
❌ Tests Failed: 0
📊 Success Rate: 100%

🎉 ALL TESTS PASSED! Your MDB server is working perfectly!
```

### **Step 3: Verify Results in MongoDB**

**Connect to MongoDB:**
```bash
mongosh mongodb://adminUser:adminPass123@127.0.0.1:27017/admin
```

**Switch to the database:**
```javascript
use translationhub
```

**See all test reports from the script:**
```javascript
db.reports.find({"extra.testSource": "test-runner.js"}).sort({createdAt: -1})
```

**See latest 3 reports:**
```javascript
db.reports.find().sort({createdAt: -1}).limit(3)
```

**Count total reports:**
```javascript
db.reports.count()
```

## ✅ Success Indicators

- **Test script shows:** `✅ Tests Passed: 5, ❌ Tests Failed: 0`
- **MongoDB shows:** New reports with `testSource: "test-runner.js"`
- **Reports contain:** Proper device info, error stacks, timestamps
- **Different report types:** Both "error" and "feedback" types created

---

**🎯 This confirms your MDB server error reporting system is fully functional!**
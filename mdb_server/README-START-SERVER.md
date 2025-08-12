# 🧪 Quick Demo Steps

## Terminal Setup
```bash
# Terminal 1
mongod --port 27017 --dbpath C:\data\db --auth

# Terminal 2  
cd C:\TH_Final_Test\mdb_server
node server.js

# Terminal 3
cd C:\TH_Final_Test\your-app-name
npx expo start

# Terminal 4
mongosh mongodb://adminUser:adminPass123@127.0.0.1:27017/admin
```

## Demo
1. Open React Native app
2. Tap orange "🧪 Test Error Reporting" button (in welcome screen)

3. In MongoDB shell:
```javascript
use translationhub
db.reports.find().sort({createdAt: -1}).limit(1)
```

**Done.** Error report should show here after the reports.find commanad.
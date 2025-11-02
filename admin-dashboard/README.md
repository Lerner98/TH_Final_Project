# An Admin designed Dashboard that showcases Error logs from the React Native mobile app.

Flow of actions: Global Error logging on clien side catches errors => sends them through a designated API to the MongoDB server,
MongoDB Server authenticates the information using a speical key and then updates the DB with the relevant data.
Once the data is updated inside the database it then shows in the dashboard with all the relevant details including error stack, message, date and location.

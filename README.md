Madcow bids generator + backend + Ethereum pusher. 
Frontend code contained in another repo. Pushed to frontend are made with PubNub.

It is SBT project. To run - `sbt run`.
Frontend part - `npm run dev` with frontend repo clone.

Components

1) Bid generator. Generates pseudo-random buy/sell bids. Implementation uses sinus function to generate general 'trend' in bids and random dispersion for making buy/sell bid clusters around this sinus. 
Implemented as akka actor. 
Sends bids by HTTP (currently to the same machine)

2) Controller. Accepts random bids and runs logic which determine if deal should be made or if bid should be saved to DB or both (i.e. sell/buy part of qty and store other part for future trading).
a) Buy deals stored in PriorityQueue sorted by price decreasing order. This way, buy deal with highest price takes priority. However, if sell deal that came to the system have less price than buy deal, deal will be generated for sell bid price (this way no one loses nothing). If qty of buy deal isn't enough, next deal is taken from queue and checked with same logic, until quantity is covered or final quantity is stored to buy deals queue. 
b) Sell deals stored in db have almost the same logic except queue is sorted in increasing order, therefore most cheap sells have a priority.

c) Database is emulated as two in-memory priority queues

2.1) Controller reports to PubNub
a) Bid that is saved in DB
b) Deals made
a.2) Bid that is taken from db, decreased in quantity and saved back.

All reports are handled by Redux reducers in frontend component.

3) Controller reports to Ethereum blockchain through a Contract. 
This part isn't fully done. I only create a contract (not saving it id for future re-boots) inside local blockchain (by my understanding) and then write into it Int, Int pairs which represents Timestamp and Total Volume. Int/Int choice was made for simplicity and won't obviously be enough for production env. Test blockchain facade didn't worked for me due to some Spring issue I don't have time to debug.

4) Frontend
React+redux, subscribes to Controller events through PubNub. Nothing fancy otherwise.



Deploy currently just git pull from my VPS. Frontend served with nginx. Backend is Scala app.

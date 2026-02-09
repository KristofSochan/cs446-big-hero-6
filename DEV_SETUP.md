# Development Setup

## Project Setup (One-time, First Developer)

1. Create Firebase project at https://console.firebase.google.com/
2. Enable: Firestore Database, Cloud Functions, Cloud Messaging
3. Install CLI: `npm install -g firebase-tools`
4. Login: `firebase login`
5. Initialize: `firebase init` (select Functions + Firestore, TypeScript)
6. Set project: `firebase use YOUR_PROJECT_ID`

## Developer Setup (Each Developer)

1. Install CLI: `npm install -g firebase-tools`
2. Login: `firebase login`
3. Set project: `firebase use YOUR_PROJECT_ID`
4. Install deps: `cd functions && npm install`
5. Build: `cd functions && npm run build`

## Deploy Functions

```bash
cd functions
npm run lint -- --fix

npm run build
npm run deploy
```

## Test Locally

```bash
cd functions
npm run serve
```

## Android Studio Tips

### Evaluate Expression

#### Example: Manually add your user to a waitlist

1. Set a breakpoint in HomeScreen (line 22)
2. Run the app in debug mode (Bug icon)
3. When breakpoint hints, open "Evaluate Expression" window
4. navController.navigate("station/8dK92iAsn0ALwZ1R9iT7")

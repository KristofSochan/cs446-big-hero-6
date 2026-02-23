# Code Structure

Simple overview of how the app is organized.

## Directory Structure

```
app/src/main/java/ca/uwaterloo/cs446/bighero6/
├── data/              # Data models (Station, User, Attendee, etc.)
├── repository/        # Firestore operations (FirestoreRepository)
├── viewmodel/         # Business logic (StationViewModel, HomeViewModel)
├── ui/screens/        # UI screens (Compose)
├── navigation/        # Navigation setup
└── util/              # Utilities (DeviceIdManager)
```

## How It Works

1. **Screens** (`ui/screens/`) - Display UI, handle user input
2. **ViewModels** (`viewmodel/`) - Business logic, state management
3. **Repository** (`repository/`) - Database operations (Firestore)
4. **Data Models** (`data/`) - Data structures

## Flow Example: Joining Waitlist

```
User clicks "Join" button
    ↓
StationInfoScreen calls viewModel.joinWaitlist()
    ↓
StationViewModel calls repository.addToWaitlist()
    ↓
FirestoreRepository updates Firestore
    ↓
ViewModel updates state
    ↓
Screen updates UI
```

## Key Files

- `FirestoreRepository.kt` - All database operations
- `StationViewModel.kt` - Station/waitlist logic
- `HomeViewModel.kt` - Home screen logic
- `NavGraph.kt` - Screen navigation

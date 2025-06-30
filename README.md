# Wizard-Duel-Game

## Overview
Wizard Duel is a two-player mobile game developed for Android using Java and Firebase. Players engage in a magical duel, casting spells (attack, shield, heal) by performing specific phone gestures detected via the device's accelerometer. The game features real-time multiplayer functionality, a 90-second timer, and a Firebase backend for synchronizing game state between players.
Features

Real-Time Multiplayer: Two players can join a game session via Firebase, with one acting as Player 1 and the other as Player 2.
Gesture-Based Spell Casting:
Attack: Forward-back motion (Z-axis) deals 20 damage to the opponent unless blocked by a shield.
Shield: Horizontal motion (X-axis) blocks the next attack.
Heal: Vertical motion (Y-axis) restores 20 health to the player.


Cooldown System: Spells have cooldowns (Attack: 3s, Shield: 4s, Heal: 5s) with a 1-second global cooldown between casts.
Health System: Each player starts with 100 health. The game ends when a player's health reaches 0 or the timer expires.
Game Timer: Matches last 90 seconds, with the winner determined by remaining health or a draw if health is equal.
Sound Effects: Includes background music, spell sound effects (attack, shield, heal), and win/lose sounds.
UI Feedback: Displays health bars, spell status icons, cooldown timers, and game timer.

## Project Structure

MainActivity.java: Handles the game lobby, player joining, and Firebase initialization. Players press a "Start Game" button to join or create a game session.
GameActivity.java: Manages the core gameplay, including accelerometer-based spell casting, Firebase synchronization, and game state updates (health, cooldowns, spells).
GameOverActivity.java: Displays the game result (win, lose, or draw) with appropriate visuals and sound effects, and allows players to return to the main menu.
activity_main.xml: Layout for the main screen with a start button and waiting text for Player 2.
activity_game.xml: Layout for the game screen, showing health bars, spell status icons, cooldown timers, and game timer.
activity_game_over.xml: Layout for the game over screen, displaying the result and an end game button.
AndroidManifest.xml: Configures the app's activities and permissions.

## Prerequisites

Android Studio: To build and run the app.
Firebase Account: For real-time database functionality.
Android Device/Emulator: With accelerometer support for gesture detection.
Dependencies:
Firebase Realtime Database (com.google.firebase:firebase-database)
Android Support Library for AppCompatActivity
Resources for sound effects (e.g., R.raw.win, R.raw.lose, R.raw.attack, etc.) and images (e.g., R.drawable.happy_wizard, R.drawable.sad_wizard, etc.)



## Setup Instructions

Clone the Repository:git clone <repository-url>


Set Up Firebase:
Create a Firebase project at console.firebase.google.com.
Add an Android app to your Firebase project and download the google-services.json file.
Place google-services.json in the app/ directory of the project.
Enable the Realtime Database in Firebase and set up rules to allow read/write access for testing:{
  "rules": {
    ".read": "true",
    ".write": "true"
  }
}




Add Resources:
Place sound files (e.g., win.mp3, lose.mp3, attack.mp3, shield_activate.mp3, shield_block.mp3, heal.mp3, background_tune.mp3) in the res/raw/ directory.
Place image files (e.g., happy_wizard.png, sad_wizard.png, attack_icon.png, shield_icon.png, heal_icon.png, default_icon.png) in the res/drawable/ directory.


Build and Run:
Open the project in Android Studio.
Sync the project with Gradle.
Connect an Android device or use an emulator with accelerometer support.
Build and run the app.



## How to Play

Launch the app and press the "Start Game" button.
Player 1: Creates a game session and waits for Player 2 to join (displays "Waiting for Player 2 to join...").
Player 2: Joins the existing game session, starting the match.
During the game:
Perform gestures to cast spells:
Attack: Shake the phone forward and back.
Shield: Move the phone side to side.
Heal: Move the phone up and down.


Monitor health bars, spell cooldowns, and the game timer.


The game ends when a player's health reaches 0 or the 90-second timer expires.
The game over screen shows the result (win, lose, or draw) with a wizard image and sound effect.
Press the "End Game" button to return to the main menu.

## Notes

The game uses a single game ID (test_game) for simplicity. For production, implement dynamic game IDs for multiple simultaneous matches.
Ensure both players have a stable internet connection for Firebase synchronization.
The accelerometer sensitivity (GESTURE_THRESHOLD) is set to 15.0f and may need adjustment for different devices.
Sound and image resources must be provided in the res/ directory as specified.

## Future Improvements

Add dynamic game ID generation for multiple concurrent games.
Implement player authentication for secure matchmaking.
Enhance UI with animations for spell effects.
Add support for different difficulty levels or spell variations.
Improve error handling for network issues and Firebase connectivity.

## Authors
Danielle Joseph
Nicole Joseph

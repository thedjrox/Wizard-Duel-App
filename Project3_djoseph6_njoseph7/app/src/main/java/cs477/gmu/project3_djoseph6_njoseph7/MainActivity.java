package cs477.gmu.project3_djoseph6_njoseph7;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private DatabaseReference gameRef;
    private String playerId, gameId;
    private boolean isPlayer1 = false;
    private Button startButton;
    private TextView waitingText;
    private ValueEventListener player2JoinListener;
    private MediaPlayer backgroundTune;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        startButton = findViewById(R.id.start_button);
        waitingText = findViewById(R.id.waiting_text);
        waitingText.setVisibility(TextView.GONE); // Initially hidden

        // Initialize background tune
        backgroundTune = MediaPlayer.create(this, R.raw.background_tune);
        if (backgroundTune != null) {
            backgroundTune.setLooping(true); // Set to loop
            backgroundTune.start(); // Start playing
        }

        // Initialize Firebase
        playerId = "player_" + System.currentTimeMillis();
        gameId = "test_game";
        gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);

        // Clear the game session on start
        gameRef.removeValue().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(MainActivity.this, "Failed to clear game session", Toast.LENGTH_SHORT).show();
            }
        });

        // Test Firebase connectivity
        FirebaseDatabase.getInstance().getReference("test").setValue("Hello, Firebase!")
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(MainActivity.this, "Failed to connect to Firebase :(", Toast.LENGTH_SHORT).show();
                    }
                });

        // Set up button to start the game
        startButton.setOnClickListener(v -> startGame());
    }

    private void startGame() {
        startButton.setEnabled(false);

        // Check if player1 exists in the game session
        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists() || snapshot.child("player1").getValue(String.class) == null) {
                    // This is Player 1
                    isPlayer1 = true;
                    Map<String, Object> gameState = new HashMap<>();
                    gameState.put("player1", playerId);
                    gameState.put("player1_health", 100);
                    gameState.put("player2_health", 100);
                    gameState.put("player1_attack_cooldown", 0L);
                    gameState.put("player1_shield_cooldown", 0L);
                    gameState.put("player1_heal_cooldown", 0L);
                    gameState.put("player2_attack_cooldown", 0L);
                    gameState.put("player2_shield_cooldown", 0L);
                    gameState.put("player2_heal_cooldown", 0L);
                    gameState.put("player1_spell", "none");
                    gameState.put("player2_spell", "none");
                    gameState.put("player1_shield", false);
                    gameState.put("player2_shield", false);
                    gameState.put("gameStarted", false);
                    gameState.put("game_start_time", 0L); // Will be set when Player 2 joins
                    gameState.put("last_updated", System.currentTimeMillis());
                    gameRef.setValue(gameState);

                    // Show waiting message for Player 1
                    waitingText.setVisibility(TextView.VISIBLE);
                    waitingText.setText("Waiting for Player 2 to join...");

                    // Listen for Player 2 joining
                    listenForPlayer2();
                } else if (snapshot.child("player2").getValue(String.class) == null) {
                    // This is Player 2
                    isPlayer1 = false;
                    //long currentTime = System.currentTimeMillis();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("player2", playerId);
                    updates.put("gameStarted", true);
                    updates.put("game_start_time", ServerValue.TIMESTAMP); // Use server timestamp
                    updates.put("last_updated", System.currentTimeMillis());
                    gameRef.updateChildren(updates);
                    Toast.makeText(MainActivity.this, "Player 2 Joined!", Toast.LENGTH_SHORT).show();

                    // Navigate to GameActivity for Player 2
                    goToGameActivity();
                } else {
                    // Check if the session is stale
                    Long lastUpdated = snapshot.child("last_updated").getValue(Long.class);
                    long currentTime = System.currentTimeMillis();
                    if (lastUpdated != null && (currentTime - lastUpdated) > 30_000) {
                        isPlayer1 = true;
                        Map<String, Object> gameState = new HashMap<>();
                        gameState.put("player1", playerId);
                        gameState.put("player1_health", 100);
                        gameState.put("player2_health", 100);
                        gameState.put("player1_attack_cooldown", 0L);
                        gameState.put("player1_shield_cooldown", 0L);
                        gameState.put("player1_heal_cooldown", 0L);
                        gameState.put("player2_attack_cooldown", 0L);
                        gameState.put("player2_shield_cooldown", 0L);
                        gameState.put("player2_heal_cooldown", 0L);
                        gameState.put("player1_spell", "none");
                        gameState.put("player2_spell", "none");
                        gameState.put("player1_shield", false);
                        gameState.put("player2_shield", false);
                        gameState.put("gameStarted", false);
                        gameState.put("game_start_time", 0L);
                        gameState.put("last_updated", currentTime);
                        gameRef.setValue(gameState);

                        // Show waiting message for Player 1
                        waitingText.setVisibility(TextView.VISIBLE);
                        waitingText.setText("Waiting for Player 2 to join...");

                        // Listen for Player 2 joining
                        listenForPlayer2();
                    } else {
                        Toast.makeText(MainActivity.this, "Game is full!", Toast.LENGTH_LONG).show();
                        startButton.setEnabled(true);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MainActivity.this, "Firebase Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                startButton.setEnabled(true);
            }
        });
    }

    private void listenForPlayer2() {
        player2JoinListener = gameRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String player2Id = snapshot.child("player2").getValue(String.class);
                    Boolean gameStarted = snapshot.child("gameStarted").getValue(Boolean.class);
                    if (player2Id != null && gameStarted != null && gameStarted) {
                        // Player 2 has joined, go to GameActivity
                        gameRef.removeEventListener(this);
                        goToGameActivity();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MainActivity.this, "Firebase Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                startButton.setEnabled(true);
                waitingText.setVisibility(TextView.GONE);
            }
        });
    }

    private void goToGameActivity() {
        Intent intent = new Intent(MainActivity.this, GameActivity.class);
        intent.putExtra("playerId", playerId);
        intent.putExtra("gameId", gameId);
        intent.putExtra("isPlayer1", isPlayer1);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-enable the Start Game button and hide waiting text
        startButton.setEnabled(true);
        waitingText.setVisibility(TextView.GONE);

        // Resume background tune
        if (backgroundTune != null && !backgroundTune.isPlaying()) {
            backgroundTune.start();
        }

        // Reset playerId for a new game session
        playerId = "player_" + System.currentTimeMillis();
        gameId = "test_game";
        gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);

        // Clear the game session again to ensure a fresh start
        gameRef.removeValue().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(MainActivity.this, "Failed to clear game session", Toast.LENGTH_SHORT).show();
            }
        });

        // Remove any existing listener
        if (player2JoinListener != null) {
            gameRef.removeEventListener(player2JoinListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause background tune
        if (backgroundTune != null && backgroundTune.isPlaying()) {
            backgroundTune.pause();
        }

        // Remove listener when activity is paused
        if (player2JoinListener != null) {
            gameRef.removeEventListener(player2JoinListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release background tune resources
        if (backgroundTune != null) {
            backgroundTune.stop();
            backgroundTune.release();
            backgroundTune = null;
        }

        // Remove listener if it exists
        if (player2JoinListener != null) {
            gameRef.removeEventListener(player2JoinListener);
        }
    }
}
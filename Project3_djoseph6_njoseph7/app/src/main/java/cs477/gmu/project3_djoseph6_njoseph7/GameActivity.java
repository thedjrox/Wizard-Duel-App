package cs477.gmu.project3_djoseph6_njoseph7;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class GameActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ProgressBar playerHealthBar, opponentHealthBar;
    private ImageView spellStatusIcon, opponentSpellStatusIcon;
    private TextView p_health, op_health, spellKeyText, attackCooldownText, shieldCooldownText, healCooldownText, timerText;
    private DatabaseReference gameRef;
    private DatabaseReference serverTimeOffsetRef;
    private String playerId, gameId;
    private float lastX, lastY, lastZ;
    private static final float GESTURE_THRESHOLD = 15.0f;
    private int playerHealth = 100, opponentHealth = 100;
    private boolean shieldActive = false;
    private boolean isPlayer1 = false;
    private boolean gameStarted = true;
    private long lastCastTime = 0;
    private static final long GLOBAL_COOLDOWN = 1000; // 1s between any spell casts
    private static final long GAME_DURATION_MS = 90_000; // 90 seconds
    private MediaPlayer shieldActivateSound, shieldBlockSound, attackSound, healSound;
    private long lastUpdateTimestamp = 0;
    private long gameStartTime = 0;
    private long attackCooldownTimestamp = 0;
    private long shieldCooldownTimestamp = 0;
    private long healCooldownTimestamp = 0;
    private long serverTimeOffset = 0; // Offset between local time and server time, got from Firebase
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!gameStarted) {
                timerHandler.removeCallbacks(this);
                return;
            }

            long currentLocalTime = System.currentTimeMillis();
            // Estimate current server time for the game timer
            long currentServerTime = currentLocalTime + serverTimeOffset;

            // Update game timer using server time
            if (gameStartTime > 0) {
                long elapsedTime = currentServerTime - gameStartTime;
                long remainingTime = Math.max(GAME_DURATION_MS - elapsedTime, 0);
                timerText.setText(String.format("Time: %.1fs", remainingTime / 1000.0));
                if (remainingTime <= 0) {
                    endGame();
                    return;
                }
            }

            // Update cooldown timers using local time
            long attackCooldownMs = attackCooldownTimestamp > currentLocalTime ?
                    attackCooldownTimestamp - currentLocalTime : 0;
            long shieldCooldownMs = shieldCooldownTimestamp > currentLocalTime ?
                    shieldCooldownTimestamp - currentLocalTime : 0;
            long healCooldownMs = healCooldownTimestamp > currentLocalTime ?
                    healCooldownTimestamp - currentLocalTime : 0;

            attackCooldownText.setText(String.format("%.1fs", attackCooldownMs / 1000.0));
            shieldCooldownText.setText(String.format("%.1fs", shieldCooldownMs / 1000.0));
            healCooldownText.setText(String.format("%.1fs", healCooldownMs / 1000.0));

            // Schedule the next update
            timerHandler.postDelayed(this, 100); // Update every 100ms
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // Initialize UI components
        playerHealthBar = findViewById(R.id.player_health_bar);
        opponentHealthBar = findViewById(R.id.opponent_health_bar);
        spellStatusIcon = findViewById(R.id.spell_status_icon);
        opponentSpellStatusIcon = findViewById(R.id.opponent_spell_status_icon);
        p_health = findViewById(R.id.player_health_value);
        op_health = findViewById(R.id.opponent_health_value);
        spellKeyText = findViewById(R.id.spell_key_text);
        attackCooldownText = findViewById(R.id.attack_cooldown_text);
        shieldCooldownText = findViewById(R.id.shield_cooldown_text);
        healCooldownText = findViewById(R.id.heal_cooldown_text);
        timerText = findViewById(R.id.timer_text);

        // Update spell key text to show updated cooldowns
        spellKeyText.setText("Spell Cooldowns:\nAttack: 3s\nShield: 4s\nHeal: 5s");

        // Initialize sound effects
        shieldActivateSound = MediaPlayer.create(this, R.raw.shield_activate);
        shieldBlockSound = MediaPlayer.create(this, R.raw.shield_block);
        attackSound = MediaPlayer.create(this, R.raw.attack);
        healSound = MediaPlayer.create(this, R.raw.heal);

        // Get data from Intent
        playerId = getIntent().getStringExtra("playerId");
        gameId = getIntent().getStringExtra("gameId");
        isPlayer1 = getIntent().getBooleanExtra("isPlayer1", false);

        // Log to verify isPlayer1
        Log.d("GameActivity", "onCreate: isPlayer1=" + isPlayer1 + ", playerId=" + playerId);

        // Initialize accelerometer
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        } else {
            Log.e("Sensor", "Accelerometer not available on this device");
            Toast.makeText(this, "Accelerometer not available", Toast.LENGTH_LONG).show();
        }

        // Initialize Firebase
        gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);
        serverTimeOffsetRef = FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset");

        // Listen for server time offset
        serverTimeOffsetRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Long offset = snapshot.getValue(Long.class);
                if (offset != null) {
                    serverTimeOffset = offset;
                    Log.d("GameActivity", "Server time offset updated: " + serverTimeOffset + "ms");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("Firebase", "Failed to get server time offset: " + error.getMessage());
            }
        });

        // Start timer and listen for game updates
        setupFirebaseListener();
    }

    private void setupFirebaseListener() {
        gameRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.e("Firebase", "Game snapshot does not exist");
                    return;
                }

                // Check last_updated timestamp to avoid reprocessing
                Long currentTimestamp = snapshot.child("last_updated").getValue(Long.class);
                if (currentTimestamp == null || currentTimestamp <= lastUpdateTimestamp) {
                    Log.d("Firebase", "Ignoring stale or duplicate update: timestamp=" + currentTimestamp);
                    return;
                }
                lastUpdateTimestamp = currentTimestamp;

                String player1Id = snapshot.child("player1").getValue(String.class);
                String player2Id = snapshot.child("player2").getValue(String.class);
                if (player1Id == null || player2Id == null) {
                    Log.e("Firebase", "Player ID is null: player1=" + player1Id + ", player2=" + player2Id);
                    return;
                }

                // Update health
                String opponentKey = isPlayer1 ? "player2_health" : "player1_health";
                String playerKey = isPlayer1 ? "player1_health" : "player2_health";
                Integer opponentHealthValue = snapshot.child(opponentKey).getValue(Integer.class);
                Integer playerHealthValue = snapshot.child(playerKey).getValue(Integer.class);
                opponentHealth = opponentHealthValue != null ? opponentHealthValue : 100;
                playerHealth = playerHealthValue != null ? playerHealthValue : 100;
                opponentHealthBar.setProgress(opponentHealth);
                playerHealthBar.setProgress(playerHealth);
                op_health.setText(opponentHealth + "/100");
                p_health.setText(playerHealth + "/100");

                // Check for game end condition (health = 0)
                if (playerHealth <= 0 || opponentHealth <= 0) {
                    endGame();
                    return;
                }

                // Update shield status
                String playerShieldKey = isPlayer1 ? "player1_shield" : "player2_shield";
                Boolean playerShieldActive = snapshot.child(playerShieldKey).getValue(Boolean.class);
                shieldActive = playerShieldActive != null && playerShieldActive;

                // Update opponent spell icon
                String opponentSpellKey = isPlayer1 ? "player2_spell" : "player1_spell";
                String opponentSpell = snapshot.child(opponentSpellKey).getValue(String.class);
                if (opponentSpell != null && !opponentSpell.equals("none")) {
                    Log.d("Firebase", "Processing opponent spell: " + opponentSpell);
                    updateOpponentSpellStatus(opponentSpell);
                    // Clear opponent spell to prevent reprocessing
                    Map<String, Object> updates = new HashMap<>();
                    updates.put(opponentSpellKey, "none");
                    updates.put("last_updated", System.currentTimeMillis());
                    gameRef.updateChildren(updates).addOnFailureListener(e -> {
                        Log.e("Firebase", "Failed to clear opponent spell: " + e.getMessage());
                    });
                }

                // Update game start time
                Object gameStartTimeValue = snapshot.child("game_start_time").getValue();
                if (gameStartTimeValue != null && gameStartTimeValue instanceof Long) {
                    gameStartTime = (Long) gameStartTimeValue;
                    if (gameStartTime > 0 && !timerHandler.hasCallbacks(timerRunnable)) {
                        // Start the real-time timer updates
                        timerHandler.post(timerRunnable);
                    }
                }

                // Update cooldown timestamps
                String attackCooldownKey = isPlayer1 ? "player1_attack_cooldown" : "player2_attack_cooldown";
                String shieldCooldownKey = isPlayer1 ? "player1_shield_cooldown" : "player2_shield_cooldown";
                String healCooldownKey = isPlayer1 ? "player1_heal_cooldown" : "player2_heal_cooldown";

                Long attackCooldown = snapshot.child(attackCooldownKey).getValue(Long.class);
                Long shieldCooldown = snapshot.child(shieldCooldownKey).getValue(Long.class);
                Long healCooldown = snapshot.child(healCooldownKey).getValue(Long.class);

                attackCooldownTimestamp = attackCooldown != null ? attackCooldown : 0;
                shieldCooldownTimestamp = shieldCooldown != null ? shieldCooldown : 0;
                healCooldownTimestamp = healCooldown != null ? healCooldown : 0;

                // Initial update of cooldowns (subsequent updates handled by timerRunnable)
                long currentTime = System.currentTimeMillis();
                long attackCooldownMs = attackCooldownTimestamp > currentTime ?
                        attackCooldownTimestamp - currentTime : 0;
                long shieldCooldownMs = shieldCooldownTimestamp > currentTime ?
                        shieldCooldownTimestamp - currentTime : 0;
                long healCooldownMs = healCooldownTimestamp > currentTime ?
                        healCooldownTimestamp - currentTime : 0;

                attackCooldownText.setText(String.format("%.1fs", attackCooldownMs / 1000.0));
                shieldCooldownText.setText(String.format("%.1fs", shieldCooldownMs / 1000.0));
                healCooldownText.setText(String.format("%.1fs", healCooldownMs / 1000.0));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(GameActivity.this, "Firebase Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("Firebase", "Listener cancelled: " + error.getMessage());
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!gameStarted || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // Check global cooldown
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCastTime < GLOBAL_COOLDOWN) {
            Log.d("GameActivity", "Ignoring sensor event: within global cooldown");
            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float deltaX = Math.abs(x - lastX);
        float deltaY = Math.abs(y - lastY);
        float deltaZ = Math.abs(z - lastZ);

        String spell = "none";
        // Attack: Forward-back motion (Z-axis)
        if (deltaZ > GESTURE_THRESHOLD && deltaZ > deltaX && deltaZ > deltaY) {
            spell = "attack";
        }
        // Shield: Horizontal motion (X-axis)
        else if (deltaX > GESTURE_THRESHOLD && deltaX > deltaY && deltaX > deltaZ) {
            spell = "shield";
        }
        // Heal: Vertical motion (Y-axis)
        else if (deltaY > GESTURE_THRESHOLD && deltaY > deltaX && deltaY > deltaZ) {
            spell = "heal";
        }

        if (!spell.equals("none")) {
            Log.d("GameActivity", "Detected spell: " + spell + ", isPlayer1=" + isPlayer1);
            castSpell(spell);
            lastCastTime = currentTime;
        }

        lastX = x;
        lastY = y;
        lastZ = z;
    }

    private long getCooldownDuration(String spell) {
        switch (spell) {
            case "attack":
                return 3000; // 3s
            case "shield":
                return 4000; // 4s
            case "heal":
                return 5000; // 5s
            default:
                return 0;
        }
    }

    private void castSpell(String spell) {
        String playerSpellKey = isPlayer1 ? "player1_spell" : "player2_spell";
        String opponentHealthKey = isPlayer1 ? "player2_health" : "player1_health";
        String playerHealthKey = isPlayer1 ? "player1_health" : "player2_health";
        String opponentShieldKey = isPlayer1 ? "player2_shield" : "player1_shield";
        String playerShieldKey = isPlayer1 ? "player1_shield" : "player2_shield";
        String playerCooldownKey = isPlayer1 ? "player1_" + spell + "_cooldown" : "player2_" + spell + "_cooldown";

        // Update spell status icon
        updateSpellStatus(spell);

        // Process spell in a transaction
        gameRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                long currentTime = System.currentTimeMillis();

                // Check cooldown for the specific spell
                Long cooldownTimestamp = currentData.child(playerCooldownKey).getValue(Long.class);
                if (cooldownTimestamp != null && cooldownTimestamp > currentTime) {
                    Log.e("Firebase", "Transaction aborted: spell " + spell + " on cooldown until " + cooldownTimestamp);
                    return Transaction.abort();
                }

                // Apply spell
                Integer currentOpponentHealth = currentData.child(opponentHealthKey).getValue(Integer.class);
                Integer currentPlayerHealth = currentData.child(playerHealthKey).getValue(Integer.class);
                Boolean opponentShieldActive = currentData.child(opponentShieldKey).getValue(Boolean.class);

                if (currentOpponentHealth == null) currentOpponentHealth = 100;
                if (currentPlayerHealth == null) currentPlayerHealth = 100;
                boolean isOpponentShieldActive = opponentShieldActive != null && opponentShieldActive;

                // Set spell cooldown
                long cooldownDuration = getCooldownDuration(spell);
                currentData.child(playerCooldownKey).setValue(currentTime + cooldownDuration);
                currentData.child(playerSpellKey).setValue(spell);

                if (spell.equals("attack")) {
                    if (isOpponentShieldActive) {
                        currentData.child(opponentShieldKey).setValue(false);
                    } else {
                        currentData.child(opponentHealthKey).setValue(Math.max(currentOpponentHealth - 20, 0));
                    }
                } else if (spell.equals("heal")) {
                    currentData.child(playerHealthKey).setValue(Math.min(currentPlayerHealth + 20, 100));
                } else if (spell.equals("shield")) {
                    currentData.child(playerShieldKey).setValue(true);
                }

                currentData.child("last_updated").setValue(System.currentTimeMillis());

                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null || !committed) {
                    Toast.makeText(GameActivity.this, "Failed to cast spell: " + (error != null ? error.getMessage() : "Spell on cooldown"), Toast.LENGTH_SHORT).show();
                    Log.e("Firebase", "Spell transaction failed: " + (error != null ? error.getMessage() : "Not committed"));
                    updateSpellStatus("none");
                    return;
                }

                // Play sound effects
                if (spell.equals("attack")) {
                    Boolean shieldActive = snapshot.child(opponentShieldKey).getValue(Boolean.class);
                    if (shieldActive == null || !shieldActive) {
                        if (attackSound != null) attackSound.start();
                    } else {
                        if (shieldBlockSound != null) shieldBlockSound.start();
                    }
                } else if (spell.equals("heal")) {
                    if (healSound != null) healSound.start();
                } else if (spell.equals("shield")) {
                    if (shieldActivateSound != null) shieldActivateSound.start();
                }

                // Update local state
                playerHealth = snapshot.child(playerHealthKey).getValue(Integer.class);
                opponentHealth = snapshot.child(opponentHealthKey).getValue(Integer.class);
                playerHealthBar.setProgress(playerHealth);
                p_health.setText(playerHealth + "/100");
                opponentHealthBar.setProgress(opponentHealth);
                op_health.setText(opponentHealth + "/100");

                // Check for game end condition after spell cast
                if (playerHealth <= 0 || opponentHealth <= 0) {
                    endGame();
                }

                Log.d("GameActivity", "Spell cast: " + spell + ", playerHealth=" + playerHealth + ", opponentHealth=" + opponentHealth);
            }
        });
    }

    private void endGame() {
        gameStarted = false;
        sensorManager.unregisterListener(this);
        timerHandler.removeCallbacks(timerRunnable);

        // Determine winner based on health
        boolean hasWon;
        String result;
        if (playerHealth <= 0 && opponentHealth <= 0) {
            hasWon = false;
            result = "Draw!";
        } else if (playerHealth <= 0) {
            hasWon = false;
            result = "You Lose!";
        } else if (opponentHealth <= 0) {
            hasWon = true;
            result = "You Win!";
        } else {
            // Timer ran out
            if (playerHealth > opponentHealth) {
                hasWon = true;
                result = "You Win!";
            } else if (playerHealth < opponentHealth) {
                hasWon = false;
                result = "You Lose!";
            } else {
                hasWon = false;
                result = "Draw!";
            }
        }

        Toast.makeText(GameActivity.this, "Game Over: " + result, Toast.LENGTH_LONG).show();
        gameRef.removeValue();

        // Release sound resources
        if (shieldActivateSound != null) {
            shieldActivateSound.release();
            shieldActivateSound = null;
        }
        if (shieldBlockSound != null) {
            shieldBlockSound.release();
            shieldBlockSound = null;
        }
        if (attackSound != null) {
            attackSound.release();
            attackSound = null;
        }
        if (healSound != null) {
            healSound.release();
            healSound = null;
        }

        // Navigate to GameOverActivity
        Intent intent = new Intent(GameActivity.this, GameOverActivity.class);
        intent.putExtra("hasWon", hasWon);
        intent.putExtra("result", result);
        startActivity(intent);
        finish();
    }

    private void updateSpellStatus(String spell) {
        switch (spell) {
            case "attack":
                spellStatusIcon.setImageResource(R.drawable.attack_icon);
                break;
            case "shield":
                spellStatusIcon.setImageResource(R.drawable.shield_icon);
                break;
            case "heal":
                spellStatusIcon.setImageResource(R.drawable.heal_icon);
                break;
            default:
                spellStatusIcon.setImageResource(R.drawable.default_icon);
                break;
        }
        spellStatusIcon.setAlpha(0f);
        spellStatusIcon.animate().alpha(1f).setDuration(500).start();
    }

    private void updateOpponentSpellStatus(String spell) {
        switch (spell) {
            case "attack":
                opponentSpellStatusIcon.setImageResource(R.drawable.attack_icon);
                break;
            case "shield":
                opponentSpellStatusIcon.setImageResource(R.drawable.shield_icon);
                break;
            case "heal":
                opponentSpellStatusIcon.setImageResource(R.drawable.heal_icon);
                break;
            default:
                opponentSpellStatusIcon.setImageResource(R.drawable.default_icon);
                break;
        }
        opponentSpellStatusIcon.setAlpha(0f);
        opponentSpellStatusIcon.animate().alpha(1f).setDuration(500).start();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        if (gameStarted && gameStartTime > 0) {
            timerHandler.post(timerRunnable);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (gameStarted) {
            endGame();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        if (shieldActivateSound != null) {
            shieldActivateSound.release();
            shieldActivateSound = null;
        }
        if (shieldBlockSound != null) {
            shieldBlockSound.release();
            shieldBlockSound = null;
        }
        if (attackSound != null) {
            attackSound.release();
            attackSound = null;
        }
        if (healSound != null) {
            healSound.release();
            healSound = null;
        }
    }
}
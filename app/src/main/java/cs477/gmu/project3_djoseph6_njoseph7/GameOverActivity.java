package cs477.gmu.project3_djoseph6_njoseph7;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class GameOverActivity extends AppCompatActivity {
    private MediaPlayer winSound, loseSound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over);

        // Initialize sound effects
        winSound = MediaPlayer.create(this, R.raw.win);
        loseSound = MediaPlayer.create(this, R.raw.lose);

        // Get the game result from the Intent
        boolean hasWon = getIntent().getBooleanExtra("hasWon", false);
        String result = getIntent().getStringExtra("result");

        // Update the result text
        TextView gameResultText = findViewById(R.id.game_result_text);
        gameResultText.setText(result != null ? result : "Game Over!");

        // Update the wizard image based on win/lose
        ImageView wizardImage = findViewById(R.id.wizard_image);
        if (hasWon) {
            wizardImage.setImageResource(R.drawable.happy_wizard);
        } else {
            wizardImage.setImageResource(R.drawable.sad_wizard);
        }

        // Play appropriate sound
        if (hasWon && winSound != null) {
            winSound.start();
        } else if (!hasWon && loseSound != null) {
            loseSound.start();
        }

        // Set up the End Game button to return to MainActivity
        Button endGameButton = findViewById(R.id.end_game_button);
        endGameButton.setOnClickListener(v -> {
            Intent intent = new Intent(GameOverActivity.this, MainActivity.class);
            // Clear the activity stack to start fresh
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release sound resources
        if (winSound != null) {
            winSound.release();
            winSound = null;
        }
        if (loseSound != null) {
            loseSound.release();
            loseSound = null;
        }
    }
}
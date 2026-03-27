package alex.qochinyan.first;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput, passwordInput;
    private Button registerBtn, guestBtn;
    private FirebaseAuth mAuth;

    @Override
    protected void onStart() {
        super.onStart();
        // Если уже вошли — летим в Main
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        registerBtn = findViewById(R.id.registerBtn);
        guestBtn = findViewById(R.id.guestBtn);

        // РЕГИСТРАЦИЯ / ВХОД ПО ПОЧТЕ
        registerBtn.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (email.isEmpty() || password.length() < 6) {
                Toast.makeText(this, "Email empty or pass < 6 symbols", Toast.LENGTH_SHORT).show();
            } else {
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            if (task.isSuccessful()) {
                                goToMain();
                            } else {
                                mAuth.signInWithEmailAndPassword(email, password)
                                        .addOnCompleteListener(taskLogin -> {
                                            if (taskLogin.isSuccessful()) goToMain();
                                            else Toast.makeText(this, "Auth Error", Toast.LENGTH_SHORT).show();
                                        });
                            }
                        });
            }
        });

        // ВХОД КАК ГОСТЬ (ИСПРАВЛЕНО)
        guestBtn.setOnClickListener(v -> {
            mAuth.signInAnonymously()
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            Log.d("AUTH", "Guest sign-in success");
                            goToMain();
                        } else {
                            Log.e("AUTH", "Guest sign-in failed", task.getException());
                            Toast.makeText(this, "Guest login error", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private void goToMain() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }
}
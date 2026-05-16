package alex.qochinyan.first;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText emailField, passwordField, confirmPasswordField;
    private MaterialButton loginBtn, signUpBtn, guestBtn;
    private FirebaseAuth mAuth;

    @Override
    protected void onStart() {
        super.onStart();
        mAuth = FirebaseAuth.getInstance();
        // Если уже залогинены — летим в Main
        if (mAuth.getCurrentUser() != null) {
            startMainActivity();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // Привязка UI
        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        confirmPasswordField = findViewById(R.id.confirmPasswordField);
        loginBtn = findViewById(R.id.loginBtn);
        signUpBtn = findViewById(R.id.signUpBtn);
        guestBtn = findViewById(R.id.guestBtn);

        // ЛОГИН
        loginBtn.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Введите почту и пароль", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            startMainActivity();
                        } else {
                            Toast.makeText(this, "Ошибка: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });

        // РЕГИСТРАЦИЯ
        signUpBtn.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();
            String confirm = confirmPasswordField.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirm)) {
                Toast.makeText(this, "Пароли не совпадают!", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            startMainActivity();
                        } else {
                            Toast.makeText(this, "Ошибка регистрации: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });

        // ГОСТЬ (Работает без Firebase)
        guestBtn.setOnClickListener(v -> startMainActivity());
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
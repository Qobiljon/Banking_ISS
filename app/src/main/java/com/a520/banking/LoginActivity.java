package com.a520.banking;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    // region Variables
    private AutoCompleteTextView usernameTextView;
    private TextView passwordTextView;
    private Button buttonLogIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        initialize();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(R.anim.activity_in, R.anim.activity_out);
    }
    // endregion

    private void initialize() {
        usernameTextView = findViewById(R.id.textview_login);
        passwordTextView = findViewById(R.id.textview_password);
        buttonLogIn = findViewById(R.id.button_login);

        ArrayList<String> users = new ArrayList<>();
        for (File file : getFilesDir().listFiles()) {
            String fileName = file.getName();
            if (fileName.endsWith(User.FILE_FORMAT))
                users.add(fileName.substring(0, fileName.lastIndexOf('.')));
        }
        ArrayAdapter<String> usrAdapter = new ArrayAdapter<>(this, android.R.layout.select_dialog_item, users);
        usernameTextView.setAdapter(usrAdapter);
        usernameTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                passwordTextView.requestFocus();
            }
        });
    }

    public void logInClick(View view) {
        String username = usernameTextView.getText().toString();
        String password = passwordTextView.getText().toString();

        User user = User.recover(this, String.format("%s.%s", username, User.FILE_FORMAT));
        if (user == null) {
            Toast.makeText(this, "Wrong credentials, please try again!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (user.checkPassword(password)) {
            Toast.makeText(this, "Successfully logged in!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("username", user.username);
            intent.putExtra("password", password);
            startActivity(intent);
            overridePendingTransition(R.anim.activity_in, R.anim.activity_out);
            finish();
        } else
            Toast.makeText(this, "Wrong credentials, please try again!", Toast.LENGTH_SHORT).show();
    }

    public void registerClick(View view) {
        String username = usernameTextView.getText().toString();
        String password = passwordTextView.getText().toString();

        if (password.length() != Tools.PASSWORD_LENGTH) {
            Toast.makeText(this, String.format(Locale.US, "Password must be strictly %d characters!", Tools.PASSWORD_LENGTH), Toast.LENGTH_SHORT).show();
            return;
        }

        User user = User.getInstance(this, username, password);
        if (user == null) {
            Toast.makeText(this, "Username already exists, please choose some other username!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (UserLog.initLogFile(this, user, password)) {
            user.writeToFile(this);
            buttonLogIn.performClick();
        } else
            Toast.makeText(this, "Error occurred while creating a log file!", Toast.LENGTH_SHORT).show();
    }
}

/*
* Information Systems Security
* Course project: 520 Bank
* Group: 520
* Members: Kobiljon Toshnazarov
 * Akhmadjon Abdullajanov
 * Nematjon Narziev
 * Saidrasulkhon Usmankhudjaev
* */

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

import org.json.JSONException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
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
        ArrayList<String> users;
        try {
            User.init(this);
            users = User.listUsernames(this);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return;
        }

        // region UI Variables
        usernameTextView = findViewById(R.id.textview_login);
        passwordTextView = findViewById(R.id.textview_password);
        buttonLogIn = findViewById(R.id.button_login);
        // endregion

        // for username autocompletion, load existing username-list from storage, set into adapter, and request next view on username click
        ArrayAdapter<String> usrAdapter = new ArrayAdapter<>(this, android.R.layout.select_dialog_item, users);
        usernameTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                passwordTextView.requestFocus();
            }
        });
        usernameTextView.setAdapter(usrAdapter);
    }

    public void logInClick(View view) {
        String username = usernameTextView.getText().toString();
        String password = passwordTextView.getText().toString();

        // try to recover the user (if exists)
        User user;
        if (User.exists(this, username))
            try {
                user = User.recover(this, username);
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "Wrong credentials, please try again!", Toast.LENGTH_SHORT).show();
                return;
            }
        else {
            Toast.makeText(this, "Wrong credentials, please try again!", Toast.LENGTH_SHORT).show();
            return;
        }

        // when user exists, check the password using the hash value of the password user has typed in
        try {
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
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            Toast.makeText(this, "Something went wrong in your device's hashing system, please contact application administrators!", Toast.LENGTH_SHORT).show();
        }
    }

    public void registerClick(View view) {
        String username = usernameTextView.getText().toString();
        String password = passwordTextView.getText().toString();

        // check username length (minimum 4 for convenience)
        if (username.length() < User.USERN_MINLEN || username.length() > User.USERN_MAXLEN) {
            Toast.makeText(this, String.format(Locale.US, String.format(Locale.US, "Username length must be between %d and %d characters!", User.USERN_MINLEN, User.USERN_MAXLEN), User.USERN_MAXLEN), Toast.LENGTH_SHORT).show();
            usernameTextView.requestFocus();
            return;
        }

        // strictly check the length of password (8 characters), because length of salt and password must be 16 (per 8 bytes)
        if (password.length() < User.PASSW_MINLEN || password.length() > User.PASSW_MAXLEN) {
            Toast.makeText(this, String.format(Locale.US, "Password length must be between %d and %d characters!", User.PASSW_MINLEN, User.PASSW_MAXLEN), Toast.LENGTH_SHORT).show();
            passwordTextView.requestFocus();
            return;
        }

        // try to create a new user (if it doesn't already exist)
        User user;
        try {
            user = User.getInstance(this, username, password);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "This username is occupied, please choose some other username!", Toast.LENGTH_SHORT).show();
            return;
        }

        // create a companion file (log file) for logging the user's activities
        try {
            UserLog.initLogFile(this, user, password);
            user.writeToFile(this);
            buttonLogIn.performClick();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error occurred while creating a log file!", Toast.LENGTH_SHORT).show();
        }
    }
}

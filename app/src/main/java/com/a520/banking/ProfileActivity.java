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

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    // region Variables
    private User user;
    private String password;
    private Button logOutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        initialize();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        Intent intent = new Intent();
        intent.putExtra("username", user.username);
        intent.putExtra("password", password);
        setResult(2, intent);
        finish();
        overridePendingTransition(R.anim.activity_in, R.anim.activity_out);
    }
    // endregion

    private void initialize() {
        logOutButton = findViewById(R.id.button_logout);

        String username = getIntent().getStringExtra("username");
        password = getIntent().getStringExtra("password");
        try {
            user = User.recover(this, username);
        } catch (IOException | JSONException e) {
            e.printStackTrace();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Errors on this user's file");
            builder.setMessage("Please, refer to application administrator!");
            builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    dialog.dismiss();
                }
            });
            builder.show();
        }
    }

    public void deleteUserClick(View view) {
        // show dialog for confirming before starting user-deletion process
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete your profile?");
        builder.setMessage("Please confirm whether you want to delete your profile or not.");
        builder.setNegativeButton("No, don't delete it.", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                dialog.dismiss();
            }
        });
        builder.setPositiveButton("Delete it, now!", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // for convenience, count number of deleted files, and display as a toast
                try {
                    user.delete(ProfileActivity.this);
                    Toast.makeText(ProfileActivity.this, "Profile has been deleted!", Toast.LENGTH_SHORT).show();
                    logOutButton.performClick();
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        builder.show();
    }

    public void logoutClick(View view) {
        Intent intent = new Intent();
        intent.putExtra("logout", true);
        setResult(2, intent);
        finish();
        overridePendingTransition(0, R.anim.activity_out);
    }

    public void changePasswordClick(View view) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("New password");

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Enter new password here");
        alert.setView(input);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Toast.makeText(ProfileActivity.this, "Operation canceled manually!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        AlertDialog dialog = alert.create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new ChangePasswordListener(input, dialog));
    }

    public void closeWindow(View view) {
        onBackPressed();
    }

    private class ChangePasswordListener implements View.OnClickListener {
        // region Variables
        private TextView input;
        private AlertDialog dialog;
        private String firstInput = null;
        // endregion

        ChangePasswordListener(TextView input, AlertDialog dialog) {
            this.input = input;
            this.dialog = dialog;
        }

        @Override
        public void onClick(View view) {
            // get input in string format
            String data = input.getText().toString();

            if (firstInput == null) {
                // input length mismatch
                if (data.length() < User.PASSW_MINLEN || data.length() > User.PASSW_MAXLEN) {
                    Toast.makeText(ProfileActivity.this, String.format(Locale.US, "Password length must be between %d and %d characters!", User.PASSW_MINLEN, User.PASSW_MAXLEN), Toast.LENGTH_SHORT).show();
                    input.requestFocus();
                    return;
                }
                firstInput = data;
                input.setText("");
                input.setHint("Confirm the password");
                input.requestFocus();
            } else if (data.equals(firstInput)) {
                try {
                    user.changePassword(ProfileActivity.this, password, password = data);
                    Toast.makeText(ProfileActivity.this, "Password has been changed successfully!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();

                    AlertDialog.Builder builder = new AlertDialog.Builder(ProfileActivity.this);
                    builder.setTitle("Failed to save changes");
                    builder.setMessage("Please restart the application!");
                    builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.dismiss();
                        }
                    });
                    builder.show();
                }
                dialog.dismiss();
            } else {
                firstInput = null;
                input.setText("");
                input.setHint("Enter new password here");
                input.requestFocus();
            }
        }
    }
}

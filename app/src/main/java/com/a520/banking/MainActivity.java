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
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // region Variables
    private TextView logTextView;
    private TextView balanceTextView;
    private User user;
    private UserLog log;
    private String password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialize(getIntent());
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(R.anim.activity_in, R.anim.activity_out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 2 && resultCode == 2) {
            if (data.getBooleanExtra("logout", false)) {
                logOut();
                return;
            }

            try {
                user = User.recover(this, data.getStringExtra("username"));
                log = UserLog.recover(this, user, password = data.getStringExtra("password"));
            } catch (Exception e) {
                e.printStackTrace();

                // this case will occur only if user intentionally edits the file hacking the android system storage location /data
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Errors on this user's files");
                builder.setMessage("Please, refer to application administrator!!");
                builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });
                builder.show();
            }
        }
    }
    // endregion

    private void initialize(Intent intent) {
        // region UI Variables
        logTextView = findViewById(R.id.textview_log);
        balanceTextView = findViewById(R.id.textview_balance);
        TextView userTextView = findViewById(R.id.textview_user);
        // endregion

        // recover user data from files
        password = intent.getStringExtra("password");

        try {
            user = User.recover(this, intent.getStringExtra("username"));
            log = UserLog.recover(this, user, password);
            // set username as title
            userTextView.setText(String.format(Locale.US, getResources().getString(R.string.current_user_hint), user.username));
        } catch (Exception e) {
            e.printStackTrace();

            // this case will occur only if user intentionally edits the file hacking the android system storage location /data
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Errors on this user's log file");
            builder.setMessage("Please, delete your account and recreate it!!");
            builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    dialog.dismiss();
                }
            });
            builder.show();
            return;
        }

        if (Tools.exists(this, String.format(Locale.US, "%s.%s", user.username, UserLog.TMP_FORMAT)))
            try {
                int count = log.acceptTransfers(this, password);
                Toast.makeText(this, String.format(Locale.US, "There are %d new money transfer records.", count), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                // this case will occur only if user intentionally edits the file hacking the android system storage location /data
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Errors on latest deposits");
                builder.setMessage("Failed to update the latest deposit updates from your temporary file. Please recreate your account or refer to application administrators!");
                builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });
                builder.show();
            }

        logTextView.setText(log.toString());
        balanceTextView.setText(Tools.putCommas(log.getBalance()));
    }

    public void customersClick(View root) {
        // load customers' names from storage, except the requesting customer's username
        ArrayList<String> userList;
        try {
            userList = User.listUsernames(this);
        } catch (Exception e) {
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.select_dialog_item, userList);

        // the case when there is no other customers than the requesting one
        if (adapter.getCount() == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("No customers found!");
            builder.setMessage("List of customers is empty!");
            builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    dialog.dismiss();
                }
            });
            builder.show();
            return;
        }

        // when there are other customers, display all loaded customers' names
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Customers");
        builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int index) {
                String customer = ((String) ((AlertDialog) dialog).getListView().getItemAtPosition(index));
                Intent intent = new Intent(MainActivity.this, DecryptionActivity.class);
                intent.putExtra("username", user.username);
                intent.putExtra("password", password);
                intent.putExtra("customer", customer);
                startActivity(intent);
                overridePendingTransition(R.anim.activity_in, R.anim.activity_out);
            }
        });
        builder.show();
    }

    public void depositClick(View view) {
        // request user to enter amount of deposit
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Deposit");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setRawInputType(Configuration.KEYBOARD_12KEY);
        input.setHint("Deposit amount");
        alert.setView(input);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // get input in string format, convert it into integer (note that input mode is only NUMBER = no exceptions)
                String data = input.getText().toString();
                // but first check if user hasn't entered anything
                if (data.length() == 0) {
                    Toast.makeText(MainActivity.this, "Field is empty!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // log the activity into customer's log file
                if (log.log(MainActivity.this, UserLog.OP_DEPOSIT, Integer.parseInt(data), null))
                    try {
                        log.writeToFile(MainActivity.this, password);
                    } catch (Exception e) {
                        e.printStackTrace();
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("Failed to write to storage!");
                        builder.setMessage("An error occurred while writing into storage, please reopen the application, and if it doesn't help, refer to application's administrators!");
                        builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int i) {
                                dialog.dismiss();
                            }
                        });
                        builder.show();
                        return;
                    }
                else {
                    Toast.makeText(MainActivity.this, "Operation canceled due to wrong input!", Toast.LENGTH_SHORT).show();
                    return;
                }

                logTextView.setText(log.toString());
                balanceTextView.setText(Tools.putCommas(log.getBalance()));
                dialog.dismiss();
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Toast.makeText(MainActivity.this, "Operation canceled manually!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        alert.show();
    }

    public void withdrawClick(View view) {
        // request user to enter amount of withdrawal
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Withdraw");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setRawInputType(Configuration.KEYBOARD_12KEY);
        input.setHint("Withdraw amount");
        alert.setView(input);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // get input in string format, convert it into integer (note that input mode is only NUMBER = no exceptions)
                String data = input.getText().toString();
                // but first check if user hasn't entered anything, and check if the customer hasn't entered greater amount that they have
                int amount;
                if (data.length() == 0) {
                    Toast.makeText(MainActivity.this, "Field is empty!", Toast.LENGTH_SHORT).show();
                    return;
                } else if ((amount = Integer.parseInt(data)) > log.getBalance()) {
                    Toast.makeText(MainActivity.this, String.format(Locale.US, "You don't have that much in your balance!\nBalance: %d Won", log.getBalance()), Toast.LENGTH_SHORT).show();
                    return;
                }

                // log the activity into customer's log file
                if (log.log(MainActivity.this, UserLog.OP_WITHDRAW, amount, null))
                    try {
                        log.writeToFile(MainActivity.this, password);
                    } catch (Exception e) {
                        e.printStackTrace();
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("Failed to write to storage!");
                        builder.setMessage("An error occurred while writing into storage, please reopen the application, and if it doesn't help, refer to application's administrators!");
                        builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int i) {
                                dialog.dismiss();
                            }
                        });
                        builder.show();
                        return;
                    }
                else {
                    Toast.makeText(MainActivity.this, "Operation canceled!", Toast.LENGTH_SHORT).show();
                    return;
                }

                logTextView.setText(log.toString());
                balanceTextView.setText(Tools.putCommas(log.getBalance()));
                dialog.dismiss();
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Toast.makeText(MainActivity.this, "Operation canceled!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        alert.show();
    }

    public void transferClick(View view) {
        // load customers' names from storage, except the requesting customer's username
        ArrayList<String> userList;
        try {
            userList = User.listUsernames(this);
            userList.remove(user.username);
        } catch (Exception e) {
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.select_dialog_item, userList);

        // the case when there is no other customers than the requesting one
        if (adapter.getCount() == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("No customers found!");
            builder.setMessage("List of customers is empty!");
            builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    dialog.dismiss();
                }
            });
            builder.show();
            return;
        }

        // when there are other customers, display all loaded customers' names
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Transfer money to");
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int index) {
                // when customer selects another customer for transferring money to, open a window to request amount of transfer
                String username = ((String) ((AlertDialog) dialog).getListView().getItemAtPosition(index));
                dialog.dismiss();
                transferToUser(username);
            }
        });
        builder.show();
    }

    private void transferToUser(final String username) {
        // request user to enter amount of transfer
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(String.format(Locale.US, "Transfer to %s", username));
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setRawInputType(Configuration.KEYBOARD_12KEY);
        input.setHint("Transfer amount");
        alert.setView(input);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // get input in string format, convert it into integer (note that input mode is only NUMBER = no exceptions)
                String data = input.getText().toString();
                // but first check if user hasn't entered anything, and check if the customer hasn't entered greater amount that they have
                int amount;
                if (data.length() == 0) {
                    Toast.makeText(MainActivity.this, "Field is empty!", Toast.LENGTH_SHORT).show();
                    return;
                } else if ((amount = Integer.parseInt(data)) > log.getBalance()) {
                    Toast.makeText(MainActivity.this, String.format(Locale.US, "You don't have that much in your balance!\nBalance: %d Won", log.getBalance()), Toast.LENGTH_SHORT).show();
                    return;
                }

                // log the activity into customer's log file
                if (log.log(MainActivity.this, UserLog.OP_TRANSFER, amount, username))
                    try {
                        log.writeToFile(MainActivity.this, password);
                    } catch (Exception e) {
                        e.printStackTrace();
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("Failed to write to storage!");
                        builder.setMessage("An error occurred while writing into storage, please reopen the application, and if it doesn't help, refer to application's administrators!");
                        builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int i) {
                                dialog.dismiss();
                            }
                        });
                        builder.show();
                        return;
                    }
                else {
                    Toast.makeText(MainActivity.this, "Operation canceled!", Toast.LENGTH_SHORT).show();
                    return;
                }

                logTextView.setText(log.toString());
                balanceTextView.setText(Tools.putCommas(log.getBalance()));
                dialog.dismiss();
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Toast.makeText(MainActivity.this, "Operation canceled!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        alert.show();
    }

    public void profileClick(View view) {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("username", user.username);
        intent.putExtra("password", password);
        startActivityForResult(intent, 2);
        overridePendingTransition(R.anim.activity_in, R.anim.activity_out);
    }

    private void logOut() {
        // log out and switch from main window into log in window
        startActivity(new Intent(this, LoginActivity.class));
        overridePendingTransition(R.anim.activity_in, 0);
        finish();
    }
}

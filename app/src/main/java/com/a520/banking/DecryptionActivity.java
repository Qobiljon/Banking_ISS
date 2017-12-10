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

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

public class DecryptionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_decryption);

        String username = getIntent().getStringExtra("username");
        String password = getIntent().getStringExtra("password");
        String customer = getIntent().getStringExtra("customer");
        ((TextView) findViewById(R.id.text_decrypt_title)).setText(String.format(Locale.US, getResources().getString(R.string.text_decrypt_title), Character.toUpperCase(username.charAt(0)) + username.substring(1), customer));
        TextView dataText = findViewById(R.id.text_decrypt_data);

        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            os.write(password.getBytes(Tools.ENCODING));
            os.write(Base64.decode(User.recover(this, username).salt, Base64.DEFAULT));
            byte[] encKey = os.toByteArray();
            String decData = UserLog.decrypt(encKey, Tools.readString(this, String.format(Locale.US, "%s.%s", customer, UserLog.FILE_FORMAT)));
            dataText.setText(decData);
        } catch (Exception e) {
            e.printStackTrace();
            dataText.setText(R.string.decryption_failure);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(R.anim.activity_in, R.anim.activity_out);
    }
}

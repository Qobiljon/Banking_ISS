/*
* This file contains utility classes and functions that support application's consistency.
*
* Information Systems Security
* Course project: 520 Bank
* Group: 520
* Members: Kobiljon Toshnazarov
 * Akhmadjon Abdullajanov
 * Nematjon Narziev
 * Saidrasulkhon Usmankhudjaev
* */

package com.a520.banking;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class Tools {
    /*
    * This class contains static utility functions for multiple usages within the application.
    * This class also contains constant properties for application consistency.
    * */

    // region Constants
    static final String ENCODING = "UTF-8";
    static final String NEWLINE = System.getProperty("line.separator");
    // endregion

    static boolean exists(Context context, String fileName) {
        // function for checking existence of a file
        return new File(context.getFilesDir(), fileName).exists();
    }

    static void writeString(Context context, String content, String fileName) throws IOException {
        // function for writing string into a file
        FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
        fos.write(content.getBytes(Tools.ENCODING));
        fos.close();
    }

    static String readString(Context context, String fileName) throws IOException {
        // return loaded byte array in string with utf-8 encoding
        return new String(readBytes(context, fileName), Tools.ENCODING);
    }

    static byte[] readBytes(Context context, String fileName) throws IOException {
        // function for reading string, first open file input stream for reading byte[], and then byte array output stream for creating dynamic syze byte[]
        FileInputStream fos = context.openFileInput(fileName);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        // create 64 byte size buffer
        byte[] buf = new byte[64];
        int read;
        while ((read = fos.read(buf)) > 0)
            os.write(buf, 0, read);

        // close all streams after usage (clean after usage :)
        os.close();
        fos.close();

        return os.toByteArray();
    }

    static void writeBytes(Context context, byte[] rawBytes, String fileName) throws IOException {
        // create a new file object for writing
        File file = new File(context.getFilesDir(), fileName);
        // open a stream for writing raw bytes
        OutputStream os = new FileOutputStream(file);
        // write the raw bytes
        os.write(rawBytes);
        // close the opened stream
        os.close();
    }

    static String putCommas(int amount) {
        if (amount == 0)
            return "0";
        StringBuilder sb = new StringBuilder();
        while (amount > 0) {
            if (sb.length() > 0)
                sb.insert(0, ',');
            if (amount >= 1000)
                sb.insert(0, String.format(Locale.US, "%03d", amount % 1000));
            else
                sb.insert(0, amount);
            amount /= 1000;
        }
        return sb.toString();
    }
}

class User {
    /*
    * This class creates, encrypts, decrypts, extracts customer-data from user's encrypted file.
    * */

    // region Constants
    static final String HASH_MODE = "sha-1";
    static final int ENC_KEY_LEN = 16;
    static final int PASSW_MINLEN = 4;
    static final int PASSW_MAXLEN = 14;
    static final int USERN_MINLEN = 4;
    static final int USERN_MAXLEN = 16;
    private static final String FILE_NAME = "users.json";
    // endregion

    // region Variables
    String username;
    String salt;
    String passwordHash;
    // endregion

    private User(String username, String salt, String passwordHash) {
        this.username = username;
        this.salt = salt;
        this.passwordHash = passwordHash;
    }

    static void init(Context context) throws IOException {
        if (!Tools.exists(context, FILE_NAME))
            Tools.writeString(context, "{}", FILE_NAME);
    }

    static String createSalt(int saltLength) throws NoSuchAlgorithmException, NoSuchProviderException {
        // function for creation of a random secure salt for hashing
        byte[] salt = new byte[saltLength];
        SecureRandom.getInstance("SHA1PRNG").nextBytes(salt);
        return Base64.encodeToString(salt, Base64.DEFAULT);
    }

    static String hash(String plainText, String salt) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        // function for hasing input using salt, and returning the resulting encrypted raw byte[]
        MessageDigest md = MessageDigest.getInstance(HASH_MODE);
        md.update(Base64.decode(salt, Base64.DEFAULT));

        byte[] hashBytes = md.digest(plainText.getBytes(Tools.ENCODING));

        StringBuilder result = new StringBuilder();
        for (byte b : hashBytes) {
            int halfbyte = (b >>> 4) & 0x0F;
            int two_halves = 0;
            do {
                result.append((0 <= halfbyte) && (halfbyte <= 9) ? (char) ('0' + halfbyte) : (char) ('a' + (halfbyte - 10)));
                halfbyte = b & 0x0F;
            } while (two_halves++ < 1);
        }
        return result.toString();

    }

    static User getInstance(Context context, String username, String password) throws Exception {
        // function for creating an instance, securely checking existing files, and input data
        if (User.exists(context, username))
            throw new Exception("User has already registered!");

        String salt = createSalt(ENC_KEY_LEN - password.length());
        String passwordHash = hash(password, salt);
        return new User(username, salt, passwordHash);
    }

    static boolean exists(Context context, String username) {
        try {
            JSONObject object = new JSONObject(Tools.readString(context, FILE_NAME));
            return object.has(username);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    static ArrayList<String> listUsernames(Context context) throws IOException, JSONException {
        JSONObject object = new JSONObject(Tools.readString(context, FILE_NAME));
        ArrayList<String> list = new ArrayList<>();
        Iterator<String> iter = object.keys();
        while (iter.hasNext()) list.add(iter.next());
        return list;
    }

    static User recover(Context context, String username) throws IOException, JSONException {
        // function for recovering a user's file from storage
        JSONObject object = new JSONObject(Tools.readString(context, FILE_NAME)).getJSONObject(username);
        return new User(object.getString("username"), object.getString("salt"), object.getString("passwordHash"));
    }

    boolean checkPassword(String password) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        // function for comparing hash values for checking correctness of a given password
        return passwordHash.equals(hash(password, salt));
    }

    void writeToFile(Context context) throws IOException, JSONException {
        // function for storing the current user object into storage
        JSONObject object = new JSONObject(Tools.readString(context, FILE_NAME));
        JSONObject child = object.has(username) ? object.getJSONObject(username) : new JSONObject();
        child.put("username", username);
        child.put("passwordHash", passwordHash);
        child.put("salt", salt);
        object.put(username, child);
        Tools.writeString(context, object.toString(), FILE_NAME);
    }

    void changePassword(Context context, String oldPassword, String newPassword) throws Exception {
        UserLog log = UserLog.recover(context, this, oldPassword);

        salt = createSalt(ENC_KEY_LEN - newPassword.length());
        passwordHash = hash(newPassword, salt);
        writeToFile(context);

        log.changePassword(context, newPassword);
    }

    void delete(Context context) throws IOException, JSONException {
        JSONObject object = new JSONObject(Tools.readString(context, FILE_NAME));
        object.remove(username);
        Tools.writeString(context, object.toString(), FILE_NAME);

        new File(context.getFilesDir(), String.format(Locale.US, "%s.%s", username, UserLog.TMP_FORMAT)).delete();
        new File(context.getFilesDir(), String.format(Locale.US, "%s.%s", username, UserLog.FILE_FORMAT)).delete();
    }
}

class UserLog {
    /*
    * This class creates, encrypts, decrypts, extracts customer-data from user's encrypted file.
    * */

    // region Constants
    static final String FILE_FORMAT = "log";
    static final String TMP_FORMAT = "tmp";
    static final String ENCRYPTION = "aes/cbc/nopadding";
    static final int OP_DEPOSIT = 0;
    static final int OP_WITHDRAW = 1;
    static final int OP_TRANSFER = 2;
    static final IvParameterSpec iv = new IvParameterSpec(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
    // endregion

    // region Variables
    private JSONArray log;
    private byte[] encKey;
    private int balance;
    private String username;
    // endregion

    private UserLog(String username, JSONArray log, byte[] encKey) throws JSONException {
        this.username = username;
        this.log = log;
        this.encKey = encKey;

        if (this.log.length() > 0)
            this.balance = this.log.getJSONObject(this.log.length() - 1).getInt("balance");
        else
            this.balance = 0;
    }

    static void initLogFile(Context context, User user, String password) throws Exception {
        // create a new user log file by encrypting it with user's plain password
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(password.getBytes(Tools.ENCODING));
        os.write(Base64.decode(user.salt, Base64.DEFAULT));
        os.close();

        byte[] encrypted = encrypt(os.toByteArray(), "[]".getBytes(Tools.ENCODING));
        Tools.writeBytes(context, encrypted, String.format(Locale.US, "%s.%s", user.username, FILE_FORMAT));
    }

    static UserLog recover(Context context, User user, String password) throws Exception {
        // recover the encrypted userlog file
        String fileName = String.format(Locale.US, "%s.%s", user.username, UserLog.FILE_FORMAT);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(password.getBytes(Tools.ENCODING));
        os.write(Base64.decode(user.salt, Base64.DEFAULT));

        byte[] encKey = os.toByteArray();
        JSONArray log = new JSONArray(new String(decrypt(encKey, Tools.readBytes(context, fileName)), Tools.ENCODING));
        UserLog result = new UserLog(user.username, log, encKey);
        return result;
    }

    static byte[] encrypt(byte[] encKey, byte[] plain_data) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(encKey, ENCRYPTION.substring(0, 3));
        Cipher cipher = Cipher.getInstance(ENCRYPTION);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(plain_data);
        if (plain_data.length % 16 != 0)
            os.write(new byte[16 * (int) Math.ceil((float) plain_data.length / 16) - plain_data.length]);

        return cipher.doFinal(os.toByteArray());
    }

    static byte[] decrypt(byte[] encKey, byte[] encrypted_data) throws Exception {
        if (encrypted_data.length == 0)
            return new byte[0];

        SecretKeySpec key = new SecretKeySpec(encKey, ENCRYPTION.substring(0, 3));
        Cipher cipher = Cipher.getInstance(ENCRYPTION);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        byte[] clearText = cipher.doFinal(encrypted_data);

        int stopByte = clearText.length - 1;
        while (clearText[stopByte] == 0) stopByte--;

        return Arrays.copyOfRange(clearText, 0, stopByte + 1);
    }

    @Override
    public String toString() {
        // function for formatting object string for display purposes
        try {
            StringBuilder sb = new StringBuilder();

            for (int n = 0; n < log.length(); n++) {
                JSONObject object = log.getJSONObject(n);

                Calendar time = GregorianCalendar.getInstance(Locale.US);
                time.setTimeInMillis(object.getLong("time"));

                int operation = object.getInt("operation");
                int amount = object.getInt("amount");
                int balance = object.getInt("balance");

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd            hh:mm:ss");
                sb.append(dateFormat.format(time.getTime()));
                sb.append(Tools.NEWLINE);

                if (operation == UserLog.OP_TRANSFER) {
                    String transferUsername = object.getString("transferred-to");
                    sb.append(String.format(Locale.US, "Transfer to %s         %s", transferUsername, Tools.putCommas(amount)));
                } else if (operation == OP_WITHDRAW)
                    sb.append(String.format(Locale.US, "Withdraw                            -%s", Tools.putCommas(amount)));
                else if (operation == OP_DEPOSIT)
                    sb.append(String.format(Locale.US, "Deposit                             %s", Tools.putCommas(amount)));

                sb.append(Tools.NEWLINE);
                sb.append(String.format(Locale.US, "Balance                            %s", Tools.putCommas(balance)));
                sb.append(Tools.NEWLINE);
                sb.append("----------------------------------");
                sb.append(Tools.NEWLINE);
            }

            return sb.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    void changePassword(Context context, String newPassword) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        os.write(newPassword.getBytes(Tools.ENCODING));
        os.write(Base64.decode(User.recover(context, username).salt, Base64.DEFAULT));
        os.close();
        encKey = os.toByteArray();

        writeToFile(context, newPassword);
    }

    public boolean log(Context context, int operation, int amount, @Nullable String transferUsername) {
        // function for logging a new action by customer
        Calendar logTime = GregorianCalendar.getInstance(Locale.US);
        if (operation != OP_DEPOSIT && amount > getBalance())
            return false;

        try {
            JSONObject item = new JSONObject();
            item.put("time", logTime.getTimeInMillis());
            item.put("operation", operation);
            item.put("amount", amount);
            if (operation == OP_TRANSFER) {
                item.put("transferred-to", transferUsername);
                logTransfer(context, transferUsername, amount);
            }
            item.put("balance", balance += operation == OP_DEPOSIT ? amount : -amount);
            log.put(item);
            return true;
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    final int getBalance() {
        return balance;
    }

    private void logTransfer(Context context, String transferToUser, int amount) throws JSONException, IOException {
        // function for storing a transfer action into recipient side's temporary file in a transfer
        String fileName = String.format(Locale.US, "%s.%s", transferToUser, TMP_FORMAT);
        JSONArray array;

        if (Tools.exists(context, fileName))
            array = new JSONArray(Tools.readString(context, fileName));
        else array = new JSONArray();

        JSONObject object = new JSONObject();
        object.put("time", Calendar.getInstance().getTimeInMillis());
        object.put("amount", amount);
        array.put(object);

        Tools.writeString(context, array.toString(), fileName);
    }

    int acceptTransfers(Context context, String password) throws Exception {
        // function for accepting new transfer records found in customer's temporary file
        String tmpFileName = String.format(Locale.US, "%s.%s", username, TMP_FORMAT);
        JSONArray tmpLog = new JSONArray(Tools.readString(context, tmpFileName));
        int count;
        for (count = 0; count < tmpLog.length(); count++) {
            JSONObject tmpObject = tmpLog.getJSONObject(count);
            long time = tmpObject.getLong("time");
            int amount = tmpObject.getInt("amount");

            JSONObject item = new JSONObject();
            item.put("time", time);
            item.put("operation", OP_DEPOSIT);
            item.put("amount", amount);
            item.put("balance", balance += amount);
            log.put(item);
        }
        writeToFile(context, password);
        new File(context.getFilesDir(), tmpFileName).delete();
        return count;
    }

    void writeToFile(Context context, String password) throws Exception {
        // function for storing the current user's user-log object into storage
        byte[] encrypted = encrypt(encKey, log.toString().getBytes(Tools.ENCODING));
        Tools.writeBytes(context, encrypted, String.format(Locale.US, "%s.%s", username, UserLog.FILE_FORMAT));
    }
}

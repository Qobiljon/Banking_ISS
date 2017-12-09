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
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

class Tools {
    /*
    * This class contains static utility functions for multiple usages within the application.
    * This class also contains constant properties for application consistency.
    * */

    // region Constants
    static final String ENCODING = "utf8";
    static final int PASSWORD_LENGTH = 8;
    static final int USERNAME_MINLENGTH = 4;
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
    static final String FILE_FORMAT = "user";
    static final String HASH_MODE = "sha-1";
    private static final int SALT_LENGTH = 8;
    // region Variables
    String username;
    // endregion
    byte[] saltBytes;
    String passwordHash;

    private User(String username, byte[] saltBytes, String passwordHash) {
        this.username = username;
        this.saltBytes = saltBytes;
        this.passwordHash = passwordHash;
    }
    // endregion

    static byte[] createSalt() throws NoSuchAlgorithmException, NoSuchProviderException {
        // function for creation of a random secure salt for hashing
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    static String hash(String plainText, byte[] saltBytes) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        // function for hasing input using saltBytes, and returning the resulting encrypted raw byte[]
        MessageDigest md = MessageDigest.getInstance(HASH_MODE);
        md.update(saltBytes);

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
        if (Tools.exists(context, String.format("%s.%s", username, FILE_FORMAT)))
            throw new Exception("User file doesn't exist.");

        byte[] saltBytes = createSalt();
        String passwordHash = hash(password, saltBytes);
        return new User(username, saltBytes, passwordHash);
    }

    static User recover(Context context, String fileName) throws IOException {
        // function for recovering a user's file from storage
        byte[] rawBytes = Tools.readBytes(context, fileName);
        String rawString = new String(rawBytes, Tools.ENCODING);

        int startIndex = 0;
        int index = rawString.indexOf(Tools.NEWLINE, startIndex);
        String username = rawString.substring(startIndex, index);

        startIndex = index + Tools.NEWLINE.length();
        index = rawString.indexOf(Tools.NEWLINE, startIndex);
        String passwordHash = rawString.substring(startIndex, index);

        startIndex = index + Tools.NEWLINE.length();
        byte[] saltBytes = Arrays.copyOfRange(rawBytes, startIndex, rawBytes.length);

        return new User(username, saltBytes, passwordHash);
    }

    boolean checkPassword(String password) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        // function for comparing hash values for checking correctness of a given password
        return passwordHash.equals(hash(password, saltBytes));
    }

    void writeToFile(Context context) throws IOException {
        // function for storing the current user object into storage
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        os.write(username.getBytes(Tools.ENCODING));
        os.write(Tools.NEWLINE.getBytes(Tools.ENCODING));
        os.write(passwordHash.getBytes(Tools.ENCODING));
        os.write(Tools.NEWLINE.getBytes(Tools.ENCODING));
        os.write(saltBytes);
        os.close();

        Tools.writeBytes(context, os.toByteArray(), String.format("%s.%s", username, FILE_FORMAT));
        os.close();
    }
}

class UserLog {
    /*
    * This class creates, encrypts, decrypts, extracts customer-data from user's encrypted file.
    * */

    // region Constants
    static final String FILE_FORMAT = "log";
    static final String TMP_FORMAT = "tmp";
    static final String ENC_MODE = "aes";

    static final int OP_DEPOSIT = 0;
    static final int OP_WITHDRAW = 1;
    static final int OP_TRANSFER = 2;
    // endregion

    // region Variables
    JSONArray log;
    private byte[] encKey;
    private int balance;

    private UserLog(JSONArray log, byte[] encKey) throws JSONException {
        this.log = log;
        this.encKey = encKey;

        if (this.log.length() > 0)
            this.balance = this.log.getJSONObject(this.log.length() - 1).getInt("balance");
        else
            this.balance = 0;
    }
    // endregion

    static void initLogFile(Context context, User user, String password) throws Exception {
        // create a new user log file by encrypting it with user's plain password
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(password.getBytes(Tools.ENCODING));
        os.write(user.saltBytes);
        os.close();

        byte[] dataBytes = encrypt(os.toByteArray(), "[]");
        String fileName = String.format(Locale.US, "%s.%s", user.username, FILE_FORMAT);

        Tools.writeBytes(context, dataBytes, fileName);
    }

    static UserLog recover(Context context, User user, String password) throws Exception {
        // recover the encrypted userlog file
        String fileName = String.format(Locale.US, "%s.%s", user.username, UserLog.FILE_FORMAT);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(password.getBytes(Tools.ENCODING));
        os.write(user.saltBytes);

        byte[] encKey = os.toByteArray();
        JSONArray log = new JSONArray(decrypt(encKey, Tools.readBytes(context, fileName)));
        UserLog result = new UserLog(log, encKey);
        return result;
    }

    private static byte[] encrypt(byte[] encKey, String plain_data) throws Exception {
        // function for encrypting plain string
        byte[] dataBytes = plain_data.getBytes("utf-8");

        SecretKeySpec key = new SecretKeySpec(encKey, "aes");
        Cipher cipher = Cipher.getInstance("aes");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        return cipher.doFinal(dataBytes);
    }

    private static String decrypt(byte[] encKey, byte[] encrypted_data) throws Exception {
        // function for decrypting encrypted data into string
        SecretKeySpec key = new SecretKeySpec(encKey, "aes");
        Cipher cipher = Cipher.getInstance("aes");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] res = cipher.doFinal(encrypted_data);

        return new String(res, "utf-8");
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

    private void logTransfer(Context context, String username, int amount) throws JSONException, IOException {
        // function for storing a transfer action into recipient side's temporary file in a transfer
        String fileName = String.format(Locale.US, "%s.%s", username, TMP_FORMAT);
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
                sb.append("----------------------------------------------------------------------------");
                sb.append(Tools.NEWLINE);
            }

            return sb.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    int acceptTransfers(Context context, User user, String password) throws Exception {
        // function for accepting new transfer records found in customer's temporary file
        String tmpFileName = String.format(Locale.US, "%s.%s", user.username, TMP_FORMAT);
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
        writeToFile(context, user, password);
        new File(context.getFilesDir(), tmpFileName).delete();
        return count;
    }

    void writeToFile(Context context, User user, String password) throws Exception {
        // function for storing the current user's user-log object into storage
        String fileName = String.format(Locale.US, "%s.%s", user.username, UserLog.FILE_FORMAT);
        byte[] contentBytes = encrypt(encKey, log.toString());
        Tools.writeBytes(context, contentBytes, fileName);
    }
}

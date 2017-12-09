package com.a520.banking;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    // region Constants
    static final String ENCODING = "utf8";
    static final int PASSWORD_LENGTH = 8;
    static final String NEWLINE = System.getProperty("line.separator");
    // endregion

    static boolean exists(Context context, String fileName) {
        return new File(context.getFilesDir(), fileName).exists();
    }

    static void writeString(Context context, String content, String fileName) {
        try {
            FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
            fos.write(content.getBytes(Tools.ENCODING));
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static String readString(Context context, String fileName) {
        try {
            FileInputStream fos = context.openFileInput(fileName);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buf = new byte[64];
            int read;
            while ((read = fos.read(buf)) > 0)
                os.write(buf, 0, read);
            os.close();
            fos.close();
            return new String(os.toByteArray(), Tools.ENCODING);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    static byte[] readBytes(Context context, String path) {
        byte[] getBytes = {};
        try {
            File file = new File(context.getFilesDir(), path);
            getBytes = new byte[(int) file.length()];
            InputStream is = new FileInputStream(file);
            if (is.read(getBytes) != getBytes.length)
                Log.e("ERROR", "File is read but length is not the same [weird].");
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return getBytes;
    }

    static void writeBytes(Context context, byte[] rawBytes, String fileName) {
        try {
            File file = new File(context.getFilesDir(), fileName);
            OutputStream os = new FileOutputStream(file);
            os.write(rawBytes);
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class User {
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
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    static String hash(String plainText, byte[] saltBytes) {
        try {
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
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    static User getInstance(Context context, String username, String password) {
        if (Tools.exists(context, String.format("%s.%s", username, FILE_FORMAT)))
            return null;

        try {
            byte[] saltBytes = createSalt();
            String passwordHash = hash(password, saltBytes);
            return new User(username, saltBytes, passwordHash);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    static User recover(Context context, String fileName) {
        if (Tools.exists(context, fileName)) {
            byte[] rawBytes = Tools.readBytes(context, fileName);
            try {
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
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    boolean checkPassword(String password) {
        return passwordHash.equals(hash(password, saltBytes));
    }

    void writeToFile(Context context) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();

            os.write(username.getBytes(Tools.ENCODING));
            os.write(Tools.NEWLINE.getBytes(Tools.ENCODING));
            os.write(passwordHash.getBytes(Tools.ENCODING));
            os.write(Tools.NEWLINE.getBytes(Tools.ENCODING));
            os.write(saltBytes);
            os.close();

            Tools.writeBytes(context, os.toByteArray(), String.format("%s.%s", username, FILE_FORMAT));
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class UserLog {
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

    static boolean initLogFile(Context context, User user, String password) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            os.write(password.getBytes(Tools.ENCODING));
            os.write(user.saltBytes);
            os.close();

            byte[] dataBytes = encrypt(os.toByteArray(), "[]");
            String fileName = String.format(Locale.US, "%s.%s", user.username, FILE_FORMAT);

            Tools.writeBytes(context, dataBytes, fileName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    static UserLog recover(Context context, User user, String password) {
        String fileName = String.format(Locale.US, "%s.%s", user.username, UserLog.FILE_FORMAT);

        if (Tools.exists(context, fileName)) {
            try {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                os.write(password.getBytes(Tools.ENCODING));
                os.write(user.saltBytes);

                byte[] encKey = os.toByteArray();
                JSONArray log = new JSONArray(decrypt(encKey, Tools.readBytes(context, fileName)));
                UserLog result = new UserLog(log, encKey);
                return result;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static byte[] encrypt(byte[] encKey, String logText) throws Exception {
        byte[] dataBytes = logText.getBytes("utf-8");

        SecretKeySpec key = new SecretKeySpec(encKey, "aes");
        Cipher cipher = Cipher.getInstance("aes");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        return cipher.doFinal(dataBytes);
    }

    private static String decrypt(byte[] encKey, byte[] logEncBytes) throws Exception {
        SecretKeySpec key = new SecretKeySpec(encKey, "aes");
        Cipher cipher = Cipher.getInstance("aes");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] res = cipher.doFinal(logEncBytes);

        return new String(res, "utf-8");
    }

    public boolean log(Context context, int operation, int amount, @Nullable String transferUsername) {
        try {
            Calendar logTime = GregorianCalendar.getInstance(Locale.US);
            // for a complete meditation
            if (operation != OP_DEPOSIT && amount > getBalance())
                return false;

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
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    int getBalance() {
        return balance;
    }

    private void logTransfer(Context context, String username, int amount) throws JSONException {
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
                    sb.append(String.format(Locale.US, "Transfer to %s         %d", transferUsername, -amount));
                } else if (operation == OP_WITHDRAW)
                    sb.append(String.format(Locale.US, "Withdraw                            %d", -amount));
                else if (operation == OP_DEPOSIT)
                    sb.append(String.format(Locale.US, "Deposit                             %d", amount));

                sb.append(Tools.NEWLINE);
                sb.append(String.format(Locale.US, "Balance                            %d", balance));
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

    boolean updateDepositsIfNeeded(Context context, User user, String password) {
        String tmpFileName = String.format(Locale.US, "%s.%s", user.username, TMP_FORMAT);
        if (Tools.exists(context, tmpFileName))
            try {
                JSONArray tmpLog = new JSONArray(Tools.readString(context, tmpFileName));
                for (int n = 0; n < tmpLog.length(); n++) {
                    JSONObject tmpObject = tmpLog.getJSONObject(n);
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
                return true;
            } catch (JSONException e) {
                e.printStackTrace();
                return false;
            }
        return true;
    }

    void writeToFile(Context context, User user, String password) {
        String fileName = String.format(Locale.US, "%s.%s", user.username, UserLog.FILE_FORMAT);
        try {
            byte[] contentBytes = encrypt(encKey, log.toString());
            Tools.writeBytes(context, contentBytes, fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

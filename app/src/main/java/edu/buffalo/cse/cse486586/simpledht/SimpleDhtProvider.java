package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {

    static final String TAG = SimpleDhtProvider.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final int SERVER_PORT = 10000;

    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    List<String> allKeys = new ArrayList<>();
    private static Context context;

    private final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }


    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        context = getContext();
        TelephonyManager tel = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        Log.v(TAG, "My port is: "+ myPort);
        return false;
    }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        if(selection.compareTo("@") == 0) {
            allKeys.clear();
            Log.v(TAG, "Deleting @");
        }
        else if(selection.compareTo("*") == 0) {
            Log.v(TAG, "Deleting from all *");
        }
        else {
            allKeys.remove(selection);
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        String a = values.getAsString("key");
        String b = values.getAsString(("value"));

        String filename = a;
        String fileContents = b;
//        Log.e(TAG, "inside insert");
        try (FileOutputStream fos = getContext().openFileOutput(filename, Context.MODE_PRIVATE)) {
            fos.write(fileContents.getBytes());
            allKeys.add(filename);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found exception in insert");

            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "IOException in insert");
            e.printStackTrace();
        }
        Log.v(TAG, values.toString());
        return uri;
//        return null;
    }

    private String queryHelper(String selection) {
        FileInputStream fis = null;
        try {
            fis = getContext().openFileInput(selection);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        InputStreamReader inputStreamReader = new InputStreamReader(fis, StandardCharsets.UTF_8);
        StringBuilder stringBuilder = new StringBuilder();
        try(BufferedReader reader = new BufferedReader(inputStreamReader)) {
            String line = reader.readLine();
            stringBuilder.append(line).append('\n');
            return line;


        }
        catch (IOException e) {
            // Error occurred when opening raw file for reading.
        } finally {
            String contents = stringBuilder.toString();
        }


//        Log.v("query", selection);
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // TODO Auto-generated method stub
        MatrixCursor mc = new MatrixCursor(new String[]{"key", "value"});

        if(selection.compareTo("@") == 0) {
            Log.v(TAG, "Query @");
            for(String k: allKeys) {
                String line = queryHelper(k);
                mc.newRow().add("key", k).add("value",line);
            }
        }
        else if(selection.compareTo("*") == 0) {
            Log.v(TAG, "Query *");
            for(String k: allKeys) {
                String line = queryHelper(k);
                mc.newRow().add("key", k).add("value",line);
            }
        }
        else {
            String line = queryHelper(selection);
            mc.newRow().add("key", selection).add("value",line);
            Log.v(TAG, "inside else of query");
        }
        return mc;

//        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
}

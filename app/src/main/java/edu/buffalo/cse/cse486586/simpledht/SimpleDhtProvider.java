package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.ListIterator;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

public class SimpleDhtProvider extends ContentProvider {

    static final String TAG = SimpleDhtProvider.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    static final int SERVER_PORT = 10000;
    static String succ = null;
    static String pred = null;
    static String myhash = null;

;
    List<String> allKeys = new ArrayList<>();
    class Avd implements Comparable<Avd>{
        private String port;
        private String hash;
        private String pred_port;
        private String pred_hash;
        private String succ_hash;
        private String succ_port;
        Avd(String port) {
            if(port.length() > 4) {
                port = String.valueOf((Integer.parseInt(port)/2));
            }
            this.port = port;
            this.pred_port = null;
            this.succ_port = null;

            try {
                this.hash = genHash(this.port);

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        public String getHash() {
            return this.hash;
        }
        public int compareTo(Avd device) {
            if(device.hash.compareTo(this.hash) < 0) return 1;
            else return -1;
        }
        public String getLongPort() {
            return String.valueOf((Integer.parseInt(this.port) * 2));
        }
        public String getPort() {
            return this.port;
        }
        public void setPred_port(String port) {
            this.pred_port = port;
            try {
                this.pred_hash = genHash(port);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        public void setSucc_port(String port) {
            this.succ_port = port;
            try {
                this.succ_hash = genHash(port);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
    }
    private static Avd currentAvd = null;

    List<Avd> activeDevices = new ArrayList<>();

    private static Context context;

    private final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }
    private void initializations(String myport) {
        switch(myport) {
            case REMOTE_PORT0:
                succ = REMOTE_PORT1;
                pred = REMOTE_PORT4;
                break;
            case REMOTE_PORT1:
                succ = REMOTE_PORT2;
                pred = REMOTE_PORT0;
                break;
            case REMOTE_PORT2:
                succ = REMOTE_PORT3;
                pred = REMOTE_PORT1;
                break;
            case REMOTE_PORT3:
                succ = REMOTE_PORT4;
                pred = REMOTE_PORT2;
                break;
            case REMOTE_PORT4:
                succ = REMOTE_PORT0;
                pred = REMOTE_PORT3;
                break;

        }
    }


    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        context = getContext();
        TelephonyManager tel = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));



        currentAvd = new Avd(portStr);
        activeDevices.add(currentAvd);


        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return false;
        }
        //replace myPort with getPort;
        if(!myPort.equals(REMOTE_PORT0)) {
            String msg = "J,"+portStr+"\n";
            String recPort = REMOTE_PORT0;
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, recPort);
        }



        return false;
    }
    private void stabilizeRing() {
        Collections.sort(activeDevices);

        ListIterator<Avd> it = activeDevices.listIterator();
        Avd temp = it.next();
        Avd first = temp;
        while(it.hasNext()) {
            Avd nxt = it.next();
            temp.setSucc_port(nxt.getPort());
            temp = nxt;
        }
        Avd last = temp;
        first.setPred_port(last.getPort());
        last.setSucc_port(first.getPort());
        while(it.hasPrevious()) {
            Avd pre = it.previous();
            temp.setPred_port(pre.getPort());
            temp = pre;
        }

        for(Avd a: activeDevices) {
            Log.v(a.getPort(), "Pred: "+ a.pred_port + " Succ: "+ a.succ_port);
        }
    }

    /***
     * Avd 1 receives the join request. (J,"port")
     * All avds receieve the insert , query and remove requests (I/Q/R,"hash_key")
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            try {
                while(true) {
                    Socket clientSocket = serverSocket.accept();
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out =
                            new PrintWriter(clientSocket.getOutputStream(), true);
                    String inputLine= in.readLine();
                    String[] str = inputLine.split(",");
                    if(str[0].equals("J")) {
                        Log.d(TAG, "Join request received for:"+str[1]);
                        // Only received by avd 0
                        // Reply with currentAvd.pred, succ, if you want to.
                        out.println("J,take this id!\n");
                        activeDevices.add(new Avd(str[1]));
                        stabilizeRing();

                    }
                    else if(str[0].equals("Q")) {
                        Log.d(TAG, "Query request received for:"+str[1]);
                    }
                    else if(str[0].equals("I")) {
                        Log.d(TAG, "Insert request received for:"+str[1]);
                        ContentValues cv = new ContentValues();
                        cv.put(KEY_FIELD, str[1]);
                        cv.put(VALUE_FIELD, str[2]);
                        insert(null, cv);
                        out.println("I, done insert\n");
                    }
                    else if(str[0].equals("R")) {
                        Log.d(TAG, "Remove request received for:"+str[1]);
                    }
//                    publishProgress(inputLine);
                }

            } catch (IOException e) {
                Log.e(TAG,"Exception caught when trying to listen on port "
                        + sockets[0] + " or listening for a connection");
                Log.e(TAG, e.getMessage());
            }


            return null;
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
//            String strReceived = strings[0].trim();
//            TextView textView = (TextView) findViewById(R.id.textView1);
//            textView.append(strReceived + "\n");
//            final ContentValues mContentValue = new ContentValues();
//            mContentValue.put(KEY_FIELD, sequenceNo);
//            mContentValue.put(VALUE_FIELD, strReceived);
//            if(strInsert(mContentValue)) {
//                textView.append("inserted");
//            }
//            else { textView.append("some error"); }




            return;
        }
    }

    /***
     * Called by insert, query or remove function
     * msgs array contains the 'msgtosend' and 'recPort'
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
//                String remotePort = REMOTE_PORT0;
//                if (msgs[1].equals(REMOTE_PORT0))
//                    remotePort = REMOTE_PORT1;

                String msgToSend = msgs[0];
                String recPort = msgs[1];
                String replyStr = "Empty";

                BufferedReader in;

                Socket socket;
                PrintWriter out;

                Log.d(TAG, "Sending: "+ msgToSend+ " to: "+recPort);
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(recPort));

                out = new PrintWriter(socket.getOutputStream(), true);
                out.println(msgToSend);


                //Reply from the server
                in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                replyStr = in.readLine();
                Log.v("Reply receieved: ", replyStr);
                String[] reply = replyStr.split(",");
                if(reply[0].equals("J")) {
                    Log.v("Join was accepted", "!");
                    currentAvd.pred_port = "10000"; // this port doesn't matter.
                }

//                }

//                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }


            return null;
        }
    }

    private String getPartitionPort(String msg) {
        String msgHash = "";
        try {
            msgHash = genHash(msg);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        ListIterator<Avd> it = activeDevices.listIterator();

        while(it.hasNext()) {
            Avd tempAvd = it.next();
            if(tempAvd.getHash().compareTo(msgHash) > 0) return tempAvd.getLongPort();
        }
        // If not returned yet. The msgHash is greater than the last Avd in the list.
        // Hence the successor will be the first Avd;
        it = activeDevices.listIterator();
        return it.next().getLongPort();

    }


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if(selection.compareTo("@") == 0) {
            allKeys.clear();
            Log.v(TAG, "Deleting @");
        }
        else if(selection.compareTo("*") == 0) {
            // get the correct port of the msg's location, create a new client and sent the delete query to that port.
            Log.v(TAG, "Deleting from all *");
        }
        else {
            //perform the following line in the responsible avd.
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


        String k = values.getAsString("key");
        String v = values.getAsString("value");

        String filename = null;

        // for only one avd
        if(currentAvd.pred_port == null) {
            try {
                filename = genHash(k);
            }
            catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        else {
          // Devices have joined.
          if(currentAvd.getLongPort().equals(REMOTE_PORT0)) {
              // when insert request received by avd0. from activity or other device.
              String port = getPartitionPort(k);
              if(port.equals(REMOTE_PORT0)) {
                  // When request belongs to avd0
                  try {
                      filename = genHash(k);
                  }
                  catch (NoSuchAlgorithmException e) {
                      e.printStackTrace();
                  }
              }
              else {
                  // when key belongs to other avd(not 0)
                  new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "I," + k + "," + v + "\n", port);
                  return null;
              }
          }
          else {
              // wHEN REQUEST RECEIEVED from !AVD0
              if(uri == null) {
                  // request came from avd0; insert
                  try {
                      filename = genHash(k);
                  }
                  catch (NoSuchAlgorithmException e) {
                      e.printStackTrace();
                  }
              }
              else {
                  // request received from other activity.
                  new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "I," + k + "," + v + "\n", REMOTE_PORT0);
                  return null;
              }
          }
        }





        String fileContents = v;
//        Log.e(TAG, "inside insert");
        try (FileOutputStream fos = getContext().openFileOutput(filename, Context.MODE_PRIVATE)) {
            fos.write(fileContents.getBytes());
            allKeys.add(k);
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
            fis = getContext().openFileInput(genHash(selection));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
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
            Log.e(TAG, "Error while opening file");
        } finally {
//            String contents = stringBuilder.toString();
        }

        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {

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
            // Needs communication here with avds.
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

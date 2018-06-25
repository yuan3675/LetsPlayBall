package com.example.yuan.letsplayball;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class NetworkClient {

    public String connect(String request) throws IOException{
        String host = "";
        int port = 9487;
        Socket socket = null;
        try {
            try {
                JSONObject object = new JSONObject(request);
                socket = new Socket("127.0.0.1", port);
                DataInputStream input = null;
                DataOutputStream output = null;

                try {
                    input = new DataInputStream(socket.getInputStream());
                    output = new DataOutputStream(socket.getOutputStream());
                    while (true) {
                        output.writeUTF(object.toString());
                        String response = input.readUTF();
                        Log.v("Response", response);
                        return(response);
                    }
                } catch (IOException e) {
                } finally {
                    if (input == null) input.close();
                    if (output == null) output.close();
                }
            }
            catch (JSONException e) {
                e.printStackTrace();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (socket != null) {
                socket.close();
                Log.v("Close check", "Socket closed");
            }
        }
        return null;
    }
}


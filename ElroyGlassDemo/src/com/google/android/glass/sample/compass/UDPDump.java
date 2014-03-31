package com.google.android.glass.sample.compass;

import android.view.Menu;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;

import android.location.Location;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.provider.Settings.Secure;
import org.json.*;


public class UDPDump {
	static private int PORT = 2323;
	static private String TAG = "udpdump";
	
	private DatagramSocket m_socket = null;
	private AsyncTask<Void, Void, Void> m_task = null;
	
	public float Heading;
	public float Pitch;
	public float LightLevel;
	public float Gravity[] = new float[3];
	public float LinearAcceleration[] = new float[3];
	public Location Location;
    
	private java.util.Date m_lastUpdate = new java.util.Date();
	
	public UDPDump() {
		try {
			m_socket = new DatagramSocket();
			m_socket.setBroadcast(true);
			Log.i(TAG, "Connected to datagram socket.");
		} catch (Exception e) {
			Log.e(TAG, "Exception opening DatagramSocket UDP " +e.getMessage());
		}
	}

	public void send() {
		if (m_socket == null) {
			Log.w(TAG, "Sending when socket is null");
			return;
		}
		
		java.util.Date now = new java.util.Date();
		if(now.getTime()-m_lastUpdate.getTime() < 200)
			return;
		
		m_lastUpdate = now;

		if (m_task == null || (m_task != null && m_task.getStatus() == Status.FINISHED) ) {
			m_task = new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					
					JSONObject object = new JSONObject();
					try {
						object.put("GlassId", Secure.ANDROID_ID);
						object.put("Heading",Heading);
						object.put("Pitch",Pitch);
						object.put("LightLevel",LightLevel);
						
						
						JSONArray gravArr = new JSONArray();
						gravArr.put(Gravity[0]);
						gravArr.put(Gravity[0]);
						gravArr.put(Gravity[0]);
						object.put("Gravity",gravArr);
						
						JSONArray accelArr = new JSONArray();
						accelArr.put(LinearAcceleration[0]);
						accelArr.put(LinearAcceleration[1]);
						accelArr.put(LinearAcceleration[2]);
						object.put("LinearAcceleration",accelArr);
						
						object.put("Lat",Location.getLatitude());
						object.put("Lng",Location.getLongitude());	
						object.put("Time",new java.util.Date().getTime());
						
						byte[] outBuffer = object.toString().getBytes();
						
						try {
							DatagramPacket sendPacket = new DatagramPacket(
									outBuffer, outBuffer.length,
									InetAddress.getByName("255.255.255.255"), PORT);
							m_socket.send(sendPacket);
						} catch (IOException e) {
							Log.e(TAG,"Exception Failed to send udp packet: " + e.getMessage());
						}
						
					} catch (JSONException e) {
						e.printStackTrace();
					}
					
					return null;
				}
			};
			m_task.execute();
		}
		
	}
}

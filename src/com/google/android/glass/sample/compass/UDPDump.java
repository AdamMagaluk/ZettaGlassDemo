package com.google.android.glass.sample.compass;

import android.view.Menu;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;

import android.os.AsyncTask;
import android.os.AsyncTask.Status;


public class UDPDump {
	static private int PORT = 2323;
	static private String TAG = "udpdump";
	
	private DatagramSocket m_socket = null;
	private AsyncTask<Void, Void, Void> m_task = null;
	
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

		if (m_task == null || (m_task != null && m_task.getStatus() == Status.FINISHED) ) {
			m_task = new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					byte[] outBuffer = new java.util.Date().toString().getBytes();
					try {
						DatagramPacket sendPacket = new DatagramPacket(
								outBuffer, outBuffer.length,
								InetAddress.getByName("255.255.255.255"), PORT);
						m_socket.send(sendPacket);
					} catch (IOException e) {
						Log.e(TAG,"Exception Failed to send udp packet: " + e.getMessage());
					}
					return null;
				}
			};
			m_task.execute();
		}
		
	}
}

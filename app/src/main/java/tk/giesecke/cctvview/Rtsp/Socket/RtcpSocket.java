package tk.giesecke.cctvview.Rtsp.Socket;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import tk.giesecke.cctvview.BuildConfig;
import tk.giesecke.cctvview.CCTVview;

/**
 *This class is used to send the rtcp packet to the server
 */
class RtcpSocket {

	private DatagramSocket mSocket;
	private DatagramPacket mPacket;
	private Handler mHandler;
	private String serverIp;
	private int serverPort;
	private boolean isStoped;
	private HandlerThread thread;

	RtcpSocket(int port, String serverIp, int serverPort) {
		if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtcpSocket");
		try {
			this.serverIp = serverIp;
			this.serverPort = serverPort;
			mSocket = new DatagramSocket(port);
			byte[] message = new byte[2048];
			mPacket = new DatagramPacket(message, message.length);
			thread = new HandlerThread("RTCPSocketThread");
			thread.start();
			isStoped = false;
			mHandler = new Handler(thread.getLooper());
		} catch ( SocketException e ) {
			if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtcpSocket Exception " + e.getMessage());
		}
	}

	public void start() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				while ( !isStoped ) {
					try {
						if (mSocket != null) {
							mSocket.receive(mPacket);
						} else {
							if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtcpSocket start mSocket was NULL");
						}
					} catch (IOException e) {
						if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtcpSocket start receive Exception " + e.getMessage());
					}
//					if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "Rtcp rev package " + mPacket.toString());
				}
			}
		});
	}

	void stop(){
		if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtcpSocket stop");
		mSocket.close();
		mSocket = null;
		mPacket = null;
		isStoped = true;
		thread.quit();
	}

	void sendReceiverReport() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				startSendReport();
			}
		}).start();
	}

	private void startSendReport() {
		byte[] sendData = new byte[2];
		sendData[0] = (byte) Integer.parseInt("10000000",2);
		sendData[1] = (byte) 201;
		try {
			mPacket = new DatagramPacket(sendData,sendData.length, InetAddress.getByName(serverIp),serverPort);
		} catch (UnknownHostException e) {
			if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "startSendReport Exception " + e.getMessage());
		}
	}
}

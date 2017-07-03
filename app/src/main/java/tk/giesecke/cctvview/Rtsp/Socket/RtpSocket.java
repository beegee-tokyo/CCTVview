package tk.giesecke.cctvview.Rtsp.Socket;

import android.util.Log;

import tk.giesecke.cctvview.BuildConfig;
import tk.giesecke.cctvview.CCTVview;
import tk.giesecke.cctvview.Rtsp.Stream.RtpStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingDeque;

/**
 *RtpSocket is used to set up the rtp socket and receive the data via udp or tcp protocol
 * 1. set up the socket , four different socket : video udp socket, audio udp socket, video tcp socket, audio tcp socket
 * 2. make a thread to get the data form rtp server
 */
@SuppressWarnings({"AccessStaticViaInstance", "SameParameterValue"})
public class RtpSocket implements Runnable {
	private final static String tag = "RtpSocket";
	// Unused as audio streams are not implemented!!!!
	// private final static int TRACK_VIDEO = 0x01;
	// private final static int TRACK_AUDIO = 0x02;

	private DatagramSocket mUdpSocket;
	private DatagramPacket mUdpPackets;
	private Socket mTcpSocket;
	private BufferedReader mTcpPackets;

	private RtcpSocket mRtcpSocket;
	private Thread mThread;
	private final byte[] message = new byte[2048];
	private final int port;
	private final String ip;
	private final boolean isTcptranslate;
	private RtpStream mRtpStream;
	private final int serverPort;
	private long recordTime = 0;
	private boolean useRtspTcpSocket,isStoped;
	private InputStream  rtspInputStream;
	private final LinkedBlockingDeque<byte[]> tcpBuffer = new LinkedBlockingDeque<>();
	private Thread tcpThread;

	private static class rtspPacketInfo {
		int len;
		int offset;
		boolean inNextPacket;
		public byte[] data;
	}
	private rtspPacketInfo rtspBuffer = new rtspPacketInfo();
	private boolean packetFlag;

// trackType is unused as audio streams are not implemented!!!!
//	public RtpSocket(boolean isTcptranslate, int port, String ip, int serverPort,int trackType) {
	public RtpSocket(boolean isTcptranslate, int port, String ip, int serverPort) {
		if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpSocket");
		this.port = port;
		this.ip = ip;
		this.isTcptranslate = isTcptranslate;
		this.serverPort = serverPort;
		this.isStoped = false;
		if(serverPort == -1) useRtspTcpSocket = false;
		else if(serverPort == -2) useRtspTcpSocket = true;
		if(!isTcptranslate) setupUdpSocket();
	}

	public void setRtspSocket(Socket s) {
		try {
			rtspInputStream = s.getInputStream();
		} catch (IOException e) {
			if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "setRtspSocket Exception " + e.getMessage());
		}
	}

	private void setupUdpSocket() {
		if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "Start to setup the udp socket , the port is:  " + port + "....");
		try {
			mUdpSocket = new DatagramSocket(port);
		} catch (SocketException e) {
			if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpSocket setupUdpSocket exception: " + e.getMessage());
		}
		mUdpPackets = new DatagramPacket(message,message.length);
		mRtcpSocket = new RtcpSocket(port+1,ip,serverPort+1);
		mRtcpSocket.start();
	}

	private void tcpRecombineThread() {
		tcpThread = new Thread(new Runnable() {
			@Override
			public void run() {
				rtspBuffer.inNextPacket = false;
				int offset;
				while (!Thread.interrupted() && !isStoped) {
					try {
						byte[] tcpbuffer = tcpBuffer.take();
						offset = 0;

						if(rtspBuffer.inNextPacket) {
							if(packetFlag) {
								rtspBuffer.len = ((tcpbuffer[0]&0xFF)<<8)|(tcpbuffer[1]&0xFF);
								rtspBuffer.data = new byte[rtspBuffer.len];
								rtspBuffer.offset = 0;
							}

							if(rtspBuffer.len > tcpbuffer.length) {
								System.arraycopy(tcpbuffer,0,rtspBuffer.data,rtspBuffer.offset,tcpbuffer.length);
								rtspBuffer.offset += tcpbuffer.length;
								rtspBuffer.len = rtspBuffer.len - tcpbuffer.length;
								rtspBuffer.inNextPacket = true;
							} else {
								System.arraycopy(tcpbuffer, 0, rtspBuffer.data, rtspBuffer.offset, rtspBuffer.len);
								mRtpStream.receiveData(rtspBuffer.data, rtspBuffer.data.length);
								offset += rtspBuffer.len;
								rtspBuffer.inNextPacket = false;
								analysisOnePacket(tcpbuffer,offset);
							}
						}else{
							analysisOnePacket(tcpbuffer,0);
						}
					} catch (InterruptedException e) {
						if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "tcpRecombineThread - The tcp buffer queue is empty.. " + e.getMessage());
					}
				}
			}
		},"TcpPacketRecombineThread");
		tcpThread.start();
	}

	private void analysisOnePacket(byte[] data, int offset){
		int datalen;
		int[] tmp = getRtspFrameInfo(data,offset);
		byte[] buffer;
		datalen = data.length-tmp[0];
		while(tmp[1] != -1) {
			if(tmp[1] == -2) {
				rtspBuffer.inNextPacket = true;
				packetFlag = true;
				break;
			} else packetFlag = false;
			if(tmp[1] < datalen) {
				//This packet have some rtsp frame
				buffer = new byte[tmp[1]];
				System.arraycopy(data,tmp[0],buffer,0,tmp[1]);
				mRtpStream.receiveData(buffer,buffer.length);
				offset = tmp[0] + tmp[1];
				tmp = getRtspFrameInfo(data,offset);
				datalen = data.length - tmp[0];
				rtspBuffer.inNextPacket = false;
			} else if(tmp[1] > datalen) {
				//This packet have not enough rtsp frame, next packet have some
				rtspBuffer.data = new byte[tmp[1]];
				rtspBuffer.len = tmp[1] - datalen;
				rtspBuffer.offset = datalen;
				if(rtspBuffer.offset != 0){
					System.arraycopy(data,tmp[0],rtspBuffer.data,0,datalen);
				}
				rtspBuffer.inNextPacket = true;
				break;
			} else if(tmp[1] == datalen) {
				buffer = new byte[tmp[1]];
				System.arraycopy(data,tmp[0], buffer, 0, tmp[1]);
				mRtpStream.receiveData(buffer,buffer.length);
				rtspBuffer.inNextPacket = false;
				break;
			}
		}
	}

	private int[] getRtspFrameInfo(byte[] data,int offset){
		int mOffset,length;
		boolean haveRtspFrame = false;
		for(mOffset = offset; mOffset< data.length-1; ++mOffset){
			if(data[mOffset] == 0x24 && data[mOffset+1] == 0x00) {
				haveRtspFrame = true;
				break;
			}
			haveRtspFrame = false;
		}
		if(haveRtspFrame) {
			if(mOffset + 3 < data.length) {
				length = ((data[mOffset + 2] & 0xFF) << 8) | (data[mOffset + 3] & 0xFF);
				mOffset += 4;
			} else length = -2; //This time 0x24 0x00 and data length is not in one packet
		}
		else
			length = -1;
		return new int[]{mOffset, length};
	}

	private void useRtspTcpReading() {
		int len;
		byte[] buffer = new byte[1024*10];
		try {
			while((len = rtspInputStream.read(buffer)) != -1) {
				byte[] tcpbuffer = new byte[len];
				System.arraycopy(buffer,0,tcpbuffer,0,len);
				try {
					tcpBuffer.put(tcpbuffer);
				} catch (InterruptedException e) {
					if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpSocket useRtspTcpReading - The tcp buffer queue is full.. " + e.getMessage());
				}
			}
		} catch (IOException e) {
			if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpSocket useRtspTcpReading exception: " + e.getMessage());
		}
	}

	private void setupTcpSocket() {
		Log.d(tag, "Start to setup the tcp socket , the ip is: " + ip + ", the port is: " + port +"....");
		try {
			mTcpSocket = new Socket(ip,port);
		} catch (IOException e) {
			if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpSocket setupTcpSocket exception: " + e.getMessage());
		}
		try {
			mTcpPackets = new BufferedReader(new InputStreamReader(mTcpSocket.getInputStream()));
		} catch (IOException e) {
			if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpSocket setupTcpSocket exception: " + e.getMessage());
		}
		if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "setupTcpSocket");
	}

	public void startRtpSocket() {
		if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "startRtpSocket");
		mThread = new Thread(this,"RTPSocketThread");
		mThread.start();
	}

	private void startTcpReading(){
		String readLine;
		try {
			while ( (readLine = mTcpPackets.readLine()) != null ) {
				if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "the tcp read data is: " + readLine);
			}
		} catch (IOException e) {
			if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpSocket startTcpReading exception: " + e.getMessage());
		}
	}

	private void startUdpReading() {
		long currentTime;
		if (isStoped) {
			return;
		}
		try {
			mUdpSocket.receive(mUdpPackets);
		} catch (IOException e) {
			if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpSocket startUdpReading exception: " + e.getMessage());
		}
		if (mUdpPackets == null) { // Maybe in stopping status
			Log.d(CCTVview.DEBUG_LOG_TAG, "RtpSocket startUdpReading mUdpPackets was NULL");
			return;
		}
		byte[] buffer = new byte[mUdpPackets.getLength()];
		System.arraycopy(mUdpPackets.getData(), 0, buffer, 0, mUdpPackets.getLength());
		//Use Rtp stream thread to decode the receive data
		mRtpStream.receiveData(buffer, mUdpPackets.getLength());

		//every 30s send a rtcp packet to server
		currentTime = System.currentTimeMillis();
		if(currentTime-30000 > recordTime) {
			recordTime = currentTime;
			mRtcpSocket.sendReceiverReport();
		}
	}

	public void setStream(RtpStream stream) {
		mRtpStream = stream;
	}

	@Override
	public void run() {
		if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpSocket start to get rtp data via socket...");
		if(isTcptranslate) {
			tcpRecombineThread();
			if(useRtspTcpSocket) {
				useRtspTcpReading();
			}
			else{
				setupTcpSocket();
				startTcpReading();
			}
		} else {
			while ( !Thread.interrupted() )
				startUdpReading();
		}
	}

	public void stop(){
		isStoped = true;
		if(isTcptranslate) {
			if(mTcpSocket != null) {
				try {
					mTcpSocket.close();
				} catch (IOException e) {
					if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpSocket - stop exception: " + e.getMessage());
				}
				mTcpPackets = null;
			}
		}
		else{
			mUdpSocket.close();
			mUdpPackets = null;
		}
		if(mRtcpSocket!=null){
			mRtcpSocket.stop();
			mRtcpSocket = null;
		}
		if(mThread!=null) mThread.interrupt();
		if(rtspBuffer!=null) rtspBuffer=null;
		if(tcpThread!=null) tcpThread.interrupt();
	}
}

package tk.giesecke.cctvview.Rtsp.Stream;

import android.os.HandlerThread;
import android.os.Handler;
import android.util.Log;

import java.util.concurrent.LinkedBlockingDeque;

import tk.giesecke.cctvview.BuildConfig;
import tk.giesecke.cctvview.CCTVview;

/**
 *This class is used to analysis the data from rtp socket , recombine it to video or audio stream
 * 1. get the data from rtp socket
 * 2. put the data into buffer
 * 3. use the thread to get the data from buffer, and unpack it
 */
public abstract class RtpStream {

	// Unused as audio streams are not implemented!!!!
	// protected final static int TRACK_VIDEO = 0x01;
	// protected final static int TRACK_AUDIO = 0x02;

	private final Handler mHandler;
	private byte[] buffer;
	private final HandlerThread thread;
	private boolean isStoped;

	protected class StreamPacks {
//		boolean mark;
		public int pt;
//		long timestamp;
//		int sequenceNumber;
//		long Ssrc;
		public byte[] data;
	}

	private static class bufferUnit {
		public byte[] data;
		int len;
	}

	private static final LinkedBlockingDeque<bufferUnit> bufferQueue = new LinkedBlockingDeque<>();

	protected RtpStream() {
		thread = new HandlerThread("RTPStreamThread");
		thread.start();
		mHandler = new Handler(thread.getLooper());
		unpackThread();
		isStoped = false;
	}

	public static void receiveData(byte[] data, int len) {
		bufferUnit tmpBuffer = new bufferUnit();
		tmpBuffer.data = new byte[len];
		System.arraycopy(data,0,tmpBuffer.data,0,len);
		tmpBuffer.len = len;
		try {
			bufferQueue.put(tmpBuffer);
		} catch (InterruptedException e) {
			if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpStream - receiveData exception: " + e.getMessage());
		}
	}

	private void unpackThread() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				bufferUnit tmpBuffer;
				while (!isStoped) {
					try {
						tmpBuffer = bufferQueue.take();
						buffer = new byte[tmpBuffer.len];
						System.arraycopy(tmpBuffer.data,0,buffer,0,tmpBuffer.len);
						unpackData();
					} catch (InterruptedException e) {
						if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpStream - unpackThread exception: " + e.getMessage());
					}
				}
				buffer = null;
				bufferQueue.clear();
			}
		});
	}

	protected void stop(){
		isStoped = true;
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpStream - stop exception: " + e.getMessage());
		}
		bufferQueue.clear();
		buffer = null;
		thread.quit();
	}


	protected abstract void recombinePacket(StreamPacks sp);

	private void unpackData() {
		StreamPacks tmpStreampack = new StreamPacks();
		if(buffer.length == 0) return;
		int rtpVersion = (buffer[0]&0xFF)>>6;
		if(rtpVersion != 2) {
			if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "RtpStream This is not a rtp packet.");
			return;
		}
//		tmpStreampack.mark = (buffer[1] & 0xFF & 0x80) >> 7 == 1;
		tmpStreampack.pt = buffer[1] & 0x7F;
//		tmpStreampack.sequenceNumber = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
//		tmpStreampack.timestamp = Long.parseLong(Integer.toHexString(((buffer[4] & 0xFF) << 24) | ((buffer[5]&0xFF) << 16) | ((buffer[6]&0xFF) << 8) | (buffer[7]&0xFF)) , 16);
//		tmpStreampack.Ssrc = ((buffer[8]&0xFF) << 24) | ((buffer[9]&0xFF) << 16) | ((buffer[10]&0xFF) << 8) | (buffer[11]&0xFF);
		tmpStreampack.data = new byte[buffer.length-12];

		System.arraycopy(buffer, 12, tmpStreampack.data, 0, buffer.length - 12);

		recombinePacket(tmpStreampack);
	}
}

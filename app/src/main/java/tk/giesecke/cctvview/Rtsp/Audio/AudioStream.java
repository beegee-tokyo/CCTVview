package tk.giesecke.cctvview.Rtsp.Audio;

import android.util.Log;

import tk.giesecke.cctvview.BuildConfig;
import tk.giesecke.cctvview.CCTVview;
import tk.giesecke.cctvview.Rtsp.Stream.RtpStream;

/**
 *
 */
public abstract class AudioStream extends RtpStream {

	protected void recombinePacket(StreamPacks streamPacks) {
		if (BuildConfig.DEBUG) Log.d(CCTVview.DEBUG_LOG_TAG, "AudioStream - recombinePacket");
	}
}

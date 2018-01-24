package tk.giesecke.cctvview;

import android.app.Service;
import android.content.Intent;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;

import com.github.druk.rxdnssd.BonjourService;
import com.github.druk.rxdnssd.RxDnssd;
import com.github.druk.rxdnssd.RxDnssdBindable;

import java.net.InetAddress;
import java.util.Map;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

import static tk.giesecke.cctvview.CCTVview.DEBUG_LOG_TAG;
import static tk.giesecke.cctvview.CCTVview.spmIP;

public class findSPMbyMDNS extends Service {

	/** Service type for Arduino devices */
	private static final String SERVICE_TYPE = "_arduino._tcp.";
	/* Available services:
	 _workstation
	 _UnoWiFi
	 _udisks-ssh
	 _airplay
	 _raop
	 _xbmc-events
	 _xbmc-jsonrpc
	 _xbmc-jsonrpc-h
	 _http
	 _sftp-ssh
	 _ssh
	 _arduino
	 */

	/** Countdown timer to stop the discovery after some time */
	private CountDownTimer timer = null;

	/** RxDnssd bindable */
	private RxDnssd rxDnssd;
	/** Service browser subscription */
	private Subscription browseSubscription;

	@Override
	public IBinder onBind(Intent intent) {
		// TODO: Return the communication channel to the service.
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public int onStartCommand( Intent intent, int flags, int startId ) {

		// Start service discovery if not running already
		rxDnssd = new RxDnssdBindable(this.getApplicationContext());
		if (browseSubscription == null) {
			startBrowse();
		}

		// Start a countdown to stop the service after 15 seconds
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
		timer = new CountDownTimer(15000, 1000) {
			public void onTick(long millisUntilFinished) {
				//Nothing here!
			}

			public void onFinish() {
				stopBrowse();
				timer.cancel();
				timer = null;
				if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "RxDns Discovery finished!");
				stopSelf();
			}
		};
		timer.start();

		return super.onStartCommand( intent, flags, startId );
	}

	/**
	 * Start service discovery
	 *
	 */
	private void startBrowse() {
		String discoverService;
		if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "RxDns Start discovery services for " + SERVICE_TYPE);
		discoverService = SERVICE_TYPE;

		browseSubscription = rxDnssd.browse(discoverService, "local.")
				.compose(rxDnssd.resolve())
				.compose(rxDnssd.queryRecords())
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Action1<BonjourService>() {
					@Override
					public void call(BonjourService bonjourService) {
						if (bonjourService.isLost()) {
							if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "RxDns - lost: " + bonjourService.toString());
						}
						else {
							if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "RxDns - found: " + bonjourService.toString());
							InetAddress address = bonjourService.getInet4Address();
							if (address == null) {
								return; // No IPv4 address found for this service
							}
							Map<String, String> txtRecord = bonjourService.getTxtRecords();
							if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "RxDns - txtRecord: " + txtRecord.toString());
							if (txtRecord.containsKey("service") && txtRecord.get("service").equalsIgnoreCase("MHC")) {
								if (txtRecord.containsKey("type")) {
									if (txtRecord.get("type").equalsIgnoreCase("Solar Panel Monitor")) {
										spmIP = address.toString().substring(1);
										if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "RxDns Found SPM!");
//										timer.cancel();
										stopBrowse();
										timer.cancel();
										timer = null;
										if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "RxDns Discovery finished!");
										stopSelf();
									}
								}
							}
						}
					}
				}, new Action1<Throwable>() {
					@Override
					public void call(Throwable throwable) {
						if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "RxDns - error", throwable);
					}
				});
	}

	/**
	 * Stop service discovery
	 *
	 */
	private void stopBrowse() {
		if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "RxDns - Stop browsing");
		browseSubscription.unsubscribe();
		browseSubscription = null;
	}
}

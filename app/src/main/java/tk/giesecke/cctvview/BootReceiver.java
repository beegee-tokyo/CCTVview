package tk.giesecke.cctvview;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

public class BootReceiver extends BroadcastReceiver {

	Context bootContext;

	@Override
	public void onReceive(Context context, Intent intent) {
		bootContext = context;
		Handler mHandler = new Handler();
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				Intent myIntent = new Intent(bootContext, CCTVview.class);
				myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				bootContext.startActivity(myIntent);
			}
		},10000);
	}
}

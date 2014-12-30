/*
 * This file is part of Network Spoofer for Android.
 * Network Spoofer lets you change websites on other people’s computers
 * from an Android phone.
 * Copyright (C) 2014 Will Shackleton <will@digitalsquid.co.uk>
 *
 * Network Spoofer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Network Spoofer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Network Spoofer, in the file COPYING.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package uk.digitalsquid.netspoofer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import uk.digitalsquid.netspoofer.UpdateChecker.OnUpdateListener;
import uk.digitalsquid.netspoofer.UpdateChecker.UpdateInfo;
import uk.digitalsquid.netspoofer.config.FileFinder;
import uk.digitalsquid.netspoofer.config.FileInstaller;
import uk.digitalsquid.netspoofer.config.LogConf;
import uk.digitalsquid.netspoofer.misc.AsyncTaskHelper;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager.BadTokenException;
import android.widget.Button;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

public class NetSpoof extends Activity implements OnClickListener, LogConf, OnUpdateListener {
	/**
	 * A dialog to tell the user to mount their SD card.
	 */
	static final int DIALOG_R_SD = 1;
	/**
	 * A dialog to tell the user to mount their SD card rw.
	 */
	static final int DIALOG_W_SD = 2;
	static final int DIALOG_ROOT = 3;
	static final int DIALOG_BB = 4;
	/**
	 * BB found, no chroot.
	 */
	static final int DIALOG_BB_2 = 6;
	static final int DIALOG_ABOUT = 5;
	static final int DIALOG_AGREEMENT = 7;
	static final int DIALOG_CHANGELOG = 8;

	static final int DIALOG_UPDATE_AVAILABLE = 9;
	
	private Button startButton;
	
	private boolean showChangelog = false;
	
	private UpdateChecker updateChecker;

	private SharedPreferences prefs;
	/** Called when the activity is first created. */
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	
		startButton = (Button) findViewById(R.id.startButton);
		startButton.setOnClickListener(this);
		findViewById(R.id.about).setOnClickListener(this);
		
		try {
			updateChecker = new UpdateChecker((App) getApplication(), this);
			if(Build.VERSION.SDK_INT <= 10)
				updateChecker.execute();
			else
				updateChecker.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		} catch (NameNotFoundException e1) {
			Log.e(TAG, "Failed to initialise UpdateChecker", e1);
		}
		AsyncTaskHelper.execute(loadTask);
		
		// Changelog dialog
		int versionCode = -1;
		try {
			versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		int oldVersionCode = prefs.getInt("oldVersion", -1);
		Log.v(TAG, "Old version is " + oldVersionCode + ", new version is " + versionCode);
		if(versionCode != -1 && versionCode > oldVersionCode) {
			Log.i(TAG, "Displaying changelog");
			prefs.edit().putInt("oldVersion", versionCode).commit();
			showChangelog = true;
		}
		
		if(prefs.getBoolean("firstTime", true)) { // First time, show license thing
			showDialog(DIALOG_AGREEMENT);
		} else postInit(); // This isn't called on first time, as new users don't need to see changelog
		
		// Google analytics
		Tracker t = ((App)getApplication()).getTracker();
		if(t != null) {
			t.setScreenName(getClass().getCanonicalName());
			t.send(new HitBuilders.AppViewBuilder().build());
		}
	}
	
	/**
	 * Non-important tasks after initialisation & agreement
	 */
	public void postInit() {
		if(showChangelog)
			showDialog(DIALOG_CHANGELOG);
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()) {
			case R.id.startButton:
				startActivity(new Intent(this, SpoofSelector.class));
				break;
			case R.id.about:
				startActivity(new Intent(this, About.class));
				break;
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	protected Dialog onCreateDialog(int id, final Bundle args) {
		super.onCreateDialog(id, args);
		Dialog dialog = null;
		AlertDialog.Builder builder;
		View view;
		switch(id) {
			case DIALOG_R_SD:
				builder = new AlertDialog.Builder(this);
				builder.setMessage("Please connect SD card / exit USB mode to continue.")
					.setCancelable(false)
					.setPositiveButton("OK", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							NetSpoof.this.finish();
						}
					});
				dialog = builder.create();
				break;
			case DIALOG_W_SD:
				builder = new AlertDialog.Builder(this);
				builder.setMessage("Please set the SD Card to writable. May work without but expect problems.")
					.setCancelable(false)
					.setPositiveButton("OK", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) { }
					});
				dialog = builder.create();
				break;
			case DIALOG_ROOT:
				builder = new AlertDialog.Builder(this);
				builder.setMessage("Please root your phone before using this application. Search the internet for instructions on how to do this for your phone.\nA custom firmware (such as CyanogenMod) is also recommended.\n" +
				"If the 'su' application is somewhere else on your phone, please specify it in the settings.")
					.setCancelable(false)
					.setPositiveButton("OK", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) { }
					});
				dialog = builder.create();
				break;
			case DIALOG_BB:
				builder = new AlertDialog.Builder(this);
				builder.setMessage("Please install Busybox (either manually or from the Android Market) before using this application. Network Spoofer will try to run, but may be unstable as Busybox is missing.")
					.setCancelable(false)
					.setPositiveButton("OK", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) { }
					});
				dialog = builder.create();
			case DIALOG_BB_2:
				builder = new AlertDialog.Builder(this);
				builder.setTitle("So close..");
				builder.setMessage("You have Busybox installed, but it doesn't appear to have a required component '" + missingBBComponent + "'. Please update Busybox or try a different version of Busybox. Network Spoofer will most likely not work until you have updated Busybox")
					.setCancelable(false)
					.setPositiveButton("OK", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) { }
					});
				dialog = builder.create();
				break;
			case DIALOG_AGREEMENT:
				builder = new AlertDialog.Builder(this);
				view = getLayoutInflater().inflate(R.layout.agreement, null);
				builder.setView(view);
				builder.setTitle(R.string.agreementTitle);
				builder.setCancelable(false);
				builder.setPositiveButton(R.string.agreementPositive, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						prefs.edit().putBoolean("firstTime", false).commit();
						postInit();
					}
				});
				builder.setNegativeButton(R.string.agreementNegative, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				});
				dialog = builder.create();
				break;
			case DIALOG_CHANGELOG:
				builder = new AlertDialog.Builder(this);
				view = getLayoutInflater().inflate(R.layout.changelog, null);
				builder.setView(view);
				builder.setTitle(R.string.agreementTitle);
				builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				});
				dialog = builder.create();
				break;
			case DIALOG_UPDATE_AVAILABLE:
				final UpdateInfo info = (UpdateInfo) args.get("info");
				builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.updateAvailableTitle);
				builder.setMessage(
						getResources().getString(
								R.string.updateAvailableDescription,
								info.versionName));
				builder.setPositiveButton(R.string.updateAvailableYes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Intent update = new Intent(Intent.ACTION_VIEW, Uri.parse(info.url));
						startActivity(update);
					}
				});
				builder.setNegativeButton(R.string.updateAvailableNo, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				});
				dialog = builder.create();
				break;
		}
		return dialog;
	}
	
	private String missingBBComponent = "?";
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.netspoofmenu, menu);
	    return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.netSpoofMenuItemPrefs:
	    	startActivity(new Intent(this, Preferences.class));
	        return true;
	    case R.id.netSpoofMenuItemAbout:
	    	startActivity(new Intent(this, About.class));
	    	return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}
		
	private AsyncTask<Void, Integer, Void> loadTask = new AsyncTask<Void, Integer, Void>() {
		
		@Override
		protected Void doInBackground(Void... params) {
			if(prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			
			// Install scripts & BB
			installFiles();
	
			// Find files etc.
			try {
				FileFinder.initialise(getApplicationContext());
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				if(e.getMessage().equals("su")) {
					publishProgress(DIALOG_ROOT);
				} else if(e.getMessage().equals("busybox")) {
					publishProgress(DIALOG_BB);
				} else if(e.getMessage().startsWith("bb:")) {
					missingBBComponent = e.getMessage().substring(2); // 2 = end of bb:
					publishProgress(DIALOG_BB_2);
				}
			}

			if(Build.VERSION.SDK_INT >= 18) { // 4.3
				Log.w(TAG, "Running Android 4.3 or above, with known quirks");
				Log.w(TAG, String.format("Env says ext storage is %s, using /sdcard because of root issues",
						Environment.getExternalStorageDirectory()));
			}
			
			return null;
		}
		
		/**
		 * Shows the dialog with the given ID.
		 */
		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
			try {
				showDialog(values[0]);
			} catch(BadTokenException e) {
				Log.w(TAG, "Activity not visible, tryed to show dialog", e);
			}
		}
		
		@Override
		protected void onPostExecute(Void result) {
			startButton.setEnabled(true);
			findViewById(R.id.loading).setVisibility(View.INVISIBLE);
		}
		
		/**
		 * Installs scripts & files into the data folder.
		 */
		private void installFiles() {
			try {
				FileInstaller fi = new FileInstaller(getBaseContext());
				
				fi.installScript("busybox", R.raw.busybox);
				
				fi.installScript("arpspoof", R.raw.arpspoof);
				fi.installScript("iptables", R.raw.iptables);
				fi.installScript("spoof", R.raw.spoof);
				
				// Remove old debimg file
				if(getExternalFilesDir(null) != null) {
					File imgFolder = new File(
							getExternalFilesDir(null).getAbsolutePath() + "/img");
					String[] files = imgFolder.list();
					if(files != null) {
						for(String file : files)
							new File(imgFolder, file).delete();
					}
					imgFolder.delete();
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (NotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(TAG, "Failed to install scripts.");
			}
		}
	};
	@Override
	public void updateAvailable(UpdateInfo info) {
		Bundle args = new Bundle();
		args.putParcelable("info", info);
		showDialog(DIALOG_UPDATE_AVAILABLE, args);
	}
}
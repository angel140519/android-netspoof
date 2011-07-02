/*
 * This file is part of Network Spoofer for Android.
 * Network Spoofer - change and mess with webpages and the internet on
 * other people's computers
 * Copyright (C) 2011 Will Shackleton
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

package uk.digitalsquid.netspoofer.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import uk.digitalsquid.netspoofer.InstallService;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.os.Environment;

public final class ConfigChecker implements Config {
	
	public static final boolean checkInstalled(Context context) {
		if(getSDStatus(false)) {
			final File sd = context.getExternalFilesDir(null);
			File debian = new File(sd.getAbsolutePath() + "/" + DEB_VERSION_FILE);
			if(debian.exists()) return true;
		}
		return false;
	}
	
	public static final boolean checkInstalledLatest(Context context) {
		if(getSDStatus(false)) {
			final File sd = context.getExternalFilesDir(null);
			File version = new File(sd.getAbsolutePath() + "/" + DEB_VERSION_FILE);
			String ver;
			try {
				FileInputStream verReader = new FileInputStream(version);
				BufferedReader reader = new BufferedReader(new InputStreamReader(verReader));
				ver = reader.readLine();
				reader.close();
				verReader.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return false;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			if(ver != null) {
				try {
				if(Integer.parseInt(ver) >= DEB_IMG_URL_VERSION) return true;
				} catch(NumberFormatException e) {
					e.printStackTrace();
					return false;
				}
			}
		}
		return false;
	}

	public static final boolean getSDStatus(final boolean checkWritable) {
		String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state)) {
		    // We can read and write the media
			return true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			return !checkWritable;
		} else {
			return false;
		}
	}
	
	private static ActivityManager am;
	public static final boolean isInstallServiceRunning(Context context) {
		if(am == null) 
		    am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
	    	if (InstallService.class.getName().equals(service.service.getClassName())) {
	            return true;
	    	}
	    }
	    return false;
	}
}

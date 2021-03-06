package us.shandian.giga.get;

import android.os.Handler;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

import static us.shandian.giga.BuildConfig.DEBUG;

public class DownloadRunnable implements Runnable
{
	private static final String TAG = DownloadRunnable.class.getSimpleName();
	
	private DownloadMission mMission;
	private Handler mHandler;
	private int mId;
	
	public DownloadRunnable(DownloadMission mission, Handler handler, int id) {
		mMission = mission;
		mHandler = handler;
		mId = id;
	}
	
	@Override
	public void run() {
		boolean retry = mMission.recovered;
		long position = mMission.getPosition(mId);
		
		if (DEBUG) {
			Log.d(TAG, mId + ":default pos " + position);
		}
		
		while (mMission.errCode == -1 && mMission.running && position < mMission.blocks) {
			
			if (Thread.currentThread().isInterrupted()) {
				mMission.pause();
				return;
			}
			
			// Wait for an unblocked position
			while (!retry && position < mMission.blocks && mMission.isBlockPreserved(position)) {
				
				if (DEBUG) {
					Log.d(TAG, mId + ":position " + position + " preserved, passing");
				}
				
				position++;
			}
			
			retry = false;
			
			if (position >= mMission.blocks) {
				break;
			}
			
			if (DEBUG) {
				Log.d(TAG, mId + ":preserving position " + position);
			}
			
			// TODO: Thread may be interrupted suddenly, so a preserved block may not have been downloaded
			// Maybe we need some new methods to calculate if a block is downloaded
			mMission.preserveBlock(position);
			mMission.setPosition(mId, position);
			
			long start = position * DownloadManager.BLOCK_SIZE;
			long end = start + DownloadManager.BLOCK_SIZE - 1;
			
			if (end >= mMission.length) {
				end = mMission.length - 1;
			}
			
			HttpURLConnection conn = null;
			
			int total = 0;
			
			try {
				URL url = new URL(mMission.url);
				conn = (HttpURLConnection) url.openConnection();
				conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
				
				// A server may be ignoring the range requet
				if (conn.getResponseCode() != 206) {
					mMission.errCode = DownloadMission.ERROR_SERVER_UNSUPPORTED;
					notifyError(DownloadMission.ERROR_SERVER_UNSUPPORTED);
					
					if (DEBUG) {
						Log.e(TAG, mId + ":Unsupported");
					}
					
					break;
				}
				
				if (DEBUG) {
					Log.d(TAG, mId + ":" + conn.getRequestProperty("Range"));
					Log.d(TAG, mId + ":Content-Length=" + conn.getContentLength() + " Code:" + conn.getResponseCode());
				}
				
				RandomAccessFile f = new RandomAccessFile(mMission.location + "/" + mMission.name, "rw");
				f.seek(start);
				BufferedInputStream ipt = new BufferedInputStream(conn.getInputStream());
				byte[] buf = new byte[512];
				
				while (start < end && mMission.running) {
					int len = ipt.read(buf, 0, 512);
					
					if (len == -1) {
						break;
					} else {
						start += len;
						total += len;
						f.write(buf, 0, len);
						notifyProgress(len);
					}
				}
				
				if (DEBUG) {
					Log.d(TAG, mId + ":position " + position + " finished, total length " + total);
				}
				
				f.close();
				ipt.close();
				
				if (!mMission.running) {
					return;
				}
			} catch (Exception e) {
				// TODO Retry count limit & notify error
				retry = true;
				
				notifyProgress(-total);
				
				if (DEBUG) {
					Log.d(TAG, mId + ":position " + position + " retrying");
				}
			}
		}
		
		if (DEBUG) {
			Log.d(TAG, "thread " + mId + " finished");
		}
		
		if (mMission.errCode == -1) {
			if (DEBUG) {
				Log.d(TAG, "no error has happened, notifying");
			}
			notifyFinished();
		}
	}
	
	private void notifyProgress(final long len) {
		synchronized (mMission) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					mMission.notifyProgress(len);
				}
			});
		}
	}
	
	private void notifyError(final int err) {
		synchronized (mMission) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					mMission.notifyError(err);
					mMission.pause();
				}
			});
		}
	}
	
	private void notifyFinished() {
		synchronized (mMission) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					mMission.notifyFinished();
				}
			});
		}
	}
}

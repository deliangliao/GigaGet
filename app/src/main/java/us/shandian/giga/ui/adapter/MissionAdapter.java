package us.shandian.giga.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import android.support.v7.widget.RecyclerView;

import java.io.File;

import us.shandian.giga.R;
import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.ui.common.ProgressDrawable;
import us.shandian.giga.ui.main.DetailActivity;
import us.shandian.giga.util.Utility;

public class MissionAdapter extends RecyclerView.Adapter<MissionAdapter.ViewHolder>
{
	private static final int[] BACKGROUNDS = new int[]{
		R.color.blue,
		R.color.green,
		R.color.orange,
		R.color.gray,
		R.color.purple
	};
	
	private static final int[] FOREGROUNDS = new int[]{
		R.color.blue_dark,
		R.color.green_dark,
		R.color.orange_dark,
		R.color.gray_dark,
		R.color.purple_dark
	};
	
	private Context mContext;
	private LayoutInflater mInflater;
	private DownloadManager mManager;
	
	public MissionAdapter(Context context, DownloadManager manager) {
		mContext = context;
		mManager = manager;
		
		mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	@Override
	public MissionAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		final ViewHolder h =  new ViewHolder(mInflater.inflate(R.layout.mission_item, parent, false));
		
		h.menu.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				buildPopup(h);
			}
		});
		
		h.itemView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showDetail(h);
			}
		});
		
		return h;
	}

	@Override
	public void onViewRecycled(MissionAdapter.ViewHolder h) {
		super.onViewRecycled(h);
		h.mission.removeListener(h.observer);
		h.mission = null;
		h.observer = null;
		h.progress = null;
		h.position = -1;
		h.lastTimeStamp = -1;
		h.lastDone = -1;
		h.colorId = 0;
	}

	@Override
	public void onBindViewHolder(MissionAdapter.ViewHolder h, int pos) {
		DownloadMission ms = mManager.getMission(pos);
		h.mission = ms;
		h.position = pos;
		h.letter.setText(ms.name.substring(0, 1));
		h.name.setText(ms.name);
		h.size.setText(Utility.formatBytes(ms.length));
		
		int first = (ms.name.charAt(0) + ms.name.charAt(ms.name.length() - 1)) / ms.name.length();
		h.colorId = first % BACKGROUNDS.length;
		h.progress = new ProgressDrawable(mContext, BACKGROUNDS[h.colorId], FOREGROUNDS[h.colorId]);
		h.bkg.setBackgroundDrawable(h.progress);
		
		h.observer = new MissionObserver(this, h);
		ms.addListener(h.observer);
		
		updateProgress(h);
	}

	@Override
	public int getItemCount() {
		return mManager.getCount();
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	private void updateProgress(ViewHolder h) {
		long now = System.currentTimeMillis();
		
		if (h.lastTimeStamp == -1) {
			h.lastTimeStamp = now;
		}
		
		if (h.lastDone == -1) {
			h.lastDone = h.mission.done;
		}
		
		long deltaTime = now - h.lastTimeStamp;
		long deltaDone = h.mission.done - h.lastDone;
		
		if (deltaTime == 0 || deltaTime > 1000) {
			if (h.mission.errCode > 0) {
				h.status.setText(R.string.msg_error);
			} else {
				float progress = (float) h.mission.done / h.mission.length;
				h.status.setText(String.format("%.2f%%", progress * 100));
				h.progress.setProgress(progress);
			
			}
		}
		
		if (deltaTime > 1000 && deltaDone > 0) {
			float speed = (float) deltaDone / deltaTime;
			String speedStr = Utility.formatSpeed(speed * 1000);
			String sizeStr = Utility.formatBytes(h.mission.length);
			
			h.size.setText(sizeStr + " " + speedStr);
			
			h.lastTimeStamp = now;
			h.lastDone = h.mission.done;
		}
	}
	
	private void showDetail(ViewHolder h) {
		if (h.mission.finished) return;
		
		// Pass the manager
		DetailActivity.sManager = mManager;
		
		Intent i = new Intent();
		i.setAction(Intent.ACTION_MAIN);
		i.setClass(mContext, DetailActivity.class);
		i.putExtra("colorId", h.colorId);
		i.putExtra("id", h.position);
		mContext.startActivity(i);
	}
	
	private void buildPopup(final ViewHolder h) {
		PopupMenu popup = new PopupMenu(mContext, h.menu);
		popup.inflate(R.menu.mission);
		
		Menu menu = popup.getMenu();
		MenuItem start = menu.findItem(R.id.start);
		MenuItem pause = menu.findItem(R.id.pause);
		MenuItem view = menu.findItem(R.id.view);
		MenuItem delete = menu.findItem(R.id.delete);
		
		// Set to false first
		start.setVisible(false);
		pause.setVisible(false);
		view.setVisible(false);
		delete.setVisible(false);
		
		if (!h.mission.finished) {
			if (!h.mission.running) {
				if (h.mission.errCode == -1) {
					start.setVisible(true);
				}
				
				delete.setVisible(true);
			} else {
				pause.setVisible(true);
			}
		} else {
			view.setVisible(true);
		}
		
		popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				switch (item.getItemId()) {
					case R.id.start:
						mManager.resumeMission(h.position);
						return true;
					case R.id.pause:
						mManager.pauseMission(h.position);
						h.lastTimeStamp = -1;
						h.lastDone = -1;
						return true;
					case R.id.view:
						Intent i = new Intent();
						i.setAction(Intent.ACTION_VIEW);
						File f = new File(h.mission.location + "/" + h.mission.name);
						String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(Utility.getFileExt(h.mission.name).substring(1));
						
						if (f.exists()) {
							i.setDataAndType(Uri.fromFile(f), mime);
							mContext.startActivity(i);
						}
						
						return true;
					case R.id.delete:
						mManager.deleteMission(h.position);
						notifyDataSetChanged();
						return true;
					default:
						return false;
				}
			}
		});
		
		popup.show();
	}
	
	static class ViewHolder extends RecyclerView.ViewHolder {
		public DownloadMission mission;
		public int position;
		
		public TextView status;
		public TextView letter;
		public TextView name;
		public TextView size;
		public View bkg;
		public ImageView menu;
		public ProgressDrawable progress;
		public MissionObserver observer;
		
		public long lastTimeStamp = -1;
		public long lastDone = -1;
		public int colorId = 0;
		
		public ViewHolder(View v) {
			super(v);
			
			status = Utility.findViewById(v, R.id.item_status);
			letter = Utility.findViewById(v, R.id.item_letter);
			name = Utility.findViewById(v, R.id.item_name);
			size = Utility.findViewById(v, R.id.item_size);
			bkg = Utility.findViewById(v, R.id.item_bkg);
			menu = Utility.findViewById(v, R.id.item_more);
		}
	}
	
	static class MissionObserver implements DownloadMission.MissionListener {
		private MissionAdapter mAdapter;
		private ViewHolder mHolder;
		
		public MissionObserver(MissionAdapter adapter, ViewHolder holder) {
			mAdapter = adapter;
			mHolder = holder;
		}
		
		@Override
		public void onProgressUpdate(long done, long total) {
			mAdapter.updateProgress(mHolder);
		}

		@Override
		public void onFinish() {
			//mAdapter.mManager.deleteMission(mHolder.position);
			// TODO Notification
			//mAdapter.notifyDataSetChanged();
			mHolder.size.setText(Utility.formatBytes(mHolder.mission.length));
		}

		@Override
		public void onError(int errCode) {
			mAdapter.updateProgress(mHolder);
		}
		
	}
}

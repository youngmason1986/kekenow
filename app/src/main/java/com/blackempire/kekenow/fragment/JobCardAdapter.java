package com.blackempire.kekenow.fragment;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.blackempire.kekenow.R;
import com.blackempire.kekenow.model.FareBand;
import com.blackempire.kekenow.model.RideRecord;

import java.util.List;
import java.util.Locale;

/**
 * ListView adapter for the rider job list.
 *
 * Each row shows the passenger destination, distance, fare band, request
 * time and a coloured status badge. Tapping a row opens the ride detail
 * screen (see {@link RiderFragment}).
 *
 * Framework-only (android.widget.BaseAdapter) — no RecyclerView, so the
 * offline build stays dependency-free.
 */
public class JobCardAdapter extends BaseAdapter {

    public interface OnJobClickListener {
        void onJobClick(RideRecord record);
    }

    private List<RideRecord> records;
    private final OnJobClickListener listener;

    public JobCardAdapter(List<RideRecord> records, OnJobClickListener listener) {
        this.records  = records;
        this.listener = listener;
    }

    public void setRecords(List<RideRecord> records) {
        this.records = records;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return records != null ? records.size() : 0;
    }

    @Override
    public RideRecord getItem(int position) {
        return records.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_job_card, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        bind(holder, getItem(position));
        return convertView;
    }

    private void bind(ViewHolder holder, RideRecord record) {
        // Destination
        String dest = (record.destName != null && !record.destName.isEmpty())
            ? record.destName : String.format(Locale.US, "%.4f, %.4f", record.destLat, record.destLon);
        holder.tvDestination.setText(dest);

        // Distance
        double km = record.distanceMetres / 1000.0;
        holder.tvDistance.setText(String.format(Locale.US, "%.1f km", km));

        // Fare
        FareBand fare = new FareBand(record.fareMin, record.fareMax);
        holder.tvFare.setText(fare.toDisplayString());

        // Time
        String time = record.createdAt != null ? record.createdAt : "—";
        // Show only the time portion if it's ISO-8601
        if (time.contains("T")) {
            String[] parts = time.split("T");
            if (parts.length > 1) {
                time = parts[1].replace("Z", "").split("\\.")[0]; // HH:MM:SS
            }
        }
        holder.tvTime.setText(time);

        // Status badge
        holder.tvStatus.setText(statusLabel(record.status));
        int badgeColor = statusColor(holder.itemView, record.status);
        GradientDrawable bg = (GradientDrawable) holder.itemView.getContext()
            .getDrawable(R.drawable.bg_status_badge);
        if (bg != null) {
            bg = (GradientDrawable) bg.mutate();
            bg.setColor(badgeColor);
            holder.tvStatus.setBackground(bg);
        }

        holder.itemView.setOnClickListener(v -> listener.onJobClick(record));
    }

    // ------------------------------------------------------------------ //

    private String statusLabel(String status) {
        if (status == null) return "?";
        switch (status) {
            case RideRecord.STATUS_ACCEPTED:  return "Accepted";
            case RideRecord.STATUS_COMPLETED: return "Completed";
            case RideRecord.STATUS_DECLINED:  return "Declined";
            default:                          return "Pending";
        }
    }

    private int statusColor(View v, String status) {
        if (status == null) return v.getContext().getColor(R.color.status_pending);
        switch (status) {
            case RideRecord.STATUS_ACCEPTED:  return v.getContext().getColor(R.color.status_accepted);
            case RideRecord.STATUS_COMPLETED: return v.getContext().getColor(R.color.status_completed);
            case RideRecord.STATUS_DECLINED:  return v.getContext().getColor(R.color.status_declined);
            default:                          return v.getContext().getColor(R.color.status_pending);
        }
    }

    // ------------------------------------------------------------------ //

    static class ViewHolder {
        final View itemView;
        TextView tvDestination;
        TextView tvDistance;
        TextView tvFare;
        TextView tvTime;
        TextView tvStatus;

        ViewHolder(View itemView) {
            this.itemView = itemView;
            tvDestination = itemView.findViewById(R.id.tv_job_destination);
            tvDistance    = itemView.findViewById(R.id.tv_job_distance);
            tvFare        = itemView.findViewById(R.id.tv_job_fare);
            tvTime        = itemView.findViewById(R.id.tv_job_time);
            tvStatus      = itemView.findViewById(R.id.tv_job_status_badge);
        }
    }
}
